---
name: s3k-implement-object
description: "Implement a Sonic 3 & Knuckles object or badnik with complete ROM accuracy. This skill guides complete implementation including art, animation, sound effects, all subtypes, and cross-validation against the Sonic 3&K disassembly."
---
# Implement Sonic 3&K Object/Badnik

Implement a Sonic 3 & Knuckles object or badnik with complete ROM accuracy. This skill guides complete implementation including art, animation, sound effects, all subtypes, and cross-validation against the Sonic 3&K disassembly.

## Inputs

$ARGUMENTS: Object name or ID (e.g., "Rhinobot", "Mushmeanie", "Orbinaut badnik")

## Related Skills

When delegating agents to explore the disassembly, instruct them to use the **s3k-disasm-guide** skill for:
- Directory structure and file locations (`Levels/{ZONE}/`, `General/Sprites/`, `sonic3k.asm`)
- Compression types and label prefixes (`ArtKosM_`, `ArtNem_`, `ArtKos_`, `Pal_`, `AnPal_`)
- RomOffsetFinder tool commands (all require `--game s3k` flag)
- Object system reference (S2-style field names: `routine`, `x_pos`, `y_pos`, etc.)
- Zone abbreviations and IDs (AIZ, HCZ, MGZ, CNZ, FBZ, ICZ, LBZ, MHZ, SOZ, LRZ, SSZ, DEZ, DDZ, HPZ)

## Implementation Process

### Critical: Use S&K-Side ROM Addresses

The locked-on ROM has two halves: **S&K** (0x000000–0x1FFFFF) and **S3** (0x200000–0x3FFFFF). Many shared assets exist in both halves with identical data. **Always use S&K-side addresses (< 0x200000)** for all ROM constants in `Sonic3kConstants.java`.

When RomOffsetFinder returns results from both `sonic3k.asm` and `s3.asm`, always use the `sonic3k.asm` address. When reading object disassembly, always use the `sonic3k.asm` version (S3KL code path), as it may contain zone-specific overrides absent from the S3 standalone version.

### Phase 1: Research & Discovery

**Important — Zone-Set-Aware Object IDs:** S3K uses **two object pointer tables** that remap many IDs by zone:
- **S3KL** (S3K-Level Object Set): Zones 0-6 (AIZ, HCZ, MGZ, CNZ, FBZ, ICZ, LBZ)
- **SKL** (SK-Level Object Set): Zones 7-13 (MHZ, SOZ, LRZ, SSZ, DEZ, DDZ)

The same ID can mean different objects depending on the zone set. For example, 0x8F = CaterKillerJr (S3KL) vs Butterdroid (SKL). Use `S3kZoneSet.forZone(zoneId)` to determine which set applies, and `Sonic3kObjectRegistry.getPrimaryName(id, zoneSet)` to resolve the correct name.

Delegate multiple agents to explore the disassembly. **Include this instruction in each agent prompt:**

> Use the s3k-disasm-guide skill (`.agents/skills/s3k-disasm-guide/SKILL.md`) for reference on disassembly structure, label conventions, RomOffsetFinder commands, and object system patterns.

Agents should:

1. **Identify the object** - Parse $ARGUMENTS to determine the object name
   - Search `Sonic3kObjectIds.java` for ID/name mapping
   - Search `Sonic3kObjectRegistry.java` for existing registration
   - If ambiguous, search the disassembly for the object:
     ```bash
     grep -n "Obj_ObjectName" docs/skdisasm/sonic3k.asm
     ```

2. **Locate disassembly source** - Find the object's code and data:
   - Object code: Inline in `docs/skdisasm/sonic3k.asm` as `Obj_Name` routine
   - Shared sprite data: `docs/skdisasm/General/Sprites/{Name}/` (Art, Map, DPLC, Anim)
   - Zone-specific data: `docs/skdisasm/Levels/{ZONE}/Misc Object Data/` (Map, Anim)

