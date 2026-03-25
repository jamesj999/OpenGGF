# Singleton Lifecycle

This document describes the singleton architecture used throughout the engine: which classes
follow the pattern, how they are initialized, how they are torn down for testing, and what
thread-safety guarantees they provide.

## 1. Singleton Inventory

The table below covers the ~20 core singletons. Game-screen-specific singletons
(TitleScreenManager, TitleCardManager, SpecialStageManager, LevelSelectManager, etc.) follow
the same pattern but are omitted — they are created on first use and reset via their own
`reset()` methods when the screen exits.

| Class | Package | Reset method | Thread-safe getInstance? | Category |
|---|---|---|---|---|
| `Engine` | `com.openggf` | none | `synchronized` | Core |
| `SonicConfigurationService` | `com.openggf.configuration` | none | `synchronized` | Core |
| `GameModuleRegistry` | `com.openggf.game` | `reset()` (static) | `synchronized` (getCurrent/setCurrent) | Core |
| `GameStateManager` | `com.openggf.game` | `resetState()` / `resetSession()` | `synchronized` | Core |
| `CrossGameFeatureProvider` | `com.openggf.game` | `resetState()` | `synchronized` | Core |
| `RomManager` | `com.openggf.data` | none | `synchronized` | Core |
| `AudioManager` | `com.openggf.audio` | `resetState()` | `synchronized` | Core |
| `GraphicsManager` | `com.openggf.graphics` | `resetState()` | `synchronized` | Core |
| `FadeManager` | `com.openggf.graphics` | `resetState()` | `synchronized` | Core |
| `Camera` | `com.openggf.camera` | `resetState()` | `synchronized` | Core |
| `LevelManager` | `com.openggf.level` | `resetState()` | `synchronized` | Core |
| `ParallaxManager` | `com.openggf.level` | `resetState()` | `synchronized` | Core |
| `WaterSystem` | `com.openggf.level` | `reset()` | none (double-check null) | Core |
| `SpriteManager` | `com.openggf.sprites.managers` | `resetState()` | `synchronized` | Core |
| `CollisionSystem` | `com.openggf.physics` | `resetState()` | `synchronized` | Core |
| `TerrainCollisionManager` | `com.openggf.physics` | `resetState()` | `synchronized` | Core |
| `TimerManager` | `com.openggf.timer` | `resetState()` | `synchronized` | Core |
| `PerformanceProfiler` | `com.openggf.debug` | `reset()` | `synchronized` | Core |
| `DebugOverlayManager` | `com.openggf.debug` | none | `synchronized` | Core |
| `MemoryStats` | `com.openggf.debug` | none | none (static final INSTANCE) | Core |
| `PlaybackDebugManager` | `com.openggf.debug.playback` | none | none (static final INSTANCE) | Core |
| `RenderOrderRecorder` | `com.openggf.graphics.pipeline` | none | `synchronized` | Core |

**Category definitions:**

- **Core** — always active; created at most once per JVM run in production. Persist across
  level loads; only their internal state is reset between levels.
- **Game-Scoped** — created by a `GameModule` implementation and accessed via provider
  interfaces (e.g. `ZoneFeatureProvider`, `ScrollHandlerProvider`). Destroyed and recreated
  when `GameModuleRegistry.reset()` replaces the module.
- **Screen-Scoped** — screen managers (title, level select, special stage, etc.) that are
  lazy-created on first access and hold a `reset()` method called when the screen exits via
  `GameLoop`.

**Reset method naming conventions (standardized in Task 4):**

- `resetState()` — the preferred name; clears mutable state without destroying the singleton.
- `resetSession()` — `GameStateManager`-specific alias; delegates to `resetState()`.
- `reset()` — used by `WaterSystem`, `PerformanceProfiler`, and several screen-scoped classes.
- `resetInstance()` — **deprecated** on `Camera`, `CollisionSystem`, and `FadeManager`; sets
  the static field to null, destroying the instance. Avoid in new code; use `resetState()`
  so cached references remain valid.

## 2. Initialization Order

### Eager initialization (Engine field declarations, lines 53–82)

These singletons are created the moment `Engine` is instantiated, before `init()` or
`run()` is called:

1. `SonicConfigurationService.getInstance()` — reads `config.json`; used by `Engine`
   constructor for window/FPS config.
