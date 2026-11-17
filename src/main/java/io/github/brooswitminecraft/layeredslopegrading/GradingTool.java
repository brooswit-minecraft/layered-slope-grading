package io.github.brooswitminecraft.layeredslopegrading;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Manual terrain shaping: right-click a block with a pickaxe to pull one
 * sixteenth of it toward you, or with a shovel to push one sixteenth of it
 * away from you, via smooth(). Pickaxes have no vanilla right-click action on
 * blocks, so nothing vanilla is displaced there; shovels do (path conversion),
 * so grading takes over a shovel click on any block it recognizes at all,
 * even when the push itself cannot proceed -- hoes are untouched and still
 * till.
 *
 * <p>Grading eligibility (does this block participate in grading?) is
 * separate from move feasibility (does this specific direction have room?).
 * A pickaxe only cancels the vanilla click when the pull would actually
 * succeed, since leaving it alone is itself a no-op. A shovel cancels the
 * click whenever the clicked block is grading-eligible, succeeding push or
 * not, so an unsupported/blocked push is a safe no-op instead of an
 * unexpected flatten-to-path. When it applies, the click is cancelled on both
 * client and server so the client does not predict a vanilla action.
 */
public final class GradingTool {
    private GradingTool() {}

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        boolean pickaxe = stack.is(ItemTags.PICKAXES);
        boolean shovel = !pickaxe && stack.is(ItemTags.SHOVELS);
        if (!pickaxe && !shovel) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!Smoothing.isGradingSource(level, pos)) {
            return;
        }
        Vec3 center = pos.getCenter();
        Direction towardPlayer = Direction.valueOf(Heading.toward(player.getX() - center.x, player.getZ() - center.z).name());
        Direction direction = pickaxe ? towardPlayer : towardPlayer.getOpposite();

        if (!Smoothing.canSmooth(level, pos, direction)) {
            if (shovel) {
                // Grading-eligible terrain: suppress vanilla path conversion as a safe no-op
                // rather than letting an unsupported/blocked push fall through to it.
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
            }
            return;
        }
        if (!level.isClientSide()) {
            Smoothing.smooth(level, pos, direction);
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
    }
}
