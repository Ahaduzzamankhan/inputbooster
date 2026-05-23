# Changelog

All notable changes to InputBooster will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [3.0.1] - 2026-05-23

### 🐛 Fixed

#### Critical Crash Fixes
- **Settings screen crash — `Can only blur once per frame`:** In MC 1.21.11 the rendering pipeline already triggers a background blur before delegating to a screen's `render()` method. The old code called `renderBackground()` manually at the top of `InputBoosterScreen.render()`, firing the blur shader a second time in the same frame and crashing. Removed the manual call; `super.render()` at the end of the method handles it correctly.

- **Mixin crash on startup — `CallbackInfoReturnable is required`:** In MC 1.21.11, `MinecraftClient.doAttack()` was changed from `void` to `boolean`. Mixin's `@Inject` on a non-void method requires `CallbackInfoReturnable<T>` instead of plain `CallbackInfo`. The `onDoAttack` handler in `GameTickMixin` was using `CallbackInfo`, causing a fatal mixin apply error at launch. Fixed by changing the parameter to `CallbackInfoReturnable<Boolean>` and replacing `ci.cancel()` with `cir.setReturnValue(false)`.

#### Gameplay Fixes
- **Double block break on single click:** A single left-click was destroying two blocks instead of one. Root cause: `InputDrainer.apply()` called `attackBlock()` for block targets while vanilla's `handleBlockBreaking()` loop was already calling `attackBlock()` every tick the button was held — resulting in two `attackBlock()` calls per tick. Fixed by removing the `attackBlock()` call from the drainer entirely for block targets. Block breaking is now driven exclusively by vanilla's continuous held-key loop. Entity hits remain one-shot events and are still fired from the drainer.

- **CPS always showing 0:** After the double-block-break fix moved `recordClick()` into the entity-only branch, block clicks were no longer counted. Moved `recordClick()` up to `drainAll()` so it fires for every accepted `ATTACK_PRESSED` event regardless of target type (entity or block).

#### Overlay / HUD Fixes
- **Overlay visible during F3:** The HUD panel rendered on top of the F3 debug screen, making both unreadable. The overlay now checks `mc.getDebugHud().shouldShowDebugHud()` and returns early, so it disappears automatically when F3 is open and reappears when it is closed.

- **Matrix stack compile error (`push`/`pop`/`scale` not found):** `ctx.getMatrices()` in MC 1.21.11 returns JOML's raw `Matrix3x2fStack`, which has no `push()`, `pop()`, or three-argument `scale()`. The attempt to cast it to `net.minecraft.client.util.math.MatrixStack` also failed at runtime. Removed matrix stack scaling entirely; overlay size and line spacing are now computed directly from the scale factor in pixel coordinates.

---

### ✨ Added

#### HUD Overlay Improvements
- **Overlay is now a proper always-on HUD** — renders on the game screen at all times (not only in F3). Automatically hides when F3 is open to avoid clutter.
- **Configurable position** — cycle through Top-Left, Top-Right, Bottom-Left, Bottom-Right from the Advanced settings tab.
- **Configurable scale** — slider from 0.5× to 3.0× in the Advanced settings tab.
- **Semi-transparent background box** — drawn behind all overlay lines for readability in any environment.

#### Settings Screen
- Renamed misleading **"F3 Overlay Info"** toggle to **"HUD Overlay"** to reflect that it controls the always-on HUD, not an F3-only panel.
- Added **"Overlay Position"** button (cycles through 4 corners) to the Advanced tab.
- Added **"Overlay Scale"** slider (0.5× – 3.0×) to the Advanced tab.

#### Config File
- New key `overlay_position` (integer 0–3, default `0` = Top-Left).
- New key `overlay_scale` (float 0.5–3.0, default `1.0`).

---

### 🗑️ Removed
- **"Double-hit fix: ON"** line removed from the HUD overlay — the fix is always active and the line added noise.

---

## [3.0.0] - 2026-05-01

### 🎉 Major Release

#### Core Features Added
- **Adaptive Burst Mode** — automatically spikes poll rate to 1000 Hz for 3 seconds when FPS drops more than 20% in one second, then returns to normal.
- **Session Stats tracker** — records uptime, total recovered inputs, estimated missed inputs without the mod, and a 60-second CPS sparkline.
- **Config Profiles** — save and load up to 5 named presets (PvP, Mining, Idle, Hybrid, Custom) from the in-game screen.
- **Latency Profiler** — tracks per-drain latency with rolling average and peak, displayed in the HUD overlay.
- **In-game Settings Screen** — full tabbed GUI (Poll Rate, Features, Advanced, Stats, Profiles) opened with the O key.
- **CPS Limiter** — configurable cap (1–20 CPS) with `allowClick()` gate and real-time feedback in the overlay.
- **Burst Mode Manager** — monitors FPS every N ticks (configurable) and triggers burst on significant drops.
- **W-Tap Assist, Auto-Strafe, Anti-Idle, Sprint Manager** — all wired into the new polling and drain pipeline.