2. `SpriteManager.getInstance()`
3. `GraphicsManager.getInstance()`
4. `Camera.getInstance()`
5. `PerformanceProfiler.getInstance()`
6. `LevelManager.getInstance()`

`DebugRenderer` is intentionally **not** eagerly captured — its static initializer
references `java.awt.Color`, which is unavailable in GraalVM native-image builds. It is
lazy-created on first `getDebugRenderer()` call.

### Lazy initialization (all other singletons)

Every other singleton is created on first `getInstance()` call. The typical pattern is:

```java
private static SomeThing instance;

public static synchronized SomeThing getInstance() {
    if (instance == null) {
        instance = new SomeThing();
    }
    return instance;
}
```

`WaterSystem` and the two static-final singletons (`MemoryStats`, `PlaybackDebugManager`)
use class-loading for initialization instead of a null check.

### GameModuleRegistry default

`GameModuleRegistry` holds `private static GameModule current = new Sonic2GameModule()`
as a field initializer — Sonic 2 is always the fallback module until ROM detection or
an explicit `setCurrent()` call.

## 3. Reset Graph

### `GameContext.forTesting()` — full teardown sequence

`GameContext.forTesting()` is the canonical way to reset all engine state between integration
tests. It executes in this order:

1. **Increment generation counter** — invalidates any previously issued `GameContext`
   references (stale references throw `IllegalStateException` on next access).

2. **Capture current game's `LevelInitProfile`** — captured BEFORE resetting the module
   registry, so the *previous* game's teardown cleans up its own state (e.g. S3K resets its
   own level event manager, not S2's).

3. **`GameModuleRegistry.reset()`** — replaces the current module with a fresh
   `Sonic2GameModule` instance (the default).

4. **Execute `profile.levelTeardownSteps()`** — the full teardown sequence from
   `AbstractLevelInitProfile.levelTeardownSteps()` (see table below).

5. **Execute `profile.postTeardownFixups()`** — game-specific static fixups (e.g. resetting
   VRAM tile assignments that are not covered by the standard steps).

6. **Return `production()`** — wraps the now-reset singletons as a fresh `GameContext`.

### `AbstractLevelInitProfile.levelTeardownSteps()` — step-by-step

These 13 steps execute in the order listed. Each step corresponds to the inverse of a ROM
initialization phase (documented in the Javadoc table in `AbstractLevelInitProfile`).

| # | Step name | Method called | Undoes ROM phase |
|---|---|---|---|
| 1 | `ResetAudio` | `GameServices.audio().resetState()` | PlayMusic / bgm_Fade (S1:D, S2:C, S3K:F) |
| 2 | `ResetCrossGameFeatures` | `CrossGameFeatureProvider.getInstance().resetState()` | CrossGameFeatureProvider.initialize() |
| 3 | *(game-specific)* | `levelEventTeardownStep()` — subclass hook | Zone event handlers, boss arena state |
| 4 | `ResetParallax` | `ParallaxManager.getInstance().resetState()` | DeformBgLayer/DeformLayers (S1:G, S2:E, S3K:H) |
| 5 | `ResetLevelManager` | `GameServices.level().resetState()` | LevelDataLoad/LoadZoneTiles (S1:G, S2:E, S3K:I) |
| 6 | `ResetSprites` | `SpriteManager.getInstance().resetState()` | InitPlayers/SpawnLevelMainSprites (S1:I-J, S2:G, S3K:O) |
| 7 | `ResetCollision` | `CollisionSystem.getInstance().resetState()` | ConvertCollisionArray/LoadSolids (S1:H, S2:F, S3K:K) |
| 8 | `ResetCamera` | `GameServices.camera().resetState()` | LevelSizeLoad/Get_LevelSizeStart (S1:G, S2:E, S3K:H) |
| 9 | `ResetGraphics` | `GraphicsManager.getInstance().resetState()` | VDP register config (S1:B, S2:B, S3K:D) |
| 10 | `ResetFade` | `GameServices.fade().resetState()` | PaletteFadeOut/Pal_FadeToBlack (S1:A, S2:A, S3K:A) |
| 11 | `ResetGameState` | `GameServices.gameState().resetState()` | Ring/timer/lives init (S1:K, S2:H, S3K:N) |
| 12 | `ResetTimers` | `TimerManager.getInstance().resetState()` | Level_frame_counter/demo timer (S1:J, S2:I, S3K:P) |
| 13 | `ResetWater` | `WaterSystem.getInstance().reset()` | LZWaterFeatures/WaterEffects (S1:C, S2:B, S3K:E+L) |

