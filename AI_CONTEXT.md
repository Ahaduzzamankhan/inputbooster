# INPUTBOOSTER — AI CONTEXT

> AI CONTEXT IDENTIFIER: `IB-AIC-261-001-042-128-1000`
>
> ARCHITECTURE IDENTIFIER: `IB-ARC-261-001-042`
>
> STATUS: `AUTHORITATIVE`
>
> PRIORITY: `READ-BEFORE-CODE`
>
> SCOPE: `ENTIRE-REPOSITORY`

---

# 0. AI READ DIRECTIVE

If you are an AI agent, coding assistant, autonomous developer, code reviewer, or software-engineering model working inside this repository:

**READ AND UNDERSTAND THIS FILE BEFORE MODIFYING CODE.**

Do not treat individual source files as independent components. InputBooster is a connected system.

Before changing a file, understand:

1. what architectural layer the file belongs to;
2. who calls it;
3. what it calls;
4. what thread executes it;
5. what data flows through it;
6. what invariants depend on it;
7. whether it is part of a performance-sensitive path;
8. whether it interacts with Minecraft's vanilla input system;
9. whether it affects other modules.

If another document conflicts with the actual source code, the current source implementation and current build configuration are authoritative.

Do not blindly follow historical README information.

---

# 1. PROJECT IDENTITY

Project: `InputBooster`

Repository: `Ahaduzzamankhan/inputbooster`

Package namespace: `dev.inputbooster`

Purpose:

InputBooster is a Minecraft client-side input-processing system designed to provide high-frequency input observation, reliable event capture, controlled event execution, diagnostics, and optional input-related features.

It is NOT simply a key remapper, CPS counter, FPS mod, macro engine, or collection of unrelated utilities.

Correct mental model:

> InputBooster is an input-event pipeline with feature modules attached to it.

---

# 2. CURRENT ARCHITECTURE IDENTITY

Current architecture must be determined from the active source tree and build files.

Current architecture associated with this context:

Minecraft: `26.1`

NeoForge: `26.1.0.1-beta`

Java: `25`

Mod ID: `inputbooster`

Group: `dev.inputbooster`

License: `MIT`

IMPORTANT: Older repository documentation may describe a previous Fabric / older Minecraft / older Java architecture. Do not mix historical architecture with the current implementation.

Before making Minecraft-specific changes, inspect:

- `build.gradle`
- `gradle.properties`
- mod metadata
- source tree
- mixins
- mappings/API usage

---

# 3. CORE ARCHITECTURAL MODEL

The entire project revolves around this pipeline:

    Minecraft Input State
            |
            v
       KeySnapshot
            |
            v
    InputPollingThread
            |
            v
       Edge Detection
            |
            v
        InputAction
            |
            v
      Stamped Event
            |
            v
    InputActionQueue
            |
            v
       Game Thread
            |
            v
       InputDrainer
            |
            v
    Minecraft Gameplay

The five fundamental stages are:

    CAPTURE → DETECT → QUEUE → DRAIN → EXECUTE

Every major feature should be understood in relation to this pipeline.

---

# 4. FUNDAMENTAL THREAD MODEL

InputBooster has a critical thread boundary.

## Polling Thread

Responsible for observing input snapshots, comparing current and previous state, detecting transitions, generating semantic input events, timestamping events, submitting events to the queue, and maintaining polling timing.

The polling thread must remain lightweight.

## Minecraft Game Thread

Responsible for Minecraft gameplay, Minecraft client state, player interaction, game-mode actions, UI, draining queued events, and execution of Minecraft operations.

## HARD RULE

The polling thread must NOT directly manipulate Minecraft gameplay state.

Correct:

    PollingThread → InputAction → InputActionQueue → Minecraft Game Thread → Minecraft API

Incorrect:

    PollingThread → player.attack() / jump() / gameMode.attack() / gameplay mutation

Unless a future architectural redesign explicitly establishes thread-safe execution, preserve the current game-thread execution boundary.

---

# 5. KEY SNAPSHOT

`KeySnapshot` represents a complete input state at a specific observation point.

It should be treated as immutable.

Conceptually it contains states such as:

- attack
- use
- sprint
- sneak
- jump
- forward
- back
- left
- right
- drop
- swap
- pickBlock

The purpose of the snapshot is to create a consistent state boundary between Minecraft and the polling thread.

Do not introduce unnecessary mutable shared state into `KeySnapshot`.

---

# 6. INPUT POLLING THREAD

`InputPollingThread` is a performance-sensitive component.

Responsibilities:

