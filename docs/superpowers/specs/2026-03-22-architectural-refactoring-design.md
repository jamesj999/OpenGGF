# Architectural Refactoring Design: Singleton Lifecycle, LevelManager Decomposition, and Cycle Breaking

**Date:** 2026-03-22
**Branch:** `feature/ai-architectural-refactoring`
**Status:** Draft

## Overview

Three phased refactors to address the top architectural concerns identified in a comprehensive codebase review:

1. **Phase 1 — Singleton Lifecycle Completion**: Fill `resetState()` gaps, deprecate `resetInstance()`, add test enforcement
2. **Phase 2 — LevelManager Decomposition**: Extract 3 focused classes from the 5,068-line god class
3. **Phase 3 — Cycle Breaking**: Introduce `PlayableEntity` and `PowerUpSpawner` interfaces to break the `sprites ↔ level` circular dependency

Phases are independently shippable. Phase 1 delivers immediate test reliability improvements; Phases 2-3 build on the stable test infrastructure.

## Motivation

- **Test reliability regression**: Singleton state leaking between tests due to incomplete reset coverage and ad-hoc reset calls
- **LevelManager god class**: 5,068 lines, 126 public methods, 80+ fields, 81 imports spanning 11 packages — high cognitive load, hard to modify safely
- **Circular dependencies**: 3 confirmed cycles (`sprites ↔ level`, `physics ↔ sprites`, `game ↔ level`) prevent independent subsystem testing and increase change ripple risk

## Non-Goals

- Full dependency injection framework (e.g., Guice, Spring)
- Breaking the `physics ↔ sprites` cycle (natural coupling, low risk)
- Refactoring SmpsSequencer, PlayableSpriteMovement, or GameLoop (separate efforts)
- Changing ROM-accurate physics or collision behavior

---

## Phase 1: Singleton Lifecycle Completion

### Context

The codebase has mature reset infrastructure:
- `LevelInitProfile` defines ordered teardown (12 steps) and per-test reset (8 steps) sequences
- `AbstractLevelInitProfile` implements the canonical teardown order matching ROM init phase inverses
- `GameContext.forTesting()` orchestrates full resets via the profile steps
- `TestEnvironment` provides `resetAll()` and `resetPerTest()` entry points

The pattern is migrating from `resetInstance()` (destroy-and-recreate) to `resetState()` (clear-in-place). FadeManager and CollisionSystem have already deprecated their `resetInstance()` methods.

**Gaps:**
- 35 of 43 singletons lack `resetInstance()`, but many already have `resetState()` — the real gap is coverage verification
- AudioManager `resetState()` may not clear donor loaders and secondary state
- TerrainCollisionManager has no reset mechanism at all
- HeadlessTestRunner still calls deprecated `resetInstance()` directly
- No automated enforcement of per-test reset

### 1.1 Fill resetState() Gaps

**AudioManager**: Verify `resetState()` clears all state — donor loaders, donor configs, donor DAC data, ring-left alternation flag, sound map. If any are missed, add them to `resetState()`.

**TerrainCollisionManager**: Add `resetState()` that clears `pooledResults[]` array. Minimal state, but completes the contract so every singleton in the teardown chain has a reset path.

**DebugOverlayManager**: Evaluate whether it holds per-level state (active overlays, toggle state). If so, add to `perTestResetSteps()`. If stateless between levels, no change needed.

### 1.2 Deprecate Remaining resetInstance() Methods

Add `@Deprecated` annotation to `resetInstance()` on these 6 classes, with Javadoc pointing to `resetState()`:

| Class | Current resetInstance() | Migration |
|-------|------------------------|-----------|
| Camera | Nulls static field | Deprecate; `resetState()` already exists and is used in teardown |
| RomManager | Calls `close()` then nulls | Keep functional (needed for ROM switch); deprecate for test use |
| GraphicsManager | Calls `cleanup()` then nulls | Deprecate; `resetState()` already exists and is used in teardown |
| Sonic1ConveyorState | Nulls static field (unsynchronized) | Deprecate; add `resetState()` that clears `reversed` and `spawned[]` |
| Sonic1SwitchManager | Nulls static field (unsynchronized) | Deprecate; add `resetState()` that clears `switchState[]` |
| CrossGameFeatureProvider | Nulls static field | Deprecate; add `resetState()` that clears ROM readers and cached providers |

