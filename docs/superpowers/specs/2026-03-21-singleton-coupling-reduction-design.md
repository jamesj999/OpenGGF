# Singleton Coupling Reduction: GameServices Expansion + ObjectServices

**Date:** 2026-03-21
**Status:** Draft
**Branch:** feature/ai-common-utility-refactors

## Problem

The engine has 44 singletons with ~1,650 `getInstance()` calls. The worst coupling is in the object/badnik layer: 400+ object files call `LevelManager.getInstance()` (~600 refs) and `AudioManager.getInstance()` (~380 refs) to reach sub-managers they could receive at construction time. This creates:

- Fragile test setups requiring manual singleton reset sequences
- Dependency inversion violations (game-agnostic objects importing game-specific singletons)
- Hidden dependencies that resist refactoring

## Solution Overview

Two complementary changes targeting different layers:

1. **GameServices facade expansion** — static accessors for the remaining high-traffic singletons (AudioManager, Camera, LevelManager, FadeManager). For non-factory code: event handlers, scroll handlers, title screens, GameLoop.
2. **ObjectServices interface** — injectable service handle for objects created by ObjectManager. Replaces direct singleton access in AbstractObjectInstance and AbstractBadnikInstance.

## Strategy 1: GameServices Facade Expansion

### Current State

```java
public final class GameServices {
    public static GameStateManager gameState() { return GameStateManager.getInstance(); }
    public static TimerManager timers()        { return TimerManager.getInstance(); }
    public static RomManager rom()             { return RomManager.getInstance(); }
    public static DebugOverlayManager debugOverlay() { return DebugOverlayManager.getInstance(); }
}
```

### Change

Add four accessors:

```java
public static AudioManager audio()    { return AudioManager.getInstance(); }
public static Camera camera()         { return Camera.getInstance(); }
public static LevelManager level()    { return LevelManager.getInstance(); }
public static FadeManager fade()      { return FadeManager.getInstance(); }
```

### Migration

Incremental. Both `AudioManager.getInstance()` and `GameServices.audio()` work simultaneously. Files migrate as they are touched for other work, or in bulk batches per package.

### Scope

Event handlers, scroll handlers, title screens, level event managers, GameLoop, Engine — any code that legitimately needs global manager access and is not created by ObjectManager.

## Strategy 2: ObjectServices Interface

### Interface

Located in `com.openggf.level.objects`:

```java
public interface ObjectServices {
    ObjectManager objectManager();
    ObjectRenderManager renderManager();
    LevelGamestate levelGamestate();
    int currentZone();
    int currentAct();
    void playSfx(int soundId);
}
```

This exposes only what objects actually need from LevelManager and AudioManager, based on grep analysis of ~600 call sites.

### Default Implementation

Located in `com.openggf.level.objects`:

```java
public class DefaultObjectServices implements ObjectServices {
    @Override
    public ObjectManager objectManager() {
        return LevelManager.getInstance().getObjectManager();
    }

    @Override
    public ObjectRenderManager renderManager() {
        return LevelManager.getInstance().getObjectRenderManager();
    }

    @Override
    public LevelGamestate levelGamestate() {
        return LevelManager.getInstance().getLevelGamestate();
    }

    @Override
    public int currentZone() {
        return LevelManager.getInstance().getCurrentZone();
    }

    @Override
    public int currentAct() {
        return LevelManager.getInstance().getCurrentAct();
    }

    @Override
    public void playSfx(int soundId) {
        AudioManager.getInstance().playSfx(soundId);
    }
}
```

### Injection Point

`ObjectManager.syncActiveSpawns()` is the single creation point for all objects (line 548). After `registry.create(spawn)` returns the instance, ObjectManager calls `setServices()`:

```java
ObjectInstance instance = registry.create(spawn);
if (instance instanceof AbstractObjectInstance aoi) {
    aoi.setServices(objectServices);
}
```

ObjectManager holds one `DefaultObjectServices` instance, created in its constructor or set via a setter.

### AbstractObjectInstance Changes

```java
// New field and accessor
private ObjectServices services;

public void setServices(ObjectServices services) {
    this.services = services;
}

protected ObjectServices services() {
    return services;
}
```

The existing static helper methods (`spawnDynamicObject()`, `isPlayerRiding()`, `getRenderer()`, `getRenderManager()`) remain as fallbacks during migration but are candidates for deprecation once objects use `services()`.

### AbstractBadnikInstance Changes

Remove the `protected final LevelManager levelManager` field and the constructor parameter that accepts it. The two existing constructors:

```java
// Before (4-arg)
protected AbstractBadnikInstance(ObjectSpawn spawn, LevelManager levelManager,
        String name, DestructionConfig destructionConfig)

// Before (3-arg, backwards compat)
protected AbstractBadnikInstance(ObjectSpawn spawn, LevelManager levelManager, String name)
```

Become:

```java
// After (3-arg)
protected AbstractBadnikInstance(ObjectSpawn spawn, String name,
        DestructionConfig destructionConfig)

// After (2-arg)
protected AbstractBadnikInstance(ObjectSpawn spawn, String name)
```

Subclasses that currently pass `LevelManager.getInstance()` to super stop doing so. Code that accessed `levelManager.getObjectManager()` migrates to `services().objectManager()`.

`DestructionEffects.destroyBadnik()` currently receives `LevelManager` as a parameter. Change it to accept `ObjectServices` instead.

### Factory Signature

`ObjectFactory` remains unchanged:

```java
ObjectInstance create(ObjectSpawn spawn, ObjectRegistry registry);
```

Services are injected post-construction by ObjectManager, not passed through the factory. This avoids changing every factory lambda across all three game registries.

### Dynamic Object Spawning

Objects that spawn children (e.g., EggPrison spawning animals, Turtloid firing projectiles) currently call `LevelManager.getInstance().getObjectManager().addDynamicObject(child)`. With ObjectServices:

```java
services().objectManager().addDynamicObject(child);
```

The child object also needs services. ObjectManager already handles this — `addDynamicObject()` flows through the same path that calls `setServices()`.

Verify: `ObjectManager.addDynamicObject()` must call `setServices()` on the new object, same as `syncActiveSpawns()` does. This is a one-line addition to the existing `addDynamicObject()` method.

### Test Usage

Tests can create a stub implementation:

```java
ObjectServices stubServices = new ObjectServices() {
    @Override public ObjectManager objectManager() { return mockOm; }
    @Override public ObjectRenderManager renderManager() { return null; }
    @Override public LevelGamestate levelGamestate() { return mockState; }
    @Override public int currentZone() { return 0; }
    @Override public int currentAct() { return 0; }
    @Override public void playSfx(int soundId) { /* no-op */ }
};
object.setServices(stubServices);
```

No singleton reset required. Objects become testable in isolation.

## Migration Strategy

Both patterns coexist during migration. No big-bang change required.

### Phase 1: Infrastructure (non-breaking)
1. Add four accessors to `GameServices`
2. Create `ObjectServices` interface and `DefaultObjectServices`
3. Add `services` field + setter + accessor to `AbstractObjectInstance`
4. Wire `ObjectManager` to inject services after object creation (both `syncActiveSpawns` and `addDynamicObject`)

### Phase 2: AbstractBadnikInstance migration (breaking for badnik constructors)
1. Remove `LevelManager` constructor parameter from `AbstractBadnikInstance`
2. Update `DestructionEffects.destroyBadnik()` to accept `ObjectServices`
3. Update all badnik subclass constructors across S1, S2, S3K
4. Update all badnik factory registrations to stop passing `LevelManager.getInstance()`

### Phase 3: Incremental object migration (non-breaking, file-by-file)
- Migrate individual object files from `LevelManager.getInstance().getXxx()` to `services().xxx()`
- Migrate `AudioManager.getInstance().playSfx(x)` to `services().playSfx(x)` in objects
- Each file is independent — can be done alongside other work

### Phase 4: Incremental non-object migration (non-breaking, file-by-file)
- Migrate event handlers, scroll handlers, etc. from `AudioManager.getInstance()` to `GameServices.audio()`
- Migrate `LevelManager.getInstance()` to `GameServices.level()` in non-object code

## Files Modified

### New files
- `src/main/java/com/openggf/level/objects/ObjectServices.java`
- `src/main/java/com/openggf/level/objects/DefaultObjectServices.java`

### Modified files (Phase 1)
- `GameServices.java` — add 4 accessors
- `AbstractObjectInstance.java` — add services field/setter/accessor
- `ObjectManager.java` — inject services after object creation

### Modified files (Phase 2)
- `AbstractBadnikInstance.java` — remove LevelManager field and constructor param
- `DestructionEffects.java` — change LevelManager param to ObjectServices
- All badnik subclasses (~60 files across S1/S2/S3K) — update constructor calls
- All badnik factory registrations in `Sonic1ObjectRegistry`, `Sonic2ObjectRegistry`, `Sonic3kObjectRegistry`

### Modified files (Phase 3-4, incremental)
- ~400 object files, migrated individually
- ~50 non-object files for GameServices facade migration

## What This Does NOT Change

- No singleton is removed. DefaultObjectServices delegates to the same singletons.
- No DI framework introduced.
- ObjectFactory signature unchanged.
- Engine.java, GameLoop.java, LevelManager.java internals unchanged.
- GameContext.forTesting() continues to work as-is.

## Success Criteria

- All existing tests pass with no changes to test logic
- New objects can be tested without singleton resets by passing stub ObjectServices
- AbstractBadnikInstance no longer holds a LevelManager reference
- Object files migrated in Phase 3 have zero `LevelManager.getInstance()` calls
