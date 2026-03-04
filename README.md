# OpenGGF - The Open-Source Java-Based Speedy Erinaceidae Engine

> This project is a work in progress. For the current state, please see the latest version in the
> Releases section of this document.

## Introduction

OpenGGF is an open-source, Java-based game engine for research and preservation of classic Mega
Drive / Genesis platform games, specifically the mainline Sonic the Hedgehog series. It aims to
faithfully reimplement the physics and rendering behaviour of the original hardware using data
loaded from user-supplied ROM images. The project's primary goal
is accuracy: physics, collision, and audio are all verified against community-maintained
disassemblies of titles in the Sonic the Hedgehog series. No copyrighted assets are included in
this repository; a legally obtained ROM is required to run the engine.

The engine also aims to provide modern tooling such as a level editor and an open framework for
modding and customisation.

> **Disclaimer:** This project is not affiliated with or endorsed by Sega. Sonic the Hedgehog and
> all related characters, names, and trademarks are the property of Sega Corporation. No ROM images
> or other copyrighted game data are included in this repository. Users must supply their own
> legally obtained ROM files to use this software.

## Configuration

The engine currently makes limited use of `config.json` to hold some basic configurations. Change
these at your own risk.

## Controls

> Currently, only keyboard controls are supported.

### Player Controls

| Key | Action |
|-----|--------|
| Arrow Keys | Movement |
| Space | Jump |
| Z | Cycle Acts |
| X | Cycle Zones |

### Debug Controls

| Key | Action |
|-----|--------|
| F1 | Show/Hide Debug Overlay (text and bounding boxes) |
| F2 | Show/Hide Shortcuts Overlay |
| F3 | Show/Hide Player Panel |
| F4 | Show/Hide Sensor Labels |
| F5 | Show/Hide Object Labels |
| F6 | Show/Hide Camera Bounds |
| F7 | Show/Hide Player Bounds |
| F9 | Show/Hide Ring Bounds |
| F10 | Show/Hide Plane Switchers |
| F11 | Show/Hide Touch Response |
| F12 | Show/Hide Art Viewer |

## FAQ

### What does "GGF" stand for?

Gotta Go Fast!

### Is this an emulator?

No. OpenGGF is an independent reimplementation of the game logic and physics, written in Java
from scratch. It does not emulate the Mega Drive CPU or VDP. Instead, it reads data (level
layouts, art, music) from original ROM images and runs its own implementation of the game rules.
The implementation is developed and verified against the community-maintained disassemblies
([s1disasm], [s2disasm], [skdisasm]) to achieve pixel-accurate behaviour. The audio engine is a
partial exception: it features software emulation of the YM2612 FM synthesiser and SN76489 PSG
chips (based on [libvgm] and [Genesis Plus GX] reference cores) driven by a reimplemented SMPS
sound driver.

[libvgm]: https://github.com/ValleyBell/libvgm
[Genesis Plus GX]: https://github.com/ekeeke/Genesis-Plus-GX

[s1disasm]: https://github.com/sonicretro/s1disasm
[s2disasm]: https://github.com/sonicretro/s2disasm
[skdisasm]: https://github.com/sonicretro/skdisasm

### Which games are supported?

| Game | Status |
|------|--------|
| Sonic the Hedgehog (S1) | Broadly playable. All 7 zones, 6 bosses, special stages, title screen, ending/credits. |
| Sonic the Hedgehog 2 (S2) | Most complete. All zones, 9 bosses (including both DEZ bosses), special stages, Tails AI, credits/ending. |
| Sonic 3 & Knuckles (S3K) | Early. Angel Island Zone with intro cutscene, gameplay objects, miniboss, water system, and fire transition. |

Work is ongoing across all three games. See CHANGELOG.md for detailed progress.

### Where do I get ROMs?

We do not supply ROM images. You must provide your own legally obtained copies. The engine expects
these specific revisions, placed in the working directory:

| Game | Expected filename | Revision |
|------|-------------------|----------|
| Sonic 1 | `Sonic The Hedgehog (W) (REV01) [!].gen` | World, Revision 01 |
| Sonic 2 | `Sonic The Hedgehog 2 (W) (REV01) [!].gen` | World, Revision 01 |
| Sonic 3&K | `Sonic and Knuckles & Sonic 3 (W) [!].gen` | World (lock-on combined ROM) |

