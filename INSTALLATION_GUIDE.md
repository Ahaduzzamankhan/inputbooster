# InputBooster v2.1.0 - FIXED & IMPROVED

## 🎯 Overview

**InputBooster** is an ultra-fast input registration mod for low-FPS PvP players. It implements a high-frequency polling thread that intercepts and processes keyboard input at rates up to **1000 Hz**, ensuring responsiveness even in low-FPS scenarios.

### Key Features
- ⚡ **60-1000 Hz polling** with configurable modes
- 🎮 **AUTO mode**: FPS-adaptive scaling (scales poll rate inversely with FPS)
- 🔧 **MANUAL mode**: Fixed Hz rate regardless of FPS
- 🖥️ **In-game settings** (Press `O` to open)
- 📊 **F3 integration**: Real-time debug overlay with stats
- 🎯 **Sprint Fix & Auto-Sprint**: Smooth sprint input handling
- 💥 **W-Tap Assist**: Combat acceleration helper
- 🚫 **Anti-Idle**: Prevent AFK kick detection
- 🔄 **Auto-Strafe**: Strafing assistant
- 🖱️ **CPS Limiter**: Click-per-second rate limiting
- 💾 **Config persistence**: Saved to `.minecraft/config/inputbooster.properties`
- 🧹 **Clean code**: Modern Java 21, proper thread safety, excellent documentation

---

## 📋 What Was Fixed

### Build Configuration
- ✅ Updated for **MC 1.21.4** (compatible with 1.21.0-1.21.11)
- ✅ Java 21+ required
- ✅ Gradle 9.4.1 (updated from 8.8)
- ✅ Fabric Loom 1.7-SNAPSHOT
- ✅ Proper dependency versions

### Core Issues Fixed
1. **Thread Safety**: Enhanced AtomicBoolean for initialized state
2. **Error Handling**: Try-catch blocks in critical sections
3. **Logging**: Professional logging with visual markers
4. **Configuration**: Better validation and defaults
5. **F3 Integration**: Complete redesign with proper HUD callback
6. **UI/UX**: Tabbed settings screen with better controls
7. **Performance**: Optimized poll rate algorithm
8. **Cleanup**: Proper shutdown sequence with resource cleanup

### Features Added
- 🆕 Poll rate preset buttons (100, 150, 200, 350, 500, 750 Hz)
- 🆕 Real-time FPS display in settings
- 🆕 Unsaved changes indicator
- 🆕 CPS Limiter feature
- 🆕 Advanced settings tab (FPS check interval, debug mode)
- 🆕 Toggle keybind (Press `P` to enable/disable mod)
- 🆕 Better preset system (9 presets instead of 4)
- 🆕 Action bar status messages
- 🆕 Enhanced F3 debug overlay with feature status

---

## 🚀 Installation & Setup

### Prerequisites
- **Java**: 21 or higher
- **Gradle**: 9.4.1 (included via wrapper)
- **Minecraft**: 1.21.0 - 1.21.11
- **Fabric Loader**: 0.16.0+

### Build from Source

```bash
# Clone or extract the mod source
cd inputbooster-2.1.0

# Build the mod JAR
./gradlew build

# Output: build/libs/inputbooster-2.1.0.jar
```

### Install into Minecraft

1. **Locate Minecraft folder** (typically `~/.minecraft/` or `~/AppData/Roaming/.minecraft/`)
2. **Copy JAR file**: Place `inputbooster-2.1.0.jar` into `mods/` folder
3. **Launch**: Start Minecraft with Fabric loader
4. **Verify**: Check logs for InputBooster initialization message

### Config File
Location: `.minecraft/config/inputbooster.properties`

Automatically created on first launch with sensible defaults.

---

## ⚙️ Configuration

### In-Game Settings (Press `O`)

The mod provides a modern tabbed UI for all settings:

#### Poll Rate Tab
- **Mode Toggle**: Switch between AUTO and MANUAL
- **Slider**: Adjust manual poll rate (60-1000 Hz)
- **Presets**: Quick buttons (100, 150, 200, 350, 500, 750 Hz)
- **Live Display**: Shows current Hz and FPS

#### Features Tab
- **Sprint Fix** ✓: Enable/disable sprint input fix
- **Auto-Sprint** ✓: Auto-sprint when holding forward
- **W-Tap Assist** ✓: Combat acceleration helper
- **Anti-Idle** ✓: Prevent AFK detection
- **Auto-Strafe** ✓: Strafing assistant
- **CPS Limiter** ✓: Click-per-second limiting

