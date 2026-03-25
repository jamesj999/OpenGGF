# OpenGGF Roadmap Proposal

This document is a proposal for how to prioritize OpenGGF after the `v0.4.20260304` release.

It is intentionally opinionated:

- Accuracy is more important than raw feature count.
- Shared engine maturity is more important than one-off hacks.
- A smaller number of release themes is better than a wide, vague backlog.

## Current Position

OpenGGF has crossed an important threshold:

- Sonic 1 is broadly playable from start to finish.
- Sonic 2 is the most complete game module and now includes ending/credits coverage.
- Sonic 3 & Knuckles has meaningful early-game support, but is still the least mature module.
- The engine now has stronger multi-game architecture, better tests, and a more credible release story than before `v0.4`.

That changes the planning problem. The project no longer needs a catch-all "prove this can work" roadmap. It needs a focused roadmap that turns broad momentum into a smaller number of high-value outcomes.

## Guiding Principles

### 1. Accuracy First

Gameplay, rendering, collision, audio, and event flow should continue to be validated against ROM behavior and disassembly references. "Looks close enough" should not be accepted as a default.

### 2. Shared Systems Before Workarounds

When multiple games need the same kind of capability, prefer improving shared providers, managers, and load pipelines instead of adding more game-specific branching in core flow.

### 3. Vertical Slices Over Shallow Coverage

A smaller number of fully coherent zones, cutscenes, and gameplay flows is more valuable than many partially implemented areas.

### 4. Release Discipline

Each release should have a narrow theme, explicit exit criteria, and documentation that matches reality.

## v0.5 Retrospective (2026-03-25)

### What the roadmap asked for

v0.5 was themed **"S3K Expansion and Shared-System Hardening"** with four priority areas:
S3K zone breadth, S3K runtime resource parity, shared load/lifecycle architecture, and release/CI
hygiene.

### What actually happened

The release skewed heavily architectural. 1,805 commits delivered:

**Delivered against v0.5 goals:**
- S3K object coverage is clearly broader: AIZ miniboss defeat flow completed, signpost and results
  screen, Blue Ball special stages (WIP), insta-shield, ~10 new objects, per-character physics,
  palette cycling for all zones.
- S3K PLC art registry populated for all zone badniks; DPLC and mapping table fixes.
- Shared systems hardened significantly (see below).
- Release metadata, CI, user guide, and docs all aligned.

**Not delivered against v0.5 goals:**
- No second playable S3K zone. AIZ2 itself is incomplete: missing the Flying Battery event, the
  AIZ2 boss fight, and the AIZ-to-HCZ transition. HCZ has palette cycling and a water surface
  object but no scroll handler, events, or playable object set.
- No new S3K scroll handlers beyond `SwScrlAiz`.
- No zone-specific event scripting beyond AIZ.

**Delivered ahead of schedule (v0.6/v0.7 items):**
- **Two-tier service architecture**: all 180+ object classes migrated from singleton access to
  `GameServices`/`ObjectServices` injection with NoOp sentinels. This was not in any roadmap
  milestone.
- **GameRuntime**: explicit mutable state owner with `resetState()` lifecycle on all singletons.
  Enables safe editor mode enter/exit. This is v0.6 editor foundation work.
- **LevelManager decomposition**: the engine's largest class broken into three focused components.
  This is the kind of separation the v0.6 tooling theme requires.
- **MutableLevel**: snapshot, mutation, and dirty-region tracking for level data. This is
  directly v0.6 level editor foundation.
- **Common code extraction (5 phases)**: 15+ abstract base classes, 10+ shared utilities,
  systematic deduplication. This makes the engine materially easier to extend (v0.6 exit criterion).
- **User guide**: comprehensive contributor documentation (v0.6 goal).
- **Cross-game abstraction**: `PlayableEntity`, `DonorCapabilities`, `CanonicalAnimation`,
  `AnimationTranslator`, bidirectional donation. More cross-game maturity than the roadmap
  anticipated at any milestone.
- **Multi-sidekick system** and **Tails AI rework**: these are v0.7 "close gameplay gaps" items.
- **Insta-shield**: a complete new gameplay mechanic with ROM parity.

### Assessment

