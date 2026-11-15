# InputBooster 3.1.6-alpha — Minecraft 1.21.1 / 1.21.4 / 1.21.11

**Alpha build.** Client-side only; install it in `mods/` alongside Fabric API.

## Supported Minecraft versions

| Jar | Minecraft |
|---|---|
| `inputbooster-3.1.6-alpha-mc12111.jar` | 1.21.11 |
| `inputbooster-3.1.6-alpha-mc1214.jar` | 1.21.4 |
| `inputbooster-3.1.6-alpha-mc1211.jar` | 1.21.1 |

Each jar declares its own exact Minecraft range, requires **Java 21**, Fabric
loader 0.19+, and Fabric API at or above the release it was compiled against.

## Verified bug fixes

The same input-pipeline fixes as the modern line, ported to the 1.21.x sources:

- **Native input sampling left the game thread.** The poller no longer calls the
  platform key-state API; the game thread publishes an immutable snapshot once per
  rendered frame and once per tick, and the poller reads that. Sampling failures
  are reported once and degrade to tick-rate sampling.
- **Shared key bindings.** A key bound to two actions now dispatches both from the
  event path, matching the polling path (bit-mask resolution instead of
  first-match).
- **POLL mode state tracking.** Slots are updated in POLL mode (dispatch
  suppressed), so a mode switch never starts from stale state.
- **Defensive copies** for the key-binding arrays, so no caller can rebind an
  action by mutating internal state.
- **Atomic click/scroll windows**, so two threads cannot both accept a click in
  the same debounce window.

## Legacy-specific notes

- Compiled and tested on a Java 21 toolchain; the jars declare a Java 21 floor.
  An earlier 3.1.5 jar declared `>=25` and was rejected by the loader on 1.21.x.
- The 1.21.x Sodium releases used here do not ship the Config API, so these jars
  declare no Sodium entrypoint and always show InputBooster's own options button.

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

The full suite (102 tests) runs on every target, including `StabilityAuditTest`
(shared bindings, reset, aliasing, concurrent click claim, concurrent state
updates, polling rates 60–1000 Hz, unavailable native state) and
`MixinTargetSignatureTest`, which verifies every mixin hook against the resolved
Minecraft jar for that exact version.

