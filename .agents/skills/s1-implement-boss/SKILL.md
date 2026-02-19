---
name: s1-implement-boss
description: "Implement a Sonic 1 zone boss with complete ROM accuracy. This skill guides complete implementation including arena setup, hit handling, defeat sequences, and cross-validation against the Sonic 1 disassembly."
---
# Implement Sonic 1 Boss

Implement a Sonic 1 zone boss with complete ROM accuracy. This skill guides complete implementation including arena setup, hit handling, defeat sequences, and cross-validation against the Sonic 1 disassembly.

## Inputs

$ARGUMENTS: Boss name or zone (e.g., "GHZ boss", "Green Hill boss", "0x3D", "Final Zone boss")

## Related Skills

- **s1disasm-guide** (`.agents/skills/s1disasm-guide/SKILL.md`) - Disassembly navigation, label conventions, RomOffsetFinder
- **s1-implement-object** (`.agents/skills/s1-implement-object/SKILL.md`) - For non-boss Sonic 1 objects and badniks

## Sonic 1 Boss List

| Zone | Object ID | ASM File | Notes |
|------|-----------|----------|-------|
| GHZ | 0x3D | `_incObj/3D Boss - Green Hill (part 1-2).asm` | Wrecking ball on chain |
| MZ | 0x73 | `_incObj/73 Boss - Marble.asm` | Fire dropper over lava |
| SYZ | 0x75 | `_incObj/75 Boss - Spring Yard.asm` | Spike dropper with retracting platforms |
| LZ | 0x77 | `_incObj/77 Boss - Labyrinth.asm` | Rising water chase |
| SLZ | 0x7A | `_incObj/7A Boss - Star Light.asm` | Seesaw bomb launcher |
| SBZ | 0x7D | `_incObj/7D Boss - Scrap Brain 2 & Final.asm` | Appears in SBZ2 cutscene |
| FZ | 0x85 | `_incObj/85 Boss - Final.asm` | Final boss (4 pistons + energy balls) |

## Key Differences: S1 Bosses vs S2 Bosses

| Aspect | Sonic 1 | Sonic 2 |
|--------|---------|---------|
| **Boss object file** | `_incObj/XX Boss - Zone.asm` | Inline in `s2.asm` (ObjXX) |
| **Sub-object spawning** | `BossName_ObjData` tables | `AllocateObjectAfterCurrent` |
| **Hit counter** | `obColProp(a0)` (collision property) | `objoff_3C` or similar |
| **Palette flash** | `v_palette+$22` (line 1, color 1) | Boss-specific palette line |
| **Hit count** | 8 hits (standard) | 8 hits (standard) |
| **Arena setup** | Zone-specific camera lock in object code | `LevEvents_ZONE2` routines |
| **Level events** | No dedicated LevelEventManager for S1 | `LevelEventManager.java` |
| **Music** | `MUS_BOSS` (0x8C) | `Sonic2AudioConstants.MUS_BOSS` |
| **Defeat** | Explosion sequence + flee + EggPrison | Similar pattern |
| **Boss state** | Object-local fields (`obRoutine`, `ob2ndRout`) | `BossStateContext` |
| **Child objects** | Defined via `ObjData` tables or inline | `AbstractBossChild` |
| **Constants file** | `Sonic1Constants.java` | `Sonic2Constants.java` |
| **Art prefix** | `Nem_Eggman`, `Nem_BossItems` | `ArtNem_Eggman`, `ArtNem_BossItems` |

## Implementation Process

### Phase 1: Research & Discovery

Delegate multiple agents to explore the disassembly. **Include this instruction in each agent prompt:**

> Use the s1disasm-guide skill (`.agents/skills/s1disasm-guide/SKILL.md`) for reference on disassembly structure, label conventions, RomOffsetFinder commands, and object system patterns.

