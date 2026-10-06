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
| In-game rendering | none | The mod draws nothing: no HUD, no overlay, no render state, so there is no backend-specific code to get wrong. |
| Settings screen and widgets | vanilla `Screen` / `OptionsSubScreen` only | Vanilla widgets are submitted through the same deferred path on every backend. |
| Input | none | No input is intercepted, sampled or queued, so every key press costs exactly what it costs without the mod. |

Consequences that are enforced by
`fabric/src/test/java/dev/inputbooster/SilentModTest.java`:

- No source file and no shipped class may reference `org.lwjgl`,
  `GlStateManager`, `RenderSystem`, `GL11`/`GL20`/`GL30`.
- `org.joml:joml` and `org.lwjgl` are not declared in `fabric/build.gradle`.
- Sodium, Iris, VulkanMod and every other renderer remain **optional**: none of
  them is a dependency in `fabric.mod.json`, and the mod's single mixin touches
  neither rendering nor chunking.
- `org.joml` and `org.lwjgl` are not declared in `fabric/build.gradle`.
  Minecraft ships both, and the mod no longer needs either: it constructs no
  pose matrix because it draws nothing.

## Optional mod integrations

Other mods that replace the options screen are supported the same way:
reflectively, and never as a dependency.

| Mod | What InputBooster does |
| --- | ------------------------ |
| Sodium | Adds itself to Sodium's options **sidebar** next to the entries other mods register there, and opens the same settings screen. |

`dev.inputbooster.integration.SodiumOptionsBridge` is the whole integration:

- Sodium is detected with `FabricLoader.isModLoaded("sodium")`; without it the
  method returns immediately.
- Every Sodium type is reached by name through reflection, and the
  `ConfigEntryPoint` interface is implemented with a `java.lang.reflect.Proxy`,
  so nothing Sodium-specific is on the compile-time classpath. The mod cannot
  fail to load on a vanilla install.
- Registration goes through Sodium's own
  `ConfigManager#registerConfigEntryPoint`, the same call its
  `ConfigLoaderFabric` makes for mods that declare the `sodium:config_api_user`
  entrypoint. A mod cannot declare that entrypoint itself, because Fabric would
  try to load the class on a vanilla install and fail.
- The page is built in `registerConfigLate`, not `registerConfigEarly`: the
  early phase runs from Sodium's own entrypoint, which may already have happened
  by the time this mod starts, whereas the late phase runs from a `Minecraft`
  mixin during game load — after every client entrypoint.
- The sidebar entry is an *external* page, which just opens InputBooster's own
  screen, so the settings keep their real widgets rather than being
  re-expressed through Sodium's option model.
- Every failure is caught and logged once. A Sodium version that moves its API
  costs the sidebar entry and nothing else.

Two reflection rules are not optional, and breaking either one ends the game
rather than losing the sidebar entry:

1. **Read a hook from the public API type, never from `target.getClass()`.**
   Sodium returns `ModOptionsBuilderImpl`, which is *package-private*. A method
   read off that class reports `Modifier.PUBLIC` and `Method.invoke` still
   refuses to call it, because the declaring class is unreachable from
   InputBooster's package. The 4.0.0-alpha release shipped this bug and the
   client died on launch with
   `IllegalAccessException: … cannot access a member of class
   …ModOptionsBuilderImpl with modifiers "public"`. Every hook therefore comes
   from the public interface that declares it - `ConfigBuilder`,
   `ModOptionsBuilder`, `ExternalPageBuilder`.
2. **The `ConfigEntryPoint` proxy handler must not propagate.** Sodium calls
   `registerConfigLate` inside a handler whose catch ends the game with
   `ConfigManager#crashWithMessage`, so an escaping exception becomes a crash
   report. The handler absorbs `Throwable`, logs it once, and returns `null` for
   hooks it does not recognise.

Enforced by `fabric/src/test/java/dev/inputbooster/SodiumIntegrationTest.java`
(source-level rules) and
`fabric/src/test/java/dev/inputbooster/integration/SodiumBridgeAccessTest.java`
(the two rules exercised at runtime against a package-private fake).