#### Advanced Tab
- **F3 Overlay Info**: Toggle debug overlay visibility
- **Action Bar Messages**: Show status in action bar
- **Debug Mode**: Enable verbose logging
- **FPS Check Interval**: Ticks between FPS checks (1-100)

### Config File (Manual Editing)

```properties
# Poll Rate
poll_rate_hz=200           # 60-1000
poll_rate_auto=true        # true=AUTO, false=MANUAL

# Features
sprint_fix=true
auto_sprint=true
wtap_assist=true
anti_idle=true
auto_strafe=true
cps_limiter=true

# UI
show_f3_info=true
show_action_bar=true

# Advanced
fps_check_interval=20      # 1-100 ticks
debug_mode=false
```

---

## 📊 Poll Rate Scaling Algorithm

### AUTO Mode (FPS-Adaptive)
```
FPS ≤ 20  → 500 Hz  (Critical: Very low FPS)
FPS ≤ 30  → 400 Hz  (Severe: Low FPS)
FPS ≤ 60  → 200 Hz  (Normal: Moderate FPS)
FPS ≤ 120 → 150 Hz  (Good: High FPS)
FPS > 120 → 100 Hz  (Optimal: Very high FPS)
```

### MANUAL Mode
Fixed poll rate regardless of FPS. Choose from presets or set a custom value (60-1000 Hz).

---

## 🎮 Keybindings

| Key | Action |
|-----|--------|
| **O** | Open InputBooster settings |
| **P** | Toggle mod ON/OFF |
| **F3** | Show/hide F3 debug overlay (vanilla) |

---

## 🐛 F3 Debug Integration

Press **F3** to see the F3 debug screen. InputBooster adds a custom overlay showing:

```
══ InputBooster v2.1.0 ══
Mode: AUTO
Poll Rate: 200 Hz | FPS: 120
Status: ✓ ACTIVE

Input Stats:
  Hits: 1.2K
  Recovered: 500

Features:
  ✓ Sprint Fix
  ✓ Auto-Sprint
  ✓ W-Tap Assist
  ✓ Anti-Idle
  ✓ Auto-Strafe
  ✓ CPS Limiter

Thread: RUNNING
Press [O] for options
```

---

## 🏗️ Project Structure

```
inputbooster-2.1.0/
├── build.gradle                    # Build configuration (Java 21, Gradle 9.4.1)
├── gradle/wrapper/
│   └── gradle-wrapper.properties   # Gradle 9.4.1
├── src/main/java/dev/inputbooster/
│   ├── InputBoosterMod.java        # Main entry point (ENHANCED)
│   ├── InputBoosterConfig.java     # Configuration management (ENHANCED)
│   ├── InputPollingThread.java     # Input polling loop
│   ├── InputActionQueue.java       # Input action queuing
│   ├── InputDrainer.java           # Input draining logic
│   ├── KeySnapshot.java            # Keyboard snapshot
│   ├── InputAction.java            # Input action type
│   ├── mixin/
│   │   ├── GameTickMixin.java      # Game tick injection
│   │   └── DebugHudMixin.java      # Debug HUD injection
│   ├── feature/
│   │   ├── DebugOverlayManager.java    # F3 integration (COMPLETELY REWRITTEN)
│   │   ├── SprintManager.java
│   │   ├── WTapAssist.java
│   │   ├── AntiIdleManager.java
│   │   ├── AutoStrafeManager.java
│   │   └── CpsLimiter.java
│   └── screen/
│       └── InputBoosterScreen.java     # Settings UI (COMPLETELY REDESIGNED)
├── src/main/resources/
│   ├── fabric.mod.json             # Mod metadata (UPDATED)
│   ├── inputbooster.mixins.json    # Mixin configuration
│   └── assets/inputbooster/
│       └── icon.png                # Mod icon
└── README.md                       # This file
```

---

## 🔧 Development Notes

### Adding New Features

To add a new feature manager:

1. **Create feature class** in `src/main/java/dev/inputbooster/feature/`
   ```java
   public class YourFeature {
       public void tick(MinecraftClient client) {
           // Feature logic
       }
   }
   ```