3. **Analyze the disassembly** to understand:
   - All routines (indexed by `routine` values: 0, 2, 4, ...)
   - State machines and transitions via `routine` and `routine_secondary`
   - All subtypes and their behaviors (from `subtype` byte interpretation)
   - Movement physics (velocities, timers, ranges)
   - Collision handling (`collision_flags` = type + size index)
   - Shield reactions (`shield_reaction` byte â€” S3K-specific)
   - Character-specific behavior (check for `character_id` comparisons)
   - Animation sequences and frame timing from Anim file
   - Sound effects triggered (search for `sfx` or sound command references)
   - Art references (`ArtKosM_` or `ArtNem_` labels)

4. **Find art and mappings**:
   - Search for art references in the object code
   - Use RomOffsetFinder to get ROM addresses:
     ```bash
     mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search ObjectName" -q
     ```
   - Parse mappings from `General/Sprites/{Name}/Map - Name.asm` or `Levels/{ZONE}/Misc Object Data/Map - Name.asm`
   - S3K mappings use 6-byte piece format with word header (same as S2)
   - Check if art is zone-specific or shared

5. **Check for boss objects** - If the object is a mini-boss or end boss:
   - **Redirect to `/s3k-implement-boss`** skill instead
   - Boss indicators: `Obj_XXXMiniboss`, `Obj_XXXEndBoss`, `collision_property` used as hit counter, camera lock behavior

### Phase 2: Implementation

#### 2.1 Constants (if needed)

Add ROM address to `Sonic3kConstants.java`. If this file does not exist, create it following `Sonic2Constants.java`:

```java
package uk.co.jamesj999.sonic.game.sonic3k.constants;

public final class Sonic3kConstants {
    private Sonic3kConstants() {}

    // Object art ROM addresses
    public static final int ART_KOSM_OBJECTNAME_ADDR = 0xXXXXX;
}
```

Add to `Sonic3kObjectIds.java`. If this file does not exist, create it following `Sonic2ObjectIds.java`:

```java
package uk.co.jamesj999.sonic.game.sonic3k.constants;

public final class Sonic3kObjectIds {
    private Sonic3kObjectIds() {}

    public static final int OBJECT_NAME = 0xXX;
}
```

#### 2.2 Art Loading

S3K art infrastructure exists: `Sonic3kObjectArt.java` (loader methods), `Sonic3kObjectArtProvider.java` (registration/access), `Sonic3kObjectArtKeys.java` (string keys). Follow the existing pattern.

Key differences from S2:
- Use `Sonic3kConstants` for ROM addresses
- S3K primarily uses Kosinski Moduled compression (not regular Kosinski)
- S3K mappings use 6-byte piece format with word header (same as S2)
- Use `safeLoadKosinskiModuledPatterns()` for `ArtKosM_` data
- Use `safeLoadNemesisPatterns()` for `ArtNem_` data

**PLC-loaded art:** Some objects use art loaded by zone PLCs at runtime rather than at level load. If the object's art comes from a zone PLC (e.g., PLC 0x0B for AIZ1 objects), it may need runtime PLC application for act transitions or boss arenas. See the **`s3k-plc-system`** skill for PLC system docs.

##### For level-art objects (art loaded by PLCs or level init):

These objects use patterns already loaded into the level's pattern table. Parse mappings directly from ROM:

1. Find mapping ROM address with RomOffsetFinder: `--game s3k find Map_ObjectName`
2. Add `MAP_*_ADDR` constant to `Sonic3kConstants.java`
3. Extract `artTileBase` and `palette` from the object code's `make_art_tile(base, pal, pri)` instruction
4. Add builder method to `Sonic3kObjectArt.java`:
   ```java
   public ObjectSpriteSheet buildObjectNameSheet() {
       return buildLevelArtSheetFromRom(Sonic3kConstants.MAP_OBJECT_NAME_ADDR,
               artTileBase, palette);
   }
   ```

