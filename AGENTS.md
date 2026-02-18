# Guidance for future AI agents

## Project Mission
This project is a faithful recreation of the **Sonic the Hedgehog** game engine in Java. It aims to:
1.  Use the original ROM data to render levels.
2.  Perfectly and precisely replicate the original physics. (This is IMPORTANT. The engine must recreate the original pixel-for-pixel)
3.  Eventually support user-made characters and level editing tools.

## Current Status
The project is in an **alpha** state. Core systems are functional with 291 passing tests. Recent consolidation work has unified several subsystems (ObjectManager, RingManager, SpriteManager, per-sprite controllers) to reduce complexity while maintaining ROM accuracy.

### Rendering
*   **Status:** ✅ Functional.
*   **Level Data:** Patterns, chunks, and blocks load correctly from ROM via Kosinski decompression.
*   **Flipping Logic:** Horizontal/vertical flip implemented in `PatternRenderCommand`, `BatchedPatternRenderer`, and `SpritePieceRenderer`.
*   **Sprite Rendering:** Player sprites render with DPLC-based animation via `PlayerSpriteRenderer` and `SpritePieceRenderer`.
*   **Supported Zones:** EHZ, CPZ, ARZ have zone-specific scroll handlers.

### Decompression
*   **Status:** ✅ Complete.
*   All four formats implemented with tests: Nemesis, Kosinski, Enigma, Saxman.

### Camera
*   **Status:** ✅ ROM-accurate (core logic).
*   Deadzone following, spindash lag buffer.
*   **Per-zone parallax:** Incomplete – EHZ, CPZ, ARZ implemented; other zones need scroll handlers.

### Physics
*   **Status:** ⚠️ Functional (needs validation).
*   Ground speed model, slope handling, 360° sensor array (A-F), loop ground modes, springs.
*   **TODO:** Validate against original ROM behavior for accuracy.

### Audio
*   **Status:** ✅ Implemented (parity testing ongoing).
*   YM2612 FM synthesis, SN76489 PSG, SMPS driver/sequencer.
*   Reference: `docs/MUSIC_IMPLEMENTATION.md`, `docs/AudioParityPlan.md`.

### Suggested Tasks
1.  **Audio parity testing** – Compare output against SMPSPlay reference.
2.  **Additional zone support** – Implement scroll handlers for remaining zones.
3.  **Game objects** – Enemies, bosses, zone-specific gimmicks.
4.  **Title screen / menus** – Implement game flow beyond level play.
5.  **Special Stage completion** – Finish special stage mechanics and results screen.

## Agent Directives
1.  **Branching:** Always create pull requests from the same branch within a session. Use the following naming convention:
    *   `feature/ai-` for new features.
    *   `bugfix/ai-` for bug fixes.
2.  **Code Structure:** Keep logic within existing or new manager classes. Avoid putting all logic into `Engine.java` to maintain a strong object-oriented design.

