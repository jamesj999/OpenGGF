# Editor Cursor Navigation Design

**Date:** 2026-04-09
**Status:** Design
**Scope:** Restore the originally intended editor cursor/navigation behavior for the in-engine level editor overlay MVP

## Overview

The current GPT editor branch implements the session/runtime foundation for entering editor mode,
parking gameplay state, and resuming play-test from an editor cursor position. What it does not
yet implement is the actual navigation model described in the original overlay design and plan.

This design closes that gap.

The intended MVP behavior is:

- entering editor seeds a world-space cursor from the current player position
- in `WORLD` depth, the cursor moves freely in pixels like debug mode
- the camera follows that cursor continuously while in `WORLD`
- descending into `BLOCK` or `CHUNK` stops free movement and switches arrow keys to composition-grid navigation
- exiting editor resumes gameplay from the current editor cursor position

This keeps the editor overlay aligned with the original product expectation: a world-first,
keyboard-driven editing flow rather than a passive mode switch with placeholder UI.

## Problem

The current branch diverged from the original plan in a specific way:

- `EditorModeContext` stores a cursor, but only as session state
- `LevelEditorController` has hierarchy and selection state, but no cursor or navigation model
- `EditorInputHandler` only maps `Enter` and `Escape`
- `EditorWorldOverlayRenderer` can draw a cursor marker, but it does not participate in movement

As a result, editor mode can technically exist without providing the user-facing navigation
behavior it was supposed to ship with.

## Goals

- restore free-moving world cursor behavior in `WORLD` depth
- match existing debug-mode movement semantics closely enough to feel familiar
- make the camera follow the editor cursor during world editing
- switch arrow-key behavior to focused composition-grid navigation in `BLOCK` and `CHUNK`
- keep the editor cursor as the source of truth for play-test resume position
- preserve the existing runtime/session architecture rather than reintroducing a singleton editor root

## Non-Goals

- no object editor
- no ring editor
- no save/load pipeline
- no mouse-driven editing in this change
- no attempt to finish every planned overlay visual from the broader editor MVP

## Recommended Approach

Three implementation shapes were considered:

1. Controller-owned cursor with depth-dependent input modes
2. Session-owned cursor read directly by input/render code
3. Camera-owned navigation with inferred cursor position

The recommended approach is `1`.

Reasons:

- the cursor is editor behavior, not just session bootstrap data
- depth-specific input rules belong in editor control logic
- renderers should consume editor state rather than invent it
- session state should remain durable and minimal

## Architecture

### Ownership

- `EditorModeContext` remains the durable mode/session owner for:
  - `WorldSession`
  - current `EditorCursorState`
  - play-test stash
- `LevelEditorController` becomes the behavioral owner for:
  - world cursor movement
  - block/chunk composition selection
  - hierarchy depth
  - breadcrumb and editing history
- `EditorInputHandler` becomes the translator from held keys into controller actions
- `Engine` and `GameLoop` remain orchestration layers only

### State Model

The editor should distinguish two navigation shapes:

- `WORLD` depth
  - cursor position is pixel-based world position
  - camera follows cursor continuously
  - arrow keys move the cursor every frame while held
- `BLOCK` and `CHUNK` depth
  - world cursor position is frozen
  - arrows move focused composition-grid selection
  - camera no longer free-follows input

This gives the world-edit flow the freedom the user expects, while keeping deeper editing layers
precise and structured.

## Input and Navigation Behavior

### WORLD Depth

Behavior:

- held arrow keys move the editor cursor in pixel space
- movement uses a fixed debug-style step per frame
- opposite directions cancel naturally by applying neither net delta nor conflicting camera shifts
- cursor movement updates the active `EditorModeContext` cursor each frame
- camera follows the cursor continuously

The movement model should be intentionally simple. It does not use gameplay physics, terrain
collision, or momentum.

### BLOCK and CHUNK Depth

Behavior:

- arrow keys stop controlling the world cursor
- arrow keys move the active cell selection within the focused composition grid
- `Enter` still descends where valid
- `Escape` still ascends
- `Space` and `E` remain reserved for apply/eyedrop behavior as the broader editor MVP grows

This preserves the original hierarchical editing model instead of treating every depth as the same
kind of canvas.

## Camera Behavior

In `WORLD` depth, the camera follows the editor cursor directly.

The editor cursor should be treated as the camera anchor for editor mode. The player remains frozen
underneath the editor overlay, but is no longer the camera focus while editing.

In `BLOCK` and `CHUNK` depth, the camera should remain stable rather than reacting to grid
navigation. Focus shifts to the pane-level composition surface, not to world-space travel.

## Data Flow

### Entering Editor

1. Capture the current player position.
2. Seed `EditorModeContext` cursor from that position.
3. Initialize or sync `LevelEditorController` with that cursor.
4. Park gameplay runtime and activate editor mode.
5. Camera begins following the controller/session cursor.

### Editing in WORLD

1. `GameLoop` routes editor-mode update into `EditorInputHandler`.
2. `EditorInputHandler` reads held movement keys.
3. `LevelEditorController` updates world cursor position.
4. `EditorModeContext` cursor is kept in sync.
5. Camera is updated from the current editor cursor.
6. `EditorWorldOverlayRenderer` renders the cursor at that location.

### Exiting Editor

1. Read the current synced cursor position.
2. Resume gameplay from that position.
3. Restore parked runtime/play-test stash behavior already implemented on the branch.

## Testing

Required automated coverage:

- controller test: `WORLD` arrow movement changes cursor position
- controller test: `BLOCK` and `CHUNK` arrow movement changes composition selection instead of cursor position
- integration test: entering editor seeds cursor from player position
- integration test: moving cursor in editor changes resume position on play-test return
- render smoke test: world overlay renderer builds cursor marker from the current cursor state after movement

Tests should assert behavior, not just absence of exceptions.

## Risks and Mitigations

### Risk: Split source of truth between controller and session

Mitigation:

- controller is the behavioral owner
- `EditorModeContext` is the durable persisted mode state
- sync occurs through explicit controller-to-session updates during editor-mode ticks

### Risk: Camera logic accidentally reverts to player focus

Mitigation:

- in editor mode, camera updates should derive from the editor cursor, not normal gameplay follow
- tests should cover cursor-based resume and editor-mode camera expectations where practical

### Risk: Overreaching into unfinished editor features

Mitigation:

- limit this design to cursor/navigation behavior and the minimum state needed to support it
- leave broader apply/eyedrop/world-grid richness to later slices

## Acceptance Criteria

This design is complete when:

- `Shift+Tab` enters editor with a visible cursor at the current player position
- in `WORLD`, the cursor moves freely with held arrows like debug mode
- camera follows that cursor continuously in `WORLD`
- in `BLOCK` or `CHUNK`, arrows navigate the composition grid rather than moving the world cursor
- `Shift+Tab` back to gameplay resumes from the moved cursor position
- the behavior is covered by deterministic tests
