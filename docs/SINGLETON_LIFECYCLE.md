# Service Lifecycle

This branch no longer treats gameplay state as a web of global singletons. The engine is now split into a small process-level service root plus an explicit gameplay runtime. This document describes the current model and the few compatibility boundaries that still exist while the migration closes.

## 1. Architecture Overview

OpenGGF now uses three access patterns, each with a different scope.

| Layer | Entry point | Scope | Intended users |
|---|---|---|---|
| Process services | `EngineServices` | One per process/bootstrap | engine bootstrap, process-global managers |
| Gameplay runtime | `GameRuntime` | One per gameplay session | runtime-owned mutable state |
| Static/runtime facade | `GameServices` | façade over current runtime + engine services | non-object gameplay code |
| Per-object injection | `ObjectServices` | object-scoped handle | `AbstractObjectInstance` subclasses |

### Process-level services

These services are not recreated on every level load. Typical examples:

- `SonicConfigurationService`
- `GraphicsManager`
- `AudioManager`
- `RomManager`
- `PerformanceProfiler`
- `DebugOverlayManager`
- `PlaybackDebugManager`
- `RomDetectionService`
- `CrossGameFeatureProvider`

They are assembled into `EngineServices` and installed through `RuntimeManager` during bootstrap.

### Runtime-owned gameplay state

These services live inside `GameRuntime` and are recreated when gameplay is torn down and rebuilt:

- `Camera`
- `LevelManager`
- `SpriteManager`
- `GameStateManager`
- `TimerManager`
- `FadeManager`
- `CollisionSystem`
- `TerrainCollisionManager`
- `WaterSystem`
- `ParallaxManager`

This is the state that used to be singleton-heavy. Production code should now reach it through `GameServices` or `ObjectServices`, not through `getInstance()` or direct `RuntimeManager.getCurrent()` lookups.

## 2. Access Rules

### Non-object code

Managers, controllers, level/event code, HUD code, and other non-object runtime logic should use `GameServices`:

```java
Camera camera = GameServices.camera();
LevelManager level = GameServices.level();
GameStateManager gameState = GameServices.gameState();
AudioManager audio = GameServices.audio();
```

Use the `*OrNull()` variants only when code genuinely supports the absence of an active runtime:

```java
LevelManager level = GameServices.levelOrNull();
```

### Object code

Anything extending `AbstractObjectInstance` should use injected `ObjectServices`:

```java
services().camera()
services().objectManager()
services().audioManager()
services().gameState()
services().gameModule()
```

Object code should not call:

- `Foo.getInstance()`
- `RuntimeManager.getCurrent()`
- `RuntimeManager.getEngineServices()`
- `GameServices.*` when the injected object handle is available

The main exception is framework code inside `AbstractObjectInstance` / `DefaultObjectServices`, where the object-layer bridge itself is implemented.

## 3. Lifecycle

### Engine bootstrap

1. The engine assembles process-global services.
2. Those services are wrapped in `EngineServices`.
3. `RuntimeManager` installs the engine-services root.
4. `RuntimeManager.createGameplay()` creates a fresh `GameRuntime` and `WorldSession`.

### Gameplay reset

Destroying gameplay should rebuild runtime-owned state, not null out process services. The normal flow is:

1. reset module/session state as needed
2. destroy the current `GameRuntime`
3. create a fresh `GameRuntime`
4. load the requested ROM/zone/module state into the new runtime

### Level/object construction

`ObjectManager` is the construction boundary for object DI:

1. it creates the object
2. it installs `ObjectServices`
3. the object runs update/render logic against injected services

If an object needs service-dependent behavior during construction, it must use the construction-context bridge supplied by `AbstractObjectInstance`. It must not escape to `GameServices`.

## 4. Compatibility Boundaries

The migration is not yet fully at the ideal end state. A small amount of compatibility scaffolding still exists:

- `EngineServices.fromLegacySingletonsForBootstrap()` remains the temporary bootstrap bridge for process services.
- Some process-global classes still expose `getInstance()` for compatibility, but new production code should not depend on those accessors.

Treat those APIs as migration boundaries, not endorsed architecture.

## 5. Testing Guidance

### Preferred runtime reset

Tests should use the shared reset/runtime helpers instead of manually reassembling engine state in arbitrary order.

Use:

- `TestEnvironment.resetAll()` for full environment reset
- `RuntimeManager.createGameplay()` only when a test is explicitly validating runtime creation
- `@RequiresRom(...)` for ROM-backed tests once the Jupiter migration is complete

### Headless gameplay tests

Headless tests still follow the same runtime model:

1. reset environment/runtime
2. initialize headless graphics if required
3. create/load gameplay state
4. drive the test through `HeadlessTestRunner` or explicit runtime APIs

Prefer examples like:

```java
GraphicsManager graphics = GameServices.graphics();
LevelManager level = GameServices.level();
Camera camera = GameServices.camera();
```

Avoid stale singleton-era setup snippets like:

```java
GraphicsManager.getInstance()
LevelManager.getInstance()
Camera.getInstance()
```

## 6. Guardrails

The migration is enforced by source/bytecode guards in test code. The important ones are:

- `TestRuntimeSingletonGuard`
- `TestProductionSingletonClosureGuard`
- `TestObjectServicesMigrationGuard`
- `TestNoServicesInObjectConstructors`

When these fail, the correct fix is normally one of:

1. move access to `GameServices`
2. move access to injected `ObjectServices`
3. inject the dependency directly
4. if and only if it is a real bootstrap boundary, document the exception explicitly

Weakening a guard to preserve a convenience singleton path is usually the wrong move.
