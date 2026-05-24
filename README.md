[![GitHub Stats](https://github-readme-stats.vercel.app/api?username=Ahaduzzamankhan&show_icons=true&theme=radical)](https://github.com/Ahaduzzamankhan))

# InputBooster

InputBooster is a client-side Fabric mod for players who want cleaner input during PvP, survival, and low-FPS gameplay. It keeps your clicks and movement keys responsive when frames dip, shows useful live stats, and gives you simple tools for profiles, CPS control, and replay testing.

This build is for Minecraft 1.21.11.

## What It Does

Minecraft normally checks input as part of the client loop. When your FPS drops, fast clicks and key taps can feel late or inconsistent. InputBooster adds a high-frequency input layer that watches important controls, queues clean press events, and applies them safely on the game tick.

The goal is simple: fewer missed clicks, smoother movement timing, and better feedback while you play.

## Feature Showcase

### Faster Input Feel

InputBooster polls attack, use, sprint, sneak, jump, movement, drop, swap, and pick-block inputs at a higher rate than normal frame timing. This helps short taps register even when your game stutters.

### PvP-Friendly Combat Tools

The built-in CPS limiter can run in multiple modes:

- `FIXED`: steady max CPS cap
- `HUMANIZED`: lightly varies the limit so clicks feel less robotic
- `COOLDOWN`: spaces clicks with a minimum delay
- `WEAPON_AWARE`: keeps combat CPS more conservative

The entity double-hit protection is also active, so one physical click should not become two entity attacks from the mod and vanilla at the same time.

### Movement Helpers

InputBooster includes sprint support, W-tap timing support, auto-strafe correction, and anti-idle protection. These are designed to improve input consistency without playing the game for you.

### Profiles

Save different settings for different play styles. For example:

- PvP
- Survival
- Low FPS
- Debug
- Balanced

The new per-server profile backend can auto-switch profiles based on the server you join.

### Input Replay

Replay tools help test whether input timing is working correctly.

Default keybinds:

- `R`: start or stop replay recording
- `K`: play the recorded input replay
- `O`: open InputBooster settings
- `P`: toggle InputBooster on or off

You can change these in Minecraft's keybind menu.

### Live Overlay

The in-game overlay can show:

- InputBooster version
- Poll rate
- FPS
- CPS
- Recovered inputs
- Latency stats
- Active module count
- Replay status
- Safe mode status
- Latest debug event when debug mode is enabled

### Safe Mode

If the mod sees repeated internal errors, safe mode can disable active modules instead of letting errors repeat during gameplay.

### Keybind Conflict Warnings

InputBooster can scan your keybinds and log conflicts so you can spot controls that share the same key.

## Installation

1. Install Fabric Loader for Minecraft 1.21.11.
2. Install Fabric API.
3. Put `inputbooster-3.0.1.jar` in your `.minecraft/mods` folder.
4. Launch the Fabric profile.

## Requirements

- Minecraft Java Edition 1.21.11
- Fabric Loader
- Fabric API
- Java 21 or newer

## Latest Version

Current version: `3.0.1`

The built jar is created at:

`build/libs/inputbooster-3.0.1.jar`

## Notes

InputBooster is client-side. It does not need to be installed on a server.

Some multiplayer servers have strict rules about client mods. Check the server rules before using any PvP utility mod.
