package io.github.brooswitminecraft.layeredslopegrading;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired on {@code NeoForge.EVENT_BUS} immediately before {@link Smoothing#smooth} mutates the
 * world, so a protection/claim mod gets a chance to cancel the move before either end changes --
 * the same cancellable-event shape vanilla's own {@code BlockEvent.BreakEvent} uses, which is
 * what existing claim-protection mods already listen for.
 *
 * <p>Covers both halves of the move: {@link #source()} loses up to one layer, {@link
 * #destination()} gains up to one layer (or becomes a new block entirely, e.g. AIR -&gt;
 * LayeredBlock). A listener must authorize (or deny) the move as a whole; there is no partial
 * grant of just one end. Cancelling leaves the world completely unchanged and {@code smooth()}
 * returns {@code false} -- indistinguishable from any other rejected move (blocked destination,
 * unsupported anchor, etc).
 *
 * <p>This event does not itself cover a resulting neighbor update or cave-in: those propagate
 * through vanilla's own {@code Block.UPDATE_ALL} block-change notification once a move is
 * authorized and applied, and are a consumer's (or a listening protection mod's) responsibility
 * to further gate if it cares about that propagation, same as it already must for any other
 * vanilla block change.
 */
public final class SmoothingAuthorizationEvent extends Event implements ICancellableEvent {
    private final Level level;
    private final BlockPos source;
    private final BlockPos destination;
    private final Direction direction;
    private final boolean disturb;

    public SmoothingAuthorizationEvent(Level level, BlockPos source, BlockPos destination, Direction direction, boolean disturb) {
        this.level = level;
        this.source = source;
        this.destination = destination;
        this.direction = direction;
        this.disturb = disturb;
    }

    public Level level() {
        return level;
    }

    /** The cell that would lose up to one layer. */
    public BlockPos source() {
        return source;
    }

    /** The cell that would gain up to one layer. */
    public BlockPos destination() {
        return destination;
    }

    public Direction direction() {
        return direction;
    }

    /** Whether this move counts as disturbance (erosion) rather than manual/debug grading. */
    public boolean disturb() {
        return disturb;
    }
}
