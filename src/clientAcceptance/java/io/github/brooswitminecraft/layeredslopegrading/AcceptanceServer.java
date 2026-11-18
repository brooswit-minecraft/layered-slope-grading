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
 * Only compiled with -PclientAcceptance, never part of a normal release. Prepares a plain vanilla
 * DIRT source with a supported empty neighbor to its west, and a survival player holding a
 * shovel standing east of it (so grading's push heading is WEST, away from the player) -- the
 * fixture {@link AcceptanceClient} drives the real client-prediction interaction against.
 */
@EventBusSubscriber(modid = LayeredSlopeGradingMod.MODID)
public final class AcceptanceServer {
    private static int ticks;

    static final BlockPos SOURCE = new BlockPos(0, 100, 1);
    static final BlockPos WEST = new BlockPos(-1, 100, 1);

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var players = event.getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        var player = players.getFirst();
        var level = player.serverLevel();
        if (ticks++ == 0) {
            player.setInvulnerable(true);
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            level.setBlock(SOURCE.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(WEST.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(SOURCE, Blocks.DIRT.defaultBlockState(), 3);
            level.setBlock(WEST, Blocks.AIR.defaultBlockState(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SHOVEL));
            player.teleportTo(2.5, 100, 1.5); // east of SOURCE: pull heading = EAST, so push heading = WEST
            LayeredSlopeGradingMod.LOGGER.info("GRADING_ACCEPTANCE_SERVER_READY");
        }
    }
}