## Key information
*   **Entry point:** `uk.co.jamesj999.sonic.Engine` (declared in the manifest). A `main` method creates a GLFW window with a manual timing game loop.
*   **Build:** `mvn package`. Tests can be run with `mvn test` (JUnit 4).
*   **Run:** `java -jar target/sonic-engine-0.05-BETA-jar-with-dependencies.jar`.
*   **ROM Requirement:** The engine now supports Sonic 1, Sonic 2, and Sonic 3&K modules. Keep the relevant ROM in the project root (typically gitignored): `Sonic The Hedgehog 2 (W) (REV01) [!].gen`, `Sonic The Hedgehog (W) (REV01) [!].gen`, and `Sonic and Knuckles & Sonic 3 (W) [!].gen`. S3K-focused tests should pass `-Ds3k.rom.path="Sonic and Knuckles & Sonic 3 (W) [!].gen"` when needed.
*   **Important packages** under `src/main/java/uk/co/jamesj999/sonic`:
    *   `Control` – input handling
    *   `camera` – camera logic
    *   `configuration` – game settings via `SonicConfiguration` and `SonicConfigurationService`
    *   `data` – ROM loaders and game classes
    *   `debug` – debug overlay (`DebugRenderer`), enabled via the `DEBUG_VIEW_ENABLED` configuration flag
    *   `game` – core game-agnostic interfaces, providers, and `GameServices` façade
    *   `game.sonic2` – Sonic 2-specific implementations
    *   `game.sonic2.objects` – object factories and instance classes
    *   `game.sonic2.objects.badniks` – badnik AI implementations
    *   `game.sonic2.constants` – ROM offsets, object IDs, audio constants
    *   `game.sonic3k` – Sonic 3&K game module, level loading, and bootstrap logic
    *   `game.sonic3k.constants` – Sonic 3&K ROM offsets and table metadata
    *   `graphics` – GL wrappers and render managers
    *   `level` – level structures (patterns, blocks, chunks, collision)
    *   `level.objects` – unified `ObjectManager` with placement, collision, touch response
    *   `level.rings` – unified `RingManager` with placement, rendering, lost rings
    *   `level.bumpers` – unified `CNZBumperManager` for Casino Night Zone
    *   `physics` – sensors, terrain collision, and unified `CollisionSystem`
    *   `sprites` – sprite classes, including playable character logic
    *   `sprites.playable` – `PlayableSpriteController` coordinates movement, animation, drowning
    *   `timer` – utility timers for events
    *   `tools` – utilities such as `KosinskiReader` for decompressing Sega data
*   **Tests:** Live under `src/test/java/uk/co/jamesj999/sonic/tests` and cover ROM loading, decompression, and collision.

## Headless Testing with HeadlessTestRunner

The `HeadlessTestRunner` utility (`uk.co.jamesj999.sonic.tests.HeadlessTestRunner`) enables physics and collision integration tests without an OpenGL context.

### Usage
```java
HeadlessTestRunner runner = new HeadlessTestRunner(sprite);
runner.stepFrame(up, down, left, right, jump);  // Simulate one frame
runner.stepIdleFrames(5);                        // Step multiple idle frames
```

### Critical Setup Requirements
1. **Reset singletons:** `GraphicsManager.resetInstance()`, `Camera.resetInstance()`
2. **Initialize headless graphics:** `GraphicsManager.getInstance().initHeadless()`
3. **Create and register playable sprite first:** add the main sprite to `SpriteManager` and set camera focus before `loadZoneAndAct(...)` (required by current `LevelManager` load path)
4. **Load level:** `LevelManager.getInstance().loadZoneAndAct(zone, act)`
5. **Fix GroundSensor:** `GroundSensor.setLevelManager(LevelManager.getInstance())` (static field becomes stale between tests)
6. **Update camera:** `Camera.getInstance().updatePosition(true)` AFTER level load (bounds set during load)

See `TestHeadlessWallCollision.java` for a complete example.

## Core Services Façade

Access core singletons through `GameServices` instead of direct `getInstance()` calls:
```java
GameServices.gameState()    // GameStateManager - score, lives, emeralds
GameServices.timers()       // TimerManager - event timing
GameServices.rom()          // RomManager - ROM data access
GameServices.debugOverlay() // DebugOverlayManager - debug rendering
```

## Consolidated Subsystems

Several manager classes have been consolidated to reduce complexity:

### Object System (`ObjectManager`)
Contains all object-related functionality as inner classes:
- `ObjectManager.Placement` – Spawn windowing, remembered objects
- `ObjectManager.SolidContacts` – Riding, landing, ceiling, side collision
- `ObjectManager.TouchResponses` – Enemy bounce, hurt, category detection
- `ObjectManager.PlaneSwitchers` – Plane switching logic

### Ring System (`RingManager`)
- `RingPlacement` – Collection state, sparkle animation, windowed spawning
- `RingRenderer` – Ring rendering with cached patterns
- `LostRingPool` – Lost ring physics and collection

### Per-Sprite Controller (`PlayableSpriteController`)
Owned by `AbstractPlayableSprite`, coordinates:
- `PlayableSpriteMovement` – Physics and movement
- `PlayableSpriteAnimation` – Animation state
- `SpindashDustController` – Spindash dust effects
- `DrowningController` – Underwater mechanics

