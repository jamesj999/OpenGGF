# Adding Bosses

Boss fights follow the same object implementation pattern described in the
[tutorial](tutorial-implement-object.md), but with additional complexity. This page covers
the boss-specific patterns.

## State Machine

Bosses have more states than typical objects. In the disassembly, these are encoded as
`routine(a0)` and `routine_secondary(a0)` values. The convention is:

- `routine` handles major phases (init, approach, main, defeated, exploding).
- `routine_secondary` handles sub-phases within a major phase (attack cycle steps, wind-up,
  recovery, etc.).

In the engine, this maps to a state enum or state tracking fields:

```java
private enum BossState {
    INIT, APPROACH, IDLE, ATTACK, RETREAT, HIT_RECOIL, DEFEATED, EXPLODING, FLED
}

private BossState state = BossState.INIT;
private int subState = 0;  // for sub-phases within a state
```

Each state has its own update logic. A typical `update()` dispatches on the current state:

```java
@Override
public void update(int frameCounter, PlayableEntity player) {
    switch (state) {
        case INIT -> updateInit();
        case APPROACH -> updateApproach();
        case IDLE -> updateIdle(player);
        case ATTACK -> updateAttack(player);
        // ...
    }
}
```

## Hit Detection and Health

Bosses track their health via `collision_property`, which starts at the boss's hit count
(typically 8) and decrements on each hit.

In the engine:

```java
private int hitCount = 8;
private int invincibilityTimer = 0;

// Called by the collision system when the player hits the boss
public void onHit() {
    if (invincibilityTimer > 0) return;
    hitCount--;
    invincibilityTimer = FLASH_DURATION;  // typically $20 = 32 frames
    services().playSfx(bossHitSfxId);
    if (hitCount <= 0) {
        state = BossState.DEFEATED;
    }
}
```

The boss flashes during the invincibility period. This is typically done by toggling
rendering on alternate frames:

```java
@Override
public void appendRenderCommands(List<GLCommand> commands) {
    if (invincibilityTimer > 0 && (invincibilityTimer & 1) != 0) {
        return;  // skip rendering on odd frames (flash effect)
    }
    // normal rendering
}
```

## Child Objects

Bosses frequently spawn child objects: projectiles, rotating platforms, body parts,
debris. The pattern is the same as the ArrowShooter tutorial:

```java
SomeProjectile projectile = new SomeProjectile(x, y, direction);
services().objectManager().addDynamicObject(projectile);
```

Common child objects for bosses:
- **Projectiles** (fireballs, hammers, energy balls)
- **Body parts** (cockpit glass, pendulum ball, drill)
- **Debris** on defeat (explosion fragments, falling pieces)

In the disassembly, child objects often share the boss's object ID with different routine
values (like the ArrowShooter). In the engine, each child type is its own class.

## Camera Lock and Arena Setup

Boss encounters typically lock the camera to create an arena. This is handled by the
**level event system**, not by the boss object itself.

The level event for a zone detects when the player crosses a trigger X position and:
1. Changes the camera's right boundary to create a locked screen.
2. Starts the boss music.
3. May change the left boundary too (preventing backtracking).

In the engine, this is implemented in the zone's level event class:

```java
// In Sonic2EHZLevelEvent.java (simplified)
if (act == 1 && camera.getX() >= BOSS_TRIGGER_X) {
    camera.setRightBound(BOSS_ARENA_RIGHT);
    camera.setLeftBound(BOSS_ARENA_LEFT);
    services().playMusic(MusicId.BOSS);
    spawnBoss();
}
```

The boss object itself handles its own behavior within the arena but does not manage
camera boundaries.

## Defeat Sequence

When a boss is defeated (hit count reaches zero), the typical sequence is:

1. **Boss enters defeated state.** Stops attacking, may drift or fall.
2. **Explosion chain.** A series of timed explosions at random offsets around the boss.
   These are spawned as `BossExplosionObjectInstance` children.
3. **Boss is deleted.** After the explosion chain finishes.
4. **Camera unlocks.** The level event detects the boss is gone and restores normal
   camera boundaries.
5. **Signpost or capsule appears.** For Act 2 bosses, an egg prison is spawned. For Act 1,
   a signpost appears.
6. **Music changes.** Boss music stops, act clear or normal music plays.

The explosion chain in the disassembly is driven by a timer and frame counter:

```java
private void updateDefeated() {
    defeatTimer--;
    if (defeatTimer > 0) {
        // Spawn explosions at intervals
        if ((defeatTimer & 0x07) == 0) {
            spawnExplosion(currentX + randomOffset(), currentY + randomOffset());
        }
        return;
    }
    // Timer expired: delete boss, trigger end-of-act
    setDestroyed(true);
}
```

## Example: EHZ Boss Structure

The EHZ boss (`Sonic2EHZBossInstance`) is a good reference for a straightforward boss.
Its state machine:

| State | What happens |
|-------|-------------|
| INIT | Set up art, position, create cockpit child |
| APPROACH | Fly in from the right side of the screen |
| IDLE | Hover, wait before attacking |
| ATTACK | Drill descend toward player |
| RETREAT | Rise back up after attack |
| HIT_RECOIL | Flash and bounce after being hit |
| DEFEATED | Explosion chain, then delete |
| FLED | Robotnik escapes right (egg prison spawns) |

Files:
- `src/main/java/com/openggf/game/sonic2/objects/bosses/Sonic2EHZBossInstance.java`
- `docs/s2disasm/s2.asm` -- search for `Obj56` (the EHZ boss object ID)

## Checklist for a New Boss

1. Read the boss object in the disassembly. Map out all routines and child objects.
2. Read the zone's level event code to understand camera lock triggers.
3. Create the boss class extending `AbstractObjectInstance`.
4. Implement the state machine with all phases.
5. Create classes for each child object type.
6. Register the boss in the object registry.
7. Wire the level event to trigger the boss encounter.
8. Test: approach trigger, attack patterns, hit detection, defeat sequence, camera unlock.
9. Compare against the original: frame counts, positions, velocities, SFX timing.

## Next Steps

- [Adding Zones](adding-zones.md) -- If the boss's zone is not yet supported
- [Tutorial: Implement an Object](tutorial-implement-object.md) -- Base patterns
- [Testing](testing.md) -- Writing boss tests
