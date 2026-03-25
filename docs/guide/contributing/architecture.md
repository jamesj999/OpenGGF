# Architecture Deep Dive

This page explains the engine's major architectural patterns so you know where to make
changes when adding objects, bosses, zones, or engine features.

## Package Layout

```
com.openggf/
  Engine.java                -- Application entry, window creation, main loop setup
  GameLoop.java              -- Per-frame orchestration: input, update, render
  LevelFrameStep.java        -- Single-frame level update sequence

  game/                      -- Game module system
    GameModule.java          -- Interface each game implements
    GameModuleRegistry.java  -- Maps game identifiers ("s1","s2","s3k") to modules
    GameServices.java        -- Global services: ROM access, graphics, configuration
    GameRuntime.java         -- Mutable gameplay state container (in progress)

    sonic1/                  -- Sonic 1 module
      Sonic1GameModule.java  -- S1 provider wiring
      Sonic1.java            -- S1 ROM parsing and game data
      constants/             -- ROM addresses
      objects/               -- Object instances and registry
      audio/                 -- S1 SMPS configuration
      scroll/                -- S1 parallax scroll handlers
      events/                -- S1 per-zone level events

    sonic2/                  -- Sonic 2 module (same structure as above)
    sonic3k/                 -- Sonic 3&K module (same structure)

  level/                     -- Level infrastructure
    LevelManager.java        -- Active level state, tile grid, object spawning
    objects/                 -- Base object classes, registry interface, spawn records
      AbstractObjectInstance.java  -- Base class for all game objects
      ObjectRegistry.java         -- Interface: maps object IDs to factories
      ObjectManager.java          -- Active object tracking, spawn/despawn
      ObjectServices.java         -- Contextual services available to objects
      ObjectSpawn.java            -- Data record: x, y, objectId, subtype, flags

  physics/                   -- Physics and collision
    CollisionDetector.java   -- Player/object terrain checks
    SolidObjectHelper.java   -- Solid object push/stand/land logic
    ObjectTerrainUtils.java  -- Wall/floor/ceiling distance checks

  sprites/                   -- Sprite system
    playable/                -- Player character classes
    animation/               -- Animation controller
    art/                     -- Sprite art set, DPLC handling
    render/                  -- Pattern sprite renderer

  graphics/                  -- GPU rendering pipeline
    PatternAtlas.java        -- Tile texture atlas
    TilemapRenderer.java     -- Background plane rendering
    GLCommand.java           -- Render command interface

  audio/                     -- Sound system
    smps/                    -- SMPS sequencer, channel state
    ym2612/                  -- FM synthesis
    sn76489/                 -- PSG synthesis
    dac/                     -- DAC sample playback

  camera/                    -- Camera position, boundaries, shake
  data/                      -- ROM reading, decompression (Kosinski, Nemesis, etc.)
  debug/                     -- Debug overlays and visualization
  tools/                     -- Offline tools (RomOffsetFinder, etc.)
```

## The GameModule / Provider Pattern

The engine supports three games through a pluggable module system. Each game implements
the `GameModule` interface, which returns a collection of **providers** -- objects that
supply game-specific behavior for a generic engine capability.

Here is a simplified view of what `GameModule` provides:

| Provider | What it supplies |
|----------|-----------------|
| `ObjectRegistry` | Maps object IDs to factory functions that create instances |
| `ScrollHandlerProvider` | Per-zone parallax background scroll logic |
| `PhysicsProvider` | Per-character physics profiles (speeds, gravity, acceleration) |
| `LevelEventProvider` | Per-zone dynamic camera boundaries, triggers, cutscenes |
| `WaterDataProvider` | Per-zone water heights, underwater palettes, dynamic handlers |
| `ObjectArtProvider` | Sprite art sets, PLCs, mappings |
| `ZoneArtProvider` | Zone-specific tile art configuration |
| `TitleScreenProvider` | Game-specific title screen |
| `LevelSelectProvider` | Game-specific level select |
| `EndingProvider` | Credits and ending cutscene |
| `ZoneRegistry` | Zone/act metadata and identifiers |
| `TouchResponseTable` | Collision response rules |

The engine core calls these providers without knowing which game is active. To add
behavior for a specific game, you implement or extend the relevant provider in that
game's module directory.

**Example:** When the engine needs to know the water height for the current zone, it
calls `gameModule.getWaterDataProvider().getWaterHeight(zone, act)`. Sonic 2's module
returns water heights for ARZ and CPZ; Sonic 1's returns heights for LZ and SBZ3;
Sonic 3&K's returns heights for HCZ and LBZ. The engine does not know or care which
zones have water -- it just asks the provider.

## GameServices and ObjectServices

The engine uses a two-tier service architecture:

**GameServices** is the global tier. It provides access to things that exist once for the
entire application:
- ROM data access
- Graphics pipeline
- Audio system
- Configuration

**ObjectServices** is the contextual tier. It provides access to things that are specific
to the current gameplay context:
- Current level and camera
- Object manager (for spawning dynamic objects)
- Sound effect playback
- Game state (rings, lives, score)

Every object instance receives an `ObjectServices` reference via `services()`. This is
how objects interact with the world: `services().playSfx(id)`,
`services().objectManager().addDynamicObject(obj)`, etc.