1. obtain the current snapshot;
2. compare current state with previous state;
3. detect transitions;
4. create semantic events;
5. timestamp events;
6. enqueue events;
7. maintain polling frequency;
8. respond correctly to shutdown/interruption.

The polling loop may operate from approximately 60 Hz up to 1000 Hz depending on configuration and runtime behavior.

Avoid unnecessary allocations, logging, blocking, Minecraft gameplay calls, and expensive collection operations in this loop.

---

# 7. EDGE DETECTION

InputBooster converts state into events.

For a button:

    previous = false
    current  = true

means `PRESSED`.

For:

    previous = true
    current  = false

means `RELEASED`.

Do not duplicate this mechanism inside individual feature modules.

---

# 8. INPUT ACTION

`InputAction` is the semantic event layer.

Examples include:

    ATTACK_PRESSED
    ATTACK_RELEASED
    USE_PRESSED
    USE_RELEASED
    SPRINT_PRESSED
    SPRINT_RELEASED
    SNEAK_PRESSED
    SNEAK_RELEASED
    JUMP_PRESSED
    FORWARD_PRESSED
    FORWARD_RELEASED
    BACK_PRESSED
    BACK_RELEASED
    LEFT_PRESSED
    LEFT_RELEASED
    RIGHT_PRESSED
    RIGHT_RELEASED
    DROP_PRESSED
    SWAP_PRESSED
    PICK_BLOCK_PRESSED

The semantic event layer is intentionally separate from Minecraft execution.

---

# 9. STAMPED INPUT ACTIONS

`InputAction.Stamped` represents an action plus its capture timestamp.

Use `System.nanoTime()` for elapsed-time measurements.

Do not interpret this timestamp as a wall-clock timestamp. It represents timing within the process/runtime.

---

# 10. INPUT ACTION QUEUE

`InputActionQueue` is the communication boundary between `InputPollingThread` and the Minecraft Game Thread.

It is a thread-safe producer/consumer queue.

Current conceptual capacity: `128 events`.

The bounded queue is intentional. It prevents unlimited queue growth, memory pressure, increasing latency, and instability.

Do not casually replace it with an unbounded structure.

---

# 11. QUEUE PERFORMANCE

Queue operations are performance-sensitive.

Avoid unnecessary allocations, synchronization, logging, copying, blocking, and collection traversal.

Any change to queue ordering, capacity, overflow, or count tracking requires concurrency analysis.

---

# 12. QUEUE OVERFLOW

The queue is finite. Overflow behavior must remain intentional.

When modifying queue logic, consider event loss, ordering, count consistency, race conditions, latency, and recovery.

Do not remove the bound merely to hide an overflow symptom.

---

# 13. INPUT DRAINER

`InputDrainer` is the primary execution boundary.

Conceptually:

    dequeue event
        ↓
    process event
        ↓
    measure latency
        ↓
    apply feature policy
        ↓
    execute Minecraft action

The drainer runs on the Minecraft/game thread.

Do not turn it into a giant class containing every feature implementation. Complex features should remain separated behind appropriate managers/modules.

---

# 14. CENTRAL EXECUTION PATH

Whenever possible:

    InputAction → InputActionQueue → InputDrainer → Minecraft

Do not create unnecessary alternative execution paths.

Features, replay, and other event producers should reuse the central execution infrastructure when appropriate.

---

# 15. VANILLA DUPLICATE EXECUTION

A critical Minecraft integration problem is duplicate execution.

If InputBooster executes an attack and Minecraft's own input handling executes the same attack, one physical input can become two actions.

Per-tick handled state such as `attackHandledThisTick` and `useHandledThisTick` exists to prevent this.

Conceptually:

    InputDrainer → execute action → mark handled → vanilla hook suppresses duplicate

Do not remove or bypass duplicate protection without understanding the complete vanilla execution path.

---

# 16. MIXIN CONTEXT

Before modifying a Mixin, determine:

1. exact target method;
2. exact injection point;
3. execution frequency;
4. whether it is cancellable;
5. whether it can execute more than once;
6. whether InputBooster is active;
7. whether initialization is complete;
8. whether vanilla behavior must remain unchanged;
9. whether method names/signatures changed in the current Minecraft version.

Mixin changes are architectural changes.

---

# 17. ATTACK VS BLOCK BREAKING

Attack input and continuous block breaking are not necessarily the same behavior.

An attack event may represent a discrete action. Block breaking can depend on Minecraft's continuous held-input behavior.

Before changing attack logic, identify whether the request concerns entity attack, block breaking, attack input, held attack, attack cooldown, or vanilla input handling.

