---
name: s3k-implement-boss
description: "Implement a Sonic 3 & Knuckles zone boss with complete ROM accuracy. This skill guides complete implementation including arena setup, hit handling, defeat sequences, and cross-validation against the Sonic 3&K disassembly."
---
# Implement Sonic 3&K Boss

Implement a Sonic 3 & Knuckles zone boss with complete ROM accuracy. This skill guides complete implementation including arena setup, hit handling, defeat sequences, and cross-validation against the Sonic 3&K disassembly.

## Inputs

$ARGUMENTS: Boss name or zone (e.g., "AIZ mini-boss", "Angel Island end boss", "HCZ Act 2 boss", "Doomsday boss")

## Related Skills

- **s3k-disasm-guide** (`.agents/skills/s3k-disasm-guide/SKILL.md`) - Disassembly navigation, label conventions, RomOffsetFinder
- **s3k-implement-object** (`.agents/skills/s3k-implement-object/SKILL.md`) - For non-boss Sonic 3&K objects and badniks

## Zone-Set-Aware Boss IDs

S3K uses **two object pointer tables** that remap boss IDs by zone:
- **S3KL** (zones 0-6: AIZ through LBZ): Boss IDs 0x90-0xCD
- **SKL** (zones 7-13: MHZ through DDZ): Boss IDs 0x91-0xB6

The same numeric ID maps to different bosses depending on the zone set. Use `S3kZoneSet.forZone(zoneId)` and `Sonic3kObjectRegistry.getPrimaryName(id, zoneSet)` for correct resolution.

## Sonic 3&K Boss List

S3K has **both mini-bosses (Act 1) and end bosses (Act 2)** per zone — significantly more bosses than S1/S2.

### Mini-Bosses (Act 1)

| Zone | Label | Notes |
|------|-------|-------|
| AIZ | `Obj_AIZMiniboss` | Fire-breathing sub-boss |
| HCZ | `Obj_HCZMiniboss` | Underwater sub-boss |
| MGZ | `Obj_MGZMiniboss` | Drill sub-boss |
| CNZ | `Obj_CNZMiniboss` | Carnival sub-boss |
| FBZ | `Obj_FBZMiniboss` | Battery sub-boss |
| ICZ | `Obj_ICZMiniboss` | Ice sub-boss |
| LBZ | `Obj_LBZMiniboss` | Launch sub-boss |
| MHZ | `Obj_MHZMiniboss` | Mushroom sub-boss |
| SOZ | `Obj_SOZMiniboss` | Sand sub-boss |
| LRZ | `Obj_LRZMiniboss` | Lava sub-boss |
| DEZ | `Obj_DEZMiniboss` | Death Egg sub-boss |

### End Bosses (Act 2)

| Zone | Label | Notes |
|------|-------|-------|
| AIZ | `Obj_AIZEndBoss` | Eggman fire dropper |
| HCZ | `Obj_HCZEndBoss` | Underwater Eggman |
| MGZ | `Obj_MGZEndBoss` / `Obj_MGZEndBossKnux` | Drill vehicle, Knuckles variant |
| CNZ | `Obj_CNZEndBoss` | Carnival Eggman |
| FBZ | `Obj_FBZEndBoss` | With pillar sub-objects (`Obj_FBZBossPillar`) |
| ICZ | `Obj_ICZEndBoss` | Ice Eggman |
| LBZ | `Obj_LBZEndBoss`, `Obj_LBZFinalBoss1`, `Obj_LBZFinalBoss2`, `Obj_LBZFinalBossKnux` | Multi-phase with Knuckles variant |
| MHZ | `Obj_MHZEndBoss` | Mushroom Eggman |
| SOZ | `Obj_SOZEndBoss` | Sand Eggman |
| LRZ | `Obj_LRZEndBoss` | Lava Eggman |
| SSZ | `Obj_SSZEndBoss` | Sky Sanctuary bosses (remixed S1/S2: `Obj_SSZGHZBoss`, `Obj_SSZMTZBoss`) |
| DEZ | `Obj_DEZEndBoss`, `Obj_DEZ3_Boss` | Multi-phase Death Egg boss |
| DDZ | `Obj_DDZEndBoss` | Doomsday final chase boss |

