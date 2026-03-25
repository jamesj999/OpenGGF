# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

OpenGGF is an open-source, Java-based game engine for research and preservation of classic Mega Drive / Genesis platform games, specifically the mainline Sonic the Hedgehog series. It faithfully reimplements the physics and rendering behaviour of the original hardware using data loaded from user-supplied ROM images (Sonic 1, 2, and 3&K). No copyrighted assets are included in this repository.

**Critical requirement:** The engine must replicate original physics pixel-for-pixel. Accuracy is paramount. Always verify against the disassembly.

## Build & Run Commands

```bash
mvn package                          # Build (creates executable JAR with dependencies)
mvn test                             # Run tests
mvn test -Dtest=TestCollisionLogic   # Run a single test class
java -jar target/sonic-engine-0.4.prerelease-jar-with-dependencies.jar  # Run (requires ROM)
```

Maven Silent Extension (MSE) is configured in this repo via `.mvn/extensions.xml`, and `.mvn/maven.config` enables `-Dmse=relaxed` by default for repo-local Maven commands. Use `-Dmse=off` when full Maven logs are needed.

## ROM Requirement

Keep ROMs in the working directory (gitignored):
- `Sonic The Hedgehog (W) (REV01) [!].gen`
- `Sonic The Hedgehog 2 (W) (REV01) [!].gen`
- `Sonic and Knuckles & Sonic 3 (W) [!].gen`

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
mvn exec:java "-Dexec.mainClass=com.openggf.tools.disasm.RomOffsetFinder" "-Dexec.args=<command>" -q

