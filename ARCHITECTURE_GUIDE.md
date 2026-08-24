# INPUTBOOSTER — ARCHITECTURE GUIDE

> Architecture Identifier: `IB-ARC-261-001-042`
>
> Companion AI Context: `IB-AIC-261-001-042-128-1000`
>
> Status: `AUTHORITATIVE`

---

## 1. Purpose

InputBooster is a client-side Minecraft input processing system. Its architecture separates input observation from Minecraft execution so that high-frequency input processing remains controlled, measurable, and thread-safe.

The central design principle is:

> **Capture input, convert it into semantic events, transport those events across the thread boundary, then execute them on the Minecraft game thread.**

The canonical pipeline is:

```text
Minecraft Input State
        ↓
KeySnapshot
        ↓
InputPollingThread
        ↓
Edge Detection
        ↓
InputAction
        ↓
Stamped Event
        ↓
InputActionQueue
        ↓
Minecraft Game Thread
        ↓
InputDrainer
        ↓
Feature Policies
        ↓
Minecraft Gameplay
        ↓
Vanilla Duplicate Protection
```

---

## 2. Architectural Layers

InputBooster can be understood as several layers.

### Layer A — Input State

`KeySnapshot` contains a consistent representation of relevant input state.

### Layer B — Input Detection

`InputPollingThread` observes snapshots and detects transitions.

### Layer C — Semantic Events

`InputAction` converts raw state transitions into meaningful events.

### Layer D — Transport

`InputActionQueue` transports events between threads.

### Layer E — Execution

`InputDrainer` executes events on the Minecraft game thread.

### Layer F — Feature Policy

CPS, movement, replay, adaptive polling, and other systems modify or consume behavior around the central pipeline.

### Layer G — Vanilla Integration

Mixins integrate the system with Minecraft's own execution paths and prevent duplicate execution.

### Layer H — Configuration and Diagnostics

Configuration, profiles, metrics, logging, Safe Mode, and UI observe or control the system without replacing the core event pipeline.

---

## 3. Thread Architecture

The most important concurrency boundary is:

```text
                 GAME THREAD
                     │
                     │ publishes immutable state
                     ▼
               KeySnapshot
                     │
                     │ shared safely
                     ▼
              POLLING THREAD
                     │
                     │ creates events
                     ▼
             InputActionQueue
                     │
                     │ thread boundary
                     ▼
                 GAME THREAD
                     │
                     ▼
               InputDrainer
```

The polling thread is an observer and event producer.

The game thread is the executor.

This separation is fundamental.

---

## 4. KeySnapshot

`KeySnapshot` is the state representation used by the input pipeline.

It should represent a coherent state at one observation point.

Typical state includes:

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
- pick block

The snapshot should be immutable after construction.

The reason is not only code cleanliness. The polling thread must not observe a half-updated state while the game thread is publishing input information.

---

## 5. InputPollingThread

The polling thread is the high-frequency input observer.

Its conceptual loop is:

```text
read snapshot
      ↓
compare with previous snapshot
      ↓
detect state transitions
      ↓
create InputAction
      ↓
timestamp
      ↓
enqueue
      ↓
wait until next poll
```

The loop must remain lightweight because it can execute hundreds or up to roughly a thousand times per second.

Avoid putting expensive work inside it.

Do not put Minecraft gameplay operations here.

Do not turn individual features into additional polling systems.

---

## 6. Edge Detection

The event system is based on transitions.

For a boolean input:

```text
false → true  = pressed
true  → false = released
```

This allows the system to convert continuous state into semantic events.

For example:

```text
attack false → true
```

becomes:

```text
ATTACK_PRESSED
```

A feature should normally consume that event rather than repeating the same low-level state comparison.

---

## 7. InputAction

`InputAction` is the abstraction that separates input detection from execution.

The polling layer should say:

```text
ATTACK_PRESSED
```

rather than:

```text
execute Minecraft attack now
```

This separation allows the same event model to support:

