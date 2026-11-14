# Changelog

## 4.1.2-beta - Native Sodium configuration pages

| Loader | Minecraft | Jar |
| ------ | --------- | --- |
| NeoForge | 26.2 | `inputbooster-4.1.2-beta-nf-mc262.jar` |
| Fabric | 26.2 | `inputbooster-4.1.2-beta-fabric-mc262.jar` |
| Fabric | 26.3 | `inputbooster-4.1.2-beta-fabric-mc263.jar` |

### Added

- InputBooster now appears in Sodium's own settings, under its own entry in
  the Video Settings sidebar, next to Sodium's configuration pages. The pages
  are built with Sodium's own widgets: native toggles, sliders, dropdowns,
  option groups and tooltips.
- Four pages are registered: General (automatic and manual polling, the
  input engine mode and the compatibility fallback), Features (sprint fix,
  auto sprint, W-tap assistance, anti-idle, auto strafe, combo keys, CPS
  limiter, maximum CPS and adaptive burst mode), Overlay (F3 information,
  keystroke display, action-bar messages, display mode, position, opacity,
  scale and the three diagnostic lines) and Advanced (keyboard repeat guard,
  scroll debounce, click deduplication, the experimental keyboard handler,
  safe mode, event logging, key conflict warnings, debug mode, FPS check
  interval, replay recording, per-server profiles and the click sounds with
  their pitch and volume).
- The mod icon is shown next to the InputBooster entry in the sidebar.
- The documented default of every setting now lives in one place,
  `InputBoosterConfig.Defaults`, instead of being written down separately by
  the loader, the settings screen and the Sodium pages.
- The build matrix now checks that the Sodium entrypoint is declared and that
  no Sodium class ends up inside the InputBooster jar.

### Fixed

- The Sodium integration did not compile: the storage handler was a
  `Runnable` where Sodium's Config API expects a `StorageEventHandler`. Both
  Fabric build jobs failed with `incompatible types: Runnable cannot be
  converted to StorageEventHandler`.
- Sodium's "reset to default" action restored whatever the option happened to
  hold instead of the documented default, and which value it restored
  depended on whether the configuration file had been read yet.
- The configuration pages could be built before the configuration file had
  been read, because Sodium builds them while the game finishes loading and
  the configuration is normally read on the first client tick.
- A slider step larger than one made Sodium reject a hand-edited but
  perfectly usable value as out of range and write the default back over it,
  silently changing a setting the user never touched.
- The headless client smoke test could not start the client: the X server was
  opened with its default 8 bit screen, a package that Ubuntu 24.04 no longer
  ships was in the install list, and the client was left probing for a
  hardware Vulkan driver that is not present on the runner.

### Unchanged

- Sodium stays optional. The pages only exist when Sodium is installed, and
  the settings screen, the keybinds and every other feature work exactly the
  same without it.
- The configuration file keeps its format, its defaults and its migration
  path. Sodium is only a second window onto the settings that were already
  there; every change made in Sodium is written through the same atomic save.

## 4.1.2-alpha - Movable, scrollable and resizable settings window

| Loader | Minecraft | Jar |
| ------ | --------- | --- |
| NeoForge | 26.2 | `inputbooster-4.1.2-alpha-nf-mc262.jar` |
| Fabric | 26.2 | `inputbooster-4.1.2-alpha-fabric-mc262.jar` |
| Fabric | 26.3 | `inputbooster-4.1.2-alpha-fabric-mc263.jar` |

### Added

- Drag the window header to move the settings window anywhere on screen.
- Drag the bottom-right corner grip to resize it, down to 380x260, up to the
  edge of the window.
- Scroll the section content with the mouse wheel or by dragging the scrollbar.
  Rows that do not fit the viewport are hidden rather than drawn over the header
  or footer.
- Geometry is kept for the session, so reopening the screen keeps the size and
  position you chose. It is deliberately not written to the config file: it is a
  view preference, not a mod setting.

### Fixed

- On a short window, controls below the fold, including Save & Close in some
  sections, could not be reached at all. The content area now scrolls and the
  tab rail and footer stay fixed.
- The panel no longer always covers the entire screen height; it opens at a
  sensible size and can be resized.

## 4.1.1-alpha - Fix the launch crash in 4.1.0-alpha

4.1.0-alpha crashed during startup on every loader with
`InvalidInjectionException ... Invalid descriptor on
inputbooster.mixins.json:KeyboardHandlerMixin`. The two observing input mixins
were written against the 1.21 signature `keyPress(long, int key, int scancode,
int action, int modifiers)`; 26.2 and 26.3 use `keyPress(long window, int
action, KeyEvent event)` and `MouseHandler.onButton(long window, MouseButtonInfo
button, int action)` instead of `onPress`. `require = 0` does not protect against
this: it tolerates a missing target, not a handler whose parameters disagree
with the target, so the client died while transforming `KeyboardHandler`.