## Key Differences: S3K Bosses vs S2/S1 Bosses

| Aspect | S3K | S2 | S1 |
|--------|-----|-----|-----|
| **Boss count** | ~25 (mini + end per zone) | ~11 (one per zone) | 7 (one per zone) |
| **Mini-bosses** | Yes (Act 1 bosses) | No | No |
| **Character variants** | Some bosses differ for Knuckles | Single version | Single version |
| **Shield reactions** | `shield_reaction` field affects projectiles | No shield variants | No shield variants |
| **Child sprite system** | Inline child sprites via `mainspr_childsprites` | Separate `AbstractBossChild` | `ObjData` tables |
| **Hit counter** | `collision_property` (0x29) | `objoff_3C` or similar | `obColProp` |
| **Object code** | Inline in `sonic3k.asm` | Inline in `s2.asm` | `_incObj/XX Boss - Zone.asm` |
| **Multi-phase** | Common (LBZ, DEZ have 2-3 phases) | Rare | Rare (FZ only) |
| **Boss spawning** | Via `Special_events_routine` and screen events | `LevelEventManager` | Object-local camera lock |
| **Arena setup** | Zone screen event code | `LevEvents_ZONE2` routines | Boss object code |
| **Knuckles paths** | Separate boss or variant behavior | N/A | N/A |
| **Art compression** | Primarily Kosinski Moduled (`kosm`) | Nemesis (`nem`) | Nemesis (`nem`) |
| **Object pointer tables** | 2 tables (S3KL + SKL) by zone | Single table | Single table |
| **Constants file** | `Sonic3kConstants.java` | `Sonic2Constants.java` | `Sonic1Constants.java` |
| **Registry** | `Sonic3kObjectRegistry.java` | `Sonic2ObjectRegistry.java` | `Sonic1ObjectRegistry.java` |

## Implementation Process

### Phase 1: Research & Discovery

Delegate multiple agents to explore the disassembly. **Include this instruction in each agent prompt:**

> Use the s3k-disasm-guide skill (`.agents/skills/s3k-disasm-guide/SKILL.md`) for reference on disassembly structure, label conventions, RomOffsetFinder commands, and object system patterns.

**Research checklist:**
- [ ] Locate boss object in `docs/skdisasm/sonic3k.asm` (search for `Obj_ZONEMiniboss` or `Obj_ZONEEndBoss`)
- [ ] Determine if this is a mini-boss (Act 1) or end boss (Act 2)
- [ ] Check for Knuckles variant (e.g., `Obj_MGZEndBossKnux`, `Obj_LBZFinalBossKnux`)
- [ ] Find sprite data in `General/Sprites/` or `Levels/{ZONE}/Misc Object Data/`
- [ ] Identify child object spawning pattern (inline children vs `AllocateObjectAfterCurrent`)
- [ ] Document state machine (`routine` / `routine_secondary`) transitions
- [ ] Note arena setup: camera lock coordinates, boundary changes
- [ ] Note defeat sequence timing (explosion count, flee direction)
- [ ] Check for multi-phase transitions (especially LBZ, DEZ)
- [ ] Find boss art addresses using RomOffsetFinder:
  ```bash
  mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search ArtKosM_ZONEBoss" -q
  mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search ZONEBoss" -q
  mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search Eggman" -q
  ```

**Key disassembly patterns to identify:**
- `collision_flags` set to boss collision category
- `collision_property` used for hit count (starts at 8, decremented)
- Boss palette flash via palette RAM writes
- Camera boundary manipulation for arena lock
- `shield_reaction` if boss projectiles are affected by shields
- `character_id` checks for Knuckles-specific behavior
- Child sprite setup via `mainspr_childsprites` (inline children at offset +$16)

### Phase 2: Arena & Level Event Setup

S3K bosses use zone screen events for arena setup. There are two approaches:

#### Option A: Add S3K Support to LevelEventManager
If `LevelEventManager` has been extended for S3K, add zone-specific event handling:

```java
// In LevelEventManager or a Sonic3kLevelEventManager
private void updateAizAct1Events() {
    switch (eventRoutine) {
        case 0 -> {
            // Wait for approach trigger
            if (camera.getX() >= AIZ_MINIBOSS_TRIGGER_X) {
                camera.setMinX(camera.getX());
                eventRoutine += 2;
            }
        }
        case 2 -> {
            // Lock arena and spawn boss
            if (camera.getX() >= AIZ_MINIBOSS_ARENA_X) {
                camera.setMinX((short) AIZ_MINIBOSS_ARENA_MIN);
                camera.setMaxX((short) AIZ_MINIBOSS_ARENA_MAX);
                spawnAizMiniboss();
                eventRoutine += 2;
                AudioManager.getInstance().playMusic(Sonic3kAudioProfile.MUS_MINIBOSS);
            }
        }
        // ...
    }
}
```

#### Option B: Self-Contained Boss Setup
If no S3K LevelEventManager exists yet, the boss object can handle its own arena setup during initialization:

```java
@Override
protected void initializeBossState() {
    Camera camera = Camera.getInstance();
    camera.setMinX((short) ARENA_MIN_X);
    camera.setMaxX((short) ARENA_MAX_X);
    // ... set up arena boundaries from disassembly
}
```

**Extract arena coordinates from disassembly** - search for camera boundary writes in the boss code.

### Phase 3: Boss Instance Class

Create the boss class in the S3K objects package:

```java
package uk.co.jamesj999.sonic.game.sonic3k.objects.bosses;

public class Sonic3kZoneBossInstance extends AbstractBossInstance {

    // State machine constants (from routine / routine_secondary values)
    private static final int ROUTINE_INIT = 0x00;
    private static final int ROUTINE_MAIN = 0x02;
    private static final int ROUTINE_DEFEAT = 0x04;

    // Position constants from disassembly
    private static final int BOSS_START_X = 0xXXXX;
    private static final int BOSS_START_Y = 0xXXXX;

    public Sonic3kZoneBossInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, levelManager, "Zone Boss");
    }

    @Override
    protected void initializeBossState() {
        state.x = BOSS_START_X;
        state.y = BOSS_START_Y;
        state.routineSecondary = ROUTINE_INIT;
        spawnChildComponents();
    }

    @Override
    protected void updateBossLogic(int frameCounter, AbstractPlayableSprite player) {
        switch (state.routineSecondary) {
            case ROUTINE_INIT -> updateInit();
            case ROUTINE_MAIN -> updateMain(player);
            case ROUTINE_DEFEAT -> updateDefeat();
        }
    }

    @Override protected int getInitialHitCount() { return 8; }
    @Override protected int getCollisionSizeIndex() { /* from collision_flags */ return 0x0F; }
    @Override protected void onHitTaken(int remainingHits) { /* hit reaction */ }
    @Override protected void onDefeatStarted() { /* begin defeat sequence */ }
}
```

**Implementation checklist:**
- [ ] Create boss class extending `AbstractBossInstance`
- [ ] Define state machine constants matching `routine`/`routine_secondary` values
- [ ] Define position/velocity constants from disassembly
- [ ] Implement `initializeBossState()` with child spawning
- [ ] Implement `updateBossLogic()` state machine
- [ ] Override `getInitialHitCount()` (typically 8)
- [ ] Override `getCollisionSizeIndex()`
- [ ] Override `onHitTaken()` for hit reactions
- [ ] Override `onDefeatStarted()` for defeat transition
- [ ] Implement `appendRenderCommands()`
- [ ] Handle shield reactions if applicable (`shield_reaction` byte)
- [ ] Handle Knuckles variant if applicable (`character_id` checks)

### Phase 4: Child Components & Inline Children

S3K bosses use **two child systems**:

#### Inline Child Sprites (via `mainspr_childsprites`)
For visual-only children that share the parent's object slot:
```java
// Up to 8 inline child sprites at offset +$16
// These are rendered as part of the parent, not separate objects
// Handle in appendRenderCommands() with per-child offsets
```