**Research checklist:**
- [ ] Locate boss object file in `docs/s1disasm/_incObj/` (may have multiple parts)
- [ ] Find animation file in `docs/s1disasm/_anim/Eggman.asm` or boss-specific
- [ ] Find mapping file in `docs/s1disasm/_maps/` (e.g., `Eggman.asm`, `Boss Items.asm`)
- [ ] Identify sub-object spawning pattern (`ObjData` tables or inline)
- [ ] Document state machine (`obRoutine` / `ob2ndRout`) transitions
- [ ] Note arena setup: camera lock coordinates, boundary changes
- [ ] Note defeat sequence timing (explosion count, flee direction)
- [ ] Find boss art addresses using RomOffsetFinder:
  ```bash
  mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 search Nem_Eggman" -q
  mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 search Nem_BossItems" -q
  mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 search Boss" -q
  ```
  - Use `plc` command to see which PLCs load boss art:
    ```bash
    mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 plc PLC_GHZ" -q
    ```
  - Search results now show PLC cross-references inline

**Key disassembly patterns to identify:**
- `obColType` set to boss collision category
- `obColProp(a0)` used for hit count (starts at 8, decremented)
- Boss palette flash via direct palette RAM writes (`v_palette+$22`)
- Camera boundary manipulation for arena lock
- Sub-object data tables: `BossName_ObjData` defines children with offsets and IDs

### Phase 2: Arena & Level Event Setup

S1 bosses handle their own camera lock and arena setup, unlike S2 which uses `LevelEventManager`. There are two approaches:

#### Option A: Add S1 Support to LevelEventManager
If `LevelEventManager` has been extended for S1, add zone-specific event handling:

```java
// In LevelEventManager or a Sonic1LevelEventManager
private void updateGhzEvents() {
    if (currentAct != 2) return; // Act 3 (0-indexed act 2)
    switch (eventRoutine) {
        case 0 -> {
            // Wait for approach trigger
            if (camera.getX() >= GHZ_BOSS_TRIGGER_X) {
                camera.setMinX(camera.getX());
                eventRoutine += 2;
            }
        }
        case 2 -> {
            // Lock arena and spawn boss
            if (camera.getX() >= GHZ_BOSS_ARENA_X) {
                camera.setMinX((short) GHZ_BOSS_ARENA_MIN);
                camera.setMaxX((short) GHZ_BOSS_ARENA_MAX);
                spawnGhzBoss();
                eventRoutine += 2;
                AudioManager.getInstance().playMusic(Sonic1AudioProfile.MUS_BOSS);
            }
        }
        // ...
    }
}
```

#### Option B: Self-Contained Boss Setup
If no S1 LevelEventManager exists yet, the boss object can handle its own arena setup during initialization (matching the ROM pattern where bosses directly manipulate camera boundaries):

```java
@Override
protected void initializeBossState() {
    // Lock camera to arena
    Camera camera = Camera.getInstance();
    camera.setMinX((short) ARENA_MIN_X);
    camera.setMaxX((short) ARENA_MAX_X);
    // ... set up arena boundaries from disassembly
}
```

**Extract arena coordinates from disassembly** - search for camera boundary writes in the boss ASM or in `Constants.asm`:
```asm
boss_ghz_x: equ $2A70   ; Boss X position
```

### Phase 3: Boss Instance Class

Create the boss class in the S1 objects package:

```java
package uk.co.jamesj999.sonic.game.sonic1.objects.bosses;

public class Sonic1ZoneBossInstance extends AbstractBossInstance {

    // State machine constants (from obRoutine / ob2ndRout values)
    private static final int ROUTINE_INIT = 0x00;
    private static final int ROUTINE_MAIN = 0x02;
    private static final int ROUTINE_DEFEAT = 0x04;

    // Position constants from disassembly
    private static final int BOSS_START_X = 0xXXXX;
    private static final int BOSS_START_Y = 0xXXXX;

    public Sonic1ZoneBossInstance(ObjectSpawn spawn, LevelManager levelManager) {
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
    @Override protected int getCollisionSizeIndex() { /* from obColType */ return 0x0F; }
    @Override protected void onHitTaken(int remainingHits) { /* hit reaction */ }
    @Override protected void onDefeatStarted() { /* begin defeat sequence */ }
}
```

