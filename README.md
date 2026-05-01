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

## User Guide

A comprehensive user guide is available in [`docs/guide/`](docs/guide/index.md), covering:

- **Players:** [Getting started](docs/guide/playing/getting-started.md), [controls](docs/guide/playing/controls.md), [configuration](docs/guide/playing/configuration.md), [game status](docs/guide/playing/game-status.md), and [troubleshooting](docs/guide/playing/troubleshooting.md).
- **Contributors:** [Dev setup](docs/guide/contributing/dev-setup.md), [architecture overview](docs/guide/contributing/architecture.md), [adding zones](docs/guide/contributing/adding-zones.md), [adding bosses](docs/guide/contributing/adding-bosses.md), [audio system](docs/guide/contributing/audio-system.md), [testing](docs/guide/contributing/testing.md), and [trace replay testing](docs/guide/contributing/trace-replay.md).
- **Cross-referencers:** [68000 primer](docs/guide/cross-referencing/68000-primer.md), [mapping exercises](docs/guide/cross-referencing/mapping-exercises.md), [per-game notes](docs/guide/cross-referencing/per-game-notes.md), and [tooling](docs/guide/cross-referencing/tooling.md).

Contributor tests are JUnit 5 / Jupiter only. Do not add JUnit 4 tests, rules, runners, or `org.junit.*` imports.

## Configuration

The engine reads runtime settings from `config.json`. Key bindings can be written either as GLFW
integer codes or as human-readable names such as `"SPACE"`, `"Q"`, or `"F9"`. See
[`CONFIGURATION.md`](CONFIGURATION.md) and the player guide for the full reference.

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

### Editor Controls

| Key | Action |
|-----|--------|
| Shift+Tab | Toggle between gameplay and the experimental editor overlay (`EDITOR_ENABLED` must be `true`) |
| F5 | Restart the playtest from editor mode |

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
| Sonic the Hedgehog (S1) | Broadly playable. All 7 zones, 6 bosses, special stages, title screen, ending/credits. When S3K is the donor, S1 can also use the donated S3K data select screen with runtime-generated zone previews and cross-game team launch support. |
| Sonic the Hedgehog 2 (S2) | Most complete. All zones, 9 bosses (including both DEZ bosses), special stages, Tails AI, credits/ending. When S3K is the donor, S2 can also use the donated S3K data select screen with runtime-generated zone previews and cross-game team launch support. |
| Sonic 3 & Knuckles (S3K) | Progressing. Angel Island Zone is substantially playable, Hydrocity now has early HCZ2 chase coverage, and S3K includes title screen, level select, data select with save/load support, Knuckles glide/climb, Blue Ball special stages (WIP), bonus-stage parity work, palette cycling, and expanding object/badnik coverage. Data select can also be donated to S1/S2 via cross-game donation. |

Recent engine work has also moved shared zone behavior onto runtime-owned frameworks: `ZoneRuntimeRegistry`, `PaletteOwnershipRegistry`, `AnimatedTileChannelGraph`, `ZoneLayoutMutationPipeline`, `ScrollEffectComposer`, `SpecialRenderEffectRegistry`, and `AdvancedRenderModeController`. Current retrofit work is focused on uplifting existing Sonic 1 and Sonic 2 logic onto these systems while continuing incremental Sonic 3&K bring-up.

Current migration status is intentionally partial rather than universal. Sonic 2 already uses the runtime-owned stack for HTZ/CNZ runtime state, palette ownership, animated tile orchestration, CNZ staged overlay rendering, and CNZ layout mutations via `ZoneLayoutMutationPipeline`. Sonic 3&K uses the same stack for AIZ/HCZ/CNZ runtime-state adapters, AIZ staged render effects and advanced render modes, HCZ/SOZ animated tile channels, CNZ runtime-state-backed scroll behavior, and seamless terrain-swap/mutation paths routed through the mutation pipeline. The shared scroll-composition helpers are live in AIZ, HCZ, and MGZ. Other S1/S2/S3K zones still mix these frameworks with older zone-local paths and should be treated as follow-up migration work rather than implied complete adoption.

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

A feature that lets a donor game (S2 or S3K) provide player sprites, spindash mechanics, sound
effects, and the data select (save/load) screen while you play a different base game (e.g.
Sonic 1). This means you can play S1 levels with S2's Sonic and Tails sprites, spindash, and
sidekick AI — and when S3K is the donor, you also get the full S3K data select screen with
save slots and team selection before gameplay begins.
When S3K is the donor, that donated data select now also uses host-specific emerald presentation
and runtime-generated S1/S2 zone preview screenshots. Data select donation is only enabled when
`CROSS_GAME_FEATURES_ENABLED` is `true` and `CROSS_GAME_SOURCE` is `"s3k"`. Enable it in
`config.json`:

```json
{
  "CROSS_GAME_FEATURES_ENABLED": true,
  "CROSS_GAME_SOURCE": "s3k"
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
the commit history doesn't hide it; you'll see `Co-Authored-By` tags throughout. But the project
has been in development since 2013, long before AI coding assistants existed.

The core engine framework, architecture, rendering pipeline, physics engine, and collision system
were designed and coded by hand. The multi-game provider architecture, the GPU shader pipeline, the
SMPS audio driver, and the original physics rewrite are all human-authored. AI was brought in
for bulk analysis and research, to accelerate bulk object and boss implementation, debugging, validation, and
unit tests; all under direct architectural oversight, with accuracy verified against the original
ROM disassemblies. Every commit is reviewed, tested, and corrected where needed.

You can't prompt your way to ROM accuracy (yet!). But we certainly prompted our way through object
implementations, research and boilerplate code a lot faster than would have been possible by hand.

### How can I contribute?

The project is open source. Check the issue tracker, OBJECT_CHECKLIST.md for unimplemented game
objects, and CHANGELOG.md for the current state of each game. The codebase uses a provider-based
architecture that makes it relatively straightforward to add new objects, zones, and game-specific
behaviour.

## Releases

### v0.6.prerelease (Current development snapshot)

Development since `v0.5.20260411` is the active 0.6 prerelease line. The detailed running notes now
live in `CHANGELOG.md`; this README keeps only the high-level shape of the release.

- **Editor groundwork:** a config-gated editor/playtest loop, focused block and chunk previews,
  derive edits, world-grid navigation, and safer mode switching are being built toward usable
  in-engine editing.
- **Runtime modernization:** gameplay state continues moving behind `GameRuntime`, `GameServices`,
  `ObjectServices`, and runtime-owned frameworks for palette ownership, zone state, animated tiles,
  layout mutation, scroll composition, special render passes, and advanced render modes.
- **S3K bring-up and parity:** Angel Island, Carnival Night, Hydrocity, Marble Garden, data select,
  save handling, and sidekick/object interactions continue to gain ROM-cited behavior and tests.
- **Trace replay and diagnostics:** S1, S2, and S3K trace replay tooling now has stronger recorder
  schemas, comparison-only aux streams, compressed fixtures, and focused workflows for parity fixes.
- **Trace recorder:** S3K v6.6 AIZ diagnostics expose tree/boundary pre/post state at the F4679
  sidekick boundary frame, transition-floor SolidObjectTop decisions at the F5415 frame, and
  fire-handoff terrain/SolidObjectTop state around F5435 while keeping trace data comparison-only;
  S3K v6.7 CNZ diagnostics now expose cylinder P2 slot and
  execution state around the F4508 frame plus regenerated focused Tails position-write hooks around
  F4790, with recorder diagnostic locals compacted under BizHawk's NLua limit, and engine-side
  sidekick CPU/control diagnostics around the F5087 blocker. S3K v6.11-s3k now records `(a1)`/`(a0)`
  M68K registers per `position_write` hit and a new `solid_object_cont_entry` event capturing
  `y_radius`/`default_y_radius` at `SolidObject_cont` entry so the CNZ F7614 geometric contradiction
  (captured `loc_1E154` lift PCs vs. trace numerics that should fail the precondition) can be
  resolved once the trace is regenerated.
- **S3K trace replay fixes:** Carnival Night sidekick push/facing ordering, grounded release
  input timing, S3K air right-wall separation, wire-cage release parity, high-speed cage capture
  velocity, horizontal-spring airborne contact handling, the SolidObject on-screen gate now
  reading per-object width_pixels against the previous frame's camera (matching ROM render_flags
  bit 7 timing), the new `solidObjectTopBranchAlwaysLiftsOnUpwardVelocity` feature flag
  (matching ROM `loc_1E154`'s position lift before the upward-velocity check at
  `sonic3k.asm:41606-41632`, gated S3K-only) with per-(player, object) standing-bit tracking
  mirroring ROM `a0.d6` semantics, and the cross-game spring-trigger `Status_OnObj` clear
  (matching ROM `sub_22F98`/`sub_233CA`/`sub_234E6` `bclr #Status_OnObj` after
  `bset #Status_InAir` for S3K, S2 `s2.asm:33732-33733`, and S1
  `_incObj/41 Springs.asm:88-89/183-184`) plus the wired `onObjectAtFrameStart` snapshot in
  the Tails CPU `loc_13DA6` follow-steering gate now advance the CNZ v6.5/v6.7 replay frontier
  from F3905 to F7919 while preserving S1/S2 trace baselines.
