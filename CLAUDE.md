# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Java-based Sonic the Hedgehog game engine that faithfully recreates the original Mega Drive/Genesis physics. It loads game data from original ROMs (Sonic 1, 2, and 3&K) and aims for pixel-perfect gameplay recreation.

**Critical requirement:** The engine must replicate original physics pixel-for-pixel. Accuracy is paramount. Always verify against the disassembly.

## Build & Run Commands

```bash
mvn package                          # Build (creates executable JAR with dependencies)
mvn test                             # Run tests
mvn test -Dtest=TestCollisionLogic   # Run a single test class
java -jar target/sonic-engine-0.3.20260206-jar-with-dependencies.jar  # Run (requires ROM)
```

## ROM Requirement

Keep ROMs in the working directory (gitignored):
- `Sonic The Hedgehog (W) (REV01) [!].gen`
- `Sonic The Hedgehog 2 (W) (REV01) [!].gen`
- `Sonic and Knuckles & Sonic 3 (W) [!].gen`

Sonic 2 ROM download: `http://bluetoaster.net/secretfolder/Sonic%20The%20Hedgehog%202%20%28W%29%20%28REV01%29%20%5B!%5D.gen`

For S3K tests: `-Ds3k.rom.path="Sonic and Knuckles & Sonic 3 (W) [!].gen"`. `TestRomLogic` is skipped when ROM is absent.

## Reference Materials

- **`docs/s2disasm/`** - Sonic 2 disassembly (68000 assembly). Essential for accuracy verification.
- **`docs/skdisasm/`** - Sonic 3&K disassembly. Primary reference for S3K level layout and collision.
- **`docs/s1disasm/`** - Sonic 1 disassembly.
- **`docs/SMPS-rips/SMPSPlay/`** - SMPS audio driver source and reference implementations

These directories are untracked but available locally.

## ROM Offset Finder Tool

Use **RomOffsetFinder** to search disassembly items and find ROM offsets. Supports S1, S2, S3K.

```bash
# Base command pattern (add --game s1 or --game s3k before command for non-S2)
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="<command>" -q

# Examples
# search <pattern>     - Search for items by label/filename
# verify <label>       - Verify calculated offset against ROM
# list [type]          - List all includes (optionally by type: nem/kos/kosm/eni/sax/bin)
# test <offset> <type> - Test decompression at offset
# export <type> [prefix] - Export verified offsets as Java constants
# verify-batch [type]  - Batch verify all/filtered items
# find <label> [offset] - Find ROM offset by decompression search
# search-rom <hex> [start] [end] - Search ROM for hex byte pattern (inline data, pointer tables, etc.)
```

Game selection: `--game s1`, `--game s2` (default), or `--game s3k`. Auto-detects from disasm path if not specified.

**S3K note:** Compression type is encoded in label suffix (e.g., `AIZ1_8x8_Primary_KosM`) rather than file extension. The tool auto-infers from label.

See `uk.co.jamesj999.sonic.tools.disasm` package for programmatic API.

## Architecture

### Entry Point
`uk.co.jamesj999.sonic.Engine` - GLFW window with manual timing loop (`display()` -> `update()` -> `draw()`).

### Core Managers (Singleton Pattern via `getInstance()`)
- **LevelManager** - Level loading, rendering, zone/act management
- **SpriteManager** - All game sprites, input handling, render bucketing
- **GraphicsManager** - OpenGL rendering, shader management
- **AudioManager** - SMPS audio driver, YM2612/PSG synthesis
- **Camera** - Camera position tracking

### Core Services Facade
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
| `game.sonic2.objects` | Object factories, instance classes, badnik AI |
| `game.sonic2.constants` | ROM offsets, object IDs, audio constants |
| `game.sonic3k` | Sonic 3&K game module, level loading, bootstrap |
| `tools` | Compression utilities, disassembly tools |

### Consolidated Subsystems

**ObjectManager** inner classes: `Placement` (spawn windowing), `SolidContacts` (riding/landing/ceiling/side collision), `TouchResponses` (enemy bounce/hurt), `PlaneSwitchers` (plane switching logic).

**RingManager** inner classes: `RingPlacement` (collection state, sparkle, spawning), `RingRenderer` (cached pattern rendering), `LostRingPool` (lost ring physics).

**PlayableSpriteController** coordinates: `PlayableSpriteMovement` (physics), `PlayableSpriteAnimation` (animation state), `SpindashDustController`, `DrowningController`.

**CollisionSystem** (`uk.co.jamesj999.sonic.physics`) - Unified collision orchestration: terrain probes via `TerrainCollisionManager`, solid object resolution via `ObjectManager.SolidContacts`, post-resolution ground mode/headroom checks. Supports trace recording via `CollisionTrace`.