---

# 18. LATENCY PROFILER

`LatencyProfiler` measures the InputBooster software pipeline:

    event captured → queue → drained → measurement

Conceptually:

    latency = drainTime - capturedAt

This is NOT total physical keyboard latency and does not directly measure hardware, USB, OS, monitor, network, or end-to-end player latency.

Do not make stronger claims than the measurement supports.

---

# 19. ADAPTIVE POLLING

Adaptive polling can adjust polling behavior based on runtime performance.

Conceptual model:

    FPS → smoothing → stability analysis → polling decision → new polling rate

The purpose is to balance responsiveness, CPU usage, stability, and unnecessary polling.

Avoid rapid oscillation between polling rates.

---

# 20. BURST MODE

Burst Mode is a temporary high-frequency response mechanism.

Conceptually:

    significant FPS drop → Burst Mode → temporary high rate → recovery → normal polling

The current architecture can temporarily reach approximately 1000 Hz for a bounded period.

Do not convert Burst Mode into permanent maximum-rate polling without an intentional redesign.

---

# 21. CPS LIMITER

`CpsLimiter` is a policy module.

Correct separation:

    InputPollingThread → detects attack → ATTACK_PRESSED → CpsLimiter → allowed? → InputDrainer → Minecraft

The CPS limiter should not become another input polling system.

---

# 22. MOVEMENT FEATURES

Movement modules such as `SprintManager`, `WTapAssist`, `AutoStrafeManager`, and `AntiIdleManager` should consume semantic input or centralized runtime state.

Do not independently implement multiple high-frequency polling loops for individual features.

---

# 23. REPLAY

Replay should operate on the same semantic event architecture.

Recording:

    InputAction → ReplayRecorder → timestamp / offset

Playback:

    recorded event → InputAction → InputActionQueue → InputDrainer → Minecraft

Replay should not create a second gameplay execution engine unless explicitly required.

---

# 24. CONFIGURATION

`InputBoosterConfig` is the central configuration authority.

Configuration categories can include polling, movement, sprint, W-tap, anti-idle, auto-strafe, CPS limiter, Burst Mode, replay, Safe Mode, logging, conflict detection, click sounds, overlays, and debug information.

Do not scatter configurable values throughout unrelated classes.

---

# 25. CONFIGURATION OWNERSHIP

Preferred architecture:

    InputBoosterConfig → Feature → Runtime

Avoid independent configuration engines inside individual features.

Centralized configuration keeps profiles and UI maintainable.

---

# 26. PROFILE SYSTEM

`ProfileManager` represents configuration snapshots.

Conceptual architecture:

    InputBoosterConfig → capture → Profile → persist

Applying:

    Profile → apply → InputBoosterConfig → Features

Profiles should not become an independent configuration engine.

---

# 27. MODULE MANAGER

`ModuleManager` provides logical grouping of functionality.

Do not confuse module state with global mod activation, configuration state, Safe Mode, thread health, or runtime error state.

Think in layers:

    Configuration → Module State → Runtime State → Actual Execution

---

# 28. SAFE MODE

`SafeModeManager` is a failure-protection system.

Conceptual behavior:

    error → recordError() → recent-error window → threshold → Safe Mode → disable active input processing

Safe Mode exists because input manipulation is sensitive.

When adding error-prone code to the core pipeline, consider how it interacts with Safe Mode.

---

# 29. SESSION STATISTICS

`SessionStats` is diagnostic state.

Possible information includes session start, uptime, recovered inputs, CPS history, FPS history, estimated missed inputs, and polling health.

Clearly distinguish measured values from estimates.

---

# 30. EVENT LOGGING

Event logging is diagnostic.

Never introduce unrestricted logging into a high-frequency polling path.

Prefer counters, aggregation, debug mode, rate-limited logs, and snapshots.

---

# 31. UI ARCHITECTURE

UI should observe core state.

Preferred:

    Core → Metrics / State → UI

The core input engine must work correctly even when HUD, overlays, or settings screens are unavailable.

---

# 32. PERFORMANCE HOT PATHS

Treat these as performance-critical:

    InputPollingThread.poll()
    InputActionQueue.queue()
    InputActionQueue.poll()
    InputDrainer.drainAll()
    InputAction creation
    timestamping

Before modifying them, ask:

    Does this allocate?
    Does this lock?
    Does this block?
    Does this log?
    Does this traverse a collection?
    Does this call Minecraft?
    Does this increase GC pressure?
    Does this increase contention?
    Does this change timing?
    Does this change event ordering?

