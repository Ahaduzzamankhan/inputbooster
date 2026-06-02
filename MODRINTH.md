# InputBooster

InputBooster is a client-side Fabric mod made for players who want more reliable clicks and key presses during PvP, survival, and low-FPS gameplay.

When Minecraft stutters, short clicks and quick key taps can feel inconsistent. InputBooster helps by watching important inputs at a higher rate, queueing clean events, and applying them safely on the game tick.

## Highlights

- High-frequency input polling for attack, use, movement, sprint, sneak, jump, drop, swap, and pick-block.
- Cleaner PvP click handling with entity double-hit protection.
- CPS limiter with multiple modes: fixed, humanized, cooldown, and weapon-aware.
- Sprint, W-tap, auto-strafe, and anti-idle helpers.
- Live overlay with FPS, poll rate, CPS, recovered inputs, latency, module status, replay status, and safe mode state.
- Profiles for different play styles.
- Per-server profile backend.
- Input replay recording and playback for testing.
- Safe mode for repeated internal errors.
- Keybind conflict warnings.

## Default Keybinds

- `O`: open InputBooster settings
- `P`: toggle InputBooster
- `R`: start or stop input replay recording
- `K`: play recorded input replay

All keybinds can be changed in Minecraft's controls menu.

## Requirements

- Minecraft 26.1.x
- Fabric Loader
- Fabric API
- Java 25 or newer

## Version

Latest build: `3.0.2-alpha02`

Download/use: `inputbooster-3.0.2-alpha02.jar`

## Important

InputBooster is client-side only. Servers do not need to install it.

Always check server rules before using client-side PvP or input utility mods.
