# Multi-Sidekick Daisy Chain

## Summary

Support an arbitrary number of CPU-controlled sidekick characters following the main player in a daisy chain. Each sidekick follows the one in front of it rather than all following Sonic directly. The config accepts comma-separated character names (e.g. `"tails,knuckles,sonic,sonic"`).

**Motivation:** Fun/novelty — multiple characters running together looks great, from Sonic+Tails+Knuckles to five Sonics in a conga line.

## Configuration

`SIDEKICK_CHARACTER_CODE` changes from a single string to a comma-separated list:

| Value | Behavior |
|-------|----------|
| `""` | No sidekicks (unchanged) |
| `"tails"` | Single sidekick (unchanged behavior) |
| `"tails,knuckles"` | Two sidekicks, Tails follows Sonic, Knuckles follows Tails |
| `"tails,sonic,sonic,sonic"` | Four sidekicks in a chain |

Parsing: split on comma, trim whitespace. Duplicate names are allowed — multiple Sonics are visually identical (no palette swaps).

## Spawn Positioning

Each sidekick spawns progressively further behind the main player:

- Sidekick `i` (0-indexed) is positioned at `mainSprite.getX() - 0x20 * (i + 1)`
- Position is clamped to `max(cameraLeftBound, computed position)`
- Any sidekick that would spawn off-screen or out-of-bounds starts in `SPAWNING` state immediately, triggering its respawn animation naturally rather than popping in at a clamped position

Sprite codes use suffixes to avoid HashMap collisions: `_p2`, `_p3`, `_p4`, etc.

## SpriteManager Changes

### New API

- `getSidekicks()` — returns `List<AbstractPlayableSprite>` ordered by chain position. Returns empty list when suppressed.
- `getSidekick()` — deprecated, returns first sidekick for compatibility during migration. Callers should migrate to `getSidekicks()`.

### Internal State

- New field: `List<AbstractPlayableSprite> sidekicks` maintained in chain order on add/remove.
- `isCpuSidekickSuppressed()` applies to all sidekicks uniformly (unchanged logic).
- `isSuppressedSidekickSprite()` unchanged — checks `isCpuControlled()` against suppression.

## SidekickCpuController (Renamed from TailsCpuController)

### Rename & Generalization

`TailsCpuController` is renamed to `SidekickCpuController`. This is the shared controller for all sidekick types.

Key field changes:

| Old | New | Purpose |
|-----|-----|---------|
| `sonic` | `leader` | The sprite this sidekick follows |
| `findSonic()` | `findLeader()` | Returns the assigned leader reference directly (no scan) |

### State Machine

States: `INIT`, `SPAWNING`, `APPROACHING` (renamed from `FLYING`), `NORMAL`, `PANIC`.

- `APPROACHING` is the movement-agnostic state for "actively returning to the chain." What happens during this state is determined by the `SidekickRespawnStrategy`.

### Leader Assignment

- Constructed with a `leader` reference: sidekick[0]'s leader = main player, sidekick[1]'s leader = sidekick[0], etc.
- `setLeader(AbstractPlayableSprite)` allows dynamic reassignment.

### Chain Healing via `getEffectiveLeader()`

When a sidekick's direct leader is despawned or not yet settled, the chain self-heals upward:

```java
AbstractPlayableSprite getEffectiveLeader() {
    AbstractPlayableSprite current = leader;
    while (current != null && current.isCpuControlled()) {
        SidekickCpuController ctrl = current.getCpuController();
        if (ctrl != null && ctrl.isSettled()) {
            return current;  // leader is spawned and settled
        }
        current = ctrl.getLeader();  // walk up the chain
    }
    return current;  // main player (not cpu-controlled)
}
```

`isSettled()` returns `true` when the sidekick has been in `NORMAL` state for 15+ consecutive frames (~quarter second at 60fps).

The history buffer is read from whichever sprite `getEffectiveLeader()` resolves to.

### Following Behavior

- Each sidekick reads from its effective leader's position/input history with a fixed **17-frame delay** (ROM-accurate value, `FOLLOW_DELAY_FRAMES`).
- With N sidekicks, the total delay from Sonic to the last sidekick is `17 * N` frames.
- If this proves unworkable at high sidekick counts, the delay can be tuned later — starting with the ROM value.

## Respawn Strategy Interface

```java
public interface SidekickRespawnStrategy {
    /**
     * Called each frame while in APPROACHING state.
     * Return true when respawn is complete and sidekick should transition to NORMAL.
     */
    boolean updateApproaching(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader,
                              int frameCounter);

    /**
     * Position and initialize the sidekick when transitioning from SPAWNING to APPROACHING.
     */
    void beginApproach(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader);
}
```

### Implementations

**`TailsRespawnStrategy`** — extracted from current `respawnToFlying()`/`updateFlying()` logic. Spawns above leader, flies down, drops when close. **Implemented in this spec.**

**`KnucklesRespawnStrategy`** — glides in from off-screen above the leader, drops when aligned on X. **Stubbed; falls back to `TailsRespawnStrategy`. Follow-up work.**