---

# 33. THREAD SAFETY

Before introducing shared state, ask:

1. Which thread writes it?
2. Which thread reads it?
3. Is it immutable?
4. Is it volatile?
5. Is it atomic?
6. Is it protected by a lock?
7. Is synchronization actually required?
8. Could stale data cause incorrect gameplay?

Avoid mutable state shared between polling and game threads without an explicit synchronization strategy.

---

# 34. EVENT ORDERING

Input events are ordered.

When modifying queue or replay logic, preserve event ordering unless the feature explicitly requires transformation.

Do not accidentally turn a sequence such as:

    FORWARD_PRESSED
    ATTACK_PRESSED
    ATTACK_RELEASED
    FORWARD_RELEASED

into an incorrectly ordered sequence.

---

# 35. FOCUS LOSS / RECOVERY

Input systems must consider alt-tab, menus, focus loss, focus regain, and keys released outside the game.

When modifying recovery logic, inspect interactions between KeySnapshot, InputPollingThread, previous state, release events, Minecraft focus state, and queue state.

Do not inject arbitrary releases without understanding the state machine.

---

# 36. DEBUGGING METHOD

When debugging, trace the event from beginning to end.

    Symptom
       ↓
    Minecraft execution
       ↓
    InputDrainer
       ↓
    InputActionQueue
       ↓
    InputAction
       ↓
    InputPollingThread
       ↓
    KeySnapshot

Example: double attack.

Check:

    Was one physical transition detected?
    Was one InputAction generated?
    Was it queued once?
    Was it drained once?
    Was the action executed once?
    Was attackHandledThisTick set?
    Did vanilla doAttack() execute anyway?

---

# 37. FEATURE DEVELOPMENT RULE

When adding a feature:

1. understand the existing pipeline;
2. identify the smallest appropriate integration point;
3. reuse existing events;
4. reuse existing configuration;
5. reuse existing metrics;
6. reuse existing execution infrastructure;
7. avoid creating parallel systems.

Ask:

    Can this feature consume an existing InputAction?
    Can this feature use InputDrainer?
    Can this feature use existing configuration infrastructure?

If yes, prefer reuse.

---

# 38. DO NOT DUPLICATE INFRASTRUCTURE

Bad:

    Feature A → own polling thread
    Feature B → own polling thread
    Feature C → own event queue

Preferred:

    InputPollingThread
          ↓
      InputAction
          ↓
    InputActionQueue
          ↓
      InputDrainer
          ↓
    Feature A / B / C

---

# 39. CLASS RESPONSIBILITY PRINCIPLE

`KeySnapshot`: immutable input state.

`InputPollingThread`: high-frequency observation and edge detection.

`InputAction`: semantic event representation.

`InputActionQueue`: thread-safe event transport.

`InputDrainer`: game-thread event execution.

`CpsLimiter`: CPS policy.

`LatencyProfiler`: InputBooster pipeline timing.

`ReplayRecorder`: input event recording.

`ProfileManager`: configuration snapshots.

`SafeModeManager`: failure protection.

`SessionStats`: runtime/session diagnostics.

`EventLog`: diagnostic event logging.

`DebugOverlayManager`: debug information presentation.

Do not make one class responsible for everything.

---

# 40. VERSION MIGRATION RULE

Never assume an older Minecraft method still exists.

Before using a method from memory, inspect current mappings/source/API.

Particularly verify client tick methods, attack methods, item-use methods, input APIs, screen/focus APIs, key mapping APIs, loader APIs, and mixin target names.

Historical code is reference material, not automatically current truth.

---

# 41. CODE QUALITY

Prefer code that is clear, predictable, structurally organized, low-overhead, and testable.

Avoid clever hacks, unexplained globals, duplicate systems, silent exception swallowing, unnecessary abstraction, and giant multipurpose classes.

---

# 42. COMMENT RULE

Comments should explain WHY rather than merely WHAT.

Do not flood performance-sensitive code with unnecessary comments.

Document important architectural decisions.

---

# 43. CHANGE IMPACT ANALYSIS

Before changing a core class, identify dependencies.

Changing `InputAction` may affect InputPollingThread, InputActionQueue, InputDrainer, ReplayRecorder, LatencyProfiler, tests, logging, and feature modules.

Changing `InputDrainer` may affect attack, use, CPS, replay, latency, vanilla suppression, movement, and statistics.

Changing `KeySnapshot` may affect polling, edge detection, focus recovery, and input consistency.

Never treat a central class as an isolated implementation detail.

---

# 44. TESTING CORE INPUT