#### Independent Child Objects (via `AllocateObjectAfterCurrent`)
For children with separate behavior/collision:
```java
private void spawnChildComponents() {
    ObjectManager manager = LevelManager.getInstance().getObjectManager();

    BossChildInstance child = new BossChildInstance(this, xOffset, yOffset);
    manager.addDynamicObject(child);
    childComponents.add(child);
}
```

Children can extend `AbstractBossChild` (shared with S2) or be independent objects.

### Phase 5: Hit Handling

S3K hit handling uses `collision_property` (same as S2):

```java
@Override
protected void onHitTaken(int remainingHits) {
    // Flash palette
    // Bounce player back
    // Invulnerability period
    // Some bosses change behavior at low health
}
```

**Shield-specific interactions** (S3K-only):
```java
// Boss projectiles may react to shields
private static final int SHIELD_FIRE_IMMUNE = 0x10;
private static final int SHIELD_LIGHTNING_IMMUNE = 0x20;
private static final int SHIELD_BUBBLE_IMMUNE = 0x40;

// Set on projectile child objects
projectile.setShieldReaction(SHIELD_FIRE_IMMUNE);
```

### Phase 6: Multi-Phase Bosses

Several S3K bosses have multiple phases (especially LBZ and DEZ). Implement as:

```java
// Phase transition on defeat
@Override
protected void onDefeatStarted() {
    if (currentPhase < MAX_PHASE) {
        currentPhase++;
        transitionToNextPhase();
    } else {
        startFinalDefeat();
    }
}

private void transitionToNextPhase() {
    // Reset hit count
    // Change movement pattern
    // May spawn new child objects
    // May change art/animation
}
```

### Phase 7: Art Loading

```bash
# Find boss art
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search ArtKosM_ZONEBoss" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search Eggman" -q
```

S3K bosses typically use:
- Shared Eggman art (`ArtKosM_Eggman` or similar)
- Zone-specific boss weapon/vehicle art
- **Kosinski Moduled** compression (primary for S3K, not Nemesis)

**Implementation checklist:**
- [ ] Add ROM address constants to `Sonic3kConstants.java` (create if needed)
- [ ] Add art keys (create `Sonic3kObjectArtKeys.java` if needed)
- [ ] Create loader method (create `Sonic3kObjectArt.java` if needed)
- [ ] Use `safeLoadKosinskiModuledPatterns()` for `ArtKosM_` data
- [ ] Create mappings method (parse from `General/Sprites/` or `Misc Object Data/`)
- [ ] Register in art provider

### Phase 8: Factory Registration

Register in `Sonic3kObjectRegistry` (create if needed):

```java
registerFactory(Sonic3kObjectIds.ZONE_MINIBOSS,
    (spawn, registry) -> new Sonic3kZoneMinibossInstance(spawn, LevelManager.getInstance()));

registerFactory(Sonic3kObjectIds.ZONE_ENDBOSS,
    (spawn, registry) -> new Sonic3kZoneEndBossInstance(spawn, LevelManager.getInstance()));
```

If `Sonic3kObjectRegistry` doesn't exist yet, create it following `Sonic2ObjectRegistry` pattern.

### Phase 9: Code Quality

Ensure the implementation:
- Has no TODOs or placeholder code
- Has no "engine limitation" workarounds
- Uses explicit disassembly references in comments
- Handles object creation and cleanup correctly
- Properly manages object lifecycle
- Follows existing code patterns

### Phase 10: Cross-Validation

Delegate to a review agent:

```
Review the implementation of [ZoneName] Boss against the Sonic 3&K disassembly.

Reference: Use the s3k-disasm-guide skill for disassembly navigation guidance.

Files to review:
- [List all created/modified files]

Disassembly references:
- Boss object: docs/skdisasm/sonic3k.asm (search for Obj_ZONEMiniboss / Obj_ZONEEndBoss)
- Sprite data: docs/skdisasm/General/Sprites/ or docs/skdisasm/Levels/{ZONE}/Misc Object Data/

Validation checklist:
1. Arena setup matches disassembly camera boundaries
2. State machine transitions match routine/routine_secondary values
3. All child/sub-objects spawned correctly (inline children + independent children)
4. Hit count and invulnerability timing match ROM
5. Defeat sequence matches ROM (explosions, flee, EggPrison)
6. Art and mappings render correctly
7. Music transitions work (boss music on spawn, zone music on defeat)
8. Camera boundaries lock/unlock correctly
9. Shield reactions implemented if applicable
10. Knuckles variant handled if applicable
11. Multi-phase transitions correct if applicable
12. No TODOs or simplifications
13. No "engine limitation" workarounds

Report any discrepancies with specific line references.
```

### Phase 11: Finalization

1. **Verify registration** in `Sonic3kObjectRegistry`

2. **Add to IMPLEMENTED_IDS** in `Sonic3kObjectProfile.java` (the `IMPLEMENTED_IDS` set):
   ```java
   0xXX  // ZoneName Boss
   ```
   Keep the set entries sorted numerically.

3. **Build and test**:
   ```bash
   mvn package
   ```

4. Report completion with summary.

## Reference Files

| Purpose | Location |
|---------|----------|
| **Disassembly guide** | `.agents/skills/s3k-disasm-guide/SKILL.md` |
| **Object skill** | `.agents/skills/s3k-implement-object/SKILL.md` |
| Base boss | `src/.../level/objects/boss/AbstractBossInstance.java` |
| Boss state context | `src/.../level/objects/boss/BossStateContext.java` |
| Boss child base | `src/.../level/objects/boss/AbstractBossChild.java` |
| Zone set enum | `src/.../game/sonic3k/constants/S3kZoneSet.java` |
| Object IDs | `src/.../game/sonic3k/constants/Sonic3kObjectIds.java` |
| ROM offsets | `src/.../game/sonic3k/constants/Sonic3kConstants.java` |
| Registry | `src/.../game/sonic3k/objects/Sonic3kObjectRegistry.java` |
| Audio profile | `src/.../game/sonic3k/audio/Sonic3kAudioProfile.java` (to be created) |
| S2 boss examples | `src/.../game/sonic2/objects/bosses/` |
| Disassembly main | `docs/skdisasm/sonic3k.asm` |
| Shared sprites | `docs/skdisasm/General/Sprites/` |
| Zone-specific data | `docs/skdisasm/Levels/{ZONE}/Misc Object Data/` |
| Implemented IDs | `src/.../tools/Sonic3kObjectProfile.java` (IMPLEMENTED_IDS set) |

## Boss-Specific Notes

### AIZ Mini-Boss (`Obj_AIZMiniboss`)
- Fire-breathing sub-boss
- First boss encounter in the game
- Relatively simple pattern

### AIZ End Boss (`Obj_AIZEndBoss`)
- Eggman fire dropper
- Sets Angel Island on fire (palette change transition)
- Arena over waterfall area

### LBZ Bosses (Multi-Phase)
- `Obj_LBZEndBoss` - First phase
- `Obj_LBZFinalBoss1` - Second phase (Ball Shooter)
- `Obj_LBZFinalBoss2` - Third phase (Big Arms)
- `Obj_LBZFinalBossKnux` - Knuckles variant
- Most complex boss chain in S3K

### SSZ End Boss (`Obj_SSZEndBoss`)
- Remixed S1/S2 boss fights: `Obj_SSZGHZBoss`, `Obj_SSZMTZBoss`
- References original game boss patterns
- Sky Sanctuary crumbling arena

### DEZ Bosses (Multi-Phase)
- `Obj_DEZEndBoss` - Main Death Egg boss
- `Obj_DEZ3_Boss` - Death Egg Act 3 boss (Robotnik's giant mech)
- Multi-phase with escalating difficulty

### DDZ End Boss (`Obj_DDZEndBoss`)
- Doomsday Zone final chase boss
- Unique: Super Sonic only, flight chase format
- No traditional arena (auto-scrolling)
- Missile/projectile dodging during chase

