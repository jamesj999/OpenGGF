# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Java-based Sonic the Hedgehog game engine that faithfully recreates the original Mega Drive/Genesis physics. It loads game data from the original Sonic 2 ROM and aims for pixel-perfect gameplay recreation.

**Critical requirement:** The engine must replicate original physics pixel-for-pixel. Accuracy is paramount.

## Build & Run Commands

```bash
# Build (creates executable JAR with dependencies)
mvn package

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=TestCollisionLogic

# Run the engine (requires ROM file)
java -jar target/sonic-engine-0.1.20260110-jar-with-dependencies.jar
```

## ROM Requirement

The engine supports Sonic 1, Sonic 2, and Sonic 3&K ROM modules. Keep the relevant ROMs in the working directory (typically gitignored):
- `Sonic The Hedgehog (W) (REV01) [!].gen`
- `Sonic The Hedgehog 2 (W) (REV01) [!].gen`
- `Sonic and Knuckles & Sonic 3 (W) [!].gen`

For development/testing, the Sonic 2 ROM can be downloaded from: `http://bluetoaster.net/secretfolder/Sonic%20The%20Hedgehog%202%20%28W%29%20%28REV01%29%20%5B!%5D.gen`

For S3K-focused tests, pass: `-Ds3k.rom.path="Sonic and Knuckles & Sonic 3 (W) [!].gen"`

`TestRomLogic` is intentionally skipped when the ROM is absent.

## Reference Materials

- **`docs/s2disasm/`** - Sonic 2 disassembly (68000 assembly). Use this to understand original game logic, ROM addresses, and behavior. Essential for accuracy verification.
- **`docs/skdisasm/`** - Sonic 3&K disassembly. Primary reference for S3K level layout, solid index pointers, and level event parity.
- **`docs/SMPS-rips/SMPSPlay/`** - SMPS audio driver source and reference implementations
- **`docs/s2ssedit-0.2.0/`** - Special stage editor source code

These directories are untracked (not in git) but available locally.

## ROM Offset Finder Tool

If `docs/s2disasm`, `docs/s1disasm`, or `docs/skdisasm` is present, use the **RomOffsetFinder** tool to search for disassembly items and find their ROM offsets. Supports Sonic 1, Sonic 2, and Sonic 3&K.

### Quick Reference

```bash
# Sonic 2 (default) - search, verify, export
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="search SpecialStars" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="verify ArtNem_SpecialHUD" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="export nem ART_" -q

# Sonic 1 - use --game s1 flag
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 search Nem_GHZ" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 list nem" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 search Pal_Sonic" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 verify Pal_Sonic" -q

# Sonic 3&K - use --game s3k flag
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search AIZ" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k list nem" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search Pal_AIZ" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k verify ArtNem_TitleScreenText" -q

# Other commands (work with all games)
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="list nem" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="test 0xDD8CE nem" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="test 0x3000 auto" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="verify-batch nem" -q
```

Game selection: `--game s1`, `--game s2` (default), or `--game s3k`. Also supports `-Dgame=s1` system property. Auto-detects from disasm path if `--game` not specified.

The `search` command automatically calculates ROM offsets using known anchor points from the game's `GameProfile`. Verified offsets are added as runtime anchors to improve accuracy for nearby items.

### CLI Commands

| Command | Description |
|---------|-------------|
| `[--game s1\|s2\|s3k] search <pattern>` | Search for items by label/filename |
| `[--game s1\|s2\|s3k] find <label> [offset]` | Find ROM offset by decompression search |
| `[--game s1\|s2\|s3k] test <offset> <type>` | Test decompression at offset |
| `[--game s1\|s2\|s3k] list [type]` | List all includes (optionally by type) |
| `[--game s1\|s2\|s3k] verify <label>` | Verify calculated offset against ROM |
| `[--game s1\|s2\|s3k] verify-batch [type]` | Batch verify all/filtered items |
| `[--game s1\|s2\|s3k] export <type> [prefix]` | Export verified offsets as Java constants |

### Compression Types
| Type | Extension | CLI Arg |
|------|-----------|---------|
| Nemesis | `.nem` | `nem` |
| Kosinski | `.kos` | `kos` |
| Kosinski Moduled | `.kosm` | `kosm` |
| Enigma | `.eni` | `eni` |
| Saxman | `.sax` | `sax` |
| Uncompressed | `.bin` | `bin` |

**Note:** S3K encodes compression type in the label suffix (e.g., `AIZ1_8x8_Primary_KosM`) rather than the file extension. The tool automatically infers the correct type from the label when the file extension is `.bin`.

### Palette Macro Support

The tool parses `palette` macro lines from the disassembly:
```assembly
Pal_SS: palette Special Stage Main.bin ; comment
```
Search for palettes with `search Pal_SS`. They appear as `art/palettes/` paths with `Uncompressed` type.

### Programmatic Usage

```java
// Search the disassembly
DisassemblySearchTool searchTool = new DisassemblySearchTool("docs/s2disasm");
List<DisassemblySearchResult> results = searchTool.search("Ring");

// Test decompression at a ROM offset
CompressionTestTool testTool = new CompressionTestTool("path/to/rom.gen");
CompressionTestResult result = testTool.testDecompression(0x3000, CompressionType.NEMESIS);

// Verify and export offsets
RomOffsetFinder finder = new RomOffsetFinder("docs/s2disasm", "rom.gen");
VerificationResult vr = finder.verify("ArtNem_SpecialHUD");
List<VerificationResult> batch = finder.verifyBatch(CompressionType.NEMESIS);

// Export as Java constants
ConstantsExporter exporter = new ConstantsExporter();
exporter.exportAsJavaConstants(batch, "ART_", writer);
```

