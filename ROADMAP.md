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

## Proposed Priorities

## v0.5 Theme: S3K Expansion and Shared-System Hardening

This should be the next major focus.

### Primary Goals

- Expand Sonic 3 & Knuckles beyond the current AIZ-heavy slice.
- Harden the systems that S3K stresses hardest: PLC/art loading, level bootstrap/init, zone events, water, and object registration.
- Keep Sonic 1 and Sonic 2 in maintenance mode unless shared-engine work requires changes there.

### Priority Areas

#### 1. Sonic 3 & Knuckles Zone Breadth

- Add dedicated scroll handlers for more S3K zones.
- Implement more S3K objects, badniks, and boss scaffolding.
- Improve zone-specific event scripting beyond the current AIZ concentration.
- Reduce "loads but is not truly playable" scenarios.

#### 2. S3K Runtime Resource Parity

- Continue improving PLC parity and dynamic art loading.
- Reduce remaining resource-reference warnings and dynamic art mismatches.
- Tighten object art/mapping loading so S3K implementations rely less on temporary bridges and special cases.

#### 3. Shared Load and Lifecycle Architecture

- Continue pushing level initialization through `LevelInitProfile`.
- Keep test teardown and runtime init flow aligned with the ROM-oriented model.
- Move more cross-game behavior behind providers instead of conditional logic in top-level engine flow.

#### 4. Release and CI Hygiene

- Keep docs, version numbers, tags, and release notes in sync.
- Improve ROM-optional test behavior for contributors and CI.
- Make regression failures easier to diagnose and less sensitive to environment issues.

### Suggested Exit Criteria for v0.5

- Sonic 3 & Knuckles has at least one additional zone or gameplay slice that feels meaningfully beyond bootstrap status.
- S3K object and scroll-handler coverage is clearly broader than in `v0.4`.
- Major shared-system work for S3K no longer feels provisional.
- CI, release metadata, and top-level docs remain aligned throughout development.

## v0.6 Theme: Tooling and Modding Foundations

Once S3K is less fragile, the next logical investment is tooling.

### Primary Goals

- Build the foundation for a usable level editor.
- Improve ROM/disassembly tooling integration.
- Make engine data flows easier to inspect, validate, and modify safely.

### Priority Areas

- Level editor foundation work.
- Better authoring and inspection workflows around objects, level data, mappings, and PLC data.
- Clearer separation between engine logic and editable content/configuration.
- More contributor-facing documentation for adding objects, bosses, and game-module features.

### Suggested Exit Criteria for v0.6

- The project has a real editor/tooling foundation, not just design notes.
- Common reverse-engineering and parity workflows are easier to repeat.
- The engine is materially easier to extend without deep familiarity with every subsystem.

## v0.7 Theme: Completion, Polish, and Parity Closure

This release should focus on reducing obvious gaps rather than introducing new strategic directions.

### Primary Goals

- Close high-visibility gameplay gaps across all three games.
- Improve end-to-end reliability of title, save, special-stage, ending, and transition flows.
- Convert lingering TODO/FIXME areas into tested behavior or explicit deferrals.

### Priority Areas

- Remaining high-value S3K gameplay gaps.
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

If the project needs a single sentence roadmap:

`v0.5` should make Sonic 3 & Knuckles materially deeper and more stable, `v0.6` should make the engine meaningfully easier to inspect and edit, and `v0.7` should focus on completion and parity closure rather than new strategic scope.
