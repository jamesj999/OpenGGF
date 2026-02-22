---
name: s2-implement-boss
description: "Implement a Sonic 2 zone boss with complete ROM accuracy. This skill guides complete implementation including LevelEventManager integration, multi-component architecture, hit handling, defeat sequences, and cross-validation against the disassembly."
---
# Implement Sonic 2 Boss

Implement a Sonic 2 zone boss with complete ROM accuracy. This skill guides complete implementation including LevelEventManager integration, multi-component architecture, hit handling, defeat sequences, and cross-validation against the disassembly.

## Inputs

$ARGUMENTS: Boss name or zone (e.g., "EHZ boss", "Chemical Plant boss", "0x56")

## Related Skills

- **s2disasm-guide** (`.agents/skills/s2disasm-guide/SKILL.md`) - Disassembly navigation, label conventions, RomOffsetFinder
- **s2-implement-object** (`.agents/skills/s2-implement-object/SKILL.md`) - For non-boss Sonic 2 objects and badniks

## Key Differences: Bosses vs Regular Objects

| Aspect | Regular Objects | Bosses |
|--------|-----------------|--------|
| **Spawning** | From level layout data via ObjectManager | Dynamically via LevelEventManager |
| **Camera** | No camera control | Arena setup with boundary locking |
| **Components** | Single entity or simple children | Multi-component with AbstractBossChild |
| **Hits** | 1 hit (badniks) or indestructible | 8 hits with invulnerability periods |
| **Defeat** | Explosion + animal | Explosion sequence â†’ Flee â†’ EggPrison |
| **Art** | Unique per object | Often shares Eggpod art + unique parts |
| **Music** | No music changes | Boss music at spawn, stops at defeat |
| **State** | Object-local | Shared BossStateContext (ROM: Boss_X_pos, etc.) |

## Implementation Process

### Phase 1: Research & Discovery

Delegate multiple agents to explore the disassembly. **Include this instruction in each agent prompt:**

> Use the s2disasm-guide skill (`.agents/skills/s2disasm-guide/SKILL.md`) for reference on disassembly structure, label conventions, RomOffsetFinder commands, and object system patterns.

**Research checklist:**
- [ ] Locate boss object in disassembly (e.g., `Obj56`, `Obj5D`, `Obj51`, `Obj89`)
- [ ] Find corresponding `LevEvents_ZONE2` routine for arena setup
- [ ] Identify all `AllocateObjectAfterCurrent` calls for child components
- [ ] Document state machine (`routine_secondary`) transitions
- [ ] Note defeat sequence timing (explosion duration, flee direction)
- [ ] Find boss-specific art addresses (`ArtNem_XXXBoss`, `Map_XXXBoss`)
- [ ] Use `plc` command to identify which PLCs load boss art:
  ```bash
  mvn exec:java -Dexec.mainClass="disasm.com.openggf.tools.RomOffsetFinder" -Dexec.args="plc PlrList_EhzBoss" -q
  ```
  Search results show PLC cross-references inline

**Key disassembly patterns to identify:**
- `collision_flags` set to `$C0 | size_index` (boss category)
- `objoff_3C` used for hit count or defeat timer
- `Boss_HandleHits` routine usage
- `routine_secondary` state machine with approach/battle/defeat phases

### Phase 2: LevelEventManager Setup

Bosses are spawned dynamically by zone-specific event handlers in `LevelEventManager.java`.

**Implementation checklist:**
- [ ] Add zone constant if not present
- [ ] Add boss reference field (e.g., `private Sonic2XXXBossInstance xxxBoss`)
- [ ] Implement `updateXXX()` method with 4-6 event routines
- [ ] Implement `spawnXXXBoss()` method
- [ ] Add boss spawn coordinates and arena boundaries from disassembly
- [ ] Initialize boss reference in `initLevel()`

**Example event routine pattern:**

```java
// In LevelEventManager.java - updateZONE() method
private void updateZONE() {
    if (currentAct != 1) return;  // Act 2 only

    switch (eventRoutine) {
        case 0 -> {
            // Wait for approach trigger
            if (camera.getX() >= APPROACH_TRIGGER_X) {
                camera.setMinX(camera.getX());
                camera.setMaxYTarget((short) ARENA_MAX_Y);
                eventRoutine += 2;
            }
        }
        case 2 -> {
            // Lock arena and start spawn delay
            if (camera.getX() >= ARENA_LOCK_X) {
                camera.setMinX((short) ARENA_MIN_X);
                camera.setMaxX((short) ARENA_MAX_X);
                eventRoutine += 2;
                bossSpawnDelay = 0;
                AudioManager.getInstance().fadeOutMusic();
                GameServices.gameState().setCurrentBossId(BOSS_ID);
            }
        }
        case 4 -> {
            // Lock floor and spawn boss after delay
            if (camera.getY() >= ARENA_FLOOR_Y) {
                camera.setMinY((short) ARENA_FLOOR_Y);
            }
            bossSpawnDelay++;
            if (bossSpawnDelay >= 0x5A) {  // 90 frames
                spawnBoss();
                eventRoutine += 2;
                AudioManager.getInstance().playMusic(Sonic2AudioConstants.MUS_BOSS);
            }
        }
        case 6 -> {
            // Prevent backtracking during fight
            if (camera.getX() > camera.getMinX()) {
                camera.setMinX(camera.getX());
            }
        }
    }
}
```