| Loader | Minecraft | Jar |
| ------ | --------- | --- |
| NeoForge | 26.2 | `inputbooster-4.1.1-alpha-nf-mc262.jar` |
| Fabric | 26.2 | `inputbooster-4.1.1-alpha-fabric-mc262.jar` |
| Fabric | 26.3 | `inputbooster-4.1.1-alpha-fabric-mc263.jar` |

### Fixed

- `KeyboardHandlerMixin` injects into `keyPress(long, int, KeyEvent)` and reads
  the key from `KeyEvent.key()`, which exists in both 26.2 (`key`/`scancode`/
  `modifiers`) and 26.3 (`key`/`keycode`/`modifiers`).
- `MouseHandlerMixin` injects into `onButton(long, MouseButtonInfo, int)` and
  reads the button from `MouseButtonInfo.button()`. `onScroll(long, double,
  double)` was already correct.
- `MixinTargetSignatureTest` checks every `@Inject`, `@Accessor` and `@Invoker`
  in `inputbooster.mixins.json` against the resolved Minecraft jar, for both
  the 26.2 and 26.3 builds. A wrong hook signature now fails `./gradlew test`
  instead of crashing the game during startup.

## 4.1.0-alpha - Input engine, new overlay, new settings

Alpha: the engine and the screens are new code and have not been run in a client.
4.0.3-stable stays the recommended build until this is tested.

| Loader | Minecraft | Jar |
| ------ | --------- | --- |
| NeoForge | 26.2 | `inputbooster-4.1.0-alpha-nf-mc262.jar` |
| Fabric | 26.2 | `inputbooster-4.1.0-alpha-fabric-mc262.jar` |
| Fabric | 26.3 | `inputbooster-4.1.0-alpha-fabric-mc263.jar` |

### Added

- **Input engine** (`dev.inputbooster.engine`): `InputEngine`,
  `KeyStateManager`, `KeyboardProcessor`, `MouseProcessor`,
  `KeyBindingProcessor`, `InputEventDispatcher`, `CompatibilityManager`,
  `DiagnosticsManager` and `ConfigurationManager`.
- Two observing mixins on `KeyboardHandler.keyPress` and
  `MouseHandler.onPress/onScroll`. Both run at HEAD, cancel nothing, change no
  binding and use `require = 0`, so a rename degrades to polling instead of
  breaking the client.
- Engine modes: automatic, event driven and polled, with automatic fallback when
  a known input mod (Controlify, Midnight Controls, Controllable, Sodium, Iris,
  Zoomify) owns input routing.
- `DiagnosticsManager` counters: keyboard and mouse events, actions submitted,
  rejected, duplicates suppressed, repeats ignored, and mean dispatch cost.
- **Overlay rewrite**: `OverlayRenderer` and `OverlayLayout`, an always-on mode,
  configurable content, cached text, and four-corner placement with clamping.
- **Settings redesign**: seven sections (Overview, Keyboard, Mouse, Overlay,
  Performance, Compatibility, Advanced) in a left rail, plus a separate profiles
  screen. Every control writes a real config value.
- Nine new config keys, all persisted and validated: `engine_mode`,
  `keyboard_repeat_guard`, `mouse_scroll_debounce_ms`, `mouse_click_dedupe_ms`,
  `compat_fallback`, `overlay_mode`, `overlay_show_engine`,
  `overlay_show_events`, `overlay_show_overhead`. Schema version 305.
- Tests: engine state edges, cross-source duplicate suppression, poll-mode
  suppression, unknown-key isolation, diagnostics, overlay layout clamping, and
  config round trips. 81 tests total.

### Fixed

- **The overlay's text was invisible.** It passed `0x55FFFF` and `0xFFAA00` as
  glyph colours: RGB values whose alpha byte is zero, which the GUI treats as
  "do not draw". The panel drew and every line in it was transparent. Colours are
  now ARGB.
- **The overlay never appeared without F3.** It required the debug screen to be
  open. `overlay_mode=1` keeps it on during normal play.
- **Overlay placement could leave the panel off-screen** after a resize. Corner
  placement now clamps inside the window, and the maths is unit tested.
- Per-poll allocation removed from the hot path: the engine reads key codes
  directly instead of building a snapshot object on every poll.

## 4.0.3-stable - Launch crash fix

**Do not use 4.0.2-stable.** It crashes before the main menu, on every client, in
every mod list. Use these jars instead.

| Loader | Minecraft | Jar |
| ------ | --------- | --- |
| NeoForge | 26.2 | `inputbooster-4.0.3-stable-nf-mc262.jar` |
| Fabric | 26.2 | `inputbooster-4.0.3-stable-fabric-mc262.jar` |
| Fabric | 26.3 | `inputbooster-4.0.3-stable-fabric-mc263.jar` |