After modifying the input pipeline, test at minimum:

    attack press
    attack release
    use press
    use release
    movement press
    movement release
    jump
    sprint
    sneak
    rapid input
    held input
    release input
    focus loss
    focus regain
    low FPS
    queue pressure
    replay
    CPS limiting
    Safe Mode

For Minecraft-specific changes also verify entity attack, item use, block interaction, continuous block breaking, and vanilla duplicate suppression.

---

# 45. BUILD VALIDATION

After source modifications, verify compilation, tests, generated resources, mod metadata, mixin configuration, and runtime startup.

A successful compile does not prove that a Mixin target works at runtime.

---

# 46. AI RESPONSE REQUIREMENTS

When explaining a proposed change, structure reasoning as:

## WHY
What problem is being solved?

## WHERE
Which architectural layer owns the behavior?

## WHAT
What code changes are required?

## IMPACT
What existing behavior could change?

## SAFETY
Could this affect threading, queueing, vanilla duplication, timing, replay, or Safe Mode?

## VALIDATION
How should the change be tested?

---

# 47. NEVER MAKE THESE ASSUMPTIONS

Do not assume higher polling always means lower latency.

Do not assume 1000 Hz means exactly 1000 evenly-spaced polls.

Do not assume LatencyProfiler measures physical input latency.

Do not assume estimated missed inputs are exact.

Do not assume a Minecraft method from an older version still exists.

Do not assume a feature should create its own polling thread.

Do not assume a queued event can safely execute from the polling thread.

Do not assume attack and block breaking are identical.

Do not assume README documentation is newer than source code.

---

# 48. CORE INVARIANTS

### INVARIANT 001
PollingThread does not directly execute Minecraft gameplay.

### INVARIANT 002
KeySnapshot represents immutable state.

### INVARIANT 003
InputAction represents semantic input events.

### INVARIANT 004
InputActionQueue is the thread boundary.

### INVARIANT 005
InputDrainer executes queued actions on the game thread.

### INVARIANT 006
Vanilla duplicate execution must be prevented where InputBooster has already handled the same action.

### INVARIANT 007
The event queue remains bounded.

### INVARIANT 008
The high-frequency polling path remains lightweight.

### INVARIANT 009
Features should reuse the central event pipeline.

### INVARIANT 010
Configuration remains centrally managed.

### INVARIANT 011
Replay should reuse the normal event execution path whenever possible.

### INVARIANT 012
Diagnostics must distinguish measurements from estimates.

### INVARIANT 013
Safe Mode must remain capable of disabling malfunctioning input processing.

---

# 49. QUICK REFERENCE

    KEY STATE
       ↓
    KeySnapshot
       ↓
    InputPollingThread
       ↓
    EDGE DETECTION
       ↓
    InputAction
       ↓
    Stamped
       ↓
    InputActionQueue
       ↓
    GAME THREAD
       ↓
    InputDrainer
       ↓
    FEATURE POLICY
       ↓
    Minecraft
       ↓
    VANILLA DUPLICATE CONTROL

Around it:

    Config
    Profiles
    Modules
    Replay
    CPS
    Movement
    Burst Mode
    Latency
    Statistics
    Logging
    Safe Mode
    UI

---

# 50. FINAL AI DIRECTIVE

> **InputBooster is an input-event pipeline, not a collection of independent features.**

The canonical flow is:

    KeySnapshot
        ↓
    InputPollingThread
        ↓
    InputAction
        ↓
    InputActionQueue
        ↓
    InputDrainer
        ↓
    Minecraft Game Thread

Features should attach to this pipeline rather than bypassing it.

The polling thread detects.

The queue transports.

The game thread executes.

Mixins integrate with vanilla.

Configuration controls behavior.

Profiles snapshot configuration.

Diagnostics observe the system.

Safe Mode protects the system.

Performance-sensitive code must remain lightweight.

Thread boundaries must remain explicit.

Vanilla duplicate execution must remain controlled.

When uncertain, inspect the surrounding architecture before editing.

Do not optimize blindly.

Do not refactor blindly.

Do not create parallel input systems without a strong architectural reason.

Do not assume historical documentation represents the current implementation.

Always preserve correctness, thread safety, event ordering, bounded resource usage, Minecraft compatibility, and maintainability.

---

# AI CONTEXT END

`IB-AIC-261-001-042-128-1000`

`IB-ARC-261-001-042`

STATUS: `AUTHORITATIVE`

NEXT ACTION:

**UNDERSTAND THE ARCHITECTURE BEFORE MODIFYING CODE.**