`buildLevelArtSheetFromRom()` calls `S3kSpriteDataLoader.loadMappingFrames()` to parse S3K 6-byte mapping frames from ROM, automatically computes tile ranges, and delegates to `buildLevelArtSheet()`.

##### For dedicated-art objects (own compressed art):

These objects have self-contained art that doesn't depend on level patterns:

```java
public ObjectSpriteSheet loadObjectNameSheet() {
    Pattern[] patterns = safeLoadKosinskiModuledPatterns(
        Sonic3kConstants.ART_KOSM_OBJECTNAME_ADDR, "ObjectName");
    if (patterns.length == 0) return null;
    List<SpriteMappingFrame> mappings = createObjectNameMappings();
    return new ObjectSpriteSheet(patterns, mappings, paletteIndex, 1);
}
```

##### Fallback: hardcoded mappings

Only hardcode mapping pieces when the ROM mapping table can't be used directly (e.g., dynamically constructed frames, or mappings that combine data from multiple sources).

#### 2.3 Object Instance Class

Create the instance class following existing Sonic 2 patterns but in the Sonic 3&K package.

**Choose the appropriate pattern based on behavior:**

##### Pattern 1: Simple Object
```java
package uk.co.jamesj999.sonic.game.sonic3k.objects;

public class ObjectNameObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {
    // Single object with its own collision and rendering
}
```

##### Pattern 2: Badnik (Enemy with AI)
```java
package uk.co.jamesj999.sonic.game.sonic3k.objects.badniks;

public class ObjectNameBadnikInstance extends AbstractBadnikInstance {
    @Override
    protected void updateMovement(int frameCounter, AbstractPlayableSprite player) {
        // Movement AI from disassembly
    }

    @Override
    protected void updateAnimation(int frameCounter) {
        // Animation state machine from Anim file
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE_INDEX; // From disassembly collision_flags
    }
}
```

##### Pattern 3: Boss
**Use the dedicated `/s3k-implement-boss` skill** (`.agents/skills/s3k-implement-boss/SKILL.md`) for boss implementations.

**Detect a boss when:**
- Object label contains `Miniboss` or `EndBoss`
- Uses `collision_property` for hit counter
- Has camera lock and arena setup behavior
- Spawned by zone screen events

#### 2.4 Implementation Requirements

**Engine Extensions**: If the ROM uses functionality that the engine doesn't expose, **you MUST extend the engine** rather than working around it or documenting it as a limitation.

Never accept "engine limitation" as a reason for incomplete behavior.

**Constants**: Extract all magic numbers as named constants with disassembly comments:
```java
// From disassembly: move.w #$100,x_vel(a0)
private static final int X_VELOCITY = 0x100;
```

**S3K field name mapping**: When translating disassembly, use these S3Kâ†’engine mappings:
| S3K Field | Engine Method/Field |
|-----------|-------------------|
| `x_pos` | `getX()` / `setX()` (center coords) |
| `y_pos` | `getY()` / `setY()` (center coords) |
| `x_vel` | X velocity |
| `y_vel` | Y velocity |
| `routine` | routine state variable |
| `routine_secondary` | secondary routine |
| `subtype` | `spawn.subtype()` |
| `collision_flags` | collision flags (type + size) |
| `collision_property` | collision property (hit count for bosses) |
| `mapping_frame` | current mapping frame |
| `shield_reaction` | shield reaction flags (S3K-specific) |
| `character_id` | player character type (Sonic/Tails/Knuckles) |

**Shield reactions** (S3K-specific): If the object uses `shield_reaction`:
```java
// shield_reaction bit flags
private static final int SHIELD_BOUNCE = 0x08;   // bit 3
private static final int SHIELD_FIRE = 0x10;      // bit 4
private static final int SHIELD_LIGHTNING = 0x20;  // bit 5
private static final int SHIELD_BUBBLE = 0x40;     // bit 6
```

