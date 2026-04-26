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

Development since `v0.5.20260411` has focused on making the in-engine editor usable without
destabilising gameplay, while continuing the runtime cleanup needed for safe mode switching and
further S3K parity work.

- **Experimental editor overlay:** a config-gated editor/playtest loop now exists behind
  `EDITOR_ENABLED`, with `Shift+Tab` to move between gameplay and the editor, focused block/chunk
  previews, derive edits, world-grid navigation, and safer resume/restart handling.
- **Runtime and singleton closure:** the engine-service and singleton-compatibility cleanup
  continued across runtime, render, audio, title, special-stage, and editor paths, with JUnit 5
  annotation-based ROM fixtures now the preferred test path.
- **Runtime-owned framework stack:** `GameRuntime` now hosts shared systems for typed zone state
  (`ZoneRuntimeRegistry`), palette ownership (`PaletteOwnershipRegistry`), animated tile channels
  (`AnimatedTileChannelGraph`), live level edits (`ZoneLayoutMutationPipeline`), deform/parallax
  reuse (`ScrollEffectComposer`), staged special draw passes (`SpecialRenderEffectRegistry`), and
  frame-level render-mode control (`AdvancedRenderModeController`).
- **Solid ordering parity:** same-frame solid contact ordering now snapshots pre/post-contact
  player state explicitly, tightening ROM parity across rideable, breakable, and trigger-driven
  objects in S1, S2, and S3K and fixing follow-on regressions in springs, dash triggers, and
  collapsing solids.
- **Trace replay tooling:** S2/S3K trace recorder and replay fixtures now capture BK2-backed frame
  data, object snapshots, sidekick state, and S3K AIZ/CNZ parity probes for regression work. The
  trace recorder is now at v6.1-s3k with per-frame Tails CPU state events plus per-frame
  `oscillation_state` snapshots; AIZ and CNZ traces are rerecorded against it. The recorder also
  re-captures `Level_frame_counter` at the first physics row instead of arm time, fixing a
  one-tick bootstrap drift that affected `level_gated_reset_aware` profiles. The new
  `trace-replay-bug-fixing` skill (mirrored in `.claude/skills/skill.md` and
  `.agents/skills/SKILL.md`) codifies the comparison-only invariant, the four mission rules, the
  diagnose-fix-regen-loop workflow, and pointers to disassembly and process skills.
- **S3K trace replay fixes:** AIZ first-error advanced 2590 → 2667 → 2721 → 2919 → 3834 (per-game
  sidekick fly-back exit gate at sonic3k.asm:26625-26630; ROM `SolidObject_cont` on-screen
  render-flags gate at `s2.asm:35140-35145` / `sonic3k.asm:41390-41392`, S3K-only via
  `PhysicsFeatureSet.solidObjectOffscreenGate`; Tails CPU auto-jump pushing-bypass at
  sonic3k.asm:26702-26705 / s2.asm:38943-38946 universal across S2/S3K; bonus removal of an
  engine-spurious `controlLocked` write on AIZ vine grab that was poisoning Sonic's recorded
  inputHistory and feeding wrong inputs to Tails CPU 16 frames later; opt-out from the
  off-screen solid-contact gate for springs because ROM `SolidObjectFull2_1P` at
  sonic3k.asm:41065 does not test render_flags bit 7, advancing F2919 → F3834). CNZ first-error
  advanced 1685 → 1740 → 1758 → 1791 → 1815 (despawn-on-id-mismatch gating, slope-repel slip
  in CnzWireCage release, per-tick `slopeRepelJustSlipped` flag, v6.1-s3k recorder
  pre-trace-osc semantic correction that resolved a separate F850 Hover Fan one-frame trigger
  lag, `CnzWireCage` airborne-capture `object_control` bit 0 set per ROM `loc_3394C` at
  sonic3k.asm:69921 with a matching pre-physics state snapshot pattern on
  `AbstractPlayableSprite`, and a Tails CPU bit-7 gate so the auto-jump trigger keeps firing
  while ROM cage holds the rider with bits 0-6 set — `sonic3k.asm:26672 bmi.w` is sign-bit-only,
  not an `isObjectControlled()` boolean). The earlier per-frame CPU-state hydration was
  reverted as a violation of the comparison-only invariant — engine state machines now
  produce ROM-correct values natively.
- **Trace recorder v6.2-s3k:** adds per-frame `object_state` (per nearby OST slot) and
  `interact_state` (per player) diagnostic events on top of v6.1 `oscillation_state` and v6.0
  `cpu_state`. All four event types are read-only diagnostic input — never hydrated into engine
  state per the comparison-only invariant captured by the `trace-replay-bug-fixing` skill.
- **CNZ object unit tests:** 60/71 → 71/71 passing. Multiple test assertions corrected to match
  ROM (`sub_324C0` clears Status_Roll at cylinder capture, hover-fan placement at fan centre,
  cannon test player Y standing on top, balloon launch via TouchResponseListener). Engine
  tweaks: cannon test seam restored, balloon pop animation flips destroyed flag, rising platform
  retains step-off bounce.
