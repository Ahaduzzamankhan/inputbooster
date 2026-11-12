# InputBooster

InputBooster is a client-side Minecraft mod that samples keyboard and mouse input faster than the game tick does. At low FPS, or during a stutter, Minecraft can swallow a short tap because the key went down and up between two ticks. InputBooster catches those taps and turns them into the same action vanilla would have run.

It is not an autoclicker and it does not play the game for you. Every attack, use, jump and movement action still starts from a key or button you pressed. The mod only makes sure the game sees it.

**Minecraft:** 26.2 and 26.3 · **Loaders:** Fabric, NeoForge · **Java:** 25 · **Side:** client only

## Supported builds

| Loader | Minecraft | Jar |
| ------ | --------- | --- |
| Fabric | 26.2 | `inputbooster-4.0.3-stable-fabric-mc262.jar` |
| Fabric | 26.3 | `inputbooster-4.0.3-stable-fabric-mc263.jar` |
| NeoForge | 26.2 | `inputbooster-4.0.3-stable-nf-mc262.jar` |

**4.0.2-stable is withdrawn.** It crashes during startup on every client. Use the
jars above.

Fabric builds need Fabric Loader 0.19.0 or newer and Fabric API 0.161.0 or newer. NeoForge builds need 26.2.0.88 or newer. One jar per loader and Minecraft version. Do not install two of them at once.

## Install

1. Install the loader for your Minecraft version.
2. Drop the matching jar into `.minecraft/mods`.
3. Start the game. InputBooster is client side, so a server does not need it and cannot see it.