### 1.3 Migrate HeadlessTestRunner

Replace direct `resetInstance()` calls in `HeadlessTestRunner` setup:

```java
// Before:
GraphicsManager.resetInstance();
Camera.resetInstance();

// After:
GraphicsManager.getInstance().resetState();
Camera.getInstance().resetState();
```

This aligns HeadlessTestRunner with the profile-based teardown pattern.

### 1.4 Stale Reference Guard

Add a generation counter to `GameContext` to catch test code that caches singleton references across resets:

```java
public final class GameContext {
    private static int generation = 0;
    private final int capturedGeneration;

    public static GameContext forTesting() {
        generation++;
        // ... existing teardown logic ...
        return production();
    }

    private GameContext(...) {
        this.capturedGeneration = generation;
        // ... existing constructor ...
    }

    private void checkFresh() {
        if (capturedGeneration != generation) {
            throw new IllegalStateException(
                "Stale GameContext: created at generation " + capturedGeneration
                + " but current generation is " + generation
                + ". Do not hold GameContext references across forTesting() calls.");
        }
    }

    public Camera camera() { checkFresh(); return camera; }
    public LevelManager levelManager() { checkFresh(); return levelManager; }
    // ... all accessors get checkFresh() ...
}
```

Lightweight: zero cost in production (GameContext is not used in the game loop), only triggers in test code that holds stale refs.

### 1.5 Test Enforcement Extension

Create `SingletonResetExtension` (JUnit 5 `BeforeEachCallback`):

```java
public class SingletonResetExtension implements BeforeEachCallback {
    @Override
    public void beforeEach(ExtensionContext context) {
        if (context.getRequiredTestMethod().isAnnotationPresent(FullReset.class)
            || context.getRequiredTestClass().isAnnotationPresent(FullReset.class)) {
            TestEnvironment.resetAll();
        } else {
            TestEnvironment.resetPerTest();
        }
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface FullReset {}
```

Tests opt in via `@ExtendWith(SingletonResetExtension.class)`. Tests that need a full teardown (ROM switch, module change) annotate `@FullReset`. This replaces scattered manual `resetAll()` calls.

Adoption is incremental — no existing tests break. New tests use the extension; existing tests are migrated as they're touched.

### 1.6 Remove Defensive services() Null Checks

The 13 `if (services() == null)` checks are remnants from the migration. Since `services()` now throws `IllegalStateException` if called before injection, these null checks are dead code. Remove them.

### 1.7 Migrate Remaining Scroll Handlers

`SwScrlScz` and `SwScrlHtz` still use `Camera.getInstance()` instead of the services pattern. Migrate to `GameServices.camera()` for consistency with the rest of the codebase.

### Phase 1 Verification

- All existing tests pass (no behavior change)
- `GameContext.forTesting()` teardown sequence covers every singleton with mutable state
- HeadlessTestRunner uses `resetState()` not `resetInstance()`
- No `services() == null` checks remain in production code
- Stale GameContext detection triggers on synthetic test

---

## Phase 2: LevelManager Decomposition

### Context

LevelManager.java: 5,068 lines, 126 public methods, ~80+ fields. The deep-dive method inventory identified clear responsibility clusters with self-contained field groups.

### Extraction Strategy

Extract 3 classes based on field-group isolation — fields in each group are accessed *only* by methods in that group:

### 2.1 LevelTilemapManager

**Package:** `com.openggf.level`

**Responsibility:** GPU tilemap lifecycle — build, cache, upload, invalidate, and swap tilemap data for the TilemapGpuRenderer.

**Methods moved (~20):**
- `ensureBackgroundTilemapData()`, `ensureForegroundTilemapData()`, `ensurePatternLookupData()`
- `buildBackgroundTilemapData()`, `buildForegroundTilemapData()`, `buildTilemapData(byte)`
- `uploadForegroundTilemap()`, `invalidateForegroundTilemap()`
- `prebuildTransitionTilemaps()`, `swapToPrebuiltTilemaps()`, `hasPrebuiltTilemaps()`
- `writeEmptyChunk()`, `writeEmptyTile()`, `writeTileDescriptor()`
- `findActualBgTilemapDataHeight()`

