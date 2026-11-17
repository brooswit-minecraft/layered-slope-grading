package io.github.brooswitminecraft.layeredslopegrading;

import io.github.brooswitminecraft.layeredslopes.LayeredBlock;
import io.github.brooswitminecraft.layeredslopes.LayeredSlopesMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Acceptance evidence for {@link SmoothingAuthorizationEvent}: a listener that cancels the event
 * must leave BOTH the source and destination ends of the move completely unchanged -- the
 * ticket's explicit "cancellation leaves both ends unchanged" requirement, and the actual
 * mechanism a claim/protection mod would use to deny grading on its land.
 */
@GameTestHolder(LayeredSlopeGradingMod.MODID)
@PrefixGameTestTemplate(false)
public final class SmoothingAuthorizationGameTests {
    private SmoothingAuthorizationGameTests() {}

    @GameTest(template = "flat_platform")
    public static void cancellingTheAuthorizationEventLeavesBothEndsUnchanged(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 2, 1);
        BlockPos destPos = sourcePos.relative(Direction.EAST);
        BlockPos supportPos = destPos.below();

        helper.setBlock(sourcePos, Blocks.DIRT.defaultBlockState());
        helper.setBlock(destPos, Blocks.AIR.defaultBlockState());
        helper.setBlock(supportPos, Blocks.STONE.defaultBlockState());

        BlockState sourceBefore = helper.getBlockState(sourcePos);
        BlockState destBefore = helper.getBlockState(destPos);

        Object denyAll = new Object() {
            @SubscribeEvent
            public void onAuthorization(SmoothingAuthorizationEvent event) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.register(denyAll);
        boolean moved;
        try {
            moved = Smoothing.smooth(helper.getLevel(), helper.absolutePos(sourcePos), Direction.EAST);
        } finally {
            NeoForge.EVENT_BUS.unregister(denyAll);
        }

        helper.assertTrue(!moved, "smooth() must return false when a listener cancels the authorization event");
        helper.assertTrue(helper.getBlockState(sourcePos).equals(sourceBefore),
                "the source cell must stay completely unchanged when the move is cancelled");
        helper.assertTrue(helper.getBlockState(destPos).equals(destBefore),
                "the destination cell must stay completely unchanged when the move is cancelled");
        helper.succeed();
    }

    /** Control: with no listener registered (the production default), the same move still succeeds. */
    @GameTest(template = "flat_platform")
    public static void withNoListenerTheMoveStillSucceeds(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 2, 1);
        BlockPos destPos = sourcePos.relative(Direction.EAST);
        BlockPos supportPos = destPos.below();

        helper.setBlock(sourcePos, Blocks.DIRT.defaultBlockState());
        helper.setBlock(destPos, Blocks.AIR.defaultBlockState());
        helper.setBlock(supportPos, Blocks.STONE.defaultBlockState());

        boolean moved = Smoothing.smooth(helper.getLevel(), helper.absolutePos(sourcePos), Direction.EAST);
        helper.assertTrue(moved, "smooth() must still succeed when no listener denies the authorization event");
        helper.assertTrue(helper.getBlockState(sourcePos).getBlock() == LayeredSlopesMod.LAYERED_DIRT.get(),
                "the source cell must become LAYERED_DIRT after an authorized move");
        helper.assertTrue(helper.getBlockState(destPos).getValue(LayeredBlock.LAYERS) == 1,
                "the destination cell must gain exactly one layer after an authorized move");
        helper.succeed();
    }
}