If the game starts without it, check the log for `[InputBooster] Ready!`. Anything else is covered under [Troubleshooting](#troubleshooting).

## Opening the settings

Two ways in:

- **Options → InputBooster...** in the top right of the vanilla options screen.
- The key binding screen has an InputBooster category if you want to rebind anything.

The settings screen has five tabs. Changes apply as you make them; **Save & Close** writes them to disk.

### Poll

The core of the mod. The poller runs on its own thread and never touches a Minecraft object, so it cannot stall the render thread.

- **Mode AUTO** follows your FPS: the poll rate goes up when the frame rate drops and back down when it recovers. **Mode MANUAL** holds a fixed rate.
- **Poll Rate** slider, 60 to 1000 Hz, plus presets at 100, 200, 350, 500, 750 and 1000.
- **Burst Mode** pushes the poller to 1000 Hz for a moment when you click.
- **Combo Keys** sets the rate with `Ctrl+1` to `Ctrl+5` without opening the menu.

### Features

- **Sprint Fix**: keeps sprinting when you hold forward and the sprint key, even if the client had a bad tick.
- **Auto-Sprint**: sprints while forward is held, you are not sneaking, and your food level is above 6.
- **W-Tap Assist**: helps the short release-and-press that resets your sprint.
- **Anti-Idle**: tiny view adjustment every 200 ticks.
- **Auto-Strafe**: small yaw correction while strafing below 40 FPS.
- **CPS Limiter**: caps clicks per second. The **Max CPS** slider sets the cap from 1 to 20. The mode (`FIXED`, `HUMANIZED`, `COOLDOWN`, `WEAPON_AWARE`) lives in the config file.
- **Key Click Sounds**: optional click feedback. **Click Pitch** and **Click Volume** shape it.

### Advanced

Overlay controls (**HUD Overlay**, **Keystrokes Overlay**, position, scale, opacity), action bar messages, debug mode, the replay recorder, safe mode, the event log, key conflict warnings, per-server profiles, and the FPS check interval.

**Custom Keyboard (ALPHA)** is the one option that changes how input is routed. Read [Alpha keyboard handler](#alpha-keyboard-handler) before turning it on.

### Stats

Session uptime, how many inputs the poller recovered, an estimate of what vanilla would have missed, drain latency, whether the poll thread is alive, the current poll rate, and a 60 second CPS graph. **Reset Peak Latency** clears the latency high-water mark.

### Profiles

Save the current settings under a name, load them back, or delete them. Quick buttons cover the common setups. Per-server profiles, when enabled, switch automatically based on the server you join.

## Alpha keyboard handler

This is off by default and marked alpha on purpose.

Normally the mod reads key state through its own path but still falls back to Minecraft's key mappings. The alpha handler removes that fallback: InputBooster decides on its own whether the world or a screen owns the keyboard, from the call the game itself makes when it opens a screen. That is what fixes a space typed in chat turning into a jump.

What it does not do: it does not edit your vanilla bindings, it does not cancel another mod's key events, and it does not intercept text fields.

What can still go wrong: it is alpha. A Minecraft version or mod combination we have not tested can route input differently, and the handler may then either pass a key twice or drop it. Turning it on opens a warning screen first, and a red line stays on the settings screen while it is active. Turn it off in the same Advanced tab, or set `custom_keyboard_handler=false` in the config.

The chat-jump fix does not require this switch. If typing in chat still moves your player with the handler off, the screen hook did not apply; see [Troubleshooting](#troubleshooting).

## Key binds

| Key | Action |
| --- | ------ |
| `R` | Start or stop replay recording |
| `K` | Play the recording back |

Both live in the InputBooster category of the controls screen, so you can move them.

Replay is a testing tool, not a macro. Playback waits while you are actually pressing keys, it never double-fires a click, and it stops when no recording exists. Recordings are not written to disk, so they end with the session. Opening a screen pauses playback instead of consuming events behind it.

## Configuration

Settings are stored in `.minecraft/config/inputbooster.properties` and written atomically. Profiles and per-server bindings sit next to it. You can edit the file by hand while the game is closed.

| Key | Default | Meaning |
| --- | ------- | ------- |
| `poll_rate_auto` | `true` | Follow FPS instead of a fixed rate |
| `poll_rate_hz` | `200` | Fixed rate, 60 to 1000 |
| `burst_mode` | `true` | 1000 Hz burst while clicking |
| `combo_keys` | `true` | `Ctrl+1` to `Ctrl+5` presets |
| `sprint_fix` | `true` | Sprint correction |
| `auto_sprint` | `true` | Sprint while moving forward |
| `wtap_assist` | `true` | W-tap helper |
| `anti_idle` | `true` | Periodic view nudge |
| `auto_strafe` | `true` | Low-FPS strafe correction |
| `cps_limiter` | `true` | Enable the CPS cap |
| `max_cps` | `20` | Cap used by the limiter, 1 to 20 |
| `cps_mode` | `FIXED` | `FIXED`, `HUMANIZED`, `COOLDOWN` or `WEAPON_AWARE` |
| `click_sounds` | `false` | Click feedback |
| `click_sound_pitch` | `1.35` | Pitch, 0.5 to 2.0 |
| `click_sound_volume` | `0.35` | Volume, 0.0 to 1.0 |
| `replay_enabled` | `true` | Replay recorder |
| `safe_mode` | `true` | Disable a module that keeps failing |
| `event_log` | `true` | Recent activity log |
| `key_conflict_warn` | `true` | Warn about clashing binds |
| `per_server_profiles` | `true` | Switch profiles per server |
| `custom_keyboard_handler` | `false` | Alpha handler, see above |
| `show_f3_info` | `true` | F3 poll-rate badge |
| `show_keystrokes` | `true` | Keystrokes overlay |
| `overlay_position` | `0` | `0` top-left, `1` top-right, `2` bottom-left, `3` bottom-right |
| `overlay_scale` | `1.0` | Overlay scale, 0.5 to 3.0 |
| `overlay_opacity` | `0.8` | Overlay opacity, 0.0 to 1.0 |
| `show_action_bar` | `true` | Action bar messages |
| `fps_check_interval` | `20` | Ticks between FPS checks |
| `debug_mode` | `false` | Verbose diagnostics |

Bad values are not fatal. Every entry that cannot be parsed falls back to its default, and the log names the ones that were reset. `True`, `yes`, `1` and `on` all count as true.

## How it works

The poller samples the platform key state at the configured rate and pushes edge events (pressed, released) into a bounded queue of 128 entries. Once per tick, the drainer takes that queue and applies it to the player, using vanilla calls so the server sees normal actions.

Three rules keep it honest:

1. Nothing executes while a screen is open. A click in a chest is a chest click.
2. Vanilla's own handler for the same action is suppressed for that tick, so a fast click cannot fire twice.
3. Each action runs inside its own guard. If one throws, it is logged, safe mode sees it, and the queue keeps draining.

When the mod cannot use its raw sampling path, it falls back to the per-tick key snapshot. It says so in the log.

## Building from source

Java 25 and a network connection for the Gradle toolchain are required.

```bash
# NeoForge, Minecraft 26.2
./gradlew build

# Fabric, Minecraft 26.3 (default in fabric/gradle.properties)
cd fabric && ./gradlew build

# Fabric, Minecraft 26.2
cd fabric && ./gradlew build \
  -Pminecraft_version=26.2 \
  "-Pminecraft_version_range=>=26.2 <26.3" \
  -Pfabric_version=0.161.0+26.2
```

Jars land in `build/libs` (NeoForge) and `fabric/build/libs` (Fabric).

Run the unit tests with `cd fabric && ./gradlew test`. CI runs the same suite for both Fabric targets and publishes all three jars to a GitHub release on a version bump.

## Troubleshooting

Start with the log. These lines tell you what happened:

| Log line | Meaning |
| -------- | ------- |
| `[InputBooster] Ready!` | Normal startup |
| `[Mixin] All client hooks present.` | Vanilla methods the mod needs still exist |
| `[Mixin] Screen hooks: setScreenAndShow=…, setScreen=…` | Which screen hook applied. At least one must be `true` |
| `[Mixin] Missing vanilla method(s): …` | This Minecraft version changed. Update the mod |
| `[Mixin] pick-block hook unavailable` | Pick-block input is disabled, everything else works |
| `Could not read every key binding` | Some bindings fell back to vanilla defaults |
| `Fatal init error!` | Initialisation failed once and stopped. The mod is off for that session |
| `Polling thread stopped.` | Normal shutdown, or the poller could not start |

Common cases:

- **Typing in chat still jumps.** The screen hook did not apply, or another mod is feeding the player input. Check the screen hook line first, then try the alpha handler.
- **Nothing works in one module.** Safe mode disabled it after repeated errors. Restart, or turn the module off and on. The log names the module.
- **Clicks feel capped below your settings.** The CPS limiter is on. Raise Max CPS or turn the limiter off.
- **The overlay is missing.** It draws only with the F3 debug screen open, with opacity above zero, and with HUD Overlay enabled.
- **Game crashes at launch.** Remove the jar and check the log for a mixin error. Only one InputBooster jar belongs in `mods`.

## Project

- [CHANGELOG.md](CHANGELOG.md) for every release and what changed
- [CONTRIBUTING.md](CONTRIBUTING.md) for the code layout and how to send a patch
- [SECURITY.md](SECURITY.md) for reporting a vulnerability
- [LICENSE](LICENSE), MIT

Issues: <https://github.com/Ahaduzzamankhan/inputbooster/issues>

InputBooster measures nothing it cannot observe. There is no FPS number in this README because the mod has not been benchmarked in a running client, and a number we did not measure would be a guess.