### Sonic 2 Level Animation (`Sonic2LevelAnimationManager`)
Implements both `AnimatedPatternManager` and `AnimatedPaletteManager` via:
- `Sonic2PatternAnimator` – Animated tile scripts
- `Sonic2PaletteCycler` – Zone-specific palette cycling

### CNZ Bumpers (`CNZBumperManager`)
Combines placement windowing and ROM-accurate bounce physics with type-specific handlers.

### Unified Collision Pipeline (`CollisionSystem`)
Orchestrates terrain and solid object collision in defined phases:
1. **Terrain probes** – Ground/ceiling/wall sensors via `TerrainCollisionManager`
2. **Solid object resolution** – Platforms, moving solids via `ObjectManager.SolidContacts`
3. **Post-resolution adjustments** – Ground mode, headroom checks

`PlayableSpriteMovement` uses `CollisionSystem.terrainProbes()` for all terrain collision.

Supports trace recording for testing via `CollisionTrace` interface:
- `RecordingCollisionTrace` – Records events for comparison
- `NoOpCollisionTrace` – Production no-op (default)

### Unified UI Render Pipeline (`UiRenderPipeline`)
Located in `graphics.pipeline`, ensures correct render ordering:
1. **Scene** – Level and sprites (external)
2. **Overlay** – HUD via `HudRenderManager`
3. **Fade pass** – Screen transitions via `FadeManager`

`Engine.display()` uses `UiRenderPipeline.updateFade()` and `renderFadePass()` for screen transitions.

Includes `RenderOrderRecorder` for testing render order compliance.

## Multi-Game Support Architecture

The engine supports multiple Sonic games (Sonic 1, Sonic 2, Sonic 3&K) through a provider-based abstraction layer.

### Core Components
| Class/Interface | Purpose |
|-----------------|---------|
| `GameModule` | Central interface defining all game-specific providers |
| `GameModuleRegistry` | Singleton holding the current game module |
| `RomDetectionService` | Auto-detects ROM type and sets appropriate module |
| `RomDetector` | Interface for game-specific ROM detection logic |

### Key Providers
| Provider | Purpose |
|----------|---------|
| `ZoneRegistry` | Zone/level metadata (names, act counts, start positions) |
| `ObjectRegistry` | Object creation factories and ID mappings |
| `SpecialStageProvider` | Chaos Emerald special stage logic |
| `BonusStageProvider` | Checkpoint bonus stage logic (S3K) |
| `ScrollHandlerProvider` | Per-zone parallax scroll handlers |
| `ZoneFeatureProvider` | Zone-specific mechanics (CNZ bumpers, water) |
| `RomOffsetProvider` | Type-safe ROM address access |

### Usage
```java
// Access current game module
GameModule module = GameModuleRegistry.getCurrent();
ObjectRegistry objects = module.createObjectRegistry();
ZoneRegistry zones = module.getZoneRegistry();

// Auto-detect ROM and set module
GameModuleRegistry.detectAndSetModule(rom);
```

### Sonic 3&K Bring-up Notes (Critical)

- **Dual object pointer tables (zone-set system):** S3K uses two object pointer tables that remap many IDs by zone. `S3kZoneSet` enum: `S3KL` (zones 0-6: AIZ-LBZ) and `SKL` (zones 7-13: MHZ-DDZ). `Sonic3kObjectRegistry.getPrimaryName(id, zoneSet)` resolves zone-set-aware names. `Sonic3kObjectProfile` uses per-level resolution for names, badnik IDs, and boss IDs. Disasm source files: `Levels/Misc/Object pointers - SK Set 1.asm` (S3KL, 256 entries) and `Object pointers - SK Set 2.asm` (SKL, 185 entries).
- `Sonic3kLevel.loadMap(...)` must decode layout row pointers as interleaved FG/BG words per row:
  - FG pointer word at `header + row * 4`
  - BG pointer word at `header + 2 + row * 4`
  Parsing FG and BG pointers as contiguous tables corrupts terrain rendering and collision lookups.
