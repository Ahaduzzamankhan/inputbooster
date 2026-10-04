# Changelog

## 3.1.3 - Loader compatibility fix (Fabric 26.2 + 26.3)

Every 3.1.2 Fabric jar crashed on launch with
`requires version [26.2,26.3) of 'Minecraft', but only the wrong version is present: 26.2!`
even though the game really was 26.2.

| Loader | Minecraft | Jar |
| ------ | --------- | --- |
| NeoForge | 26.2 | `inputbooster-3.1.3-nf-mc262.jar` |
| Fabric | 26.2 | `inputbooster-3.1.3-fabric-mc262.jar` |
| Fabric | 26.3 | `inputbooster-3.1.3-fabric-mc263.jar` |

### Fixed

- **Fabric Minecraft dependency range rejected the game version it was built for.**
  The mod metadata declared `"minecraft": "[26.2,26.3)"`. Fabric Loader 0.19.5
  parses a two-component version against that bracket range as `[26.2.0, 26.3.0)`
  and then fails the comparison, so the loader evaluated the predicate to `false`
  for `26.2`, `26.2.1` and `26.3` alike. The declared range is now the
  space-separated comparator form the loader evaluates correctly:
  `">=26.2 <26.3"` and `">=26.3 <26.4"`. Same semantics, accepted versions.

### Added

- **Regression test `FabricDependencyRangeTest`** that parses the generated
  `fabric.mod.json` and evaluates its `minecraft` predicate with the real Fabric
  loader classes, asserting that the target version is accepted, unrelated
  versions are rejected, and that no bracket-range form is ever emitted again.
- **CI metadata check.** The release workflow now unzips each produced jar and
  fails if its `fabric.mod.json` does not declare the comparator-style
  `"minecraft": ">=..."` range, so a broken dependency cannot ship again.

### Note

NeoForge's `[26.2,26.3)` range syntax is parsed by its own loader and is
unaffected by this bug; it is unchanged.

## 3.1.2 - Minecraft 26.2 (NeoForge) / 26.2 + 26.3 (Fabric)

Addresses the 15-point code review: duplicate-click suppression, CPS limiting,
replay/input separation, safe mode, the HUD overlay, mixin strictness and
configuration migration. Supersedes 3.1.1 (withdrawn).

| Loader | Minecraft | Jar |
| ------ | --------- | --- |
| NeoForge | 26.2 | `inputbooster-3.1.2-nf-mc262.jar` |
| Fabric | 26.2 | `inputbooster-3.1.2-fabric-mc262.jar` |
| Fabric | 26.3 | `inputbooster-3.1.2-fabric-mc263.jar` |

### Fixed

- **#1 Duplicate-click handling made tick-scoped (was the most serious issue).**
  `attackHandledThisTick` / `useHandledThisTick` were plain booleans cleared
  *inside* the injected vanilla methods. If vanilla stopped calling that method
  (changed input order, a click handled elsewhere, a paused game) the flag
  survived and cancelled a legitimate attack on a later tick. Each suppression is
  now a single-use token tagged with the tick id that issued it, and is expired
  automatically when that tick ends, so it can never leak across ticks.
- **#2 CPS limiter no longer rejects bursty but valid input.** The hard rolling
  window counted every attempt in the same second, so a fast 8-click burst
  followed by a pause still cost 8 tokens. The cap is now a token bucket that
  refills at `maxCps`/s and holds `maxCps`: the long-run average is still capped
  while short bursts pass. Cooldown mode keeps its minimum gap.
- **#3 `HUMANIZED` mode is no longer random per click** (already fixed in 3.1.1,
  kept): one cap per one-second window, derived deterministically from the window
  index so it varies naturally but stays consistent within the second.
- **#4 Replay is separated from physical input.** Queued events now carry an
  `InputAction.Origin` (`INPUT` / `REPLAY`). Playback waits while real input is
  pending in the queue, so a recording can no longer be injected on top of a live
  click and double an attack.
- **#5 Recording truncation is reported, not silent.** The buffer grew from 400
  to 4000 events; overflow is counted, logged when recording stops, and shown in
  the replay status line.
- **#6 Safe Mode is thread-safe.** Error counts, window starts and the disabled
  set moved to `ConcurrentHashMap` + `AtomicInteger` (already fixed in 3.1.1,
  kept), and safe mode never touches the mod-level `active` flag.
- **#7 Safe Mode disables only the failing module**, and only when the failing
  source maps to a real module, so its module set cannot grow with arbitrary
  error strings. The polling thread checks `active`/`initialized` and stops
  cleanly; disabled modules are simply not ticked.
- **#8 HUD overlay placeholders implemented.** `register()` documents that there
  is no separate registration step, `getDebugLines()` now returns real status
  lines (poll rate, queue depth, poller state, overlay position) and
  `isInitialized()` reports whether the overlay can actually draw.
- **#9 Overlay opacity 0 now skips rendering entirely** instead of queueing a
  fully transparent text state every frame.
- **#10 Options button uses a translation key** (`options.inputbooster.button`)
  instead of hard-coded `§b`, so the styling lives in the language file.
- **#11 Mixins are strict by default.** `defaultRequire` moved from `0` to `1`,
  so a mapping change fails loudly instead of silently disabling the feature.
  Critical injections (`tick`, `startAttack`, `startUseItem`, `close`) are
  `require = 1`; the two convenience hooks (HUD render state, options button)
  keep an explicit `require = 0` so they cannot stop the game from starting. A
  startup check still logs any vanilla method that is missing.