### Phase 3: Boss Instance Class

**Implementation checklist:**
- [ ] Create `Sonic2XXXBossInstance extends AbstractBossInstance`
- [ ] Define state machine constants (SUB0, SUB2, SUB4, etc.)
- [ ] Define position/velocity constants from disassembly
- [ ] Implement `initializeBossState()` with child spawning
- [ ] Implement `updateBossLogic()` state machine
- [ ] Implement each state update method
- [ ] Override `getInitialHitCount()` (usually 8)
- [ ] Override `getCollisionSizeIndex()` (usually 0x0F)
- [ ] Override `onHitTaken()` for hit reactions
- [ ] Override `onDefeatStarted()` for defeat transition
- [ ] Override `usesDefeatSequencer()` (false for custom defeat)
- [ ] Implement `appendRenderCommands()`

**Boss instance template:**

```java
package uk.co.jamesj999.sonic.game.sonic2.objects.bosses;

public class Sonic2ZoneBossInstance extends AbstractBossInstance {

    // State machine constants (ROM: routine_secondary values)
    private static final int SUB0_APPROACH = 0x00;
    private static final int SUB2_ACTIVE = 0x02;
    private static final int SUB4_ATTACK = 0x04;
    private static final int SUB6_DEFEATED = 0x06;
    private static final int SUB8_FLEEING = 0x08;

    // Position/velocity constants from disassembly
    private static final int MAIN_START_X = 0xXXXX;
    private static final int MAIN_START_Y = 0xXXXX;

    public Sonic2ZoneBossInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, levelManager, "Zone Boss");
    }

    @Override
    protected void initializeBossState() {
        state.x = MAIN_START_X;
        state.y = MAIN_START_Y;
        state.routineSecondary = SUB0_APPROACH;
        spawnChildComponents();
    }

    @Override
    protected void updateBossLogic(int frameCounter, AbstractPlayableSprite player) {
        switch (state.routineSecondary) {
            case SUB0_APPROACH -> updateApproach();
            case SUB2_ACTIVE -> updateActiveBattle(player);
            // ... other states
        }
    }

    @Override protected int getInitialHitCount() { return 8; }
    @Override protected int getCollisionSizeIndex() { return 0x0F; }
    @Override protected void onHitTaken(int remainingHits) { /* hit reaction */ }
    @Override protected void onDefeatStarted() { /* transition to defeat state */ }
}
```

#### Configurable Methods

Override these in subclasses for boss-specific behavior:

| Method | Default | Override When |
|--------|---------|---------------|
| `getInvulnerabilityDuration()` | 32 | Boss uses different duration (ARZ=64, CNZ=48) |
| `getPaletteLineForFlash()` | 1 | Boss flashes different palette line (ARZ=0) |
| `usesDefeatSequencer()` | true | Boss has custom defeat logic |
| `calculateHoverOffset()` | sine oscillation | Boss uses different hover pattern |

#### Common Helper Methods

`AbstractBossInstance` provides these utilities:

| Method | Description |
|--------|-------------|
| `calculateHoverOffset()` | Sine-based floating motion (2 per frame, >>6) |
| `spawnDefeatExplosion()` | Random-offset explosion during defeat |
| `applyObjectMoveAndFall()` | Apply velocity and gravity |
| `getCustomFlag(offset)` / `setCustomFlag(offset, value)` | `objoff_XX` emulation |

### Phase 4: Child Components

**Implementation checklist:**
- [ ] Create child classes extending `AbstractBossChild`
- [ ] Add children to `ObjectManager` via `addDynamicObject()`
- [ ] Store children in parent's `childComponents` list
- [ ] Implement position synchronization
- [ ] Handle destruction when parent defeated

**Boss child component template:**

```java
public class BossChildName extends AbstractBossChild {

    public BossChildName(Sonic2ZoneBossInstance parent) {
        super(parent, "Child Name", priority, Sonic2ObjectIds.BOSS_ID);
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (!shouldUpdate(frameCounter)) return;
        syncPositionWithParent();
        // Child-specific logic
        updateDynamicSpawn();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Render relative to parent position
    }
}
```

