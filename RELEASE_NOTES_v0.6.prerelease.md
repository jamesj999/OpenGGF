OpenGGF `0.6.prerelease` is currently focused on making the in-engine editor usable without
destabilising gameplay, while continuing the runtime cleanup and parity work needed for a stronger
Sonic 3 & Knuckles baseline.

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
- Last code commit scanned for these notes: `20e8d8b43` (`Fix S3K water restore after stage returns`)
- Notes-publishing commit: `a6e0c3992` (`docs: refresh v0.6 prerelease release notes`)
- Recommended next comparison range: `20e8d8b43..HEAD`
