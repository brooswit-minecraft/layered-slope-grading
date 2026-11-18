package io.github.brooswitminecraft.layeredslopegrading;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.brooswitminecraft.layeredslopes.LayeredBlock;
import io.github.brooswitminecraft.layeredslopes.LayeredSlopesMod;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Only compiled with -PclientAcceptance, never part of a normal release. Separate test JVM from
 * {@link AcceptanceServer}. MINECRAFT-557 (and the self-review/John 23:54-23:57 escalation on the
 * ticket): a server-only GameTest proves {@code GradingTool}'s {@code smooth()} call and its
 * {@code if (!level.isClientSide())} guard, but not that the real client-prediction seam -- {@code
 * MultiPlayerGameMode#useItemOn}, which fires the exact same {@code PlayerInteractEvent} as its
 * server counterpart -- actually cancels an eligible shovel click without locally predicting a
 * mutation, a durability loss, or vanilla path conversion, while still leaving the server free to
 * perform the real, authoritative move once its own copy of the packet-driven interaction runs
 * (the packet is sent to the server unconditionally, regardless of the client's own cancellation --
 * confirmed by reading {@code MultiPlayerGameMode#useItemOn}'s {@code startPrediction} call).
 *
 * <p>This drives {@code Minecraft.getInstance().gameMode.useItemOn(...)} directly -- the real
 * production method a mouse click calls, never {@link GradingTool} called directly.
 */
@EventBusSubscriber(modid = LayeredSlopeGradingMod.MODID, value = Dist.CLIENT)
public final class AcceptanceClient {
    private static int ticks;
    private static boolean clicked;
    private static int damageBeforeClick;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ticks++;
        try {
            if (!clicked) {
                // Give the server's fixture time to place blocks and the client time to sync chunks.
                if (ticks != 60) return;
                BlockState before = mc.level.getBlockState(AcceptanceServer.SOURCE);
                if (before.getBlock() != Blocks.DIRT) {
                    throw new IllegalStateException(
                            "fixture DIRT source never synced to the client at the prepared position: " + before);
                }
                damageBeforeClick = mc.player.getMainHandItem().getDamageValue();
                BlockHitResult hit = new BlockHitResult(
                        Vec3.atCenterOf(AcceptanceServer.SOURCE), Direction.NORTH, AcceptanceServer.SOURCE, true);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);

                // Immediately after the client-predicted click returns: no local misprediction at all,
                // even though the click packet has already been sent to the server (see class javadoc).
                BlockState sourceAfter = mc.level.getBlockState(AcceptanceServer.SOURCE);
                if (sourceAfter.getBlock() != Blocks.DIRT) {
                    throw new IllegalStateException(
                            "client predicted a mutation of the source block instead of leaving the move "
                                    + "to the server's authoritative result: " + sourceAfter);
                }
                BlockState westAfter = mc.level.getBlockState(AcceptanceServer.WEST);
                if (westAfter.getBlock() != Blocks.AIR) {
                    throw new IllegalStateException(
                            "client predicted a push into the destination instead of leaving the move "
                                    + "to the server's authoritative result: " + westAfter);
                }
                int damageAfterClick = mc.player.getMainHandItem().getDamageValue();
                if (damageAfterClick != damageBeforeClick) {
                    throw new IllegalStateException(
                            "client locally consumed durability before the server confirmed the move");
                }
                clicked = true;
                return;
            }
            // Give the round trip -- server processes the packet, mutates, syncs blocks/inventory
            // back -- time to land, then confirm the server's authoritative result actually arrived.
            if (ticks != 150) return;
            BlockState sourceFinal = mc.level.getBlockState(AcceptanceServer.SOURCE);
            if (sourceFinal.getBlock() != LayeredSlopesMod.LAYERED_DIRT.get()
                    || sourceFinal.getValue(LayeredBlock.LAYERS) != 15) {
                throw new IllegalStateException(
                        "server's authoritative push never reached the client for the source block: " + sourceFinal);
            }
            BlockState westFinal = mc.level.getBlockState(AcceptanceServer.WEST);
            if (westFinal.getBlock() != LayeredSlopesMod.LAYERED_DIRT.get()
                    || westFinal.getValue(LayeredBlock.LAYERS) != 1) {
                throw new IllegalStateException(
                        "server's authoritative push never reached the client for the destination block: " + westFinal);
            }
            int finalDamage = mc.player.getMainHandItem().getDamageValue();
            if (finalDamage != damageBeforeClick + 1) {
                throw new IllegalStateException(
                        "server's authoritative durability charge never synced back to the client, got " + finalDamage);
            }
            Files.writeString(Path.of("client-acceptance-result.json"),
                    "{\"passed\":true,\"client_did_not_predict_mutation\":true,"
                            + "\"client_did_not_predict_durability_loss\":true,"
                            + "\"server_authoritative_push_synced\":true}");
            LayeredSlopeGradingMod.LOGGER.info("GRADING_ACCEPTANCE_PASS");
            mc.stop();
        } catch (Exception failure) {
            LayeredSlopeGradingMod.LOGGER.error("GRADING_ACCEPTANCE_FAIL", failure);
            try {
                Files.writeString(Path.of("client-acceptance-failure.txt"), failure.toString());
            } catch (Exception ignored) {
                // best-effort evidence only
            }
            mc.stop();
        }
    }
}