- AIZ1 intro-skip bootstrap currently uses a parity bridge:
  - `Sonic3k.loadLevel(...)` resolves an AIZ1 gameplay-after-intro bootstrap profile.
  - It loads gameplay overlays from the intro `LevelLoadBlock` entry for `art2` and `blocks2`.
  - It uses `LevelSizes` index `26` (AIZ intro profile) so post-intro spawn is valid (`yEnd = 0x1000`).
  This intentionally skips full intro scripting and is documented in code comments.
- Camera clamping must happen after bounds are assigned:
  - In `LevelManager.loadCurrentLevel(...)`, call `camera.updatePosition(true)` again after setting `minX/maxX/minY/maxY`.
  - Without this, high-Y starts can be evaluated against stale bounds and trigger bad pit/death behavior.
- S3K collision index pointers:
  - Use `Sonic3k.decodeCollisionPointer(...)` marker logic (low-bit/high-bit marker + address threshold).
  - `Sonic3kLevel.readCollisionIndex(...)` uses stride-2 indexing, matching the original code path for chunk collision references.
- **PLC system:** See `s3k-plc-system` skill for Pattern Load Cue system docs (runtime art loading, act transitions, boss art).
- Current known limitation:
  - `validateResourceReferences()` may still log high chunk pattern references in some S3K acts (`maxChunkPatternIndex > patternCount`), indicating dynamic art/PLC parity is still incomplete.
- Regression tests to keep:
  - `TestS3kAiz1SpawnStability`
  - `TestSonic3kLevelLoading`
  - `TestSonic3kBootstrapResolver`
  - `TestSonic3kDecodingUtils`

## Object & Badnik System

Game objects use a factory pattern with game-specific registries.

### Key Classes
| Class | Purpose |
|-------|---------|
| `ObjectManager` | Unified manager with Placement, SolidContacts, TouchResponses, PlaneSwitchers |
| `Sonic2ObjectRegistry` | Factory registry for Sonic 2 objects |
| `Sonic2ObjectRegistryData` | Static name mappings for object IDs |
| `AbstractBadnikInstance` | Base class for enemy AI with collision handling |
| `ObjectFactory` | Functional interface for object creation |

### Adding New Objects
1. Add object ID to `Sonic2ObjectIds.java`
2. Create instance class extending `AbstractObjectInstance` (or `AbstractBadnikInstance` for enemies)
3. Register factory in `Sonic2ObjectRegistry.registerDefaultFactories()`
4. For solid objects, collision is handled automatically via `ObjectManager.SolidContacts`
5. For enemies, touch response is handled via `ObjectManager.TouchResponses`

### Game-Specific Art Loading

**Important:** Keep `ObjectArtData` game-agnostic. Game-specific art (badniks, zone objects) uses a provider pattern:

1. **Add ROM address** to `Sonic2Constants.java`
2. **Add art key** to `Sonic2ObjectArtKeys.java`
3. **Add public loader method** to `Sonic2ObjectArt.java`:
   ```java
   public ObjectSpriteSheet loadNewBadnikSheet() {
       Pattern[] patterns = safeLoadNemesisPatterns(ADDR, "Name");
       if (patterns.length == 0) return null;
       return new ObjectSpriteSheet(patterns, createMappings(), palette, 1);
   }
   ```
4. **Register in provider** `Sonic2ObjectArtProvider.loadArtForZone()`:
   ```java
   registerSheet(Sonic2ObjectArtKeys.NEW_BADNIK, artLoader.loadNewBadnikSheet());
   ```

**DO NOT** add badnik/enemy sheets to `ObjectArtData` - it should remain game-agnostic.

**S2 object art:** Prefer `S2SpriteDataLoader.loadMappingFrames(reader, mappingAddr)` to parse S2 mappings from ROM. Object instance files should use `S2SpriteDataLoader` directly instead of inline parser copies.

**S1 object art:** Use `Sonic1ObjectArt.buildArtSheet(artAddr, mappings, palette, bankSize)` for Nemesis art with mappings. Use `S1SpriteDataLoader.loadMappingFrames(reader, mappingAddr)` for ROM-parsed S1 mappings. Note: most S1 object mappings are inline assembly macros, so many objects still use hardcoded mappings.