See `uk.co.jamesj999.sonic.tools.disasm` package for full API.

## Architecture

### Entry Point
`uk.co.jamesj999.sonic.Engine` - GLFW window with manual timing loop. Creates the game loop via `display()` → `update()` → `draw()`.

### Core Managers (Singleton Pattern)
The codebase uses singletons extensively via `getInstance()`:
- **LevelManager** - Level loading, rendering, zone/act management
- **SpriteManager** - All game sprites (player, objects, enemies), input handling, render bucketing
- **GraphicsManager** - OpenGL rendering, shader management
- **AudioManager** - SMPS audio driver, YM2612/PSG synthesis
- **Camera** - Camera position tracking, following player

### Core Services Façade
Access core singletons through the `GameServices` façade instead of direct `getInstance()` calls:
```java
GameServices.gameState()    // GameStateManager - score, lives, emeralds
GameServices.timers()       // TimerManager - event timing
GameServices.rom()          // RomManager - ROM data access
GameServices.debugOverlay() // DebugOverlayManager - debug rendering
```

### Key Packages
| Package | Purpose |
|---------|---------|
| `sprites.playable` | Sonic/Tails player logic, physics |
| `physics` | Terrain collision, sensors |
| `level` | Level structures, rendering, scrolling |
| `level.objects` | Game object management, rendering, factories |
| `audio` | SMPS driver, YM2612/PSG chip emulation |
| `data` | ROM loading, decompression (Kosinski, Nemesis, Saxman) |
| `game` | Core game-agnostic interfaces and providers |
| `game.sonic2` | Sonic 2-specific implementations |
| `game.sonic2.objects` | Object factories and instance classes |
| `game.sonic2.objects.badniks` | Badnik AI implementations |
| `game.sonic2.scroll` | Zone-specific parallax scroll handlers |
| `game.sonic2.constants` | ROM offsets, object IDs, audio constants |
| `game.sonic3k` | Sonic 3&K game module, level loading, bootstrap, and event managers |
| `game.sonic3k.constants` | Sonic 3&K ROM table addresses and decode constants |
| `tools` | Compression utilities (KosinskiReader, etc.) |

### Consolidated Subsystems

Several manager classes have been consolidated to reduce complexity while preserving functionality:

#### Object System (`ObjectManager`)
The `ObjectManager` now contains all object-related functionality as inner classes:
| Inner Class | Purpose |
|-------------|---------|
| `ObjectManager.Placement` | Spawn windowing, remembered objects (was `ObjectPlacementManager`) |
| `ObjectManager.SolidContacts` | Riding, landing, ceiling, side collision (was `SolidObjectManager`) |
| `ObjectManager.TouchResponses` | Enemy bounce, hurt, category detection (was `TouchResponseManager`) |
| `ObjectManager.PlaneSwitchers` | Plane switching logic (was `PlaneSwitcherManager`) |

#### Ring System (`RingManager`)
The `RingManager` consolidates ring functionality:
| Inner Class | Purpose |
|-------------|---------|
| `RingManager.RingPlacement` | Collection state, sparkle animation, windowed spawning |
| `RingManager.RingRenderer` | Ring rendering with cached patterns |
| `RingManager.LostRingPool` | Lost ring physics and collection |

#### Per-Sprite Controller (`PlayableSpriteController`)
`AbstractPlayableSprite` owns a `PlayableSpriteController` that coordinates:
| Component | Purpose |
|-----------|---------|
| `PlayableSpriteMovement` | Physics and movement handling |
| `PlayableSpriteAnimation` | Animation state and scripted animations |
| `SpindashDustController` | Spindash dust effects |
| `DrowningController` | Underwater drowning mechanics |

#### Sonic 2 Level Animation (`Sonic2LevelAnimationManager`)
Implements both `AnimatedPatternManager` and `AnimatedPaletteManager`:
- `Sonic2PatternAnimator` - Animated tile scripts
- `Sonic2PaletteCycler` - Zone-specific palette cycling

#### CNZ Bumpers (`CNZBumperManager`)
Combines placement windowing and ROM-accurate bounce physics with type-specific handlers for all 6 bumper types.

#### Collision Pipeline (`CollisionSystem`)
Unified collision orchestration in `uk.co.jamesj999.sonic.physics`:
| Phase | Purpose |
|-------|---------|
| Terrain probes | Ground/ceiling/wall sensors via `TerrainCollisionManager` |
| Solid object resolution | Platforms, moving solids via `ObjectManager.SolidContacts` |
| Post-resolution | Ground mode, headroom checks |

`PlayableSpriteMovement` uses `CollisionSystem.terrainProbes()` for all terrain collision. Supports trace recording for testing via `CollisionTrace` interface.

#### UI Render Pipeline (`UiRenderPipeline`)
Located in `uk.co.jamesj999.sonic.graphics.pipeline`, ensures correct render ordering:
1. **Scene** - Level and sprites (rendered by LevelManager)
2. **Overlay** - HUD via `HudRenderManager`
3. **Fade pass** - Screen transitions via `FadeManager`

`Engine.display()` uses `UiRenderPipeline.updateFade()` and `renderFadePass()` for screen transitions. Includes `RenderOrderRecorder` for testing.

### Terminology (differs from standard Sonic 2 naming)
- **Pattern** - 8x8 pixel tile
- **Chunk** - 16x16 pixel tile (composed of Patterns)
- **Block** - 128x128 pixel area (composed of Chunks)