The shared-system hardening goal was exceeded. The S3K expansion goal was partially met for AIZ
depth but missed on zone breadth. The release pulled forward substantial v0.6 editor foundation
and v0.7 gameplay gap work that was not originally planned for v0.5.

The remaining v0.5 S3K work (complete AIZ2, begin HCZ) carries forward as the immediate priority.

---

## Proposed Priorities (Updated 2026-03-25)

## v0.5 Remaining: Complete AIZ

Before tagging v0.5, the following must land:

1. **Flying Battery event/cutscene** at end of AIZ2.
2. **AIZ2 boss fight** (Eggman flame-craft).
3. **AIZ-to-HCZ zone transition** (bridge burn, waterfall drop).

These three items close AIZ as the first fully playable S3K zone from intro cutscene to zone
transition. This is the minimum bar for the v0.5 exit criteria.

## v0.6 Theme: S3K Zone Breadth and Level Editor

The v0.6 scope now combines the remaining S3K zone expansion (originally v0.5) with the tooling
foundation theme (originally v0.6), since much of the editor foundation was already delivered.

### Primary Goals

- Expand S3K beyond AIZ: bring up HCZ and at least one additional zone to playable status.
- Deliver a functional level editor prototype using the MutableLevel, GameRuntime, and
  LevelManager decomposition foundations already in place.
- Continue improving S3K runtime resource parity for non-AIZ zones.

### Priority Areas

#### 1. S3K Zone Expansion (carried from v0.5)

- HCZ scroll handlers, zone events, objects, and boss scaffolding.
- At least one more zone (CNZ or MHZ) with scroll handler and basic playability.
- S3K zone-specific event managers beyond AIZ.

#### 2. Level Editor Prototype

- Wire MutableLevel into an interactive editing mode with tile placement.
- Implement enter/exit play-testing using GameRuntime snapshot/restore.
- Basic undo/redo using MutableLevel's saveState/restoreState.

#### 3. S3K Runtime Resource Parity (continued)

- PLC and dynamic art loading improvements for HCZ and beyond.
- Reduce resource-reference warnings for non-AIZ zones.

#### 4. ROM/Disassembly Tooling

- Better authoring and inspection workflows around objects, level data, and PLC data.

### Suggested Exit Criteria for v0.6

- At least two S3K zones are playable from start to zone transition.
- The level editor supports basic tile editing with undo/redo and play-test round-trips.
- S3K object coverage is broad enough that non-AIZ zones feel populated, not empty.

## v0.7 Theme: Completion, Polish, and Parity Closure

This release should focus on reducing obvious gaps rather than introducing new strategic directions.

### Primary Goals

- Close high-visibility gameplay gaps across all three games.
- Improve end-to-end reliability of title, save, special-stage, ending, and transition flows.
- Convert lingering TODO/FIXME areas into tested behavior or explicit deferrals.

### Priority Areas

- Remaining high-value S3K gameplay gaps (additional zones, bosses, special stage polish).
- Special stage polish where current support exists but parity is incomplete.
- Final parity passes on transitions, boss sequences, and edge-case object behavior.
- Documentation cleanup around what is complete, partial, or intentionally deferred.

## 1.0 Criteria

Version `1.0` should not mean "every object from every game has been implemented."

It should mean:

- Sonic 1, Sonic 2, and Sonic 3 & Knuckles each have a credible full-game path.
- The engine can support long play sessions without major flow-breaking issues.
- Physics, collision, rendering, and audio regressions are heavily covered by automated tests.
- Remaining missing content is mainly content-completion work, not foundational architecture work.
- Contributors can understand the main extension points without reverse-engineering the entire codebase first.

## Explicit Non-Goals for the Near Term

These are all valid ideas, but they should not outrank the current roadmap themes:

- Broad new experimental features that bypass ROM accuracy concerns.
- Large UI rewrites that do not unlock gameplay, tooling, or reliability.
- Expanding many new zones at once without finishing the shared systems they depend on.
- Treating S1/S2 content growth as the main release driver while S3K remains structurally immature.

## Short Version

Complete AIZ2 for v0.5. Then `v0.6` expands S3K zone breadth and delivers a level editor prototype,
`v0.7` focuses on completion and parity closure.
