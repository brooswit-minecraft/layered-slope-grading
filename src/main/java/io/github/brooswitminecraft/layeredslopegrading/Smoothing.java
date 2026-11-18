package io.github.brooswitminecraft.layeredslopegrading;

import io.github.brooswitminecraft.layeredslopes.Anchor;
import io.github.brooswitminecraft.layeredslopes.LayerMath;
import io.github.brooswitminecraft.layeredslopes.LayeredBlock;
import io.github.brooswitminecraft.layeredslopes.LayeredGrassBlock;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;

/**
 * smooth(direction): move exactly one sixteenth of material from a block into
 * its neighbour. Deterministic; erosion and manual tools call this.
 */
public final class Smoothing {
    private Smoothing() {}

    /**
     * A planned move: nothing has changed in the world yet. Every field here, {@link
     * #plan}/{@link #kind} included, is re-derived and compared by value after firing {@link
     * SmoothingAuthorizationEvent} ({@code smooth()}'s {@code equals(freshlyRecomputedMove)}
     * check) -- a synchronous listener can mutate ANY part of the world this plan depended on
     * (source, destination, OR the anchor-support block the destination relies on, which is
     * never one of those two positions), and this plan must never be applied against a world
     * that has since changed out from under it in any such way.
     */
    private record Move(LayeredBlock block, LayeredBlock destinationBlock, BlockPos source, BlockPos destination,
            SmoothRules.Plan plan, SmoothRules.Destination kind,
            BlockState sourceStateBefore, BlockState destinationStateBefore) {}

    /** @return true if a layer moved, false if the move was rejected. */
    public static boolean smooth(Level level, BlockPos pos, Direction direction) {
        return smooth(level, pos, direction, false);
    }

    /**
     * @param disturb whether this move counts as disturbance: grass-group material
     *     settles as dirt on both ends instead of keeping a grass top. Erosion
     *     disturbs; manual grading and the debug command do not (grass keeps its
     *     grass top while graded).
     * @return true if a layer moved, false if the move was rejected, including a rejection by a
     *     {@link SmoothingAuthorizationEvent} listener (e.g. a claim/protection mod) cancelling
     *     the move before either end is touched -- indistinguishable from any other rejection --
     *     or because the listener itself mutated the world (anywhere the plan depended on,
     *     including the anchor-support block) in a way that makes the plan stale (see {@link
     *     Move}); the move is also rejected in that case rather than applying a stale plan on
     *     top of an unexpectedly changed world.
     */
    public static boolean smooth(Level level, BlockPos pos, Direction direction, boolean disturb) {
        if (level.isClientSide()) {
            return false;
        }
        Optional<Move> move = plan(level, pos, direction);
        if (move.isEmpty()) {
            return false;
        }
        Move m = move.get();
        SmoothingAuthorizationEvent authorization =
                new SmoothingAuthorizationEvent(level, m.source(), m.destination(), direction, disturb);
        NeoForge.EVENT_BUS.post(authorization);
        if (authorization.isCanceled()) {
            return false;
        }
        // Re-derive the plan from the world as it stands right now and require it to match
        // byte-for-byte what was authorized above: this catches a listener changing source,
        // destination, OR the anchor-support block (never one of those two positions, so the
        // state-equality check above alone cannot see it) -- any such change invalidates the
        // specific operation that was just authorized, so re-plan() rather than re-check two
        // fixed positions.
        if (!plan(level, pos, direction).equals(move)) {
            return false;
        }
        Settling.Result settle = Settling.settle(disturb, m.block() instanceof LayeredGrassBlock, m.kind(),
                m.destinationBlock() instanceof LayeredGrassBlock);
        LayeredBlock sourceBlock = settle.source() ? LayeredMaterials.disturbed(m.block()) : m.block();
        LayeredBlock destBlock = settle.destination() ? LayeredMaterials.disturbed(m.destinationBlock()) : m.destinationBlock();
        level.setBlock(m.source(), stateOf(level, m.source(), sourceBlock, m.plan().source()), Block.UPDATE_ALL);
        level.setBlock(m.destination(), stateOf(level, m.destination(), destBlock, m.plan().destination()), Block.UPDATE_ALL);
        return true;
    }