- normal input;
- CPS limiting;
- movement assistance;
- replay;
- diagnostics;
- future input features.

---

## 8. Stamped Events

A stamped event associates an input action with a capture timestamp.

Conceptually:

```text
Stamped {
    action
    capturedAt
}
```

The timestamp provides the starting point for software pipeline timing.

Use monotonic timing such as `System.nanoTime()` for elapsed durations.

Do not treat it as a calendar timestamp.

---

## 9. InputActionQueue

The queue is one of the most important architectural components because it creates the explicit thread boundary.

Producer:

```text
InputPollingThread
```

Consumer:

```text
Minecraft Game Thread / InputDrainer
```

The queue is intentionally bounded.

A bounded queue protects the system from an unlimited backlog.

```text
input flood
    ↓
queue capacity
    ↓
bounded failure/recovery behavior
```

An unbounded queue could instead create progressively increasing latency and memory usage.

Therefore queue capacity is an architectural property, not just an implementation detail.

---

## 10. InputDrainer

The drainer is the execution gateway.

Conceptually:

```text
InputActionQueue
       ↓
InputDrainer
       ↓
latency measurement
       ↓
feature policy
       ↓
Minecraft API
```

It should run on the game thread.

Its job is orchestration, not becoming a monolithic implementation of every feature.

When a feature grows complex, isolate that feature behind an appropriate manager rather than continuously expanding the drainer.

---

## 11. Vanilla Integration

InputBooster does not exist in isolation from Minecraft.

Minecraft already has its own input processing.

Therefore the architecture must handle two possible paths:

```text
InputBooster input
```

and:

```text
Vanilla input
```

If both execute the same action, duplicate behavior can occur.

For important actions such as attack and use, the system therefore maintains per-tick handled state and uses Minecraft integration hooks to prevent duplicate execution.

This is one of the most important correctness requirements in the project.

---

## 12. Attack Pipeline

A simplified attack path is:

```text
physical attack state
        ↓
KeySnapshot
        ↓
InputPollingThread
        ↓
ATTACK_PRESSED
        ↓
InputActionQueue
        ↓
InputDrainer
        ↓
attack policy
        ↓
Minecraft attack
        ↓
handled-this-tick state
        ↓
vanilla duplicate suppression
```

When debugging attack problems, inspect every stage.

Do not assume the problem is in CPS logic merely because the visible symptom is repeated clicking.

---

## 13. Block Breaking

Block breaking may involve Minecraft's continuous held-input behavior.

Therefore it must not automatically be treated as a sequence of independent attack presses.

When changing attack-related behavior, determine whether the requirement is about:

- entity attacks;
- block breaking;
- attack input;
- held attack;
- attack cooldown;
- vanilla game-mode behavior.

This distinction prevents regressions where normal Minecraft block breaking is accidentally replaced with incorrect repeated actions.

---

## 14. Use / Item Interaction

Item use follows a similar architectural pattern.

The important concern is again duplicate execution.

If InputBooster executes a use action and vanilla also executes it for the same physical input, the user can experience duplicate interactions.

Therefore use handling must preserve its own handled-state logic and vanilla interception behavior.

---

## 15. CPS Limiting

CPS limiting is a policy layer, not a low-level input detector.

Correct conceptual architecture:

```text
input state
    ↓
InputPollingThread
    ↓
ATTACK_PRESSED
    ↓
CpsLimiter
    ↓
allowed / rejected
    ↓
InputDrainer
```

The CPS module should not create another high-frequency input pipeline.

It should consume the existing semantic event stream.

---

## 16. Movement Systems

Movement features can consume the same event model.

Examples include:

- sprint management;
- W-tap assistance;
- auto-strafe;
- anti-idle behavior.

The architecture should avoid independent polling loops for each movement feature.

Instead:

```text
central input pipeline
        ↓
semantic event
        ↓
movement policy
```

This reduces duplication and keeps behavior consistent.

---

## 17. Adaptive Polling

Adaptive polling exists to balance responsiveness and resource consumption.

