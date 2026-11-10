package io.github.brooswitminecraft.layeredslopegrading;

import io.github.brooswitminecraft.layeredslopes.LayeredBlock;
import io.github.brooswitminecraft.layeredslopes.LayeredSlopesMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * MINECRAFT-511 standalone acceptance evidence (Jira comments 52878/52881 on this ticket): this
 * mod must resolve ordinary vanilla-block grading with Layered Slopes alone -- {@code ci.yml}'s
 * "Run GameTests" step boots {@code ./gradlew runGameTestServer} with ONLY this mod and
 * layered-slopes installed, Dynamic Terrain never on the classpath at all. The run succeeding is
 * itself standalone-load evidence; the tests below additionally prove the real production seam
 * this ticket's correction was about -- that {@link LayeredMaterials}'s registration, now owned
 * by {@link LayeredSlopeGradingMod}'s own static init rather than Dynamic Terrain's, actually
 * resolves a plain vanilla block to its layered form and grades it through {@link Smoothing},
 * not just a coinstalled-with-DT scenario.
 */
@GameTestHolder(LayeredSlopeGradingMod.MODID)
@PrefixGameTestTemplate(false)
public final class StandaloneGradingGameTests {
    private StandaloneGradingGameTests() {}

    /**
     * Without Dynamic Terrain anywhere on the classpath, LayeredMaterials.layeredFor(DIRT) must
     * still resolve to Layered Slopes' real registered block: proof LayeredSlopeGradingMod's own
     * static init -- not a copy living in some other, absent mod -- populated the registry.
     */
    @GameTest(template = "flat_platform")
    public static void layeredMaterialsResolvesVanillaDirtStandalone(GameTestHelper helper) {
        helper.assertTrue(LayeredMaterials.layeredFor(Blocks.DIRT) == LayeredSlopesMod.LAYERED_DIRT.get(),
                "LayeredMaterials.layeredFor(DIRT) must resolve standalone, with no Dynamic Terrain installed");
        helper.assertTrue(LayeredMaterials.layeredForSlab(Blocks.STONE_SLAB) == LayeredSlopesMod.LAYERED_STONE.get(),
                "LayeredMaterials.layeredForSlab(STONE_SLAB) must resolve standalone, with no Dynamic Terrain installed");
        helper.succeed();
    }

    /**
     * End-to-end real grade: a plain vanilla DIRT block (not a pre-placed LayeredBlock) actually
     * grades into its neighbour through Smoothing.smooth(), the same path GradingTool's
     * right-click interaction drives. This exercises registration, not just the pure SmoothRules
     * decision already covered by SmoothRulesTest.
     */
    @GameTest(template = "flat_platform")
    public static void realVanillaDirtBlockGradesIntoNeighbourStandalone(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 2, 1);
        BlockPos destPos = sourcePos.relative(Direction.EAST);
        BlockPos supportPos = destPos.below();

        helper.setBlock(sourcePos, Blocks.DIRT.defaultBlockState());
        helper.setBlock(destPos, Blocks.AIR.defaultBlockState());
        helper.setBlock(supportPos, Blocks.STONE.defaultBlockState());

        boolean moved = Smoothing.smooth(helper.getLevel(), helper.absolutePos(sourcePos), Direction.EAST);
        helper.assertTrue(moved, "a plain vanilla DIRT block must grade through Smoothing with no Dynamic Terrain installed");

        BlockState source = helper.getBlockState(sourcePos);
        BlockState dest = helper.getBlockState(destPos);
        helper.assertTrue(source.getBlock() == LayeredSlopesMod.LAYERED_DIRT.get(),
                "the source cell must become Layered Slopes' real LAYERED_DIRT after grading");
        helper.assertTrue(dest.getBlock() == LayeredSlopesMod.LAYERED_DIRT.get(),
                "the destination cell must become Layered Slopes' real LAYERED_DIRT after grading");
        helper.assertTrue(source.getValue(LayeredBlock.LAYERS) == 15, "source should have lost exactly one layer");
        helper.assertTrue(dest.getValue(LayeredBlock.LAYERS) == 1, "destination should have gained exactly one layer");
        helper.succeed();
    }
}