**UiRenderPipeline** (`uk.co.jamesj999.sonic.graphics.pipeline`) - Render ordering: Scene -> HUD overlay -> Fade pass. `Engine.display()` uses it for screen transitions.

**Sonic2LevelAnimationManager** - Implements `AnimatedPatternManager` and `AnimatedPaletteManager` (pattern animation scripts + zone-specific palette cycling).

**CNZBumperManager** - Placement windowing and ROM-accurate bounce physics for all 6 bumper types.

### Terminology (differs from standard Sonic 2 naming)
- **Pattern** = 8x8 pixel tile
- **Chunk** = 16x16 pixel tile (composed of Patterns)
- **Block** = 128x128 pixel area (composed of Chunks)

### Configuration
`SonicConfigurationService` loads from `config.json`: `DEBUG_VIEW_ENABLED`, `DEBUG_MODE_KEY` (68 = GLFW_KEY_D for free-fly debug mode), `AUDIO_ENABLED`, `ROM_FILENAME`, `S3K_SKIP_AIZ1_INTRO`.

## Level Resource Overlay System

Some zones share level resources with overlays (e.g., HTZ shares base data with EHZ, then applies HTZ-specific pattern/block overlays). Implemented in `uk.co.jamesj999.sonic.level.resources`:

- `LoadOp` - Single load operation (ROM address, compression, dest offset)
- `LevelResourcePlan` - Lists of LoadOps for patterns, blocks, chunks, collision
- `ResourceLoader` - Loading with overlay composition (copy-on-write)
- `Sonic2LevelResourcePlans` - Factory for zone-specific resource plans

To add overlay support for other zones: add ROM offsets to `Sonic2Constants`, create a plan in `Sonic2LevelResourcePlans`, update `getPlanForZone()`.

## Multi-Game Support Architecture

Game-specific behavior is isolated behind the `GameModule` interface. `GameModuleRegistry` holds the current module, `RomDetectionService` auto-detects ROM type.

Key providers returned by `GameModule`: `ZoneRegistry`, `ObjectRegistry`, `ScrollHandlerProvider`, `ZoneFeatureProvider`, `RomOffsetProvider`, `LevelEventProvider` (returns game-specific `AbstractLevelEventManager` subclass), `PhysicsProvider`, `SpecialStageProvider`, `BonusStageProvider`, `TitleCardProvider`, `DebugModeProvider`.

Each game has its own module (`Sonic1GameModule`, `Sonic2GameModule`, `Sonic3kGameModule`) and `RomDetector`.

## Unified Level Event Framework

Level events (boss arena setup, dynamic boundaries, zone transitions) are managed through a shared base class with game-specific subclasses:

- **`AbstractLevelEventManager`** (`game/`) - Shared state machine mechanics: `eventRoutine` counter, zone/act tracking, `initLevel()`/`update()` lifecycle, boss spawn coordination.
- **`Sonic1LevelEventManager`** (`game/sonic1/events/`) - S1 zone event handlers. Per-zone handler classes.
- **`Sonic2LevelEventManager`** (`game/sonic2/`) - S2 zone event handlers (HTZ earthquake, boss arenas, EHZ/CPZ/ARZ/CNZ events).
- **`Sonic3kLevelEventManager`** (`game/sonic3k/`) - S3K zone event handlers (zone handlers pending implementation).
- **`PlayerCharacter`** enum (`game/`) - Character identity enum (`SONIC`, `TAILS`, `KNUCKLES`) for character-specific branching in event logic.

Each `GameModule` returns its game-specific subclass via `LevelEventProvider`. Call sites use `AbstractLevelEventManager` for polymorphic access.

## Per-Game Physics Framework

Physics differences across S1/S2/S3K are handled through a layered provider system:

| Class | Purpose |
|-------|---------|
| `PhysicsProfile` | Immutable per-character movement constants (18 fields, values in subpixels where 256=1px) |
| `PhysicsModifiers` | Water/speed shoes multiplier rules (shared `STANDARD` across all games) |
| `PhysicsFeatureSet` | Feature flags gating mechanics per game (primary extension point) |
| `CollisionModel` | Enum: `UNIFIED` (S1) vs `DUAL_PATH` (S2/S3K) |
| `PhysicsProvider` | Interface tying above together, per game module |

### Resolution Flow
1. `AbstractPlayableSprite` constructor calls `defineSpeeds()` (S2 fallback values)
2. Then `resolvePhysicsProfile()` queries `GameModuleRegistry.getCurrent().getPhysicsProvider()`
3. Profile values overwrite fallbacks; modifiers and feature set are cached
4. Getters apply modifiers dynamically (water/speed shoes)
5. Feature set gates checked at call sites

