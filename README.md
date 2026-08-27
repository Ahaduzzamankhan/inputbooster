# ⚡ InputBooster

<p align="center">
  <strong>High-frequency input handling for Minecraft Java Edition.</strong><br>
  Cleaner clicks • More consistent movement • Useful live diagnostics • Replay testing
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21.11-62b47a?style=for-the-badge&logo=minecraft&logoColor=white" alt="Minecraft 1.21.11">
  <img src="https://img.shields.io/badge/Fabric-Client--Side-DBD0B5?style=for-the-badge" alt="Fabric">
  <img src="https://img.shields.io/badge/Java-21%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21+">
  <img src="https://img.shields.io/badge/Version-3.0.2--beta01-8B5CF6?style=for-the-badge" alt="Version 3.0.2-beta01">
</p>

> **InputBooster** is a client-side Fabric mod focused on making keyboard and mouse input feel more consistent during PvP, survival, and low-FPS gameplay.

---

## ✨ Why InputBooster?

Minecraft normally processes input around the client/game loop. When frame rate drops or the game stutters, very short key presses and clicks can feel delayed or inconsistent.

InputBooster adds a dedicated input layer that can poll important controls at a higher frequency, queue clean input events, and safely apply them on the game tick.

**The goal:** better input consistency without turning the mod into an automated gameplay system.

---

## 🚀 Features

| Feature | What it does |
| --- | --- |
| ⚡ **High-frequency input** | Polls important mouse and keyboard actions more frequently than normal frame timing. |
| 🖱️ **CPS control** | Configurable click limiting with multiple modes. |
| ⚔️ **Combat helpers** | Includes entity double-hit protection and PvP-oriented input tools. |
| 🏃 **Movement helpers** | Sprint support, W-tap timing support, auto-strafe correction, and anti-idle protection. |
| 🎛️ **Profiles** | Save different configurations for PvP, Survival, Low FPS, Debug, and Balanced play. |
| 🌐 **Server profiles** | Automatically switch profiles based on the server you join. |
| 🎥 **Input Replay** | Record and replay input sequences for timing and debugging tests. |
| 📊 **Live overlay** | View FPS, CPS, poll rate, recovered inputs, latency, module count, and more. |
| 🛡️ **Safe Mode** | Can disable active modules after repeated internal errors. |
| 🔎 **Keybind diagnostics** | Detect and log keybind conflicts. |

---

## 🖱️ CPS Modes

InputBooster provides several CPS-limit modes:

- **`FIXED`** — keeps a steady maximum CPS limit.
- **`HUMANIZED`** — lightly varies the limit to avoid perfectly uniform timing.
- **`COOLDOWN`** — spaces clicks using a minimum delay.
- **`WEAPON_AWARE`** — uses a more conservative combat CPS limit.

> ⚠️ Multiplayer servers can have different rules for client-side utilities. Always check the server's rules before using InputBooster.

---

## 🎮 Movement Helpers

InputBooster can assist with **input consistency** through:

- Sprint support
- W-tap timing support
- Auto-strafe correction
- Anti-idle protection

These features are designed around input handling rather than automatically playing the game for you.

---

## 🎛️ Profiles

Create separate configurations for different situations:

```text
PvP
Survival
Low FPS
Debug
Balanced
```

The server-profile system can automatically select a configuration when you connect to a supported server.

---

## 🎥 Input Replay

Replay tools make it easier to test whether input timing behaves as expected.

### Default keybinds

| Key | Action |
| --- | --- |
| `R` | Start / stop replay recording |
| `K` | Play the recorded replay |
| `O` | Open InputBooster settings |
| `P` | Toggle InputBooster on / off |

All keybinds can be changed from Minecraft's normal keybind menu.

---

## 📊 Live Overlay

The in-game overlay can display:

- InputBooster version
- Poll rate
- FPS
- CPS
- Recovered inputs
- Latency statistics
- Active module count
- Replay status
- Safe Mode status
- Latest debug event when Debug Mode is enabled

This makes the overlay useful for both everyday gameplay and diagnosing input problems.

---

## 🛡️ Safe Mode

If InputBooster detects repeated internal errors, **Safe Mode** can disable active modules instead of allowing the same errors to continue during gameplay.

This provides an additional layer of protection while testing new configurations or features.

---

## 🔎 Keybind Conflict Detection

InputBooster can inspect Minecraft keybinds and log conflicts, making it easier to find controls that accidentally share the same key.

---

## 📦 Installation

### Requirements

- **Minecraft Java Edition:** `1.21.11`
- **Fabric Loader**
- **Fabric API**
- **Java:** `21+`

### Install

1. Install **Fabric Loader** for Minecraft `1.21.11`.
2. Install **Fabric API** for the same Minecraft version.
3. Download or build `inputbooster-3.0.2-beta01.jar`.
4. Place the JAR inside your Minecraft `mods` folder.
5. Start Minecraft using your Fabric profile.

The development build is produced at:

```text
build/libs/inputbooster-3.0.2-beta01.jar
```

---

## 🧑‍💻 Development

InputBooster is a Java-based Minecraft client mod built around Fabric's client-side modding environment.

The codebase separates important responsibilities into components such as:

```text
InputPollingThread
        ↓
   KeySnapshot
        ↓
 InputActionQueue
        ↓
   InputDrainer
        ↓
 Minecraft client tick
```

This architecture keeps high-frequency input collection separate from the safer game-tick application path.

---

## 🗂️ Project Structure

```text
src/main/java/dev/inputbooster/
├── InputBoosterMod.java       # Main mod entry point
├── InputBoosterConfig.java    # Configuration
├── InputPollingThread.java    # High-frequency input polling
├── InputAction.java           # Input action definitions
├── InputActionQueue.java      # Queued input events
├── InputDrainer.java          # Applies queued actions
├── KeySnapshot.java           # Input state snapshots
├── McCompat.java              # Minecraft compatibility helpers
└── feature/                   # Feature modules and diagnostics
```

---

## 🧪 Version

**Current build:** `3.0.2-beta01`

Minecraft target: **`1.21.11`**

> This is a **beta** build. Features and behavior may change as development continues.

---

## ⚠️ Multiplayer Notice

InputBooster is **client-side** and does not need to be installed on a server.

However, individual multiplayer servers may restrict or prohibit certain client modifications. **Read the rules of the server you play on before enabling InputBooster features.**

---

## 🤝 Contributing

Found a bug, have an improvement, or want to help develop InputBooster?

- Open an issue with clear reproduction steps.
- Include your Minecraft version and InputBooster version.
- For technical bugs, include relevant logs when possible.
- Keep pull requests focused and easy to review.

---

## ☕ Support InputBooster

If you find InputBooster useful, you can support the project through SupportKori:

```html
<script
  src="https://www.supportkori.com/widget.js"
  data-id="fluxenite"
  data-message="Buy me a coffee "
  data-color="#FFDD00"
  data-position="right"
></script>
```

> **Note:** GitHub README pages sanitize executable `<script>` tags, so this widget will be displayed as code rather than executed on GitHub. It should be placed on a website page that supports JavaScript if you want the floating widget to actually appear.

---

## 📌 Quick Summary

```text
InputBooster
├─ ⚡ High-frequency input
├─ 🖱️ CPS control
├─ 🏃 Movement helpers
├─ 🎛️ Profiles
├─ 🎥 Input replay
├─ 📊 Live diagnostics
├─ 🛡️ Safe Mode
└─ 🔎 Keybind conflict detection
```

**Built for players who want their inputs to feel more consistent — especially when Minecraft isn't running perfectly smoothly.**