### Fixed

- **CRITICAL — the client crashed during startup.** The three accessor mixins
  carried their cast helpers as `public static` methods. Mixin only permits
  *private* static methods inside a mixin, and a non-private one aborts the
  transformation of the whole target class:

  ```
  InvalidMixinException: Mixin inputbooster.mixins.json:KeyMappingAccessor
  contains non-private static method boundCode(Lnet/minecraft/client/KeyMapping;)I
  ```

  That ran while the game was loading `Minecraft`, so the launch ended with
  "Mixin transformation of net.minecraft.client.Minecraft failed" and no crash
  report. The helpers now live in the plain class
  `dev.inputbooster.access.MixinAccess`; the mixins contain nothing but their
  `@Accessor` / `@Invoker` members.
- **A test now guards the rule.** `MixinDeclarationTest` fails the build if any
  registered mixin declares a non-private static method, so this cannot ship
  again. The old tests only checked the accessor members themselves, which is
  why CI stayed green while the jar could not start.

## 4.0.2-stable - First stable release of the 4.0.2 line

> **Withdrawn.** Crashes on launch, see 4.0.3-stable. Kept for history.

First non-prerelease build since 4.0.0. The same three jars as 4.0.2-alpha,
with the bugs found in the stable audit fixed.

| Loader | Minecraft | Jar |
| ------ | --------- | --- |
| NeoForge | 26.2 | `inputbooster-4.0.2-stable-nf-mc262.jar` |
| Fabric | 26.2 | `inputbooster-4.0.2-stable-fabric-mc262.jar` |
| Fabric | 26.3 | `inputbooster-4.0.2-stable-fabric-mc263.jar` |

### Fixed

- **Replay skipped inputs while a screen was open.** Playback kept advancing
  behind an inventory or the pause menu, but the drainer clears the queue there,
  so those events were taken out of the recording and never executed. Playback
  now waits until the player owns the keyboard again.
- **A gap in the key source could swallow the next press.** If raw key sampling
  was unavailable with the custom handler on, the poller kept its previous key
  flags forever, so releasing and pressing a key during that gap produced no
  press. It now re-seeds from "nothing held" when there is no source.
- **Initialisation and the tick loop only caught `Exception`.** A missing class
  or linkage error from a version mismatch would escape into the tick handler on
  every tick. Both now catch `Throwable`, disable the mod once and log it.
- **A silently unapplied screen hook was invisible.** Startup now logs which of
  `setScreenAndShow` / `setScreen` the client actually has, so a router that did
  not apply can be told apart from a jump bug.
- The alpha warning's cancel button said "Turn it off" before the handler had
  ever been on. It now says "Cancel (keep it off)".

### Changed

- `README.md` rewritten: supported versions, install, every settings tab, the
  config keys, build commands and troubleshooting are all in one place.

## 4.0.2-alpha - Alpha keyboard handler

| Loader | Minecraft | Jar |
| ------ | --------- | --- |
| NeoForge | 26.2 | `inputbooster-4.0.2-alpha-nf-mc262.jar` |
| Fabric | 26.2 | `inputbooster-4.0.2-alpha-fabric-mc262.jar` |
| Fabric | 26.3 | `inputbooster-4.0.2-alpha-fabric-mc263.jar` |

### Added

- **Custom keyboard handler, alpha, off by default.** A new `KeyboardRouter`
  makes the screen decision from the game's own show-screen call instead of
  asking the HUD at tick time. A new `ScreenTrackingMixin` only observes that
  call: it cancels nothing, changes no vanilla binding and never sees another
  mod's input. With the handler on, the poller reads key state exclusively
  through its own path and no longer falls back to Minecraft's key mappings.
- The Advanced tab has a `Custom Keyboard (ALPHA)` toggle. Turning it on opens
  a warning screen first, and a red line stays on the settings screen while it
  is active. The value is stored as `custom_keyboard_handler=false`.

### Fixed

- **Space in chat jumped the player.** The poller read raw key state at full
  rate with no reliable screen signal, so a space that belonged to the chat box
  became a `JUMP_PRESSED` event. Screen state is now recorded before the screen
  is shown, anything already queued is dropped at that moment, and the drainer
  checks again before executing an event.

## 4.0.1-beta - Input pipeline correctness pass

Bug review of the whole input path. No new features.

| Loader | Minecraft | Jar |
| ------ | --------- | --- |
| NeoForge | 26.2 | `inputbooster-4.0.1-beta-nf-mc262.jar` |
| Fabric | 26.2 | `inputbooster-4.0.1-beta-fabric-mc262.jar` |
| Fabric | 26.3 | `inputbooster-4.0.1-beta-fabric-mc263.jar` |

### Fixed