Other revisions (REV00, etc.) are untested and will likely produce incorrect results, as
ROM addresses are verified against these specific builds. ROM filenames are configurable via
`config.json` (see `SONIC_1_ROM`, `SONIC_2_ROM`, `SONIC_3K_ROM` keys).

### What is cross-game feature donation?

A feature that lets a donor game (S2 or S3K) provide player sprites, spindash mechanics, and sound
effects while you play a different base game (e.g. Sonic 1). This means you can play S1 levels
with S2's Sonic and Tails sprites, spindash, and sidekick AI. Enable it in `config.json`:

```json
{
  "CROSS_GAME_FEATURES_ENABLED": true,
  "CROSS_GAME_SOURCE": "s2"
}
```

Both the base game ROM and the donor game ROM must be present.

### Why Java?

We knew Java, and nobody had done it before. Every other Sonic engine reimplementation out there is
written in C, C++, or C#. A Java implementation proves it can be done on a managed runtime, and
the JVM's cross-platform nature means it runs on Windows, macOS, and Linux without platform-specific
builds (though a GraalVM native image is also available for those who prefer it).

### Will Sega shut this down?

This project contains no copyrighted material. No ROM data, sprites, music, or other Sega assets
are included in the repository. The engine is an independent reimplementation, developed and
verified against the community-maintained disassemblies, that requires users to supply their own
legally obtained ROM files. We have no affiliation with Sega and make no claim to any of their
intellectual property.

### What platforms does it run on?

Anywhere Java 21 and LWJGL run: Windows, macOS, and Linux. The engine uses OpenGL 4.1 core profile
(chosen for macOS compatibility). A GraalVM native image build is also supported for ahead-of-time compiled
binaries.

### Did you use AI to write this? / This is AI slop!

Various agents (Claude, Codex, and Gemini, in various models, versions and forms) have all been used at various points in the project's history, and
the commit history doesn't hide it — you'll see `Co-Authored-By` tags throughout. But the project
has been in development since 2013, long before AI coding assistants existed.

The core engine framework, architecture, rendering pipeline, physics engine, and collision system
were designed and coded by hand. The multi-game provider architecture, the GPU shader pipeline, the
SMPS audio driver, and the original physics rewrite are all human-authored. AI was brought in
for bulk analysis and research, to accelerate bulk object and boss implementation, debugging, validation, and
unit tests — all under direct architectural oversight, with accuracy verified against the original
ROM disassemblies. Every commit is reviewed, tested, and corrected where needed.

You can't prompt your way to ROM accuracy (yet!). But we certainly prompted our way through object
implementations, research and boilerplate code a lot faster than would have been possible by hand.

### How can I contribute?

The project is open source. Check the issue tracker, OBJECT_CHECKLIST.md for unimplemented game
objects, and CHANGELOG.md for the current state of each game. The codebase uses a provider-based
architecture that makes it relatively straightforward to add new objects, zones, and game-specific
behaviour.

## Releases

### v0.4 (Unreleased)

A release-sized update focused on expanding playable coverage, ending sequences, and engine maturity.

- **Package rename** from `uk.co.jamesj999.sonic` to `com.openggf` across the entire codebase.
- **Master title screen** implemented: engine-wide PNG-based title screen with animated clouds, game
  selection, and pixel font renderer. Displayed on startup before entering game-specific title flow.
- **Sonic 1** has moved from initial support to feature complete: title screen flow, special
  stages, major per-zone event scripting, extensive object and badnik additions, multiple boss
  implementations (GHZ, MZ, SYZ, LZ, SLZ, FZ), Labyrinth water/drowning/splash behaviour,
  ending/credits work, SBZ post-level-end sequence, demo playback, edge balance and push block
  collision corrections, and slope crest sensor guard. Expect minor bugs, but the game should be playable
  from beginning to end.
- **Sonic 2** adds title screen support, major object passes for MTZ/SCZ/WFZ/OOZ, 9 boss fights
  (MCZ, MTZ, WFZ, and both DEZ bosses — Mecha Sonic and Death Egg Robot, plus Robotnik escape),
  a complete credits and ending cutscene system with ROM-accurate visuals, expanded per-zone event
  architecture, demo playback, signpost/badnik palette/stair block art fixes, and a systematic
  TODO resolution pass with disassembly validation.
