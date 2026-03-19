# Per-Character Respawn Strategies

## Summary

Implement `KnucklesRespawnStrategy` and `SonicRespawnStrategy` to replace the default Tails fly-in behavior for Knuckles and Sonic sidekicks. Wire up per-character strategy selection in the spawn loop.

**Depends on:** Multi-sidekick daisy chain (already merged) — specifically the `SidekickRespawnStrategy` interface, `TailsRespawnStrategy`, and `setRespawnStrategy()` on `SidekickCpuController`.

## KnucklesRespawnStrategy

### beginApproach

1. Determine screen edge based on leader's movement direction: leader moving right → spawn from left edge, left → right edge. If stationary, default to left.
2. Position sidekick at (edgeX, leaderY - 192px) — off-screen horizontally, above the leader vertically.
3. Set direction toward leader. Set glide animation.

### updateApproaching

1. Move horizontally toward leader at ~4px/frame, descend at ~1px/frame (shallow glide angle).
2. When within 16px of leader's X position, stop horizontal movement and let gravity take over (drop).
3. Return `true` when the sidekick is no longer airborne (has landed on ground).

## SonicRespawnStrategy

### beginApproach

1. Determine screen edge: leader moving right → spawn from left edge, left → right edge. If stationary, default to left.
2. Get target Y from leader's centre Y position.
3. Terrain probe: use `ObjectTerrainUtils.checkFloorDist(edgeX, targetY)` to find the nearest floor below (or at) that Y position, searching up to 128px downward.
4. **If no floor found**: do NOT transition to APPROACHING — return immediately. The controller stays in SPAWNING and retries on the next eligible frame (every 64 frames per the existing spawning gate). This prevents spawning over pits.
5. **If floor found**: place sidekick at (edgeX, floorY). Set direction toward leader.
   - If leader's absolute ground speed > 0x200 (512 subpixels, ~2px/frame): spawn in spindash-release state — set rolling flag, set ground speed toward leader at a fixed charge value (e.g. 0x800).
   - Otherwise: spawn in walking state — set ground speed toward leader at a walking pace (e.g. 0x200).

### updateApproaching

1. The sidekick is already moving via ground speed set in `beginApproach`. Set directional input (`inputLeft`/`inputRight`) toward the leader to maintain momentum through terrain.
2. Return `true` when the sidekick is within 32px of the leader's X position, completing the approach.

### Terrain Probe Failure Handling

When `beginApproach` can't find ground, `SonicRespawnStrategy` signals this to the controller. The interface's `beginApproach` is `void`, so the strategy sets an internal `approachFailed` flag. When `updateApproaching` is called immediately after a failed begin, it returns `false` and the controller's `updateApproaching()` method needs to detect this and revert to SPAWNING state.

**Alternative (simpler):** Change `beginApproach` to return `boolean` — `true` if approach started successfully, `false` if it should stay in SPAWNING. This requires a minor interface change.

**Recommendation:** Change the interface. It's a one-method signature change with only two existing implementations to update.

### Interface Change

```java
public interface SidekickRespawnStrategy {
    /**
     * Called each frame while in APPROACHING state.
     * @return true when respawn is complete and sidekick should transition to NORMAL
     */
    boolean updateApproaching(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader,
                              int frameCounter);

    /**
     * Position and initialize the sidekick when transitioning from SPAWNING to APPROACHING.
     * @return true if approach started, false if conditions not met (stay in SPAWNING)
     */
    boolean beginApproach(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader);
}
```

`TailsRespawnStrategy.beginApproach` and `KnucklesRespawnStrategy.beginApproach` always return `true`.

`SonicRespawnStrategy.beginApproach` returns `false` when no floor is found.

The controller's `respawnToApproaching()` checks the return value — if `false`, remains in SPAWNING.

## Strategy Selection

In `Engine.java` spawn loop, after creating the `SidekickCpuController`:

```java
if ("knuckles".equalsIgnoreCase(charName)) {
    controller.setRespawnStrategy(new KnucklesRespawnStrategy(controller));
} else if (!"tails".equalsIgnoreCase(charName)) {
    // Any non-Tails, non-Knuckles character (Sonic, clones) uses Sonic respawn
    controller.setRespawnStrategy(new SonicRespawnStrategy(controller));
}
// Tails is the default — already set in constructor
```

## Constants

| Constant | Value | Used By |
|----------|-------|---------|
| `GLIDE_HORIZONTAL_SPEED` | 4 px/frame | Knuckles |
| `GLIDE_DESCENT_SPEED` | 1 px/frame | Knuckles |
| `GLIDE_X_THRESHOLD` | 16 px | Knuckles (X-aligned → drop) |
| `FLOOR_SEARCH_DEPTH` | 128 px | Sonic (max downward probe) |
| `SPINDASH_SPEED_THRESHOLD` | 0x200 (512 subpixels) | Sonic (walk vs spindash) |
| `SPINDASH_RELEASE_SPEED` | 0x800 (2048 subpixels) | Sonic (initial roll speed) |
| `WALK_ENTRY_SPEED` | 0x200 (512 subpixels) | Sonic (initial walk speed) |
| `APPROACH_COMPLETE_THRESHOLD` | 32 px | Sonic (close enough → NORMAL) |
| `SPAWN_Y_OFFSET` | 192 px | Knuckles (above leader) |

## Testing

### Unit Tests (no ROM)

- `TestRespawnStrategySelection` — verify character name → strategy type mapping

### Headless Integration (ROM required)

- `TestKnucklesRespawnGlide` — Knuckles sidekick glides in from screen edge, lands near leader
- `TestSonicRespawnWalk` — Sonic sidekick walks in from screen edge when leader is slow/stationary
- `TestSonicRespawnSpindash` — Sonic sidekick spindashes in when leader is moving fast
- `TestSonicRespawnNoFloor` — Sonic sidekick stays in SPAWNING when no floor at screen edge

## Files

### New

| File | Purpose |
|------|---------|
| `src/main/java/com/openggf/sprites/playable/KnucklesRespawnStrategy.java` | Glide-in respawn |
| `src/main/java/com/openggf/sprites/playable/SonicRespawnStrategy.java` | Walk/spindash-in respawn |

### Modified

| File | Change |
|------|--------|
| `src/main/java/com/openggf/sprites/playable/SidekickRespawnStrategy.java` | `beginApproach` returns `boolean` |
| `src/main/java/com/openggf/sprites/playable/TailsRespawnStrategy.java` | Update `beginApproach` to return `true` |
| `src/main/java/com/openggf/sprites/playable/SidekickCpuController.java` | Handle `false` from `beginApproach`, stay in SPAWNING |
| `src/main/java/com/openggf/Engine.java` | Strategy selection in spawn loop |

## Out of Scope

- Animation IDs for Knuckles glide/drop (use placeholder or Tails animations until Knuckles art is available)
- Spindash dust particles during Sonic respawn
- Per-character respawn sound effects