- **COOLDOWN CPS mode allowed one click per session.** The minimum gap was
  computed in nanoseconds and then multiplied by 1,000,000 again, which came out
  at roughly 15 hours. The gap is now 35 ms or one window divided by the cap,
  whichever is larger.
- **Clicks inside a screen were executed in the world.** The poller only checked
  for the pause menu, so a left-click in a chest or inventory was queued as an
  attack and fired at whatever was behind the window. Both the poller and the
  drainer now require that no screen is open, and the decision is made on the
  game thread.
- **Right-click ignored block interaction.** `USE_PRESSED` called `useItem` on
  every press and then suppressed vanilla, so chests, levers and doors could not
  be opened. Only the "nothing under the crosshair" case is executed now and
  everything else goes back to `startUseItem()`.
- **The swap-offhand key used the offhand item.** Pressing F ate food or placed
  a block instead of swapping. The swap is vanilla's job, so the mod no longer
  touches it.
- **One failing action could crash the tick.** Every action is applied inside a
  try/catch, the failure is logged and reported to Safe Mode, and the queue
  keeps draining. Safe Mode can now switch the execution layer off through a new
  `input` module instead of only recording an error it could not act on.
- **Jump from the mod never ran.** The guard required the jump key to be up, but
  the event only exists because the key went down. The mod now jumps a tick
  earlier when the player can leave the ground, and vanilla sees the player
  airborne so it does not jump twice.
- **Fallback sprint and sneak keys were swapped.** When a binding could not be
  read, the defaults used left shift for sprint and left control for sneak, the
  reverse of vanilla.
- **A failed init retried on every tick.** It logged the same stack trace each
  tick and could start a second polling thread per attempt. Initialisation now
  stops after one failure, and the poller starts only once everything else is
  ready.
- **Keys held through a pause fired fresh presses on resume.** The edge detector
  cleared its previous state instead of re-reading the held keys, so unpausing
  with attack down produced an attack the player did not make.
- **Entity attacks could target a stale hit result.** The hit result is one frame
  old at tick start, so the drainer now checks that the target is still alive and
  within reach before attacking.
- **Replay inflated session stats.** Replayed attacks ticked the hit counter,
  the CPS graph and the click sound. Only physical input does that now.
- **`ConfigTools` ignored the config directory.** Import and export used a
  hard-coded `config` path while the rest of the mod honours
  `inputbooster.configDir`, so export failed when the two differed.
- **New configs advertised the previous schema version.** `resetDefaults()` wrote
  `config_version=303` while the current version is 304, so a fresh install
  migrated itself on the next load.
- **The event log could exceed its bound.** The size counter was incremented
  outside the trim lock, so two producers could trim each other's entries.
- **The CPS window state was written without a lock.** Two threads could roll the
  window twice in one second and skip a limit.
- **Overlay scale did not reach the text.** The panel was scaled but the glyphs
  were not, which clipped the badge at any scale other than 1.

## 3.1.4 - Mixin declaration fix (launch crash)

The 3.1.3 jars could not start the client at all:

```
InvalidMixinException: @Mixin target type mismatch:
net.minecraft.client.KeyMapping is not an interface
  in [inputbooster.mixins.json:KeyMappingAccessor]
```

Mixin ran during class loading of another mod (Sodium) and aborted the game.

| Loader | Minecraft | Jar |
| ------ | --------- | --- |
| NeoForge | 26.2 | `inputbooster-3.1.4-nf-mc262.jar` |
| Fabric | 26.2 | `inputbooster-3.1.4-fabric-mc262.jar` |
| Fabric | 26.3 | `inputbooster-3.1.4-fabric-mc263.jar` |

### Fixed

- **CRITICAL — the client crashed on startup.** `KeyMappingAccessor`,
  `GuiAccessor` and `MinecraftClientAccessor` were declared as `interface`
  mixins, but their targets (`KeyMapping`, `Gui`, `Minecraft`) are classes.
  Mixin only accepts an interface-form mixin for an interface target, so mixin
  preparation failed and killed the game before the window opened. All three are
  now `abstract class` mixins, which is the correct form for a class target.
- **Casts moved into the mixins.** A class-form mixin is not a compile-time
  supertype of its target, so `input instanceof Accessor` no longer compiles.
  Each accessor now exposes a static helper (`boundCode`, `renderState`,
  `pickBlockOrEntity`) that performs the cast through `Object` and returns a
  fallback (`-1`, `null`, `false`) when the mixin is not applied, so a missing
  hook degrades instead of throwing `ClassCastException` on a key press.

### Added

- **Regression test `MixinDeclarationTest`** (4 tests): every mixin registered
  in `inputbooster.mixins.json` is loaded and asserted to be a class, the
  accessor/invoker members are asserted to be public and abstract, the three
  accessors the mod needs are asserted to be registered, and `defaultRequire`
  is asserted to stay at 1. Verified to fail on the previous interface form.

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
