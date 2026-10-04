# Minecraft 26.2 vs 26.3 compatibility

Everything the mod touches was compared between the two client jars with
`javap`. Only the entries below differ; everything else the mod uses is
identical in both versions, which is why one shared core
(`src/main/java`) serves both.

Build a branch for one version only:

| Branch | `fabric/gradle.properties` | Jar |
| ------ | ------------------------- | --- |
| `fabric-26.2` | `minecraft_version=26.2` | `inputbooster-<version>-fabric-mc262.jar` |
| `fabric-26.3` | `minecraft_version=26.3` | `inputbooster-<version>-fabric-mc263.jar` |

The per-version API differences are isolated in `src/compat/mc26_2/java` and
`src/compat/mc26_3/java` (`dev.inputbooster.compat.McVersion`); the build picks
one of the two source directories from `minecraft_version`.

## Differences that matter

| Area | Minecraft 26.2 | Minecraft 26.3 | Handled by |
| ---- | -------------- | -------------- | ---------- |
| Raw key state | `InputConstants.isKeyDown(Window, int)` | `InputConstants.isKeyDown(int)` (the window parameter is gone) | `McVersion.isKeyDown` |
| Arm swing | `LivingEntity.swing(InteractionHand)` | `LivingEntity.swing(InteractionHand, SwingAnimation, boolean)` | `McVersion.swingArm` |
| Drop item | `Entity.drop(ItemStack, boolean, boolean)` | `Entity.drop(ItemStack, boolean, Prediction)` | `McVersion.dropHeldItem` |
| Prediction type | class does not exist | `net.minecraft.util.Prediction` | 26.3 shim only |
| LocalPlayer drop helper | `LocalPlayer.drop(boolean)` exists | removed | not used |
| Options screen ctor | `(Screen, Options, boolean)` | `(Screen, Options)` | not constructed by the mod, only `init` is injected |
| Screen narration | `runNarration(boolean)`, `updateNarratorStatus(boolean)` | both take a `NarrationTrigger` | not used |
| Backend/graphics internals | `com.mojang.blaze3d.systems.GpuSurface`, `authlib…yggdrasil` | `com.mojang.renderpearl…`, `authlib…services` | not used |
| Tracy instrumentation | absent | `jtracy` sections in `Minecraft` / `Gui` | not used |

## Verified identical in both

- `Minecraft`: `gui`, `font`, `options`, `player`, `level`, `hitResult`,
  `gameMode`, `getWindow()`, `setScreenAndShow`, `startAttack`, `startUseItem`,
  `getDebugOverlay()`, `getFps()`, `pickBlockOrEntity()` (private, invoked
  through a mixin), `close()`.
- `KeyMapping`: `protected Key key`, `setKey`, `getDefaultKey`, `matches`,
  `isDown`, `Category.MISC`, the `InputConstants.KEY_*` and `MOUSE_BUTTON_*`
  constants.
- `Gui`: `guiRenderState` (private, read through a mixin), `screen()`,
  `extractRenderState(DeltaTracker, boolean, boolean)`.
- `Options`: `keyAttack`, `keyUse`, `keyPickItem`, `keyJump`, `keySprint`,
  `keyShift`, `keyLeft`, `keyRight`, `keyDrop`, `keySwapOffhand`.
- `GuiRenderState` / `GuiTextRenderState`: same API, so the F3 overlay renders
  identically on both.

## Mixin rules that are version independent

These caused real launch crashes and are now enforced by
`fabric/src/test/java/dev/inputbooster/MixinDeclarationTest.java`:

1. A mixin whose target is a class must be an `abstract class`, never an
   `interface`. An interface-form mixin fails with
   `@Mixin target type mismatch: … is not an interface`.
2. A class-form mixin may not declare static methods. Mixin merges everything
   into the target and aborts with
   `contains non-private static method …`. Call sites therefore use
   `dev.inputbooster.mixin.MixinAccess`, a plain utility class, and the casts go
   through `Object` because a class-form mixin is not a compile-time supertype
   of its target.
3. A mixin may not declare a non-private constructor.
4. Fabric loader 0.19.5 cannot evaluate a bracket range such as `[26.2,26.3)`
   for a two-component Minecraft version, so the declared dependency uses the
   comparator form `">=26.2 <26.3"`.