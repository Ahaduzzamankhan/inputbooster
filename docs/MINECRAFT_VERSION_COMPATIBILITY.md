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
| Options screen layout field | `private final HeaderAndFooterLayout layout` | same | shadowed by `OptionsScreenMixin` |
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

1. **A declared mixin package is a reserved namespace.** Mixin refuses to load
   anything inside `dev.inputbooster.mixin` at runtime unless it is a mixin
   itself, and aborts with
   `IllegalClassLoadError: … is in a defined mixin package dev.inputbooster.mixin.* … cannot be referenced directly`.
   Every class in that package must therefore be listed in
   `inputbooster.mixins.json`. Plain helpers belong outside it, which is why the
   call-site utility is `dev.inputbooster.access.MixinAccess`.
2. **An accessor or invoker mixin that is called from running code must be an
   `interface`, even when its target is a class.** `MixinInfo.getVariant`
   returns the loadable `ACCESSOR` variant only for an interface whose methods
   are all `@Accessor`/`@Invoker`; any other shape is an ordinary, non-loadable
   mixin and casting to it throws the `IllegalClassLoadError` from rule 1.
   Fabric API ships this form: `KeyMappingAccessor` and `ScreenAccessor` are
   interfaces targeting the classes `KeyMapping` and `Screen`.
3. **An interface mixin that carries a non-accessor method needs an interface
   target.** Mixin then rejects a class target with
   `@Mixin target type mismatch: … is not an interface`. So the `@Inject`
   mixins (`GameTickMixin`, `InGameHudMixin`, `OptionsScreenMixin`) target
   classes and must stay classes.
4. A class-form mixin may not declare static methods. Mixin merges everything
   into the target and aborts with
   `contains non-private static method …`. This is a second, independent
   reason the call-site helper lives outside the mixin package. The casts go
   through `Object`, because a mixin is not a compile-time supertype of its
   target.
5. **A lambda that captures nothing inside a class-form mixin violates rule 4.**
   `javac` compiles it to a *static* synthetic method in the enclosing mixin
   class, so `settings -> settings.alignHorizontallyCenter()` is enough to abort
   the game. A lambda that captures `this` becomes an instance synthetic method
   and is fine; so is a method reference to a static method on another class,
   which generates no synthetic member at all. This is why
   `OptionsScreenMixin` applies the layout settings through
   `InputBoosterScreen::anchorEntryToContentBottom` rather than an inline lambda.
6. A mixin may not declare a non-private constructor.
7. Fabric loader 0.19.5 cannot evaluate a bracket range such as `[26.2,26.3)`
   for a two-component Minecraft version, so the declared dependency uses the
   comparator form `">=26.2 <26.3"`.

## Renderer independence (OpenGL and Vulkan)

Minecraft 26.2 and 26.3 can drive the client through OpenGL **or** Vulkan.
Anything that reaches past Minecraft's own abstractions into LWJGL or the GL
bindings only exists on the OpenGL backend, so every drawing path in the mod
uses the abstractions the backend-agnostic render state exposes:

| What | API used | Backend agnostic because |
| ---- | -------- | ----------------------- |
| HUD poll-rate badge | `GuiRenderState#addText` with a `GuiTextRenderState` | The render state records the command; the backend replays it. |
| Settings screen and widgets | `GuiGraphicsExtractor` (`fill`, `text`, …) | Same deferred submission as every vanilla screen. |
| Input polling and queueing | `Window` / `KeyMapping` only | Never reads render state, so timing is identical everywhere. |

Consequences that are enforced by
`fabric/src/test/java/dev/inputbooster/RenderingApiTest.java`:

- No source file and no shipped class may reference `org.lwjgl`,
  `GlStateManager`, `RenderSystem`, `GL11`/`GL20`/`GL30`.
- `org.joml:joml` and `org.lwjgl` are not declared in `fabric/build.gradle`.
  Minecraft ships both; a second JOML on the classpath can differ from the one
  the game already loaded. `org.joml.Matrix3x2f` is still used, but only as the
  pose type `GuiRenderState#addText` requires.
- Sodium, Iris, VulkanMod and every other renderer remain **optional**: none of
  them is a dependency in `fabric.mod.json`, and the mod's mixins do not touch
  the classes they replace.