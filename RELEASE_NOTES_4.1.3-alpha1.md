# InputBooster 4.1.3-alpha1 — Minecraft 26.1 – 26.3

**Alpha build.** Client-side only; install it in `mods/` alongside Fabric API.

## Supported Minecraft versions

| Jar | Minecraft |
|---|---|
| `inputbooster-4.1.3-alpha1-mc263.jar` | 26.3 |
| `inputbooster-4.1.3-alpha1-mc262.jar` | 26.2 |
| `inputbooster-4.1.3-alpha1-mc2612.jar` | 26.1.2 |
| `inputbooster-4.1.3-alpha1-mc2611.jar` | 26.1.1 |
| `inputbooster-4.1.3-alpha1-mc261.jar` | 26.1 |

Each jar declares its own exact Minecraft range, requires **Java 25**, Fabric
loader 0.19+, and Fabric API at or above the build it was compiled against.

## Verified bug fixes

### Native input no longer sampled off the game thread
`glfwGetKey` is a main-thread function, and the high-frequency poller was calling
it (directly and through `InputConstants.isKeyDown`) from its own thread. That is
undefined behaviour: it usually appears to work, then returns stale data or
crashes under load. The poller now only reads an immutable 512-bit snapshot that
the game thread publishes once per rendered frame (HUD hook) and once per tick.
Sampling therefore still happens above tick rate, but only where the platform API
is allowed to be called. Native failures are reported once with the exception and
fall back to tick-rate sampling instead of being swallowed.

### One key bound to several actions
`KeyBindingProcessor` resolved a key code to the *first* matching slot, while the
polling path applied every slot. With two actions on one key the two sources
disagreed: a press produced one action or two depending on which saw it first.
Codes now resolve to a bit mask of every bound slot and both paths dispatch all of
them.

### POLL mode left slot state stale
The event path returned before updating slots when the engine was in POLL mode,
so the documented "the event source always updates state" contract was not met and
a live mode switch started from old data. Slots are now updated in POLL mode with
dispatch suppressed.

### Shared mutable key-binding state
`KeyBindingSet.codes` was a public array and `KeyBindingProcessor.codes()`
returned its internal array, so any caller could rebind an action mid-fight.
Both now hand out copies (`copyCodes()`, `codes()`), with regression tests.

### Click and scroll windows raced
`InputEventDispatcher.lastClickNanos` and `MouseProcessor.lastScrollNanos` were
plain fields written by both the game thread and the poller. Two clicks on
different threads could both pass the check. Both windows are now claimed with an
atomic compare-and-set, so exactly one click per window is accepted.

## Custom keyboard handler and key mapping

### Handler mode selection was not deterministic
The poller skipped the tick snapshot whenever the alpha custom handler was
enabled, and nothing checked whether that handler could actually see events. On a
client where the keyboard/mouse mixins did not apply this left the mod with **no**
input source at all, silently. Source selection is now one pure decision
(`InputPollingThread.chooseSource`, with a `RAW`/`SNAPSHOT`/`IDLE` result):

- native sampler usable -> raw, frame-rate sampled state;
- otherwise, custom handler disabled -> tick snapshot;
- otherwise, custom handler enabled **and** its hooks have delivered an event ->
  the handler owns input;
- otherwise (enabled but no event ever seen) -> tick snapshot plus one warning
  line, instead of dead input.

The active source is logged whenever it changes, a source switch resets the
engine so no key is inherited as held, and the overlay's keyboard line now
distinguishes "custom (alpha, live)" from "custom (alpha, no events yet)".

### The player's real binding is respected, and fallbacks are named
Slot resolution still falls back to the vanilla default when a mapping cannot be
read, but it no longer does so invisibly: `KeyBindingSet` records which slots
fell back and whether the cause was a missing mapping or a failed accessor, and
the fallback warning lists the affected slot names once. A mapping that resolves
always keeps the key the player actually bound, including custom mouse buttons.

### Registration happens exactly once
Fabric does not detect duplicate key mappings: registering the same `KeyMapping`
twice adds a second row to the Controls screen and the player's rebind then
applies to only one of them. The entrypoint now claims the single registration
pass before registering, and `createKeyMappings()` still runs during mod
construction, before registration and before any input path is wired.

## Tests

`StabilityAuditTest` covers: one key bound to two actions (mask, event path and
POLL mode), reset clearing every slot and allowing the next press, defensive
copies, stale-resolution removal after a rebind, eight concurrent threads
claiming one click window, four concurrent threads hammering the state manager
followed by a reset, one edge per press/release at 60/200/500/750/1000 Hz, and the
unavailable-native-state path. The suite is 106 tests on the modern targets and 102 on the legacy targets.

## Known limitations

- The overlay's F3 handling is unchanged from 4.1.2: with the debug screen open
  the panel is offset below the vanilla debug block and drops its background. It
  was not visually re-checked in the game for this build.
- Neither this release nor its tests launch Minecraft. The mixin hooks are checked
  against each resolved Minecraft jar by `MixinTargetSignatureTest`, but in-game
  behaviour still needs a manual run.
