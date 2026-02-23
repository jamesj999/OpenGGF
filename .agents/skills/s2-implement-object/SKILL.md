---
name: s2-implement-object
description: "Implement a Sonic 2 object or badnik with complete ROM accuracy. This skill guides complete implementation including art, animation, sound effects, all subtypes, and cross-validation against the disassembly."
---
# Implement Sonic 2 Object/Badnik

Implement a Sonic 2 object or badnik with complete ROM accuracy. This skill guides complete implementation including art, animation, sound effects, all subtypes, and cross-validation against the disassembly.

## Inputs

$ARGUMENTS: Object name or ID (e.g., "Masher", "0x5C", "Crawl badnik")

## Related Skills

When delegating agents to explore the disassembly, instruct them to use the **s2disasm-guide** skill for:
- Directory structure and file locations
- Compression types (.nem, .kos, .eni, .sax, .bin)
- Label naming conventions (ArtNem_, Pal_, Obj_, etc.)
- RomOffsetFinder tool commands
- Object system reference (status table offsets, routine patterns)
- Zone abbreviations and IDs

## Implementation Process

### Phase 1: Research & Discovery

Delegate multiple agents to explore the disassembly. **Include this instruction in each agent prompt:**

> Use the s2disasm-guide skill (`.agents/skills/s2disasm-guide/SKILL.md`) for reference on disassembly structure, label conventions, RomOffsetFinder commands, and object system patterns.

Agents should:

1. **Identify the object** - Parse $ARGUMENTS to determine the object ID and name
   - Search `Sonic2ObjectIds.java` and `Sonic2ObjectRegistryData.java` for ID/name mapping
   - If ambiguous, search the disassembly in `docs/s2disasm/` for object references

2. **Locate disassembly source** - Find the object's ASM file in `docs/s2disasm/_anim/` and `docs/s2disasm/` directories:
   ```bash
   find docs/s2disasm -name "Obj*.asm" | xargs grep -l "OBJECT_NAME"
   ```
   Common patterns: `ObjXX.asm`, `ObjXX - Name.asm`

3. **Analyze the disassembly** to understand:
   - All routines (indexed by `objoff_xx` offsets)
   - State machines and transitions
   - All subtypes and their behaviors (from subtype byte interpretation)
   - Movement physics (velocities, timers, ranges)
   - Collision handling (size index, touch response type)
   - Animation sequences and frame timing
   - Sound effects triggered (search for `sfx` or `SndID`)
   - Art/mappings references

4. **Find art and mappings**:
   - Search for `ArtNem_` or `ArtKos_` references in the disassembly
   - Use RomOffsetFinder to get ROM addresses:
     ```bash
     mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="search ObjectName" -q
     ```
   - Search results now show **PLC cross-references** - which PLCs load this art
   - Use `plc <name>` command to see all art entries in a PLC:
     ```bash
     mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="plc PlrList_Ehz1" -q
     ```
   - The ObjectDiscoveryTool checklist also shows PLC IDs per object per zone
   - Check existing art keys in `Sonic2ObjectArtKeys.java`
   - Check if art is zone-specific or shared

### Phase 2: Implementation

#### 2.1 Constants (if needed)

Add to `Sonic2Constants.java`:
```java
// Object art ROM addresses
public static final int ART_NEM_OBJECTNAME_ADDR = 0xXXXXX;
```

Add to `Sonic2ObjectIds.java` (if not present):
```java
public static final int OBJECT_NAME = 0xXX;
```

#### 2.2 Art Loading (if needed)

**PLC note:** S2 art is loaded via ArtLoadCues (PLCs) in the ROM. The shared `PlcParser` utility handles parsing. See `plc-system` skill. Use `RomOffsetFinder plc <name>` to inspect PLC contents from the CLI. The ObjectDiscoveryTool checklist shows PLC IDs per object.

If the object needs new art:

1. Add art key to `Sonic2ObjectArtKeys.java`:
   ```java
   public static final String OBJECT_NAME = "objectname";
   ```

2. Add loader method to `Sonic2ObjectArt.java`:
   ```java
   public ObjectSpriteSheet loadObjectNameSheet() {
       Pattern[] patterns = safeLoadNemesisPatterns(
           Sonic2Constants.ART_NEM_OBJECTNAME_ADDR, "ObjectName");
       if (patterns.length == 0) return null;
       List<SpriteMappingFrame> mappings = createObjectNameMappings();
       return new ObjectSpriteSheet(patterns, mappings, paletteIndex, 1);
   }
   ```