**Implementation checklist:**
- [ ] Create boss class extending `AbstractBossInstance`
- [ ] Define state machine constants matching `obRoutine`/`ob2ndRout` values
- [ ] Define position/velocity constants from disassembly
- [ ] Implement `initializeBossState()` with child spawning
- [ ] Implement `updateBossLogic()` state machine
- [ ] Override `getInitialHitCount()` (typically 8)
- [ ] Override `getCollisionSizeIndex()`
- [ ] Override `onHitTaken()` - S1 uses `obColProp` for remaining hits
- [ ] Override `onDefeatStarted()` for defeat transition
- [ ] Implement `appendRenderCommands()`

### Phase 4: Sub-Object / Child Components

S1 bosses use `ObjData` tables to define child objects. Parse these tables and spawn children:

```java
// S1 pattern: sub-objects defined as data table entries
private void spawnChildComponents() {
    ObjectManager manager = LevelManager.getInstance().getObjectManager();

    // From BossName_ObjData table in disassembly
    // Each entry typically: x_offset, y_offset, mapping_frame, subtype
    BossChildInstance child = new BossChildInstance(this, xOffset, yOffset);
    manager.addDynamicObject(child);
    childComponents.add(child);
}
```

Children can extend `AbstractBossChild` (shared with S2) or be simple independent objects depending on the boss pattern.

### Phase 5: Hit Handling

S1 hit handling differs slightly from S2:

```java
// S1 pattern: obColProp holds remaining hit count
// Palette flash via v_palette+$22 (palette line 1, color 1)
@Override
protected void onHitTaken(int remainingHits) {
    // Flash palette (S1 uses palette line 1)
    // Bounce player back
    // Invulnerability period
}
```

The hit response typically:
1. Decrements `obColProp` (hit count)
2. Checks for defeat (hits == 0)
3. Applies palette flash to boss sprite
4. Sets invulnerability timer
5. May trigger behavior change (some bosses speed up at low health)

### Phase 6: Art Loading

**PLC note:** S1 boss art has dedicated PLC IDs in ArtLoadCues. The shared `PlcParser` utility handles parsing. See `plc-system` skill. Use `RomOffsetFinder plc <name>` to inspect PLC contents from the CLI.

```bash
# Find boss art
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 search Nem_Eggman" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 search Nem_BossItems" -q
```

S1 bosses typically use shared Eggman art plus zone-specific boss weapon art:
- `Nem_Eggman` - Eggpod / Eggman sprite
- `Nem_BossItems` - Shared boss accessories (explosions, etc.)
- Zone-specific art (e.g., `Nem_GHZBoss_Wrecking_Ball` for GHZ)

**Implementation checklist:**
- [ ] Add ROM address constants to `Sonic1Constants.java`
- [ ] Add art keys (create `Sonic1ObjectArtKeys.java` if needed)
- [ ] Create loader method (create `Sonic1ObjectArt.java` if needed)
- [ ] Create mappings method (parse from `_maps/Eggman.asm`, `_maps/Boss Items.asm`)
- [ ] Register in art provider

### Phase 7: Factory Registration

Register in `Sonic1ObjectRegistry`:

```java
registerFactory(Sonic1ObjectIds.ZONE_BOSS,
    (spawn, registry) -> new Sonic1ZoneBossInstance(spawn, LevelManager.getInstance()));
```

If `Sonic1ObjectRegistry` doesn't yet support factories, refactor it following `Sonic2ObjectRegistry` pattern.

### Phase 8: Code Quality

Ensure the implementation:
- Has no TODOs or placeholder code
- Has no "engine limitation" workarounds
- Uses explicit disassembly references in comments
- Handles object creation and cleanup correctly
- Properly manages object lifecycle
- Follows existing code patterns

### Phase 9: Cross-Validation

Delegate to a review agent:

```
Review the implementation of [ZoneName] Boss (0xXX) against the Sonic 1 disassembly.

Reference: Use the s1disasm-guide skill for disassembly navigation guidance.

Files to review:
- [List all created/modified files]

Disassembly references:
- Boss object: docs/s1disasm/_incObj/XX Boss - Zone.asm
- Boss animation: docs/s1disasm/_anim/Eggman.asm (or boss-specific)
- Boss mappings: docs/s1disasm/_maps/Eggman.asm, _maps/Boss Items.asm

Validation checklist:
1. Arena setup matches disassembly camera boundaries
2. State machine transitions match obRoutine/ob2ndRout values
3. All child/sub-objects spawned correctly
4. Hit count and invulnerability timing match ROM
5. Defeat sequence matches ROM (explosions, flee, EggPrison)
6. Art and mappings render correctly
7. Music transitions work (boss music on spawn, zone music on defeat)
8. Camera boundaries lock/unlock correctly
9. No TODOs or simplifications
10. No "engine limitation" workarounds

Report any discrepancies with specific line references.
```

### Phase 10: Finalization

1. **Verify registration** in `Sonic1ObjectRegistry`

2. **Add to IMPLEMENTED_IDS** in `Sonic1ObjectProfile.java` (the `IMPLEMENTED_IDS` set):
   ```java
   Sonic1ObjectIds.ZONE_BOSS
   ```
   Keep the set entries sorted logically with the other entries.

3. **Build and test**:
   ```bash
   mvn package
   ```

4. Report completion with summary.

## Reference Files

| Purpose | Location |
|---------|----------|
| **Disassembly guide** | `.agents/skills/s1disasm-guide/SKILL.md` |
| **Object skill** | `.agents/skills/s1-implement-object/SKILL.md` |
| Base boss | `src/.../level/objects/boss/AbstractBossInstance.java` |
| Boss state context | `src/.../level/objects/boss/BossStateContext.java` |
| Boss child base | `src/.../level/objects/boss/AbstractBossChild.java` |
| Object IDs | `src/.../game/sonic1/constants/Sonic1ObjectIds.java` |
| ROM offsets | `src/.../game/sonic1/constants/Sonic1Constants.java` |
| Audio profile | `src/.../game/sonic1/audio/Sonic1AudioProfile.java` |
| Registry | `src/.../game/sonic1/objects/Sonic1ObjectRegistry.java` |
| S2 boss examples | `src/.../game/sonic2/objects/bosses/` |
| Disassembly bosses | `docs/s1disasm/_incObj/*Boss*.asm` |
| Disassembly mappings | `docs/s1disasm/_maps/Eggman.asm`, `_maps/Boss Items.asm` |
| Implemented IDs | `src/.../tools/Sonic1ObjectProfile.java` (IMPLEMENTED_IDS set) |

## Boss-Specific Notes

### GHZ Boss (0x3D) - Wrecking Ball
- Eggpod swings a wrecking ball on a chain
- Chain links are calculated positions (Multi-Piece pattern)
- Ball has separate collision from Eggpod
- Swings left-right with increasing speed

### MZ Boss (0x73) - Fire Dropper
- Flies over lava arena
- Drops fire projectiles
- Arena has rising/falling lava

### SYZ Boss (0x75) - Spike Dropper
- Drops spikes from above
- Retracting platforms in arena
- Spike timing pattern from disassembly

### LZ Boss (0x77) - Rising Water
- Unique chase boss (not arena-based)
- Water rises, Sonic must climb
- Eggman at top of shaft
- Different defeat pattern (no traditional arena)

### SLZ Boss (0x7A) - Seesaw Bombs
- Uses seesaw mechanic from SLZ
- Launches bombs via seesaw
- Player uses same seesaws to reach Eggman

### SBZ Boss (0x7D) - Scrap Brain / Final Zone Setup
- Appears in SBZ2 cutscene transition
- May share code with FZ boss sequence

### FZ Boss (0x85) - Final Boss
- Multi-phase final boss
- 4 crushing pistons
- Energy ball projectiles
- No rings available
- Most complex S1 boss

