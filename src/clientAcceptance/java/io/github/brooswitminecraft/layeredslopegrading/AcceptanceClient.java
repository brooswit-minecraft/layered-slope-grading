package io.github.brooswitminecraft.layeredslopegrading;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.brooswitminecraft.layeredslopes.LayeredBlock;
import io.github.brooswitminecraft.layeredslopes.LayeredSlopesMod;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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
 * {@link AcceptanceServer}. MINECRAFT-557: a server-only GameTest proves {@code GradingTool}'s
 * {@code smooth()} call and its {@code if (!level.isClientSide())} guard, but not that the real
 * client-prediction seam -- {@code MultiPlayerGameMode#useItemOn}, which fires the exact same
 * {@code PlayerInteractEvent} as its server counterpart -- actually cancels an eligible shovel
 * click without locally predicting a mutation, a durability loss, or vanilla path conversion,
 * while still leaving the server free to perform the real, authoritative result once its own
 * copy of the packet-driven interaction runs (the packet is sent to the server unconditionally,
 * regardless of the client's own cancellation -- confirmed by reading {@code
 * MultiPlayerGameMode#useItemOn}'s {@code startPrediction} call). Covers both branches John's
 * direction and wroosbit's review required: a supported, successful push (SOURCE/WEST) and a
 * blocked/unsupported one (BLOCKED_SOURCE/BLOCKED_WEST, destination occupied) that must stay a
 * safe no-op on both client and server.
 *
 * <p>This drives {@code Minecraft.getInstance().gameMode.useItemOn(...)} directly -- the real
 * production method a mouse click calls, never {@link GradingTool} called directly.
 */
@EventBusSubscriber(modid = LayeredSlopeGradingMod.MODID, value = Dist.CLIENT)
public final class AcceptanceClient {
    private static int ticks;
    private static int phase;
    private static int damageBeforeSuccessfulClick;
    private static int damageAfterSuccessfulClick;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ticks++;
        try {
            switch (phase) {
                case 0 -> {
                    // Give the server's fixture time to place blocks and the client time to sync chunks.
                    if (ticks != 60) return;
                    assertBlock(mc, AcceptanceServer.SOURCE, Blocks.DIRT,
                            "fixture DIRT source never synced to the client at the prepared position");
                    damageBeforeSuccessfulClick = mc.player.getMainHandItem().getDamageValue();
                    click(mc, AcceptanceServer.SOURCE);

                    // Immediately after the client-predicted click returns: no local misprediction at
                    // all, even though the click packet has already been sent to the server (javadoc).
                    assertBlock(mc, AcceptanceServer.SOURCE, Blocks.DIRT,
                            "client predicted a mutation of the successful-push source instead of leaving "
                                    + "the move to the server's authoritative result");
                    assertBlock(mc, AcceptanceServer.WEST, Blocks.AIR,
                            "client predicted a push into the successful-push destination instead of "
                                    + "leaving the move to the server's authoritative result");
                    assertDamage(mc, damageBeforeSuccessfulClick,
                            "client locally consumed durability before the server confirmed the successful push");
                    phase = 1;
                }
                case 1 -> {
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
                    damageAfterSuccessfulClick = mc.player.getMainHandItem().getDamageValue();
                    if (damageAfterSuccessfulClick != damageBeforeSuccessfulClick + 1) {
                        throw new IllegalStateException(
                                "server's authoritative durability charge never synced back to the client, got "
                                        + damageAfterSuccessfulClick);
                    }
                    phase = 2;
                }
                case 2 -> {
                    if (ticks != 180) return;
                    assertBlock(mc, AcceptanceServer.BLOCKED_SOURCE, Blocks.DIRT,
                            "fixture DIRT blocked-source never synced to the client at the prepared position");
                    assertBlock(mc, AcceptanceServer.BLOCKED_WEST, Blocks.STONE,
                            "fixture STONE blocked-destination never synced to the client at the prepared position");
                    click(mc, AcceptanceServer.BLOCKED_SOURCE);

                    // The blocked click must be exactly as inert locally as the successful one was,
                    // including -- the whole point of this branch -- no predicted vanilla path
                    // conversion, since the block above BLOCKED_SOURCE is air and the clicked face is
                    // not DOWN, exactly the shape vanilla's own flatten check looks for.
                    assertBlock(mc, AcceptanceServer.BLOCKED_SOURCE, Blocks.DIRT,
                            "client predicted a mutation (including possible vanilla path conversion) of "
                                    + "the blocked source instead of a safe no-op");
                    assertBlock(mc, AcceptanceServer.BLOCKED_WEST, Blocks.STONE,
                            "client predicted a change to the blocked destination instead of a safe no-op");
                    assertDamage(mc, damageAfterSuccessfulClick,
                            "client locally consumed durability on a blocked push that cannot move anything");
                    phase = 3;
                }
                case 3 -> {
                    // Give the round trip time to land, then confirm the server ALSO made no change --
                    // the blocked push is authoritatively a safe no-op, not just silently dropped.
                    if (ticks != 270) return;
                    assertBlock(mc, AcceptanceServer.BLOCKED_SOURCE, Blocks.DIRT,
                            "the server's authoritative result for the blocked push mutated the source "
                                    + "(e.g. flattened to a path) instead of leaving it a safe no-op");
                    assertBlock(mc, AcceptanceServer.BLOCKED_WEST, Blocks.STONE,
                            "the server's authoritative result for the blocked push mutated the blocked "
                                    + "destination instead of leaving it a safe no-op");
                    assertDamage(mc, damageAfterSuccessfulClick,
                            "the server charged durability for a blocked push that moved nothing");

                    Files.writeString(Path.of("client-acceptance-result.json"),
                            "{\"passed\":true,\"client_did_not_predict_mutation\":true,"
                                    + "\"client_did_not_predict_durability_loss\":true,"
                                    + "\"server_authoritative_push_synced\":true,"
                                    + "\"blocked_push_is_safe_noop_client_and_server\":true}");
                    LayeredSlopeGradingMod.LOGGER.info("GRADING_ACCEPTANCE_PASS");
                    mc.stop();
                }
                default -> throw new IllegalStateException("unreachable phase " + phase);
            }
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

    private static void click(Minecraft mc, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, true);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
    }

    private static void assertBlock(Minecraft mc, BlockPos pos, net.minecraft.world.level.block.Block expected, String message) {
        BlockState actual = mc.level.getBlockState(pos);
        if (actual.getBlock() != expected) {
            throw new IllegalStateException(message + ": " + actual);
        }
    }

    private static void assertDamage(Minecraft mc, int expected, String message) {
        int actual = mc.player.getMainHandItem().getDamageValue();
        if (actual != expected) {
            throw new IllegalStateException(message + ", got " + actual);
        }
    }
}