**Character-specific behavior**: Some S3K objects behave differently per character:
```java
// Check character_id for character-specific paths
int characterId = player.getCharacterId(); // 0=Sonic, 1=Tails, 2=Knuckles
if (characterId == 2) {
    // Knuckles-specific behavior
}
```

**Subtypes**: Implement ALL subtypes from the subtype byte:
```java
int subtype = spawn.subtype();
int behaviorBits = (subtype >> 4) & 0x0F;
int configBits = subtype & 0x0F;
```

**Sound effects**: Use constants from `Sonic3kAudioProfile.java` (create if needed):
```java
AudioManager.getInstance().playSfx(Sonic3kAudioProfile.SFX_SPRING);
```

**Debug visualization**: Implement when debug enabled:
```java
@Override
public void appendDebugRenderCommands(List<GLCommand> commands) {
    if (!SonicConfigurationService.getInstance().isDebugViewEnabled()) return;
    // Draw collision bounds, state info, etc.
}
```

#### 2.5 Factory Registration

Register in `Sonic3kObjectRegistry`:

```java
registerFactory(Sonic3kObjectIds.OBJECT_NAME,
    (spawn, registry) -> new ObjectNameObjectInstance(spawn));
```

For badniks:
```java
registerFactory(Sonic3kObjectIds.OBJECT_NAME,
    (spawn, registry) -> new ObjectNameBadnikInstance(spawn, levelManager));
```

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

> Use the s3k-disasm-guide skill (`.agents/skills/s3k-disasm-guide/SKILL.md`) for reference on disassembly structure, label conventions, and object system patterns.

```
Review the implementation of [ObjectName] against the Sonic 3&K disassembly.

Reference: Use the s3k-disasm-guide skill for disassembly navigation guidance.

Files to review:
- [List all created/modified files]

Disassembly references:
- Object code: docs/skdisasm/sonic3k.asm (search for Obj_ObjectName)
- Sprite data: docs/skdisasm/General/Sprites/ObjectName/ (or Levels/{ZONE}/Misc Object Data/)

Validation checklist:
1. Code quality: clean, concise, well-commented
2. Art implementation: patterns, mappings, palette
3. All subtypes implemented with correct behavior
4. Animation frames and timing match Anim file
5. Sound effects match disassembly SFX IDs
6. Movement/physics values match disassembly
7. Collision handling matches disassembly (collision_flags)
8. Shield reactions implemented if applicable (shield_reaction byte)
9. Character-specific behavior handled if applicable (character_id checks)
10. Debug visualization present
11. No TODOs or simplifications
12. Object lifecycle handled correctly
13. No "engine limitation" workarounds

Report any discrepancies with specific line references from both code and disassembly.
```

If issues are found:
1. Fix all identified issues
2. Delegate another review agent
3. Repeat until validation passes

### Phase 5: Finalization

Once cross-validation is confirmed bug-free:

1. **Verify registration** in `Sonic3kObjectRegistry` - ensure the factory is registered and the object is no longer returned as a `PlaceholderObjectInstance`.

2. **Add to IMPLEMENTED_IDS** in `Sonic3kObjectProfile.java` (the `IMPLEMENTED_IDS` set):
   ```java
   0xXX  // ObjectName
   ```
   Keep the set entries sorted numerically.

3. **Build and test**:
   ```bash
   mvn package
   ```

4. Report completion with summary of implementation details.

## Reference Files