#### Technical
- Torn-read fix in `InputPollingThread`: captures `keySnapshot` reference once per poll cycle so all key reads in a cycle are consistent.
- `require=0` on all optional `@Inject` targets so a method rename loads the game rather than crashing.
- Replaced `volatile long` hit counters with `AtomicLong` to eliminate lost-update races.

---

## [2.0.2] - 2026-03-30

### 🐛 Fixed
- **"Not Responding" on MC 1.21.11+** — polling thread now checks for interruption and logs errors instead of silently failing.
- **CPS never recorded** — `CpsLimiter.recordClick()` was never called from `InputDrainer`. Fixed.
- **Silent errors in polling** — changed error handling from silent to WARN-level logging.
- **Null dereference** — added null checks in `InputDrainer` and `GameTickMixin`.
- **WTapAssist silent failure** — added try-catch around `onWRelease()` so feature errors don't block the drain loop.
- **Sleep efficiency** — polling thread sleep changed to millisecond precision for better CPU and battery usage.

### ✨ Added
- Colorful F3 display with status-based color coding (red/yellow/green/gray).
- Visual status indicators (✓ / ✗) for enabled/disabled features.

---

## [2.0.1] - 2026-03-29

### 🐛 Fixed
- **Double input drain** — `InputDrainer.drainAll()` was called twice per tick (GameTickMixin + tick event). Removed redundant drain.
- **AtomicLong race condition** — `totalHits` and `recoveredInputs` were `volatile long` incremented with `++` from multiple threads. Replaced with `AtomicLong`.
- **WTapAssist dead code** — `FORWARD_RELEASED` events were queued but never handled in `InputDrainer`. Wired up.
- **Unsafe MC reads from polling thread** — introduced `gameReady` / `gamePaused` volatile flags updated on the main thread; polling thread reads flags instead of MC objects directly.
- **O(n) queue size** — `ConcurrentLinkedQueue.size()` was called hundreds of times per second. Replaced with `AtomicInteger` for O(1) tracking.
- **Poll rate bounds mismatch** — internal clamp was `[60, 1000]` while docs said `[100, 500]`. Unified.

---

## [2.0.0] - 2026-03-27

### 🎉 First Public Release — Complete Rewrite

Internal v1.0 was never released due to memory leaks, Sodium/Iris crashes, config corruption, and thread-safety issues. v2.0 is a ground-up rewrite.

### ✨ Added
- Dynamic FPS-aware poll rate (100–500 Hz auto-scaling).
- F3 debug integration (poll rate, FPS, recovered inputs, CPS, feature states).
- Real-time CPS tracker with sliding-window algorithm.
- Auto-Strafe correction for diagonal movement at low FPS.
- Anti-Idle protection against AFK kicks during lag spikes.
- Lock-free concurrent input queue.
- Mixin-based event interception compatible with Sodium, Iris, Lithium.
- Graceful thread lifecycle (clean startup and shutdown).
- Properties-file config with validation and auto-creation.

### 🗑️ Removed (vs internal v1.0)
- HUD overlay system — replaced with F3 integration.
- Experimental/unstable features.
- MC 1.19–1.20 legacy compatibility modes.
- Built-in macro system.

---

## Version History

| Version | Date       | Status      | Summary |
|---------|------------|-------------|---------|
| 3.0.1   | 2026-05-23 | ✅ Released | Crash fixes, HUD overlay overhaul, CPS fix, double-break fix |
| 3.0.0   | 2026-05-01 | ✅ Released | Burst mode, profiles, settings screen, latency profiler |
| 2.0.2   | 2026-03-30 | ✅ Released | Thread safety, CPS recording, colorful F3 |
| 2.0.1   | 2026-03-29 | ✅ Released | Double-drain fix, AtomicLong, WTap wiring |
| 2.0.0   | 2026-03-27 | ✅ Released | First public release, complete rewrite |
| 1.0.0   | —          | ❌ Unreleased | Internal only, abandoned |

---

## License

MIT License — see [LICENSE](LICENSE) for details.

**Author:** Ahaduzzaman Khan  
**Contributors:** PvP community beta testers  
**Special Thanks:** Fabric Team, Sodium developers

*For bug reports and feature requests, visit [GitHub Issues](https://github.com/ahaduzzamankhan/inputbooster/issues)*
