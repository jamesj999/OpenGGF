# Known Bugs and Unfinished Work — Sonic 3 & Knuckles

This document tracks **Sonic 3 & Knuckles bugs**, incomplete implementations, and known parity gaps that we intend to fix but haven't addressed yet. Entries here are *not* intentional — they're acknowledged problems with a plan (or hope) of eventual resolution.

For **intentional** S3K deviations (architectural choices, feature extensions, deliberate bug-fixes of ROM data), see [S3K_KNOWN_DISCREPANCIES.md](S3K_KNOWN_DISCREPANCIES.md).

For general (cross-game) bugs, see [KNOWN_BUGS.md](KNOWN_BUGS.md).

Entries should include:
- **Location** — the file(s) where the bug lives, if known
- **Symptom** — what goes wrong and where you can observe it (test name, trace frame, manual repro)
- **Suspected cause** — best current theory, with ROM/disasm references when relevant
- **Removal condition** — what needs to be true for this entry to be deleted

---

## Table of Contents

1. [CNZ1 Miniboss Arena Entry — Music Play-In Missing](#cnz1-miniboss-arena-entry--music-play-in-missing)
2. [AIZ1 Tails Rolling-Airborne Early Landing (Trace Frame 2150)](#aiz1-tails-rolling-airborne-early-landing-trace-frame-2150)
3. [CNZ1 CPU Tails Ground Over-Deceleration (Trace Frame 318)](#cnz1-cpu-tails-ground-over-deceleration-trace-frame-318)

---

## CNZ1 Miniboss Arena Entry — Music Play-In Missing

**Location:** `Sonic3kCNZEvents.enterMinibossArena()`
**ROM Reference:** `sonic3k.asm:144841` (`moveq #cmd_FadeOut,d0; jsr Play_Music`) plus the boss-music play-in that follows when `Obj_CNZMiniboss` becomes active.

### Symptom

When `Obj_CNZMiniboss` crosses its camera-X gate (`$31E0`), `Sonic3kCNZEvents.enterMinibossArena()` mirrors the ROM's music fade-out via `audio().fadeOutMusic()`, but the miniboss-music play-in is not yet wired. Audio drops to silence between the fade-out and boss defeat instead of switching to the miniboss theme. All other arena-entry effects (camera lock, PLC `0x5D`, `Pal_CNZMiniboss` install, `Boss_flag`, wall-grab suppression) match the ROM bit-for-bit, so visual/gameplay parity is unaffected.

### Current State

The site is marked with an inline `TODO(T12)` comment. Workstream T12 ("CNZ miniboss audio handoff") owns wiring `Sonic3kMusic.MINIBOSS` (or the equivalent S3K music ID) into the existing `audio()` boss-music handoff once the miniboss audio routing lands.

No tests assert on music selection during the miniboss fight, so this gap does not block test coverage elsewhere.

### Removal Condition

Remove once the miniboss theme plays on arena entry and a regression test asserts on the active music ID between fade-out and boss defeat.

---

## AIZ1 Tails Rolling-Airborne Post-Jump y_speed Divergence (Trace Frame 2202)

**Location:** `PlayableSpriteMovement.doJumpHeight` or flight-gravity gate — the exact code path that mishandles Tails's rolling-jump airborne velocity is still open.
**Trace reference:** `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun`, first strict error at frame 2202.

### Status

Frame 2150 was an earlier divergence on a `0xFA` slope zero-distance landing; that was fixed by narrowing the `Sonic3kZoneFeatureProvider.shouldTreatZeroDistanceAirLandingAsGround` angle window to exactly-flat (0x00 or 0xFF) per commit `ad232cf10`. The AIZ replay's first strict error has since moved to frame 2202.

### Symptom

At frame 2202 the engine produces `tails_y_speed = -0x02E0` vs the ROM's expected `-0x03E0` (engine is 0x0100 subpixels less upward than the ROM). Seed state at frame 2201: Tails `y_vel = -0x0418`, rolling+airborne (status 0x07, no rolling-jump bit), jumping flag set from a frame-1947 rolling jump.

The expected result is standard +0x38 air gravity: `-0x0418 + 0x38 = -0x03E0`. The engine is applying an extra ~0x0100 downward velocity somewhere in the air-movement path.

### Suspected Cause

The extra 0x100 matches `Tails_JumpHeight`'s jump-release cap behaviour: ROM `sonic3k.asm:28592` clips `y_vel` to `-0x0400` when `jumping` is set, `y_vel < -0x0400`, and no A/B/C button is *just-pressed* this frame. After the clip, gravity adds +0x38 → `-0x03C8`, still 0x018 above the engine's observed value. The remaining difference (and the 0x100 total gap) likely comes from a layered interaction: CPU-Tails input replay (button-press vs button-held distinction), rolling-airborne animation state, or an air-collision side-effect. Needs instrumentation.

### Removal Condition

Remove once `TestS3kAizTraceReplay`'s first strict error moves past frame 2202, OR a unit test pins the exact per-frame y_speed divergence for CPU-Tails rolling-airborne and a fix lands.

---

## CNZ1 CPU Tails Ground Over-Deceleration (Trace Frame 318)

**Location:** Unknown — ground physics, input-cap interaction, or slope handling. Not the Tails CPU follow AI input override (that threshold was fixed in the sidekick-follow-snap-threshold work).
**Trace reference:** `src/test/resources/traces/s3k/cnz`, first strict error at frame 318.

### Symptom

`TestS3kCnzTraceReplay.replayMatchesTrace` reports the first strict error at trace frame 318:

- Frame 317: Tails at x=0x0406, g_speed=0x01A9, moving right, status=0x00 (direction=right, on ground). Sonic is at x=0x03BE, to the LEFT of Tails by 72 pixels.
- Frame 318 ROM: g_speed=0x0192 (−0x17 from F317, mild friction consistent with pressing LEFT against +g_speed).
- Frame 318 engine: g_speed=0x0092 (−0x117 from F317 — 0x100 extra deceleration).

### Suspected Cause

With `|dx|=72 >= 0x30` (S3K sidekick-follow-snap threshold), both the ROM and the engine's follow AI correctly force the LEFT press. The extra 0x100 must come from somewhere in the engine's deceleration chain. Candidate causes:
- `inputAlwaysCapsGroundSpeed = false` for S3K may not be applied correctly to CPU-controlled Tails (double cap)
- Unexpected skid penalty on top of the normal deceleration
- Slope-angle mismatch contributing an extra slopeRunning/slopeRolling term

### Removal Condition

Remove once `TestS3kCnzTraceReplay`'s first strict error moves past frame 318 AND the root cause is either fixed (entry deleted) or documented as intentional (moved to `S3K_KNOWN_DISCREPANCIES.md`).