**`SonicRespawnStrategy`** — walks in if leader stationary, spindashes in from the edge the leader is moving away from. **Stubbed; falls back to `TailsRespawnStrategy`. Follow-up work.**

Strategy selection is based on character type at construction time.

## ObjectManager Changes

### Signature

`update(int cameraX, AbstractPlayableSprite player, AbstractPlayableSprite sidekick, int touchFrameCounter)`

becomes:

`update(int cameraX, AbstractPlayableSprite player, List<AbstractPlayableSprite> sidekicks, int touchFrameCounter)`

(Both overloads updated.)

### TouchResponses Inner Class

Currently maintains a dedicated pair of overlap buffers (`sidekickBufferA`/`sidekickBufferB`) for the single sidekick.

Changes to a `Map<AbstractPlayableSprite, Set<ObjectInstance>>` keyed by sidekick — each sidekick gets independent overlap tracking for proper edge-detection of new touches.

- `updateSidekick(sidekick, frameCounter)` called in a loop for each sidekick
- `handleTouchResponseSidekick`, `applySidekickHurt`, `checkMultiRegionTouchSidekick` already take `AbstractPlayableSprite` as a parameter — no signature changes, just called more times
- `reset()` clears the entire map

### Performance Note

No distance culling — consistent with the ROM, which checks `(Sidekick).w` against every active object unconditionally with no spatial skip. The object activation window already limits the active object set. If profiling shows a problem at high sidekick counts, optimization can be added later.

## Art Loading

### VRAM Bank Allocation

Each sidekick that shares a character type with the main player or another sidekick needs its own VRAM bank to prevent DPLC corruption (same fix as the existing single-sidekick case at `LevelManager.java:1110-1117`, generalized).

- Load `SpriteArtSet` once per unique character name
- For each sidekick, create a clone with shifted `basePatternIndex`: `originalBase + bankSize * slotIndex`
- Slot 0 is the main player (if same character type), slots 1+ are sidekicks of that type
- Example: `"sonic"` main + `"sonic,sonic,sonic"` sidekicks → base, base+bank, base+2×bank, base+3×bank
- Example: `"tails,sonic,sonic"` sidekicks → Tails gets its own base (no conflict with main Sonic), first Sonic sidekick gets sonicBase+bank, second gets sonicBase+2×bank

### Tail Appendage

`initTailsTails()` is only called for sidekicks whose character type is `"tails"`.

### Missing Art Handling

If a character's art is unavailable (e.g. Knuckles configured but no S3K donor), that sidekick slot is skipped with a log warning rather than crashing.

## LevelManager Call Site Migration

All ~7 `getSidekick()` references become `getSidekicks()` loops:

| Call Site | Change |
|-----------|--------|
| `objectManager.update(...)` | Pass sidekicks list |
| Ring collection | Loop over sidekicks |
| Art loading / init | Loop over sidekicks (with per-type dedup for loading) |
| State reset | Loop over sidekicks |
| `spawnSidekick()` → `spawnSidekicks()` | Reposition all sidekicks |
| `reregisterPlayerDynamicObjects` | Loop over sidekicks |
| Act transition offset | Loop over sidekicks |

## Object Instance Call Site Migration

~20 files that call `getSidekick()` to apply physics/interactions (fans, seesaws, springs, launchers, vines, etc.) become `getSidekicks()` loops. These are mechanical — same logic per sidekick, just iterated.

## PlayerCharacter Enum

**No change.** `PlayerCharacter` is determined by the main character only. Sidekick composition does not affect level event routing. A Sonic main with Tails+Knuckles sidekicks still resolves to `SONIC_AND_TAILS`.

## Testing

### Unit Tests (No ROM/OpenGL)

| Test | Purpose |
|------|---------|
| `TestSidekickChainHealing` | `getEffectiveLeader()` walks chain: settled → returns leader; despawned → returns leader's leader; all despawned → returns main player |
| `TestSidekickCpuControllerRename` | `SidekickCpuController` state machine regression — identical behavior to old `TailsCpuController` |
| `TestSidekickConfigParsing` | Comma-separated parsing: trim, empty string, single value, duplicates |
| `TestSidekickArtBankAllocation` | `basePatternIndex` shifts correct for various configs |

### Headless Integration Tests (ROM Required)

| Test | Purpose |
|------|---------|
| `TestMultiSidekickSpawn` | 3 sidekicks in EHZ1: all in `getSidekicks()`, chain order correct, leaders assigned |
| `TestMultiSidekickFollowing` | Step frames moving right: X positions form chain with ~17-frame gaps per link |
| `TestSidekickChainHealingIntegration` | Despawn middle sidekick: downstream follows next available leader, reverts when middle respawns and settles |

### Existing Test Migration

Tests calling `objectManager.update(..., sidekick, ...)` with a single sidekick (or null) need signature updates: wrap in `List.of(sidekick)` or `List.of()`. Mechanical changes only.

## Out of Scope

- Knuckles respawn strategy (glide-in) — follow-up spec
- Sonic respawn strategy (walk/spindash-in) — follow-up spec
- Palette swaps for duplicate characters
- "Heroes mode" character swapping
- Distance-based collision culling optimization
