package dev.inputbooster.screen;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import dev.inputbooster.feature.ProfileManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Profile load, save and delete.
 *
 * <p>Split out of the settings screen so the seven main sections stay readable.
 * Layout mirrors the main panel: same palette, same row rhythm, one back button.
 */
public class ProfilesScreen extends Screen {

    private static final int PANEL_BG = 0xF0141820;
    private static final int BORDER   = 0xFF2A3540;
    private static final int MUTED    = 0xFF93A1AE;
    private static final int TEXT     = 0xFFE6EDF3;

    private final Screen parent;
    private int panelX, panelY, panelW, panelH;

    public ProfilesScreen(Screen parent) {
        super(Component.literal("InputBooster profiles"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = Math.min(440, this.width - 16);
        panelH = Math.min(300, this.height - 16);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        ProfileManager manager = InputBoosterMod.profileManager;
        int rowY = panelY + 52;

        if (manager == null) {
            addRenderableWidget(Button.builder(Component.literal("§cProfiles unavailable"), b -> {})
                .bounds(panelX + 14, rowY, panelW - 28, 20).build());
        } else {
            List<ProfileManager.Profile> profiles = manager.getProfiles();
            for (int i = 0; i < profiles.size() && i < 8; i++) {
                ProfileManager.Profile profile = profiles.get(i);
                final String name = profile.name();
                final int index = i;
                addRenderableWidget(Button.builder(Component.literal("§aLoad §f" + name), b -> {
                    manager.loadProfile(name, this.minecraft);
                    rebuildWidgets();
                }).bounds(panelX + 14, rowY, 220, 20).build());
                addRenderableWidget(Button.builder(Component.literal("§cDelete"), b -> {
                    manager.deleteProfile(index);
                    rebuildWidgets();
                }).bounds(panelX + panelW - 90, rowY, 76, 20).build());
                rowY += 24;
            }

            if (profiles.size() < ProfileManager.MAX_PROFILES) {
                rowY += 6;
                String[] quick = {"PvP", "Mining", "Idle"};
                int width = (panelW - 28 - 8) / quick.length;
                for (int i = 0; i < quick.length; i++) {
                    String name = quick[i];
                    addRenderableWidget(Button.builder(Component.literal("Save " + name), b -> {
                        manager.saveProfile(name);
                        rebuildWidgets();
                    }).bounds(panelX + 14 + i * (width + 4), rowY, width, 20).build());
                }
            }
        }

        addRenderableWidget(Button.builder(Component.literal("§7Back"), b -> onClose())
            .bounds(panelX + panelW / 2 - 50, panelY + panelH - 28, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xC00A0D11);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 1, BORDER);
        ctx.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, BORDER);
        ctx.fill(panelX, panelY, panelX + 1, panelY + panelH, BORDER);
        ctx.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, BORDER);

        ctx.text(this.font, "§b§lProfiles", panelX + 14, panelY + 10, TEXT);
        ctx.text(this.font, "§7" + describe(), panelX + 14, panelY + 26, MUTED);
        ctx.fill(panelX + 1, panelY + 40, panelX + panelW - 1, panelY + 41, 0x3355D6E8);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private String describe() {
        ProfileManager manager = InputBoosterMod.profileManager;
        if (manager == null) return "Mod is not initialised";
        int count = manager.getProfiles().size();
        return count == 0
            ? "No profiles saved yet. Save one below."
            : count + " saved profile(s), max " + ProfileManager.MAX_PROFILES;
    }

    @Override
    public void onClose() {
        InputBoosterConfig.save();
        if (this.minecraft != null) this.minecraft.setScreenAndShow(parent);
    }
}
