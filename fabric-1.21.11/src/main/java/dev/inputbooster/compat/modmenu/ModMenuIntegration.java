package dev.inputbooster.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.inputbooster.screen.InputBoosterVanillaScreen;

/**
 * The Mod Menu integration.
 *
 * <p>Mod Menu asks for the configuration screen of every mod through the
 * {@code modmenu} entrypoint. This class is only ever loaded when Mod Menu is
 * installed, because nothing else asks for that entrypoint, so the Mod Menu
 * API classes it references are never needed otherwise.
 *
 * <p>The screen it opens is the same one the vanilla options screen offers, so
 * there is only one settings screen and one source of truth for the
 * configuration, whether Mod Menu, Sodium or neither of them is installed.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return InputBoosterVanillaScreen::new;
    }
}
