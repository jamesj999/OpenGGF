# Implement Sonic 1 Object/Badnik

Implement a Sonic 1 object or badnik with complete ROM accuracy. This skill guides complete implementation including art, animation, sound effects, all subtypes, and cross-validation against the Sonic 1 disassembly.

## Inputs

$ARGUMENTS: Object name or ID (e.g., "Crabmeat", "0x1F", "Motobug badnik")

## Related Skills

When delegating agents to explore the disassembly, instruct them to use the **s1disasm-guide** skill for:
- Directory structure and file locations (`_incObj/`, `_anim/`, `_maps/`, `artnem/`)
- Compression types and label prefixes (`Nem_`, `Map_`, `Ani_`)
- RomOffsetFinder tool commands (all require `--game s1` flag)
- Object system reference (S1 field names: `obRoutine`, `obX`, `obY`, etc.)
- Zone abbreviations and IDs (GHZ, LZ, MZ, SLZ, SYZ, SBZ, FZ)

## Implementation Process

### Phase 1: Research & Discovery

Delegate multiple agents to explore the disassembly. **Include this instruction in each agent prompt:**

> Use the s1disasm-guide skill (`.claude/skills/s1disasm-guide/skill.md`) for reference on disassembly structure, label conventions, RomOffsetFinder commands, and object system patterns.

Agents should:

1. **Identify the object** - Parse $ARGUMENTS to determine the object ID and name
   - Search `Sonic1ObjectIds.java` for ID/name mapping
   - Search `Sonic1ObjectRegistry.java` for existing registration
   - If ambiguous, search the disassembly in `docs/s1disasm/_incObj/` for object files

2. **Locate disassembly source** - Find the object's files:
   - Object code: `docs/s1disasm/_incObj/HEX Name.asm` (e.g., `_incObj/1F Crabmeat.asm`)
   - Animation: `docs/s1disasm/_anim/Name.asm` (e.g., `_anim/Crabmeat.asm`)
   - Mappings: `docs/s1disasm/_maps/Name.asm` (e.g., `_maps/Crabmeat.asm`)
   - Some objects have multiple parts: `_incObj/11 Bridge (part 1).asm`, `(part 2).asm`, etc.

3. **Analyze the disassembly** to understand:
   - All routines (indexed by `obRoutine` values: 0, 2, 4, ...)
   - State machines and transitions via `obRoutine` and `ob2ndRout`
   - All subtypes and their behaviors (from `obSubtype` byte interpretation)
   - Movement physics (velocities, timers, ranges)
   - Collision handling (`obColType` = type + size index)
   - Animation sequences and frame timing from `_anim/` file
   - Sound effects triggered (search for `sfx` or sound command references)
   - Art references (`Nem_` labels)

4. **Find art and mappings**:
   - Search for `Nem_` references in the object's ASM file
   - Use RomOffsetFinder to get ROM addresses:
     ```bash
     mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 search ObjectName" -q
     ```
   - Parse mappings from `_maps/Name.asm` (5-byte piece format, byte header)
   - Check if art is zone-specific or shared

5. **Check for boss objects** - If the object is a boss (IDs: 0x3D GHZ, 0x73 MZ, 0x75 SYZ, 0x77 LZ, 0x7A SLZ, 0x7D SBZ, 0x85 FZ):
   - **Redirect to `/s1-implement-boss`** skill instead

### Phase 2: Implementation

#### 2.1 Constants (if needed)

Add ROM address to `Sonic1Constants.java`:
```java
// Object art ROM addresses
public static final int ART_NEM_OBJECTNAME_ADDR = 0xXXXXX;
```

Add to `Sonic1ObjectIds.java` (if not present):
```java
public static final int OBJECT_NAME = 0xXX;
```

#### 2.2 Art Loading

**PLC note:** S1 loads object art via ArtLoadCues (PLCs) during level init. The shared `PlcParser` utility handles parsing. See `plc-system` skill.

**Note:** Sonic 1 art infrastructure is not yet fully established. If `Sonic1ObjectArt.java`, `Sonic1ObjectArtKeys.java`, and `Sonic1ObjectArtProvider.java` do not exist, create them following the Sonic 2 pattern:

1. **Create `Sonic1ObjectArtKeys.java`** (if needed):
   ```java
   package uk.co.jamesj999.sonic.game.sonic1;

   public final class Sonic1ObjectArtKeys {
       private Sonic1ObjectArtKeys() {}
       public static final String OBJECT_NAME = "objectname";
   }
   ```

2. **Create `Sonic1ObjectArt.java`** (if needed):
   Follow the pattern in `Sonic2ObjectArt.java`. Key differences:
   - Use `Sonic1Constants` for ROM addresses
   - S1 mappings use 5-byte piece format (byte header, 5 bytes per piece)
   - Parse mappings accordingly

3. **Create `Sonic1ObjectArtProvider.java`** (if needed):
   Follow `Sonic2ObjectArtProvider.java` pattern.

