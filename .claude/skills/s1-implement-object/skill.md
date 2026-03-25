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
     mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 search ObjectName" -q
     ```
   - Search results now show **PLC cross-references** - which PLCs load this art
   - Use `plc <name>` command to see all art entries in a PLC:
     ```bash
     mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 plc PLC_GHZ" -q
     ```
   - The ObjectDiscoveryTool checklist also shows PLC IDs per object per zone
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

**PLC note:** S1 loads object art via ArtLoadCues (PLCs) during level init. The shared `PlcParser` utility handles parsing. See `plc-system` skill. Use `RomOffsetFinder plc <name>` to inspect PLC contents from the CLI. The ObjectDiscoveryTool checklist shows PLC IDs per object. For dedicated object art that shouldn't overwrite level pattern tiles, use standalone decompression: `PlcParser.decompressAll(rom, plc)` returns independent `Pattern[]` arrays per PLC entry.

**S1 art infrastructure exists:** `Sonic1ObjectArt.java` and `Sonic1ObjectArtProvider.java` are established. Art keys use the shared `ObjectArtKeys` class (`com.openggf.level.objects.ObjectArtKeys`). Follow the existing pattern:

1. **Add art key** to `ObjectArtKeys.java`:
   ```java
   public static final String OBJECT_NAME = "objectname";
   ```

2. **Add loader method** to `Sonic1ObjectArt.java`:
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
package com.openggf.game.sonic1.objects;

public class ObjectNameObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {
    // Single object with its own collision and rendering
}
```

##### Pattern 2: Badnik (Enemy with AI)
```java
package com.openggf.game.sonic1.objects.badniks;

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

#### 2.4 Reusable Engine Utilities

**IMPORTANT: Before writing any physics, movement, or collision code, check these existing utilities. Do NOT reimplement functionality that already exists.**

##### Movement & Physics

| Utility | Location | Use When |
|---------|----------|----------|
| `SubpixelMotion.moveSprite(state, gravity)` | `com.openggf.level.objects.SubpixelMotion` | 16:8 fixed-point position update with gravity (ROM's `ObjectFall`/`MoveSprite`). Create a `SubpixelMotion.State` field, sync positions/velocities before call, read back after. |
| `SubpixelMotion.moveSprite2(state)` | `com.openggf.level.objects.SubpixelMotion` | Same without gravity (ROM's `MoveSprite2`). |
| `SubpixelMotion.moveX(state)` | `com.openggf.level.objects.SubpixelMotion` | X-only movement (horizontal projectiles). |
| `SwingMotion.update()` | `com.openggf.physics.SwingMotion` | Object oscillates/bobs/swings (ROM's `Swing_UpAndDown`). |
| `PatrolMovementHelper` | `com.openggf.level.objects.PatrolMovementHelper` | Left-right patrol with turn-at-edge detection. |
| `PlatformBobHelper` | `com.openggf.level.objects.PlatformBobHelper` | Sine-based standing-nudge displacement for platforms. |
| `ObjectTerrainUtils.checkFloorDist()` | `com.openggf.physics` | Single-point floor/ceiling/wall detection for objects. |
| `TrigLookupTable.calcAngle()` / `sinHex()` / `cosHex()` | `com.openggf.physics` | ROM-accurate angle calculation and trig. |

##### Base Classes

| Base Class | Location | Use When |
|------------|----------|----------|
| `AbstractBadnikInstance` | `com.openggf.level.objects` | All badniks (enemies). Provides touch response, destruction with `DestructionEffects`, debug rendering. S1 badniks pass S1-specific `DestructionConfig`. Objects receive `ObjectServices` via injection — use `services()` to access camera, audio, level, game state. |
| `AbstractProjectileInstance` | `com.openggf.level.objects` | Fire-and-forget projectiles (missiles, fireballs). Handles motion, gravity, off-screen destroy, HURT collision. |
| `AbstractMonitorObjectInstance` | `com.openggf.level.objects` | Monitor objects. Shared icon-rise physics and state machine. Override `applyPowerup()`. |
| `AbstractPointsObjectInstance` | `com.openggf.level.objects` | Floating score popups. Shared rise-and-fade physics. Override `getFrameForScore()`. |
| `GravityDebrisChild` | `com.openggf.level.objects` | Debris/fragment children with gravity. Override `appendRenderCommands()`. |

##### Collision & Touch Response

| Pattern | When to Use |
|---------|-------------|
| `TouchResponseAttackable` + `TouchResponseProvider` | Destroyable enemies. Player jump/roll destroys them. |
| `TouchResponseProvider` only (no `Attackable`) | Non-destroyable hazards. Return `0x80 \| sizeIndex` for HURT. |
| `DestructionEffects.destroyBadnik()` | Explosion + animal + points on badnik defeat. |
| `SpringBounceHelper` | `com.openggf.level.objects.SpringBounceHelper` — shared spring bounce physics. |

##### Rendering & Animation

| Utility | Use When |
|---------|----------|
| `getRenderer(artKey)` | Static method on `AbstractObjectInstance`. Returns ready `PatternSpriteRenderer` or null. Use instead of manual render manager access. |
| `AnimationTimer` | `com.openggf.util.AnimationTimer` — cyclic frame animation timer. |
| `LazyMappingHolder` | `com.openggf.util.LazyMappingHolder` — lazy-loading holder for sprite mappings. |
| `PatternDecompressor` | `com.openggf.util.PatternDecompressor` — bytes→Pattern[] conversion. |

##### Object Lifecycle

| Utility | Use When |
|---------|----------|
| `buildSpawnAt(x, y)` | Inherited from `AbstractObjectInstance`. Use in `getSpawn()` overrides instead of constructing `new ObjectSpawn(...)` manually. |
| `isPlayerRiding()` | Inherited from `AbstractObjectInstance`. Safe null-check chain for platform riding detection. |
| `isOnScreen(margin)` | Inherited from `AbstractObjectInstance`. Off-screen visibility check. |
| `DebugRenderContext` | `com.openggf.debug.DebugRenderContext` — use for `appendDebugRenderCommands()`. |

#### 2.5 Implementation Requirements

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
services().audioManager().playSfx(Sonic1AudioProfile.SFX_SPRING);
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

#### 2.6 Factory Registration

Register in `Sonic1ObjectRegistry.registerDefaultFactories()`:

```java
registerFactory(Sonic1ObjectIds.OBJECT_NAME,
    (spawn, registry) -> new ObjectNameObjectInstance(spawn));
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
| Art infrastructure | Established (`Sonic1ObjectArt/Provider/Keys`) | Fully established |