The separation exists because the planned level editor will have multiple simultaneous
level contexts. GameServices stays shared; ObjectServices will be backed by a specific
runtime context.

## GameRuntime (In Progress)

The target architecture moves all mutable gameplay state into an explicit `GameRuntime`
object. Currently, some state lives in static singletons (e.g., `LevelManager.getInstance()`).
The migration is ongoing. As a contributor, be aware that:

- New code should prefer receiving dependencies through method parameters or
  `ObjectServices` rather than calling static `getInstance()` methods.
- Existing `getInstance()` patterns still work but represent the old style.

## Level Initialization: LevelInitProfile

Each game defines a `LevelInitProfile`: a declarative sequence of initialization steps
that run when a level loads. This replaced a monolithic `loadLevel()` method.

The steps (13 in total, defined by the `InitStep` enum) include:

1. Load level layout data
2. Decompress tile art
3. Load chunk and block mappings
4. Set up collision arrays
5. Load object placement list
6. Configure palettes
7. Set up water (if applicable)
8. Initialize camera and scroll boundaries
9. Register zone-specific objects
10. Load PLCs (sprite art)
11. Configure level events
12. Set player start position
13. Initialize audio (zone music)

Each game's profile specifies which steps to run and in what order. Some steps are
shared across games; others are game-specific. The profile is defined in the game
module (e.g., `Sonic2LevelInitProfile`).

## Object Lifecycle

### Placement Data

Each act has an object placement list in the ROM: a sequence of records specifying
object ID, position, subtype, and render flags. These are loaded into `ObjectSpawn`
records when the level initializes.

### Spawning

The `ObjectManager` tracks which objects are in range. As the camera scrolls, objects
whose X position falls within the spawn window are created:

1. The `ObjectSpawn` record is passed to `ObjectRegistry.create(spawn)`.
2. The registry looks up the object ID and calls the registered factory function.
3. The factory creates and returns an `ObjectInstance` subclass (e.g.,
   `ArrowShooterObjectInstance`).
4. The instance is added to the active object list.

### Update Loop

Every frame, the engine calls `update(frameCounter, player)` on each active object.
This is the equivalent of the 68000 jumping to the object's routine entry point. The
object reads its state, makes decisions, updates its position, and prepares render
commands.

### Rendering

After all objects have updated, the engine collects render commands. Each object's
`appendRenderCommands(commands)` method adds GPU draw calls to a command list. The
commands are sorted by priority bucket and executed.

### Destruction

Objects mark themselves for removal by calling `setDestroyed(true)`. The object manager
removes them at the end of the frame. Common reasons:
- Off-screen cleanup (the `isOnScreen()` check, equivalent to `MarkObjGone`)
- Defeated badnik (after explosion animation)
- Collected item (ring, monitor)
- Projectile hit a wall

### Dynamic Objects

Objects created at runtime (projectiles, explosions, debris) are not part of the
placement list. They are added via `ObjectManager.addDynamicObject(obj)`. They follow
the same update/render/destroy lifecycle but are not subject to camera-based spawn/despawn.

## Rendering Pipeline

The rendering pipeline is GPU-based (OpenGL 4.1 core profile). Contributors adding
objects or zones rarely need to interact with it directly.

**What you need to know:**

- **PatternSpriteRenderer:** The primary way objects draw themselves. Call
  `getRenderer(artKey)` to get a renderer for your object's art, then
  `drawFrameIndex(frame, x, y, hFlip, vFlip)` to draw a mapping frame.
- **Priority buckets:** Objects specify a priority via `getPriorityBucket()`. Lower
  numbers draw behind higher numbers. This matches the VDP's priority system.
- **Debug rendering:** Override `appendDebugRenderCommands(ctx)` to draw bounding boxes,
  sensor lines, or labels when the debug overlay is active.

**What you do not need to touch:**

- The pattern atlas (tile upload, GPU texture management)
- The tilemap shader (background plane rendering)
- FBO compositing (priority plane layering)
- The LWJGL/OpenGL layer

## Audio Pipeline

The audio system reimplements the SMPS (Sample Music Playback System) sound driver:

1. **SmpsLoader** parses music and SFX data from the ROM using pointer tables.
2. **SmpsSequencer** processes sequence commands each frame: note on/off, volume changes,
   tempo, loops, modulation.
3. **YM2612** produces FM synthesis audio from register writes.
4. **SN76489** produces PSG audio (square waves and noise).
5. **DacPlayer** handles PCM drum sample playback.

Each game has a `SmpsSequencerConfig` that captures driver differences:
- **Tempo mode:** S3K uses OVERFLOW (overflow = skip), S2 uses OVERFLOW2 (overflow = tick).
- **Note mapping:** S1 uses a different base note than S2/S3K.
- **PSG envelopes:** Per-game envelope tables.
- **Operator order:** S1 uses a different FM operator ordering.

To play a sound effect from an object: `services().playSfx(SfxEnum.SOUND_NAME.id)`.

## Next Steps

- [Tutorial: Implement an Object](tutorial-implement-object.md) -- Apply this knowledge
- [Adding Bosses](adding-bosses.md) -- Boss-specific patterns
- [Adding Zones](adding-zones.md) -- Bringing up a new zone
- [Audio System](audio-system.md) -- Audio details for contributors