4. **Add loader method** to `Sonic1ObjectArt.java`:
   ```java
   public ObjectSpriteSheet loadObjectNameSheet() {
       Pattern[] patterns = safeLoadNemesisPatterns(
           Sonic1Constants.ART_NEM_OBJECTNAME_ADDR, "ObjectName");
       if (patterns.length == 0) return null;
       List<SpriteMappingFrame> mappings = createObjectNameMappings();
       return new ObjectSpriteSheet(patterns, mappings, paletteIndex, 1);
   }
   ```

5. **Create S1 mappings method** - Parse from `_maps/Name.asm`:
   ```java
   private List<SpriteMappingFrame> createObjectNameMappings() {
       // S1 format: byte piece_count, then per piece:
       //   byte y_offset, byte size, word pattern, byte x_offset
       // Note: x_offset is a signed BYTE (not word like S2)
   }
   ```

#### 2.3 Object Instance Class

Create the instance class following existing Sonic 2 patterns but in the Sonic 1 package.

**Choose the appropriate pattern based on behavior:**

##### Pattern 1: Simple Object
```java
package uk.co.jamesj999.sonic.game.sonic1.objects;

public class ObjectNameObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {
    // Single object with its own collision and rendering
}
```

##### Pattern 2: Badnik (Enemy with AI)
```java
package uk.co.jamesj999.sonic.game.sonic1.objects.badniks;

public class ObjectNameBadnikInstance extends AbstractBadnikInstance {
    @Override
    protected void updateMovement(int frameCounter, AbstractPlayableSprite player) {
        // Movement AI from disassembly
    }

    @Override
    protected void updateAnimation(int frameCounter) {
        // Animation state machine from _anim/Name.asm
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE_INDEX; // From disassembly obColType
    }
}
```

##### Pattern 3: Boss
**Use the dedicated `/s1-implement-boss` skill** (`.claude/skills/s1-implement-boss/skill.md`) for boss implementations.

**Detect a boss when:**
- Object file is named `Boss - Zone Name.asm`
- Object IDs: 0x3D (GHZ), 0x73 (MZ), 0x75 (SYZ), 0x77 (LZ), 0x7A (SLZ), 0x7D (SBZ), 0x85 (FZ)
- Uses `obColProp` for hit counter
- Has camera lock and arena setup behavior

#### 2.4 Implementation Requirements

**Engine Extensions**: If the ROM uses functionality that the engine doesn't expose, **you MUST extend the engine** rather than working around it or documenting it as a limitation.

Never accept "engine limitation" as a reason for incomplete behavior.

**Constants**: Extract all magic numbers as named constants with disassembly comments:
```java
// From disassembly: move.w #$100,obVelX(a0)
private static final int X_VELOCITY = 0x100;
```

**S1 field name mapping**: When translating disassembly, use these S1→engine mappings:
| S1 Field | Engine Method/Field |
|----------|-------------------|
| `obX` | `getX()` / `setX()` (center coords) |
| `obY` | `getY()` / `setY()` (center coords) |
| `obVelX` | X velocity |
| `obVelY` | Y velocity |
| `obRoutine` | routine state variable |
| `ob2ndRout` | secondary routine |
| `obSubtype` | `spawn.subtype()` |
| `obColType` | collision flags (type + size) |
| `obColProp` | collision property (hit count for bosses) |
| `obFrame` | current mapping frame |
| `obAnim` | current animation ID |

**Subtypes**: Implement ALL subtypes from the subtype byte:
```java
int subtype = spawn.subtype();
int behaviorBits = (subtype >> 4) & 0x0F;
int configBits = subtype & 0x0F;
```

**Sound effects**: Use constants from `Sonic1AudioProfile.java`:
```java
AudioManager.getInstance().playSfx(Sonic1AudioProfile.SFX_SPRING);
```

Key Sonic 1 SFX IDs:
| ID | Name | Constant |
|----|------|----------|
| 0xA0 | Jump | `SFX_JUMP` |
| 0xA1 | Lamppost | `SFX_LAMPPOST` |
| 0xA3 | Death/Hurt | `SFX_DEATH` |
| 0xAC | Hit Boss | `SFX_HIT_BOSS` |
| 0xB4 | Bumper | `SFX_BUMPER` |
| 0xB5 | Ring | `SFX_RING` |
| 0xC1 | Break Item | `SFX_BREAK_ITEM` |
| 0xCC | Spring | `SFX_SPRING` |

**Debug visualization**: Implement when debug enabled:
```java
@Override
public void appendDebugRenderCommands(List<GLCommand> commands) {
    if (!SonicConfigurationService.getInstance().isDebugViewEnabled()) return;
    // Draw collision bounds, state info, etc.
}
```

#### 2.5 Factory Registration

Register in `Sonic1ObjectRegistry`. The current registry returns `PlaceholderObjectInstance` for all objects. Add factory registration by converting to a factory-based pattern (following `Sonic2ObjectRegistry`):

```java
// In Sonic1ObjectRegistry - add factory support
registerFactory(Sonic1ObjectIds.OBJECT_NAME,
    (spawn, registry) -> new ObjectNameObjectInstance(spawn));
```

If `Sonic1ObjectRegistry` doesn't support factories yet, refactor it to match the `Sonic2ObjectRegistry` pattern with `registerFactory()` and `registerDefaultFactories()`.

