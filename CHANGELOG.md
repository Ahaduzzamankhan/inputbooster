# Changelog

All notable changes to InputBooster will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.0.4] - 2026-04-05

### 🚀 Port — Minecraft 26.1.1 "Tiny Takeover"

This release ports InputBooster to **Minecraft 26.1.1**, the April 2026 "Tiny Takeover" hotfix drop (protocol 775, data version 4788). It is the first fully unobfuscated Minecraft release. No gameplay or feature changes were made — this is a pure compatibility update.

### 🔧 Changed

- **Minecraft target:** `1.21.1` → `26.1.1` (also supports `26.1` base drop)
- **Java toolchain:** `21` → `25` (required by MC 26.1.x)
- **Mappings:** Yarn → Official Mojang mappings (no Yarn for 26.1.x yet)
- **Fabric API:** Updated to `0.145.3+26.1.1`
- **fabric-loader:** Updated to `0.18.6`
- **Gradle wrapper:** `8.8` → `8.11` (fabric-loom 1.9-SNAPSHOT requires ≥ 8.11)
- **Mod version string:** `2.0.4-26.1.1` (encodes both mod version and MC target)

### 🐛 Fixed

- **Build failure:** `gradle build` was failing with *"Plugin net.fabricmc:fabric-loom:1.9-SNAPSHOT requires at least Gradle 8.11. This build uses Gradle 8.8."* Resolved by upgrading `gradle/wrapper/gradle-wrapper.properties` to Gradle 8.11.

### 📝 Notes

- `26.1` (data version 4786) and `26.1.1` (data version 4788) are both supported. To target the base `26.1` drop, change `26.1.1` → `26.1` in `build.gradle` dependencies and fabric-api suffix accordingly.
- Yarn mappings for 26.1.x do not yet exist. When they become available, the project can optionally switch from `loom.officialMojangMappings()`.

---

## [2.0.3] - 2026-04-01

### 🔧 Changed

- **Minecraft target:** Preparatory update to `26.1` (base "Tiny Takeover" release, data version 4786)
- **fabric-loader:** Bumped to `0.18.6`
- Internal version bump only; no feature changes

---

## [2.0.2] - 2026-03-30

### 🐛 Fixed

#### Critical Bug Fixes
- **"Not Responding" on MC 1.21.11+:** Fixed a critical issue where the polling thread would silently fail on newer Minecraft versions. The thread now properly checks for interruption and logs errors instead of silently failing.
- **Missing interrupt check:** Added `!Thread.currentThread().isInterrupted()` check in polling thread main loop to ensure graceful shutdown.
- **CPS never recorded:** `CpsLimiter.recordClick()` was never called when attacks were processed. Added recording in `InputDrainer` when `ATTACK_PRESSED` is executed.
- **Silent errors in polling:** Changed polling thread error handling from silently ignoring exceptions to logging them at WARN level for better debugging.
- **Unsafe null dereference:** Added extra null checks in `InputDrainer.poll()` for MinecraftClient and in `GameTickMixin` to prevent null pointer exceptions.
- **Missing error handling in WTapAssist:** Added try-catch around `WTapAssist.onWRelease()` call to prevent feature exceptions from blocking input drain.

#### Thread Safety & Performance
- **Better sleep efficiency:** Changed polling thread sleep from nanosecond precision to millisecond precision for better CPU efficiency and battery life.
- **Thread interruption handling:** Properly handles `InterruptedException` to ensure clean shutdown.

### ✨ Added

#### UI/Display Enhancements
- **Colorful F3 display:** Enhanced debug overlay with vibrant color gradients — red for MAX BOOST (🔥), yellow for high boost, green for normal, gray for light boost.
- **Visual status indicators:** Bold checkmarks (✓) for enabled features, X marks for disabled.
- **Improved formatting:** Added separator lines and better spacing in F3 display.
- **FPS threshold indicator:** Now shows "target: 60+" to guide players on ideal FPS.
- **Enhanced boost labels:** More descriptive Hz descriptions (ultra boost, high boost, normal, light, minimum).

---

## [2.0.1] - 2026-03-29

### 🐛 Fixed

#### Critical Bug Fixes
- **Double input drain (critical):** `InputDrainer.drainAll()` was being called twice per tick — once at HEAD of `MinecraftClient.tick()` via `GameTickMixin`, and again in `ClientTickEvents.END_CLIENT_TICK`. Every queued input was firing twice, causing double-attacks, double-sprints, and duplicate actions. Removed the redundant drain from the tick event.
- **Race condition on recovered/total input counters:** `totalHits` and `recoveredInputs` were `volatile long` fields incremented with `++` from multiple threads. `volatile` does not make `++` atomic — this was a classic lost-update race condition under high polling load. Replaced with `AtomicLong` and `incrementAndGet()`.
- **WTapAssist completely broken (dead code):** `WTapAssist.onWRelease()` was never called anywhere. The polling thread queued `FORWARD_RELEASED` events correctly, but `InputDrainer` silently discarded them. Fixed by wiring `FORWARD_RELEASED` in `InputDrainer` to notify `WTapAssist`.

