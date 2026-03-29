# ⚡ InputBooster

![Version](https://img.shields.io/badge/version-2.0.1-blue.svg)
![Minecraft](https://img.shields.io/badge/minecraft-1.21.x-green.svg)
![Loader](https://img.shields.io/badge/loader-Fabric-orange.svg)
![Java](https://img.shields.io/badge/java-21+-red.svg)
![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)

**Ultra-fast input polling for competitive Minecraft PvP — never miss a click again.**

---

## Overview

InputBooster is a client-side Fabric mod designed to solve one of the most frustrating problems in low-FPS Minecraft gameplay: missed inputs. In vanilla Minecraft, input polling is tied directly to your frame rate. If your game is running at 20 FPS, your keyboard and mouse are only checked 20 times per second — meaning fast clicks, sprint presses, or movement inputs can slip through entirely undetected.

InputBooster decouples input polling from the render loop by running a dedicated high-frequency background thread operating between 100Hz and 500Hz. Every input captured on this thread is queued and processed on the next available game tick, ensuring nothing is ever dropped — even during severe frame drops or lag spikes. The result is a noticeably more responsive experience in combat, movement, and general gameplay, without touching your FPS or server-side behavior.

The mod is fully automatic. There is no activation step, no keybind to toggle, and no complicated setup. Install it, launch the game, and it works silently in the background.

---

## How It Works

At its core, InputBooster introduces a secondary thread that runs independently of Minecraft's main game loop. This thread continuously polls the state of your input devices at a configurable rate, defaulting to 200Hz but scaling dynamically based on your current FPS:

| Client FPS | Poll Rate |
|------------|-----------|
| < 20 FPS   | 500 Hz    |
| ~30 FPS    | 350 Hz    |
| ~60 FPS    | 200 Hz    |
| 60+ FPS    | 100 Hz    |

When an input event is detected between frames, it is pushed into a thread-safe queue. On the next game tick, the main thread drains this queue and processes all buffered inputs in order. This means even a click that happened halfway between two frames will still fire correctly — something vanilla Minecraft simply cannot guarantee.

---

## Features

### High-Frequency Input Polling
The central feature of the mod. Input polling runs on a dedicated thread at 100–500Hz, completely independent of your render rate. Inputs are never skipped, never delayed beyond a single tick, and never dependent on your GPU or render pipeline.

### Sprint Fix Engine
Minecraft's sprint state is surprisingly fragile — it can drop silently during lag spikes, packet delays, or rapid direction changes. The Sprint Fix Engine re-asserts your sprint state every tick, ensuring you remain sprinting as long as the sprint key is held, regardless of what the server or client does internally.

### W-Tap Assist
W-tapping is a PvP technique that involves briefly releasing the forward key during combat to reset momentum and improve knockback trades. InputBooster's W-Tap Assist provides sub-frame detection of W-key releases, making the timing window more consistent and reliable without automating the action itself.

### Auto-Strafe Correction
At low FPS, diagonal movement can cause unintended speed loss due to how Minecraft calculates movement vectors per frame. Auto-Strafe Correction compensates for this by adjusting strafe inputs to maintain expected momentum, keeping your movement smooth even when frames are inconsistent.

### Real-Time CPS Tracker
InputBooster tracks your actual clicks per second and surfaces this data directly in Minecraft's F3 debug screen. No external tools or overlays required.

### Anti-Idle Protection
During extended lag spikes, Minecraft's idle detection can trigger AFK kicks even if you are actively playing. Anti-Idle Protection sends periodic synthetic input signals to prevent this from happening, keeping you connected during temporary performance issues.

### F3 Debug Integration
All mod statistics are visible live in the F3 debug screen:

```
[InputBooster 2.0.1] ACTIVE
Poll Rate   : 350 Hz (high boost)
Client FPS  : 28
Recovered   : 1,482 inputs
CPS         : 8
Sprint Fix  : ON  | Auto-Sprint : ON
W-Tap       : ON  | Auto-Strafe : ON
Anti-Idle   : ON
by Ahaduzzaman Khan
```

---

## Installation

### Requirements

| Dependency    | Version   |
|---------------|-----------|
| Minecraft     | 1.21.x    |
| Fabric Loader | 0.16.0+   |
| Fabric API    | Latest    |
| Java          | 21+       |

### Steps

1. Download the latest `.jar` from the [Releases](https://github.com/ahaduzzamankhan/inputbooster/releases) page
2. Place the file in your `.minecraft/mods/` directory
3. Launch Minecraft using the Fabric profile
4. The mod activates automatically — no additional steps required

### Mod Compatibility

**Compatible with:**
- Sodium
- Iris
- Lithium
- OptiFabric
- Most performance-oriented mods

**May conflict with:**
- Other input-modifying mods
- Macro or autoclicker mods

---

## Configuration

The configuration file is located at `.minecraft/config/inputbooster.properties` and is generated automatically on first launch.

```properties
# Polling rate in Hz — higher values improve responsiveness at low FPS
# Range: 100–500 | Default: 200
poll_rate_hz=200

# Re-assert sprint state every tick to prevent sprint drops
sprint_fix=true

# Hold W to sprint automatically, without tapping the sprint key
auto_sprint=true

# Enable sub-frame W-key release detection for W-tap timing
wtap_assist=true

# Prevent AFK kicks during lag spikes
anti_idle=true

# Correct diagonal movement speed loss at low FPS
auto_strafe=true

# Display live mod statistics in the F3 debug screen
show_f3_info=true
```

> **Note:** All configuration changes require a full Minecraft restart to take effect.

### Recommended Settings by Use Case

| Scenario              | Recommended Config                          |
|-----------------------|---------------------------------------------|
| FPS below 30          | `poll_rate_hz=500`                          |
| Vanilla sprint preference | `auto_sprint=false`                     |
| PvP-focused setup     | All features enabled, `poll_rate_hz=400+`   |
| Casual play           | Default config is sufficient                |

---

## Limitations

InputBooster is strictly a client-side input optimization tool. It does not and cannot:

- Increase your rendered frames per second (use Sodium for that)
- Compensate for server-side lag or low TPS
- Reduce network latency or ping
- Automate inputs, clicks, or actions of any kind

The mod captures inputs faster and more reliably — it does not generate them.

---

## Building from Source

```bash
git clone https://github.com/ahaduzzamankhan/inputbooster.git
cd inputbooster
./gradlew build
# Output: build/libs/inputbooster-2.0.1.jar
```

---

## Contributing

Contributions are welcome. To contribute:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m 'Add your feature'`
4. Push to the branch: `git push origin feature/your-feature`
5. Open a Pull Request for review

Please keep pull requests focused and well-described.

---

## Contributors

| Name | Role |
|------|------|
| [Ahaduzzaman Khan](https://github.com/ahaduzzamankhan) | Creator & Lead Developer |

---

## Support

| Channel      | Link |
|--------------|------|
| Bug Reports  | [GitHub Issues](https://github.com/ahaduzzamankhan/inputbooster/issues) |
| Discussions  | [GitHub Discussions](https://github.com/ahaduzzamankhan/inputbooster/discussions) |
| Download     | [Releases Page](https://github.com/ahaduzzamankhan/inputbooster/releases) |

> **Modrinth:** The mod is not yet listed on Modrinth. Download from the Releases page above. This note will be removed once the Modrinth listing goes live.

---

## Acknowledgments

- **Fabric Team** — For building and maintaining an excellent modding framework
- **Sodium Team** — For performance optimization work that inspired parts of this mod's design
- **PvP Community** — For ongoing feedback, testing, and feature suggestions

---

## License

This project is licensed under the **MIT License**. See [LICENSE](LICENSE) for full terms.

---

<div align="center">

**Made with ⚡ by [Ahaduzzaman Khan](https://github.com/ahaduzzamankhan)**

*If InputBooster improved your gameplay, consider starring the repository.* ⭐

</div>