### Configuration
`SonicConfigurationService` loads from `config.json`. Key settings:
- `DEBUG_VIEW_ENABLED` - Overlays sensor/collision info (default: true)
- `DEBUG_MODE_KEY` - Key to toggle debug movement mode (default: 68 = GLFW_KEY_D). When active, Sonic can fly freely with arrow keys, ignoring collision/physics.
- `AUDIO_ENABLED` - Sound on/off
- `ROM_FILENAME` - ROM path
- `S3K_SKIP_AIZ1_INTRO` - S3K bootstrap flag for skipping the cinematic AIZ1 intro path during current bring-up

## Level Resource Overlay System

Some Sonic 2 zones share level resources with overlays applied to customize the graphics. The most notable example is **Hill Top Zone (HTZ)**, which shares base data with **Emerald Hill Zone (EHZ)** and applies HTZ-specific overlays.

### HTZ/EHZ Resource Composition

From the s2disasm `SonLVL.ini`:
```ini
[Hill Top Zone Act 1/2]
tiles=../art/kosinski/EHZ_HTZ.bin|../art/kosinski/HTZ_Supp.bin:0x3F80
blocks=../mappings/16x16/EHZ.bin|../mappings/16x16/HTZ.bin:0x980
chunks=../mappings/128x128/EHZ_HTZ.bin
colind1=../collision/EHZ and HTZ primary 16x16 collision index.bin
colind2=../collision/EHZ and HTZ secondary 16x16 collision index.bin
```

**What the overlays do:**
- **Patterns (8×8 tiles):** Base EHZ_HTZ data is loaded, then HTZ_Supp replaces tiles starting at byte offset `0x3F80` (tile index 0x01FC). This replaces EHZ palm tree foreground tiles with HTZ fir trees.
- **Blocks (16×16 mappings):** Base EHZ blocks are loaded, then HTZ blocks overwrite starting at byte offset `0x0980` (block index 0x0130).
- **Chunks and Collision:** Fully shared between EHZ and HTZ (no overlay needed).

### ROM Addresses (Rev01)

| Resource | ROM Address | Notes |
|----------|-------------|-------|
| Base patterns (EHZ_HTZ) | `0x095C24` | Kosinski compressed |
| HTZ supplement patterns | `0x098AB4` | Overlay at +0x3F80 bytes |
| Base blocks (EHZ) | `0x094E74` | Kosinski compressed |
| HTZ supplement blocks | `0x0985A4` | Overlay at +0x0980 bytes |
| Shared chunks (EHZ_HTZ) | `0x099D34` | Kosinski compressed |
| Primary collision | `0x044E50` | Shared EHZ/HTZ |
| Secondary collision | `0x044F40` | Shared EHZ/HTZ |

### Implementation

The overlay system is implemented in the `uk.co.jamesj999.sonic.level.resources` package:

| Class | Purpose |
|-------|---------|
| `LoadOp` | Describes a single load operation (ROM address, compression, dest offset) |
| `LevelResourcePlan` | Holds lists of LoadOps for patterns, blocks, chunks, collision |
| `ResourceLoader` | Performs the actual loading with overlay composition |
| `Sonic2LevelResourcePlans` | Factory for zone-specific resource plans |

**Key points:**
- Each `LoadOp` specifies a `destOffsetBytes` (0 for base, non-zero for overlays)
- `ResourceLoader.loadWithOverlays()` allocates a fresh buffer, loads base, then applies overlays
- Overlays never mutate cached data (copy-on-write pattern)
- `Sonic2.loadLevel()` checks for custom plans via `Sonic2LevelResourcePlans.getPlanForZone()`

### Adding Similar Overlay Support for Other Zones

If another zone requires overlay-based loading:

1. Add ROM offset constants to `Sonic2Constants.java`
2. Create a plan factory method in `Sonic2LevelResourcePlans.java`
3. Update `getPlanForZone()` to return the plan for that zone ID
4. Write tests in `LevelResourceOverlayTest.java`

## Multi-Game Support Architecture

The engine supports multiple Sonic games (Sonic 1, Sonic 2, Sonic 3&K) through a provider-based abstraction layer. Game-specific behavior is isolated behind interfaces, allowing the engine core to remain game-agnostic.

### Core Components

| Class/Interface | Purpose |
|-----------------|---------|
| `GameModule` | Central interface defining all game-specific providers |
| `GameModuleRegistry` | Singleton holding the current game module |
| `RomDetectionService` | Auto-detects ROM type and sets appropriate module |
| `RomDetector` | Interface for game-specific ROM detection logic |

### GameModule Interface

The `GameModule` interface is the entry point for all game-specific functionality:

```java
// Access the current game module
GameModule module = GameModuleRegistry.getCurrent();

// Get game-specific providers
ObjectRegistry objects = module.createObjectRegistry();
ZoneRegistry zones = module.getZoneRegistry();
SpecialStageProvider specialStage = module.getSpecialStageProvider();
ScrollHandlerProvider scroll = module.getScrollHandlerProvider();
```

### Provider Interfaces

| Provider | Purpose |
|----------|---------|
| `ZoneRegistry` | Zone/level metadata (names, act counts, start positions) |
| `ObjectRegistry` | Object creation factories and ID mappings |
| `SpecialStageProvider` | Chaos Emerald special stage logic |
| `BonusStageProvider` | Checkpoint bonus stage logic (S3K) |
| `ScrollHandlerProvider` | Per-zone parallax scroll handlers |
| `ZoneFeatureProvider` | Zone-specific mechanics (CNZ bumpers, water) |
| `RomOffsetProvider` | Type-safe ROM address access |
| `LevelEventProvider` | Dynamic camera boundaries, boss arenas |
| `TitleCardProvider` | Zone/act title card rendering |
| `DebugModeProvider` | Game-specific debug features |

