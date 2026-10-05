package dev.inputbooster;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the navigation chain and the shape of the settings GUI.
 *
 * <p>The required chain is <em>Minecraft Options → InputBooster → InputBooster
 * GUI</em>. Up to 3.1.7 the entry was a button hard-coded at
 * {@code width - 110, 6}: it floated on top of the options header, was not part
 * of the screen layout, and the settings screen behind it was a hand-drawn panel
 * with its own tabs, sliders and pixel maths.
 *
 * <p>4.0.0 replaces both halves:
 * <ul>
 *   <li>the entry is handed to the options screen's own
 *       {@code HeaderAndFooterLayout} and laid out by it;</li>
 *   <li>the settings screen extends {@code OptionsSubScreen} — the base class
 *       every vanilla options page uses — so it inherits the title header, the
 *       Done footer and the scrollable options list that reflows with the
 *       window.</li>
 * </ul>
 */
class OptionsNavigationTest {

    private static final String SCREEN = "src/main/java/dev/inputbooster/screen/InputBoosterScreen.java";
    private static final String OPTIONS_MIXIN = "src/main/java/dev/inputbooster/mixin/OptionsScreenMixin.java";
    private static final String LANG = "src/main/resources/assets/inputbooster/lang/en_us.json";

    private static String source(String relative) throws Exception {
        Path path = Path.of("..", relative);
        assertTrue(Files.exists(path), "expected source file " + path.toAbsolutePath());
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static JsonObject languageFile() throws Exception {
        return JsonParser.parseString(source(LANG)).getAsJsonObject();
    }

    // ── Navigation chain ────────────────────────────────────────────────────

    @Test
    void optionsScreenAddsTheInputBoosterEntry() throws Exception {
        String mixin = source(OPTIONS_MIXIN);
        assertTrue(mixin.contains("@Mixin(OptionsScreen.class)"),
            "the Options entry must be injected into the vanilla options screen");
        assertTrue(mixin.contains("@Inject(method = \"init\""),
            "the entry has to be added once the options screen has built its layout");
    }

    @Test
    void theEntryIsLaidOutByMinecraftInsteadOfFloatingOverTheHeader() throws Exception {
        String mixin = source(OPTIONS_MIXIN);

        // The regression: a widget placed at hard-coded coordinates is drawn on
        // top of the options header and is never re-positioned on resize.
        assertFalse(mixin.contains(".bounds("),
            "the Options entry must not be placed with hard-coded bounds; hand it to the screen layout");
        assertFalse(Pattern.compile("this\\.width\\s*-\\s*\\d+").matcher(mixin).find(),
            "the Options entry must not be positioned relative to this.width; that is the old "
                + "floating top-right button");

        assertTrue(mixin.contains("addToContents"),
            "the entry must be given to the options screen's HeaderAndFooterLayout");
        assertTrue(mixin.contains("InputBoosterScreen::anchorEntryToContentBottom"),
            "the entry's placement lives in one place on the GUI class; the mixin only wires it up");
        assertTrue(mixin.contains("repositionElements"),
            "the layout has to be arranged again so the new child gets its rectangle");
    }

    @Test
    void theEntryOpensTheInputBoosterScreen() throws Exception {
        String mixin = source(OPTIONS_MIXIN);
        assertTrue(mixin.contains("new InputBoosterScreen("),
            "the Options entry must open the InputBooster GUI");
        assertTrue(mixin.contains("setScreenAndShow"),
            "screens must be opened through the client's setScreenAndShow");
    }

    @Test
    void theEntryIsLabelledThroughTheLanguageFile() throws Exception {
        JsonObject lang = languageFile();
        assertTrue(lang.has("options.inputbooster.button"),
            "the Options entry needs a translation key, not a hard-coded string");
        assertTrue(lang.get("options.inputbooster.button").getAsString().contains("InputBooster"),
            "the entry has to say which mod it belongs to");
    }

    // ── GUI structure ───────────────────────────────────────────────────────

    @Test
    void theScreenIsBuiltOnTheVanillaOptionsBaseClass() throws Exception {
        Class<?> screen = Class.forName("dev.inputbooster.screen.InputBoosterScreen", false,
            OptionsNavigationTest.class.getClassLoader());
        Class<?> base = Class.forName("net.minecraft.client.gui.screens.options.OptionsSubScreen", false,
            OptionsNavigationTest.class.getClassLoader());
        assertTrue(base.isAssignableFrom(screen),
            "the GUI must extend OptionsSubScreen so it inherits the vanilla title, footer and "
                + "reflowing options list instead of reimplementing them");

        Method addOptions = findDeclared(screen, "addOptions");
        assertNotNull(addOptions, "the screen must populate the vanilla options list");
        assertTrue(Modifier.isProtected(addOptions.getModifiers()),
            "addOptions is the OptionsSubScreen extension point");
    }

    @Test
    void theScreenContainsNoHandRolledPanelOrTabBar() throws Exception {
        String screen = source(SCREEN);

        // The old GUI drew its own panel, its own tab row and its own geometry.
        for (String leftover : List.of("drawPanel", "TAB_LABELS", "TAB_COUNT", "currentTab")) {
            assertFalse(screen.contains(leftover),
                "the hand-rolled tab bar and panel are gone; found leftover '" + leftover + "'");
        }
        assertFalse(screen.contains("Panel"),
            "the GUI must use the vanilla options list, not a self-drawn panel");
    }

    @Test
    void theScreenUsesVanillaLayoutAndListApis() throws Exception {
        String screen = source(SCREEN);
        for (String api : List.of(
            "OptionsSubScreen",
            "list.addBig",
            "list.addSmall",
            "list.addHeader",
            "AbstractSliderButton",
            "Checkbox.builder",
            "CycleButton",
            "Button.builder",
            "StringWidget",
            "Tooltip.create")) {
            assertTrue(screen.contains(api),
                "the redesigned GUI is expected to use the native widget API " + api);
        }
    }

    @Test
    void theEntryJoinsTheOptionsGridInsteadOfOverlappingIt() throws Exception {
        String screen = source(SCREEN);
        assertTrue(screen.contains("findOptionsGrid"),
            "the GUI locates the options grid so the entry can be one of its cells");
        assertTrue(screen.contains("addToOptionsGrid"),
            "the entry must be added as a real grid cell; the grid then grows by a row and re-centres");
        assertTrue(screen.contains("grid.addChild(entry, row, column"),
            "the cell is placed with the grid's own addChild so it cannot collide with a vanilla button");
        assertTrue(screen.contains("anchorEntryToContentBottom"),
            "a fallback placement is kept for a future options screen without a grid");

        String mixin = source(OPTIONS_MIXIN);
        assertTrue(mixin.contains("InputBoosterScreen.findOptionsGrid")
                && mixin.contains("InputBoosterScreen.addToOptionsGrid"),
            "the mixin wires the entry into that grid");
    }

@Test
    void theEntryIsNeverPositionedByAConstantOffset() throws Exception {
        // 4.0.0-alpha first anchored the entry at the bottom of the content
        // area. On a short window that drew it straight over the last vanilla
        // row, which is what the player reported. The cell is now derived from
        // the grid's own contents, so it also survives a Minecraft version that
        // adds or removes an options button.
        String screen = source(SCREEN);
        assertTrue(screen.contains("leftEdges.add(element.getX())")
                && screen.contains("existing.size() / columns"),
            "the grid cell must be measured from the grid, not hard-coded to a row/column");
        assertFalse(Pattern.compile("addChild\\(entry, \\d+, \\d+").matcher(screen).find(),
            "a literal row/column would be wrong as soon as vanilla changes the number of "
                + "options buttons");
    }

    @Test
    void noWidgetIsTallerThanAnOptionsListRow() throws Exception {
        // OptionsList is constructed with a fixed item height of 25, and
        // Entry#extractContent places every widget at the row's content top
        // without ever looking at the widget's own height. Anything taller than
        // the 21px of usable row height is therefore drawn straight over the
        // rows below it. A multi-line text block was exactly that bug.
        String screen = source(SCREEN);
        assertFalse(screen.contains("MultiLineTextWidget"),
            "a multi-line text block cannot fit a fixed 25px options row and would be drawn over "
                + "the rows below it; use one StringWidget per row");

        // The one custom widget that is not a vanilla control must be sized
        // from the shared constant rather than a literal, so the constraint is
        // stated in one place.
        assertTrue(screen.contains("ROW_CONTENT_HEIGHT = 20"),
            "the usable row height must be declared once; it is 20 of the 25px row");
        assertTrue(screen.contains("super(0, 0, Button.BIG_WIDTH, ROW_CONTENT_HEIGHT, label)"),
            "the sparkline is the only custom widget and must use ROW_CONTENT_HEIGHT");
        assertFalse(Pattern.compile("super\\(0, 0, [A-Za-z.]+, \\d{2,}").matcher(screen).find(),
            "custom widgets must not hard-code a height taller than an options row");
    }

    @Test
    void noWidgetIsTallerThanAnOptionsListRow() throws Exception {
        // OptionsList is constructed with a fixed item height of 25, and
        // Entry#extractContent places every widget at the row's content top
        // without ever looking at the widget's own height. Anything taller than
        // the 21px of usable row height is therefore drawn straight over the
        // rows below it. A multi-line text block was exactly that bug.
        String screen = source(SCREEN);
        assertFalse(screen.contains("MultiLineTextWidget"),
            "a multi-line text block cannot fit a fixed 25px options row and would be drawn over "
                + "the rows below it; use one StringWidget per row");

        // The one custom widget that is not a vanilla control must be sized
        // from the shared constant rather than a literal, so the constraint is
        // stated in one place.
        assertTrue(screen.contains("ROW_CONTENT_HEIGHT = 20"),
            "the usable row height must be declared once; it is 20 of the 25px row");
        assertTrue(screen.contains("super(0, 0, Button.BIG_WIDTH, ROW_CONTENT_HEIGHT, label)"),
            "the sparkline is the only custom widget and must use ROW_CONTENT_HEIGHT");
        assertFalse(Pattern.compile("super\\(0, 0, [A-Za-z.]+, \\d{2,}").matcher(screen).find(),
            "custom widgets must not hard-code a height taller than an options row");
    }

    @Test
    void theScreenSavesTheConfigurationWhenItCloses() throws Exception {
        String screen = source(SCREEN);
        assertTrue(screen.contains("public void removed()"),
            "removed() is the one callback every exit path reaches, so the save has to be there");
        int removed = screen.indexOf("public void removed()");
        int save = screen.indexOf("InputBoosterConfig.save()", removed);
        assertTrue(save > removed && save < screen.indexOf("}", removed),
            "removed() must flush InputBoosterConfig before delegating to super");
        assertTrue(screen.contains("super.removed()"),
            "the vanilla superclass still has to save Minecraft's own options");
    }

    // ── Every setting is still reachable ────────────────────────────────────

    /** Every {@code isXxx}/{@code getXxx} toggle and slider the mod can expose. */
    private static final List<String> REQUIRED_SETTINGS = List.of(
        "isPollRateAutoMode", "getPollRateHz", "isBurstModeEnabled", "isComboKeysEnabled",
        "isSprintFixEnabled", "isAutoSprintEnabled", "isWTapAssistEnabled", "isAntiIdleEnabled",
        "isAutoStrafeEnabled", "isCpsLimiterEnabled", "getCpsMode", "getMaxCps",
        "isClickSoundsEnabled", "getClickSoundPitch", "getClickSoundVolume",
        "isShowF3Info", "isShowKeystrokes", "isShowActionBar", "getOverlayPosition",
        "getOverlayScale", "getOverlayOpacity",
        "isSafeModeEnabled", "isDebugMode", "isEventLogEnabled", "isKeyConflictWarn",
        "isReplayEnabled", "isPerServerProfiles", "getFpsCheckInterval"
    );

    @Test
    void everyConfigurableSettingIsStillReachableFromTheGui() throws Exception {
        String screen = source(SCREEN);
        List<String> missing = new ArrayList<>();
        for (String setting : REQUIRED_SETTINGS) {
            if (!screen.contains(setting + "(") && !screen.contains(setting)) {
                missing.add(setting);
            }
        }
        assertTrue(missing.isEmpty(),
            "the redesigned GUI must still expose every InputBooster setting; missing: " + missing);
    }

    @Test
    void theGuiKeepsStatsAndProfiles() throws Exception {
        String screen = source(SCREEN);
        for (String feature : List.of(
            "LatencyProfiler.resetPeak", "SessionStats", "ProfileManager",
            "saveProfile", "loadProfile", "deleteProfile")) {
            assertTrue(screen.contains(feature),
                "the GUI still has to offer " + feature);
        }
    }

    @Test
    void everyTranslationKeyUsedByTheScreenExists() throws Exception {
        JsonObject lang = languageFile();
        String screen = source(SCREEN);
        Matcher matcher = Pattern.compile("\"((?:inputbooster|options)[a-zA-Z0-9_.]*)\"").matcher(screen);

        List<String> missing = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        while (matcher.find()) {
            String key = matcher.group(1);
            seen.add(key);
            if (!lang.has(key)) missing.add(key);
        }
        assertTrue(seen.size() > 20,
            "the scan should find the GUI's translation keys but only found " + seen);
        assertTrue(missing.isEmpty(),
            "these translation keys are used by the GUI but absent from en_us.json: " + missing);
    }

    private static Method findDeclared(Class<?> type, String name) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)) return method;
        }
        return null;
    }
}