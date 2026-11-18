package io.github.brooswitminecraft.layeredslopegrading;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Only compiled with -PclientAcceptance, never part of a normal release. Prepares two plain
 * vanilla DIRT sources and a survival player holding a shovel standing east of both (so
 * grading's push heading is WEST, away from the player) -- the fixture {@link AcceptanceClient}
 * drives the real client-prediction interaction against: SOURCE/WEST is a supported, successful
 * push; BLOCKED_SOURCE/BLOCKED_WEST has its push destination occupied by STONE, the
 * blocked/unsupported case that must stay a safe no-op on both client and server.
 */
@EventBusSubscriber(modid = LayeredSlopeGradingMod.MODID)
public final class AcceptanceServer {
    private static int ticks;

    static final BlockPos SOURCE = new BlockPos(0, 100, 1);
    static final BlockPos WEST = new BlockPos(-1, 100, 1);
    static final BlockPos BLOCKED_SOURCE = new BlockPos(0, 100, 0);
    static final BlockPos BLOCKED_WEST = new BlockPos(-1, 100, 0);

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var players = event.getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        var player = players.getFirst();
        var level = player.serverLevel();
        if (ticks++ == 0) {
            player.setInvulnerable(true);
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            // A full platform, not just under the grading cells -- the player's own standing
            // position (east of both sources) needs a floor too, or they free-fall and the
            // vanilla anti-cheat kicks them for "flying" before AcceptanceClient ever clicks.
            for (int x = -2; x <= 3; x++) {
                for (int z = 0; z <= 2; z++) {
                    level.setBlock(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            level.setBlock(SOURCE, Blocks.DIRT.defaultBlockState(), 3);
            level.setBlock(WEST, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(BLOCKED_SOURCE, Blocks.DIRT.defaultBlockState(), 3);
            level.setBlock(BLOCKED_WEST, Blocks.STONE.defaultBlockState(), 3); // blocked: push destination occupied
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SHOVEL));
            player.teleportTo(2.5, 100, 1.5); // east of both sources: pull heading = EAST, push heading = WEST
            LayeredSlopeGradingMod.LOGGER.info("GRADING_ACCEPTANCE_SERVER_READY");
        }
    }
}