### PhysicsFeatureSet Fields

| Field | S1 | S2 | S3K | Purpose |
|-------|----|----|-----|---------|
| `spindashEnabled` | `false` | `true` | `true` | Gates spindash |
| `spindashSpeedTable` | `null` | 9-entry | 9-entry | Release speeds |
| `collisionModel` | `UNIFIED` | `DUAL_PATH` | `DUAL_PATH` | Collision path architecture |
| `fixedAnglePosThreshold` | `true` | `false` | `false` | S1: fixed 14px; S2/S3K: speed-dependent |
| `angleDiffCardinalSnap` | `false` | `true` | `true` | S2/S3K: snap to cardinal when sensor angle diff >= 0x20 |

### Collision Model: UNIFIED vs DUAL_PATH

**S1 (UNIFIED):** Single collision index, solidity bits hardcoded per-routine, no dynamic path switching.

**S2/S3K (DUAL_PATH):** Dual collision indices (Primary bits 0x0C/0x0D, Secondary bits 0x0E/0x0F), per-sprite `top_solid_bit`/`lrb_solid_bit`, plane switchers dynamically swap collision paths.

The setters `setTopSolidBit()`/`setLrbSolidBit()` on `AbstractPlayableSprite` silently ignore calls when `CollisionModel.UNIFIED`, making springs and plane switchers automatic no-ops for S1.

### Adding Per-Game Physics Differences

1. Identify difference in disassembly with exact ROM references
2. Add field to `PhysicsFeatureSet` (behavioral toggle), `PhysicsProfile` (per-character constant), or `PhysicsModifiers` (multiplier rule)
3. Gate behavior at call site - S2 behavior is always the fallback when `physicsFeatureSet` is null
4. Add tests following `TestSpindashGating`/`TestCollisionModel` pattern (TestableSprite inner class, no ROM/OpenGL required)

**Rules:** Always verify against disassembly. Never use game-name `if/else` chains - always use feature flags.

### Physics Tests

Tests in `src/test/java/uk/co/jamesj999/sonic/game/`: `TestPhysicsProfile`, `TestPhysicsProfileRegression`, `TestSpindashGating`, `TestCollisionModel`.

## Object & Badnik System

Objects use a factory pattern with game-specific registries. `ObjectRegistry` creates `ObjectInstance` from `ObjectSpawn`. Factories registered in `Sonic2ObjectRegistry.registerDefaultFactories()`.

Badniks extend `AbstractBadnikInstance` (which provides touch response collision, destruction behavior, movement/animation framework). Subclasses implement `updateMovement()` and `getCollisionSizeIndex()`.

To add objects: add ID to `Sonic2ObjectIds`, create instance class, register factory in `Sonic2ObjectRegistry`.

### Game-Specific Art Loading

**Keep `ObjectArtData` game-agnostic.** Game-specific sprites (badniks, zone objects) go through `Sonic2ObjectArt` (loader methods) -> `Sonic2ObjectArtProvider` (registration/access) -> `Sonic2ObjectArtKeys` (string keys).

Pattern: add ROM address to `Sonic2Constants`, add key to `Sonic2ObjectArtKeys`, add loader method in `Sonic2ObjectArt`, register in `Sonic2ObjectArtProvider.loadArtForZone()`.

### Constants Files (`game.sonic2.constants`)

`Sonic2Constants` (ROM offsets), `Sonic2ObjectIds` (object type IDs), `Sonic2ObjectConstants` (touch collision data), `Sonic2AnimationIds` (animation scripts), `Sonic2AudioConstants` (music/SFX IDs).

## Sonic 3&K Bring-up Notes

Critical constraints for current S3K support:

- **Dual object pointer tables (zone-set system):** S3K uses two object pointer tables that remap many IDs by zone. `S3kZoneSet` enum: `S3KL` (zones 0-6: AIZ-LBZ) and `SKL` (zones 7-13: MHZ-DDZ). `Sonic3kObjectRegistry.getPrimaryName(id, zoneSet)` resolves zone-set-aware names. `Sonic3kObjectProfile` uses per-level resolution for names, badnik IDs, and boss IDs via `GameObjectProfile` default methods. The `ObjectDiscoveryTool` uses composite keys (`"objectId:name"`) so same-ID-different-name objects get separate checklist entries.
- **Layout decoding:** `Sonic3kLevel.loadMap()` parses FG/BG row pointers as interleaved pairs (`FG: header + row*4`, `BG: header + 2 + row*4`), NOT contiguous tables.
- **AIZ1 intro skip:** `Sonic3k.loadLevel()` can bootstrap to post-intro gameplay. `getLevelBoundariesAddr()` must use `LEVEL_SIZES_AIZ1_INTRO_INDEX` (26) when active.
- **Camera bounds timing:** Camera placement must be refreshed AFTER assigning level bounds (`camera.updatePosition(true)`).
- **Collision decoding:** Keep `Sonic3k.decodeCollisionPointer()` marker logic and stride-2 reads in `Sonic3kLevel.readCollisionIndex()`.
- **Known limitation:** Some S3K levels log `maxChunkPatternIndex > patternCount` (dynamic art/PLC parity incomplete).

