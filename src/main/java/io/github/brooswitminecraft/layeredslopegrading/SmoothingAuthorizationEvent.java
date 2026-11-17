package io.github.brooswitminecraft.layeredslopegrading;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired on {@code NeoForge.EVENT_BUS} immediately before {@link Smoothing#smooth} mutates the
 * world, so a listener gets a chance to cancel the move before either end changes -- the
 * cancellable-{@link ICancellableEvent} shape vanilla's own {@code BlockEvent.BreakEvent} uses.
 *
 * <p><b>This is a brand-new, mod-specific event type, not {@code BlockEvent.BreakEvent} itself.
 * An existing claim/protection mod does NOT automatically gain coverage of grading/erosion moves
 * just because this event exists</b> -- it must add its own explicit listener for {@code
 * SmoothingAuthorizationEvent} (the same way it would for any other mod's custom event). State
 * exactly which protection mod(s), if any, have been integrated and tested against this event;
 * never claim universal claim-mod coverage from this event's mere existence.
 *
 * <p>Covers both halves of the move: {@link #source()} loses up to one layer, {@link
 * #destination()} gains up to one layer (or becomes a new block entirely, e.g. AIR -&gt;
 * LayeredBlock). A listener must authorize (or deny) the move as a whole; there is no partial
 * grant of just one end. Cancelling leaves the world completely unchanged and {@code smooth()}
 * returns {@code false} -- indistinguishable from any other rejected move (blocked destination,
 * unsupported anchor, etc). {@code Smoothing.smooth()} also revalidates the move's planned block
 * states immediately after this event returns, before applying anything: if a synchronous
 * listener itself mutated {@link #source()} or {@link #destination()} during this event (rather
 * than merely cancelling), the move is rejected as stale instead of being applied on top of an
 * unexpectedly changed world.
 *
 * <p><b>Not yet covered by this event at all</b> -- an explicit, named gap, not a silent omission:
 * a resulting neighbor update or cave-in. Those propagate through vanilla's own {@code
 * Block.UPDATE_ALL} block-change notification once a move is authorized and applied, with no
 * cancellable gate of their own here. A consumer (e.g. an erosion feature built on top of this
 * mod) that needs that propagation itself protected must implement and test that guarantee
 * explicitly -- it does not come from this event, and must not be assumed or claimed without
 * that implementation actually existing.
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