### Phase 3: Code Quality

Ensure the implementation:
- Has no TODOs or placeholder code
- Has no "engine limitation" workarounds
- Uses explicit disassembly references in comments for non-trivial logic
- Handles object creation and cleanup correctly
- Properly manages object lifecycle (spawning, despawning)
- Follows existing code patterns in the codebase
- Any engine extensions are clean, well-documented, and usable by other objects

### Phase 4: Cross-Validation

Delegate to a review agent to cross-validate against the disassembly. **Include this instruction in the agent prompt:**

> Use the s1disasm-guide skill (`.claude/skills/s1disasm-guide/skill.md`) for reference on disassembly structure, label conventions, and object system patterns.

```
Review the implementation of [ObjectName] (0xXX) against the Sonic 1 disassembly.

Reference: Use the s1disasm-guide skill for disassembly navigation guidance.

Files to review:
- [List all created/modified files]

Disassembly references:
- Object code: docs/s1disasm/_incObj/XX Name.asm
- Animation: docs/s1disasm/_anim/Name.asm
- Mappings: docs/s1disasm/_maps/Name.asm

Validation checklist:
1. Code quality: clean, concise, well-commented
2. Art implementation: patterns, mappings, palette
3. All subtypes implemented with correct behavior
4. Animation frames and timing match _anim/ file
5. Sound effects match disassembly SFX IDs
6. Movement/physics values match disassembly
7. Collision handling matches disassembly (obColType)
8. Debug visualization present
9. No TODOs or simplifications
10. Object lifecycle handled correctly
11. No "engine limitation" workarounds

Report any discrepancies with specific line references from both code and disassembly.
```

If issues are found:
1. Fix all identified issues
2. Delegate another review agent
3. Repeat until validation passes

### Phase 5: Finalization

Once cross-validation is confirmed bug-free:

1. **Verify registration** in `Sonic1ObjectRegistry` - ensure the factory is registered and the object is no longer returned as a `PlaceholderObjectInstance`.

2. **Add to IMPLEMENTED_IDS** in `Sonic1ObjectProfile.java` (the `IMPLEMENTED_IDS` set):
   ```java
   Sonic1ObjectIds.OBJECT_NAME
   ```
   Keep the set entries sorted logically with the other entries.

3. **Update S1_OBJECT_CHECKLIST.md**:
   - Move the object from the "Unimplemented Objects" table to the "Implemented Objects" table
   - Update the summary counts (Implemented/Unimplemented numbers and percentages)
   - In the "By Zone" section, change `[ ]` to `[x]` for every zone/act entry of this object
   - Update the per-act "Implemented" and "Unimplemented" counts in each affected act header

4. **Build and test**:
   ```bash
   mvn package
   ```

5. Report completion with summary of implementation details.

## Reference Files

| Purpose | Location |
|---------|----------|
| **Disassembly guide** | `.claude/skills/s1disasm-guide/skill.md` |
| **Boss skill** | `.claude/skills/s1-implement-boss/skill.md` |
| Object IDs | `src/.../game/sonic1/constants/Sonic1ObjectIds.java` |
| ROM offsets | `src/.../game/sonic1/constants/Sonic1Constants.java` |
| Registry | `src/.../game/sonic1/objects/Sonic1ObjectRegistry.java` |
| Audio profile | `src/.../game/sonic1/audio/Sonic1AudioProfile.java` |
| Base badnik | `src/.../game/sonic2/objects/badniks/AbstractBadnikInstance.java` (shared) |
| Disassembly objects | `docs/s1disasm/_incObj/` |
| Disassembly animations | `docs/s1disasm/_anim/` |
| Disassembly mappings | `docs/s1disasm/_maps/` |
| Disassembly art | `docs/s1disasm/artnem/` |
| Implemented IDs | `src/.../tools/Sonic1ObjectProfile.java` (IMPLEMENTED_IDS set) |
| Object checklist | `S1_OBJECT_CHECKLIST.md` (update after each implementation) |

## S1 vs S2 Key Differences Summary

| Aspect | Sonic 1 | Sonic 2 |
|--------|---------|---------|
| Object ASM location | `_incObj/HEX Name.asm` | Inline in main ASM |
| Animation files | `_anim/Name.asm` | Inline |
| Mapping files | `_maps/Name.asm` | `mappings/sprite/` |
| Mapping format | 5-byte pieces, byte header | 6-byte pieces, word header |
| Art label prefix | `Nem_` | `ArtNem_` |
| Object field names | `obRoutine`, `obX`, `obY` | `routine`, `x_pos`, `y_pos` |
| Block size | 256x256 | 128x128 |
| RomOffsetFinder | `--game s1` required | Default (no flag) |
| Constants file | `Sonic1Constants.java` | `Sonic2Constants.java` |
| Object IDs file | `Sonic1ObjectIds.java` | `Sonic2ObjectIds.java` |
| SFX constants | `Sonic1AudioProfile.java` | `Sonic2AudioConstants.java` |
| Registry | `Sonic1ObjectRegistry.java` | `Sonic2ObjectRegistry.java` |
| Art infrastructure | May need creating | Fully established |