3. Create mappings method matching disassembly:
   ```java
   private List<SpriteMappingFrame> createObjectNameMappings() {
       // Parse from Map_ObjectName in disassembly
       // Each piece: x_offset, y_offset, width_tiles, height_tiles, pattern_index, flags
   }
   ```

4. Register in `Sonic2ObjectArtProvider.loadArtForZone()`:
   ```java
   registerSheet(Sonic2ObjectArtKeys.OBJECT_NAME, artLoader.loadObjectNameSheet());
   ```

**For zone-specific graphics**: Create separate loader methods and register conditionally based on zone.

#### 2.3 Object Instance Class

Create the instance class following existing patterns. **Choose the appropriate pattern based on the object's behavior:**

##### Pattern 1: Simple Object (Single Entity)
For objects that exist as a single collision/render entity:

```java
package com.openggf.sonic.game.sonic2.objects;

public class ObjectNameObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {
    // Single object with its own collision and rendering
}
```
**Examples:** `SpringObjectInstance`, `MonitorObjectInstance`, `MTZPlatformObjectInstance`

##### Pattern 2: Multi-Piece Solid (Single State Machine, Multiple Collision Pieces)
For objects with multiple collision surfaces calculated from a **single state machine**:

```java
public class ObjectNameObjectInstance extends AbstractObjectInstance
        implements MultiPieceSolidProvider, SolidObjectListener {

    @Override
    public int getPieceCount() { return NUM_PIECES; }

    @Override
    public int getPieceX(int pieceIndex) {
        // Calculate piece position from single state (e.g., rotation angle)
        return baseX + calculateOffset(pieceIndex);
    }

    @Override
    public SolidObjectParams getPieceParams(int pieceIndex) {
        return PIECE_PARAMS;  // Shared or per-piece collision
    }
}
```
**Use when:**
- All pieces move together based on shared state (rotation, interpolation)
- Pieces are calculated, not independent entities
- Single `update()` method calculates all piece positions

**Examples:**
- `ARZRotPformsObjectInstance` - 3 orbiting platforms + 9 chain links, all rotating around a single center point
- `CPZStaircaseObjectInstance` - 4 platform pieces interpolating together as a staircase

##### Pattern 3: Parent-Child Spawning (Independent Child Objects)
For objects that spawn separate, independent child objects:

```java
public class ObjectNameObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider {

    private void spawnChildren() {
        ObjectManager manager = LevelManager.getInstance().getObjectManager();

        // Create child with its own spawn data
        ObjectSpawn childSpawn = new ObjectSpawn(childX, childY, objectId, childSubtype, ...);
        ChildObjectInstance child = new ChildObjectInstance(childSpawn, "ChildName");
        manager.addDynamicObject(child);  // Child becomes independent
    }
}
```
**Use when:**
- Children have independent state machines
- Children can be destroyed/activated separately
- Children move independently (not calculated from parent state)
- ROM uses `AllocateObjectAfterCurrent` to spawn children

**Examples:**
- `MCZRotPformsObjectInstance` - Parent (subtype 0x18) spawns 2 child platforms with independent movement
- `BubbleGeneratorObjectInstance` - Spawns individual bubble objects
- `EggPrisonObjectInstance` - Spawns button, lock, and animals as separate objects
- `AbstractBadnikInstance` - Spawns explosion + animal on destruction

##### Pattern 4: Badnik (Enemy with AI)
For enemies with touch response and destruction behavior:

```java
package com.openggf.sonic.game.sonic2.objects.badniks;

import com.openggf.game.sonic2.objects.badniks.AbstractBadnikInstance;

public class ObjectNameBadnikInstance extends AbstractBadnikInstance {
    @Override
    protected void updateMovement(int frameCounter, AbstractPlayableSprite player) {
        // Movement AI from disassembly
    }

    @Override
    protected void updateAnimation(int frameCounter) {
        // Animation state machine
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE_INDEX; // From disassembly collision_flags
    }
}
```
**Examples:** `BuzzerBadnikInstance`, `GrabberBadnikInstance`, `MasherBadnikInstance`

##### Pattern 5: Boss (Zone Act 2 Boss Fights)