- **Sonic 3&K** sees major AIZ progress including intro cutscene systems, hollow tree and vine
  traversal parity work, miniboss object set bring-up, initial badnik implementations, shield/PLC
  integration fixes, a full water system with provider architecture and underwater palettes,
  seamless AIZ fire transition flow, and related regressions/tests.
- **Cross-game feature donation** implemented: a donor game (S2 or S3K) can provide player sprites,
  spindash dust, physics, palettes, and SFX while the base game handles levels, collision, objects,
  and music. Now includes cross-game Super Sonic delegation.
- **Per-game physics** and Super Sonic state/control flow (implemented for S2, with cross-game
  delegation to S1 and S2 game modules).
- **Profile-driven level loading:** declarative `LevelInitProfile` system with 13 ROM-aligned
  steps per game, replacing the monolithic `loadLevel()` path.
- **Testability refactor:** `GameContext`, `SharedLevel`, `HeadlessTestFixture` builder, and
  profile-driven test teardown. Test grouping by level and 8-JVM parallel execution.
- **Engine fixes:** solid object edge jitter fix, S1 slope crest sensor guard, jump-while-airborne
  guard, fade transition flash fix, results screen rendering fix, HTZ earthquake fixes, SFX
  channel replacement fix.
- PLC/art-loader refactors, RomOffsetFinder/ObjectDiscoveryTool enhancements, configuration
  documentation, and broad audio/stability/performance hardening.

See CHANGELOG.md for full details.

### v0.3.20260206

A massive release covering 366 commits across every major subsystem.

- **Tails** (Miles Prower) is now a playable character with ROM-accurate CPU AI follower behaviour,
  input replay, flight, and configurable sidekick toggle.
- **Multi-game architecture:** The engine has been refactored to support multiple games via a
  provider-based abstraction layer, with initial Sonic 1 ROM support (level select, title cards, HUD,
  audio with S1-specific SMPS driver configuration) alongside the existing Sonic 2 support.
- **Physics:** The physics engine has been completely rewritten to match ROM behaviour.
- **Bosses and objects:** Boss fights are implemented for 5 zones (EHZ, CPZ, HTZ, CNZ, ARZ), along
  with 15+ new badniks and 50+ new game objects spanning all implemented zones.
- **Water:** A full water system with drowning mechanics is in place for CPZ and ARZ.
- **Graphics:** The graphics backend has been migrated from JOGL to LWJGL with a GPU-accelerated
  rendering pipeline (pattern atlas, tilemap shader, instanced sprite batching, priority FBOs).
- **Audio:** Major accuracy improvements to YM2612 FM synthesis (based on Genesis-Plus-GX reference)
  and the SMPS driver.
- **Infrastructure:** Per-game ROM configuration, a HeadlessTestRunner for physics integration
  testing, visual and audio regression test suites, a multi-game test annotation framework, GraalVM
  native build support, and significant performance optimisations throughout.

See CHANGELOG.md for full details.

### v0.2.20260117

Improvements and fixes across the board. Special stages are now implemented, feature complete with a
few known issues. Physics have been improved, parallax backgrounds implemented and complete for EHZ,
CPZ, ARZ and MCZ. Some sound improvements, title cards, level outros, etc.

### v0.1.20260110

Now vaguely resembles the actual Sonic 2 game. Real collision and graphics data is loaded from the
Sonic 2 ROM and rendered on screen. The majority of the physics are in place, although it is far
from perfect. A system for loading game objects has been created, along with an implementation for
most of the objects and badniks in Emerald Hill Zone. Rings are implemented, life and score tracking
is implemented. SFX and music are implemented. Everything has room for improvement, but this now
resembles a playable game.

### v0.05 (2015-04-09)

Little more than a tech demo. Sonic is able to run and jump and collide with terrain in a reasonably
correct way. No graphics have yet been implemented so it's a moving white box on a black background.

### v0.01 (Pre-Alpha, first documented 2013-05-22)

A moving black box. This version will be complete when we have an unskinned box that can traverse
terrain in the same way Sonic would in the original game.