- **S3K trace replay fixes:** Angel Island sidekick boundary, AIZ1 resize parity, stale reload
  object handoff, reload frame-counter cadence, catch-up flight gating, and the AIZ collapsing-
  platform state-1→state-2 transition slope-sample skip, and the state-2→state-3 unconditional
  promotion (releasing stuck rider state when the platform's stay timer expires with no player
  standing), and the new `levelBoundaryUsesCentreY` feature flag (matching ROM `Player_LevelBound`
  / `Tails_Check_Screen_Boundaries` centre-Y compare for S3K) now advance the AIZ v6.6/v6.9 replay
  frontier from F4679 to F7171; the centre-Y flag is ROM-correct and gated S3K-only pending S1/S2
  trace re-validation; the AIZ2 SonicResize1 miniboss-skip now gates on
  `apparent_zone_and_act == 1` (matching ROM `sonic3k.asm:39053`/`:39164`) instead of the
  heuristic `enteredAsAct2`, and the sidekick LEVEL_BOUNDARY kill now writes `y_vel = -0x700`
  (matching ROM `Kill_Character` at `sonic3k.asm:21149`) so `MoveSprite_TestGravity2` produces
  the ROM-correct in-frame upward shift after the kill, and runs the post-Kill_Character
  MoveSprite step before collision (matching ROM `Tails_Stand_Freespace` at
  `sonic3k.asm:27559`); the kill's touch-floor reset rolling-radius adjustment now uses
  ROM's `old_y_radius - default_y_radius` formula (matching `sonic3k.asm:29134-29156`) instead
  of the engine's prior `getHeight() - getStandYRadius()` -- the latter was injecting a +13
  px error into rolling sidekick deaths and is fully resolved at F4679 (1050 -> 1049 errors).
  AIZ2 SonicResize2 now reads `camera().previewNextX()` (matching ROM's `Do_ResizeEvents`
  ordering inside `DeformBgLayer` AFTER `MoveCameraX` at `sonic3k.asm:38303-38316`) and the
  sidekick dead-falling path now preserves `Kill_Character`'s `y_vel = -0x700` across the
  `sub_13ECA` despawn warp (matching ROM `MoveSprite_TestGravity` shifting y_pos by the
  preserved velocity at `sonic3k.asm:36032-36042` before the +0x38 gravity), advancing the
  AIZ replay frontier to F7235 (Sonic-rolling top-speed cap divergence, separate blocker).
  Visual trace bootstrap now uses the shared replay bootstrap so AIZ/CNZ visualiser sessions
  match headless replay's seed/cursor policy.
- **S3K known blockers:** Angel Island F6920 sloped collapsing-platform ordering is documented with
  ROM constraints — including precise slope-sample arithmetic, ruled-out hypotheses, and remaining
  open hypotheses — so future work avoids previous-X sampling hacks that regress earlier AIZ frames.
  Carnival Night F6304 Tails-on-door re-land is documented with ROM cites covering the door's
  `SolidObjectFull` solidity triplet and Tails CPU `leader_fast` follow-leader interaction so future
  cross-game work can land an airborne-from-above latch without ad-hoc S3K-only branches.
- **S3K bring-up and parity:** AIZ intro setup now re-adopts the live intro object across headless
  event reinitialization, keeping ROM-style pre-frame intro state available to tests.
- **Cross-game cleanup:** collision, solid-object ordering, sidekick handling, feature-flagged
  physics differences, configuration UX, debug rendering, and performance hot paths continue to be
  tightened across Sonic 1, Sonic 2, and Sonic 3 & Knuckles.

See `CHANGELOG.md` for the detailed 0.6 prerelease change history.

### v0.5.20260411 (Released 2026-04-11)

A primarily architectural release. The engine internals have been restructured to prepare for level
editor support, safe runtime teardown, and multi-instance play-testing, while Sonic 3 & Knuckles
gameplay coverage has expanded across Angel Island and Hydrocity. AIZ2 now has the Flying Battery
bombing sequence, end boss, post-boss capsule/cutscene flow, and AIZ-to-HCZ transition represented,
while HCZ now has a larger object/event pass and HCZ1-to-HCZ2 progression.