### ROM Auto-Detection

The engine automatically detects the loaded ROM and configures the appropriate game module:

```java
// Automatic detection (called during ROM load)
GameModuleRegistry.detectAndSetModule(rom);

// Manual module setting
GameModuleRegistry.setCurrent(new Sonic2GameModule());
```

Detection is performed by `RomDetector` implementations registered with `RomDetectionService`. Each detector examines ROM headers/checksums to identify its game.

## Per-Game Physics Framework

The engine differentiates physics behavior across Sonic 1, Sonic 2, and Sonic 3&K through a layered provider system. Each layer handles a different concern:

### Architecture

| Class | Purpose | Location |
|-------|---------|----------|
| `PhysicsProfile` | Immutable per-character movement constants (18 fields) | `game/PhysicsProfile.java` |
| `PhysicsModifiers` | Water and speed shoes multiplier rules | `game/PhysicsModifiers.java` |
| `PhysicsFeatureSet` | Feature flags gating mechanics per game | `game/PhysicsFeatureSet.java` |
| `CollisionModel` | Enum: collision path architecture (UNIFIED vs DUAL_PATH) | `game/CollisionModel.java` |
| `PhysicsProvider` | Interface tying the above together per game | `game/PhysicsProvider.java` |

Each game module returns its own `PhysicsProvider`:
- `Sonic1PhysicsProvider` → `game/sonic1/`
- `Sonic2PhysicsProvider` → `game/sonic2/`
- `Sonic3kPhysicsProvider` → `game/sonic3k/`

### Resolution Flow

1. `AbstractPlayableSprite` constructor calls `defineSpeeds()` (hardcoded S2 fallback values)
2. Then calls `resolvePhysicsProfile()`, which queries `GameModuleRegistry.getCurrent().getPhysicsProvider()`
3. Profile values overwrite the fallback fields; modifiers and feature set are cached
4. Getters like `getRunAccel()` apply modifiers dynamically (water/speed shoes)
5. Feature set gates are checked at call sites (spindash, collision path switching, etc.)

### PhysicsProfile — Per-Character Constants

All values in subpixels (256 = 1 pixel). Pre-defined profiles:

| Profile | Used By | Key Differences |
|---------|---------|-----------------|
| `SONIC_2_SONIC` | S1 Sonic, S2 Sonic, S3K Sonic/Knuckles | Baseline |
| `SONIC_2_TAILS` | S2 Tails, S3K Tails | Shorter (standYRadius=15, runHeight=30) |
| `SONIC_3K_SUPER_SONIC` | S3K Super Sonic | max=0xA00, accel=0x30, decel=0x100 |

### PhysicsModifiers — Water & Speed Shoes

`PhysicsModifiers.STANDARD` is shared across all three games. Modifiers are applied dynamically via getter methods on `AbstractPlayableSprite`:

```java
// Example: sprite.getRunAccel() internally calls:
physicsModifiers.effectiveAccel(runAccel, isInWater(), speedShoes);
```

Water halves accel/decel/friction/max and overrides jump to 0x380. Speed shoes double accel/friction/max (decel unchanged).

### PhysicsFeatureSet — Per-Game Feature Flags

Controls which mechanics exist in each game. **This is the primary extension point for per-game physics differences.**

Current fields:

| Field | S1 | S2 | S3K | Purpose |
|-------|----|----|-----|---------|
| `spindashEnabled` | `false` | `true` | `true` | Gates `doCheckSpindash()` in `PlayableSpriteMovement` |
| `spindashSpeedTable` | `null` | 9-entry table | 9-entry table | Spindash release speeds (0x800–0xC00) |
| `collisionModel` | `UNIFIED` | `DUAL_PATH` | `DUAL_PATH` | Collision path switching architecture |
| `fixedAnglePosThreshold` | `true` | `false` | `false` | S1: fixed 14px threshold; S2/S3K: speed-dependent |

### Collision Model: UNIFIED vs DUAL_PATH

This models a fundamental architectural difference between Sonic 1 and Sonic 2/3K:

**Sonic 1 (`CollisionModel.UNIFIED`):**
- Single collision index per zone
- Solidity bits are hardcoded per-routine (not per-sprite)
- Floor sensors use bit 0x0C (top solid), wall sensors use bit 0x0D (LRB solid)
- **No dynamic path switching** — bits are locked to their defaults
- Plane switchers and springs cannot change collision layers

**Sonic 2/3K (`CollisionModel.DUAL_PATH`):**
- Dual collision indices: Primary (bits 0x0C/0x0D) and Secondary (bits 0x0E/0x0F)
- Per-sprite `top_solid_bit` and `lrb_solid_bit` stored in SST (s2.constants.asm:70-71)
- Plane switchers and springs dynamically swap sprites between collision paths
- Initialized in Obj01_Init: `top_solid_bit = $C`, `lrb_solid_bit = $D`

**Implementation:** The setters `setTopSolidBit()`/`setLrbSolidBit()` on `AbstractPlayableSprite` are guarded — they silently ignore calls when the feature set has `CollisionModel.UNIFIED`:

```java
public void setTopSolidBit(byte topSolidBit) {
    if (physicsFeatureSet != null && !physicsFeatureSet.hasDualCollisionPaths()) {
        return;  // S1: bits are locked
    }
    this.topSolidBit = topSolidBit;
}
```

This means `SpringHelper.applyCollisionLayerBits()` and `ObjectManager.PlaneSwitchers` automatically become no-ops for Sonic 1 without any changes to those classes.

### How Feature Gates Work

The pattern for checking feature flags at call sites:

```java
// In PlayableSpriteMovement.doCheckSpindash():
PhysicsFeatureSet featureSet = sprite.getPhysicsFeatureSet();
if (featureSet != null && !featureSet.spindashEnabled()) {
    return false;  // Skip entirely for S1
}

// In PlayableSpriteMovement AnglePos threshold:
if (featureSet != null && featureSet.fixedAnglePosThreshold()) {
    positiveThreshold = 14;           // S1: fixed (cmpi.w #$E,d1)
} else {
    positiveThreshold = Math.min(speedPixels + 4, 14);  // S2/S3K: speed-dependent
}
```

Null-safe: when no `GameModule` is loaded, feature set is null and the code falls through to the S2 default behavior.

### Adding a New Per-Game Physics Difference

When you discover a behavior that differs between games (from comparing disassemblies), follow this pattern:

1. **Identify the difference in the disassembly.** Note the exact ROM lines, register values, and which games differ. Reference specific assembly labels (e.g., `s2.asm:37294`).

2. **Add a field to `PhysicsFeatureSet`** if it's a feature flag or behavioral toggle:
   ```java
   public record PhysicsFeatureSet(
       boolean spindashEnabled,
       short[] spindashSpeedTable,
       CollisionModel collisionModel,
       boolean fixedAnglePosThreshold,
       boolean newDifference           // <-- add here
   ) {
       public static final PhysicsFeatureSet SONIC_1 = new PhysicsFeatureSet(
           false, null, CollisionModel.UNIFIED, true, true);    // <-- S1 value
       public static final PhysicsFeatureSet SONIC_2 = new PhysicsFeatureSet(true, new short[]{
           ...}, CollisionModel.DUAL_PATH, false, false);       // <-- S2 value
       // ... update SONIC_3K too
   }
   ```

   Or add to `PhysicsProfile` if it's a per-character constant (like speed or radius values).

   Or add to `PhysicsModifiers` if it's a modifier rule (water/shoes multipliers).

3. **Gate the behavior at the call site** in `PlayableSpriteMovement`, `AbstractPlayableSprite`, or `GroundSensor`:
   ```java
   PhysicsFeatureSet fs = sprite.getPhysicsFeatureSet();
   if (fs != null && fs.newDifference()) {
       // S1 behavior
   } else {
       // S2/S3K behavior (also the fallback when no module loaded)
   }
   ```

4. **Add tests** following the `TestSpindashGating`/`TestCollisionModel` pattern:
   - Create a `TestableSprite` inner class extending `AbstractPlayableSprite`
   - Set the game module via `GameModuleRegistry.setCurrent(new Sonic1GameModule())`
   - Assert the feature set value and the resulting behavior

**Key rules:**
- Always verify the difference against the disassembly. Pixel-perfect accuracy is paramount.
- The S2 behavior should always be the fallback (when `physicsFeatureSet` is null)
- Never add game-specific `if/else` chains by game name — always use feature flags
- Keep `PhysicsFeatureSet` focused on behavioral toggles, not constants (those go in `PhysicsProfile`)
- Document the disassembly reference in the field's Javadoc and in this CLAUDE.md section

### Test Pattern

Tests live in `src/test/java/uk/co/jamesj999/sonic/game/`:

| Test Class | Covers |
|------------|--------|
| `TestPhysicsProfile` | Profile constants match disassembly, modifier math |
| `TestPhysicsProfileRegression` | Getter values match original hardcoded S2 values |
| `TestSpindashGating` | S1 disables spindash, S2 enables it, module switching |
| `TestCollisionModel` | Collision model enum, setter guarding, spring/path switching |

All use a `TestableSprite` inner class pattern — no ROM or OpenGL required.

## Sonic 3&K Bring-up Notes

These are critical architecture constraints for current S3K support:

- Layout decoding:
  - `Sonic3kLevel.loadMap(...)` must parse FG/BG row pointers as interleaved pairs per row, not as two contiguous pointer tables.
  - FG pointer word: `header + row * 4`
  - BG pointer word: `header + 2 + row * 4`
  - Getting this wrong causes broken terrain rendering and invalid collision surfaces.

- AIZ1 intro skip bridge:
  - `Sonic3k.loadLevel(...)` can bootstrap AIZ1 to gameplay-after-intro by loading intro entry overlays for `art2` and `blocks2`.
  - `getLevelBoundariesAddr(...)` must use `LEVEL_SIZES_AIZ1_INTRO_INDEX` (`26`) when that bootstrap is active, so vertical bounds match post-intro gameplay expectations.
  - This is an intentional temporary parity bridge until full intro event scripting is implemented.

- Camera bounds timing:
  - In `LevelManager.loadCurrentLevel(...)`, camera placement must be refreshed *after* assigning level min/max bounds (`camera.updatePosition(true)`).
  - This prevents bad pit/death evaluation on high-Y starts.

- Collision table decoding:
  - Keep S3K collision pointer decoding through `Sonic3k.decodeCollisionPointer(...)` marker logic.
  - Keep stride-2 collision index reads in `Sonic3kLevel.readCollisionIndex(...)` for chunk lookup parity.