**Fields moved (~25):**
- `backgroundTilemapData`, `backgroundTilemapWidthTiles`, `backgroundTilemapHeightTiles`, `backgroundTilemapDirty`
- `backgroundVdpWrapHeightTiles`, `bgTilemapBaseX`, `currentBgPeriodWidth`
- `foregroundTilemapData`, `foregroundTilemapWidthTiles`, `foregroundTilemapHeightTiles`, `foregroundTilemapDirty`
- `patternLookupData`, `patternLookupSize`, `patternLookupDirty`, `multiAtlasWarningLogged`
- `prebuiltFgTilemap`, `prebuiltFgWidth`, `prebuiltFgHeight`
- `prebuiltBgTilemap`, `prebuiltBgWidth`, `prebuiltBgHeight`

**Dependencies:** Receives `LevelGeometry` record (see 2.4) for read access to level structure. No direct reference to LevelManager.

**Created by:** LevelManager, after `cacheLevelDimensions()`. Stored as a field, accessed by rendering methods.

### 2.2 LevelTransitionCoordinator

**Package:** `com.openggf.level`

**Responsibility:** Level transition state machine — request/consume/execute zone, act, respawn, seamless, credits, and title card transitions.

**Methods moved (~25):**
- `requestRespawn()`, `consumeRespawnRequest()`
- `requestNextAct()`, `consumeNextActRequest()`
- `requestNextZone()`, `consumeNextZoneRequest()`
- `requestZoneAndAct(int, int)`, `requestZoneAndAct(int, int, boolean)`, `consumeZoneActRequest()`, `getRequestedZone()`, `getRequestedAct()`
- `requestSeamlessTransition()`, `consumeSeamlessTransitionRequest()`
- `requestTitleCard()`, `requestInLevelTitleCard()`, `isTitleCardRequested()`, `consumeTitleCardRequest()`, `consumeInLevelTitleCardRequest()`, title card getters
- `requestCreditsTransition()`, `consumeCreditsRequest()`
- `saveBigRingReturnPosition()`, `hasBigRingReturnPosition()`, big ring getters, `clearBigRingReturnPosition()`
- `setForceHudSuppressed()`, `isForceHudSuppressed()`
- `setSuppressNextMusicChange()`, `isSuppressNextMusicChange()`

**Fields moved (~20):**
- `respawnRequested`, `nextActRequested`, `nextZoneRequested`, `specificZoneActRequested`
- `requestedZone`, `requestedAct`, `seamlessTransitionRequested`, `creditsRequested`
- `titleCardRequested`, `titleCardZone`, `titleCardAct`
- `inLevelTitleCardRequested`, `inLevelTitleCardZone`, `inLevelTitleCardAct`
- `bigRingReturnActive`, `bigRingReturnX/Y`, `bigRingReturnCameraX/Y`
- `pendingSeamlessTransitionRequest`, `forceHudSuppressed`, `suppressNextMusicChange`
- `levelInactiveForTransition`

**Dependencies:** Pure state machine — no singleton references. `executeActTransition()` is the exception; it stays in LevelManager since it orchestrates level loading, camera, and manager re-init.

**Created by:** LevelManager constructor. Exposed via `getTransitions()` getter for GameLoop consumption.

### 2.3 LevelDebugRenderer

**Package:** `com.openggf.level`

**Responsibility:** Debug visualization — collision boxes, tile priority overlays, sensor bounds, camera bounds.

**Methods moved (~5):**
- `generateCollisionDebugCommands(List<GLCommand>, Camera)`
- `generateTilePriorityDebugCommands(List<GLCommand>, Camera)`
- `drawPlayableSpriteBounds(AbstractPlayableSprite)`
- `drawCameraBounds()`
- `drawAllPatterns()`

**Fields moved (~11):**
- `debugObjectCommands`, `debugSwitcherLineCommands`, `debugSwitcherAreaCommands`
- `debugRingCommands`, `debugBoxCommands`, `debugCenterCommands`
- `collisionCommands`, `priorityDebugCommands`, `sensorCommands`, `cameraBoundsCommands`
- `reusableDebugCtx`

**Dependencies:** Takes level, camera, object positions as parameters (already the case — these methods receive `List<GLCommand>` and `Camera` params). No field coupling to LevelManager.

**Created by:** LevelManager constructor. Called from `drawWithSpritePriority()` when debug mode is active.

### 2.4 LevelGeometry Record

