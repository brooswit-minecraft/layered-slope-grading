package io.github.brooswitminecraft.layeredslopegrading;

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
 */
@Mod(LayeredSlopeGradingMod.MODID)
public class LayeredSlopeGradingMod {
    public static final String MODID = "layeredslopegrading";
    public static final Logger LOGGER = LogUtils.getLogger();

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
