# ⚡ InputBooster

<div align="center">

![Version](https://img.shields.io/badge/version-2.0.0-blue.svg)
![Minecraft](https://img.shields.io/badge/minecraft-1.21.x-green.svg)
![Loader](https://img.shields.io/badge/loader-Fabric-orange.svg)
![Java](https://img.shields.io/badge/java-21+-red.svg)
![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)

**Ultra-fast input polling for competitive Minecraft PvP**  
Never miss a click again, even at low FPS 🎯

[Installation](#-installation) • [Features](#-features) • [Configuration](#%EF%B8%8F-configuration) • [How It Works](#-how-it-works)

![InputBooster Cycle](assets/inputbooster-cycle.png)

</div>

---

## 🤔 What is this?

InputBooster is a **client-side Fabric mod** that runs a high-frequency background thread (100-500Hz) to poll your keyboard and mouse **independently of your FPS**. 

When your game is running at 20 FPS, vanilla Minecraft only checks your inputs 20 times per second. With InputBooster, your inputs are captured up to **500 times per second** and queued to fire on the next game tick.

**The cycle shown above runs automatically** - no configuration needed. Just install and play!

---

## ✨ Features

<div align="center">

![InputBooster Features](assets/inputbooster-features.png)

</div>

### Core Systems Explained

- 🎯 **High-Frequency Input Polling** (100-500Hz)
  - Automatically scales based on your FPS
  - 20 FPS → 500Hz boost | 30 FPS → 350Hz boost
  - 60 FPS → 200Hz boost | 60+ FPS → 100Hz (maintenance mode)

- 🏃 **Sprint Fix Engine**
  - Re-asserts sprint state every tick
  - Prevents sprint-drops during lag spikes

- 👊 **W-Tap Assist**
  - Sub-frame W-release detection
  - Cleaner knockback combos

- 🎮 **Auto-Strafe Correction**
  - Fixes diagonal sprint speed loss
  - Maintains momentum at low FPS

- 📊 **Real-Time CPS Tracker**
  - Shows actual clicks-per-second in F3

- 🔄 **Anti-Idle Protection**
  - Prevents AFK kicks during lag spikes

### 🖥️ F3 Debug Integration

Press **F3** to see live mod status:

```
[InputBooster 2.0.0] ACTIVE
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

## 📥 Installation

### Requirements

- ✅ Minecraft **1.21.x**
- ✅ [Fabric Loader](https://fabricmc.net/use/) **0.16.0+**
- ✅ [Fabric API](https://modrinth.com/mod/fabric-api)
- ✅ Java **21+**

### Steps

1. Download the latest `.jar` from [Releases](https://github.com/ahaduzzamankhan/inputbooster/releases)
2. Place it in `.minecraft/mods/`
3. Launch Minecraft with Fabric
4. **That's it!** The mod is auto-enabled on launch

### Compatibility

✅ **Works with:**
- Sodium
- Iris
- Lithium
- OptiFabric
- Most performance mods

⚠️ **May conflict with:**
- Other input-modifying mods
- Macro/autoclicker mods

---

## ⚙️ Configuration

Config file: `.minecraft/config/inputbooster.properties`

```properties
# Polling rate (Hz) - higher = more responsive
# Range: 100-500 | Default: 200
poll_rate_hz=200

# Enable sprint-fix system
sprint_fix=true

# Enable auto-sprint (hold W = sprint)
auto_sprint=true

# Enable W-tap assist for combos
wtap_assist=true

# Enable anti-idle protection
anti_idle=true

# Enable auto-strafe correction
auto_strafe=true

# Show info in F3 debug screen
show_f3_info=true
```

**Note:** Changes apply after restarting Minecraft

---

## 🎮 How to Use

1. **Launch the game** - InputBooster is automatically active
2. **Press F3** to view live stats
3. **Adjust settings** in `config/inputbooster.properties` if needed
4. **Play normally** - the mod works silently in the background

### Tips for Best Results

- 💡 Use **Sodium** for FPS optimization first
- 💡 Set `poll_rate_hz=500` if you have FPS < 30
- 💡 Disable `auto_sprint` if you prefer vanilla sprint
- 💡 Check F3 stats to monitor "Recovered Inputs" count

---

## 🎯 Pros & Cons

<div align="center">

![Pros and Cons](assets/pros-cons.png)

</div>

### What This Means

✅ **InputBooster IS for:**
- Capturing missed clicks at low FPS
- Maintaining sprint consistency
- Improving combat responsiveness
- W-tap assistance for PvP

❌ **InputBooster CANNOT:**
- Boost your actual FPS (use Sodium/OptiFine)
- Fix server lag or TPS issues
- Reduce network latency or ping
- Auto-click or create macros (explicitly forbidden)

---

## 🔧 Building from Source

```bash
# Clone the repository
git clone https://github.com/ahaduzzamankhan/inputbooster.git
cd inputbooster

# Build the mod
./gradlew build

# Output: build/libs/inputbooster-2.0.0.jar
```

---

## 📜 Changelog

### [2.0.0] - 2026-03-27

**Major Release - Complete Rewrite**

#### 🎉 Why v2.0 and not v1.0?

Version 1.0 was **never publicly released** due to critical stability issues:
- Memory leaks in the polling thread
- Incompatibility with Sodium/Iris
- Config system crashes on some setups
- Sprint-fix causing movement bugs

**v2.0 represents a complete ground-up rewrite** with enterprise-grade architecture.

#### ✨ New Features
- Dynamic poll rate system (100-500Hz auto-scaling)
- F3 debug integration (no HUD overlay needed)
- Real-time CPS tracker
- Auto-strafe correction engine
- Anti-idle protection system
- Modular feature toggles in config

#### 🔧 Technical Improvements
- Rewrote input polling with lock-free queues
- Migrated to Mixin-based event interception
- Added graceful thread shutdown on world unload
- Implemented FPS-aware poll rate scaling
- Fixed all Sodium/Iris compatibility issues

#### 🐛 Bug Fixes
- Resolved sprint-drop at low FPS
- Fixed W-tap detection edge cases
- Eliminated memory leaks in background threads
- Corrected diagonal movement speed calculations
- Patched config file corruption issues

---

## 🤝 Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the **MIT License** - see [LICENSE](LICENSE) for details.

```
MIT License

Copyright (c) 2026 Ahaduzzaman Khan

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 🙏 Acknowledgments

- **Fabric Team** - For the excellent modding framework
- **Sodium Team** - For performance optimization inspiration
- **PvP Community** - For feedback and feature requests

---

## 📞 Support

- 🐛 **Bug Reports:** [GitHub Issues](https://github.com/ahaduzzamankhan/inputbooster/issues)
- 💬 **Discussions:** [GitHub Discussions](https://github.com/ahaduzzamankhan/inputbooster/discussions)
- 📦 **Modrinth:** [Download Page](https://modrinth.com/mod/inputbooster) ***Note:** You may not get this mod on modrinth yet. Now download from relases page. If it come to modrinth, the note will be remove.*

---

<div align="center">

**Made with ⚡ by [Ahaduzzaman Khan](https://github.com/ahaduzzamankhan)**

*If this mod helped you, consider starring the repo!* ⭐

</div>