Shared data contract between LevelManager and LevelTilemapManager:

```java
public record LevelGeometry(
    Level level,
    int fgWidthPx, int fgHeightPx,
    int bgWidthPx, int bgContiguousWidthPx, int bgHeightPx,
    int blockPixelSize, int chunksPerBlockSide
) {}
```

Created once after `cacheLevelDimensions()`. Passed to `LevelTilemapManager` constructor. Immutable — no stale state risk.

### 2.5 What Stays in LevelManager

After extraction (~2,500-2,800 lines):

| Category | Method Count | Purpose |
|----------|-------------|---------|
| Loading orchestration | ~12 | `loadLevel()`, `initGameModule()`, `initAudio()`, `loadLevelData()`, etc. |
| Player/checkpoint init | ~10 | `initPlayerSpriteArt()`, `spawnPlayerAtStartPosition()`, `initSpindashDust()`, etc. |
| Collision queries | ~10 | `getChunkDescAt()`, `getSolidTileForChunkDesc()`, `getBlockIdAt()` (hot-path physics) |
| Rendering coordination | ~8 | `drawWithSpritePriority()`, `renderBackgroundShader()`, `enqueueForegroundTilemapPass()` |
| Frame update | ~5 | `update()`, `updateObjectPositions()` |
| Subsystem accessors | ~10 | `getObjectManager()`, `getRingManager()`, `getTransitions()`, etc. |
| Zone/act queries | ~7 | `getCurrentZone()`, `getCurrentAct()`, `getFeatureZoneId()`, etc. |
| Palette management | ~2 | `updatePalette()`, `reloadLevelPalettes()` |
| Level progression | ~7 | `nextAct()`, `nextZone()`, `loadZoneAndAct()`, `executeActTransition()` |
| Singleton/state reset | ~3 | `getInstance()`, `resetState()`, `resetFrameCounter()` |
| Pre-allocated GL commands | - | Lines 244-557 stay (coupled to rendering coordination) |

The `level` field (111+ accesses) remains in LevelManager. The pre-allocated GL command objects and `pending*` fields stay — they're tightly coupled to the rendering coordination methods.

### 2.6 Migration Strategy

Each extraction is a single commit:

1. **Create new class**, move methods and fields
2. **LevelManager delegates** via thin wrappers for methods still called externally (e.g., `invalidateForegroundTilemap()` → `tilemapManager.invalidateForegroundTilemap()`)
3. **Verify all tests pass** — no behavior change
4. **Remove wrappers** where callers can use the extracted class directly (e.g., GameLoop can call `levelManager.getTransitions().consumeRespawnRequest()`)

Order: LevelDebugRenderer first (least coupled), then LevelTransitionCoordinator, then LevelTilemapManager.

### Phase 2 Verification

- All existing tests pass after each extraction commit
- LevelManager line count reduced to ~2,500-2,800
- No new public API surface on LevelManager (extracted methods accessible via getters)
- Pre-allocated GL command performance unchanged (no extra indirection on hot path)

---

## Phase 3: Breaking the sprites ↔ level Cycle

### Context

Three confirmed circular dependency cycles:
- `sprites.playable ↔ level/level.objects` — **target of this phase**
- `physics ↔ sprites` — natural coupling, not addressed (see Non-Goals)
- `game ↔ level` — partially addressed by LevelManager decomposition

The coupling surface: ObjectManager.SolidContacts and TouchResponses call ~40 distinct methods on `AbstractPlayableSprite`. The reverse: `AbstractPlayableSprite` imports `LevelManager`, `WaterSystem`, and instantiates shield/invincibility/splash objects.

### 3.1 PlayableEntity Interface

**Package:** `com.openggf.game`

**Purpose:** Break `level.objects → sprites.playable` by providing an interface that ObjectManager and all object instances can depend on instead of the concrete `AbstractPlayableSprite`.