**S3K level-art objects:** Prefer `Sonic3kObjectArt.buildLevelArtSheetFromRom(mappingAddr, artTileBase, palette)` to parse S3K mappings from ROM at runtime. Add mapping ROM address to `Sonic3kConstants.java` (use RomOffsetFinder). Extract art_tile base and palette from the object code's `make_art_tile()` call. Only hardcode mapping pieces when the ROM table can't be used directly.

**PLC system:** `PlcParser` in `level.resources` provides game-agnostic PLC parsing. See `plc-system` skill for cross-game reference, `s3k-plc-system` for S3K-specific details.

### Constants Files
| File | Contents |
|------|----------|
| `Sonic2Constants.java` | Sonic 2 primary ROM offsets |
| `Sonic2ObjectIds.java` | Sonic 2 object type IDs (0x41=Spring, 0x26=Monitor) |
| `Sonic2ObjectConstants.java` | Sonic 2 touch collision data |
| `Sonic2AudioConstants.java` | Sonic 2 music and SFX IDs |
| `Sonic1Constants.java` | Sonic 1 ROM offsets (zone IDs, level data, collision, palettes, art) |

## Adding New Game Support

To add support for a new game:
1. Create `GameModule` implementation (e.g., `Sonic3KGameModule`)
2. Create `RomDetector` to identify the ROM
3. Implement required providers (`ZoneRegistry`, `ObjectRegistry`, audio profile)
4. Register detector in `RomDetectionService.registerBuiltInDetectors()`
5. Add a `GameProfile` factory method in `RomOffsetFinder.GameProfile` for the ROM Offset Finder tool

Sonic 1 support is actively being developed on the `feature/sonic-1-support` branch with `Sonic1GameModule`, `Sonic1ZoneRegistry`, and related providers already in place. The ROM Offset Finder tool supports S1, S2, and S3K via `GameProfile` factory methods (`sonic1()`, `sonic2()`, `sonic3k()`).

## ROM Offset Finder Tool

If `docs/s2disasm`, `docs/s1disasm`, or `docs/skdisasm` is present, you can use the **RomOffsetFinder** tool to search for disassembly items, find their ROM offsets, verify them against ROM data, and export as Java constants. Supports Sonic 1, Sonic 2, and Sonic 3&K.

### Prerequisites
- `docs/s2disasm/` (Sonic 2), `docs/s1disasm/` (Sonic 1), or `docs/skdisasm/` (Sonic 3&K) directory must be present
- Corresponding ROM file in the project root (for `test`, `verify`, `verify-batch`, `export` commands)

### Game Selection

Use `--game s1`, `--game s2` (default), or `--game s3k` to select the target game. Can also be set via `-Dgame=s1` system property. If omitted, auto-detects from the disasm path.

### CLI Commands

| Command | Description |
|---------|-------------|
| `[--game s1\|s2\|s3k] search <pattern>` | Search by label/filename, shows calculated offset |
| `[--game s1\|s2\|s3k] list [type]` | List all includes, optionally filtered by compression type |
| `[--game s1\|s2\|s3k] test <offset> <type>` | Test decompression at a ROM offset |
| `[--game s1\|s2\|s3k] verify <label>` | Verify a calculated offset against ROM data |
| `[--game s1\|s2\|s3k] verify-batch [type]` | Batch verify all offsets (optionally filtered by type) |
| `[--game s1\|s2\|s3k] export <type> [prefix]` | Export verified offsets as Java constants |
| `[--game s1\|s2\|s3k] search-rom <hex> [start] [end]` | Search ROM binary for hex byte pattern |

### Usage via Maven

