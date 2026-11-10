package io.github.brooswitminecraft.layeredslopegrading;

import io.github.brooswitminecraft.layeredslopes.LayeredSlopesMod;
import static io.github.brooswitminecraft.layeredslopes.LayeredSlopesMod.LAYERED_DIRT;
import static io.github.brooswitminecraft.layeredslopes.LayeredSlopesMod.LAYERED_GRASS;
import static io.github.brooswitminecraft.layeredslopes.LayeredSlopesMod.LAYERED_STONE;
import static io.github.brooswitminecraft.layeredslopes.LayeredSlopesMod.LAYERED_COBBLESTONE;
import static io.github.brooswitminecraft.layeredslopes.LayeredSlopesMod.LAYERED_SMOOTH_STONE;
import static io.github.brooswitminecraft.layeredslopes.LayeredSlopesMod.LAYERED_COBBLED_DEEPSLATE;
import static io.github.brooswitminecraft.layeredslopes.LayeredSlopesMod.LAYERED_COARSE_DIRT;
import static io.github.brooswitminecraft.layeredslopes.LayeredSlopesMod.LAYERED_PODZOL;
import static io.github.brooswitminecraft.layeredslopes.LayeredSlopesMod.LAYERED_MYCELIUM;

import java.util.List;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Entry point for the manual grading tool, extracted from Dynamic Terrain (MINECRAFT-511).
 * Registers the right-click-with-pickaxe grading interaction and the {@code /dtsmooth} debug
 * command. Requires Layered Slopes (MINECRAFT-499) for the layered block family this mod's
 * {@link Smoothing}/{@link SmoothRules}/{@link Settling}/{@link LayeredMaterials} code
 * operates on; never depends on Dynamic Terrain, which instead depends on this mod so its
 * erosion system ({@code Erosion.java}) can call the exact same {@link Smoothing#smooth}
 * mechanics with {@code disturb=true}.
 *
 * <p>MINECRAFT-511 correction (Jira comments 52878/52881 on this ticket): the vanilla-block to
 * layered-block {@link LayeredMaterials} registration below moved here from Dynamic Terrain's
 * {@code DynamicTerrainMod} -- this mod must resolve ordinary block/slab grading with Layered
 * Slopes alone, with no Dynamic Terrain on the classpath at all. Dynamic Terrain's own copy of
 * this static block was deleted (not kept as a duplicate) so the two can coexist without
 * double-registering.
 */
@Mod(LayeredSlopeGradingMod.MODID)
public class LayeredSlopeGradingMod {
    public static final String MODID = "layeredslopegrading";
    public static final Logger LOGGER = LogUtils.getLogger();

    static {
        LayeredMaterials.register(Blocks.DIRT, LAYERED_DIRT);
        LayeredMaterials.register(Blocks.SAND, LayeredSlopesMod.LAYERED_SAND);
        LayeredMaterials.register(Blocks.GRAVEL, LayeredSlopesMod.LAYERED_GRAVEL);
        LayeredMaterials.register(Blocks.STONE, LAYERED_STONE);
        LayeredMaterials.register(Blocks.COBBLESTONE, LAYERED_COBBLESTONE);
        LayeredMaterials.register(Blocks.SMOOTH_STONE, LAYERED_SMOOTH_STONE);
        LayeredMaterials.register(Blocks.DEEPSLATE, LayeredSlopesMod.LAYERED_DEEPSLATE);
        LayeredMaterials.register(Blocks.COBBLED_DEEPSLATE, LAYERED_COBBLED_DEEPSLATE);
        LayeredMaterials.register(Blocks.TERRACOTTA, LayeredSlopesMod.LAYERED_TERRACOTTA);
        LayeredMaterials.register(Blocks.RED_TERRACOTTA, LayeredSlopesMod.LAYERED_RED_TERRACOTTA);
        LayeredMaterials.register(Blocks.ORANGE_TERRACOTTA, LayeredSlopesMod.LAYERED_ORANGE_TERRACOTTA);
        LayeredMaterials.register(Blocks.YELLOW_TERRACOTTA, LayeredSlopesMod.LAYERED_YELLOW_TERRACOTTA);
        LayeredMaterials.register(Blocks.BROWN_TERRACOTTA, LayeredSlopesMod.LAYERED_BROWN_TERRACOTTA);
        LayeredMaterials.register(Blocks.WHITE_TERRACOTTA, LayeredSlopesMod.LAYERED_WHITE_TERRACOTTA);
        LayeredMaterials.register(Blocks.LIGHT_GRAY_TERRACOTTA, LayeredSlopesMod.LAYERED_LIGHT_GRAY_TERRACOTTA);
        LayeredMaterials.register(Blocks.MAGENTA_TERRACOTTA, LayeredSlopesMod.LAYERED_MAGENTA_TERRACOTTA);
        LayeredMaterials.register(Blocks.LIGHT_BLUE_TERRACOTTA, LayeredSlopesMod.LAYERED_LIGHT_BLUE_TERRACOTTA);
        LayeredMaterials.register(Blocks.LIME_TERRACOTTA, LayeredSlopesMod.LAYERED_LIME_TERRACOTTA);
        LayeredMaterials.register(Blocks.PINK_TERRACOTTA, LayeredSlopesMod.LAYERED_PINK_TERRACOTTA);
        LayeredMaterials.register(Blocks.GRAY_TERRACOTTA, LayeredSlopesMod.LAYERED_GRAY_TERRACOTTA);
        LayeredMaterials.register(Blocks.CYAN_TERRACOTTA, LayeredSlopesMod.LAYERED_CYAN_TERRACOTTA);
        LayeredMaterials.register(Blocks.PURPLE_TERRACOTTA, LayeredSlopesMod.LAYERED_PURPLE_TERRACOTTA);
        LayeredMaterials.register(Blocks.BLUE_TERRACOTTA, LayeredSlopesMod.LAYERED_BLUE_TERRACOTTA);
        LayeredMaterials.register(Blocks.GREEN_TERRACOTTA, LayeredSlopesMod.LAYERED_GREEN_TERRACOTTA);
        LayeredMaterials.register(Blocks.BLACK_TERRACOTTA, LayeredSlopesMod.LAYERED_BLACK_TERRACOTTA);
        LayeredMaterials.register(Blocks.RED_SAND, LayeredSlopesMod.LAYERED_RED_SAND);
        LayeredMaterials.register(Blocks.ICE, LayeredSlopesMod.LAYERED_ICE);
        LayeredMaterials.register(Blocks.PACKED_ICE, LayeredSlopesMod.LAYERED_PACKED_ICE);
        LayeredMaterials.register(Blocks.SNOW_BLOCK, LayeredSlopesMod.LAYERED_SNOW_BLOCK);
        LayeredMaterials.register(Blocks.COARSE_DIRT, LAYERED_COARSE_DIRT);
        LayeredMaterials.register(Blocks.PODZOL, LAYERED_PODZOL);
        LayeredMaterials.register(Blocks.MYCELIUM, LAYERED_MYCELIUM);
        LayeredMaterials.register(Blocks.MUD, LayeredSlopesMod.LAYERED_MUD);
        LayeredMaterials.register(Blocks.CLAY, LayeredSlopesMod.LAYERED_CLAY);
        LayeredMaterials.register(Blocks.SANDSTONE, LayeredSlopesMod.LAYERED_SANDSTONE);
        LayeredMaterials.register(Blocks.RED_SANDSTONE, LayeredSlopesMod.LAYERED_RED_SANDSTONE);
        LayeredMaterials.register(Blocks.TUFF, LayeredSlopesMod.LAYERED_TUFF);
        LayeredMaterials.register(Blocks.ANDESITE, LayeredSlopesMod.LAYERED_ANDESITE);
        LayeredMaterials.register(Blocks.GRANITE, LayeredSlopesMod.LAYERED_GRANITE);
        LayeredMaterials.register(Blocks.DIORITE, LayeredSlopesMod.LAYERED_DIORITE);
        LayeredMaterials.register(Blocks.NETHERRACK, LayeredSlopesMod.LAYERED_NETHERRACK);
        LayeredMaterials.register(Blocks.BLACKSTONE, LayeredSlopesMod.LAYERED_BLACKSTONE);

        LayeredMaterials.register(Blocks.GRASS_BLOCK, LAYERED_GRASS);
        // Soil blocks with a different top layer grade into plain layered dirt: the podzol or mycelium
        // top is lost with the first pull.
        for (Block soil : List.of(Blocks.ROOTED_DIRT)) {
            LayeredMaterials.register(soil, LAYERED_DIRT);
        }
        LayeredMaterials.registerSlab(Blocks.STONE_SLAB, LAYERED_STONE);
        LayeredMaterials.registerSlab(Blocks.COBBLESTONE_SLAB, LAYERED_COBBLESTONE);
        LayeredMaterials.registerSlab(Blocks.SMOOTH_STONE_SLAB, LAYERED_SMOOTH_STONE);
        LayeredMaterials.registerSlab(Blocks.COBBLED_DEEPSLATE_SLAB, LAYERED_COBBLED_DEEPSLATE);
        LayeredMaterials.joinGroup("soil", LAYERED_DIRT);
        LayeredMaterials.joinGroup("soil", LAYERED_GRASS);
        LayeredMaterials.joinGroup("soil", LAYERED_COARSE_DIRT);
        LayeredMaterials.joinGroup("soil", LAYERED_PODZOL);
        LayeredMaterials.joinGroup("soil", LAYERED_MYCELIUM);
        // Erosion disturbance settles grass as dirt instead of keeping a grass top
        // (see Smoothing's disturb flag); manual grading above is unaffected.
        LayeredMaterials.disturbsTo(LAYERED_GRASS, LAYERED_DIRT);
    }

    public LayeredSlopeGradingMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(GradingTool::onRightClickBlock);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Layered Slope Grading scaffold loaded");
    }

    /**
     * Debug entry point for smooth(): /dtsmooth <pos> <direction>. Op only. Keeps the literal
     * command name {@code dtsmooth} unchanged from Dynamic Terrain for compatibility
     * (MINECRAFT-511), even though the command now lives in this mod.
     */
    private void registerCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("dtsmooth")
                .requires(source -> source.hasPermission(2));
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> pos = Commands.argument("pos", BlockPosArgument.blockPos());
        for (Direction direction : Direction.values()) {
            pos.then(Commands.literal(direction.getName()).executes(context -> {
                BlockPos target = BlockPosArgument.getLoadedBlockPos(context, "pos");
                boolean moved = Smoothing.smooth(context.getSource().getLevel(), target, direction);
                context.getSource().sendSuccess(() -> Component.literal(
                        moved ? "Moved one layer " + direction.getName() : "Rejected: nothing moved"), true);
                return moved ? 1 : 0;
            }));
        }
        event.getDispatcher().register(root.then(pos));
    }
}