# Examples
# search <pattern>     - Search for items by label/filename
# verify <label>       - Verify calculated offset against ROM
# list [type]          - List all includes (optionally by type: nem/kos/kosm/eni/sax/bin)
# test <offset> <type> - Test decompression at offset
# export <type> [prefix] - Export verified offsets as Java constants
# verify-batch [type]  - Batch verify all/filtered items
# find <label> [offset] - Find ROM offset by decompression search
# search-rom <hex> [start] [end] - Search ROM for hex byte pattern (inline data, pointer tables, etc.)
# plc <name>           - Show PLC definition contents and list art entries
```

Game selection: `--game s1`, `--game s2` (default), or `--game s3k`. Auto-detects from disasm path if not specified.

**S3K note:** Compression type is encoded in label suffix (e.g., `AIZ1_8x8_Primary_KosM`) rather than file extension. The tool auto-infers from label.

**PLC cross-referencing:** Search results for art labels automatically show which PLCs reference that art. Use `plc <name>` to display all art entries within a specific PLC definition.

See `com.openggf.tools.disasm` package for programmatic API.

## Architecture

### Entry Point
`com.openggf.Engine` - GLFW window with manual timing loop (`display()` -> `update()` -> `draw()`).

### Two-Tier Service Architecture

The engine uses a **two-tier service model** that separates global access from per-object context:

**Tier 1: `GameServices` (static facade)** — Global access for managers and non-object code:
```java
GameServices.camera()       // Camera
GameServices.level()        // LevelManager
GameServices.gameState()    // GameStateManager - score, lives, emeralds
GameServices.audio()        // AudioManager - SMPS driver
GameServices.timers()       // TimerManager - event timing
GameServices.rom()          // RomManager - ROM data access
GameServices.sprites()      // SpriteManager
GameServices.fade()         // FadeManager - screen transitions
GameServices.collision()    // CollisionSystem
GameServices.parallax()     // ParallaxManager
GameServices.water()        // WaterSystem
GameServices.debugOverlay() // DebugOverlayManager
```

**Tier 2: `ObjectServices` (injected per-object)** — Context-specific services for game objects:
```java
// Inside any AbstractObjectInstance subclass:
services().objectManager()        // ObjectManager
services().renderManager()        // ObjectRenderManager
services().audioManager()         // AudioManager
services().camera()               // Camera
services().gameState()            // GameStateManager
services().zoneFeatureProvider()  // ZoneFeatureProvider
```

Objects receive `ObjectServices` via injection at construction time (ThreadLocal context set by `ObjectManager`). **Never call `getInstance()` from object code** — use `services()` instead. `GameServices` is for non-object code (managers, event handlers, controllers).

### GameRuntime

`GameRuntime` (`com.openggf.game`) is the explicit runtime object that will own all mutable gameplay state. `RuntimeManager` manages its lifecycle. This is the foundation for safe editor mode enter/exit, level rebuilds, and undo/redo. Currently `GameRuntime` coordinates singleton `resetState()` calls; the long-term goal is for it to own the state directly.

### Core Managers
- **LevelManager** - Thin coordinator after decomposition (see below)
- **SpriteManager** - All game sprites, input handling, render bucketing
- **GraphicsManager** - OpenGL rendering, shader management
- **AudioManager** - SMPS audio driver, YM2612/PSG synthesis
- **Camera** - Camera position tracking

### LevelManager Decomposition

`LevelManager` has been decomposed into focused subsystems:

| Class | Responsibility |
|-------|----------------|
| `LevelManager` | Thin coordinator, level load orchestration |
| `LevelTilemapManager` | Tilemap loading, chunk/block management, VRAM upload |
| `LevelTransitionCoordinator` | Act transitions, seamless loading, warp sequences |
| `LevelDebugRenderer` | All debug overlay rendering (collision, chunks, paths) |
| `LevelGeometry` *(record)* | Immutable level dimension/boundary data |
| `LevelDebugContext` *(record)* | Snapshot of debug state for rendering |

### MutableLevel

`MutableLevel` (`com.openggf.level`) provides snapshot + mutation + dirty-region tracking for level tile data. Foundation for the planned level editor. Uses `Block.saveState()/restoreState()` for undo/redo. Dirty regions are processed per-frame via `LevelFrameStep.processDirtyRegions()`.

### Key Packages
| Package | Purpose |
|---------|---------|
| `sprites.playable` | Sonic/Tails player logic, physics; `PlayableEntity` interface |
| `physics` | Terrain collision, sensors |
| `level` | Level structures, rendering, scrolling, `MutableLevel`, `AbstractLevel` |
| `level.objects` | Game object management, `ObjectServices`, shared base classes, utility helpers |
| `level.scroll` | `AbstractZoneScrollHandler` and per-zone scroll handlers |
| `audio` | SMPS driver, YM2612/PSG chip emulation, `AbstractAudioProfile`, `AbstractSmpsLoader` |
| `data` | ROM loading/reading (`Rom`, `RomManager`, `RomByteReader`), `Game`/`GameFactory`, art provider interfaces |
| `game` | Core game-agnostic interfaces, providers, `GameServices`, `GameRuntime`, `PlayableEntity`, `DamageCause` |
| `game.sonic2` | Sonic 2-specific implementations |
| `game.sonic2.objects` | Object factories, instance classes, badnik AI |
| `game.sonic2.constants` | ROM offsets, object IDs, audio constants |
| `game.sonic1` | Sonic 1 game module, level loading, loop/switch managers, zone features |
| `game.sonic3k` | Sonic 3&K game module, level loading, bootstrap |
| `graphics` | OpenGL rendering, shaders, pattern atlas, tilemap GPU renderer, FBO management |
| `tools` | Compression utilities (Kosinski, Nemesis, Saxman), `ObjectDiscoveryTool`, disassembly tools |

### Consolidated Subsystems

**ObjectManager** inner classes: `Placement` (spawn windowing), `SolidContacts` (riding/landing/ceiling/side collision), `TouchResponses` (enemy bounce/hurt), `PlaneSwitchers` (plane switching logic). Injects `ObjectServices` into all objects at construction time.

**RingManager** inner classes: `RingPlacement` (collection state, sparkle, spawning), `RingRenderer` (cached pattern rendering), `LostRingPool` (lost ring physics).

**PlayableSpriteController** coordinates: `PlayableSpriteMovement` (physics), `PlayableSpriteAnimation` (animation state), `SpindashDustController`, `DrowningController`.

**CollisionSystem** (`com.openggf.physics`) - Unified collision orchestration: terrain probes via `TerrainCollisionManager`, solid object resolution via `ObjectManager.SolidContacts`, post-resolution ground mode/headroom checks. Supports trace recording via `CollisionTrace`.

**UiRenderPipeline** (`com.openggf.graphics.pipeline`) - Render ordering: Scene -> HUD overlay -> Fade pass. `Engine.display()` uses it for screen transitions.

**Sonic2LevelAnimationManager** - Implements `AnimatedPatternManager` and `AnimatedPaletteManager` (pattern animation scripts + zone-specific palette cycling).

**CNZBumperManager** - Placement windowing and ROM-accurate bounce physics for all 6 bumper types.

### Terminology (differs from standard Sonic 2 naming)
- **Pattern** = 8x8 pixel tile
- **Chunk** = 16x16 pixel tile (composed of Patterns)
- **Block** = 128x128 pixel area (composed of Chunks)

### Configuration
`SonicConfigurationService` loads from `config.json`: `DEBUG_VIEW_ENABLED`, `DEBUG_MODE_KEY` (68 = GLFW_KEY_D for free-fly debug mode), `AUDIO_ENABLED`, `SONIC_1_ROM`, `SONIC_2_ROM`, `SONIC_3K_ROM`, `DEFAULT_ROM`, `S3K_SKIP_INTROS`.

## Level Resource Overlay System

Some zones share level resources with overlays (e.g., HTZ shares base data with EHZ, then applies HTZ-specific pattern/block overlays). Implemented in `com.openggf.level.resources`:

- `LoadOp` - Single load operation (ROM address, compression, dest offset)
- `LevelResourcePlan` - Lists of LoadOps for patterns, blocks, chunks, collision
- `ResourceLoader` - Loading with overlay composition (copy-on-write)
- `Sonic2LevelResourcePlans` - Factory for zone-specific resource plans

To add overlay support for other zones: add ROM offsets to `Sonic2Constants`, create a plan in `Sonic2LevelResourcePlans`, update `getPlanForZone()`.

- **PLC system:** `PlcParser` in `level.resources` provides game-agnostic PLC parsing. See `plc-system` skill for cross-game reference, `s3k-plc-system` for S3K-specific details.

## Multi-Game Support Architecture

Game-specific behavior is isolated behind the `GameModule` interface. `GameModuleRegistry` holds the current module, `RomDetectionService` auto-detects ROM type.

Key providers returned by `GameModule`: `ZoneRegistry`, `ObjectRegistry`, `GameAudioProfile`, `TouchResponseTable`, `PlaneSwitcherConfig`, `ScrollHandlerProvider`, `ZoneFeatureProvider`, `RomOffsetProvider`, `LevelEventProvider` (returns game-specific `AbstractLevelEventManager` subclass), `PhysicsProvider`, `SpecialStageProvider`, `BonusStageProvider`, `TitleCardProvider`, `TitleScreenProvider`, `LevelSelectProvider`, `DebugModeProvider`, `DebugOverlayProvider`, `ZoneArtProvider`, `ObjectArtProvider`, `RespawnState`, `LevelState`, `SuperStateController`.

Each game has its own module (`Sonic1GameModule`, `Sonic2GameModule`, `Sonic3kGameModule`) and `RomDetector`.

## Unified Level Event Framework

Level events (boss arena setup, dynamic boundaries, zone transitions) are managed through a shared base class with game-specific subclasses:

- **`AbstractLevelEventManager`** (`game/`) - Shared state machine mechanics: dual routine counters (`eventRoutineFg` and `eventRoutineBg`; S1/S2 only use Fg, S3K uses both), zone/act tracking, `initLevel()`/`update()` lifecycle, boss spawn coordination.
- **`Sonic1LevelEventManager`** (`game/sonic1/events/`) - S1 zone event handlers. Per-zone handler classes.
- **`Sonic2LevelEventManager`** (`game/sonic2/`) - S2 zone event handlers (HTZ earthquake, boss arenas, EHZ/CPZ/ARZ/CNZ events).
- **`Sonic3kLevelEventManager`** (`game/sonic3k/`) - S3K zone event handlers (zone handlers pending implementation).
- **`PlayerCharacter`** enum (`game/`) - Character identity enum (`SONIC_AND_TAILS`, `SONIC_ALONE`, `TAILS_ALONE`, `KNUCKLES`) matching ROM's `Player_mode` variable for character-specific branching in event logic.

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
| `lookScrollDelay` | `0` (none) | `0x78` (120f) | `0x78` (120f) | Delay before camera pans on look up/down |
| `waterShimmerEnabled` | `true` | `false` | `false` | S1 underwater palette shimmer effect |
| `inputAlwaysCapsGroundSpeed` | `true` | `false` | `false` | S1: input always caps ground speed; S2/S3K: preserves high speed |
| `elementalShieldsEnabled` | `false` | `false` | `true` | S3K fire/lightning/bubble shield mechanics |
| `angleDiffCardinalSnap` | `false` | `true` | `true` | S2/S3K: snap to cardinal when sensor angle diff >= 0x20 |
| `extendedEdgeBalance` | `false` | `true` | `true` | S2/S3K: 4 balance states, precarious check; S1: single state, force face edge |

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

Tests in `src/test/java/com/openggf/game/`: `TestPhysicsProfile`, `TestPhysicsProfileRegression`, `TestSpindashGating`, `TestCollisionModel`.

## Object & Badnik System

Objects use a factory pattern with game-specific registries. `ObjectRegistry` creates `ObjectInstance` from `ObjectSpawn`. Factories registered via `AbstractObjectRegistry` subclasses (`Sonic1ObjectRegistry`, `Sonic2ObjectRegistry`, `Sonic3kObjectRegistry`).

**Service injection:** All objects receive `ObjectServices` at construction via `ObjectManager`. Inside any object, call `services()` to access camera, audio, level, game state, and zone features. **Never use `getInstance()` in object code.**

**Child spawning:** Use `spawnChild(() -> new ChildObject(spawn, params))` instead of manually calling `ObjectManager.addDynamicObject()`.

Badniks extend `AbstractBadnikInstance` (`com.openggf.level.objects` — game-agnostic) which provides touch response collision, destruction behavior via `DestructionEffects`, and movement/animation framework. Subclasses implement `updateMovement()` and `getCollisionSizeIndex()`.

### Reusable Object Utilities

**Before implementing any object, check these utilities. Do NOT reimplement existing functionality.** The implement-object skills (S1/S2/S3K) have full details in their section 2.4.

| Utility | Package | Purpose |
|---------|---------|---------|
| `SubpixelMotion` | `level.objects` | 16:8 fixed-point position updates (moveSprite, moveSprite2, moveX) |
| `PatrolMovementHelper` | `level.objects` | Left-right patrol with edge detection |
| `PlatformBobHelper` | `level.objects` | Sine-based standing-nudge for platforms |
| `SpringBounceHelper` | `level.objects` | Shared spring bounce physics |
| `DestructionEffects` | `level.objects` | Badnik explosion + animal + points |
| `AnimationTimer` | `util` | Cyclic frame animation timer |
| `LazyMappingHolder` | `util` | Lazy-loading sprite mapping holder |
| `PatternDecompressor` | `util` | Bytes→Pattern[] conversion |
| `FboHelper` | `util` | FBO creation/destruction + viewport |

**Base classes** (in `level.objects`): `AbstractBadnikInstance`, `AbstractProjectileInstance`, `AbstractSpikeObjectInstance`, `AbstractMonitorObjectInstance`, `AbstractPointsObjectInstance`, `GravityDebrisChild`.

**Inherited from `AbstractObjectInstance`**: `getRenderer(artKey)`, `buildSpawnAt(x, y)`, `isPlayerRiding()`, `isOnScreen(margin)`.

To add objects: add ID to `Sonic2ObjectIds`, create instance class, register factory in `Sonic2ObjectRegistry`.

### Game-Specific Art Loading

**Keep `ObjectArtData` game-agnostic.** Game-specific sprites (badniks, zone objects) go through `Sonic2ObjectArt` (loader methods) -> `Sonic2ObjectArtProvider` (registration/access) -> `Sonic2ObjectArtKeys` (string keys).

Pattern: add ROM address to `Sonic2Constants`, add key to `Sonic2ObjectArtKeys`, add loader method in `Sonic2ObjectArt`, register in `Sonic2ObjectArtProvider.loadArtForZone()`.

**S2 object art:** Prefer `S2SpriteDataLoader.loadMappingFrames(reader, mappingAddr)` to parse S2 mappings from ROM at runtime. Add mapping ROM address to `Sonic2Constants.java`, then call the shared utility from `Sonic2ObjectArt`. Object instance files should use `S2SpriteDataLoader` directly instead of inline parser copies. The `loadMappingFramesWithTileOffset()` variant supports VRAM tile index adjustment.

**S1 object art:** Use `Sonic1ObjectArt.buildArtSheet(artAddr, mappings, palette, bankSize)` for Nemesis-compressed art with mappings. Use `S1SpriteDataLoader.loadMappingFrames(reader, mappingAddr)` for ROM-parsed S1 mappings (5-byte pieces, byte piece count). Note: most S1 object mappings are inline `spritePiece` macros in the assembly (not separate binary tables), so `buildArtSheetFromRom()` is available but many objects still use hardcoded mappings with `buildArtSheet()`.

**S3K level-art objects:** Prefer `Sonic3kObjectArt.buildLevelArtSheetFromRom(mappingAddr, artTileBase, palette)` to parse S3K mappings from ROM at runtime. Add mapping ROM address to `Sonic3kConstants.java` (use RomOffsetFinder). Extract art_tile base and palette from the object code's `make_art_tile()` call. Only hardcode mapping pieces when the ROM table can't be used directly.

### Constants Files (`game.sonic2.constants`)

`Sonic2Constants` (ROM offsets), `Sonic2ObjectIds` (object type IDs), `Sonic2ObjectConstants` (touch collision data), `Sonic2AnimationIds` (animation scripts), `Sonic2AudioConstants` (music/SFX IDs).

## Sonic 3&K Bring-up Notes

Critical constraints for current S3K support:

- **Dual object pointer tables (zone-set system):** S3K uses two object pointer tables that remap many IDs by zone. `S3kZoneSet` enum: `S3KL` (zones 0-6: AIZ-LBZ) and `SKL` (zones 7-13: MHZ-DDZ). `Sonic3kObjectRegistry.getPrimaryName(id, zoneSet)` resolves zone-set-aware names. `Sonic3kObjectProfile` uses per-level resolution for names, badnik IDs, and boss IDs via `GameObjectProfile` default methods. The `ObjectDiscoveryTool` uses composite keys (`"objectId:name"`) so same-ID-different-name objects get separate checklist entries.
- **Layout decoding:** `Sonic3kLevel.loadMap()` parses FG/BG row pointers as interleaved pairs (`FG: header + row*4`, `BG: header + 2 + row*4`), NOT contiguous tables.
- **AIZ1 intro skip:** `Sonic3k.loadLevel()` can bootstrap to post-intro gameplay. `getLevelBoundariesAddr()` must use `LEVEL_SIZES_AIZ1_INTRO_INDEX` (26) when active.
- **Camera bounds timing:** Camera placement must be refreshed AFTER assigning level bounds (`camera.updatePosition(true)`).
- **Collision decoding:** Keep `Sonic3k.decodeCollisionPointer()` marker logic and stride-2 reads in `Sonic3kLevel.readCollisionIndex()`.
- **PLC system:** See `s3k-plc-system` skill for Pattern Load Cue system docs (runtime art loading, act transitions, boss art).
- **Known limitation:** Some S3K levels log `maxChunkPatternIndex > patternCount` (dynamic art/PLC parity incomplete).
- **S3K-specific details:** See [AGENTS_S3K.md](AGENTS_S3K.md) for palette animation, zone intricacies, and implementation patterns.

**Keep these S3K tests green:**
- `com.openggf.tests.TestS3kAiz1SkipHeadless`
- `com.openggf.tests.TestSonic3kLevelLoading`
- `com.openggf.game.sonic3k.TestSonic3kBootstrapResolver`
- `com.openggf.game.sonic3k.TestSonic3kDecodingUtils`

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

**Preferred test setup:** Use `@ExtendWith(SingletonResetExtension.class)` or `@FullReset` annotation for automated singleton teardown between tests. The extension calls `resetState()` on all singletons.

**Manual setup (legacy)** - see `TestHeadlessWallCollision.java` for a complete example. Key pitfalls:
- Reset singletons first using `resetState()` (NOT the deprecated `resetInstance()`)
- Call `GroundSensor.setLevelManager()` AFTER loading a level (static field)
- Call `Camera.updatePosition(true)` AFTER level load (bounds set during load)
- Failing to reset Camera can leave `frozen=true` from death sequences

**Test infrastructure classes:**
- `SingletonResetExtension` — JUnit 5 extension for automated singleton teardown
- `@FullReset` — Annotation triggering full engine reset
- `StubObjectServices` — Test double for `ObjectServices`
- `TestObjectServicesMigrationGuard` — Scanner-based guard preventing singleton regression in objects
- `TestNoServicesInObjectConstructors` — Ensures objects don't call `services()` during construction

## Coordinate System & Rendering

### Player Sprite Coordinates

**Critical:** The ROM uses **center coordinates** for player position. Always use `getCentreX()`/`getCentreY()` for object interactions, NOT `getX()`/`getY()` (which return top-left corner for rendering). Using top-left creates a ~19px vertical offset causing incorrect collision detection.

### Y-Axis Convention
Engine uses Mega Drive convention: **Y increases downward** (Y=0 at top). `BatchedPatternRenderer` flips to OpenGL convention automatically.

### Sprite Tile Ordering
VDP sprites use **column-major** ordering: `tileIndex = column * heightTiles + row`. H-flip draws from last column first; V-flip from bottom row first.

### VDP Coordinate Offset (Disassembly Only)
VDP hardware adds 128 to X/Y. Convert: `screen_position = vdp_value - 128`. Our engine uses direct screen coordinates.

## Virtual Pattern ID System

The Mega Drive VDP uses 11-bit pattern indices (0x000–0x7FF, 2048 tiles). The engine extends this with a **virtual pattern ID** space so multiple subsystems can cache patterns without colliding. The `PatternAtlas` uses a tiered lookup: flat array (`fastEntries[8192]`) for dense low IDs (level tiles), `HashMap<Integer, Entry>` for sparse high IDs.

| Range | Category | Notes |
|-------|----------|-------|
| `0x00000` | Level tiles | Corresponds to VDP VRAM tile indices |
| `0x01000` | Special Stage | Track, objects, HUD |
| `0x10000` | Results Screen | End-of-act results |
| `0x20000` | Objects | Monitors, badniks, zone objects (`OBJECT_PATTERN_BASE`) |
| `0x28000` | HUD | Score, time, rings (`HUD_PATTERN_BASE`) |
| `0x30000` | Water surface | Underwater palette transition |
| `0x38000+` | Sidekick DPLC banks | Duplicate-character body sprites (`SIDEKICK_PATTERN_BASE`) |
| `0x39000+` | Sidekick tail appendages | Duplicate Tails Obj05 sprites |
| `0x40000` | Title Card | Zone/act title card |

**Key classes:**
- `PatternAtlas` — stores all patterns keyed by virtual ID; tiered flat+sparse lookup
- `DynamicPatternBank` — fixed-size bank for DPLC-driven updates (player sprites, objects)
- `PlayerSpriteRenderer` — renders player sprites using `renderPatternWithId()` to bypass the 11-bit VDP limit in `PatternDesc`
- `GraphicsManager.renderPatternWithId(patternId, desc, x, y)` — explicit pattern ID for atlas lookup, used when IDs exceed 0x7FF

When adding new pattern categories, choose a base that doesn't overlap existing ranges. See **[docs/KNOWN_DISCREPANCIES.md](docs/KNOWN_DISCREPANCIES.md)** for the full range table.

## Intentional Divergences

Documented in **[docs/KNOWN_DISCREPANCIES.md](docs/KNOWN_DISCREPANCIES.md)**: Gloop sound toggle, spindash release transpose fix, pattern ID ranges, HTZ cloud scroll fix, MCZ child cleanup, multi-sidekick system.

## Special Stage Implementation

Key files in `com.openggf.game.sonic2.specialstage`: `Sonic2SpecialStageManager` (main coordinator), `Sonic2TrackAnimator` (segment sequencing), `Sonic2TrackFrameDecoder` (bitstream decoder), `Sonic2SpecialStageDataLoader`, `Sonic2SpecialStageConstants`.

**Track frame format:** Each of 56 frames is a compressed bitstream: bitflags (1 bit/tile: 0=RLE fill, 1=UNC unique), UNC LUT, RLE LUT. Only UNC tiles get `flip_x` (0x0800) toggled on flip. VDP plane is 128 cells wide as 4x32 strips - flipping reverses within each strip.

**Segment types:** 0=TURN_THEN_RISE, 1=TURN_THEN_DROP, 2=TURN_THEN_STRAIGHT, 3=STRAIGHT, 4=STRAIGHT_THEN_TURN.

**Orientation system:** Persistent `SSTrack_Orientation` updates only at trigger frames (0x12, 0x0E, 0x1A). Between triggers, orientation persists from previous update.

**Stage progression:** 4 checkpoints per stage (acts 0-3). Stage ends at checkpoint 3 with ring requirement check. Layout does NOT loop.

## Code Style

- Keep logic in manager classes, not in `Engine.java`
- Source files end with newline
- Java 21 features
- Branch naming: `feature/ai-*`, `bugfix/ai-*`
