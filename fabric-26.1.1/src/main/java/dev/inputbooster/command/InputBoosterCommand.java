package dev.inputbooster.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import dev.inputbooster.compat.sodium.SodiumIntegration;
import dev.inputbooster.feature.OverlayLayout;
import dev.inputbooster.feature.OverlayRenderer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The {@code /inputbooster} client command.
 *
 * <p>It reports what the mod found at startup and it can drive the overlay,
 * which is the quickest way to find out whether the overlay is drawn at all
 * and in the right corner:
 *
 * <pre>
 * /inputbooster                    status of the mod and of the overlay
 * /inputbooster overlay on         show the overlay
 * /inputbooster overlay off        hide the overlay
 * /inputbooster overlay test       draw the bright test rectangle
 * /inputbooster overlay position   move the overlay to another corner
 * </pre>
 */
public final class InputBoosterCommand {

    private InputBoosterCommand() {}

    /** Registers the command with the client command dispatcher. */
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
                dispatcher.register(ClientCommands.literal("inputbooster")
                        .executes(InputBoosterCommand::status)
                        .then(ClientCommands.literal("overlay")
                                .then(ClientCommands.literal("on")
                                        .executes(ctx -> setOverlay(ctx.getSource(), true)))
                                .then(ClientCommands.literal("off")
                                        .executes(ctx -> setOverlay(ctx.getSource(), false)))
                                .then(ClientCommands.literal("test")
                                        .executes(ctx -> toggleTestRect(ctx.getSource())))
                                .then(ClientCommands.literal("position")
                                        .then(ClientCommands.argument("corner", StringArgumentType.word())
                                                .executes(ctx -> setPosition(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "corner"))))))));
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        source.sendFeedback(Component.literal("InputBooster " + InputBoosterMod.MOD_VERSION
                + " on Minecraft " + InputBoosterMod.minecraftVersion()
                + " (" + SodiumIntegration.integrationPath() + ")"));
        source.sendFeedback(Component.literal("Sodium: " + SodiumIntegration.describe()));
        source.sendFeedback(Component.literal("Overlay: "
                + (InputBoosterConfig.isShowF3Info() ? "on" : "off")
                + " at " + OverlayLayout.positionName(InputBoosterConfig.getOverlayPosition())
                + ", opacity " + Math.round(InputBoosterConfig.getOverlayOpacity() * 100) + "%"
                + ", scale " + Math.round(InputBoosterConfig.getOverlayScale() * 100) + "%"
                + (InputBoosterConfig.isOverlayTestRect() ? " [test rectangle]" : "")));
        return 1;
    }

    private static int setOverlay(FabricClientCommandSource source, boolean on) {
        InputBoosterConfig.setShowF3Info(on);
        InputBoosterConfig.save();
        OverlayRenderer.invalidate();
        source.sendFeedback(Component.literal("Overlay " + (on ? "enabled" : "disabled")));
        return 1;
    }

    private static int toggleTestRect(FabricClientCommandSource source) {
        boolean value = !InputBoosterConfig.isOverlayTestRect();
        InputBoosterConfig.setOverlayTestRect(value);
        InputBoosterConfig.save();
        OverlayRenderer.invalidate();
        source.sendFeedback(Component.literal("Overlay test rectangle " + (value ? "enabled" : "disabled")));
        return 1;
    }

    private static int setPosition(FabricClientCommandSource source, String corner) {
        int position = switch (corner.toLowerCase(Locale.ROOT)) {
            case "top-left", "topleft", "tl" -> OverlayLayout.TOP_LEFT;
            case "top-right", "topright", "tr" -> OverlayLayout.TOP_RIGHT;
            case "bottom-left", "bottomleft", "bl" -> OverlayLayout.BOTTOM_LEFT;
            case "bottom-right", "bottomright", "br" -> OverlayLayout.BOTTOM_RIGHT;
            case "top-center", "topcentre", "tc" -> OverlayLayout.TOP_CENTER;
            default -> -1;
        };
        if (position < 0) {
            source.sendError(Component.literal("Unknown corner '" + corner
                    + "'. Use top-left, top-right, top-center, bottom-left or bottom-right."));
            return 0;
        }
        InputBoosterConfig.setOverlayPosition(position);
        InputBoosterConfig.save();
        OverlayRenderer.invalidate();
        source.sendFeedback(Component.literal("Overlay moved to " + OverlayLayout.positionName(position)));
        return 1;
    }
}