    /** Whether smooth() would accept this move. Changes nothing, and works on either side of the connection. */
    public static boolean canSmooth(Level level, BlockPos pos, Direction direction) {
        return plan(level, pos, direction).isPresent();
    }

    private static Optional<Move> plan(Level level, BlockPos pos, Direction direction) {
        BlockState sourceState = level.getBlockState(pos);
        LayeredBlock layeredBlock;
        SmoothRules.Cell source;
        if (sourceState.getBlock() instanceof LayeredBlock lb) {
            layeredBlock = lb;
            source = new SmoothRules.Cell(sourceState.getValue(LayeredBlock.LAYERS), sourceState.getValue(LayeredBlock.ANCHOR).isCeiling());
        } else if ((layeredBlock = LayeredMaterials.layeredFor(sourceState.getBlock())) != null) {
            source = new SmoothRules.Cell(LayerMath.MAX_LAYERS, false);
        } else if (sourceState.getBlock() instanceof SlabBlock
                && (layeredBlock = LayeredMaterials.layeredForSlab(sourceState.getBlock())) != null) {
            if (sourceState.getValue(BlockStateProperties.WATERLOGGED)) {
                return Optional.empty(); // the water would have nowhere to go
            }
            SlabType type = sourceState.getValue(SlabBlock.TYPE);
            source = SmoothRules.slab(type == SlabType.TOP, type == SlabType.DOUBLE);
        } else {
            return Optional.empty();
        }

        BlockPos destPos = pos.relative(direction);
        BlockState destState = level.getBlockState(destPos);
        SmoothRules.Destination kind;
        SmoothRules.Cell dest = null;
        LayeredBlock destBlock = layeredBlock;
        if (destState.isAir()) {
            kind = SmoothRules.Destination.EMPTY;
        } else if (destState.getBlock() instanceof LayeredBlock destLayered && LayeredMaterials.sameGroup(layeredBlock, destLayered)) {
            destBlock = destLayered;
            kind = SmoothRules.Destination.SAME_MATERIAL;
            dest = new SmoothRules.Cell(destState.getValue(LayeredBlock.LAYERS), destState.getValue(LayeredBlock.ANCHOR).isCeiling());
        } else {
            kind = SmoothRules.Destination.BLOCKED;
        }
        LayeredBlock finalDestBlock = destBlock;
        LayeredBlock sourceBlock = layeredBlock;
        return SmoothRules.plan(source, kind, dest, anchorSupported(level, pos, destPos, source.ceiling()))
                .map(plan -> new Move(sourceBlock, finalDestBlock, pos, destPos, plan, kind, sourceState, destState));
    }

    /**
     * Whether a new layer at {@code destPos} would stay anchored. The source
     * cell never counts as support: it has just lost a layer, so it is no
     * longer a full face.
     */
    private static boolean anchorSupported(Level level, BlockPos sourcePos, BlockPos destPos, boolean ceiling) {
        Direction toSupport = ceiling ? Direction.UP : Direction.DOWN;
        BlockPos supportPos = destPos.relative(toSupport);
        if (supportPos.equals(sourcePos)) {
            return false;
        }
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, toSupport.getOpposite());
    }

    private static BlockState stateOf(Level level, BlockPos pos, LayeredBlock block, SmoothRules.Cell cell) {
        if (cell == null) {
            return Blocks.AIR.defaultBlockState();
        }
        BlockState state = block.defaultBlockState()
                .setValue(LayeredBlock.LAYERS, cell.layers())
                .setValue(LayeredBlock.ANCHOR, cell.ceiling() ? Anchor.CEILING : Anchor.FLOOR);
        if (block instanceof LayeredGrassBlock grass) {
            state = grass.withSnowy(level, pos, state);
        }
        return state;
    }
}
