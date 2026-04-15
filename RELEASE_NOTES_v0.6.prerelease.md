OpenGGF `0.6.prerelease` is currently focused on making the in-engine editor usable without
destabilising gameplay, while continuing the runtime cleanup and parity work needed for a stronger
Sonic 3 & Knuckles baseline. This snapshot also introduces the data select and save system.

- **Data select and save system:** a full S3K data select screen with ROM-accurate rendering,
  8 save slots, team selection (Sonic+Tails, Sonic, Tails, Knuckles), and JSON-based save
  persistence with SHA256 integrity verification. S1 and S2 can use the S3K data select via
  cross-game donation, with each game retaining its own save profiles and zone progression.
  `StartupRouteResolver` routes the title screen `ONE_PLAYER` action to data select when S3K
  presentation is available.
- **Runtime-owned frameworks:** `PaletteOwnershipRegistry` for multi-writer palette arbitration,
  `ZoneRuntimeRegistry` for typed per-zone state, and related registries normalizing zone-specific
  behavior across games.
- **Experimental editor overlay and playtest loop:** `EDITOR_ENABLED` now gates an in-engine editor
  overlay that can be entered from gameplay with `Shift+Tab`. The current snapshot includes world
  cursor navigation, focused block/chunk previews, derive edits, and safer resume/restart handling.
- **Runtime and service-boundary cleanup:** engine-service ownership and singleton-compatibility
  cleanup continued across runtime, render, audio, title, special-stage, and editor paths, making
  mode switching and test setup more predictable.
- **JUnit 5 test infrastructure:** ROM-backed fixtures now use the annotation-based JUnit 5 path
  (`@RequiresRom`, `@RequiresGameModule`, `@FullReset`) rather than the older rule-based approach.
- **Configuration and debug UX:** `config.json` key bindings now accept human-readable names such as
  `"SPACE"`, `"Q"`, and `"F9"`, and the debug/editor text stack now uses the shared pixel-font
  renderer with better batching, caching, and label spacing.
- **S3K parity work:** HCZ2 now has the moving-wall chase sequence, HCZ water/column behaviour was
  tightened further, and water state restores correctly after returning from side stages.
- OpenGGF remains an **alpha** release focused on preservation and accuracy. Sonic 1 is broadly
  playable, Sonic 2 remains the most complete module, and Sonic 3 & Knuckles continues to expand
  from its AIZ/HCZ baseline rather than full start-to-finish coverage.

See `CHANGELOG.md` for the running list of unreleased changes.

Scan metadata:

- Base release/tag: `v0.5.20260411`
- Last code commit scanned for these notes: `89eca97ce` (`Add donated data select routing tests`)
- Notes-publishing commit: `a6e0c3992` (`docs: refresh v0.6 prerelease release notes`)
- Recommended next comparison range: `89eca97ce..HEAD`