The conceptual decision process is:

```text
FPS
 ↓
smoothing
 ↓
stability evaluation
 ↓
polling-rate decision
```

The system should not constantly oscillate between rates.

A good adaptive algorithm changes gradually and remains bounded.

---

## 18. Burst Mode

Burst Mode provides a temporary high-frequency response to poor runtime conditions.

Conceptually:

```text
performance degradation
        ↓
Burst Mode
        ↓
high polling rate
        ↓
recovery period
        ↓
normal polling
```

The important architectural property is that the high rate is temporary.

Permanent maximum polling should not be introduced accidentally.

---

## 19. Latency Architecture

InputBooster latency is measured within the software pipeline.

```text
capture timestamp
        ↓
queue waiting
        ↓
drain timestamp
```

Therefore the measured quantity primarily represents:

> capture-to-drain software pipeline delay.

It should not be marketed as total physical input latency.

The architecture must keep terminology technically honest.

---

## 20. Replay Architecture

Replay should reuse the semantic event architecture.

Recording:

```text
InputAction
    ↓
ReplayRecorder
    ↓
timing information
```

Playback:

```text
recorded event
    ↓
InputAction
    ↓
InputActionQueue
    ↓
InputDrainer
    ↓
Minecraft
```

This is preferable to creating a separate gameplay executor for replay.

The same execution path means replay benefits from the same safety and ordering rules.

---

## 21. Configuration Architecture

Configuration should remain centralized.

Conceptually:

```text
InputBoosterConfig
       ↓
feature settings
       ↓
runtime behavior
```

Do not create unrelated configuration systems for individual features unless there is a strong reason.

Centralized configuration also makes profiles and UI easier to maintain.

---

## 22. Profiles

Profiles are configuration snapshots.

```text
Config
  ↓
capture
  ↓
Profile
  ↓
persist
```

Loading:

```text
Profile
  ↓
apply
  ↓
Config
  ↓
features
```

The profile system should not become a second source of truth.

---

## 23. Module Architecture

The module manager organizes feature-level state.

The following concepts should remain distinct:

```text
configuration state
module state
runtime state
global activation
Safe Mode state
```

For example, a feature may be configured but temporarily inactive because the global system is disabled or Safe Mode is active.

---

## 24. Safe Mode

Safe Mode is a protective subsystem.

Its conceptual flow is:

```text
runtime error
    ↓
record
    ↓
recent error history
    ↓
threshold
    ↓
Safe Mode
    ↓
disable risky processing
```

This prevents repeated failures from continuously affecting input processing.

New core features should consider how their failures interact with Safe Mode.

---

## 25. Diagnostics

Diagnostics include concepts such as:

- latency;
- CPS history;
- FPS history;
- session uptime;
- recovery counts;
- estimated missed inputs;
- polling health;
- event logs.

Diagnostic systems should observe the core pipeline rather than control it.

---

## 26. Measured vs Estimated Data

The architecture must distinguish exact measurements from heuristics.

Examples:

```text
capture timestamp = measured
queue/drain elapsed time = measured
estimated missed inputs = heuristic
```

UI labels and documentation should preserve that distinction.

---

## 27. Logging

Logging must not destroy the performance characteristics of the input system.

Never add unrestricted per-event or per-poll logging to a hot path.

Prefer:

- aggregated counters;
- debug-only logs;
- rate limiting;
- diagnostic snapshots.

---

## 28. UI

The UI is a presentation layer.

Preferred relationship:

```text
Core
 ↓
State / Metrics
 ↓
UI
```

The UI should not become responsible for high-frequency input detection or game-thread synchronization.

The core should remain functional when the UI is not visible.

---

## 29. Focus Loss and Recovery

Focus loss is a state-machine problem.

Potential sequence:

```text
focused
   ↓
focus lost
   ↓
physical input changes
   ↓
Minecraft no longer receives normal input
   ↓
focus regained
```

Recovery logic must prevent stuck states without generating arbitrary or duplicated actions.

When changing focus handling, inspect:

- snapshot state;
- previous state;
- release events;
- queue state;
- Minecraft focus state;
- polling lifecycle.

---

## 30. Performance Architecture

Performance-sensitive components include:

```text
InputPollingThread
InputAction creation
InputActionQueue
InputDrainer
Timestamping
```

For any optimization, consider:

```text
allocation
GC pressure
contention
synchronization
branching
logging
collection traversal
thread wakeups
```

Do not optimize based solely on theoretical assumptions.

---

## 31. Concurrency Architecture

For every shared value, establish:

```text
writer thread
reader thread
mutability
visibility mechanism
lifetime
failure behavior
```

Prefer immutable state where possible.

Use synchronization only where needed and understand its cost.

Never accidentally introduce a data race between the polling and game threads.

---

## 32. Event Ordering

The queue is not merely storage; it represents temporal ordering.

For example:

```text
FORWARD_PRESSED
ATTACK_PRESSED
ATTACK_RELEASED
FORWARD_RELEASED
```

must retain its logical ordering through transport and replay.

Changes to queue behavior must consider ordering guarantees.

---

## 33. Failure Modes

Important failure modes include:

### Queue overflow

Can cause event loss.

### Polling thread failure

Can stop input observation.

### Game-thread delay

Can increase queue latency.

### Vanilla duplicate execution

Can cause repeated actions.

### Focus loss

Can cause stale input state.

### Runtime exception

Can require Safe Mode.

### Excessive allocation

Can increase GC pressure.

### Excessive logging

Can destroy hot-path performance.

A change should consider which of these failure modes it could influence.

---

## 34. Debugging Strategy

Debug from the symptom backward through the pipeline.

```text
Minecraft behavior
        ↑
InputDrainer
        ↑
InputActionQueue
        ↑
InputAction
        ↑
InputPollingThread
        ↑
KeySnapshot
```

For duplicate behavior, check both InputBooster and vanilla paths.

For missing behavior, check snapshot state, edge detection, queue admission, queue consumption, and execution.

For latency, determine where time is being spent before changing polling frequency.

---

## 35. Adding a New Feature

Before creating a new class, ask:

1. Can an existing `InputAction` represent the required event?
2. Can an existing feature manager own the behavior?
3. Can `InputBoosterConfig` hold the configuration?
4. Can `InputDrainer` provide the execution boundary?
5. Does the feature actually require a Mixin?
6. Does the feature need game-thread execution?
7. Does it introduce a new thread?
8. Does it introduce shared state?
9. Does it need metrics?
10. Does it need Safe Mode integration?

Create new infrastructure only when existing infrastructure cannot correctly represent the feature.

---

## 36. Refactoring Rules

Do not perform large architectural refactors merely because code can be made more abstract.

Before refactoring, establish:

- current behavior;
- current callers;
- current thread ownership;
- current invariants;
- current performance characteristics;
- tests covering the behavior.

A refactor must preserve behavior unless the task explicitly requests behavior changes.

---

## 37. Mixin Change Rules

Before changing a Mixin:

```text
verify Minecraft version
verify mappings
verify target method
verify descriptor/signature
verify injection point
verify cancellation behavior
verify interaction with vanilla
verify interaction with InputDrainer
```

Do not copy an old Mixin target from another Minecraft version without verification.

---

## 38. Test Strategy

Core input changes should test:

- attack press/release;
- use press/release;
- movement;
- jump;
- sprint;
- sneak;
- rapid input;
- held input;
- focus loss;
- focus regain;
- low FPS;
- queue pressure;
- replay;
- CPS limiting;
- Safe Mode.

Minecraft-specific changes should additionally verify:

- entity attack;
- item use;
- block interaction;
- continuous block breaking;
- vanilla duplicate suppression.

---

## 39. Repository Navigation Strategy for AI

An AI entering the repository should inspect the project in this order:

```text
1. AI_CONTEXT.md
2. ARCHITECTURE_GUIDE.md
3. build.gradle
4. gradle.properties
5. source tree
6. configuration
7. mixins
8. tests
9. feature modules
10. documentation
```

