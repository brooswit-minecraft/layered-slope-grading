package io.github.brooswitminecraft.layeredslopegrading;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.github.brooswitminecraft.layeredslopes.LayeredBlock;
import io.github.brooswitminecraft.layeredslopes.LayeredSlopesMod;
import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * MINECRAFT-557 real interaction-seam evidence: every test here drives the actual production
 * entry point real gameplay uses -- {@code ServerPlayer#gameMode}'s {@code useItemOn}, which
 * fires the exact {@code PlayerInteractEvent.RightClickBlock} on {@code NeoForge.EVENT_BUS} that
 * {@link GradingTool#onRightClickBlock} is registered against, then falls through to vanilla
 * block/item interaction exactly as a real client's click would -- never {@link GradingTool}'s
 * method called directly, and never {@code GameTestHelper#useBlock}, which bypasses that event
 * entirely (it calls {@code BlockState#useItemOn} straight, skipping {@code CommonHooks} and the
 * event bus). Covers both halves of this ticket: the retained pickaxe pull (regression) and the
 * new shovel push, including the "blocked push is a safe no-op, not a flatten-to-path" and
 * "off-hand never grades" requirements the pure {@code SmoothRules}/{@code Heading} unit tests
 * cannot exercise on their own.
 */
@GameTestHolder(LayeredSlopeGradingMod.MODID)
@PrefixGameTestTemplate(false)
public final class GradingToolInteractionGameTests {
    private GradingToolInteractionGameTests() {}

    /** A real, non-creative ServerPlayer: durability assertions need a player who actually takes damage. */
    private static ServerPlayer spawnSurvivalPlayer(GameTestHelper helper, BlockPos standingNear, double dx, double dz) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        // The GameTest server's default game type is creative, which makes hurtAndBreak() a
        // silent no-op (Player#hasInfiniteMaterials()) -- force survival so durability actually moves.
        player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(standingNear));
        player.moveTo(center.x + dx, center.y, center.z + dz, 0.0F, 0.0F);
        return player;
    }

    private static BlockHitResult hitResult(GameTestHelper helper, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        return new BlockHitResult(Vec3.atCenterOf(absolute), Direction.NORTH, absolute, true);
    }

    /**
     * Regression: the pickaxe pull (MINECRAFT-511) is unchanged by the shovel-push refactor
     * ({@link Smoothing#isGradingSource} split out of {@link Smoothing#canSmooth}).
     */
    @GameTest(template = "flat_platform")
    public static void pickaxePullsOneLayerTowardThePlayerAndChargesDurability(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 2, 1);
        BlockPos east = source.relative(Direction.EAST);
        helper.setBlock(source, Blocks.DIRT.defaultBlockState());
        helper.setBlock(east, Blocks.AIR.defaultBlockState());
        helper.setBlock(east.below(), Blocks.STONE.defaultBlockState());

        ServerPlayer player = spawnSurvivalPlayer(helper, source, 2.0, 0.0); // east of source: pull heading = EAST
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);

        player.gameMode.useItemOn(player, helper.getLevel(), pickaxe, InteractionHand.MAIN_HAND, hitResult(helper, source));

        BlockState sourceState = helper.getBlockState(source);
        BlockState eastState = helper.getBlockState(east);
        helper.assertTrue(sourceState.getBlock() == LayeredSlopesMod.LAYERED_DIRT.get(), "source must become LAYERED_DIRT after the pull");
        helper.assertTrue(sourceState.getValue(LayeredBlock.LAYERS) == 15, "source should have lost exactly one layer to the pull");
        helper.assertTrue(eastState.getBlock() == LayeredSlopesMod.LAYERED_DIRT.get(), "destination must become LAYERED_DIRT after the pull");
        helper.assertTrue(eastState.getValue(LayeredBlock.LAYERS) == 1, "destination should have gained exactly one layer from the pull");
        helper.assertTrue(pickaxe.getDamageValue() == 1, "a successful pull must charge exactly one durability point");
        helper.succeed();
    }

    /** The new behavior: a shovel right-click pushes one layer AWAY from the player, opposite the pickaxe pull. */
    @GameTest(template = "flat_platform")
    public static void shovelPushesOneLayerAwayFromThePlayerAndChargesDurability(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 2, 1);
        BlockPos west = source.relative(Direction.WEST);
        helper.setBlock(source, Blocks.DIRT.defaultBlockState());
        helper.setBlock(west, Blocks.AIR.defaultBlockState());
        helper.setBlock(west.below(), Blocks.STONE.defaultBlockState());

        ServerPlayer player = spawnSurvivalPlayer(helper, source, 2.0, 0.0); // east of source: push heading = WEST (opposite of pull)
        ItemStack shovel = new ItemStack(Items.DIAMOND_SHOVEL);
        player.setItemInHand(InteractionHand.MAIN_HAND, shovel);

        player.gameMode.useItemOn(player, helper.getLevel(), shovel, InteractionHand.MAIN_HAND, hitResult(helper, source));

        BlockState sourceState = helper.getBlockState(source);
        BlockState westState = helper.getBlockState(west);
        helper.assertTrue(sourceState.getBlock() != Blocks.DIRT_PATH, "grading must have taken over the click, never vanilla path conversion");
        helper.assertTrue(sourceState.getBlock() == LayeredSlopesMod.LAYERED_DIRT.get(), "source must become LAYERED_DIRT after the push");
        helper.assertTrue(sourceState.getValue(LayeredBlock.LAYERS) == 15, "source should have lost exactly one layer to the push");
        helper.assertTrue(westState.getBlock() == LayeredSlopesMod.LAYERED_DIRT.get(), "destination (away from the player) must become LAYERED_DIRT after the push");
        helper.assertTrue(westState.getValue(LayeredBlock.LAYERS) == 1, "destination should have gained exactly one layer from the push");
        helper.assertTrue(shovel.getDamageValue() == 1, "a successful push must charge exactly one durability point");
        helper.succeed();
    }

    /** A slab source grades through the push exactly as SmoothRulesTest's pure bottom-slab case predicts. */
    @GameTest(template = "flat_platform")
    public static void shovelPushesABottomSlabAwayFromThePlayer(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 2, 1);
        BlockPos west = source.relative(Direction.WEST);
        helper.setBlock(source, Blocks.STONE_SLAB.defaultBlockState());
        helper.setBlock(west, Blocks.AIR.defaultBlockState());
        helper.setBlock(west.below(), Blocks.STONE.defaultBlockState());

        ServerPlayer player = spawnSurvivalPlayer(helper, source, 2.0, 0.0);
        ItemStack shovel = new ItemStack(Items.DIAMOND_SHOVEL);
        player.setItemInHand(InteractionHand.MAIN_HAND, shovel);

        player.gameMode.useItemOn(player, helper.getLevel(), shovel, InteractionHand.MAIN_HAND, hitResult(helper, source));

        BlockState sourceState = helper.getBlockState(source);
        BlockState westState = helper.getBlockState(west);
        helper.assertTrue(sourceState.getBlock() == LayeredSlopesMod.LAYERED_STONE.get(), "a bottom slab source must become LAYERED_STONE after the push");
        helper.assertTrue(sourceState.getValue(LayeredBlock.LAYERS) == 7, "a bottom slab (8 layers) should have lost exactly one layer to the push");
        helper.assertTrue(westState.getBlock() == LayeredSlopesMod.LAYERED_STONE.get(), "destination must become LAYERED_STONE after the push");
        helper.assertTrue(westState.getValue(LayeredBlock.LAYERS) == 1, "destination should have gained exactly one layer from the push");
        helper.succeed();
    }

    /**
     * The explicit ticket requirement: an unsupported/blocked push on grading-eligible terrain is a
     * safe no-op, never an unexpected flatten-to-path -- even though the block above the source is
     * air and the clicked face is not DOWN, which is exactly the shape vanilla's own path-flatten
     * check looks for.
     */
    @GameTest(template = "flat_platform")
    public static void shovelBlockedPushIsASafeNoOpNotPathConversion(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 2, 1);
        BlockPos west = source.relative(Direction.WEST);
        helper.setBlock(source, Blocks.DIRT.defaultBlockState());
        helper.setBlock(west, Blocks.STONE.defaultBlockState()); // blocked: not air, not the same layered material

        ServerPlayer player = spawnSurvivalPlayer(helper, source, 2.0, 0.0); // push heading = WEST, into the blocked stone
        ItemStack shovel = new ItemStack(Items.DIAMOND_SHOVEL);
        player.setItemInHand(InteractionHand.MAIN_HAND, shovel);

        player.gameMode.useItemOn(player, helper.getLevel(), shovel, InteractionHand.MAIN_HAND, hitResult(helper, source));

        helper.assertTrue(helper.getBlockState(source).getBlock() == Blocks.DIRT, "a blocked push must leave the source completely unchanged, not flattened to a path");
        helper.assertTrue(helper.getBlockState(west).getBlock() == Blocks.STONE, "a blocked push must leave the blocked destination completely unchanged");
        helper.assertTrue(shovel.getDamageValue() == 0, "a blocked push must not charge durability: nothing actually moved");
        helper.succeed();
    }

    /** Grading is deliberately blind to tools other than pickaxes/shovels: a hoe still tills normally. */
    @GameTest(template = "flat_platform")
    public static void hoeOnGradingEligibleTerrainIsUnaffectedByGrading(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 2, 1);
        helper.setBlock(source, Blocks.DIRT.defaultBlockState());
        helper.setBlock(source.above(), Blocks.AIR.defaultBlockState());

        ServerPlayer player = spawnSurvivalPlayer(helper, source, 2.0, 0.0);
        ItemStack hoe = new ItemStack(Items.DIAMOND_HOE);
        player.setItemInHand(InteractionHand.MAIN_HAND, hoe);

        player.gameMode.useItemOn(player, helper.getLevel(), hoe, InteractionHand.MAIN_HAND, hitResult(helper, source));

        helper.assertTrue(helper.getBlockState(source).getBlock() == Blocks.FARMLAND, "grading must leave a hoe's vanilla tilling completely alone");
        helper.succeed();
    }

    /**
     * Off-hand never grades (unchanged main-hand guard, now shared by both tools): a shovel in the
     * off-hand still runs vanilla path conversion untouched by grading -- proof grading cannot ALSO
     * fire for the same click, which would duplicate the move.
     */
    @GameTest(template = "flat_platform")
    public static void offHandShovelDoesNotGradeAndVanillaPathConversionStillRuns(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 2, 1);
        BlockPos west = source.relative(Direction.WEST);
        helper.setBlock(source, Blocks.DIRT.defaultBlockState());
        helper.setBlock(source.above(), Blocks.AIR.defaultBlockState());
        helper.setBlock(west, Blocks.AIR.defaultBlockState());
        helper.setBlock(west.below(), Blocks.STONE.defaultBlockState());

        ServerPlayer player = spawnSurvivalPlayer(helper, source, 2.0, 0.0);
        ItemStack shovel = new ItemStack(Items.DIAMOND_SHOVEL);
        player.setItemInHand(InteractionHand.OFF_HAND, shovel);

        player.gameMode.useItemOn(player, helper.getLevel(), shovel, InteractionHand.OFF_HAND, hitResult(helper, source));

        BlockState sourceState = helper.getBlockState(source);
        helper.assertTrue(sourceState.getBlock() == Blocks.DIRT_PATH, "an off-hand shovel must fall through to vanilla path conversion, not be swallowed silently");
        helper.assertTrue(sourceState.getBlock() != LayeredSlopesMod.LAYERED_DIRT.get(), "an off-hand shovel must never grade: that would duplicate a main-hand click's move");
        helper.assertTrue(helper.getBlockState(west).getBlock() == Blocks.AIR, "grading must not have also pushed a layer away, even though the destination was free");
        helper.succeed();
    }
}