```java
public interface PlayableEntity {
    // Position (7 methods)
    short getCentreX();
    short getCentreY();
    void setCentreX(short x);
    short getX();
    short getY();
    void setY(short y);
    void shiftX(int deltaX);

    // Dimensions (3 methods)
    int getHeight();
    int getYRadius();
    int getXRadius();

    // Physics (10 methods)
    short getXSpeed();
    short getYSpeed();
    void setXSpeed(short xSpeed);
    void setYSpeed(short ySpeed);
    short getGSpeed();
    void setGSpeed(short gSpeed);
    boolean getAir();
    void setAir(boolean air);
    byte getAngle();
    void setAngle(byte angle);

    // Ground mode (4 methods)
    boolean getRolling();
    boolean getSpindash();
    GroundMode getGroundMode();
    void setGroundMode(GroundMode mode);

    // Object interaction (3 methods)
    boolean isObjectControlled();
    void setOnObject(boolean onObject);
    void setPushing(boolean pushing);

    // Vulnerability (8 methods)
    boolean getDead();
    boolean isDebugMode();
    boolean getInvulnerable();
    boolean hasShield();
    ShieldType getShieldType();
    int getInvincibleFrames();
    int getDoubleJumpFlag();
    boolean isSuperSonic();

    // Damage/hitbox (3 methods)
    boolean getCrouching();
    int getRingCount();
    void applyHurt(int sourceX);
}
```

**39 methods** — matches the actual coupling surface discovered during analysis. Every method is called by `SolidContacts` or `TouchResponses`.

`AbstractPlayableSprite implements PlayableEntity`. The existing `Sprite` interface (basic position/dimension) remains separate — `PlayableEntity` does not extend `Sprite` because `Sprite` includes rendering methods (`draw()`, `setLayer()`) that the object system doesn't need.

### 3.2 Migrate level.objects to PlayableEntity

**ObjectManager changes:**
- `SolidContacts`: All public methods change `AbstractPlayableSprite player` → `PlayableEntity player`
- `TouchResponses`: Same parameter type change
- `update()`, `applyPlaneSwitchers()`: Accept `PlayableEntity` where currently accepting `AbstractPlayableSprite`

**AbstractObjectInstance changes:**
- Any method accepting `AbstractPlayableSprite` changes to `PlayableEntity`
- Most object instances receive player via `update(int frameCounter, PlayableEntity player)` or similar

**Scope:** All files in `level/objects/` and `game/*/objects/` that import `AbstractPlayableSprite` are candidates. The migration is mechanical — change parameter types and imports.

**What stays concrete:** `LevelManager` itself still imports `AbstractPlayableSprite` for player init, art loading, sidekick management, and sensor configuration. This is acceptable — LevelManager is the orchestrator that *creates* the player. The goal is breaking the `level.objects → sprites` direction, not eliminating all knowledge of sprites from the `level` package.

### 3.3 PowerUpSpawner Interface

**Package:** `com.openggf.game`

**Purpose:** Break `sprites.playable → level.objects` by removing shield/invincibility/splash object instantiation from `AbstractPlayableSprite`.

```java
public interface PowerUpSpawner {
    void spawnShield(PlayableEntity owner, ShieldType type);
    void spawnInvincibilityStars(PlayableEntity owner);
    void spawnSplash(PlayableEntity owner, int x, int y);
    void removeShield(PlayableEntity owner);
    void removeInvincibilityStars(PlayableEntity owner);
}
```

**Implementation:** `DefaultPowerUpSpawner` in `com.openggf.level.objects` — knows about concrete `ShieldObjectInstance`, `InvincibilityStarsObjectInstance`, `SplashObjectInstance`, `FireShieldObjectInstance`, etc. Delegates to `ObjectManager.addDynamicObject()`.

**Injection:** Into `AbstractPlayableSprite` during player init, alongside the existing `ObjectServices` injection:

```java
// In LevelManager.initPlayerSpriteArt() or similar:
sprite.setPowerUpSpawner(new DefaultPowerUpSpawner(objectManager));
```

**AbstractPlayableSprite changes:**
- Remove direct `import com.openggf.level.objects.ShieldObjectInstance` (and similar)
- Remove `import com.openggf.level.LevelManager` (for object spawning — may still need for other reasons initially)
- Replace `new ShieldObjectInstance(...)` with `powerUpSpawner.spawnShield(this, ShieldType.NORMAL)`
- Replace `LevelManager.getInstance().getObjectManager().addDynamicObject(...)` with spawner calls

### 3.4 GroundMode and ShieldType Placement

`GroundMode` is currently in `sprites.playable` — it needs to move to `com.openggf.game` so that `PlayableEntity` can reference it without creating a new dependency on `sprites`. Same for `ShieldType` if it's currently in a sprites or level package.