| Purpose | Location |
|---------|----------|
| **Disassembly guide** | `.agents/skills/s3k-disasm-guide/SKILL.md` |
| **Boss skill** | `.agents/skills/s3k-implement-boss/SKILL.md` |
| Zone set enum | `src/.../game/sonic3k/constants/S3kZoneSet.java` |
| Object IDs | `src/.../game/sonic3k/constants/Sonic3kObjectIds.java` |
| ROM offsets | `src/.../game/sonic3k/constants/Sonic3kConstants.java` |
| Registry | `src/.../game/sonic3k/objects/Sonic3kObjectRegistry.java` |
| Art loader | `src/.../game/sonic3k/Sonic3kObjectArt.java` |
| Art keys | `src/.../game/sonic3k/Sonic3kObjectArtKeys.java` |
| Art provider | `src/.../game/sonic3k/Sonic3kObjectArtProvider.java` |
| Audio profile | `src/.../game/sonic3k/audio/Sonic3kAudioProfile.java` (to be created) |
| Base badnik | `src/.../game/sonic2/objects/badniks/AbstractBadnikInstance.java` (shared) |
| Disassembly main | `docs/skdisasm/sonic3k.asm` |
| Disassembly constants | `docs/skdisasm/sonic3k.constants.asm` |
| Object ptr table (S3KL) | `docs/skdisasm/Levels/Misc/Object pointers - SK Set 1.asm` |
| Object ptr table (SKL) | `docs/skdisasm/Levels/Misc/Object pointers - SK Set 2.asm` |
| Shared sprites | `docs/skdisasm/General/Sprites/` |
| Zone-specific data | `docs/skdisasm/Levels/{ZONE}/Misc Object Data/` |
| Implemented IDs | `src/.../tools/Sonic3kObjectProfile.java` (IMPLEMENTED_IDS set) |

## S3K Badnik List (Zone-Set-Aware)

**S3KL badniks** (zones 0-6: AIZ through LBZ):
Batbot, Blaster, Blastoid, Bloominator, Bubbles Badnik, Buggernaut, Caterkiller Jr, Clamer, Corkey, Flybot767, Jawz, Mantis, Mega Chopper, Monkey Dude, Orbinaut, Penguinator, Pointdexter, Rhinobot, Ribot, Snale Blaster, Sparkle, Spiker, Star Pointer, Technosqueek, Turbo Spiker

**SKL badniks** (zones 7-13: MHZ through DDZ):
Butterdroid, Chainspike, Cluckoid, Dragonfly, Fireworm, Iwamodoki, Madmole, Mushmeanie, Rockn, Sandworm, Skorp, Spikebonker, Toxomister

## S3K vs S2 vs S1 Key Differences Summary

| Aspect | Sonic 3&K | Sonic 2 | Sonic 1 |
|--------|-----------|---------|---------|
| Object ASM location | Inline in `sonic3k.asm` | Inline in `s2.asm` | `_incObj/HEX Name.asm` |
| Sprite data | `General/Sprites/` + zone `Misc Object Data/` | `mappings/sprite/` | `_anim/`, `_maps/` |
| Mapping format | 6-byte pieces, word header | 6-byte pieces, word header | 5-byte pieces, byte header |
| Art label prefix | `ArtKosM_`, `ArtNem_` | `ArtNem_`, `ArtKos_` | `Nem_` |
| Primary compression | Kosinski Moduled (`kosm`) | Kosinski (`kos`) | Nemesis (`nem`) |
| Object field names | `routine`, `x_pos`, `y_pos` (S2-style) | `routine`, `x_pos`, `y_pos` | `obRoutine`, `obX`, `obY` |
| Shield reactions | `shield_reaction` field (bit flags) | No shield variants | No shield variants |
| Character IDs | 0=Sonic, 1=Tails, 2=Knuckles | N/A | N/A |
| Object size | `$4A` bytes | `$4A` bytes | `$40` bytes |
| RomOffsetFinder | `--game s3k` required | Default (no flag) | `--game s1` required |
| Object pointer tables | 2 tables (S3KL + SKL) by zone | Single table | Single table |
| Constants file | `Sonic3kConstants.java` | `Sonic2Constants.java` | `Sonic1Constants.java` |
| Object IDs file | `Sonic3kObjectIds.java` | `Sonic2ObjectIds.java` | `Sonic1ObjectIds.java` |
| Registry | `Sonic3kObjectRegistry.java` | `Sonic2ObjectRegistry.java` | `Sonic1ObjectRegistry.java` |
| Art infrastructure | Established (`Sonic3kObjectArt/Provider/Keys`) | Fully established | May need creating |