### `AbstractLevelInitProfile.perTestResetSteps()` — lightweight per-test reset

`perTestResetSteps()` is a subset of the above — it skips `ResetLevelManager` and
`ResetGraphics` so that loaded level geometry and OpenGL resources are preserved between
individual test cases within the same level. Steps executed:

- game-specific lead step (`perTestLeadStep()`)
- `ResetCrossGameFeatures`
- `ResetParallax`
- `ResetSprites`
- `ResetCollision`
- `ResetCamera`
- `ResetFade`
- `ResetGameState`
- `ResetTimers`
- `ResetWater`

### `CollisionSystem.resetState()` cascades

`CollisionSystem.resetState()` calls `TerrainCollisionManager.getInstance().resetState()`
internally. Callers do not need to reset `TerrainCollisionManager` separately.

## 4. Thread-Safety Contract

The engine is **primarily single-threaded**: the game loop, physics, rendering, and all
singleton mutation happen on the main thread. Audio runs on a separate thread managed by
`LWJGLAudioBackend`.

### What is synchronized

All core singletons use `synchronized getInstance()` (see table in section 1). This guards
only the lazy-creation path; subsequent method calls on the returned instance are **not**
synchronized and must only be called from the main thread.

`GameModuleRegistry.getCurrent()` and `setCurrent()` are both `synchronized` because ROM
detection (which calls `setCurrent`) can run during startup before the main loop is stable.

### What is not synchronized

`WaterSystem.getInstance()` uses a plain null check without `synchronized` — safe only
because it is always called from the main thread before the audio thread starts.

`MemoryStats` and `PlaybackDebugManager` use a `static final INSTANCE` field initialized
at class-load time, which the JVM guarantees to be thread-safe.

### Engine `volatile` fields

`Engine.debugState` and `Engine.debugOption` are declared `volatile`:

```java
private static volatile DebugState debugState = DebugState.NONE;
private static volatile DebugOption debugOption = DebugOption.A;
```

These are written by keyboard callbacks (GLFW callback thread) and read by the main loop,
so `volatile` is required for visibility.

## 5. HeadlessTestRunner Setup Checklist

Tests that use `HeadlessTestRunner` must perform manual singleton reset in this exact order.
The ordering is load-bearing: later steps depend on earlier ones having completed.

As documented in `HeadlessTestRunner.java`:

1. **Reset `GraphicsManager` and `Camera`**
   ```java
   GraphicsManager.getInstance().resetState();
   Camera.getInstance().resetState();
   ```
   Clears OpenGL state and camera bounds/frozen flags left over from any previous test.
   `Camera.resetState()` must happen before level load because a camera with `frozen=true`
   (left from a death sequence) will silently ignore `updatePosition()` calls.

2. **Initialize headless graphics**
   ```java
   GraphicsManager.getInstance().initHeadless();
   ```
   Stubs out OpenGL calls so physics tests can run without a display context.

3. **Load level**
   ```java
   LevelManager.getInstance().loadZoneAndAct(zone, act);
   ```
   Populates collision indices, level geometry, and camera bounds.

4. **Fix `GroundSensor` static field**
   ```java
   GroundSensor.setLevelManager(LevelManager.getInstance());
   ```
   `GroundSensor` holds a static reference to `LevelManager`. This field is set during
   normal level loading, but tests that load levels manually must set it explicitly,
   **after** `loadZoneAndAct()` has returned.

5. **Snap camera position**
   ```java
   Camera.getInstance().updatePosition(true);
   ```
   Must be called **after** level load because `loadZoneAndAct()` sets the camera bounds
   during load. Calling before load produces incorrect bounds.

For tests involving level events (e.g. HTZ earthquake), `HeadlessTestRunner.stepFrame()`
must also be preceded by the caller setting up `LevelEventManager.initLevel(zone, act)` —
see the MEMORY.md HTZ test setup notes.