Check before implementation: if these enums are already in `game`, no move needed.

### 3.5 Dependency Flow After Phase 3

```
BEFORE:
  sprites.playable ──→ level.objects (ShieldObjectInstance, LevelManager)
  level.objects    ──→ sprites.playable (AbstractPlayableSprite)

AFTER:
  sprites.playable ──→ game (PlayableEntity, PowerUpSpawner, ShieldType)
  level.objects    ──→ game (PlayableEntity, ShieldType)
  level.objects    ──→ level.objects (DefaultPowerUpSpawner → concrete objects)

  game package: interfaces only, no implementation coupling
```

The `game` package becomes the interface hub — consistent with its existing role hosting `GameModule`, `PhysicsFeatureSet`, `ObjectServices`, `GameServices`, etc.

### 3.6 What Is NOT Addressed

**physics ↔ sprites cycle:** `Sensor` holds `AbstractPlayableSprite` ref and calls `getGroundMode()`. `AbstractPlayableSprite` holds `Sensor[]` arrays. This is natural bidirectional coupling within the core gameplay loop. Breaking it would require a `SensorHost` interface with essentially one method — adding indirection for negligible benefit.

**LevelManager → sprites imports:** LevelManager still imports `AbstractPlayableSprite` for player creation, art loading, and sensor configuration. This is the orchestrator's job — it creates and configures the player. Only the `level.objects` → `sprites` direction is broken.

### Phase 3 Verification

- All existing tests pass
- No files in `level/objects/` import from `sprites.playable`
- `AbstractPlayableSprite` has no imports from `level.objects`
- `PlayableEntity` interface in `game` package has no imports from `sprites` or `level`
- PowerUpSpawner injection tested via existing shield/invincibility integration tests

---

## Testing Strategy

### Phase 1 Tests
- Verify `GameContext.forTesting()` covers all singletons (assert each manager's state is clean after reset)
- Verify stale `GameContext` detection (capture context, call `forTesting()`, assert accessor throws)
- Verify `SingletonResetExtension` calls `resetPerTest()` before each test
- Existing `TestSonic1/2/3kLevelInitProfile` tests validate teardown step coverage

### Phase 2 Tests
- Existing tests are the primary verification — no behavior change
- Add unit tests for `LevelTransitionCoordinator` state machine (request/consume cycles)
- Verify `LevelTilemapManager` produces identical tilemap data (byte-for-byte comparison against pre-extraction output)

### Phase 3 Tests
- Verify `PlayableEntity` interface is implemented by `AbstractPlayableSprite` (compilation check)
- Verify `DefaultPowerUpSpawner` creates correct shield types
- Existing shield/invincibility integration tests validate end-to-end behavior
- Compile-time verification: `level.objects` package has zero imports from `sprites.playable`

---

## Risk Assessment

| Risk | Mitigation |
|------|------------|
| Phase 1: resetState() misses hidden state | Verify against ROM init phase documentation in AbstractLevelInitProfile |
| Phase 1: Stale reference guard too noisy | Only applies to GameContext accessors (not used in production game loop) |
| Phase 2: Extracted class needs LevelManager field | Use LevelGeometry record; if more needed, pass as constructor param |
| Phase 2: Pre-allocated GL commands can't be separated | Leave in LevelManager — accepted trade-off for rendering performance |
| Phase 3: PlayableEntity interface too large (39 methods) | Matches actual coupling surface; splitting would be artificial |
| Phase 3: Missing method on PlayableEntity discovered during migration | Add to interface — it's a projection of existing usage, easily extended |
| Phase 3: GroundMode/ShieldType package move breaks imports | Mechanical find-replace; IDE refactoring handles this |

## Implementation Order

1. Phase 1: Singleton lifecycle (estimated 1-2 days)
2. Phase 2: LevelManager decomposition (estimated 3-4 days)
   - 2a: LevelDebugRenderer extraction
   - 2b: LevelTransitionCoordinator extraction
   - 2c: LevelTilemapManager extraction
3. Phase 3: Cycle breaking (estimated 2-3 days)
   - 3a: PlayableEntity interface + AbstractPlayableSprite implements
   - 3b: Migrate level.objects to PlayableEntity
   - 3c: PowerUpSpawner interface + DefaultPowerUpSpawner
   - 3d: Migrate AbstractPlayableSprite to PowerUpSpawner
