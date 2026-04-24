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

## AIZ1 Tails Rolling-Airborne Early Landing (Trace Frame 2150)

**Location:** Sensor/collision path for airborne Tails, likely `PlayableSpriteMovement.calculateLanding()` or `GroundSensor`.
**Trace reference:** `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun`, first strict error at frame 2150.

### Symptom

`TestS3kAizTraceReplay.replayMatchesTrace` reports the first strict error at trace frame 2150:

- Frame 2149 (both ROM and engine): Tails at `(0x1892, 0x0456)`, vel `(0xFC08, 0x0860)`, air=1, status=0x07 (rolling+air+direction-left)
- Frame 2150 ROM: still airborne — `(0x188E, 0x045F)`, vel `(0xFC08, 0x0898)`, air=1, status=0x07
- Frame 2150 engine: already landed — `tails_y_speed=0x0000`, `tails_g_speed=-0x03F8`, `tails_angle=0xFA`, `tails_air=0`

The engine detects floor contact one frame earlier than the ROM on a `0xFA` slope.

### Suspected Cause

Terrain-sensor or y-radius divergence for airborne+rolling Tails, not a CPU-AI gap. Candidate causes:
- Engine uses a different y_radius for rolling-airborne Tails than the ROM's `Tails_DoLevelCollision` path
- Subpixel accumulator rounds differently between engine and ROM
- Slope-angle probe reads a different chunk or block

### Removal Condition

Remove once `TestS3kAizTraceReplay`'s first strict error moves past frame 2150, OR a separate unit test pins the exact sensor/radius divergence and a fix lands with its own entry in the discrepancies file (if the new behavior is intentional) or this entry's removal (if it's a true parity fix).

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
