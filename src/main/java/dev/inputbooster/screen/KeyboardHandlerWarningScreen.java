package dev.inputbooster.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Warning shown before the alpha keyboard handler is switched on.
 *
 * The handler is opt-in on purpose: it is the one part of InputBooster that
 * makes its own decision about where keyboard input goes, and an alpha build
 * can still get that wrong on a Minecraft version we have not run it on. The
 * screen has to be dismissed with an explicit click, so nobody enables it by
 * tapping a toggle they did not read.
 */
public class KeyboardHandlerWarningScreen extends Screen {

    private static final String[] LINES = {
        "§7This is the §cALPHA§7 keyboard handler.",
        "",
        "§7InputBooster stops borrowing Minecraft's key bindings and reads",
        "§7key state with its own handler instead. That fixes keys firing",
        "§7while you type, but alpha code can still misbehave on a",
        "§7Minecraft version or mod combination we have not tested.",
        "",
        "§7It does not change your vanilla bindings and it does not cancel",
        "§7another mod's input. Still: §eyou use it at your own risk.",
        "",
        "§7You can turn it off again from this screen at any time.",
    };

    private final Screen parent;
    private final Runnable onAccept;

    public KeyboardHandlerWarningScreen(Screen parent, Runnable onAccept) {
        super(Component.literal("§c§lAlpha feature"));
        this.parent = parent;
        this.onAccept = onAccept;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        addRenderableWidget(Button.builder(
            Component.literal("§cTurn it off"),
            button -> onClose()
        ).bounds(cx - 155, this.height - 40, 150, 20).build());

        addRenderableWidget(Button.builder(
            Component.literal("§aEnable anyway"),
            button -> {
                onAccept.run();
                if (this.minecraft != null) this.minecraft.setScreenAndShow(parent);
            }
        ).bounds(cx + 5, this.height - 40, 150, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Screen.extractRenderState already draws the background; blurring again
        // here triggers "Can only blur once per frame".
        int cx = this.width / 2;
        int y = 40;
        ctx.centeredText(this.font, this.title.getString(), cx, y, 0xFF5555);
        y += 18;
        for (String line : LINES) {
            if (!line.isEmpty()) {
                ctx.centeredText(this.font, line, cx, y, 0xFFFFFF);
            }
            y += 11;
        }
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreenAndShow(parent);
    }
}