```bash
# Sonic 2 (default) - search, verify, export
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="search <pattern>" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="verify <label>" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="export nem ART_" -q

# Sonic 1 - use --game s1 flag
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 search Nem_GHZ" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 list nem" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 verify Pal_Sonic" -q

# Sonic 3&K - use --game s3k flag
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search AIZ" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k list nem" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k verify ArtNem_TitleScreenText" -q

# Search ROM binary for hex byte patterns (inline data, pointer tables, etc.)
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 search-rom \"07 72 73 26 15 08 FF 05\"" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search-rom \"0002 FF2A\" 0x28000 0x29000" -q

# Other commands (work with all games)
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="verify-batch nem" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="test <offset> <type>" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="list <type>" -q
```

### Examples

```bash
# Sonic 2: Search for special stage stars art
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="search SpecialStars" -q

# Sonic 2: Search for palettes (supports S2 palette macro)
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="search Pal_SS" -q

# Sonic 2: Verify a specific label's offset
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="verify ArtNem_SpecialHUD" -q

# Sonic 1: Search for GHZ Nemesis art
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 search Nem_GHZ" -q

# Sonic 1: Search for palettes (finds bincludePalette entries)
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 search Pal_Sonic" -q

# Sonic 1: Export verified Nemesis offsets as Sonic1Constants
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 export nem NEM_" -q

# Sonic 3&K: Search for Angel Island Zone items
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search AIZ" -q

# Sonic 3&K: List all Kosinski Moduled files (label-suffix detection)
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k list kosm" -q

# Sonic 3&K: Verify a specific offset against ROM
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k verify ArtNem_TitleScreenText" -q

# Search ROM for inline data (pointer tables, animation scripts, etc.)
# Useful when disassembly labels point to inline dc.w/dc.b data, not binclude files
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 search-rom \"07 72 73 26 15 08 FF 05\"" -q
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search-rom \"0002 FF2A\" 0x28000 0x29000" -q
```

### Verification Status Codes

| Code | Meaning |
|------|---------|
| `[OK]` | Calculated offset matches ROM data |
| `[!!]` | Mismatch - data found at different offset |
| `[??]` | Not found - couldn't locate data in ROM |
| `[ER]` | Error during verification |

### GameProfile and Anchor Offsets

The offset calculator uses a `GameProfile` that encapsulates all game-specific configuration:
- **Anchor offsets** - Known verified ROM addresses used as reference points for calculation
- **Main ASM file** - `s2.asm` for Sonic 2, `sonic.asm` for Sonic 1, `sonic3k.asm` for Sonic 3&K
- **Label prefix mappings** - For converting disassembly labels to Java constant names (e.g., `ArtNem_` -> `NEM_` for S2, `Nem_` -> `NEM_` for S1, `ArtKosM_` -> `KOSM_` for S3K)
- **Palette handling** - S2 uses a `palette` macro (expanded to `art/palettes/`), S1 uses `bincludePalette` (caught by the BINCLUDE regex), S3K uses plain `binclude` for palettes
- **Label-suffix compression inference** - S3K encodes compression type in label suffixes (e.g., `_KosM`, `_Kos`, `_Nem`) since files use `.bin` extension. The tool automatically infers the correct type.

Verified offsets are automatically added as runtime anchors during a session. To add permanent anchors, update the `GameProfile.sonic1()`, `GameProfile.sonic2()`, or `GameProfile.sonic3k()` factory methods in `RomOffsetFinder.java`.

### Compression Types
| Type | Extension | Argument |
|------|-----------|----------|
| Nemesis | `.nem` | `nem` |
| Kosinski | `.kos` | `kos` |
| Kosinski Moduled | `.kosm` | `kosm` |
| Enigma | `.eni` | `eni` |
| Saxman | `.sax` | `sax` |
| Uncompressed | `.bin` | `bin` |

### Palette Support
- **Sonic 2:** Parses `palette` macros (e.g., `Pal_SSResult: palette Special Stage/Results.bin` → `art/palettes/Special Stage/Results.bin`)
- **Sonic 1:** Parses `bincludePalette` directives (e.g., `Pal_Sonic: bincludePalette "palette/Sonic.bin"`)

### Programmatic Usage

The tools in `uk.co.jamesj999.sonic.tools.disasm` can also be used programmatically:

```java
// Sonic 2 (default profile)
RomOffsetFinder finder = new RomOffsetFinder("docs/s2disasm", "path/to/s2rom.gen");
VerificationResult result = finder.verify("ArtNem_SpecialHUD");

// Sonic 1 (explicit profile)
RomOffsetFinder.GameProfile s1 = RomOffsetFinder.GameProfile.sonic1();
RomOffsetFinder s1Finder = new RomOffsetFinder("docs/s1disasm", "path/to/s1rom.gen", s1);
VerificationResult s1Result = s1Finder.verify("Pal_Sonic");

// Sonic 3&K (explicit profile)
RomOffsetFinder.GameProfile s3k = RomOffsetFinder.GameProfile.sonic3k();
RomOffsetFinder s3kFinder = new RomOffsetFinder("docs/skdisasm", "path/to/s3krom.gen", s3k);
VerificationResult s3kResult = s3kFinder.verify("ArtNem_TitleScreenText");

// Search the disassembly
DisassemblySearchTool searchTool = new DisassemblySearchTool("docs/s1disasm", s1);
List<DisassemblySearchResult> results = searchTool.search("Nem_GHZ");

// Batch verify and export with game-aware labels
List<VerificationResult> batch = s1Finder.verifyBatch(CompressionType.NEMESIS);
ConstantsExporter exporter = new ConstantsExporter();
exporter.exportAsJavaConstants(batch, "", new PrintWriter(System.out), s1);
```

## Audio Engine hints
*   **Useful locations:** Work In Progress.
    *   `docs` – Contains lots of information about the audio engine in saved htm files.
	*   `docs/YM2612.java.example` – Contains a port of the Gens emulator's YM2612 implementation. Missing PCM functionality. May not be correct!
	*   `docs/SMPS-rips` – Contains ripped audio for various games, including `Sonic the Hedgehog 2`. Contains configurations for SMPSPlay.
	*   `docs/SMPS-rips/SMPSPlay` – This contains the source for SMPSPlay, which is an open-source implementation of playback of rips for game sfx/music, for games that use the SMPS driver for the Sega Genesis.
	*   `docs/SMPS-rips/SMPSPlay/libs/download/libvgm/emu/cores` – Contains source code for several consoles, but most importantly the ym2612(.c) for the sound chip, and sn76489(.c) which we are implementing on our own. These are extremely useful sources of truth for our project, as they are high-accuracy implementations.
*   **Important guidelines:** We strive for accuracy in the audio engine. Wherever possible, we should be implementing features identically to hardware. We should reference the existing libvgm cores, the SMPSPlay source, and the documentation to achieve this. We should not "twiddle knobs" or implement simplified versions of logic, instead preferring to diagnose issues and compare to reference/sources of truth.
## Useful tips

*   **Player Coordinates:** The original ROM uses **center coordinates** for player position. When implementing object interactions:
    *   `player.getX()` / `player.getY()` → Top-left corner (for rendering)
    *   `player.getCentreX()` / `player.getCentreY()` → Center position (for collision/interactions)
    *   **Always use center coordinates** for object collision checks to match ROM behavior. Using top-left creates ~19 pixel vertical offset errors.
*   **Terminology**: The codebase uses specific terms for level components that differ from standard Sonic 2 naming:
    *   **Pattern:** An 8x8 pixel tile.
    *   **Chunk:** A 16x16 pixel tile, composed of Patterns.
    *   **Block:** A 128x128 pixel area, composed of Chunks.
*   **Dependencies:** Running the engine requires LWJGL (OpenGL, OpenAL, GLFW bindings) and JOML (math library), already declared as dependencies in `pom.xml`.
*   **Debug:** `DEBUG_VIEW_ENABLED` (true by default) overlays sensor and collision info during gameplay.
*   **Level Loading:** Performed by `LevelManager`, which reads from the ROM through classes in `uk.co.jamesj999.sonic.data`.
*   **Skipped Tests**: `TestCollisionLogic` is skipped in the test environment because it requires a valid ROM file, which is not available. This is a known and accepted test outcome.
*   **File Endings**: Ensure all source code files end with a newline character.