### Phase 5: Art Loading

**PLC note:** S2 boss art has dedicated PLC IDs in ArtLoadCues. The shared `PlcParser` utility handles parsing. See `plc-system` skill. Use `RomOffsetFinder plc <name>` to inspect PLC contents from the CLI.

**Implementation checklist:**
- [ ] Add ROM address constants to `Sonic2Constants.java`
- [ ] Add art keys to `Sonic2ObjectArtKeys.java`
- [ ] Create loader method in `Sonic2ObjectArt.java`
- [ ] Create mappings method (parse from `Map_XXXBoss`)
- [ ] Register in `Sonic2ObjectArtProvider.loadArtForZone()`

### Phase 6: Factory Registration

Register in `Sonic2ObjectRegistry.registerDefaultFactories()`:

```java
registerFactory(Sonic2ObjectIds.ZONE_BOSS,
    (spawn, registry) -> new Sonic2ZoneBossInstance(spawn, LevelManager.getInstance()));
```

### Phase 7: Code Quality

Ensure the implementation:
- Has no TODOs or placeholder code
- Has no "engine limitation" workarounds - if the ROM does it, the engine must support it
- Uses explicit disassembly references in comments for non-trivial logic
- Handles object creation and cleanup correctly
- Properly manages object lifecycle (spawning, despawning)
- Follows existing code patterns in the codebase

### Phase 8: Cross-Validation

Delegate to a review agent to cross-validate against the disassembly:

```
Review the implementation of [ZoneName] Boss (0xXX) against the Sonic 2 disassembly.

Reference: Use the s2disasm-guide skill for disassembly navigation guidance.

Files to review:
- [List all created/modified files]

Disassembly references:
- Boss object: docs/s2disasm/ObjXX...
- Level events: docs/s2disasm/LevEvents...

Validation checklist:
1. LevelEventManager arena setup matches LevEvents_ZONE2
2. State machine transitions match routine_secondary values
3. All child components spawned correctly
4. Hit count and invulnerability timing match ROM
5. Defeat sequence (explosions, flee, EggPrison) matches ROM
6. Art and mappings render correctly
7. Music transitions (fade, boss music, resume) work correctly
8. Camera boundaries lock/unlock correctly
9. No TODOs or simplifications
10. No "engine limitation" workarounds

Report any discrepancies with specific line references from both code and disassembly.
```

### Phase 9: Finalization

Once cross-validation passes:

1. **Add to IMPLEMENTED_IDS** in `Sonic2ObjectProfile.java` (the `IMPLEMENTED_IDS` set):
   ```java
   0xXX,  // ZoneName Boss
   ```

2. **Build and test**:
   ```bash
   mvn package
   ```

3. Report completion with summary.

## Reference Files

| Purpose | Location |
|---------|----------|
| **Disassembly guide** | `.agents/skills/s2disasm-guide/SKILL.md` |
| Base boss | `src/.../level/objects/boss/AbstractBossInstance.java` |
| Boss state context | `src/.../level/objects/boss/BossStateContext.java` |
| Boss child base | `src/.../level/objects/boss/AbstractBossChild.java` |
| Boss child interface | `src/.../level/objects/boss/BossChildComponent.java` |
| Level events | `src/.../game/sonic2/LevelEventManager.java` |
| Boss implementations | `src/.../game/sonic2/objects/bosses/` |
| Object IDs | `src/.../game/sonic2/constants/Sonic2ObjectIds.java` |
| ROM offsets | `src/.../game/sonic2/constants/Sonic2Constants.java` |
| Art keys | `src/.../game/sonic2/Sonic2ObjectArtKeys.java` |
| Art loader | `src/.../game/sonic2/Sonic2ObjectArt.java` |
| Art provider | `src/.../game/sonic2/Sonic2ObjectArtProvider.java` |
| Registry | `src/.../game/sonic2/objects/Sonic2ObjectRegistry.java` |
| SFX constants | `src/.../game/sonic2/constants/Sonic2AudioConstants.java` |
| Disassembly | `docs/s2disasm/` |
| Implemented IDs | `src/.../tools/Sonic2ObjectProfile.java` (IMPLEMENTED_IDS set) |

## Example Implementations

Study these implemented bosses for patterns:

| Boss | Object ID | Key Features |
|------|-----------|--------------|
| `Sonic2EHZBossInstance` | 0x56 | Ground vehicle, wheel terrain tracking, custom defeat |
| `Sonic2CPZBossInstance` | 0x5D | Complex child hierarchy, gunk hazard, status bit flags |
| `Sonic2ARZBossInstance` | 0x89 | Pillar spawning, hammer collision, multi-sprite rendering |
| `Sonic2CNZBossInstance` | 0x51 | Electric balls, arena wall modification, palette swap |