**Keep these S3K tests green:**
- `uk.co.jamesj999.sonic.tests.TestS3kAiz1SpawnStability`
- `uk.co.jamesj999.sonic.tests.TestSonic3kLevelLoading`
- `uk.co.jamesj999.sonic.game.sonic3k.TestSonic3kBootstrapResolver`
- `uk.co.jamesj999.sonic.game.sonic3k.TestSonic3kDecodingUtils`

## Audio Engine

Emulates Mega Drive sound hardware: **YM2612** (FM synthesis, 6 channels), **PSG/SN76489** (square wave + noise, 4 channels), **SMPS Driver** (Sega's sound format).

Reference implementations: `docs/SMPS-rips/SMPSPlay/libs/download/libvgm/emu/cores/`. Strive for hardware accuracy - reference libvgm cores rather than simplified versions.

## Headless Testing

`HeadlessTestRunner` enables physics/collision integration tests without OpenGL.

```java
HeadlessTestRunner runner = new HeadlessTestRunner(sprite);
runner.stepFrame(up, down, left, right, jump);
runner.stepIdleFrames(5);
```

**Setup order is critical** - see `TestHeadlessWallCollision.java` for a complete example. Key pitfalls:
- Reset singletons first (`GraphicsManager.resetInstance()`, `Camera.resetInstance()`)
- Call `GroundSensor.setLevelManager()` AFTER loading a level (static field)
- Call `Camera.updatePosition(true)` AFTER level load (bounds set during load)
- Failing to reset Camera can leave `frozen=true` from death sequences

## Coordinate System & Rendering

### Player Sprite Coordinates

**Critical:** The ROM uses **center coordinates** for player position. Always use `getCentreX()`/`getCentreY()` for object interactions, NOT `getX()`/`getY()` (which return top-left corner for rendering). Using top-left creates a ~19px vertical offset causing incorrect collision detection.

### Y-Axis Convention
Engine uses Mega Drive convention: **Y increases downward** (Y=0 at top). `BatchedPatternRenderer` flips to OpenGL convention automatically.

### Sprite Tile Ordering
VDP sprites use **column-major** ordering: `tileIndex = column * heightTiles + row`. H-flip draws from last column first; V-flip from bottom row first.

### VDP Coordinate Offset (Disassembly Only)
VDP hardware adds 128 to X/Y. Convert: `screen_position = vdp_value - 128`. Our engine uses direct screen coordinates.

## Intentional Divergences

Documented in **[docs/KNOWN_DISCREPANCIES.md](docs/KNOWN_DISCREPANCIES.md)**: Gloop sound toggle, spindash release transpose fix, pattern ID ranges (GUI/Results use 0x20000+ IDs).

## Special Stage Implementation

Key files in `uk.co.jamesj999.sonic.game.sonic2.specialstage`: `Sonic2SpecialStageManager` (main coordinator), `Sonic2TrackAnimator` (segment sequencing), `Sonic2TrackFrameDecoder` (bitstream decoder), `Sonic2SpecialStageDataLoader`, `Sonic2SpecialStageConstants`.

**Track frame format:** Each of 56 frames is a compressed bitstream: bitflags (1 bit/tile: 0=RLE fill, 1=UNC unique), UNC LUT, RLE LUT. Only UNC tiles get `flip_x` (0x0800) toggled on flip. VDP plane is 128 cells wide as 4x32 strips - flipping reverses within each strip.

**Segment types:** 0=TURN_THEN_RISE, 1=TURN_THEN_DROP, 2=TURN_THEN_STRAIGHT, 3=STRAIGHT, 4=STRAIGHT_THEN_TURN.

**Orientation system:** Persistent `SSTrack_Orientation` updates only at trigger frames (0x12, 0x0E, 0x1A). Between triggers, orientation persists from previous update.

**Stage progression:** 4 checkpoints per stage (acts 0-3). Stage ends at checkpoint 3 with ring requirement check. Layout does NOT loop.

## Code Style

- Keep logic in manager classes, not in `Engine.java`
- Source files end with newline
- Java 21 features
- Branch naming: `feature/ai-*`, `bugfix/ai-*`
