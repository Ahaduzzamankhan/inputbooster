# Changelog

## 3.1.0 - Minecraft 26.2 (NeoForge) / 26.2 + 26.3 (Fabric)

The mod core is now loader-neutral: one implementation under `src/main/java`, with a
small platform entrypoint per loader and a per-version API shim under
`src/compat/`.

### Loader support

| Loader | Minecraft | Jar |
| ------ | --------- | --- |
| NeoForge | 26.2 | `inputbooster-3.1.0-nf-mc262.jar` |
| Fabric | 26.2 | `inputbooster-3.1.0-fabric-mc262.jar` |
| Fabric | 26.3 | `inputbooster-3.1.0-fabric-mc263.jar` |

### Added

- Fabric support for Minecraft 26.2 and 26.3 (Fabric Loader 0.19+, Fabric API 0.161+).
- Per-version compatibility shim (`dev.inputbooster.compat.McVersion`) for the
  input / swing / drop API differences between 26.2 and 26.3.
- 24 JUnit regression tests run by CI (`fabric/` build) covering the queue, config,
  CPS limiter, latency profiler, session stats and combo keys.

### Fixed

- **Key bindings were never registered**: the mappings were created in the client
  setup callback, which runs *after* the key-mapping registration event, so the
  R / K bindings were silently missing from the controls screen.
- **Unbounded input backlog**: `InputActionQueue.clear()` reset the counter while
  the polling thread could be mid-enqueue, so the queue could grow past its bound.
- **Ctrl+1..5 re-applied the poll rate every tick** while the digit was held, because
  the key latch was written after the loop `break`.
- **Config reload leaked stale settings**: `load()` only assigned keys present in the
  file, so importing a partial config kept the previous session's values.
- **Hand-edited config booleans disabled features**: only lowercase `true` was
  accepted; `True`, `yes`, `1` and `on` now parse correctly, garbage falls back to
  the default.
- **CPS sparkline froze after a stall**: bucket rotation used the wall clock and
  advanced at most one bucket per tick, so a gap longer than a second left the graph
  stale. It is now monotonic and closes every elapsed second.
- **Latency average included stale samples** once the ring buffer wrapped.
- **The F3 overlay never appeared**: its visibility check was inverted and it bailed
  out exactly when the debug screen was open.

### Changed

- Ported to the 26.x client API: `setScreenAndShow`, `Gui.screen()`,
  `Player.sendOverlayMessage`, `Minecraft.getDebugOverlay()`, deferred
  `Gui.extractRenderState` HUD rendering, `startAttack` / `startUseItem` mixin hooks,
  and SDL-backed key state through `InputConstants` (LWJGL/GLFW is no longer part of
  the game distribution).

## 3.0.2-rl1 - Minecraft 1.21.11 (Production Release)

This is the final stable production release of InputBooster v3.0.2. This update fixes several critical thread-safety and performance bugs, ensuring a rock-solid, production-grade gameplay experience.

### Added

- Full profile serialization support for the new Keystrokes visualizer configuration.

### Fixed

- **CRITICAL THREAD-SAFETY FIX**: Fully synchronized the list in `ReplayRecorder` and declared control flags volatile, preventing concurrent modification exceptions, memory torn-reads, and crashes caused by thread race conditions between the main Minecraft rendering thread and the high-frequency polling thread.
- **CRITICAL CPS GRAPH FIX**: Resolved a bug in `SessionStats` where the CPS sparkline was calculating cumulative values 20 times too high (due to rolling totals multiplied by ticks per second). Now accurately calculates CPS delta per second.
- **PERFORMANCE FIX**: Replaced standard `O(N)` list size operations in `EventLog` with a thread-safe `O(1)` AtomicInteger counter, eliminating log polling overhead during busy PvP sessions.

## 3.0.2-beta02 - Minecraft 1.21.11

This update resolves the keybind layout as requested by transitioning settings access to the vanilla Minecraft Options screen, introduces a premium Keystrokes Visualizer HUD element, and resolves a critical CPS Limiter bypass bug.

### Added

- A new **Keystrokes Visualizer** inside the HUD overlay displaying in real-time the state of forward/left/back/right movement keys, LMB, RMB, and Spacebar. Toggled via the Advanced Settings tab.
- Integrated **"InputBooster..." Options button** in the top-right corner of the standard Minecraft `OptionsScreen` for quick and elegant access.

### Changed

- Disabled default keybind mapping for settings screen opening (set `GLFW_KEY_O` to `GLFW_KEY_UNKNOWN`) to keep options clean and accessible without key overlaps.
- Mod version advanced to `3.0.2-beta02` and display version to `3.0.2-beta02-mc26`.

### Fixed

- **CRITICAL BUG FIX**: Resolved a CPS Limiter bypass bug where attacks blocked by the limiter were still registered by Minecraft's vanilla mouse click listener, completely bypassing the limiter cap. Set `attackHandledThisTick = true` on blocked attacks to successfully suppress vanilla handling.

## 3.0.2-beta01 - Minecraft 1.21.11

This update focuses on making InputBooster feel more complete for everyday players while keeping the mod stable during combat and low-FPS gameplay.

### Added

- Module system for combat, movement, debug, profiles, anti-idle, and replay features.
- Per-server profile backend so settings can switch automatically for different servers.
- Input replay tools:
  - `R` starts or stops replay recording.
  - `K` plays the recorded replay.
- Advanced CPS limiter modes:
  - `FIXED`
  - `HUMANIZED`
  - `COOLDOWN`
  - `WEAPON_AWARE`
- Safe mode that disables active modules after repeated internal errors.
- Keybind conflict detection with event-log reporting.
- Event log backend for recent InputBooster activity.
- Config tools for presets, import, and export.
- Overlay lines for modules, replay status, safe mode, and latest debug event.

### Changed

- Updated mod version to `3.0.2-beta01`.
- Profile saves now include the new CPS mode, replay, safe mode, event log, keybind warning, and per-server profile settings.
- CPS limiter now uses the selected CPS mode instead of only a fixed cap.

### Fixed

- Prevented queued mod attacks from firing while InputBooster is inactive or not initialized.
- Prevented stale queued inputs from firing later after the mod is toggled back on.
- Reduced the chance of one physical click causing both a mod entity attack and a vanilla entity attack.

### Build

- New jar: `build/libs/inputbooster-3.0.2-beta01.jar`