- Known limitation:
  - Some S3K levels still log `maxChunkPatternIndex > patternCount`, which indicates dynamic art/PLC parity is still incomplete.

- Keep these S3K regression tests green:
  - `uk.co.jamesj999.sonic.tests.TestS3kAiz1SpawnStability`
  - `uk.co.jamesj999.sonic.tests.TestSonic3kLevelLoading`
  - `uk.co.jamesj999.sonic.game.sonic3k.TestSonic3kBootstrapResolver`
  - `uk.co.jamesj999.sonic.game.sonic3k.TestSonic3kDecodingUtils`

## Object & Badnik System

Game objects (springs, monitors, badniks, platforms) use a factory pattern with game-specific registries.

### Object Registration

```java
// ObjectRegistry interface
ObjectInstance create(ObjectSpawn spawn);
String getPrimaryName(int objectId);

// ObjectFactory functional interface
ObjectInstance create(ObjectSpawn spawn, ObjectRegistry registry);
```

Objects are registered in `Sonic2ObjectRegistry.registerDefaultFactories()`:

```java
registerFactory(Sonic2ObjectIds.SPRING,
    (spawn, registry) -> new SpringObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
```

### Key Object Classes

| Class | Purpose |
|-------|---------|
| `Sonic2ObjectRegistry` | Factory registry for Sonic 2 objects |
| `Sonic2ObjectRegistryData` | Static name mappings for object IDs |
| `AbstractObjectInstance` | Base class for all game objects |
| `PlaceholderObjectInstance` | Fallback for unimplemented objects |

### Badnik System

Badniks (enemies) extend `AbstractBadnikInstance` which provides:
- Common collision handling via `TouchResponseProvider`
- Destruction behavior (explosion, animal spawn, points)
- Movement/animation framework

```java
public abstract class AbstractBadnikInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable {

    // Subclasses implement AI logic
    protected abstract void updateMovement(int frameCounter, AbstractPlayableSprite player);

    // Collision size for touch response
    protected abstract int getCollisionSizeIndex();
}
```

Example badniks: `MasherBadnikInstance`, `BuzzerBadnikInstance`, `CoconutsBadnikInstance`

### Adding New Objects

1. Add the object ID to `Sonic2ObjectIds.java`
2. Create an instance class extending `AbstractObjectInstance` (or `AbstractBadnikInstance` for enemies)
3. Register the factory in `Sonic2ObjectRegistry.registerDefaultFactories()`
4. Add collision data to `Sonic2ObjectConstants.java` if needed

## Game-Specific Art Loading Pattern

**Important:** Keep `ObjectArtData` game-agnostic. Game-specific sprite sheets (badniks, zone-specific objects) should be loaded through the game-specific provider, not added to `ObjectArtData`.

### Architecture

| Class | Purpose |
|-------|---------|
| `ObjectArtData` | Game-agnostic art data (monitors, springs, spikes, etc.) |
| `Sonic2ObjectArt` | Sonic 2-specific art loader with public loader methods |
| `Sonic2ObjectArtProvider` | Registers sheets and provides key-based access |
| `Sonic2ObjectArtKeys` | String keys for Sonic 2-specific art |

### Adding Game-Specific Art (e.g., new Badnik)

1. **Add ROM address** to `Sonic2Constants.java`:
   ```java
   public static final int ART_NEM_NEWBADNIK_ADDR = 0x89B9A;
   ```

2. **Add art key** to `Sonic2ObjectArtKeys.java`:
   ```java
   public static final String NEW_BADNIK = "newbadnik";
   ```

3. **Add loader method** to `Sonic2ObjectArt.java`:
   ```java
   public ObjectSpriteSheet loadNewBadnikSheet() {
       Pattern[] patterns = safeLoadNemesisPatterns(
           Sonic2Constants.ART_NEM_NEWBADNIK_ADDR, "NewBadnik");
       if (patterns.length == 0) {
           return null;
       }
       List<SpriteMappingFrame> mappings = createNewBadnikMappings();
       return new ObjectSpriteSheet(patterns, mappings, paletteIndex, 1);
   }
   ```

4. **Add mappings method** to `Sonic2ObjectArt.java`:
   ```java
   private List<SpriteMappingFrame> createNewBadnikMappings() {
       List<SpriteMappingFrame> frames = new ArrayList<>();
       // Add SpriteMappingPiece for each frame
       return frames;
   }
   ```

5. **Register in provider** `Sonic2ObjectArtProvider.loadArtForZone()`:
   ```java
   registerSheet(Sonic2ObjectArtKeys.NEW_BADNIK, artLoader.loadNewBadnikSheet());
   ```

### DO NOT add to ObjectArtData

The following should NOT be added to `ObjectArtData`:
- Badnik/enemy sprites (Masher, Buzzer, ChopChop, etc.)
- Zone-specific object sprites
- Game-specific decorative elements

These should use the loader method pattern above, keeping `ObjectArtData` focused on common objects that could be shared across game implementations.

## Constants Files

Game-specific constants are organized in the `game.sonic2.constants` package:

| File | Contents |
|------|----------|
| `Sonic2Constants.java` | Primary ROM offsets (level data, palettes, collision) |
| `Sonic2ObjectIds.java` | Object type IDs (0x41 = Spring, 0x26 = Monitor, etc.) |
| `Sonic2ObjectConstants.java` | Touch collision table address and size data |
| `Sonic2AnimationIds.java` | Animation script IDs for player sprites |
| `Sonic2AudioConstants.java` | Music and SFX IDs |

