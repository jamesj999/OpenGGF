# Services Migration Cleanup Design

**Date:** 2026-03-22
**Branch:** `feature/ai-common-utility-refactors`
**Status:** Approved

## Context

The two-tier service architecture refactor migrated 308 object files from `GameServices`/`getInstance()` singleton lookups to the injectable `services()` pattern via `ObjectServices`. This cleanup resolves the remaining issues: 3 layering violations in the game-agnostic `level/objects` package, coverage gaps in `ObjectServices`, and ~97 mechanical migration candidates in instance methods.

## Scope

### In Scope

1. Fix layering violations in `level/objects`
2. Expand `ObjectServices` with zone feature methods
3. Migrate remaining `GameServices.*` instance-method calls to `services()`

### Out of Scope

- `DefaultPowerUpSpawner` game-specific imports (accepted as documented bridge)
- Constructor/factory/static context calls (cannot use `services()`)
- `GameServices.rom()` calls (ROM is global, not context-specific)
- Level-transition methods (`requestZoneAndAct`, `requestCreditsTransition`, `invalidateForegroundTilemap`, `advanceZoneActOnly`) — these are global operations
- Non-ObjectInstance classes (HudRenderManager, etc.)

## Design

### 1. Layering Violation Fixes

#### AbstractBossInstance: Remove Sonic2Sfx Import

`AbstractBossInstance` imports `com.openggf.game.sonic2.audio.Sonic2Sfx` to provide default SFX IDs for `getBossHitSfxId()` and `getBossExplosionSfxId()`.

**Fix:** Make both methods abstract. Each game's boss subclasses already have access to their game's audio constants and must supply their own IDs. This removes the S2-specific import from the game-agnostic base class.

```java
// Before (AbstractBossInstance.java)
protected int getBossHitSfxId() {
    return Sonic2Sfx.BOSS_HIT.id;
}

// After
protected abstract int getBossHitSfxId();
```

All existing boss subclasses must implement these two methods. S2 bosses return `Sonic2Sfx.BOSS_HIT.id` / `Sonic2Sfx.BOSS_EXPLOSION.id`, S1 bosses return `Sonic1Sfx` equivalents, S3K bosses return `Sonic3kSfx` equivalents.

#### ObjectRenderManager: Remove Sonic2ObjectArtKeys Import

`ObjectRenderManager` imports `Sonic2ObjectArtKeys.EHZ_BOSS` for two convenience methods: `getEHZBossRenderer()` and `getEHZBossSheet()`.

**Fix:** Remove both convenience methods. Callers are in S2-specific code and should use `provider.getRenderer(Sonic2ObjectArtKeys.EHZ_BOSS)` directly.

#### DefaultPowerUpSpawner: Document Bridge Role

Add a class-level comment documenting the intentional bridge role and why the game-specific imports are accepted. No structural change.

### 2. ObjectServices Expansion

Add 3 methods to `ObjectServices` and `DefaultObjectServices`:

```java
// In ObjectServices interface
int featureZoneId();
int featureActId();
ZoneFeatureProvider zoneFeatureProvider();

// In DefaultObjectServices
@Override
public int featureZoneId() {
    return LevelManager.getInstance().getFeatureZoneId();
}

@Override
public int featureActId() {
    return LevelManager.getInstance().getFeatureActId();
}

@Override
public ZoneFeatureProvider zoneFeatureProvider() {
    return LevelManager.getInstance().getZoneFeatureProvider();
}
```

These are context-specific values that would differ in a level editor context.

### 3. Mechanical Migration

Migrate remaining `GameServices.*` calls in object instance methods to `services()` equivalents:

| Old Pattern | New Pattern |
|-------------|------------|
| `GameServices.level().getObjectManager()` | `services().objectManager()` |
| `GameServices.level().getObjectRenderManager()` | `services().renderManager()` |
| `GameServices.level().getRomZoneId()` | `services().romZoneId()` |
| `GameServices.level().getCurrentAct()` | `services().currentAct()` |
| `GameServices.level().getCurrentLevel()` | `services().currentLevel()` |
| `GameServices.level().getLevelGamestate()` | `services().levelGamestate()` |
| `GameServices.level().getCheckpointState()` | `services().checkpointState()` |
| `GameServices.level().getFeatureZoneId()` | `services().featureZoneId()` |
| `GameServices.level().getFeatureActId()` | `services().featureActId()` |
| `GameServices.level().getZoneFeatureProvider()` | `services().zoneFeatureProvider()` |
| `GameServices.audio().playSfx(...)` | `services().playSfx(...)` |
| `GameServices.audio().playMusic(...)` | `services().playMusic(...)` |
| `GameServices.audio().fadeOutMusic()` | `services().fadeOutMusic()` |
| `GameServices.camera()` | `services().camera()` |
| `GameServices.gameState()` | `services().gameState()` |

**Exclusion rules** — do NOT migrate calls in:
- Constructor bodies (`services()` not yet injected)
- Factory/registry lambdas (no instance context)
- Static field initializers
- Non-ObjectInstance classes

## Commit Structure

Matches the existing branch commit pattern:

1. `refactor: fix layering violations in level/objects base classes`
   - AbstractBossInstance: abstract SFX methods, remove Sonic2Sfx import
   - ObjectRenderManager: remove EHZ convenience methods, remove Sonic2ObjectArtKeys import
   - DefaultPowerUpSpawner: add bridge documentation
2. `feat: expand ObjectServices with zone feature methods`
   - Add featureZoneId(), featureActId(), zoneFeatureProvider() to interface + default impl
3. `refactor: migrate remaining level/objects GameServices calls to services()`
4. `refactor: migrate remaining S1 object GameServices calls to services()`
5. `refactor: migrate remaining S2 object GameServices calls to services()`
6. `refactor: migrate remaining S3K object GameServices calls to services()`

## Post-Migration State

After completion, `GameServices` calls in object files will only remain in justified contexts:

| Category | Approx Calls | Rationale |
|----------|-------------|-----------|
| Constructor/factory context | ~50 | `services()` unavailable at construction time |
| `GameServices.rom()` | ~16 | ROM is a global resource |
| `GameServices.debugOverlay()` | ~7 | Static field initializers |
| Level-transition methods | ~10 | Global operations, not context-specific |
| Non-ObjectInstance classes | ~5 | Not injected with ObjectServices |

## Verification

- `mvn compile` must pass after each commit
- `mvn test` must pass after final commit
- No new `GameServices.*` imports introduced in `level/objects` (except DefaultPowerUpSpawner bridge)
- All existing tests remain green
