# Implement Object/Badnik

Implement a Sonic 2 object or badnik with complete ROM accuracy. This skill guides complete implementation including art, animation, sound effects, all subtypes, and cross-validation against the disassembly.

## Inputs

$ARGUMENTS: Object name or ID (e.g., "Masher", "0x5C", "Crawl badnik")

## Implementation Process

### Phase 1: Research & Discovery

Delegate multiple agents to:

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
     mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="search ObjectName" -q
     ```
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

Create the instance class following existing patterns:

**For regular objects** - extend `AbstractObjectInstance`:
```java
package uk.co.jamesj999.sonic.game.sonic2.objects;

public class ObjectNameObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, TouchResponseProvider {
    // Implementation
}
```

**For badniks** - extend `AbstractBadnikInstance`:
```java
package uk.co.jamesj999.sonic.game.sonic2.objects.badniks;

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

#### 2.4 Implementation Requirements

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

Reference `Sonic2SmpsLoader` for SFX name → ID mapping:
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
- Uses explicit disassembly references in comments for non-trivial logic
- Handles object creation and cleanup correctly
- Properly manages object lifecycle (spawning, despawning)
- Follows existing code patterns in the codebase

### Phase 4: Cross-Validation

Delegate to a review agent to cross-validate against the disassembly:

```
Review the implementation of [ObjectName] (0xXX) against the Sonic 2 disassembly.

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

Report any discrepancies with specific line references from both code and disassembly.
```

If issues are found:
1. Fix all identified issues
2. Delegate another review agent
3. Repeat until validation passes

### Phase 5: Finalization

Once cross-validation is confirmed bug-free:

1. **Add to IMPLEMENTED_IDS** in `ObjectDiscoveryTool.java` (around line 59):
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
| Implemented IDs | `src/.../tools/ObjectDiscoveryTool.java` (line ~59) |

## Example Implementations

Study these for patterns:
- `BuzzerBadnikInstance.java` - Flying badnik with projectiles
- `GrabberBadnikInstance.java` - Complex multi-state badnik
- `SpringObjectInstance.java` - Object with subtypes
- `CNZRectBlocksObjectInstance.java` - Animated platforms
- `MTZPlatformObjectInstance.java` - Multi-subtype platform