The purpose is to establish the architectural model before reading isolated implementations.

When investigating a specific feature, trace both its callers and its dependencies.

---

## 40. Change Checklist

Before changing core input code:

```text
[ ] Identify architectural layer
[ ] Identify owning thread
[ ] Identify producers
[ ] Identify consumers
[ ] Check InputAction flow
[ ] Check queue behavior
[ ] Check InputDrainer
[ ] Check vanilla path
[ ] Check duplicate protection
[ ] Check configuration
[ ] Check Safe Mode
[ ] Check replay
[ ] Check metrics
[ ] Check performance
[ ] Check tests
[ ] Verify current Minecraft API
```

---

## 41. Architectural Invariants

These are the primary invariants.

### 001 — Thread Boundary

Polling must not directly execute Minecraft gameplay.

### 002 — Immutable Snapshot

Input state snapshots should be immutable after publication.

### 003 — Semantic Events

Raw state transitions should become `InputAction` events.

### 004 — Central Transport

`InputActionQueue` is the normal thread boundary.

### 005 — Game-Thread Execution

`InputDrainer` executes queued gameplay actions on the game thread.

### 006 — Duplicate Protection

InputBooster must not accidentally execute an action twice through its own path and vanilla's path.

### 007 — Bounded Resources

The event queue remains bounded.

### 008 — Lightweight Hot Path

Polling and queue operations remain efficient.

### 009 — Centralized Configuration

Features use the configuration system instead of scattered values.

### 010 — Reusable Event Pipeline

Replay and features reuse the central event architecture whenever possible.

### 011 — Honest Diagnostics

Measured and estimated values remain distinct.

### 012 — Safe Failure

Repeated core failures can be contained by Safe Mode.

---

## 42. Canonical Mental Model

```text
                         INPUTBOOSTER
                              │
            ┌─────────────────┴─────────────────┐
            │                                   │
       INPUT PIPELINE                    CONTROL PLANE
            │                                   │
            ▼                                   ▼
      KeySnapshot                         Config
            │                                   │
            ▼                                Profiles
   InputPollingThread                         Modules
            │                                   │
            ▼                                   ▼
      InputAction                         Runtime Policy
            │
            ▼
   InputActionQueue
            │
            ▼
      InputDrainer
            │
      ┌─────┼─────┐
      ▼     ▼     ▼
     CPS Movement Replay
      │     │     │
      └─────┼─────┘
            ▼
        Minecraft
            │
            ▼
      Vanilla Hooks
            │
            ▼
   Duplicate Protection

Diagnostics observe the system:

LatencyProfiler
SessionStats
EventLog
DebugOverlay
SafeModeManager
```

---

## 43. Golden Rule

> **Never modify InputBooster as if each file exists independently. Always reason about the complete flow: `KeySnapshot → InputPollingThread → InputAction → InputActionQueue → InputDrainer → Minecraft`, then determine where the requested change belongs.**

Preserve:

- thread boundaries;
- event semantics;
- queue bounds;
- ordering;
- vanilla duplicate protection;
- performance-sensitive hot paths;
- centralized configuration;
- replay compatibility;
- diagnostic correctness;
- Safe Mode;
- current Minecraft compatibility.

Unless the task explicitly requests an architectural redesign, these properties should remain stable.

---

## 44. Final Architecture Statement

InputBooster should remain understandable as one coherent system:

```text
CAPTURE
  ↓
DETECT
  ↓
REPRESENT
  ↓
QUEUE
  ↓
DRAIN
  ↓
POLICY
  ↓
EXECUTE
  ↓
PROTECT
  ↓
MEASURE
```

The implementation may evolve, but the AI or human engineer should always be able to map every major class and feature to one or more of these stages.

If a proposed change cannot be explained in this model, stop and inspect the architecture before implementing it.

---

`IB-ARC-261-001-042`

**END OF ARCHITECTURE GUIDE**