## Adding New Game Support

To add support for a new game (e.g., Sonic 1):

1. **Create the GameModule implementation**
   ```java
   public class Sonic1GameModule implements GameModule {
       // Implement all provider methods
   }
   ```

2. **Create a RomDetector**
   ```java
   public class Sonic1RomDetector implements RomDetector {
       public boolean canHandle(Rom rom) {
           // Check ROM header for "SONIC THE HEDGEHOG"
       }
       public GameModule createModule() {
           return new Sonic1GameModule();
       }
   }
   ```

3. **Implement required providers**
   - `ZoneRegistry` - Zone names, act counts, start positions
   - `ObjectRegistry` - Object factories for game-specific objects
   - Audio profile with correct SFX/music mappings

4. **Register the detector**
   Add to `RomDetectionService.registerBuiltInDetectors()`:
   ```java
   Class<?> sonic1DetectorClass = Class.forName(
       "uk.co.jamesj999.sonic.game.sonic1.Sonic1RomDetector");
   ```

Optional providers can return `null` if the game doesn't use that feature (e.g., `getBonusStageProvider()` for Sonic 2).

## Audio Engine

The audio system emulates the Mega Drive sound hardware:
- **YM2612** - FM synthesis chip (6 channels)
- **PSG (SN76489)** - Square wave + noise (4 channels)
- **SMPS Driver** - Sega's sound driver format

Reference implementations in `docs/SMPS-rips/SMPSPlay/libs/download/libvgm/emu/cores/` contain high-accuracy source code.

**Important:** Strive for hardware accuracy. Reference libvgm cores and SMPSPlay source rather than implementing simplified versions.

## Branch Naming Convention

- `feature/ai-*` - New features
- `bugfix/ai-*` - Bug fixes

## Headless Testing

The `HeadlessTestRunner` utility enables physics and collision integration tests without an OpenGL context.

### Usage

```java
HeadlessTestRunner runner = new HeadlessTestRunner(sprite);
runner.stepFrame(up, down, left, right, jump);  // Simulate one frame with input
runner.stepIdleFrames(5);                        // Step multiple frames with no input
```

### Required Setup

Tests using `HeadlessTestRunner` must follow this setup order:

```java
@Before
public void setUp() {
    // 1. Reset singletons (may have stale state from other tests)
    GraphicsManager.resetInstance();
    Camera.resetInstance();

    // 2. Load ROM and initialize headless graphics
    rom = new Rom();
    rom.open(romFile.getAbsolutePath());
    GameModuleRegistry.detectAndSetModule(rom);
    GraphicsManager.getInstance().initHeadless();

    // 3. Create sprite and add to SpriteManager
    sprite = new Sonic(mainCode, startX, startY);
    SpriteManager.getInstance().addSprite(sprite);
    Camera.getInstance().setFocusedSprite(sprite);

    // 4. Load level
    LevelManager.getInstance().loadZoneAndAct(zone, act);

    // 5. Fix static references (critical for test isolation)
    GroundSensor.setLevelManager(LevelManager.getInstance());

    // 6. Update camera position AFTER level loads (bounds are set during load)
    Camera.getInstance().updatePosition(true);

    // 7. Create test runner
    testRunner = new HeadlessTestRunner(sprite);
}
```

**Key pitfalls:**
- `GroundSensor` has a static `levelManager` field initialized at class load time. Must call `setLevelManager()` after loading a new level.
- `Camera.updatePosition(true)` must be called AFTER level load, as level bounds are set during `loadCurrentLevel()`.
- Failing to reset `Camera` can leave `frozen=true` from death sequences in other tests.

### Example Test

See `TestHeadlessWallCollision.java` for a complete example that verifies ground collision and walking physics.

## Code Style Notes

- Keep logic in manager classes, not in `Engine.java`
- Ensure source files end with a newline
- Uses Java 21 features

## Coordinate System & Rendering

### Player Sprite Coordinates

**Critical:** The original Sonic 2 ROM uses **center coordinates** for player position (`x_pos`, `y_pos`), not top-left corner. When implementing object interactions or collision checks:

| Method | Returns | Use Case |
|--------|---------|----------|
| `player.getX()` / `player.getY()` | Top-left corner of sprite bounding box | Rendering, bounding box calculations |
| `player.getCentreX()` / `player.getCentreY()` | Center of sprite (matches ROM `x_pos`/`y_pos`) | **Object interactions, collision checks** |

**Always use `getCentreX()`/`getCentreY()` for object interactions** to match original ROM behavior. Using `getX()`/`getY()` creates a vertical offset of ~19 pixels (half player height), causing incorrect collision detection (e.g., triggering checkpoints when running through loops beneath them).

Example from disassembly (`s2.asm`):
```assembly
move.w  x_pos(a3),d0        ; player CENTER X
sub.w   x_pos(a0),d0        ; object X
; ... collision check uses center-to-center delta
```

### Y-Axis Convention
The engine uses Mega Drive/Genesis screen coordinates internally where **Y increases downward** (Y=0 at top of screen). OpenGL uses the opposite convention (Y=0 at bottom), so the `BatchedPatternRenderer` flips the Y coordinate during rendering:
```java
int screenY = screenHeight - y;  // BatchedPatternRenderer.java:94
```

When working with screen positions, always use the Mega Drive convention (Y down = positive). The graphics layer handles the OpenGL conversion automatically.

### Sprite Tile Ordering
Mega Drive VDP sprites use **column-major** tile ordering, not row-major. For a sprite piece that is W tiles wide and H tiles tall:
```java
// Column-major: tiles go top-to-bottom within each column, then left-to-right
int tileIndex = column * heightTiles + row;
```

