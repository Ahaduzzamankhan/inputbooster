# ⚡ InputBooster v2.0.0
**Author:** Ahaduzzaman Khan | **Loader:** Fabric | **MC:** 1.21.x | **Java:** 21+

## What It Does
Runs a 100-500Hz background thread that polls your keyboard/mouse independently of FPS.
Missed clicks between frames are queued and fired on the next game tick.
AUTO-ENABLED on launch. Press F3 to see live status.

## Features
- High-Frequency Input Polling (up to 500Hz, auto-scales with FPS)
- Dynamic Poll Rate: 20FPS->500Hz, 30FPS->350Hz, 60FPS->200Hz, 60+->100Hz
- Sprint Fix: re-asserts sprint every tick so it never drops at low FPS
- Auto-Sprint: hold W = sprint automatically
- W-Tap Assist: sub-frame W-release detection for cleaner knockback
- Auto-Strafe Correction: fixes diagonal sprint speed loss at low FPS
- Anti-Idle: prevents AFK kick during lag spikes
- CPS Tracker: shows real clicks-per-second in F3
- F3 Integration: full status visible in F3 debug screen, no HUD clutter
- Sodium/Iris/Lithium compatible

## F3 Status Display
[InputBooster 2.0.0] ACTIVE
Poll Rate   : 350 Hz (high boost)
Client FPS  : 28
Recovered   : 1,482 inputs
CPS         : 8
Sprint Fix  : ON  | Auto-Sprint : ON
W-Tap       : ON  | Auto-Strafe : ON
Anti-Idle   : ON
by Ahaduzzaman Khan

## Config: .minecraft/config/inputbooster.properties
poll_rate_hz=200
sprint_fix=true
auto_sprint=true
wtap_assist=true
anti_idle=true
auto_strafe=true
show_f3_info=true

## What It CANNOT Do
- Boost your actual FPS (use Sodium for that)
- Fix server-side TPS lag
- Fix network latency/desync
- Auto-click or macro (this mod does NOT do that)

## Build
./gradlew build  ->  build/libs/inputbooster-2.0.0.jar

MIT License — Ahaduzzaman Khan