- **Two-tier service architecture:** all 180+ game object classes migrated from direct singleton
  access to a two-tier dependency injection pattern (`GameServices` global facade + `ObjectServices`
  context-scoped injection). NoOp sentinels replace null checks throughout.
- **GameRuntime:** explicit runtime object owns all mutable gameplay state, with `resetState()`
  lifecycle on all singletons. Enables safe editor mode enter/exit and level rebuilds.
- **LevelManager decomposition:** the engine's largest class broken into `LevelTilemapManager`,
  `LevelTransitionCoordinator`, and `LevelDebugRenderer` with ~73 methods extracted.
- **MutableLevel:** snapshot, mutation, and dirty-region tracking for level tile data — the
  foundation for the upcoming level editor's undo/redo and real-time tile editing.
- **Common code extraction (5 phases):** 15+ abstract base classes, 10+ shared utilities, and
  systematic deduplication across all three games, including `SubpixelMotion`, `AnimationTimer`,
  `FboHelper`, `AbstractMonitorObjectInstance`, `AbstractSpikeObjectInstance`,
  `AbstractZoneScrollHandler`, and more.
- **Knuckles** is now a playable character with full glide/climb state machine, ROM-accurate
  jump height, wall grab, ledge climb, and sliding physics. Works in S3K natively and via
  cross-game donation into S1/S2 with correct palette and HUD from the lock-on ROM.
- **Sonic 3&K** expands with title screen (SEGA logo, Sonic morph animation, interactive menu),
  level select screen (SONICMILES background, zone icons, sound test), AIZ miniboss completion
  (defeat flow, napalm attack, staggered explosions), AIZ2 Flying Battery bombing/end-boss work,
  signpost and results screen, Blue Ball special stages (WIP) with per-character art/palette,
  S3K bonus-stage work across Gumball, Glowing Sphere/Pachinko, and Slots, per-character physics
  profiles, palette cycling for all zones, HCZ water rush / conveyor / fan / block / door /
  miniboss coverage, and many new badniks/objects including CollapsingBridge, MegaChopper,
  Poindexter, Blastoid, Buggernaut, Bubbler, TurboSpiker, and InvisibleHurtBlockH.
- **Insta-shield** fully implemented with ROM parity: activation, hitbox expansion, persistent
  lifecycle, cross-game donation, and DPLC cache management.
- **Multi-sidekick system** with configurable sidekick chains, per-character respawn strategies,
  virtual VRAM bank allocation, and VDP-accurate sprite priority ordering.
- **Tails AI rework:** ROM-accurate respawn gating, PANIC mode rewrite, flying/despawn
  improvements, P2 manual override, and per-zone boss/event wiring.
- **Cross-game donation** now bidirectional: S1 can donate into S2/S3K, with `DonorCapabilities`
  interface, `CanonicalAnimation` vocabulary, and `AnimationTranslator` for any game pair.
- **Rendering pipeline:** PatternAtlas slot reclamation, batched DPLC updates, virtual pattern ID
  validation, SAT sprite-mask replay ordering for mixed-priority S3K bonus-stage art, and
  fail-fast shader error handling.
- **Trace replay testing:** automated accuracy verification that records per-frame physics state
  from the real ROM, then replays the same inputs through the engine and compares every field.
  First trace (S1 GHZ1, 3,905 frames) passes with 0 errors; the latest GHZ bridge pass fixes
  the F2967 rider Y divergence by keeping Bri_Solid's final `Plat_NoXCheck` width and updating
  the rider bend log before sag calculation (`docs/s1disasm/_incObj/11 Bridge.asm:98-114`,
  `135-152`, `_incObj/sub PlatformObject.asm:19-42`, `58-76`, `_incObj/sub ExitPlatform.asm:8-23`).
  A second baseline (S1 MZ1, 7,936 frames) now passes after the Obj52 Moving Block
  jump-carry fix: S1 `MBlock_StandOn` clears Sonic's on-object status via
  `ExitPlatform`, then still moves the block and applies one final `MvSonicOnPtfm2`
  carry on the jump-off frame (`docs/s1disasm/_incObj/52 Moving Blocks.asm:65-83`,
  `_incObj/sub ExitPlatform.asm:5-24`, `_incObj/15 Swinging Platforms.asm:177-194`).
  Supports both BizHawk (Windows, Lua) and **stable-retro** (cross-platform,
  Python) as recording backends — both produce identical output consumed by the same Java test
  infrastructure.
- Comprehensive user guide, 15+ design specs and implementation plans, and broad test coverage
  improvements including automated singleton lifecycle testing.

See CHANGELOG.md for full details.

### v0.4.20260304 (Released 2026-03-04)

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