**Use the dedicated `/s2-implement-boss` skill** (`.agents/skills/s2-implement-boss/SKILL.md`) for boss implementations.

Bosses differ significantly from regular objects:
- Dynamic spawning via `LevelEventManager` (not level layout)
- Camera arena locking with min/max boundaries
- 8 hits with invulnerability and palette flash
- Multi-component architecture with `AbstractBossChild`
- Defeat sequences (explosions, flee, EggPrison spawn)
- Music transitions (fade, boss music, resume)

**Detect a boss when disassembly shows:**
- Object spawned by `LevEvents_XXX` routines
- `collision_flags` set to `$C0 | size_index` (boss category)
- Uses `Boss_HandleHits` pattern

##### Choosing the Right Pattern

| Disassembly Pattern | Engine Pattern | Key Indicator |
|---------------------|----------------|---------------|
| Single object, single collision | Simple Object | No child allocation, single `width_pixels`/`y_radius` |
| Loop calculating piece positions | Multi-Piece Solid | Pieces calculated from shared angle/state |
| `AllocateObjectAfterCurrent` calls | Parent-Child Spawning | Children get independent `routine`/`subtype` |
| `Obj25` (enemy) base routines | Badnik | Uses touch response, spawns explosion on death |
| `LevEvents_XXX` spawning, `Boss_HandleHits` | **Use /s2-implement-boss** | Spawned by level events, 8 hits, camera lock |

#### 2.4 Implementation Requirements

**Engine Extensions**: If the ROM uses functionality that the engine doesn't expose, **you MUST extend the engine** rather than working around it or documenting it as a limitation. Examples:
- ROM reads button state (up/down/left/right) â†’ Engine must expose `isUpPressed()`, `isDownPressed()`, etc.
- ROM uses a RAM variable for inter-object communication â†’ Engine must provide equivalent manager/service
- ROM accesses player state not currently exposed â†’ Add the getter/setter to `AbstractPlayableSprite`

Never accept "engine limitation" as a reason for incomplete behavior. The engine exists to support ROM-accurate implementations.

When extending the engine:
1. Search for similar existing functionality to follow established patterns
2. Add fields/methods to the appropriate class (e.g., `AbstractPlayableSprite` for player state)
3. Update any input/update pipelines that need to populate the new state (e.g., `SpriteManager` for input)
4. Make the extension general-purpose so other objects can use it
5. Document the extension with ROM references in comments

**Constants**: Extract all magic numbers as named constants with disassembly comments:
```java
// From disassembly: move.w #$180,x_vel(a0)
private static final int X_VELOCITY = 0x180;
```

**Subtypes**: Implement ALL subtypes from the subtype byte interpretation:
```java
int subtype = spawn.subtype();
int behaviorBits = (subtype >> 4) & 0x0F;
int configBits = subtype & 0x0F;
```

**Sound effects**: Use constants from `Sonic2AudioConstants.java`:
```java
AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_SPRING);
```

Reference `Sonic2SmpsLoader` for SFX name â†’ ID mapping:
| ID | Name |
|----|------|
| 0xA0 | Jump |
| 0xA1 | Checkpoint |
| 0xA3 | Hurt |
| 0xB4 | Bumper |
| 0xB9 | Smash |
| 0xC1 | Explosion |
| 0xCC | Spring |
| ... | (see Sonic2SmpsLoader for full list) |

**Debug visualization**: Implement when debug enabled:
```java
@Override
public void appendDebugRenderCommands(List<GLCommand> commands) {
    if (!SonicConfigurationService.getInstance().isDebugViewEnabled()) return;
    // Draw collision bounds, state info, sensor rays, etc.
}
```

#### 2.5 Factory Registration

Register in `Sonic2ObjectRegistry.registerDefaultFactories()`:
```java
registerFactory(Sonic2ObjectIds.OBJECT_NAME,
    (spawn, registry) -> new ObjectNameObjectInstance(spawn));
```

For badniks:
```java
registerFactory(Sonic2ObjectIds.OBJECT_NAME,
    (spawn, registry) -> new ObjectNameBadnikInstance(spawn, levelManager));
```

### Phase 3: Code Quality

Ensure the implementation:
- Has no TODOs or placeholder code
- Has no "engine limitation" workarounds - if the ROM does it, the engine must support it
- Uses explicit disassembly references in comments for non-trivial logic
- Handles object creation and cleanup correctly
- Properly manages object lifecycle (spawning, despawning)
- Follows existing code patterns in the codebase
- Any engine extensions are clean, well-documented, and usable by other objects