#### Thread Safety Fixes
- **Unsafe MC state reads from background thread:** The polling thread was directly reading `mc.isPaused()`, `mc.currentScreen`, and `mc.player` from a background thread. Introduced `gameReady` and `gamePaused` volatile flags updated exclusively on the main thread; the polling thread now reads these flags instead.
- **O(n) queue size check on hot path:** `ConcurrentLinkedQueue.size()` is O(n) and was called hundreds of times per second. Replaced with an `AtomicInteger` counter using a CAS loop for O(1) atomic size tracking.

#### Config Fixes
- **Poll rate bounds mismatch:** Config internally clamped poll rate to `[60, 1000]` Hz while all documentation specified `[100, 500]` Hz. Fixed to clamp consistently to the documented `[100, 500]` range.

---

## [2.0.0] - 2026-03-27

### 🎉 Major Release — Complete Rewrite

This is the **first public release** of InputBooster. While internally versioned as 2.0.0, this represents the initial stable version available to users.

### 📖 Why v2.0 and not v1.0?

Version 1.0 was developed internally but never publicly released due to critical issues: memory leaks from background polling threads, Sodium/Iris incompatibility causing crashes, config corruption on write, movement bugs from the sprint-fix feature, race conditions in the input queue, and poor thread shutdown handling. Rather than patch these fundamental architectural flaws, the decision was made to **completely rewrite the mod from scratch**. Version 2.0 represents this ground-up rebuild.

### ✨ Added

#### Core Features
- **Dynamic Poll Rate System** — FPS-aware auto-scaling (100–500Hz)
- **F3 Debug Integration** — Real-time status display with no HUD overlay needed
- **Real-Time CPS Tracker** — Accurate clicks-per-second measurement
- **Auto-Strafe Correction** — Fixes diagonal movement speed loss at low FPS
- **Anti-Idle Protection** — Prevents AFK kicks during client lag spikes
- **Modular Feature System** — Each feature independently toggleable via config

#### Technical Features
- **Lock-Free Input Queue** — Concurrent queue with zero-allocation on hot path
- **Mixin-Based Event Interception** — Modern Fabric best practices
- **Graceful Thread Lifecycle** — Proper shutdown on world unload, no resource leaks
- **Comprehensive Config System** — Properties file with validation and sane defaults

### 🔧 Changed

- Complete codebase rewrite from v1.0 internal version
- Migrated from direct GLFW polling to Mixin-based input capture
- Replaced synchronized blocks with `ConcurrentLinkedQueue`
- Moved from fixed poll rate to dynamic FPS-aware scaling
- Changed HUD rendering to F3 debug integration
- Simplified config file to 7 essential options
- Changed default poll rate from 100Hz to 200Hz

### 🐛 Fixed

- Eliminated all memory leaks in background threads
- Full Sodium/Iris compatibility
- Config file corruption — robust write-and-rename pattern
- Thread safety — replaced race conditions with lock-free structures
- Graceful shutdown — threads now terminate cleanly
- Sprint dropping at low FPS
- Diagonal movement penalties
- CPS measurement inaccuracy — now uses sliding window algorithm

### 🗑️ Removed

- HUD Overlay System — replaced with F3 integration
- Legacy MC 1.19–1.20 compatibility
- Built-in Macro System — explicitly removed to prevent misuse

---

## Version History Summary

| Version | Status       | Release Date | Notes                                                        |
|---------|--------------|--------------|--------------------------------------------------------------|
| 2.0.4   | ✅ Released  | 2026-04-05   | Port to MC 26.1.1; Gradle 8.11 fix; Java 25; Mojang mappings |
| 2.0.3   | ✅ Released  | 2026-04-01   | Port to MC 26.1 base drop                                    |
| 2.0.2   | ✅ Released  | 2026-03-30   | Thread safety, "not responding" fix, colorful F3, CPS fix     |
| 2.0.1   | ✅ Released  | 2026-03-29   | 6 bugs fixed (thread safety, double drain, WTap)             |
| 2.0.0   | ✅ Released  | 2026-03-27   | First public release, complete rewrite                       |
| 1.0.0   | ❌ Unreleased | —           | Internal only, abandoned due to critical issues              |

---

## License

MIT License — see [LICENSE](LICENSE) for details.

---

## Credits

**Author:** Ahaduzzaman Khan  
**Contributors:** PvP community beta testers  
**Special Thanks:** Fabric Team, Sodium developers

---

*For bug reports and feature requests, visit [GitHub Issues](https://github.com/ahaduzzamankhan/inputbooster/issues)*