- **S3K CNZ and MGZ expansion:** Carnival Night now has a much larger traversal/object pass
  including tubes, teleporters, balloons, cannons, hover fans, trap doors, water helpers, and
  miniboss/boss scaffolding, while Marble Garden gains more event/object coverage and supporting
  PLC/art runtime updates.
- **Configuration and debug UX:** `config.json` key bindings now accept human-readable names such
  as `"SPACE"` and `"F9"`, and the debug/editor overlay text stack now uses the shared pixel-font
  renderer with improved batching and overlap handling.
- **S3K parity fixes:** HCZ2 now has the moving-wall chase sequence, HCZ water/column behavior was
  corrected further, and water state is restored properly after stage returns.
- **CNZ traversal parity:** the S3K CNZ cylinder now follows tighter ROM-facing motion, rider
  capture/release, twist-frame, priority, and visible-cadence checks, with refreshed directed
  tests and design notes backing the current behavior.
- **Data select and save system:** a full S3K data select screen with ROM-accurate rendering,
  8 save slots, team selection (Sonic+Tails, Sonic, Tails, Knuckles), and JSON-based save
  persistence with integrity verification. S1 and S2 can use the S3K data select via cross-game
  donation, with each game retaining its own save profiles and zone progression. Donated S1/S2
  now generate host-specific zone preview screenshots at runtime, use host-aware emerald colours
  and S1's six-emerald layout, and launch gameplay using the team selected in data select even
  when persistent config still names a different main character.
- **Runtime-owned frameworks:** `PaletteOwnershipRegistry` for multi-writer palette arbitration,
  `ZoneRuntimeRegistry` for typed per-zone state adapters, and related shared registries that
  normalize zone-specific behavior across games.
- **Performance parity pass:** trimmed hot-path render and runtime churn with atlas bulk-copy
  cleanup, object-manager active-list and solid/touch provider caches, direct scroll-buffer copy
  helpers, profiler gating behind the debug overlay, and direct-buffer SMPS block rendering.
- **Trace replay v3 execution counters:** physics CSVs and the replay harness now understand
  `gameplay_frame_counter`, `vblank_counter`, and `lag_counter`, with a one-shot notice when a
  pre-v3 fixture falls back to the legacy lag heuristic. The BizHawk v3 recorder upgrade is
  now landed for Sonic 1, Sonic 2, and Sonic 3 & Knuckles, with shared object-snapshot, sidekick,
  and AIZ/CNZ parity probe dumps driving regression fixtures.
- **S3K AIZ intro parity:** the AIZ1 intro now tracks the ROM for plane-explode timing,
  cutscene-Knuckles render-flag exit, giant ride-vine activated-swing behaviour, and explicit
  fire-transition promotion, with a new seed-at-frame-0 replay bootstrap path that lets
  end-to-end AIZ traces start at the first live in-level frame with ObjectManager VBlank and
  sprite state aligned to the recording.
- **S3K CNZ miniboss and Tails-carry groundwork:** CNZ miniboss arena scaffolding, a dedicated
  CNZ1 Sonic+Tails carry-in intro design spec, and a sidekick `CARRY_INIT` leader-position gate
  to keep CPU Tails aligned with Sonic through the carry handoff.
- **Sonic 1 LZ and demo parity:** wind-tunnel flat zero-distance landing, Elevator / Junction /
  Pole / Ring parity refresh, credits-demo frame-zero seeding, and MZ1 lost-ring collection
  order regression coverage for the ROM's first-ring-at-frame-2808 sequence.
- **PhysicsFeatureSet collision flags:** per-game collision behaviour (including cardinal snap
  and extended edge balance) is now flagged through `PhysicsFeatureSet` instead of ad-hoc
  `if/else` chains, with the rule reiterated in both `AGENTS.md` and `CLAUDE.md`.
- **Sonic 1 object-lifecycle parity:** explosion-item slot routing, lost-ring SST slot
  reservation, bounded `CHUNK_STEP` advancement, push-block two-stage out-of-range logic, and
  counter-based object-placement catch-up after large camera jumps all realigned with the ROM.
- **Cross-game monitor-sidekick guard:** donated S1 content now blocks AI sidekicks from breaking
  Sonic 1 monitors, matching the S2/S3K shared rule and keeping donated ownership semantics
  consistent. Documented as an intentional divergence in `docs/KNOWN_DISCREPANCIES.md`.
- **Feature-flag discipline:** per-game behavioural differences are gated by `PhysicsFeatureSet`
  flags rather than game-name `if/else` chains; `stageRingsUseObjectTouchCollection` replaces the
  last `getGameId() == GameId.S1` check in `RingManager`, and the rule is now documented in both
  `AGENTS.md` and `CLAUDE.md`.

See CHANGELOG.md for the running list of unreleased changes.

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
  First trace (S1 GHZ1, 3,905 frames) passes with 0 errors; a second baseline (S1 MZ1, 7,936
  frames) is now in-tree with expanded recorder and divergence diagnostics for ROM/engine parity
  investigation. Supports both BizHawk (Windows, Lua) and **stable-retro** (cross-platform,
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