When a sprite is horizontally flipped, the VDP draws tiles from the **last column first**. When vertically flipped, it draws from the **bottom row first**. The flip flags also flip each individual tile's pixels.

Example for a 3x2 tile sprite (width=3, height=2):
```
Normal order:     H-Flipped order:
[0][2][4]         [4][2][0]
[1][3][5]         [5][3][1]
```

### VDP Sprite Coordinate Offset (Disassembly Only)

When reading sprite coordinates from the Sonic 2 disassembly, be aware that the VDP hardware adds 128 to both X and Y coordinates. This allows sprites to be positioned partially off-screen (a sprite at VDP X=0 is 128 pixels left of the visible area).

**This only matters when interpreting raw values from the disassembly.** Our Java engine uses direct screen coordinates (0,0 = top-left visible pixel).

Example from `s2.asm`:
```asm
; The results_screen_object macro adds 128 automatically:
results_screen_object macro startx, targetx, y, routine, frame
    dc.w    128+startx, 128+targetx, 128+y
    ...

; But direct VDP writes don't:
move.w  #$B4,y_pixel(a1)  ; $B4 = 180 in VDP space = 52 in screen space
```

To convert: **screen_position = vdp_value - 128**

## Intentional Divergences from Original ROM

While the engine strives for pixel-perfect accuracy, some implementation details differ from the original hardware. These are documented in **[docs/KNOWN_DISCREPANCIES.md](docs/KNOWN_DISCREPANCIES.md)**, including:

- **Gloop Sound Toggle** - Moved from Z80 driver to `BlueBallsObjectInstance`
- **Spindash Release Transpose Fix** - Patches invalid FM transpose value
- **Pattern ID Ranges** - GUI/Results use extended IDs (0x20000+) instead of overwriting VRAM tiles

## Sonic 2 Special Stage Implementation

The special stage uses a unique pseudo-3D rendering system. Key files are in `uk.co.jamesj999.sonic.game.sonic2.specialstage`.

### Key Classes

| Class | Purpose |
|-------|---------|
| `Sonic2SpecialStageManager` | Main manager, coordinates all special stage systems |
| `Sonic2SpecialStageManager.Sonic2SpecialStageObjectManager` | Nested class for object spawning/collection |
| `Sonic2SpecialStageDataLoader` | Loads and decompresses data from ROM |
| `Sonic2TrackAnimator` | Manages segment sequencing and animation timing |
| `Sonic2TrackFrameDecoder` | Decodes track frame bitstream into VDP tiles |
| `Sonic2SpecialStageConstants` | ROM offsets and segment type constants |

### Track Frame Format

Each of the 56 track frames is a compressed bitstream with 3 segments:
1. **Bitflags** - 1 bit per tile: 0 = RLE (fill), 1 = UNC (unique)
2. **UNC LUT** - Lookup table for unique tiles (variable length)
3. **RLE LUT** - Lookup table for fill tiles (variable length)

The decoder reads the bitflags to determine which LUT to use for each tile. Only UNC tiles get their `flip_x` bit (0x0800) toggled when the track is flipped.

The VDP plane is 128 cells wide, displayed as 4 strips of 32 tiles via H-scroll interleaving. When flipping, tiles are reversed within each 32-tile strip, not the entire 128-tile row.

### Segment Types and Animations

| Type | Name | Frames | Description |
|------|------|--------|-------------|
| 0 | TURN_THEN_RISE | 24 | Turn (0x26-0x2B) + Rise (0x00-0x10) |
| 1 | TURN_THEN_DROP | 24 | Turn (0x26-0x2B) + Drop (0x15-0x25) |
| 2 | TURN_THEN_STRAIGHT | 12 | Turn (0x26-0x2B) + Exit curve (0x2C-0x30) |
| 3 | STRAIGHT | 16 | Straight frames (0x11-0x14) repeated 4x |
| 4 | STRAIGHT_THEN_TURN | 11 | Straight (0x11-0x14) + Enter curve (0x31-0x37) |

### Layout Data Format

The layout is Nemesis compressed. Decompressed format:
- **Offset table**: 7 words (14 bytes) - one offset per stage
- **Layout bytes**: Each byte defines a segment
  - Bits 0-6: Segment type (0-4)
  - Bit 7 (0x80): Flip flag (left turn vs right turn)

The layout does NOT loop - the stage ends at checkpoint 3 (4th checkpoint) before reaching the end. Checkpoints are defined in separate object location data, not in the layout.

### Orientation/Flip System

The original game maintains a persistent `SSTrack_Orientation` state that only updates at specific **trigger frames**:
- **0x12** (Straight frame 2)
- **0x0E** (Rise frame 14)
- **0x1A** (Drop frame 6)

At trigger frames, orientation is set to the **current segment's** flip bit. Between triggers, orientation persists unchanged.

This means:
- **STRAIGHT_THEN_TURN**: Flip flag is on THIS segment. Entry curve frames use current segment's flip.
- **TURN_THEN_STRAIGHT**: Uses PREVIOUS segment's flip (continues the turn).
- **TURN_THEN_RISE/DROP**: Orientation updates at rise frame 14 or drop frame 6.

### Stage Progression

- 4 checkpoints per stage (acts 0-3)
- At checkpoint 3, `SS_Check_Rings_flag` triggers stage end
- Ring requirements are checked at each checkpoint
- Stage ends with emerald award or failure before layout runs out