- **#12 Partially corrupt config files are reported.** Every key that had to fall
  back to its default is collected and logged in one warning instead of being
  silently reset field by field.
- **#13 Config migration implemented.** `config_version` is now `304` and
  `load()` runs a real migration step (legacy aliases `pollrate`, `watap`,
  `auto_sprint_key` are rewritten) before applying values.
- **#14 Polling thread runs at normal priority.** Removing `MAX_PRIORITY - 1`
  stops the poller from starving the render thread on low-end CPUs.
- **#15 Precise high-rate pacing.** `Thread.sleep` (millisecond-quantised and
  drift-prone) is replaced with `LockSupport.parkNanos` plus drift correction, so
  the configured poll rate is actually approached instead of being approximated.

### Changed

- Regression suite grown from 46 to 57 tests, run by CI on every build.

## 3.1.1 - Minecraft 26.2 (NeoForge) / 26.2 + 26.3 (Fabric)

> **Withdrawn** — superseded by 3.1.2 after the 15-point review. Kept for history.

Full audit of the input pipeline, threading, profiles, configuration and mixins.
The mod core is loader-neutral; this release contains no new features, only
correctness, stability and performance fixes.

| Loader | Minecraft | Jar |
| ------ | --------- | --- |
| NeoForge | 26.2 | `inputbooster-3.1.1-nf-mc262.jar` |
| Fabric | 26.2 | `inputbooster-3.1.1-fabric-mc262.jar` |
| Fabric | 26.3 | `inputbooster-3.1.1-fabric-mc263.jar` |

### Fixed

- **CRITICAL — real sub-tick input**: the polling thread re-read a snapshot that
  was only rebuilt once per Minecraft tick, so a tap that started and ended
  inside one tick (exactly the low-FPS case the mod targets) was never observed,
  while the loop still ran up to 1000 times per second. The poller now samples the
  platform key state itself using the key codes the player actually bound, and
  never touches a Minecraft object. The tick snapshot remains as a fallback if
  raw sampling is unavailable.
- **CRITICAL — dead key bindings**: key mappings were created during client setup,
  i.e. after the registration event, so `R` / `K` were missing from the controls
  screen and could dereference a null field.
- **Replay no longer skips events silently**: playback advanced its index even
  when the bounded queue rejected the event. It now waits, then drops one event at
  a time, counts drops and reports them.
- **CPS limiter cap is stable per window**: `HUMANIZED` re-rolled the effective
  limit on every click, so the cap was inconsistent within a single second. All
  windows now use a monotonic clock, and the humanized value is derived once per
  second.
- **Profile system rebuilt on Gson** (already shipped with Minecraft): a profile
  name containing `}` or `\"` used to corrupt the file, and the regex parser
  matched nested values. Writes are atomic, values are re-clamped on apply, and an
  unreadable file is moved to `inputbooster_profiles.json.corrupt` instead of
  breaking startup.
- **Active profile index on delete**: deleting an earlier profile left `activeIndex`
  pointing at the wrong profile; deleting the active one left a phantom index.
- **Server profile detection**: a hard-coded vanilla call could throw
  `NoSuchMethodError` (not caught by `catch (Exception)`) and crash the client
  tick. All reflection is isolated and degrades to a stable identifier, runs once
  per second instead of per tick, and normalises `Example.com`,
  `example.com:25565` and `tcp://example.com:25565/…` to the same profile.
- **Safe Mode isolates failures**: five errors anywhere disabled the entire mod
  permanently. It now disables only the failing module and re-enables it after a
  quiet window.
- **Shutdown race**: a tick after `close()` re-initialised the mod and started a
  second polling thread. Shutdown now blocks re-initialisation, waits for the
  poller to actually terminate and releases the queue, snapshot and window handle.
- **Mixin targets updated for 26.x**: `doAttack` / `doItemUse` no longer exist and
  `Gui.extractRenderState` changed signature, so duplicate-attack suppression and
  the F3 overlay were silently disabled. They now attach to `startAttack`,
  `startUseItem` and `extractRenderState(DeltaTracker, boolean, boolean)`, and a
  startup check logs any vanilla method that is missing instead of degrading
  quietly.
- **Input queue bound**: `clear()` reset the counter while the polling thread could
  be mid-enqueue, which could grow the queue past its documented limit.
- **Config integrity**: saves are synchronised and atomic, a reload starts from the
  documented defaults instead of leaking previous values, and `True` / `yes` / `1`
  / `on` are accepted as booleans.
- **Latency profiler**: the rolling average mixed expired and live samples after the
  ring buffer wrapped; the ring is now read chronologically under a lock.
- **CPS sparkline**: froze after any gap longer than a second; it now uses a
  monotonic clock and closes every elapsed second.
- **Crash paths**: a null-profile-manager NPE in the settings screen, a
  `NullPointerException` when exporting a config to a path without a parent, an
  unguarded pick-block cast that could throw `ClassCastException`, and a
  `SecurityException` from `Thread.setPriority` that aborted mod initialisation.

### Changed

- The HUD overlay is rendered through the 26.x deferred `GuiRenderState`, and its
  visibility check no longer hides the overlay exactly when F3 is open.
- Input state is cleared on disconnect/world unload so nothing carries between
  sessions.
- Regression suite grown from 24 to 46 tests, run by CI on every build.

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