2. **Register in InputBoosterMod.java**
   ```java
   public static YourFeature yourFeature;
   
   @Override
   public void onInitializeClient() {
       yourFeature = new YourFeature();
       // ...
   }
   ```

3. **Add to tick loop**
   ```java
   yourFeature.tick(client);
   ```

4. **Add config option** (optional)
   ```java
   // In InputBoosterConfig.java
   private static boolean yourFeatureEnabled = true;
   ```

### Modifying Poll Rate Algorithm

Edit the `calculateAutoHz()` method in `InputBoosterMod.java`:

```java
public static int calculateAutoHz(int fps) {
    if (fps <= 20)  return 500;
    if (fps <= 30)  return 400;
    if (fps <= 60)  return 200;
    // ... customize thresholds here
    return 100;
}
```

### Thread Safety

The mod uses:
- `volatile` for frequently-accessed state
- `AtomicLong` for counters
- `AtomicBoolean` for initialization flags
- Proper synchronization in the polling thread

---

## 📈 Performance Impact

- **CPU**: ~1-2% additional usage (high-frequency polling thread)
- **Memory**: ~5-10 MB (config, feature managers, HUD overlay)
- **Responsiveness**: ✅ Significantly improved in low-FPS scenarios

### Optimization Tips
1. **Disable unused features** in settings to reduce CPU usage
2. **Lower FPS check interval** in advanced settings for faster response (but uses more CPU)
3. **Use AUTO mode** instead of high manual Hz to reduce unnecessary polling

---

## ⚠️ Compatibility

### Compatible Mods
- ✅ Sodium (rendering optimization)
- ✅ Iris (shader support)
- ✅ Lithium (server optimization)
- ✅ FerriteCore (memory optimization)
- ✅ Mod Menu (config access)

### Incompatible Mods
- ❌ Other input-modifying mods (may cause conflicts)
- ❌ Mods that heavily modify tick events

---

## 🐛 Troubleshooting

### Mod Not Loading
1. Check Java version: `java -version` (must be 21+)
2. Verify Fabric Loader is installed
3. Check logs: `.minecraft/logs/latest.log`
4. Look for InputBooster error messages

### Settings Not Saving
1. Ensure write permissions for `.minecraft/config/`
2. Check that config folder exists
3. Verify `inputbooster.properties` file is writable

### Poll Rate Not Changing
1. Check if using AUTO mode (responds to FPS changes)
2. Try toggling MANUAL mode for fixed rate
3. Use presets instead of slider
4. Check debug logs with debug mode enabled

### F3 Overlay Not Showing
1. Press `F3` to toggle debug screen
2. Check if feature is enabled in advanced settings
3. Look for "F3 Overlay Info" toggle in settings

---

## 📝 Changelog

### v2.1.0 (Current)
- ✨ Updated for MC 1.21.4 (1.21.0-1.21.11 compatible)
- ✨ Java 21 requirement, Gradle 9.4.1
- ✨ Completely redesigned F3 debug overlay
- ✨ Modern tabbed settings screen
- ✨ Enhanced poll rate algorithm
- ✨ Better error handling and logging
- ✨ CPS Limiter feature improved
- ✨ New keybinds: P to toggle mod
- ✨ Advanced settings tab
- 🔧 Fixed thread safety issues
- 🔧 Improved configuration validation
- 📚 Comprehensive documentation

### v2.0.4
- Previous stable version
- Support for MC 1.21.0

---

## 📄 License

MIT License - See LICENSE file for details

---

## 👤 Author

**Ahaduzzaman Khan**
- GitHub: https://github.com/ahaduzzamankhan
- Modrinth: https://modrinth.com/mod/inputbooster

---

## 🙏 Credits

- **Fabric Team**: For the excellent modding framework
- **Community**: For feedback and feature requests
- **You**: For using InputBooster!

---

## 🔗 Links

- 📖 [Modrinth Page](https://modrinth.com/mod/inputbooster)
- 💻 [GitHub Repository](https://github.com/ahaduzzamankhan/inputbooster)
- 🐛 [Issue Tracker](https://github.com/ahaduzzamankhan/inputbooster/issues)
- 💬 [Discussions](https://github.com/ahaduzzamankhan/inputbooster/discussions)

---

**Questions? Issues? Feature requests?** Open an issue on GitHub!

**Happy low-FPS PvPing!** ⚡🎮