### Phase 4: Cross-Validation

Delegate to a review agent to cross-validate against the disassembly. **Include this instruction in the agent prompt:**

> Use the s2disasm-guide skill (`.agents/skills/s2disasm-guide/SKILL.md`) for reference on disassembly structure, label conventions, and object system patterns.

```
Review the implementation of [ObjectName] (0xXX) against the Sonic 2 disassembly.

Reference: Use the s2disasm-guide skill for disassembly navigation guidance.

Files to review:
- [List all created/modified files]

Disassembly reference: docs/s2disasm/...

Validation checklist:
1. Code quality: clean, concise, well-commented
2. Art implementation: patterns, mappings, palette
3. All subtypes implemented with correct behavior
4. Animation frames and timing match disassembly
5. Sound effects match disassembly SFX IDs
6. Movement/physics values match disassembly
7. Collision handling matches disassembly
8. Debug visualization present
9. No TODOs or simplifications
10. Object lifecycle handled correctly
11. No "engine limitation" workarounds - any missing engine functionality was added

Report any discrepancies with specific line references from both code and disassembly.
```

If issues are found:
1. Fix all identified issues
2. Delegate another review agent
3. Repeat until validation passes

### Phase 5: Finalization

Once cross-validation is confirmed bug-free:

1. **Add to IMPLEMENTED_IDS** in `Sonic2ObjectProfile.java` (the `IMPLEMENTED_IDS` set):
   ```java
   0xXX,  // ObjectName (brief description)
   ```
   Keep the list sorted numerically.

2. **Build and test**:
   ```bash
   mvn package
   ```

3. Report completion with summary of implementation details.

## Reference Files

| Purpose | Location |
|---------|----------|
| **Disassembly guide** | `.agents/skills/s2disasm-guide/SKILL.md` |
| **Boss skill** | `.agents/skills/s2-implement-boss/SKILL.md` |
| Object IDs | `src/.../game/sonic2/constants/Sonic2ObjectIds.java` |
| ROM offsets | `src/.../game/sonic2/constants/Sonic2Constants.java` |
| Art keys | `src/.../game/sonic2/Sonic2ObjectArtKeys.java` |
| Art loader | `src/.../game/sonic2/Sonic2ObjectArt.java` |
| Art provider | `src/.../game/sonic2/Sonic2ObjectArtProvider.java` |
| Registry | `src/.../game/sonic2/objects/Sonic2ObjectRegistry.java` |
| Base badnik | `src/.../game/sonic2/objects/badniks/AbstractBadnikInstance.java` |
| SFX mapping | `src/.../game/sonic2/audio/smps/Sonic2SmpsLoader.java` |
| SFX constants | `src/.../game/sonic2/constants/Sonic2AudioConstants.java` |
| Disassembly | `docs/s2disasm/` |
| Implemented IDs | `src/.../tools/Sonic2ObjectProfile.java` (IMPLEMENTED_IDS set) |

## Example Implementations

Study these for patterns:

### Simple Objects (Single Entity)
- `SpringObjectInstance.java` - Object with subtypes (red/yellow, direction variants)
- `MTZPlatformObjectInstance.java` - Multi-subtype platform with 12 movement types
- `CNZRectBlocksObjectInstance.java` - Animated platform with state machine

### Multi-Piece Solid (Single State, Multiple Collision Pieces)
- `ARZRotPformsObjectInstance.java` - 3 orbiting platforms + 9 chain links rotating together
- `CPZStaircaseObjectInstance.java` - 4 platform pieces interpolating as staircase

### Parent-Child Spawning (Independent Children)
- `MCZRotPformsObjectInstance.java` - Parent spawns 2 independent moving platforms (subtype 0x18)
- `BubbleGeneratorObjectInstance.java` - Spawns individual bubble objects on timer
- `EggPrisonObjectInstance.java` - Spawns button, lock, and animals as separate entities

### Badniks (Enemies with AI)
- `BuzzerBadnikInstance.java` - Flying badnik with projectile spawning
- `GrabberBadnikInstance.java` - Complex multi-state spider badnik with grabbing
- `MasherBadnikInstance.java` - Simple jumping fish badnik

