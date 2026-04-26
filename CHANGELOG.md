# Changelog

All notable changes to the OpenGGF project are documented in this file.

## Unreleased

### Sonic 3&K AIZ Trace F6255: Tails Freed-Slot Despawn Resolved (RESOLVED)

- `ObjectManager.processInlineObjectForPlayer` now exempts top-only
  solid providers from the `solidObjectOffscreenGate`. ROM's gate at
  `loc_1DF88` (sonic3k.asm:41390-41392) lives only in
  `SolidObjectFull_1P` (sonic3k.asm:41016-41018); the top-only routines
  `SolidObjectTop_1P` / `SolidObjectTopSloped_1P` /
  `SolidObjectTopSloped2_1P` (sonic3k.asm:41793, 41887, 41840) all
  branch directly into the bbox-relative `loc_1E42E` /
  `SolidObjCheckSloped` / `SolidObjCheckSloped2` (sonic3k.asm:41982,
  42095, 42071) without testing `render_flags(a0)`. The S2 sloped path
  (`SlopedSolid_SingleCharacter` -> `SlopedSolid_cont`,
  s2.asm:34927-34952, 35066) bypasses the on-screen test the same way.
  Without the exemption the universal off-screen gate dropped Tails'
  contact with the AIZ2 collapsing platform at (0x08B0, 0x0369) -- the
  platform's bbox right edge (0x90C) sat 0xD5 px past the camera left
  edge (0x985) at the moment Tails should land -- so
  `setLatchedSolidObject(slot=16)` never fired and the freed-slot
  despawn detection had no `lastRidingInstance` to compare against.
- `Sonic3kCollapsingPlatformObjectInstance.spriteOnScreenTestPasses`
  now reads the camera position directly via `services().camera()`
  instead of consulting a per-instance `previousFrameCameraX` cache.
  Round 13's cache was based on the mistaken premise that ROM
  `Load_Sprites` runs AFTER `Process_Sprites`; the actual order in the
  level main loop (sonic3k.asm:7893 `jsr Load_Sprites`; 7894 `jsr
  Process_Sprites`; 7897 `jsr DeformBgLayer`) is Load_Sprites ->
  Process_Sprites -> DeformBgLayer, so `Camera_X_pos_coarse_back` at
  frame N's `Process_Sprites` already reflects `Camera_X_pos` at the
  start of frame N (= end of frame N-1, since `DeformBgLayer` is the
  per-frame camera tracker). The engine's `LevelFrameStep` mirrors the
  same ordering by-construction (object exec is step 4, camera tracking
  is step 5), so a direct camera read at the platform's `update()`
  already provides the correct ROM-equivalent value. The cache pulled
  cam_X from too far in the past and let the platform's destruction
  lag ROM by one frame, which in turn delayed the AIZ trace F6255
  freed-slot despawn by one frame.
- Effect on `TestS3kAizTraceReplay`: first strict error advances from
  F6255 to F6313 (a downstream sidekick AI divergence). Total errors
  hold steady (1960 vs 1959). Cross-game baselines unchanged: S1 GHZ
  green, S1 MZ1 F311, S2 EHZ F1151, S3K CNZ F3649. S3K must-keep-green
  tests stay green (`TestS3kAiz1SkipHeadless`,
  `TestSonic3kLevelLoading`, `TestSonic3kBootstrapResolver`,
  `TestSonic3kDecodingUtils`, `TestS3kCollapsingBridgeParity`).

### Sonic 3&K AIZ Trace F6255: Collapsing Platform Off-Screen Lifecycle ROM-Aligned (PARTIAL)

- Aligned `Sonic3kCollapsingPlatformObjectInstance`'s off-screen delete
  with ROM `Sprite_OnScreen_Test` (sonic3k.asm:37262). ROM reads
  `Camera_X_pos_coarse_back`, which `Load_Sprites` (sonic3k.asm:37545
  `loc_1B7F2`) updates AFTER `Process_Sprites` each frame, so the value
  used during a given frame's object pass reflects the camera's X at the
  END of the previous frame. The engine's
  `ObjectManager.unloadCounterBasedOutOfRange` (S3K runExecLoop after-
  update path) reproduces the formula but feeds the CURRENT frame's
  `cameraX`, which collapsed the platform one frame earlier than ROM
  and prevented the F6255 freed-slot despawn analog from firing.
- The platform now opts out of `unloadCounterBasedOutOfRange` via
  `isPersistent()=true` and runs an in-instance
  `spriteOnScreenTestPasses()` that consults a per-instance
  `previousFrameCameraX` cache (the camera_x observed at the end of the
  prior `update()`). Distance threshold $280, unsigned 16-bit wrap;
  exactly matches ROM. Runs in states 0/1/2 mirroring `loc_20594` /
  `loc_205DE` calling `sub_205B6` -> `Sprite_OnScreen_Test` every frame
  (sonic3k.asm:44814, 44830, 44851, 37262). State 3 keeps the existing
  `isOnScreen(128)` matching `loc_20620` (sonic3k.asm:44879).
- Effect on `TestS3kAizTraceReplay`: error count 6782 -> 1959 (-71%),
  warning count 5773 -> 2034 (-65%). Platform now reaches
  `setDestroyed` at gfc=0x1746 / F6254, matching ROM's
  `Delete_Current_Sprite` event exactly.
- Architectural blocker still open: AIZ first error remains F6255.
  Tails never lands on platform x=0x08B0 in the engine's solid-contact
  framework. ROM transitions Tails from terrain to platform at F6251
  (sk_y 0x033F -> 0x033A, status 0x00 -> 0x08); the engine keeps her on
  terrain and never fires `onSolidContact(standing=true)` for slot 16.
  Without `setLatchedSolidObject(slot=16)` firing, the freed-slot
  detection in `SidekickCpuController.checkDespawn` has no
  `lastRidingInstance` to compare against. See `docs/S3K_KNOWN_BUGS.md`
  "AIZ Trace F6255" entry for the next investigation steps
  (terrain-vs-object handover at the slope-data top surface).
- Cross-game baselines unchanged: S1 GHZ green, S1 MZ1 F311, S2 EHZ
  F1151, S3K CNZ F3649 (post-cnz-merge). Required-green S3K tests stay
  green (`TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`,
  `TestSonic3kBootstrapResolver`, `TestSonic3kDecodingUtils`).

### Sonic 3&K CNZ Trace F2262 → F3649: Wire-Cage Reap-By-Camera-Distance Despawn Trigger

- **Fix:** `CnzWireCageObjectInstance.onUnload()` now flips the cage's
  destroyed flag when ObjectManager reaps it past the coarse-back chunk
  boundary (`unloadCounterBasedOutOfRange`). ROM `Obj_CNZWireCage`
  ends with `jmp (Delete_Sprite_If_Not_In_Range).l`
  (sonic3k.asm:69867 → 37301-37317 → `Delete_Current_Sprite` at
  sonic3k.asm:36108) which zeroes the cage's entire SST. The next
  frame's `sub_13EFC` (sonic3k.asm:26816) reads `(a3) = 0`, fails the
  cached `Tails_CPU_interact` compare at sonic3k.asm:26824, and falls
  into `sub_13ECA` (sonic3k.asm:26800) which warps Tails to the
  despawn marker `(0x7F00, 0)`.
- The engine cage's lifecycle was using ObjectManager's reap-by-camera-
  distance path which removes from active list but didn't flip
  `destroyed`. Round 12's freed-slot detection in
  `SidekickCpuController.checkDespawn()` (gated by
  `PhysicsFeatureSet.sidekickDespawnUsesRidingInstanceLoss`) checks
  `currentRidingInstance.isDestroyed()`, so without the destroyed flag
  the loss check never fired and Tails stayed latched to the stale
  cage instance.
- `TestS3kCnzTraceReplay` first strict error advances **F2262 →
  F3649** (~1387 frames). The new error is at F3649 (`tails_x_speed
  mismatch expected=-0x0A00 actual=-0x0060`) — a separate concern for
  the next iteration.
- Cross-game baselines unchanged: S1 GHZ PASS, S1 MZ1 F311, S2 EHZ
  F1151, S3K AIZ F6255.
- The cnz-f2262-flight-timer agent's mission hypothesis ("engine
  resets despawnCounter on any on-screen detection while ROM checks
  render_flags bit 7") turned out to be wrong — `Tails_CPU_flight_timer`
  was only 49 at F2261, nowhere near the 5*60=300 timeout. The despawn
  fired via the `cmp.w (a3),d0` zero-path, not the timer-overflow path.
  No new `PhysicsFeatureSet` flag was needed.

### Sonic 3&K AIZ Trace F6255: Sidekick CPU Freed-Slot Despawn Infrastructure (PARTIAL)

- Added engine analog of ROM `Tails_CPU_interact` (sonic3k.asm:26823)
  via `AbstractPlayableSprite.latchedSolidObjectInstance` —
  `ObjectInstance` reference tracking the live object the sidekick is
  latched onto, paralleling the existing 8-bit
  `latchedSolidObjectId` byte. The instance is set whenever the
  SolidObject framework binds a contact (`ObjectManager.SolidContacts`
  inline paths) and by latch-and-own controllers
  (`CnzBarberPoleObjectInstance`, `CnzWireCageObjectInstance`). It
  auto-clears when `setLatchedSolidObjectId(0)` is called. The
  `setLatchedSolidObject(int, ObjectInstance)` convenience setter binds
  both atomically.
- Added `SidekickCpuController.lastRidingInstance` per-frame cache and
  the freed-slot despawn analog of ROM `sub_13EFC`
  (sonic3k.asm:26816-26847). When off-screen + on-object and the cached
  instance is no longer alive (null, replaced, or `isDestroyed()`),
  triggers `triggerDespawn(OBJECT_ID_MISMATCH)` — mirroring ROM's
  `cmp.w (a3),d0` mismatch on a slot freed by
  `Delete_Referenced_Sprite` (sonic3k.asm:36116).
- Added `PhysicsFeatureSet.sidekickDespawnUsesRidingInstanceLoss` flag
  to gate the new path. S3K=true, S2=false (8-bit-id mismatch path
  already covers the freed-slot case for S2's `cmp.b id(a3),d0` because
  id of a freed slot is also 0), S1=false (no Tails CPU).
- Architectural blocker documented in `docs/S3K_KNOWN_BUGS.md`: the
  AIZ collapsing platform's lifecycle desynchronises from ROM under
  the trace replay's per-frame sidekick re-seed, so the engine's
  platform never reaches `isDestroyed()` at the same frame ROM
  deletes its slot. Without that timing match, the new despawn path
  is correctly wired but doesn't fire at F6255. F6255 fix requires
  resolving the deeper platform-timing divergence.
- Test result: TestS3kAizTraceReplay#replayMatchesTrace first strict
  error UNCHANGED at F6255 (6782 errors, 5773 warnings). Cross-game
  baselines stay green/unchanged: S1 GHZ PASS, S1 MZ1 F311, S2 EHZ
  F1151, S3K CNZ F2262 (post-cnz-merge). TestSidekickCpuDespawnParity
  (4 tests) and TestHybridPhysicsFeatureSet (10 tests) all pass.

### Sonic 3&K CNZ F2222 — Wire Cage Sidekick Spurious Release Fix (v6.3-s3k Recorder + ROM-Side Memoryexecute Hook Diagnostics)

- **Fix:** `CnzWireCageObjectInstance` now tracks whether the leader has
  released this cage and gates the sidekick's cooldown-branch input
  release on that flag. Once the leader has released, the sidekick's
  `continueRide` short-circuits with object-control flags preserved,
  matching ROM's stuck-frozen state. Cf. `sonic3k.asm:69873-69897`.
  Root cause: ROM cage's `sub_338C4` uses register `d6` as the
  player-standing bit position. With `FixBugs` disabled (the original
  ROM), `Obj_CNZWireCage` does
  `addq.b #p2_standing_bit-p1_standing_bit,d6` (sonic3k.asm:69843)
  rather than re-loading `d6` cleanly, so the d6 used for the
  Player_2 call to `sub_338C4` carries whatever value `d6` had after
  the Player_1 call's `Perform_Player_DPLC`. While the leader is
  actively rotating in `loc_33A6A` / `loc_33BAA`,
  `Perform_Player_DPLC` runs and corrupts `d6` to 0; the
  `addq.b #1,d6` then makes `d6 = 1` for Tails so
  `bset d6,status(a0)` in `sub_33C34` (sonic3k.asm:70181) sets bit 1
  of the cage's status rather than `p2_standing_bit = 4`. Once the
  leader has released the cage, `Perform_Player_DPLC` no longer runs
  from the cage path so `d6 = 3` (clean) and the
  `addq.b #1,d6` produces the correct `d6 = 4`. The cage's
  `btst d6,status(a0)` test now reads bit 4 of the cage status,
  which is clear because the original capture stored Tails at bit 1
  (the corrupted-d6 value). Net effect: ROM cage falls through to
  the capture-attempt path each frame and exits at
  `tst.b object_control(a1)` because Tails's `object_control = 0x43`
  remains from the `loc_3397A` capture markers. ROM Tails stays
  frozen at the capture position until `Tails_CPU_flight_timer`
  reaches the despawn threshold. Engine previously fired the cage's
  jump-release on the sidekick's first auto-jump press because the
  cage's `state.latched` for Tails was still true and `continueRide`
  ran the cooldown-branch input check. CNZ1 trace `replayMatchesTrace`
  first error advances F2222 → F2262 (40 frames; new error is the
  `Tails_CPU` despawn warp that the engine doesn't yet trigger
  in-sync with ROM).
- **Recorder v6.3-s3k.** Bumped `lua_script_version` to `6.3-s3k` and
  added two diagnostic-only aux event types: `cage_state` (one event
  per active CNZ wire cage object per frame, including the cage
  status byte and both per-player phase/state bytes from
  `$30/$31/$34/$35`) and `cage_execution` (one summary event per
  frame containing the M68K execution-hook hits inside the cage's
  per-player handler `sub_338C4` plus the released-mode branches
  `loc_339A0`, `loc_33ADE`, `loc_33B1E`, `loc_33B62`). Each hit
  records the entry-point register state (`a0/a1/a2/d5/d6`), the
  per-player state byte at `1(a2)`, the player's status and
  `object_control` bytes, and the cage's status byte. Used to
  pinpoint which of the cage's branches the M68K CPU actually
  executes for each player on each frame, which was load-bearing
  to root-cause the d6-corruption-by-`Perform_Player_DPLC` bug
  documented above.
- **Recorder bug fix.** The v6.2 `interact_state` event read
  `mainmemory.read_u8(PLAYER_BASE + 0x2A)` and labelled the result
  `object_control`. Offset `0x2A` is `status` (per
  `sonic3k.constants.asm:30`); the real `object_control` byte is at
  `0x2E` (`sonic3k.constants.asm:57`). The v6.3 emission now
  records `status`, `status_secondary`, and `object_control` as
  three separate JSON fields. The `TraceEvent.InteractState` parser
  on the engine side preserves the existing field for the rare
  consumers that already keyed off the (mislabelled) byte and adds
  fields for the corrected ones.
- Cross-game baselines unchanged: S1 GHZ PASS, S1 MZ1 F311, S2 EHZ
  F1151, S3K AIZ F6255.

### Sonic 3&K CNZ Trace v6.2-s3k Regeneration & F2222 Architectural Blocker Documentation

- Regenerated `src/test/resources/traces/s3k/cnz` with the v6.2-s3k
  recorder. The new aux track adds `cpu_state_per_frame`,
  `oscillation_state_per_frame`, `object_state_per_frame`, and
  `interact_state_per_frame` events (cf. `aux_schema_extras`), bringing
  CNZ to the same diagnostic depth that the v6.2 recorder already
  produces for AIZ. CSV `physics.csv` itself is unchanged.
- New aux events expose Tails `Tails_CPU_routine`,
  `Ctrl_2_held_logical`, `Ctrl_2_pressed_logical`, plus per-frame
  `interact_slot` and player object-control byte. They are diagnostic
  only — the trace replay test does NOT hydrate engine state from
  these events.
- Documented `CNZ1 Trace F2222 — Wire Cage Sidekick JUMP_RELEASE
  Spurious Fire (OPEN)` in `docs/S3K_KNOWN_BUGS.md`. After the F2175
  fix the same `tails_y_speed=-0x200` JUMP_RELEASE signature reappears
  47 frames later. Root cause analysis trace by trace plus v6.2 aux
  data narrows the divergence to the cage's `andi.w
  #button_A_mask|button_B_mask|button_C_mask, d5` check at
  `sonic3k.asm:70055`: the v6.2 aux at F2221 reports
  `ctrl2_pressed=0x48` (button_A pressed bit 0x40 SET) which by my
  reading should make the cage release Tails — yet ROM definitively
  does not release at F2221 either. The unresolved mystery (engine
  fires release one frame late, ROM does not fire at all) needs
  ROM-side instrumentation to confirm cage execution path; entry in
  `S3K_KNOWN_BUGS.md` enumerates the candidate gaps and the recorder
  bug it surfaced (`s3k_trace_recorder.lua` writes `status` byte from
  player offset `0x2A` while labelling the field `object_control` —
  the actual `object_control` field is at offset `0x2E`).
- Test result: TestS3kCnzTraceReplay#replayMatchesTrace first strict
  error UNCHANGED at F2222 (5047 errors, 5217 warnings). Cross-game
  baselines stay green: S1 GHZ PASS, S1 MZ1 F311, S2 EHZ F1151, S3K
  AIZ F6255.

### Sonic 3&K AIZ Trace F6066: Gate CaterKillerJr Logic on Obj_WaitOffscreen Visibility

- Fixed `CaterkillerJrHeadInstance.update()` to early-return until the
  badnik is on-screen via `isOnScreenX()`. ROM `Obj_CaterKillerJr`
  (`sonic3k.asm:183317-183323`) begins with
  `jsr (Obj_WaitOffscreen).l`. `Obj_WaitOffscreen`
  (`sonic3k.asm:180266-180297`) replaces the object's code pointer with
  `loc_85AD2` until the on-screen render-flag bit is set, which only
  happens once the camera reaches the spawn x. The chunk-based placement
  cursor allocates the badnik's slot ~40 frames before the camera
  reaches its spawn x, so the engine was running the swing/move cycle
  during that pre-roll, which carried CaterKillerJr ~41 px further left
  than ROM by the time Sonic encountered it (AIZ2 narrow corridor at
  spawn x=0x0850).
- Engine-side stack: `ObjectManager.runTouchResponsesForPlayer →
  TouchResponses.processCollisionLoop → handleTouchResponse → applyHurt`
  fired with `sourceX=0x07EF` against Sonic at `cx=0x07DF cy=0x033A`,
  producing the exact `applyHurt` knockback signature
  (`x_speed=-0x200, y_speed=-0x400`, `air=1`, `status=0x06`). ROM at the
  same gfc had Sonic running on the ground at `g_speed=-0x98` because
  the CaterKillerJr was 0x29 (~41) pixels further right and never
  contacted the player.
- Other AIZ badniks already had this guard
  (`BlastoidBadnikInstance`, `BuggernautBadnikInstance`,
  `MonkeyDudeBadnikInstance`, `BatbotBadnikInstance`,
  `TunnelbotBadnikInstance`); CaterKillerJr was the missing case.
- TestS3kAizTraceReplay#replayMatchesTrace: first error advances from
  F6066 (1178 errors / 1609 warnings) to F6255 (Tails despawn handoff).
- Cross-game baselines unchanged: S1 GHZ PASS, S1 MZ1 F311, S2 EHZ
  F1151, S3K CNZ F2222.

### Sonic 3&K CNZ Trace F2175: Preserve OnObject Across Frame Boundary For Latch-And-Own Controllers

- Fixed `ObjectManager.SolidContacts.finalizeInlinePlayer()` to skip the
  end-of-frame `setOnObject(false)` clear when the player has a non-zero
  `latchedSolidObjectId`. Latch-and-own controllers
  (`CnzWireCageObjectInstance`, `CnzBarberPoleObjectInstance`) own the
  player via `setLatchedSolidObjectId(spawnId)` rather than registering
  with `SolidContacts.ridingStates`, so without this gate
  `inlineSupportedPlayers` never contains the player and the OnObject
  flag was being torn down every frame.
- ROM behaviour: `sub_33C34` at `sonic3k.asm:70179` `bset
  #Status_OnObj,status(a1)` is sticky; the cage clears the bit only at
  `loc_33A0E` line 69989 `bclr #Status_OnObj,status(a1)` when its own
  release path runs.
- Without the fix, the sidekick CPU controller (`sonic3k.asm:26690
  loc_13DA6`) read `Sonic.Status_OnObj=false` and applied `leadOffset =
  0x20`, dropping `dx` from 71 to 46 and tripping the auto-jump
  trigger that prematurely released Tails from the cage with `y_speed
  = -0x200`.
- TestS3kCnzTraceReplay#replayMatchesTrace: first error advances from
  F2175 (5055 errors / 5215 warnings) to F2222 (5047 errors / 5217
  warnings).
- Cross-game baselines unchanged: S1 GHZ PASS, S1 MZ1 F311, S2 EHZ
  F1151, S3K AIZ F6066.

### Sonic 3&K AIZ Trace F5736: Tick Level_frame_counter Across Seamless Act Reload

- Fixed `LevelManager.applySeamlessTransition()` to bump
  `SpriteManager.frameCounter` by 1 on RELOAD_SAME_LEVEL and
  RELOAD_TARGET_LEVEL transitions. ROM `Level_frame_counter`
  (incremented in `VInt_0_Main` every gameplay frame) keeps ticking
  through the act-reload frame, but both `GameLoop` and
  `HeadlessTestRunner` `return` after applying a seamless transition,
  skipping `SpriteManager.update()` — the only path that increments
  `SpriteManager.frameCounter`. After AIZ act 1 → act 2 reload (F5496)
  the engine counter ran one frame behind ROM's, so Tails-CPU gates
  reading `(Level_frame_counter & MASK)` —
  `sonic3k.asm:26775 loc_13E9C` 64-frame jump cadence — fired one frame
  off the ROM cadence.
- AIZ trace F5736 ROM `Level_frame_counter = 0x1540`, `& 0x3F == 0`,
  triggers Tails's auto-jump out of his pushing-stuck state
  (`y_speed = -0x680`, `x_speed = -0x00C4`). Engine `frameCounter =
  0x153F`, `& 0x3F == 0x3F`, gate stayed shut, Tails remained grounded
  with `x_speed = 0x000C`.
- The fix is restricted to RELOAD types. `MUTATE_ONLY` transitions
  (e.g. AIZ1 fire-transition art overlay) execute mid-frame and do not
  skip the rest of the gameplay loop, so they must not double-tick the
  counter.
- TestS3kAizTraceReplay#replayMatchesTrace: first error advances
  F5736 → F6066 (1184 → 1178 errors).
- Cross-game baselines unchanged: S1 GHZ PASS, S1 MZ1 F311, S2 EHZ
  F1151, S3K CNZ F2222.

### Sonic 3&K AIZ1 Trace F5497: Refresh Sidekick CPU Bounds on Act Transition

- Fixed `LevelManager.executeActTransition()` to refresh
  `SidekickCpuController.minXBound/maxXBound/maxYBound` to the new
  camera bounds after `restoreCameraBoundsForCurrentLevel()`. The CPU
  bound overrides are populated by per-zone event handlers (e.g.
  `Sonic3kAIZEvents` boss arena lock at AIZ1 stage) and refreshed each
  frame by `Sonic3kLevelEventManager.syncSidekickBoundsToCamera()` at
  the END of `update()`. Without an in-transition resync, the next
  frame's `PlayableSpriteMovement.doLevelBoundary` reads stale AIZ1
  boss-arena bounds and clamps the post-transition Tails to that
  arena's left edge, teleporting Tails across the AIZ2 reload offset.
- ROM context: `sonic3k.asm:104722-104771` (`AIZ1BGE_Finish`) resets
  `Camera_min_X_pos` / `Camera_min_Y_pos` (lines 104758-104762) as part
  of the act 2 reload. ROM Tails reads those camera fields directly —
  there is no separate Tails-CPU bounds storage in the ROM, so the
  engine's mirror must be refreshed alongside the camera reset.
- AIZ trace F5497 was: stale `minXBound = 0x2F10` produced
  `leftBoundary = 0x2F20`; `predictedX = 0x00B2 < 0x2F20`, so
  `doLevelBoundary` clamped Tails to `0x2F20`. With the fix
  `minXBound = 0x10` (AIZ2 camera min) and Tails stays at the post-
  offset 0x00B1.
- TestS3kAizTraceReplay#replayMatchesTrace: first error advances
  F5497 → F5736 (1185 → 1184 errors).
- Cross-game baselines unchanged: S1 GHZ PASS, S1 MZ1 F311, S2 EHZ
  F1151, S3K CNZ F2175.

### Sonic 3&K AIZ1 Trace F4679: Sidekick Level-Boundary Kill Velocity Zeroing

- Fixed `SidekickCpuController.despawn()` to model ROM's two-phase
  `Player_LevelBound -> Kill_Character -> sub_13ECA` sequence when the
  sidekick crosses the bottom kill plane. ROM `Kill_Character`
  (sonic3k.asm:21136-21151) zeros `x_vel`/`y_vel`/`ground_vel` and sets
  `routine = 6` on Frame N; the next frame's death routine `loc_1578E`
  (sonic3k.asm:29263) calls `sub_123C2 -> sub_13ECA` to warp to the
  despawn marker `(0x7F00, 0)`, then post-warp `MoveSprite_TestGravity`
  adds `+0x38` gravity to the freshly-zeroed `y_vel`. Engine collapsed
  both phases into a single instant: `triggerDespawn()` warped to the
  marker AND skipped the velocity zeroing, leaving Tails carrying live
  physics velocities through the warp.
- Fix introduces a `DespawnCause` enum (`LEVEL_BOUNDARY`,
  `OFF_SCREEN_TIMEOUT`, `OBJECT_ID_MISMATCH`, `EXPLICIT`) and a new
  `State.DEAD_FALLING` engine state. `LEVEL_BOUNDARY` despawns route
  through `beginLevelBoundaryKill()` (mirrors `Kill_Character`: zero
  velocities, clear roll/push/rolljump, transition to `DEAD_FALLING`
  without warping). The next frame's `updateDeadFalling()` runs the
  `sub_13ECA + MoveSprite_TestGravity` equivalent (warp + +0x38
  y_speed write). Other despawn causes go straight to
  `applyDespawnMarker()` which preserves velocities (matching ROM
  `sub_13ECA` exactly — sonic3k.asm:26800-26809 only writes pos/routine/
  status, not velocities).
- Critical edge case caught during fix: AIZ trace F2405 exercises the
  OFF_SCREEN_TIMEOUT path (sub_13EFC's flight_timer hits 5*60), where
  ROM PRESERVES velocities through the marker warp. Initial fix that
  also zeroed velocities in `applyDespawnMarker()` broke F2405; final
  fix only zeros in `beginLevelBoundaryKill()`.
- AIZ trace first strict error advances F4679 -> F5497 (1190 -> 1185
  errors). Cross-game baselines unchanged: S1 GHZ PASS, S1 MZ1 F311,
  S2 EHZ F1151, S3K CNZ F2175.

### Sonic 3&K CNZ Wire Cage Release Radii + Landing Reset Fix

- `CnzWireCageObjectInstance.release` now applies `(x_radius=9, y_radius=19)`
  via `applyCustomRadii` instead of `restoreDefaultRadii`. Mirrors ROM
  `Obj_CNZWireCage` release paths `loc_33A0E`/`loc_33B62`
  (`sonic3k.asm:69986-69987` / `70095-70096`), which hardcode `move.b
  #$13,y_radius(a1) / move.b #9,x_radius(a1)` regardless of character.
  Tails default standing y_radius is `$F` (15), but ROM cage release
  writes Sonic-style 19/9 — required for the post-launch terrain
  landing detection to match ROM at CNZ1 trace F1815.
- `PlayableSpriteMovement.resetOnFloor` now restores standing
  collision radii when the sprite is not rolling and its
  `x/yRadius` differs from stand defaults. Matches ROM
  `Player_TouchFloor` (`sonic3k.asm:24341-24343` Sonic /
  `29134-29136` Tails), which unconditionally writes
  `default_y_radius`/`default_x_radius` before the `Status_Roll`
  branch. The roll branch then only adjusts `y_pos`. Previously the
  engine only reset radii via `setRolling(false)`, leaving Tails with
  a Sonic-sized hitbox forever after a CNZ wire-cage release and
  accumulating a 4-pixel y_pos drift over hundreds of frames
  (CNZ1 F2030+).
- TestS3kCnzTraceReplay first error advances 1815 → 2175
  (+360 frames). Cross-game baselines unchanged.

### Sonic 3&K AIZ1 Resize Timing Fix

- Fixed `Sonic3kAIZEvents.updateAct1` to scan the AIZ1 dynamic-resize table
  using the predicted end-of-frame camera X (`Camera.previewNextX()`) instead
  of the previous frame's `Camera.getX()`. ROM: `Do_ResizeEvents` runs inside
  `DeformBgLayer` (sonic3k.asm:38303-38316) AFTER `MoveCameraX`/`MoveCameraY`
  have committed the new `Camera_X_pos`, so the resize scan sees the new
  camera X on the same trace frame. The engine's `LevelFrameStep` runs events
  (step 4) before the camera step (step 5), so the previous-frame camera X
  caused `Camera_max_Y_pos` updates to lag by one frame. At AIZ1's cam_x ==
  $2D80 boundary (resize_table drop from $0390 to $02E0), this delayed the
  sidekick kill-plane fire by one frame, leaving Tails alive for an extra
  frame after ROM had already triggered `Kill_Character`. AIZ trace replay
  first-error frame 4679 advances from `tails_x_speed` to `tails_y_speed`
  (Tails now despawns on the correct frame; remaining divergence is the
  sidekick despawn-vs-kill_character semantic gap).

### Sonic 3&K CNZ Trace F1815 Probe: `cnz.collisionprobe` Diagnostic

- Added a permanent debug-only collision probe for the CNZ F1815 Tails
  landing divergence, gated behind `-Dcnz.collisionprobe=true` (or
  `-Ds3k.cnz.collisionprobe=true`). Modeled on the existing
  `-Ds3k.aiz.aircollisionprobe`. Fires only when the player is inside
  the F1815 region (X 0x1200..0x1300, Y 0x0680..0x0780). Zero overhead
  when disabled.
- Probe coverage spans `PlayableSpriteMovement.handleMovement` entry,
  mode dispatch, and `doLevelCollision` entry; `CollisionSystem`
  per-quadrant air-collision results plus `landOnFloor` pre/post; and
  `GroundSensor.scanVertical` first-pass and extension-pass tile
  lookups (chunk descriptor, block index, primary/secondary collision
  mode, solidity bit, tile index, metric).
- The probe revealed that the engine's loaded chunk descriptor at
  block 159 tile (4,3) is `0x02CD` with `primMode=NO_COLLISION` and
  `secMode=NO_COLLISION`, so terrain probe correctly returns
  `d1=+1`. However, the ROM trace's `y_sub` at F1815 (`0xC000`) is
  mathematically consistent with `add.w -3, y_pos` after MoveSprite
  advanced y_pos by `0x308` to `0x072F`, indicating ROM detected
  `d1=-3` at the same position. The mismatch points to a
  block-loading or ChunkDesc-decoding bug in
  `Sonic3kLevel.loadBlocksWithPlan` /
  `LevelDataFactory.chunksFromSegaByteArray` /
  `ChunkDesc.updateFields`, or to a layout-pointer indexing issue.
  No engine-state changes; CNZ baseline F1815 unchanged.

### Sonic 3&K AIZ Trace F2202 Fix: Permanent Destroyed-Badnik Latch (`destroyedInWindow`)

- Fixed `TestS3kAizTraceReplay#replayMatchesTrace` strict divergence at
  frame 2202 (sidekick Tails at `(0x1845, 0x0421)` flying over the spawn
  position of a MonkeyDude that ROM destroyed at F1722 via Sonic). ROM
  `tails_y_speed = -0x3E0` (gravity-only); engine produced `-0x2E0`
  (gravity + spurious `+0x100` enemy-defeat bounce from a phantom
  re-spawned MonkeyDude).
- Root cause: `ObjectManager.Placement.destroyedInWindow` was being
  cleared whenever a destroyed spawn's X position fell outside the live
  placement window (called from `trimLeftCountered`,
  `trimRightCountered`, `trimLeftNonCounter`, `trimRightNonCounter`,
  `refreshWindow`, `refreshCounterBased`, and the post-window-update
  else branch in `update`). ROM's equivalent flag is bit 7 of
  `Object_respawn_table[respawn_index]`, set by the cursor spawn
  helpers (`sonic3k.asm` `loc_1BA40` / `loc_1BA92` / `sub_1BA0C`
  `bset #7,(a3)`) and cleared **only** by the
  `Sprite_OnScreen_Test` / `Delete_Sprite_If_Not_In_Range` /
  `Sprite_CheckDeleteTouch` family (`sonic3k.asm:37271-37388`,
  `bclr #7,(a2)`) when the still-alive object self-destructs.
  After a player kill the badnik becomes `Obj_Explosion` and never
  walks that path, so bit 7 stays set permanently until level init
  wipes the table at `loc_1B784`.
- Removed the `clearDestroyedLatchOutsideWindow` helper and its six
  call sites in `ObjectManager.Placement`. `destroyedInWindow` is no
  longer auto-cleared on window-leave; the new
  `Placement.permanentDestroyLatch` flag (set via
  `ObjectManager.enablePermanentDestroyLatch()` in LevelManager when
  `gameModule.getGameId() == GameId.S3K`) gates whether
  `removeFromActive` sets the latch at all. S3K latches the bit on every
  destroyed spawn and never clears it (matching ROM); S1 and S2 do not
  set the latch -- their ROM only marks respawn-tracked spawns
  (S1 obj-id-byte bit 7 / S2 yWord bit 15, both modeled by the engine's
  `remembered` flag), so non-tracked S1/S2 destroyed spawns continue to
  re-spawn on cursor re-entry as ROM does. The orthogonal `dormant`
  flag still handles the alive-offscreen out-of-range respawn case
  (cleared in `trySpawn` when the cursor re-considers a spawn position).
- Cross-game baselines unchanged: S1 GHZ PASS, S1 MZ1 F311,
  S2 EHZ F1151, S3K CNZ F1815. AIZ trace first-error advances from
  F2202 to F4679 (a separate Tails respawn / position divergence
  unrelated to enemy bounce).

### Sonic 3&K AIZ Trace F3834 Fix: Sidekick Enemy-Bounce Self-Destroy Gating

- Fixed `TestS3kAizTraceReplay#replayMatchesTrace` strict divergence at
  frame 3834 (sidekick Tails airborne+rolling at `(0x26C8, 0x0300)` in
  flight CPU routine 6, attacking a MonkeyDude at `(0x26D8, 0x0308)`).
  ROM `tails_y_speed` transitioned `-0x140 -> -0x008` (= `-0x140 +
  0x100` enemy-defeat bounce + `0x38` gravity); engine produced
  `-0x108` (gravity only).
- Root cause: `ObjectManager.handleTouchResponseSidekick` captured the
  `aoi.isDestroyed()` flag AFTER calling `attackable.onPlayerAttack`,
  which itself runs `defeat(player) -> setDestroyed(true)` on the
  killed badnik. The post-attack check therefore always read `true`
  when the sidekick was the killer, silently skipping her `+/-$100`
  bounce. ROM `Touch_EnemyNormal`
  (`sonic3k.asm:20945-20990`, `s2.asm:84842-84889`,
  `s1disasm/sub ReactToItem.asm:213-232`) sets the destroyed bit AND
  applies the bounce in the SAME function; the skip-when-destroyed
  semantics only apply to a SUBSEQUENT collision pass after `(a1)`
  has been rewritten to `Obj_Explosion`.
- Capture `wasAlreadyDestroyed` from a pre-attack
  `instance.isDestroyed()` read; gate the bounce on that instead. The
  cross-pass skip path (P1 destroys earlier in the same frame -> P2
  skips bounce) is preserved, while the same-player kill correctly
  applies the bounce.
- Cross-game baselines verified: S1 GHZ PASS, S1 MZ1 F311, S2 EHZ
  F1151, S3K CNZ F1815. AIZ trace first-error advances from F3834 to
  F2202 (separate, pre-existing engine respawn-tracking divergence on
  MonkeyDude near AIZ miniboss arena that was previously masked by the
  buggy gating).
- Files modified: `ObjectManager.handleTouchResponseSidekick`.

### Sonic 3&K CNZ Trace F1791 Fix: Tails CPU Auto-Jump Trigger Bit-7 Gate

- Fixed `TestS3kCnzTraceReplay#replayMatchesTrace` first strict
  divergence at frame 1791 (Tails riding the wire cage with
  `tails_x_speed=-0x800`/`tails_y_speed=-0x200` ROM-side, but engine
  Tails staying on the cage with `x_speed=0`).
- The ROM Tails CPU AI's auto-jump trigger (`sonic3k.asm:26775
  loc_13E9C`) writes `Ctrl_2_logical = 0x7878` (jump pressed) on the
  64-frame cadence at this frame, and the cage's `loc_33ADE` reads
  that and launches Tails with `x_vel=-0x800, y_vel=-0x200`. The
  engine was suppressing the trigger because
  `SidekickCpuController.updateNormal()` early-exited on
  `sidekick.isObjectControlled()`, but ROM only suppresses the CPU
  dispatcher when bit 7 of `object_control` is set (`sonic3k.asm:26672
  bmi.w`); the cage uses bits 1+6 (sonic3k.asm:69937-69938) and
  optionally bit 0 (`sonic3k.asm:69921 loc_3394C`) — never bit 7.
- Added `AbstractPlayableSprite.objectControlAllowsCpu` flag (default
  `false` to preserve existing engine behaviour). Bit-7 callers
  (flight, super state, despawn marker, debug) leave the flag at the
  default; bits-0-6 callers (CNZ wire cage, MGZ twisting loop, etc.)
  set it to `true` when re-asserting `setObjectControlled(true)`.
  `setObjectControlled(false)` clears the flag automatically.
- `SidekickCpuController.updateNormal()` early-exit now tests
  `isObjectControlled() && !isObjectControlAllowsCpu()` to mirror
  ROM's `bmi.w` (sign-bit-only) skip.
- `CnzWireCageObjectInstance` sets the flag in
  `beginLatchedCooldown()` and the two `continueRide()` cooldown
  paths so Tails CPU keeps running while Tails rides the cage.
- First strict error advances F1791 → F1815 (5144 → 5080 errors).
  Cross-game baselines unchanged (S1 GHZ pass, S1 MZ1 F311, S2 EHZ
  F1151, S3K AIZ F3834).

### Sonic 3&K AIZ Trace F2919 Fix: Spring Off-Screen Solid-Contact Bypass

- Fixed `TestS3kAizTraceReplay#replayMatchesTrace` first strict
  divergence at frame 2919 (`tails_x_speed` expected `-0x0A00`,
  actual `0x036C`). The horizontal yellow spring at
  `(0x1F39, 0x04A0)` sits ~0xAA px below the camera viewport at the
  trigger frame, and the F2667 fix's universal off-screen solid-contact
  gate was suppressing the spring's push-trigger path so Tails kept
  her ground velocity instead of being launched.
- Added `SolidObjectProvider.bypassesOffscreenSolidGate()` (default
  `false`) and overrode it to `true` on the S3K and S2 spring
  instance classes, mirroring the ROM divergence between
  `SolidObjectFull_1P` (`sonic3k.asm:41016-41018`, has the
  `loc_1DF88` gate) and `SolidObjectFull2_1P`
  (`sonic3k.asm:41065-41067`, falls through directly to
  `SolidObject_cont` without the gate). Every `Obj_Spring` variant
  routes through `SolidObjectFull2_1P`
  (`sonic3k.asm:47664/47673/47692/47701/47779/47798/47829/47848/`
  `48036/48045/48064/48074`); S2 mirrors with
  `SolidObject_Always_SingleCharacter` (`s2.asm:34873-34875`).
- `ObjectManager.SolidContacts.processInlineObjectForPlayer` now
  consults the new flag before applying the off-screen gate, so
  spring contact resolution runs even when the camera has scrolled
  past the spring's bounding box.
- AIZ trace F2919 -> F3834. Cross-game baselines unchanged: S1 GHZ
  PASS, S1 MZ1 F311, S2 EHZ F1151, S3K CNZ F1815.

### Sonic 3&K CNZ Trace F1758 Fix: Wire Cage Airborne-Capture object_control Bit 0

- Fixed `TestS3kCnzTraceReplay#replayMatchesTrace` first strict
  divergence at frame 1758 (Tails on cage at angle 0x40 with
  `tails_g_speed`/`tails_y_speed` divergence: engine kept running
  `Player_SlopeResist` adding +0x20 per frame at angle 0x40, while
  ROM's physics dispatch was gated by `object_control` bit 0).
- Added pre-physics state snapshot (`AbstractPlayableSprite.capturePrePhysicsSnapshot`,
  invoked from `PlayableSpriteMovement.handleMovement`) so per-object
  hooks running AFTER physics in the engine's frame order can read
  the air/angle/g_speed state ROM saw when its object loop ran.
- `CnzWireCageObjectInstance.tryLatch` now mirrors ROM `loc_3394C`
  (`sonic3k.asm:69921 bset #0, object_control(a1)`) and sets
  `objectControlled=true` plus cooldown=1 when the player was
  airborne at the start of the frame with angle 0 (or low-speed
  airborne with angle != 0). The engine's `loc_1384A` equivalent
  (`PlayableSpriteMovement.handleMovement` line 240-242) gates the
  whole physics dispatch when `objectControlled` is set, matching
  ROM's `loc_1384A` skip of `Tails_Modes` (`sonic3k.asm:26211`).
- Updated `AbstractTraceReplayTest.applyRecordedFirstSidekickState`
  hydration seam to preserve `objectControlled` when
  `latchedSolidObjectId == CNZ_WIRE_CAGE` (existing seam already
  preserved it for the SPAWNING/despawn-marker case). The trace CSV
  does not capture `object_control` so the per-frame hydration
  was zeroing the bit between cage capture and the next-frame
  physics gate, defeating the engine's bit-0 dispatch suppression.
- First strict error advances F1758 → F1791 (next-frame Tails CPU
  AI auto-jump trigger via `Ctrl_2_logical` — overlaps with AIZ
  F2721 work).

### Sonic 3&K AIZ Trace F2667 Fix: SolidObject_cont On-Screen Gate

- Fixed `TestS3kAizTraceReplay#replayMatchesTrace` first strict
  divergence at frame 2667 (Tails-vs-spike side-push triggered in
  engine where ROM's `SolidObject_cont` skipped the side path due
  to the spike being off-screen left of the camera).
- Added ROM `SolidObject_cont` on-screen gate to
  `ObjectManager.SolidContacts.processInlineObjectForPlayer` (citing
  `s2.asm:35140-35145 SolidObject_OnScreenTest`,
  `sonic3k.asm:41390-41392 loc_1DF88`,
  `s1disasm/_incObj/sub SolidObject.asm:124-126 Solid_ChkEnter`).
  When the gate fires it only clears player push state and exits
  (mirroring ROM `sub_1E0C2` at sonic3k.asm:41528) without zeroing
  ground_vel / x_vel.
- Added `ObjectInstance.isWithinSolidContactBounds()` (default true)
  with `AbstractObjectInstance` override that uses
  `cameraBounds.contains(getX(), getY(), 16)` to mirror ROM
  Render_Sprites bit-7 semantics (16-px margin matches typical
  `width_pixels`).
- Added `PhysicsFeatureSet.solidObjectOffscreenGate` flag (S3K=true,
  S1/S2=false for now) so the gate rolls out incrementally.
- AIZ trace replay first error advanced from F2667 (1633 errors) to
  F2721 (1638 errors). The new F2721 first-error is a Tails CPU AI
  jump-trigger divergence, separate from the on-screen gate fix.
- S1 GHZ1 / S1 MZ1 / S2 EHZ1 / S3K CNZ trace baselines unchanged.

### Sonic 3&K Trace Recorder v6.2-s3k (per-frame OST + interact-state snapshots)

- Extended the recorder with two more per-frame aux event types:
  `object_state` (per nearby OST slot within OBJECT_PROXIMITY of
  either player: routine, status byte at $22, subtype at $1C, x/y,
  x_radius/y_radius) and `interact_state` (per player: interact
  field at $42 resolved to slot index, plus object_control byte at
  $2A). These made ROM control-flow at AIZ F2667 directly
  inspectable.
- Bumped recorder version 6.1-s3k -> 6.2-s3k. Added
  `object_state_per_frame` and `interact_state_per_frame` to
  `aux_schema_extras` (additive — keeps `cpu_state_per_frame` and
  `oscillation_state_per_frame` too).
- **Diagnostic only** per the comparison-only invariant: the events
  feed the divergence report and per-frame comparator; they are not
  hydrated into engine state.
- Regenerated `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/`
  with the v6.2-s3k recorder.

### Sonic 3&K Trace Recorder v6.1-s3k (per-frame Oscillating_table snapshots)

- Extended the BizHawk S3K trace recorder with a per-frame
  `oscillation_state` aux event capturing the full $42-byte
  `Oscillating_table` (sonic3k.constants.asm:853 at $FFFFFE6E) plus
  `Level_frame_counter`. This gives trace replay diagnostics direct
  visibility into the ROM's global oscillator phase, used by HoverFan,
  swinging platforms, and other oscillating objects.
- Bumped the recorder version 6.0-s3k -> 6.1-s3k and added
  `oscillation_state_per_frame` to `aux_schema_extras` (additive — keeps
  `cpu_state_per_frame` too). Java parser additions:
  `TraceEvent.OscillationState` record,
  `TraceData.oscillationStateForFrame(frame)` accessor, and
  `TraceMetadata.hasPerFrameOscillationState()` capability check.
- **Diagnostic only**: tests must not hydrate the engine's
  `OscillationManager` from these values; the engine must produce the
  correct oscillator phase natively from the same inputs as the ROM.

### Sonic 3&K CNZ Trace Replay F850 Hover Fan Trigger Fix

- Fixed the long-standing CNZ Hover Fan one-frame trigger lag (engine
  fired one frame later than ROM at every repetition) by correcting the
  recorder's `pre_trace_osc_frames` metadata field to capture
  `Level_frame_counter` at the moment the first physics row is recorded
  rather than at the moment recording arms.
- Root cause: `level_gated_reset_aware` profiles arm and immediately
  return, recording the NEXT BizHawk frame as trace frame 0 — so the lfc
  at the first physics row is `start_gameplay_frame_counter + 1`. The
  engine's seeded-frame-0 mode teleports sprite state to trace frame 0
  without running its own LevelLoop, so the bootstrap's pre-tick advance
  (`for i in preTraceOsc: OscillationManager.update`) needs the lfc at
  trace frame 0, not at arm time. With the corrected value (1 instead
  of 0 for CNZ), the engine `OscillationManager` matches ROM's
  `Oscillating_table` byte-for-byte for the first 16668 frames and the
  Hover Fan triggers on the same frames as ROM (verified via
  `oscillation_state` per-frame snapshots).
- Added diagnostic accessors on `OscillationManager`
  (`snapshotRomFormatBytes`, `controlForTest`, `valuesForTest`,
  `deltasForTest`) so trace replay tests can compare engine state to
  ROM state byte-for-byte without hydration.
- Added a `firstOscDivFrame` reporter in `AbstractTraceReplayTest` that
  prints the first frame where engine `OscillationManager` state
  diverges from the recorded ROM `Oscillating_table` (only fires when
  the trace has v6.1+ per-frame oscillation snapshots).
- CNZ trace replay error count dropped from 5351 to 4885 and the
  first-error frame moved from F850 (Sonic y_speed mismatch from late
  Hover Fan trigger) to F854 (Tails y_speed mismatch — separate, not
  oscillator-related, root cause).
- Cross-game parity verified: S1 GHZ remains green; S1 MZ, S2 EHZ, and
  S3K AIZ all show pre-existing failure modes unchanged by this fix.

### Sonic 3&K CNZ Object Test/Engine Fixes (60/71 -> 71/71 unit tests)

- Repaired the 11 failing CNZ unit-test assertions surfaced on the
  develop baseline (3 in `TestS3kCnzDirectedTraversalHeadless`, 8 in
  `TestS3kCnzLocalTraversalHeadless`).
- **Cylinder**: corrected the rolling-radii / `Status_Roll`
  assertions to match `sub_324C0` (`sonic3k.asm:67985`) — capture
  clears `Status_Roll` (line 68005) and writes `default_y_radius` /
  `default_x_radius` (lines 68003-68004); rolling radii (7, 14) only
  apply when the rider jumps off (`loc_325F2`, lines 68062-68065).
- **Hover Fan**: moved `hoverFanRaisesThePlayer...` to fan centre
  (0x0900, d1=$40) so the ROM `sub_31E96` not/double mirror path
  produces the test's expected -4 px lift; reentry test now uses
  0x092F (d1=$6F) so the ROM lift formula exits the band in one
  frame.
- **Cannon**: fixed test player Y to land standing (`cannon.y - $29 -
  yRadius + 3`); restored `setLaunchDelayFramesForTest` to advance
  state to `STATE_READY_TO_LAUNCH` for the new pull-down state
  machine.
- **Balloon**: added `triggerBalloonContact` test helper so direct
  `update()` callers exercise the `TouchResponseListener` path the
  level loop normally drives. `CnzBalloonInstance.advancePopAnimation`
  now flips `setDestroyed(true)` when the pop sequence ends, matching
  ROM `Sprite_CheckDeleteTouch3` retiring the offscreen balloon.
- **Rising Platform**: retained the step-off bounce path inside the
  floor-settled routine so the spring-back behavior still fires after
  the player fully compresses the platform (intentional gameplay
  divergence from ROM `loc_31BD2`).
- Added `getStandXRadius()` accessor on `AbstractPlayableSprite` for
  test parity assertions.

### Regenerated AIZ Trace with v6.0-s3k Recorder

- Regenerated `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/`
  (metadata.json, aux_state.jsonl) using the v6.0-s3k recorder so the trace
  carries per-frame Tails CPU state events. New
  `aux_schema_extras: ["cpu_state_per_frame"]` field opts parsers in.
- physics.csv is byte-identical to the previous recording (deterministic ROM
  emulation). aux_state.jsonl gained 20798 per-frame `cpu_state` events.
- BK2 unchanged; ROM unchanged; `pre_trace_osc_frames=0` unchanged.
  Test baseline (TestS3kAizTraceReplay#replayMatchesTrace at F2667 with 1208
  errors) is preserved.

### Regenerated CNZ Trace with v6.0-s3k Recorder

- Regenerated `src/test/resources/traces/s3k/cnz/` (metadata.json, physics.csv,
  aux_state.jsonl) using the v6.0-s3k recorder so the trace carries per-frame
  Tails CPU state events. New `aux_schema_extras: ["cpu_state_per_frame"]`
  field opts parsers in.
- BK2 unchanged; ROM unchanged. Recording produces
  `start_gameplay_frame_counter=0` (vs old=1) due to a one-tick offset at
  recording start; engine frame indexing shifts by 1 vs the previous trace.
  First strict error now surfaces at F850 (y_speed mismatch on Sonic) instead
  of F1758 (tails_y_speed) — same engine surface, different frame indexing,
  but now with ROM-side per-frame `Tails_CPU_routine` / `Ctrl_2_logical` /
  `Tails_CPU_idle_timer` / `Tails_CPU_flight_timer` / `Tails_CPU_target_X/Y` /
  `Tails_CPU_auto_fly_timer` / `Tails_CPU_auto_jump_flag` data available for
  divergence analysis.

### Sonic 3&K Tails CPU Fly-Back Exit Gate (per-game ROM parity, AIZ1 F2590)

- Split the sidekick CPU's `TailsRespawnStrategy.updateApproaching`
  fly-back-to-leader exit gate by game so each ROM's TailsCPU_Flying /
  Tails_FlySwim_Unknown landing condition is honoured exactly:
  - **S2** keeps `andi.b #$D2,d2 / bne return` (s2.asm:38872-38873) —
    bits 1+4+6+7 (in_air | roll_jump | underwater | bit7) of the
    16-frame-delayed leader status block exit.
  - **S3K** moves to `andi.b #$80,d2` (sonic3k.asm:26625) plus the
    leader-alive check `cmpi.b #6,(Player_1+routine).w / bhs`
    (sonic3k.asm:26629-26630). Bit 7 isn't a standard player status
    flag, so this gate practically never blocks; landing is decided
    by position residuals + Sonic-alive only.
  Wired through two new `PhysicsFeatureSet` fields:
  `sidekickFlyLandStatusBlockerMask` and
  `sidekickFlyLandRequiresLeaderAlive`. S1 (no CPU sidekick) sets the
  mask to 0 and the leader-alive check to false.
- Effect on `TestS3kAizTraceReplay#replayMatchesTrace`: first strict
  divergence advances from F2590 → F2667 (errors 1558 → 1208,
  warnings 2010 → 1633). At F2590 ROM Tails had just landed from the
  fly-back, fell off the giant ride vine, contacted Spike object
  slot 22 (`loc_24090`) and applied the standard hurt-bounce
  (`sub_24280` → `HurtCharacter`, sonic3k.asm:49011 / 49200 / 21065).
  The engine had Tails locked in APPROACHING with
  `object_controlled = true` for the entire descent because the
  S2-derived 0xD2 mask saw Sonic's airborne bit set and refused to
  land. The S3K-correct 0x80 mask lets Tails land as soon as
  residuals reach zero, exposing him to the spike at the same trace
  frame as the ROM.

### Sonic 3&K Slope-Repel Per-Tick `slopeRepelJustSlipped` Flag (CNZ1 F1758 partial)

- Added `AbstractPlayableSprite.slopeRepelJustSlipped` per-tick flag, cleared at the start of `PlayableSpriteMovement.handleMovement` and set inside `Player_SlopeRepel` only on the frame where the slip actually fires (`bset #Status_InAir`, sonic3k.asm:23929 / 23856).
- Replaced the `move_lock > 0` short-circuit in `CnzWireCageObjectInstance.restoreObjectLatchIfTerrainClearedIt` (introduced in iter-11 for F1740) with the new `isSlopeRepelJustSlipped()` check.
- `move_lock` lingers for 30 frames after a slip, so when the wire cage recaptured Tails 18 frames later (F1758), `move_lock` was still 12 -- the cage's restore-hack short-circuited spuriously, letting an engine-side stale-terrain-probe `air = true` propagate through the cage's release logic and detach Tails one frame too soon. The new flag fires only on the actual slip frame.
- CNZ1 trace partial advancement: F1758 first error stays at F1758 but field changes `tails_angle mismatch` → `tails_y_speed mismatch` (engine now matches `tails_x`, `tails_y`, `tails_angle`, `tails_air`, `tails_ground_mode` at F1758; only `tails_g_speed` and `tails_y_speed` still diverge by 0x20 due to slope-resist running on engine where ROM appears to skip it -- per-frame `Tails_CPU_routine`/`object_control` data needed to verify ROM behaviour). Total errors 4678 → 4625.
- F1740 still resolved (the F1740 fix keeps working with the new flag because the slip happens in the same physics tick).

### Sonic 3&K CnzWireCage: Don't Override Slope-Repel Slip (CNZ1 F1740)

- `CnzWireCageObjectInstance.restoreObjectLatchIfTerrainClearedIt` now
  short-circuits when `move_lock > 0`. The cage's "restore on-object"
  hack was a compensation for the engine's terrain-probe being too
  aggressive about marking the player airborne under invisible level
  art -- but the same `setAir(true)` is also produced by ROM-accurate
  `Player_SlopeRepel` (sonic3k.asm:23907) when |gSpeed| drops below
  `$280` at a steep angle, and that slip is a legitimate ROM detach
  that the cage must honour. `move_lock = 30` was just set by the
  same SlopeRepel, so it's a reliable signal that the air state is
  not from a stale terrain probe.
- CNZ1 trace F1740 (Tails on cage at angle 0xC0, gSpeed 0x271 < 0x280)
  was a concrete divergence: ROM ran `Player_SlopeRepel` (no
  Status_OnObj gate exists in S3K's slope repel), set
  `Status_InAir = 1`, then the cage's released path
  (`loc_33ADE` → `loc_33B1E` → `bne loc_33B62`) ran a simple release
  that preserved `y_vel = -gSpeed = -0x271`. Engine's slope repel did
  fire and set air=true, but the cage's restore-hack reverted it,
  keeping Tails on the cage instead.
- First strict error in `TestS3kCnzTraceReplay` advances F1740 → F1758
  (18 more frames of correctness).

### Trace Recorder v6: Per-Frame Tails CPU State Events

- Extended `tools/bizhawk/s3k_trace_recorder.lua` to emit a per-frame
  `cpu_state` aux event capturing the full Tails CPU global block
  (`Tails_CPU_interact`, `Tails_CPU_idle_timer`,
  `Tails_CPU_flight_timer`, `Tails_CPU_routine`,
  `Tails_CPU_target_X/Y`, `Tails_CPU_auto_fly_timer`,
  `Tails_CPU_auto_jump_flag`) plus `Ctrl_2_held_logical` and
  `Ctrl_2_pressed_logical`.
- Bumped recorder version to `6.0-s3k`. CSV schema unchanged
  (`trace_schema: 5`); new field `aux_schema_extras:
  ["cpu_state_per_frame"]` lets parsers opt-in without breaking older
  traces.
- Java side: new `TraceEvent.CpuState` record, parsing in
  `TraceEvent.parseJsonLine`, `TraceMetadata.auxSchemaExtras`,
  `TraceMetadata.hasPerFrameCpuState()`,
  `TraceData.cpuStateForFrame()`,
  `SidekickCpuController.hydrateFromRomCpuStatePerFrame()` (tolerates
  unmapped ROM routines).
- Closes the visibility gap that blocked CNZ1 trace F1740 root cause
  analysis: the existing trace's `sidekick_routine` field is the
  sprite OST routine byte, not `Tails_CPU_routine` at `$F708`, and
  `Ctrl_2_logical` was never recorded.

### Sonic 3&K Tails CPU Despawn Object-ID Mismatch Path (CNZ1 F1685)

- Gated the sidekick CPU's despawn-on-object-id-mismatch path behind a
  new `PhysicsFeatureSet.sidekickDespawnUsesObjectIdMismatch` flag.
- S2 keeps the existing engine behaviour (`true`) — `TailsCPU_CheckDespawn`
  (`s2.asm:39067`) does `cmp.b id(a3),d0`, comparing the cached object's
  id byte to the current object's id, so different object types
  legitimately trigger despawn there.
- S3K disables the path (`false`) — `sub_13EFC` (`sonic3k.asm:26823`)
  does `cmp.w (a3),d0`, comparing the high word of the cached and
  current object's routine pointers. For S3K all gameplay objects live
  in ROM `0x0001xxxx-0x0007xxxx` (typically `0x0003xxxx`), so the high
  word is identical for virtually every object encountered in normal
  play. The check almost never fires in ROM. CNZ1 trace F1685
  (barber-pole `0x4D` → wire-cage `0x4E`, both routines at
  `0x000338xx`) was a concrete example of engine-vs-ROM divergence: ROM
  cached/current high words both `0x0003`, no despawn; engine compared
  8-bit IDs and spuriously despawned Tails.

### Sonic 3&K Tails CPU Flight AI (Catch-Up + Auto-Recovery)

- Ported `Tails_Catch_Up_Flying` (`sonic3k.asm:26474`, ROM CPU routine
  0x02) to `SidekickCpuController.CATCH_UP_FLIGHT`. Triggers on either
  the sidekick's Ctrl_2_logical A/B/C/START press or the 64-frame
  `Level_frame_counter` gate (suppressed if Sonic is object-controlled
  or super). On trigger, teleports Tails to (Sonic.x, Sonic.y - 0xC0),
  zeros all three velocities, sets the air and `double_jump_flag` bits,
  and transitions to `FLIGHT_AUTO_RECOVERY`.
- Ported `Tails_FlySwim_Unknown` (`sonic3k.asm:26534`, ROM CPU routine
  0x04) to `SidekickCpuController.FLIGHT_AUTO_RECOVERY`. Implements
  the 5-second off-screen timer that rolls back to `CATCH_UP_FLIGHT`
  on expiry, the 16-frame-delayed target with the -0x20 lead when
  Sonic isn't on-object and isn't sprinting past 0x400, the X steer
  of `min(|dx|>>4, 0xC) + |Sonic.x_vel (hi byte)| + 1` clamped to
  `|dx|`, and the Y steer of +/-1 per frame. Post-step residuals
  drive the NORMAL transition so overshoot-clamp frames match ROM's
  `d0 = 0` at `loc_13CA6/loc_13CAA`. Every on-screen frame refuels
  `double_jump_property` to 240 (ROM `loc_13C3A:26552`) so the flight
  animation and gravity stay active indefinitely.
- Routed `updateNormal()`'s dead-leader branch to
  `FLIGHT_AUTO_RECOVERY`, replacing the `APPROACHING`/respawn-strategy
  approximation with the ROM-accurate behaviour from `loc_13D4A`
  (`sonic3k.asm:26656-26665`). The transition fires only on
  `leader.getDead()` (ROM `cmpi.b #6; blo.s` tests routine >= 6);
  hurt bounces (routine 0x04) deliberately stay in NORMAL so Tails
  keeps following the ricocheting Sonic.
- Corrected the `mapRomCpuRoutine` table: 0x02 now maps to
  `CATCH_UP_FLIGHT` and 0x04 to `FLIGHT_AUTO_RECOVERY` (previously
  misrouted to `SPAWNING` / `APPROACHING`).
- Two new unit-test classes cover the state machine:
  `TestSidekickCpuControllerCatchUpFlight` (3 tests — Ctrl_2 trigger,
  64-frame gate, object-control suppression) and
  `TestSidekickCpuControllerFlightAutoRecovery` (4 tests — X steer,
  off-screen timeout, close-enough NORMAL transition, dead-leader
  entry from NORMAL).
- Current AIZ/CNZ trace replays don't exercise any dead-leader or
  long-separation code path, so the trace-test first-divergence
  frames (AIZ 2150, CNZ 318) are unchanged by this workstream. The
  flight AI is a correctness fix for the "Sonic died and the game
  keeps running" edge case and for future traces that exercise
  post-carry catch-up (e.g., pause-menu pausing or level-edge
  interactions where Tails goes off-screen).

### Sonic 3&K Sidekick CPU Parity (AIZ/CNZ Trace Replay Follow-Ups)

- Gated the sidekick CPU follow-AI snap threshold by game instead of
  hardcoding the Sonic 2 value. The ROM S2 `TailsCPU_Normal_FollowLeft`/
  `FollowRight` override Sonic's delayed input when `|dx| >= 0x10`
  (`s2.asm:38952` / `s2.asm:38967`), while S3K `loc_13DF2`/`loc_13E26`
  use `|dx| >= 0x30` (`sonic3k.asm:26712` / `sonic3k.asm:26729`). Added
  `PhysicsFeatureSet.sidekickFollowSnapThreshold` with per-game values
  (`SIDEKICK_FOLLOW_SNAP_S2 = 0x10`, `SIDEKICK_FOLLOW_SNAP_S3K = 0x30`),
  wired through `CrossGameFeatureProvider.buildHybridFeatureSet`, and
  replaced the `HORIZONTAL_SNAP_THRESHOLD` constant in
  `SidekickCpuController.updateNormal()` with a feature-set lookup. This
  shifts the first `TestS3kAizTraceReplay` strict error past the
  post-miniboss landing window (frame ~1943) so Tails skids with the
  ROM-recorded delayed LEFT input when Sonic is 16-48 pixels away, and
  lets two previously-failing `TestS3kAizReplayBootstrap` tests pass.
- Preserved Tails's flight physics across the CNZ1 carry release.
  `SidekickCpuController.updateCarryInit()` now mirrors ROM `loc_13FC2`
  (`sonic3k.asm:26904`) by setting `sidekick.setDoubleJumpFlag(1)`, and
  the landing release branch in `updateCarrying()` zeros
  `x_vel/y_vel/ground_vel` while keeping `double_jump_flag` set, matching
  ROM `loc_14016` (`sonic3k.asm:26923-26946`). `applyGravity()` and
  `doObjectMoveAndFall()` in `PlayableSpriteMovement` now select flight
  gravity (+0x08) on `sprite.getSecondaryAbility() == FLY &&
  sprite.getDoubleJumpFlag() != 0` — the same predicate ROM
  `Tails_Stand_Freespace` uses when branching to
  `Tails_FlyingSwimming` (`sonic3k.asm:27553-27555`). This closes the
  "Tails Flying-With-Cargo Physics" discrepancy; the original note is
  updated in `docs/S3K_KNOWN_DISCREPANCIES.md` and the remaining gap
  (catch-up/hover AI routines 0x02 and 0x04) is recorded for follow-up
  work.

### Trace Test Mode

Config-gated dev tool that lists all trace-replay tests from
`src/test/resources/traces/` on the master title screen and plays the
chosen trace back inside the live engine with red/orange/grey divergence
counters, BK2 input visualiser (A/B/C + U/D/L/R + S), and a rolling
mismatch log. Session ends with a 1 s hold on `TRACE COMPLETE` then a
default `FadeManager` fade to black; Esc during playback skips the hold
and starts the fade immediately. Toggle via `TEST_MODE_ENABLED=true`.
See `docs/superpowers/specs/2026-04-23-trace-test-mode-design.md`.

The trace infrastructure (`TraceData`, `TraceBinder`,
`TraceReplayBootstrap`, etc.) moved from `src/test/java/com/openggf/tests/trace/`
to `src/main/java/com/openggf/trace/` so it is usable at runtime. A new
`TraceReplayFixture` interface abstracts headless vs live replay;
`PlaybackDebugManager` grew programmatic `startSession`/`endSession`
and a `PlaybackFrameObserver` hook so `GameLoop` can suppress gameplay
ticks on ROM lag frames.

### Performance, Parity, and Trace Replay

- Reduced hot-path render and runtime churn with atlas bulk-copy cleanup,
  object-manager active-list caches, direct scroll-buffer copy helpers,
  profiler gating behind the debug overlay, and direct-buffer SMPS block
  rendering parity.
- Fixed several Sonic 1 parity regressions in object lifecycle handling,
  including explosion-item slot routing, lost-ring SST slot reservation,
  bounded `CHUNK_STEP` advancement, push-block two-stage out-of-range logic,
  and counter-based object-placement catch-up after large camera jumps.
- Trace replay now supports schema v3 execution counters
  (`gameplay_frame_counter`, `vblank_counter`, `lag_counter`). Sonic 1,
  Sonic 2, and Sonic 3&K BizHawk recorders all emit the v3 schema, and
  synthetic v3 fixtures cover the per-game parser contract. Real
  BK2-derived S3K fixture coverage remains follow-up work; until it lands,
  the legacy-heuristic fallback in `TraceExecutionModel` remains reachable
  for older or pre-v3 traces.

### Sonic 3&K CNZ1 Miniboss Arena Entry (Workstream D, Task 8)

- Wired the CNZ Act 1 miniboss arena trigger in `Sonic3kCNZEvents`. When
  the camera reaches `Camera_X_pos = $31E0`
  (`Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_X`), the engine now mirrors
  ROM `loc_6D9A8` (sonic3k.asm:144830): stash the prior camera clamp
  rectangle, lock the camera to the arena box ($31E0..$3260,
  $01C0..$02B8), fade out the music, set `Boss_flag` and the
  wall-grab-suppression bit, run PLC `0x5D`, and install
  `Pal_CNZMiniboss` into palette line 1 via the shared palette
  ownership registry.
- Added a falling-edge release path: when `CnzMinibossInstance.onEndGo`
  clears `Boss_flag` after the defeat sequence, the saved camera clamps
  are restored and the wall-grab suppression bit is released so the
  post-boss camera-pan + signpost sequence can resume.
- Added the `S3kPaletteOwners.CNZ_MINIBOSS` owner ID
  (`s3k.cnz.miniboss`) and the `Sonic3kConstants.PAL_CNZ_MINIBOSS_ADDR`
  ROM offset (`0x06E370`, S&K-side). The S&K offset was confirmed via
  `RomOffsetFinder search-rom` against the
  `Levels/CNZ/Palettes/Miniboss.bin` byte signature; the S3-half
  sibling (`0x24BF70`) is documented but explicitly not referenced.
- Replaced the placeholder local threshold `MINIBOSS_CAM_X_THRESHOLD =
  0x3000` with a reference to `Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_X`
  so the trigger and the camera-min clamp share a single source of
  truth. Audio fade-in for the miniboss music is reserved for T12.
- Added `TestCnzMinibossArenaEntry` covering the threshold-crossing
  behaviour (Boss_flag set, wall-grab suppressed, BG routine =
  `BG_BOSS_START`) and pinning `CNZ_MINIBOSS_ARENA_MIN_X` to `$31E0`.

### Sonic 3&K CNZ1 Mini-Boss (Workstream D)

- Ported `Obj_CNZMiniboss` (sonic3k.asm:144823..145002) to
  `CnzMinibossInstance` atop `AbstractBossInstance`: full 8-routine
  state machine (Init, Lower, Move, Opening, WaitHit, Closing, Lower2,
  End), ROM hit count of 6, and the Lower2 per-pixel descent.
- Ported `Obj_CNZMinibossTop` (sonic3k.asm:145004..145673) to
  `CnzMinibossTopInstance`: 4-routine state machine (TopInit, TopWait,
  TopWait2, TopMain) with bouncing-ball physics and arena-chunk
  destruction publication.
- Wired CNZ1 arena entry in `Sonic3kCNZEvents`: camera clamps
  (0x31E0..0x3260, 0x01C0..0x02B8), `Boss_flag`, wall-grab
  suppression, PLC 0x5D, and `Pal_CNZMiniboss` installation — mirroring
  ROM `loc_6D9A8`. Corrected the miniboss camera-trigger X from 0x3000
  to the ROM's 0x31E0.
- Corrected `PLC_CNZ_MINIBOSS` from 0x5C to 0x5D (ROM
  `sonic3k.asm:144844`). No external behaviour depended on the
  off-by-one value in prior commits; the now-corrected PLC load is
  guarded per spec §9.
- `TestS3kCnzTraceReplay` error count remains 1954 (delta 0 vs
  post-C baseline). The cascade from the workstream-C Tails-carry arc
  (Tails lands ~frame 42 vs ROM frame 106) dominates the headline number
  and fills the first-20 divergence list before the mini-boss window is
  reached in isolation; the boss state machine is implemented correctly
  but cannot be validated from the trace alone until the carry-arc
  cascade is resolved. Baseline captured in
  `docs/s3k-zones/cnz-post-workstream-d-baseline.md`.

### Sonic 3&K CNZ1 Tails-Carry Intro (Workstream C)

- Implemented the CNZ Act 1 Tails-carry intro (`Tails_CPU_routine` 0x0C /
  0x0E / 0x20 in the S3K disassembly). The sidekick CPU driver now enters
  `CARRY_INIT` on the first gameplay tick of CNZ1 when the active team is
  Sonic + Tails, copies Tails's velocity onto Sonic each frame, and
  releases through any of the three ROM-accurate paths: ground contact,
  jump press (with the 0x12-frame cooldown), or external-velocity latch
  mismatch (with the 0x3C-frame cooldown).
- Added the `SidekickCarryTrigger` game-agnostic hook
  (`GameModule.getSidekickCarryTrigger()`), the S3K-specific
  `Sonic3kCnzCarryTrigger`, and new `SidekickCpuController` states
  `CARRY_INIT` / `CARRYING` plus `CanonicalAnimation.TAILS_CARRIED`.
  `PlayableSpriteMovement.applyGravity` now short-circuits when the sprite
  is object-controlled so the carried cargo no longer accumulates free-fall.
- `TestS3kCnzTraceReplay` first-divergence frame is now 4 (was frame 0
  on the initial workstream-C landing, frame 1 pre-workstream-C) and
  total errors are 1954 (down from 2547 on the initial landing, up 319
  from the 1635 pre-workstream-C baseline). The residual delta
  (frames 4..107) tracks the deferred Tails-flying-with-cargo physics
  gap. The subsequent follow-up fix (`c627ba2bc`) removed the premature
  `updateCarryInit()` call from `updateInit()` after the S3K ROM was
  re-verified: `loc_13A10` (sonic3k.asm:26414) sets
  `Tails_CPU_routine=$C` and `rts` — no same-frame fall-through into
  `loc_13FC2` — so the carry body now runs on the tick after the INIT
  handler. The `TestS3kAizTraceReplay` pre-existing regression is
  orthogonal and stays owned by workstream H (see
  `docs/s3k-zones/cnz-task7-regression-note.md`).

### Experimental Level Editor Overlay

- Added an experimental, config-gated editor overlay and playtest loop. Set
  `EDITOR_ENABLED` to `true` to enter the editor from gameplay with `Shift+Tab`,
  then return to the parked playtest with the same shortcut.
- Added world-cursor navigation, focus regions, toolbar/library panes, focused
  block and chunk previews, derive-edit commands, and bounded cursor/camera
  sync so the editor can inspect and modify live level data safely.
- Added session-level editor/runtime handoff (`WorldSession`,
  `GameplayModeContext`, `EditorModeContext`, `EditorPlaytestStash`) so the
  engine can park gameplay, edit, resume, and restart playtests without
  tearing down unrelated state.
- Added editor-specific resume/fade handling, restart flow, and pixel-font text
  rendering so the overlay remains usable when bouncing between playtest and
  edit mode.

### Runtime, Services, and Testing

- Completed another large service-boundary cleanup pass: the engine now owns an
  explicit service root, `GameServices` is module-owned, and remaining
  singleton compatibility fallbacks were removed from runtime, render, audio,
  title, special-stage, and editor paths.
- Migrated ROM-backed tests onto the JUnit 5 annotation/extension path and
  tightened singleton-boundary guards around the runtime bootstrap. The
  preferred fixtures are now `@RequiresRom`, `@RequiresGameModule`, and
  `@FullReset`.
- Reduced S3K load-time duplicate decompression and restored bootstrap-safe
  module/title/runtime behavior after the service-closure refactor.
- Runtime app version metadata is now generated at build time and loaded at
  runtime with a hardened fallback path, and native-image bootstrap/package
  fixes restored correct metadata and library resolution in native builds.
- Graphics teardown now clears queued render-thread work, the main loop stops
  burning CPU while the window is unfocused, and donated preview capture now
  rebuilds `LevelManager`, GPU palette/atlas state, and gameplay runtime before
  normal play resumes so stale preview state cannot leak into gameplay.

### Configuration and Debug Tooling

- `config.json` key bindings now accept human-readable names such as `"SPACE"`,
  `"Q"`, `"F9"`, and `"GLFW_KEY_Q"` in addition to raw GLFW integer codes.
  Invalid names fall back to the default binding for that action.
- Switched the editor and debug text stack onto the shared no-shadow pixel-font
  renderer without relying on AWT font rendering in production code.
- Reduced the main debug overlay and performance panel text to half-scale
  pixel-font rendering for a denser on-screen layout.
- Compacted the performance panel `Heap` and `GC` lines so they stay within the
  existing right-side column at the smaller font size.
- Removed a major source of per-frame debug text allocation by reusing the
  textured-quad vertex buffer instead of allocating a fresh quad array and
  direct float buffer for every glyph draw.
- Fixed debug overlay sensor label overlap and improved text batching/caching
  so heavy overlay usage produces less garbage and more stable spacing.
- Replaced the dual-path `Layer/Prio/Solidity` debug readout with S1-relevant
  `Layer/Tunnel` state when the active game uses the UNIFIED collision model.
  S1 now surfaces the loop-low-plane flag as `Layer: A/B` and the roll-tunnel
  flag as `Tunnel: 0/1` in both the player status panel and the overhead
  character label. S2 and S3K continue to show the existing A/B + L/H + top/lrb
  solidity readout.
- First-run startup now materializes `config.json` automatically when the file
  is missing. The bundled defaults were also normalized so generated configs
  use readable key-name bindings, an empty `PLAYBACK_MOVIE_PATH`, and the
  intended title-screen startup flow defaults.
- The on-screen debug HUD now renders ROM-native hexadecimal coordinate text
  per game instead of a generic decimal-only presentation.

### Audio and Performance

- Added hybrid SMPS batching with shared batch-boundary helpers and identity
  regression coverage; the SMPS driver now defaults to the hybrid batching path.
- Fixed override music restoration after the 1-up jingle interrupts
  invincibility music.
- Reduced audio and results-screen memory churn with non-allocating replay heap
  sampling, quieter benchmark probe semantics, smaller blip-resampler history,
  and explicit stress/benchmark coverage for the audio results tally path.

### Data Select and Save System

- Added a shared data select framework (`DataSelectProvider`,
  `DataSelectSessionController`, `DataSelectHostProfile`, `DataSelectAction`)
  that separates presentation from game-specific save logic.
- Implemented the S3K data select screen (`S3kDataSelectManager`,
  `S3kDataSelectPresentation`, `S3kDataSelectRenderer`,
  `S3kDataSelectDataLoader`) with ROM-accurate rendering, 8 save slots, team
  selection (Sonic+Tails, Sonic, Tails, Knuckles), and selector state machine.
- Added `SaveManager` with JSON file persistence, SHA256 integrity hash, and
  corrupt-file quarantine. Save session context tracks active slot, team, and
  zone/act for level launch via `SaveSessionContext`.
- Added `StartupRouteResolver` to route title screen `ONE_PLAYER` actions to
  data select when S3K presentation is available.
- Added S1 and S2 data select host profiles (`S1DataSelectProfile`,
  `S2DataSelectProfile`) enabling cross-game data select donation: S1/S2 can
  use the S3K data select screen while retaining their own save ownership,
  zone labels, and team configurations.
- Wired `GameLoop.DataSelectActionHandler` and
  `Engine.launchGameplayFromDataSelect()` to consume data select actions and
  launch gameplay with the selected team and restored game state.
- Added runtime-generated donated preview screenshots for S1 and S2 when S3K
  is the donor. Preview PNGs are generated into the save-owned image cache,
  validated against engine version plus ROM fingerprint, and then loaded back
  through the donated S3K selected-slot preview seam instead of reusing ROM
  level-select art.
- Added per-game donated preview framing control, including explicit S1 and S2
  camera override tables, hidden `PREVIEW_CAPTURE` load mode, and a gameplay
  debug shortcut that logs the current camera as a preview capture override for
  tuning screenshot positions.
- Restricted donated data select routing to the real feature gate: S1/S2 only
  enter donated S3K data select when `CROSS_GAME_FEATURES_ENABLED` is `true`
  and `CROSS_GAME_SOURCE` is `s3k`. Title-screen exits now fade correctly both
  into donated data select and directly into level load when donation is off.
- Reworked donated emerald presentation in S3K data select for host games:
  S1 now uses a symmetric six-emerald layout, S1/S2 use host-specific emerald
  colour ordering on top of the native S3K presentation, and preview rendering
  no longer corrupts emerald palettes when selected-slot screenshots are shown.
- Switched most data-select rendering onto batched pattern draws, removed the
  remaining high-frequency per-frame allocations in the screen, and corrected
  selected-slot icon palette restoration/caching so donated previews no longer
  corrupt the active slot presentation.
- Hardened preview capture cleanup for donated S1/S2 screenshots so generated
  previews no longer leave stale `LevelManager`, palette, or pattern-atlas
  state behind after cache generation.

### Runtime-Owned Frameworks

- Added `PaletteOwnershipRegistry` for multi-writer palette arbitration with
  precedence ordering and underwater mirroring.
- Added `ZoneRuntimeRegistry` for typed per-zone runtime state adapters over
  raw event/state bytes, with S3K AIZ and HCZ adapters.
- Routed HCZ palette cycling through the palette ownership registry.

### Gameplay and Parity Fixes

- Implemented the HCZ2 moving wall chase sequence and corrected HCZ water-skim
  order of operations plus spinning-column flip synchronization.
- Removed the `0x7FF` Y-mask from solid object contact resolution to prevent
  phantom collisions in tall levels.
- Fixed S3K water restoration after stage returns, and restored power-up
  spawning plus slot-machine glass priority after runtime/bootstrap changes.
- Skipped already-collected S3K special stages on entry.
- Added `ActiveGameplayTeamResolver` so donated data select launches in S1/S2
  use the team selected for the current gameplay session instead of stale
  persistent config. This fixes mismatches between launched character, palette,
  lives HUD, and gameplay-side main-character lookups.
- Fixed S3K Tails-alone startup art loading by correcting the combined-ROM
  `ArtNem_TailsLifeIcon` address, which restored the lives HUD, badnik/object
  art, and other shared sprite sheets when starting Angel Island as Tails.
- Fixed cross-game Knuckles donation in S1/S2 so the selected team now carries
  through runtime palette loading, HUD lives icon/name resolution, and
  gameplay startup. S1 donated Knuckles now uses a dedicated lives-HUD palette
  adaptation over the live S1 HUD line, while S2 keeps the correct donated HUD
  icon/text contract.
- Completed the S3K HCZ2 boss line with the full end-boss sequence, geyser and
  blade choreography, Knuckles-specific cutscene path, and a long parity pass
  over camera locks, water-column visuals, blade physics, prison/cutscene
  sequencing, and post-results behavior.
- Expanded Marble Garden coverage with the trigger platform, dash trigger,
  swinging platform and spike ball, sinking mud, animated tile graph channels,
  and parity fixes for top-platform collision/grab behavior plus MGZ1 vertical
  wrapping and intro flow.
- Added the shared MGZ/LBZ smashing pillar (`Obj_MGZLBZSmashingPillar`, IDs
  0x52 and 0x20), with zone-conditional art and dimensions, ROM-accurate
  16.16-fixed gravity descent and 1px/frame retract, ceiling-crush hurt via
  `SolidObjectFull`, and forced low VDP priority so the bottom spikes layer
  behind foreground tiles like the ROM.
- Added more S3K object and badnik coverage with Automatic Tunnel, Tunnelbot,
  Spiker, and Bubbles.
- Polished AIZ2 post-boss and bombing-run parity with fixes for sidekick bounds
  during autoscroll, bridge/button state rendering, boss flash/visibility and
  turret angles, waterfall priority, and egg-prison collision/animal rendering.
- Fixed seamless act transitions that could leave ring/time counters stale
  after a transition boundary.
- Fixed corrupted diagonal spring art in MGZ and MHZ. The S3K PLC registry
  already encoded the zone-specific override to use
  `ArtTile_MGZMHZDiagonalSpring ($0478)` instead of the shared
  `ArtTile_DiagonalSpring ($043A)`, but the hardcoded builders ignored it.
  `Sonic3kObjectArt` builder methods now take an `artTileBase` parameter and
  `Sonic3kObjectArtProvider.invokeBuilder` threads `LevelArtEntry.artTileBase()`
  through, matching the ROM's split between shared `Map_*` tables and
  per-zone `art_tile` selection. Also fixes a latent `Sonic3kObjectArtKeys.SPIKES`
  override that was silently ignored for FBZ.

## v0.5.20260411 (Released 2026-04-11)

Analysis range: `v0.4.20260304..v0.5.20260411` on `develop` (`2479` commits, `2298` non-merge commits,
`1588` files changed, `477351` insertions, `28266` deletions). Net code growth is ~449,100 lines.

A primarily architectural release. The engine internals have been restructured to prepare for level
editor support, safe runtime teardown, and future multi-instance play-testing. Sonic 3 & Knuckles
gameplay coverage has advanced significantly across Angel Island and Hydrocity: the AIZ2 Flying
Battery bombing sequence, AIZ2 end boss, post-boss capsule/cutscene flow, AIZ-to-HCZ transition,
HCZ1 miniboss, and HCZ1-to-HCZ2 transition are now represented, alongside all three S3K bonus-stage
families in active implementation.

### Architectural Overhaul: Two-Tier Service Architecture

The engine's object model has been fundamentally restructured from direct singleton access to a
two-tier dependency injection pattern.

- **GameServices** (global tier): facade over ROM, graphics, audio, camera, level, fade, and
  configuration singletons. Accessed anywhere via `GameServices.camera()`, `GameServices.audio()`, etc.
- **ObjectServices** (context tier): injected into every game object at spawn time via
  `ObjectManager`. Provides camera, game state, zone features, sidekick access, and level queries
  scoped to the object's lifecycle. Accessed via `services()` within any `AbstractObjectInstance`.
- **ThreadLocal construction context**: `ObjectServices` is available during object construction
  without requiring constructor parameters, via a `ThreadLocal` injection pattern.
- **Migration scope**: 105 Sonic 2 object files, 50 Sonic 1 object files, 25 Sonic 3K object files,
  and 6 game-agnostic base classes migrated from `getInstance()` / `LevelManager.getInstance()` to
  `services()` or `GameServices` as appropriate. All singleton `.getInstance()` calls removed from
  object classes.
- **NoOp sentinels**: null-returning provider methods replaced with NoOp sentinel objects across
  the provider interfaces (zone features, physics, water, scroll handlers), eliminating null checks
  throughout the object layer.
- **GameId enum**: replaced string-based game identification with type-safe `GameId` enum throughout
  `CrossGameFeatureProvider` and module detection.

### Architectural Overhaul: GameRuntime and Singleton Lifecycle

- **GameRuntime**: introduced `GameRuntime` as the explicit owner of all mutable gameplay state.
  `ObjectServices` is backed by the runtime instance rather than global singletons.
- **resetState() lifecycle**: all singletons (`Camera`, `RomManager`, `GraphicsManager`,
  `AudioManager`, `CollisionSystem`, `CrossGameFeatureProvider`, `DebugOverlayManager`,
  `SonicConfigurationService`, `Sonic1ConveyorState`, `Sonic1SwitchManager`,
  `TerrainCollisionManager`) now implement `resetState()` for clean teardown without destroying
  the singleton instance. `resetInstance()` deprecated across the board.
- **Generation counter**: `GameContext` tracks a generation counter for stale reference detection.
- **SingletonResetExtension**: JUnit 5 extension with `@FullReset` annotation for automated
  per-test singleton reset, replacing manual `resetInstance()` boilerplate across 35+ test classes.
- **GameRuntime lifecycle wired into test harness**: `resetPerTest()` now creates/destroys
  `GameRuntime` for CI stability.

### Architectural Overhaul: LevelManager Decomposition

`LevelManager` (previously the largest class in the engine) has been broken into focused components:

- **LevelTilemapManager**: extracted ~18 methods and ~22 fields for tilemap rendering, chunk
  lookup, and tile-level queries.
- **LevelTransitionCoordinator**: extracted ~43 methods and ~25 fields for act transitions,
  seamless level mutation, title cards, results screens, and game mode flow.
- **LevelDebugRenderer**: extracted ~12 methods and ~14 fields for collision overlay, sensor
  display, camera bounds, and other debug visualizations.
- **LevelGeometry** and **LevelDebugContext** records introduced as data carriers between the
  decomposed components.
- Game-specific art dispatching extracted from `LevelManager` into per-game modules.

### Architectural Overhaul: Cross-Game Abstraction Hardening

Systematic removal of game-specific coupling from the engine core:

- **PlayableEntity interface**: extracted from `AbstractPlayableSprite` to decouple `level.objects`
  from `sprites.playable`. Includes `isOnObject()`, `getAnimationId()`, and all methods needed
  by game objects to interact with the player.
- **PowerUpSpawner interface**: breaks `sprites.playable` dependency on `level.objects` for
  monitor/power-up spawning.
- **DamageCause**, **GroundMode**, **ShieldType** relocated from `sprites.playable` to `game`
  package for cross-game reuse.
- **SecondaryAbility enum**: replaced `instanceof Tails` checks throughout the codebase.
- **CanonicalAnimation enum** and **AnimationTranslator**: cross-game animation vocabulary
  enabling bidirectional sprite donation between any pair of games.
- **DonorCapabilities interface**: each `GameModule` declares its donation capabilities (S1, S2,
  S3K all implemented), replacing hardcoded branches in `CrossGameFeatureProvider`.
- S1 wired as donor for forward donation into S2/S3K games.
- CNZ slot machine renderer moved to `ZoneFeatureProvider`; seamless mutation moved to
  `GameModule`; Tails tail art loading moved to `GameModule`; sidekick zone suppression moved
  from hardcoded S2 IDs to `GameModule`.
- 11 cross-game classes relocated from `sonic2` to generic packages; 5 cross-game dependency
  classes decoupled.

### Common Code Extraction (Phase 1-5)

A systematic 5-phase refactoring pass eliminated structural duplication across all three games:

- **Phase 1 — Common utilities**: `SubpixelMotion` (16.16 fixed-point gravity helpers),
  `AnimationTimer` (cyclic frame animation), `FboHelper` (centralised FBO creation),
  `PatternDecompressor.nemesis()` (eliminated private Nemesis copies), `refreshDynamicSpawn()`
  extracted into `AbstractObjectInstance`, `isOnScreen(margin)` guard migrated across all objects,
  `buildSpawnAt()` helper and `getRenderer()` helper inherited by all object classes.
- **Phase 2 — Base class extraction**: `AbstractMonitorObjectInstance`, `AbstractSpikeObjectInstance`
  (S2/S3K), `AbstractProjectileInstance` (S1 missiles), `AbstractPointsObjectInstance`,
  `AbstractFallingFragment` (collapsing platforms), `AbstractSoundTestCatalog`,
  `AbstractAudioProfile`, `AbstractObjectRegistry`, `AbstractZoneRegistry`,
  `AbstractZoneScrollHandler` (~20 scroll handlers migrated), `AbstractLevelInitProfile`
  (with `buildCoreSteps()`), `AniPlcScriptState` and `AniPlcParser` extracted from pattern
  animators.
- **Phase 3 — Behavior helpers**: S1 badnik migration to shared destruction config, shared ring/object
  placement record parsers, shared title card sprite rendering utility, shared S1 Eggman boss
  methods extracted into base class.
- **Phase 4 — Gravity and debris**: `GravityDebrisChild`, `PlatformBobHelper` (3 platform objects
  migrated), `ObjectFall()` method in `SubpixelMotion`.
- **Phase 5 — Cleanup**: shared constants, `loadArtTiles` path, shader path standardization,
  `ParallaxShaderProgram` extends `ShaderProgram` (deleted lifecycle duplication).
- **Debug render migration**: all S1, S2, and S3K objects migrated from legacy
  `appendDebug`/`appendLine` API to `DebugRenderContext`.

### MutableLevel (Level Editor Foundation)

- **MutableLevel**: a new level data abstraction supporting snapshot, mutation, and dirty-region
  tracking. Wraps the read-only level data and provides `setChunkDesc()`, `getGridSide()`,
  `saveState()`/`restoreState()` for undo/redo support.
- **Block**: added `saveState()`/`restoreState()` and `setChunkDesc()` for chunk-level mutation.
- **Dirty-region processing pipeline**: `processDirtyRegions()` wired into `LevelFrameStep` for
  efficient per-frame GPU updates of only the modified tile regions.
- MutableLevel preserves game-specific overrides and `ringSpriteSheet` across mutations.
- Round-trip and integration tests verify snapshot fidelity and mutation correctness.

### Sonic 3 & Knuckles Expansion

#### Knuckles: Playable Character

Knuckles is now a fully playable character with his complete S3K ability set, working natively
in S3K and via cross-game donation into S1 and S2.

- **Glide state machine**: glide activation on jump re-press with ROM-accurate turn physics
  (sine/cosine velocity from `doubleJumpProperty` angle, gravity balance). Direct mapping frame
  control using `RawAni_Knuckles_GlideTurn` table (frames 0xC0–0xC4).
- **Floor landing and sliding**: flat surfaces enter sliding state with deceleration (0x20/frame
  while jump held, matching ROM's `.continueSliding` routine). Slide follows terrain via
  `ObjectTerrainUtils` floor probing, snapping Y position to surface with correct angle. Ledge
  detection enters fall state when floor distance >= 14.
- **Wall grab and climbing**: wall grab with climbing animation cycling (frames 0xB7–0xBC every
  4 frames). Ledge climb using `Knuckles_ClimbLedge_Frames` table (4 keyframes with x/y deltas).
  Wall jump away with facing flip and normal jump animation.
- **Fall-from-glide landing**: ROM-accurate crouch pose with 15-frame `move_lock`.
- **ROM-accurate jump height**: `PhysicsProfile.SONIC_3K_KNUCKLES` with jump velocity 0x600
  (vs Sonic's 0x680), water jump 0x300 (vs 0x380), matching `Knux_Jump` in disassembly.
- **Shield ability gating**: fire/lightning/bubble shield abilities gated to Sonic only per ROM;
  Knuckles gets passive shield protection with glide as his secondary ability. Bubble shield
  bounce correctly suppressed on glide landing (gates on `SecondaryAbility.INSTA_SHIELD`, not
  `doubleJumpFlag` value).
- **Knuckles palette**: `Pal_Knuckles` (0x0A8AFC) loaded for both native S3K and cross-game
  donation. Cross-game palette fix ensures correct palette is loaded based on character config.
- **Life icon art**: `ArtNem_KnucklesLifeIcon` (0x190E4C) with character-specific rendering.
- **Sound effects**: GRAB and GLIDE_LAND SFX registered in S3K audio profile.
- **Character detection fix**: `Sonic3kLevelEventManager.getPlayerCharacter()` now resolves from
  config (was hardcoded to `SONIC_AND_TAILS`), enabling all character-gated object behaviour.
- **AIZ rock breaking**: knucklesOnly rocks (subtype bit 7) now trigger on airborne side contacts
  (jumping/gliding into them), not just grounded push.

#### S2 Cross-Game Knuckles Support

- **Lock-on palette**: "Knuckles in Sonic 2" palette loaded from S3K ROM at 0x060BEA. Only
  indices 2–5 differ from S2's `Pal_SonicTails` (Knuckles' reds vs Sonic's blues); title cards,
  badniks, and rings are unaffected. HUD text index 4 tweaked (green→orange) for readability.
- **Lives icon**: `ArtNem_KnucklesLifeIcon` decompressed from S3K donor ROM with pixel index
  remap from S3K palette layout to S2-compatible layout (`S3K_TO_S2_PALETTE_REMAP`).
- **HUD rendering**: lives name tiles use palette 0 (no flash cycling) when donor art is active,
  via `livesNameUsesIconPalette` flag in `HudRenderManager`.
- **Palette utility**: `Palette.mergeColorsFrom()` added for targeted color range copying.

#### Title Screen

- Full S3 title screen implemented with 6-phase state machine: SEGA logo with palette fade,
  12-frame Sonic morphing animation, white flash transition, and interactive menu with banner
  bounce physics, sprite animations, and menu selection.
- ROM data loading for 7 Kosinski art sets, 4 Nemesis sprite sets, 14 Enigma plane mappings.
- Hardcoded sprite mapping frames for banner, &Knuckles text, menu text, Sonic finger/wink,
  and Tails plane sprites (`Sonic3kTitleScreenMappings`).
- FadeManager transition fix: title screen exit now renders fade-to-black internally to avoid
  `GameLoop`/`UiRenderPipeline` FadeManager instance mismatch after `RuntimeManager` migration.

#### Level Select Screen

- ROM-accurate S3K level select matching the S3 disassembly menu infrastructure.
- `Sonic3kLevelSelectConstants`: data tables (level order, mark table, switch table, icon table,
  zone text, mapping offsets) from `s3.asm` with S&K zones replacing disabled/competition entries.
- `Sonic3kLevelSelectDataLoader`: loads Nemesis art (font, menu box, icons), Enigma mappings
  (screen layout, background, icons), uncompressed SONICMILES animation art, and palettes from ROM.
  Builds screen layout in-memory with S3K zone names via the LEVELSELECT codepage.
- `Sonic3kLevelSelectManager`: two-layer rendering (Plane B SONICMILES background + Plane A
  foreground), input navigation with disabled-entry skipping, sound test (0x00–0xFF), selection
  highlight, and zone icons.

#### Special Stage Character Support

- S3K Blue Ball special stages now dynamically resolve `PlayerCharacter` from config: Sonic &
  Tails (with AI sidekick), Sonic alone, Tails alone (with spinning tails appendage), and
  Knuckles (with correct palette patch to colors 8–15 per ROM's `Pal_SStage_Knux`).

#### AIZ Object Lifecycle Fixes

- **Vine dismount**: suppressed stale jump press on release to prevent immediate insta-shield
  (Sonic) or glide (Knuckles) activation. Added edge detection so holding jump from the vine-
  reaching jump doesn't cause immediate dismount.
- **Vine respawn**: removed self-destruct cull checks from both vine objects. The vine's coarse
  range was narrower than the Placement window, causing permanent respawn prevention via the
  `destroyedInWindow` latch.
- **Collapsing platform respawn**: removed `markRemembered()` call — ROM uses
  `Delete_Current_Sprite` (allows respawn), not `Remember_Sprite`. Platforms now correctly
  respawn when the player scrolls away and returns.
- **Breakable boulders**: preserved rolling state when smashing AIZ/LRZ rocks from the side,
  matching `SolidObjectFull` behaviour.
- **Special stage return**: restored saved centre coordinates correctly on Blue Ball exit,
  preventing the player from being embedded in the floor after returning from the big ring.
- **Results screen spawn path**: signpost flow now uses `spawnChild()` for the results object,
  preserving `ObjectServices` context and fixing the end-of-act bubble monitor crash.

#### AIZ Miniboss Completion
- AIZ miniboss defeat flow fully implemented: `S3kBossDefeatSignpostFlow` reusable sequence,
  staggered explosions with `S3kBossExplosionController`, per-explosion `sfx_Explode`.
- Knuckles napalm attack: `AizMinibossNapalmController` and `AizMinibossNapalmProjectile` with
  launch/drop/explode lifecycle, gated to Knuckles-only appearance.
- AIZ2 dynamic resize state machine for correct camera boundaries during miniboss spawn.

#### AIZ2 Boss and Transition Progress
- AIZ2 Flying Battery bombing sequence implemented with battleship overlay rendering, ship-relative
  bomb placement, explosion children, background tree spawners, and object-art loading for the
  bombership / small Robotnik craft frames.
- AIZ2 end boss implemented with Robotnik ship/head overlays, arm/propeller/flame/bomb/smoke child
  systems, camera scripting, boss state flow, and regression coverage for ship bomb timing.
- Post-boss capsule/cutscene flow now includes the AIZ2 egg capsule release path and handoff toward
  the Hydrocity transition. Follow-up fixes restore AIZ transition zone-feature state and prevent
  bombership art regressions after act-transition reinitialization.

#### Signpost and Results Screen
- `S3kSignpostInstance` with 5-state machine (idle/spin/slowdown/sparkle/done), stub and sparkle
  children, `PLC_EndSignStuff` art loading from ROM.
- `S3kHiddenMonitorInstance` with signpost interaction.
- Results screen: full state machine with tally, element system rendering, art loading from ROM,
  act display via `Apparent_act`, exit timing, control lock, victory pose, and Tails-specific
  victory animation.
- End-of-level flag and `endOfLevelActive` state wired through defeat flow.

#### Blue Ball Special Stages (WIP)
- Blue Ball special stage implemented (work in progress): gameplay, rendering, HUD, banner,
  ring animation, emerald collection, exit sequence.
- `SSEntryRing` art, animation, and special stage entry sequence from giant rings.
- Special stage results screen with art loading.
- Tails P2 support in special stages with tails sprite and delayed jump.
- Player returns to big ring location after special stage (not checkpoint).

#### S3K Bonus Stages: Slots, Gumball, and Glowing Sphere (WIP)
- `Sonic3kBonusStageCoordinator` now implements the S3K ring-threshold selection formula and
  zone/music routing for the three lock-on bonus stages: Slots, Glowing Sphere (Pachinko), and
  Gumball. StarPost bonus-star entry and saved-state return are wired into the S3K bonus-stage
  lifecycle.
- Bonus-stage title card support added to `Sonic3kTitleCardManager` and mappings, including the
  dedicated `BONUS STAGE` layout and bonus-specific fade timing.
- **Gumball stage bring-up:** `GumballMachineObjectInstance`, `GumballItemObjectInstance`, and
  `GumballTriangleBumperObjectInstance` implemented with ROM-driven machine state, dispenser /
  container / exit-trigger child chains, machine Y drift and slot tracking, subtype-specific item
  behavior, spring bounce/crumble parity, shield persistence, sidekick safety, and dedicated
  `SwScrlGumball` scrolling.
- **Glowing Sphere / Pachinko bring-up:** `PachinkoFlipperObjectInstance`,
  `PachinkoTriangleBumperObjectInstance`, `PachinkoBumperObjectInstance`,
  `PachinkoPlatformObjectInstance`, `PachinkoItemOrbObjectInstance`,
  `PachinkoMagnetOrbObjectInstance`, and `PachinkoEnergyTrapObjectInstance` implemented, with
  stage entry/return flow, top-exit handling, and dedicated `SwScrlPachinko` scrolling.
- **Zone animation support:** `Sonic3kPatternAnimator` and `Sonic3kPaletteCycler` now cover the
  bonus-stage-specific Gumball direct-DMA tile animation plus Pachinko animated tiles, DMA-driven
  background strips, and palette cycling.
- **Render-path parity for the gumball machine:** per-piece VDP priority from ROM mapping data,
  SAT-style sprite-mask post-processing, and replay-role metadata now preserve the intended glass /
  shell / interior pile layering for mixed-priority machine frames.
- Pachinko energy trap bootstrap now stays persistent like the ROM object, keeps its spawned
  column/beam children alive until scripted teardown, and force-releases players from competing
  magnet orbs before trap capture. Capture now zeros X/Y/G speed and cleanly holds the player on
  the beam.
- Bonus-stage title card exit no longer freezes the pachinko trap update loop. Persistent power-up
  re-registration now clears stale object slots before `ObjectManager` rebuilds, preventing slot
  aliasing during bonus-stage entry and post-title-card resume.
- **Slot Machine stage bring-up:** `S3kSlotRomData`, `S3kSlotStageController`,
  `S3kSlotStageState`, `S3kSlotCollisionSystem`, `S3kSlotPlayerRuntime`,
  `S3kSlotOptionCycleSystem`, `S3kSlotPrizeCalculator`, and reward/cage object runtime wiring now
  cover ROM table loading, rotating-stage movement, projected ground/air physics, grid collision,
  tile interactions, reel option cycling, match detection, cage capture/release, interpolated ring
  and spike rewards, exit wind-down/fade, and slot-specific sound effects.
- **Slot Machine rendering:** `S3kSlotLayoutRenderer`, `S3kSlotLayoutAnimator`,
  `S3kSlotMachineRenderer`, `S3kSlotMachinePanelAnimator`, `S3kSlotMachineDisplayState`,
  `SwScrlSlots`, and `shader_s3k_slots.glsl` implement layout animation, palette cycling,
  goal/peppermint/reel display updates, background row refresh, debug visibility, and FG glass /
  player priority ordering.
- **Slot Machine remediation:** state ownership was moved into the slot runtime with `ObjectManager`
  rendering for cage/reward objects, preserved special collision bits across probes, authoritative
  follow-up state, persistent wall animation state, capture-cycle restart coverage, and fixes for
  player swap focus, title-card bootstrap, runtime ownership, launch physics, spike reward ring
  drain, reel display, and exit fade.
- Added regression coverage for coordinator lifecycle, bonus title card mappings/flow, gumball
  machine drift and priority diagnostics, sprite-mask helper consumption and replay ordering,
  pachinko palette/pattern animation, slot ROM data, slot collision/player/runtime/rendering/reward
  systems, registry wiring, and live trap/orb/title-card integration.

#### Per-Character Physics
- Per-character physics profiles for Sonic, Tails, and Knuckles (speed, acceleration, jump height).
- Super spindash speed table and slope sprite selection fixes.
- Ducking while moving at slow speeds (S3K-specific behavior).

#### Palette and Visual Systems
- Palette cycling implemented for all remaining zones: HCZ, CNZ, ICZ, LBZ, LRZ, BPZ, CGZ, EMZ
  (plus existing AIZ).
- Per-frame palette mutation system for AIZ1 hollow tree reveal (`palette[2][15]`).
- AIZ fire curtain overlay with cached BG descriptors and fire palette fixes, looping linger and
  graceful scroll-off.
- Heat haze deformation applied to AIZ2 background layer.
- HUD text loaded from ROM; digit rendering uses mapping frames (not tile indices).

#### New Objects and Badniks
- `BreakableWall` (0x0D), `CorkFloor`, `FloatingPlatform`, `CaterkillerJr` (with body segment
  despawn), `AutoSpin`, `Falling Log`, `InvisibleBlock`, `StarPost`, `TwistedRamp`,
  `AIZCollapsingLogBridge` (0x2C), `AIZSpikedLog` (0x2E), `AIZFlippingBridge` (0x2B), and the
  zone-specific `Button` object (0x33).
- HCZ expansion: water surface, water rush sequence (`HCZBreakableBarObjectInstance`,
  `HCZWaterRushObjectInstance`, `HCZWaterWallObjectInstance`, `HCZWaterTunnelHandler`),
  `HCZConveyorBeltObjectInstance`, `HCZCGZFanObjectInstance`, `HCZHandLauncherObjectInstance`,
  `HCZLargeFanObjectInstance`, `HCZBlockObjectInstance`, `HCZConveyorSpikeObjectInstance`,
  `HCZTwistingLoopObjectInstance`, `HczMinibossInstance`, and `DoorObjectInstance` for HCZ/CNZ/DEZ.
- Additional S3K objects and badniks: `CollapsingBridgeObjectInstance`, `BubblerObjectInstance`,
  `Sonic3kInvisibleHurtBlockHObjectInstance`, `MegaChopperBadnikInstance`,
  `PoindexterBadnikInstance`, `BlastoidBadnikInstance`, `BuggernautBadnikInstance` /
  `BuggernautBabyInstance`, and `TurboSpikerBadnikInstance`.
- `Sonic3kLevelTriggerManager` added for AIZ trigger state such as boss-driven burn activation.
- All zone badnik entries populated in `Sonic3kPlcArtRegistry`.
- Initial badnik implementations wired into object system.
- **Badnik destruction effects**: destroying S3K badniks now spawns animals and floating points
  popups, matching S1/S2 behavior. Zone-specific animal pairs loaded from ROM per
  `PLCLoad_Animals_Index` (all 13 zones mapped). Enemy score art parsed from `Map_EnemyScore`
  (shared `ArtNem_EnemyPtsStarPost` blob). `Sonic3kPointsObjectInstance` provides S3K-specific
  score-to-frame mapping.

#### Spindash Dust
- **S3K spindash dust**: implemented native `SpindashDustArtProvider` for Sonic 3&K. Art loaded
  from ROM (`ArtUnc_DashDust` at 0x18A604, `Map_DashDust` at 0x18DF4, `DPLC_DashSplashDrown` at
  0x18EE2). Uses virtual pattern base 0x34000 to avoid collision with ring tiles in the atlas.
- **Multi-character dust isolation**: sidekick dust renderers now get isolated DPLC banks
  (shifted into `SIDEKICK_PATTERN_BASE + 0x2000` range), preventing atlas corruption when
  multiple characters spindash simultaneously.

#### Invincibility Stars
- **S3K invincibility stars**: `Sonic3kInvincibilityStarsObjectInstance` implements ROM-accurate
  Obj_Invincibility (sonic3k.asm:33751) with 1 parent group + 3 trailing child groups.
  Each group renders 2 sub-sprites at opposite positions on a 32-entry circular orbit table
  (`byte_189A0`). Children trail via `PlayableEntity.getCentreX/Y(framesAgo)` at 3/6/9 frames
  behind the player; parent orbits fast (9 entries/frame), children orbit slow (1 entry/frame).
  Rotation reverses when facing left. Art loaded from ROM (`ArtUnc_Invincibility` at 0x18A204,
  `Map_Invincibility` at 0x018AEA). `DefaultPowerUpSpawner` branches on `Sonic3kGameModule`
  to create the S3K variant; S1/S2 `InvincibilityStarsObjectInstance` remains unchanged.

#### Audio
- Music tempo scaling and all-spheres SFX fix.
- Ring collection sound alternates left/right channels.
- Correct SFX: `sfx_Death` for normal hurt (not `sfx_SpikeHit`), jump SFX fix.
- S3K tumble frame base corrected to `0x31` (not S2's `0x5F`).

#### Miscellaneous S3K Fixes
- VDP priority bit correctly extracted in S3K sprite mapping loader.
- Collapsing platforms stay solid during fragment phase (S2/S3K).
- Shield re-registration after act transition; StarPost bonus stage routing fix.
- AIZ1 level bounds use normal `LevelSizes` entry.
- Prevented OOM in S3K DPLC frame loading by parsing only 1P entries (combined mapping table fix).
- SONIC art address corrected; camera bounds restored after transition.
- Lightning shield sparks rendered directly instead of via DPLC.
- Save/restore `Dynamic_resize_routine` across big ring special stage transitions (ROM: `Saved2_dynamic_resize_routine`). Without this, the resize state machine restarted from routine 0 on return, rapidly re-processing boundary thresholds and causing incorrect camera locks in AIZ Act 2.
- Title card showed wrong act after AIZ mid-act fire transition. Death or special stage return displayed "Act 2" instead of "Act 1" because the engine lacked the ROM's `Apparent_zone_and_act` variable. Added `apparentAct` tracking to `LevelManager`: seamless transitions (fire) only update `currentAct`, normal act changes update both, title card requests read `apparentAct`. Results screen exit sets `apparentAct = 1` matching ROM's `move.b #1,(Apparent_act).w`.
- AIZ2 water incorrectly enabled for Knuckles on level select load. `LevelManager.initWater()` hardcoded `SONIC_AND_TAILS` instead of resolving the actual player character from the level event manager. ROM `CheckLevelForWater` (sonic3k.asm:9754-9759) gates AIZ2 water on `Player_mode` and `Apparent_zone_and_act`, disabling it for Knuckles on direct load but enabling it during seamless AIZ1→AIZ2 transitions. Both cases now handled correctly via a `seamlessTransition` flag threaded through `WaterDataProvider`.

### Insta-Shield Implementation

Full S3K insta-shield ability implemented with ROM parity:
- ROM constants, art key, and art loading (including cross-game donation path).
- Activation via `tryShieldAbility()` with character gating (Sonic only, not Tails/Knuckles).
- Hitbox expansion in `TouchResponses` for the active insta-shield frames.
- Persistent `InstaShieldObjectInstance` lifecycle (survives level transitions).
- DPLC cache invalidation on seamless level transitions.
- Lazy art initialization to handle sprite-before-level-load ordering.
- Half-arc animation bug fix (prevented double-update per frame).

### Multi-Sidekick System

- Comma-separated sidekick config enables spawning multiple sidekicks (e.g. `"sonic,tails"`).
- `SidekickRespawnStrategy` interface extracted with `TailsRespawnStrategy` and per-character
  `requiresPhysics()` (Sonic walk-in vs Tails fly-in).
- Parallel sidekick respawn via effective leader reference.
- Virtual pattern ID range validation in `PatternAtlas` for safe multi-bank allocation.
- Sidekick DPLC banks placed in dedicated `0x30000+` range, capped at `0x800` limit with bank
  sharing on overflow.
- Sidekick rendered behind main player to match VDP sprite priority order.
- Leader reference preserved across `reset()` — sidekicks no longer become permanently idle.
- Directional input maintained during approach phase.
- Slot reclamation added to `PatternAtlas` for efficient VRAM management.

#### S3K Sidekick Knuckles Fixes

- **VRAM isolation**: every sidekick now unconditionally gets its own isolated pattern bank in the
  `SIDEKICK_PATTERN_BASE` (0x38000) range, eliminating sprite corruption when characters share
  the same ART_TILE base (Knuckles and Sonic both use 0x0680 in S3K). Removed the name-based
  `computeVramSlots` optimization that missed this collision.
- **Palette isolation**: per-sidekick `RenderContext` palette blocks loaded via
  `PlayerSpriteArtProvider.loadCharacterPalette()`. When a sidekick uses a different palette
  than the main character (e.g. Knuckles' `Pal_Knuckles` vs Sonic's `Pal_SonicTails`), a
  dedicated palette context is created so the sidekick renders with correct colors. Propagated
  to spindash dust and Tails tail appendage sub-renderers.
- **Knuckles glide-in respawn**: `KnucklesRespawnStrategy.requiresPhysics()` now returns
  `true` during the drop phase so the physics pipeline applies gravity. Previously Knuckles
  would hang in mid-air after the glide because `SpriteManager` skipped physics for all
  `APPROACHING` strategies. `GLIDE_DROP` animation set during the glide approach phase.
- **Palette texture resize safety**: `GraphicsManager.cachePaletteTexture()` now preserves
  existing palette data when the texture grows to accommodate new contexts, preventing level
  palette corruption on resize.

#### S3K Zone Bring-Up Skill System

A 7-skill agentic system for systematic, per-zone implementation of S3K visual and behavioural
features (events, parallax, animated tiles, palette cycling). Designed for agent-driven analysis
of the disassembly followed by parallel feature implementation across worktrees.

- **s3k-zone-analysis**: reads the S3K disassembly and produces a structured zone feature spec
  covering events, parallax, animated tiles, palette cycling, notable objects, and cross-cutting
  concerns. Includes Phase 4 shared state trace for cross-category dependency detection (VRAM
  ownership conflicts, palette mutation vs cycling overlaps, event flag gating).
- **s3k-zone-events**: implements `Sonic3kZoneEvents` subclasses porting `Dynamic_Resize` routines
  from the disassembly — camera locks, boss arenas, cutscenes, act transitions, palette mutations.
- **s3k-animated-tiles**: implements AniPLC script triggers in `Sonic3kPatternAnimator` with
  zone-specific gating conditions and dynamic art overrides.
- **s3k-palette-cycling**: implements or validates `AnPal` handlers in `Sonic3kPaletteCycler` using
  the counter/step/limit pattern. Supports both new implementation and validation of existing zones.
- **s3k-parallax** *(updated)*: now accepts a zone analysis spec as optional input to accelerate
  deform routine discovery.
- **s3k-zone-bring-up**: orchestrator that dispatches zone analysis, parallel feature agents in
  worktrees, merge reconciliation, build verification, and validation.
- **s3k-zone-validate**: visual validation via stable-retro reference screenshots compared against
  engine output using agent image recognition (feature presence, not pixel-perfect diffing).
- All skills published in dual format (`.claude/skills/` + `.agent/skills/`) for agent-agnostic use.
- YAML frontmatter standardised across all 20 `.claude/skills/` and 8 `.agent/skills/` files.
- HCZ zone analysis spec produced as smoke test (`docs/s3k-zones/hcz-analysis.md`).
- AIZ zone analysis cross-validated against engine implementation: events and palette cycling
  matched byte-for-byte; parallax matched 13/14 checks; animated tiles revealed 3 cross-category
  gating omissions that motivated the Phase 4 shared state trace addition.

### Tails AI Improvements

- Comprehensive Tails CPU AI rework:
  - WFZ/DEZ/SCZ now suppress the CPU sidekick in gameplay and rendering.
  - Tails switches to FLYING when Sonic dies instead of despawning.
  - Respawn uses ROM's 64-frame gate plus A/B/C/Start bypass, blocking on object-control,
    air, roll-jump, underwater, and prevent-respawn conditions.
  - Manual P2 override for gameplay and special stages.
  - PANIC mode reworked to use `move_lock` + frame-counter timing.
  - Flying/despawn reworked with on-screen checks, water clamp, exact landing criteria.
  - Boss/event updates wired for EHZ2, HTZ2, MCZ2, CNZ2, CPZ2, ARZ2, and MTZ3.
  - Special-stage Tails uses its own replay buffer + P2 takeover path.

### Rendering Pipeline Improvements

- **PatternAtlas slot reclamation**: freed VRAM slots can be reused by new pattern uploads.
- **Batched DPLC atlas updates**: `DynamicPatternBank` batches multiple pattern updates per frame
  instead of individual uploads.
- **Virtual pattern ID validation**: range checks prevent silent VRAM corruption from out-of-bounds
  pattern references.
- **FboHelper**: centralised FBO creation utility, migrated 4 renderer files.
- **writeQuad()** extracted from `BatchedPatternRenderer` for reuse.
- Fail-fast on shader compilation/linking errors with GL resource cleanup.

### Logging and Error Handling

- 22 `e.printStackTrace()` calls migrated to structured `java.util.logging`.
- 28 swallowed exceptions in S3K code replaced with `LOG.fine()`.
- Production `System.out.println` calls replaced with `LOGGER.fine()`.
- Remaining logging gaps fixed across 6 files.

### Performance

- Batched DPLC atlas updates in `DynamicPatternBank`.
- Cached `LevelManager` reference in `DefaultObjectServices` (eliminates per-call singleton lookup).
- Per-frame `ObjectSpawn` allocation eliminated in `AbstractBadnikInstance`.
- Pre-allocated debug overlay lists, collision/sensor/camera bounds command lists.
- Reduced per-frame allocations in collision, rendering, and audio hot paths.
- Batched glyph rendering for debug text.

### BizHawk Trace Replay Testing

A new automated accuracy verification system that records per-frame physics state from the real ROM
running in BizHawk emulator, then replays the same inputs through the engine and compares every
field frame-by-frame.

- **Lua trace recorder** (`tools/bizhawk/`): BizHawk Lua script that captures player position,
  speed, angle, ground mode, air/rolling flags, and controller input every frame during a BK2
  movie playback. Outputs `metadata.json`, `physics.csv`, and `aux_state.jsonl`.
- **stable-retro trace recorder** (`tools/retro/`): cross-platform Python equivalent of the
  BizHawk Lua recorder, using stable-retro (Genesis Plus GX) for headless emulation. Produces
  byte-identical output format (same CSV, JSONL, and metadata.json) consumed by the same Java
  test infrastructure. Supports stable-retro BK2 replay, BizHawk BK2 parsing, savestate boot,
  and credits demo recording. Enables trace generation on macOS and Linux without BizHawk.
  Verified byte-for-byte output match against BizHawk reference traces for first 2100+ frames
  of GHZ1 before GPGX version-specific lag frames diverge the runs.
- **stable-retro BK2 alignment** (`--bk2-offset`): replays BizHawk BK2 movies through
  stable-retro by shifting BK2 inputs to the emulator's gameplay start frame. Handles GPGX
  byte-swap, exact-0x0C game_mode detection, and `|system|P1|P2|` BK2 group parsing.
- **Lag frame handling in credits demo tests**: `AbstractCreditsDemoTraceReplayTest` now
  detects lag frames (identical physics state on consecutive frames with non-zero speed) and
  skips both engine physics and demo input advancement on those frames. Reduced MZ2 credits
  divergences from 28 errors/131 warnings to 10 errors/57 warnings. Remaining errors are
  genuine engine divergences (missed bounces, slope collision, object timing).
- **Trace replay test infrastructure** (`tests.trace` package): `TraceData` loader, `TraceFrame`
  parser, `TraceBinder` per-frame comparator with configurable tolerances, `DivergenceReport`
  with JSON output and context windows, lag frame detection for VBlank sync.
- **`AbstractTraceReplayTest`**: base class for trace replay tests with graceful skip when ROM,
  BK2, or trace data files are absent. Subclasses only specify game/zone/act/path.
- **First trace: S1 GHZ1** full-run recording (3,905 frames): passes with 0 errors, 6 warnings.
- **Second trace: S1 MZ1** full-run recording (7,936 frames): baseline added with
  `TestS1Mz1TraceReplay`, regenerated GHZ1 traces, and ROM-verified zone/act metadata.
- **Recorder/diagnostics upgrades**: trace format now captures subpixel position, player routine,
  camera state, ring count, raw status, `v_framecount`, `standOnObj`, slot dumps, routine-change
  events, and ObjPosLoad cursor state for direct ROM/engine comparison.
- **Engine-side context windows**: divergence reports now include ROM and engine routine/object
  diagnostics, riding-object context, and placement cursor counters to narrow parity failures.
- **Buzz Bomber proximity fix**: removed overcorrecting player position prediction from the
  proximity detection check. The engine's 1-frame late spawn (pre-camera X vs ROM's post-camera X)
  and the pre-physics player position naturally cancel, placing the Buzz Bomber at the correct
  stop position without prediction.
- **Post-camera object placement sync**: `LevelFrameStep` now runs a post-camera placement
  catch-up pass after the camera update, closing the spawn timing gap when the camera crosses a
  chunk boundary between object placement (step 2) and camera update (step 5).
- **Placement parity narrowing**: S1 `out_of_range` timing, dormant-spawn handling, and
  ObjPosLoad callback groundwork reduced the remaining MZ1 investigation to a terrain /
  solid-contact parity problem rather than cursor drift.

#### Physics Accuracy Fixes (discovered via trace replay)

- **16:16 fixed-point subpixel positions**: `AbstractSprite.move()` upgraded from 16:8 to 16:16
  fixed-point arithmetic, matching the ROM's 32-bit `move.l obX(a0),d2` / `asl.l #8,d0` /
  `add.l d0,d2` position update. `xSubpixel`/`ySubpixel` widened from `byte` to `short`.
  `setX()`/`setY()` no longer zero the subpixel fraction (ROM's `move.w` to x_pos doesn't
  touch x_sub). Collision adjustments use new `shiftX()`/`shiftY()` to preserve subpixel.
- **GroundMode enum order fix**: `LEFTWALL` and `RIGHTWALL` were swapped; corrected to match
  ROM's quadrant assignment (0x40 = LEFTWALL, 0xC0 = RIGHTWALL).
- **CalcRoomInFront probe quadrant**: wall probe now uses `anglePosQuadrant()` (asymmetric
  rounding matching ROM's AnglePos dispatch) instead of `(angle+0x20)&0xC0`. Fixes false wall
  detections at steep slope angles (e.g. rotated angle 0xA0).
- **CalcRoomInFront 32-bit prediction**: probe prediction uses full 16-bit subpixel, matching
  ROM's 32-bit position arithmetic.
- **Air collision landing split**: separated `doTerrainCollisionAirDirect()` for movement
  quadrants 0x40/0xC0 (land immediately when floor detected, no speed threshold) from
  quadrant 0x00 (speed-dependent threshold). Matches ROM's per-quadrant landing logic.
- **Double ground mode update**: second `updateGroundMode()` after `selectSensorWithAngle()`
  uses the new angle from terrain probes, matching ROM's end-of-frame ground mode calculation.
- **Arithmetic right shift for air drag**: `xSpeed / 32` changed to `xSpeed >> 5` to match
  68000's `asr.w #5,d1` which rounds toward negative infinity (Java `/` truncates toward zero).
- **Jump transition defers air physics**: on jump, air physics are deferred to the next frame
  (ROM's `addq.l #4,sp` pops the return address, skipping the rest of ground movement).
  `sprite.setOnObject(false)` now called before jump to match `bclr #sta_onObj`.
- **BCC carry flag parity**: spindash release speed clamp `gSpeed > 0` changed to `gSpeed >= 0`
  to match 68000's carry flag behavior (carry SET on unsigned overflow, BCC NOT taken for zero).
- **`groundWallCollisionEnabled` feature flag**: new `PhysicsFeatureSet` field. S1 does not
  call CalcRoomInFront during ground movement (no equivalent in `Sonic_MdNormal`); S2/S3K do.
- **Air-control superspeed preservation**: S3K now preserves airborne speeds already above
  `topSpeed` after ramps and springs, while S1/S2 retain the original hard cap. `TwistedRamp`
  tumble frames now remain visible while rolling.

#### Object System Fixes (discovered via trace replay)

- **Deterministic object iteration order**: active objects now sorted by spawn X position,
  matching ROM's slot-order correlation with spawn-window entry order.
- **Touch response timing**: `runTouchResponsesForPlayer()` extracted and called during the
  player physics tick (after `handleMovement()`, before solid contacts), matching ROM's
  ReactToItem timing within Sonic's ExecuteObjects slot.
- **S1 UNIFIED collision model in SpriteManager**: pre-movement solid pass skipped for S1
  (ROM processes all solid objects after Sonic's movement); post-movement pass with
  `postMovement=true` disables velocity classification adjustment.
- **SolidContacts post-movement parameter**: `updateSolidContacts()` gains `postMovement` and
  `deferSideToPostMovement` flags to support the S1/S2 collision timing difference.
- **ROM-accurate `out_of_range` semantics**: `AbstractObjectInstance.isInRange()` now matches the
  ROM's chunk-aligned X-only range check with 16-bit wraparound, and S1 now performs
  out-of-range deletion during object execution rather than before it.
- **Dormant spawn tracking**: objects deleted by S1 `out_of_range` stay dormant between ObjPosLoad
  cursors until the cursor naturally re-processes them, preventing premature or missing reloads
  during camera backtracking.
- **Standing/contact parity fixes**: `MvSonicOnPtfm` now uses `groundHalfHeight` (`d3`) for
  standing Y, HURT touch responses now remain continuous after invulnerability expires, and
  staircase / MTZ platform / nut / button / elevator contact state now uses ROM-style boolean
  latches instead of diverging frame counters.

### Object Lifecycle Safety

- Removed constructor-time `services()` usage from 38 object classes; all affected objects now
  lazily initialize renderer and service-dependent state after `ObjectServices` injection.
- `TestNoServicesInObjectConstructors` now hard-fails constructor-time service access, unsafe
  `addDynamicObject(new X(...))` patterns, and pre-registration method calls that transitively
  depend on injected services.
- Sonic 1 lava geysers now defer initialization until first update, preventing pre-registration
  crashes; the lavafall third piece also no longer cascade-spawns infinite children.

### Sonic 1 Fixes

- Drowning visuals: breathing air bubble animation frames and countdown number positioning corrected.
- LZ credits demo spike collision fix and frame tick ordering unification.
- Yadrin top-hit behaviour and underwater palette/animation fixes for LZ.
- Minor LZ fix for jumping while sliding.
- GHZ bridge collision fix with corresponding tests.
- Monitor collision fix (particularly when in a tree).
- Bubble breathing now uses the fallback animation chain correctly, so grabbing an air bubble shows
  the intended breathing animation instead of preserving the rolling/spinning pose.
- SLZ staircase activation now uses ROM-style per-frame contact latches and has dedicated headless
  regression coverage.
- Bubble makers, push blocks, and related S1 objects now use ROM-accurate range semantics; spike
  standing dimensions now match the ROM's `d2`/`d3` values, including sideways spike extension.

### Sonic 2 Fixes

- HTZ water configuration corrected (Hill Top Zone no longer reports water).
- Collapsing platforms in MCZ stay solid during fragment phase.
- Special stage results screen decoupled from object system.
- S2/S3K collapsing platforms remain solid during fragment phase.
- CPZ staircase, MTZ platforms, nuts, buttons, and elevators now use boolean contact latches
  instead of frame-counter comparisons, fixing activation regressions during title cards and
  multi-sprite updates.
- Invincibility stars (Obj35) rewritten to match s2disasm: star 0 orbits at player's current
  position with fast rotation ($12/frame), stars 1-3 trail behind via position history buffer
  (3/6/9 frames behind) with slow rotation ($02/frame). Each star renders 2 sub-sprites at
  180 degrees apart. Corrected orbit offset table (7 entries had wrong X values), animation
  tables (parent uses byte_1DB82; trailing stars use per-star primary/secondary tables), and
  direction-aware rotation (angle negated when facing left).
- RNG parity paths tightened through shared `GameRng` coverage and `Sonic2Rng` regression tests,
  including CNZ slot-machine consumers and S2 object/boss call sites.

### Cross-Game Feature Donation Enhancements

- S1 wired as donor for forward donation into S2/S3K (previously only S2/S3K could donate).
- `DonorCapabilities` interface replaces hardcoded game-specific branches.
- `CanonicalAnimation` enum provides a game-neutral animation vocabulary for cross-game translation.
- `AnimationTranslator` handles bidirectional profile translation between any pair of games.
- Spindash speed table sourced from donor `PhysicsFeatureSet`.
- Cross-game art keys promoted to `ObjectArtKeys` for game-agnostic constant references.
- Import leak cleanup: removed cross-game S2 animation ID imports from game-agnostic sidekick code.

### Test and Quality

- `SingletonResetExtension` and `@FullReset` for automated per-test singleton lifecycle.
- `GameRuntime` lifecycle wired into 35 test classes with optimized Surefire configuration.
- Multi-sidekick integration smoke tests.
- Insta-shield test suite: gating, hitbox expansion, and visual frame-by-frame capture.
- MutableLevel round-trip and integration tests.
- S3K results screen tally mechanics unit tests.
- S3K registry coverage tests for all zones.
- Per-character respawn strategy unit tests.
- Migration guard scanner for detecting `getInstance()` / `GameServices` violations in object code.
- Annotated guard tests for services() migration completeness.
- AudioManager.resetState() field-clearing verification.
- Added `TestS1Mz1TraceReplay`, `TestSonic1StaircaseActivation`, `TestAbstractObjectInstanceRange`,
  and expanded lava geyser / constructor-safety guard coverage.
- Fixed 7 test failures caused by leaked runtime state: updated S3K Knuckles physics assertion
  to expect `SONIC_3K_KNUCKLES` profile (jump=0x600), saved/restored `RuntimeManager` in render
  tests, guarded teardown camera calls with null checks, used `destroyForReinit()` for
  `TestGraphicsManagerHeadless`.

#### Test Suite Cleanup

Systematic audit and remediation of the test suite. Net result: +34 passing tests, 36→0 skipped,
no new failures.

- **Stale @Ignored stubs replaced with real tests**: `TestTodo14` (PlayerCharacter ordinals),
  `TestTodo13` (19 SBZ/FZ event routine tests), `TestTodo17` (boss flag gating), `TestTodo19`
  (rock debris table parity), `TestTodo34` (water slide chunk detection).
- **Broken live tests fixed**: `TestTodo3` (MonitorType reflection instead of test-local enum copy),
  `TestTodo37` (ROM-vs-engine constant parity via reflection).
- **Dead test files deleted**: 8 fully-@Ignored TestTodo stubs for unimplemented features (Yadrin
  spiky-top, Knuckles monitor, Super transform, rock width, rock push, ChopChop bubbles, control
  lockout, SBZ2 transition); 8 zero-assertion diagnostic dumps; `TestTodo29` (SCALE no-op).
- **Low-value tests pruned**: constant-equals-itself assertions (Knuckles cutscene timers, emerald
  scatter constants), ROM-only checks with no engine cross-reference (angle table size, CNZ
  romDataPresent), duplicate coverage (edge balance constants, water provider hasWater), test
  infrastructure self-tests (SharedLevel, InitStep fields).
- **Test uplifts**: `TestTodo1` cross-references ROM water heights against `Sonic2WaterDataProvider`;
  `TestTodo31` adds real end-game zone boundary assertions; 7 S3K palette cycling test files (AIZ2,
  CNZ, EMZ, HCZ, ICZ, LBZ, LRZ) strengthened from "color changed" to specific RGB value assertions
  using `Sonic3kPaletteCycler` with StubLevel; water data provider tests deduplicated between
  provider and handler files.
- **Integration gaps closed**: removed blocked @Ignored stubs from `TestGameLoop` (special stage
  mode) and `TestTodo4` (MCZ boss collision boxes); removed reference-file-dependent test from
  `TestSonic3kVoiceData`; removed diagnostic dump stubs from `TestS3kSonicSpriteDiag`.

### Documentation

- Comprehensive user guide for three audiences (players, developers, contributors).
- OpenSMPSDeck music tracker design spec and implementation plan.
- Rendering pipeline improvements spec and plan.
- Unified execution roadmap and Phase 0+1 implementation plan.
- GameRuntime architecture spec and implementation plan.
- Two-tier service architecture design spec and implementation plan.
- MutableLevel (Phase 3) spec and implementation plan.
- Insta-shield design spec and implementation plan.
- Multi-sidekick daisy chain design spec and implementation plan.
- Cross-game bidirectional animation donation design spec and implementation plan.
- Game-specific leak fixes spec and plan.
- Services migration cleanup design spec and implementation plan.
- Architectural fixes design spec, implementation plan, and review passes.
- Singleton lifecycle documentation.
- Phase 4 common refactoring design spec (5 phases, 25 patterns) and implementation plan (21 tasks).
- Virtual pattern IDs and multi-sidekick system documented in AGENTS.md.
- Known discrepancies documentation for multi-sidekick rendering.
- Added the `s1-trace-replay` skill and refreshed skill descriptions for the parity-driven
  object/boss/disassembly workflow docs.

## v0.4.20260304 (Released 2026-03-04)

Analysis range: `v0.3.20260206..v0.4.20260304` on `develop` (`1790` commits, `1589` non-merge commits,
`2040` files changed, `218141` insertions, `195996` deletions).

> Note: the large deletion count reflects the package rename from `uk.co.jamesj999.sonic` to
> `com.openggf`, which deleted and recreated most source files. Net code growth is ~22,100 lines.

### Sonic 1 Expansion and Content Completion

- Added full Sonic 1 title screen pipeline and title-screen-to-level-select flow
  (`Sonic1TitleScreenManager`, loader, mappings, transition handling).
- Implemented Sonic 1 rings and lamppost/checkpoint behavior.
- Implemented Sonic 1 special stage gameplay and integration:
  - Game-agnostic special stage provider refactor.
  - `Sonic1SpecialStageManager`, renderer/background renderer/data loader, block types, and results screen.
  - Giant Ring route from normal gameplay into special stage flow.
- Introduced per-zone event coverage for Sonic 1 with zone-specific managers/events for GHZ, MZ, SYZ,
  LZ (including water events), SLZ, SBZ, and ending/FZ handling.
- Major object implementation wave for Sonic 1: `117` new object-related classes
  (`78` general objects, `23` badnik classes, `16` boss-related classes).
- Boss coverage expanded to GHZ, MZ, SYZ, LZ, SLZ, and FZ with child objects/projectiles and event integration.
- Added/finished LZ water behavior and bubble systems, including per-ROM drowning music selection.
- Added ending/outro flow updates and initial credits sequence implementation.
- Added SBZ2 post-level-end sequence.
- Fixed S1 physics regressions with test coverage (multiple passes).

### Sonic 2 Gameplay Additions

- Added Sonic 2 title screen architecture and title-screen audio regression coverage.
- Added major object coverage passes:
  - Metropolis Zone object set (`16` objects) and engine crush detection.
  - Sky Chase/Tornado object set and spawn path integration.
  - Wing Fortress object set and supporting hazards/platforms.
  - Oil Ocean object and oil-surface behavior improvements.
- Added MCZ boss implementation (`Sonic2MCZBossInstance` + falling debris support) with follow-up fixes.
- Added MTZ Boss (Obj54) with S2 boss event stubs.
- Added WFZ Boss (ObjC5) with laser platform attack cycle, plus ROM-accuracy pass (17 issues).
- Added DEZ Mecha Sonic boss (ObjAF) with full state machine, plus ROM-accuracy pass (17 issues).
- Added DEZ Death Egg Robot (ObjC7) — final S2 boss, plus ROM-accuracy pass (12 issues).
- Added Robotnik escape sequence between DEZ boss fights (ObjC6).
- Six passes of DEZ boss ROM-accuracy corrections: Silver Sonic facing direction, LED overlay,
  animation phase gating, Egg Robo collision/render priorities, Death Egg Robot child systems.
- Added `61` new Sonic 2 object-related files (`45` general objects, `14` badnik classes, `2` boss files),
  including additional SCZ/WFZ/MTZ/OOZ badnik/object coverage.
- Refactored and expanded Sonic 2 zone events (`Sonic2LevelEventManager` + per-zone event classes).
- Implemented Sonic 2 credits and ending system:
  - `EndingPhase` enum, `EndingProvider` interface, and `ENDING_CUTSCENE` GameMode.
  - `Sonic2CreditsTextRenderer`, `Sonic2CreditsMappings`, `Sonic2CreditsData` with timing constants.
  - `Sonic2EndingCutsceneManager` and `Sonic2EndingArt` with DEZ star field background rendering.
  - `Sonic2LogoFlashManager` with ROM-accurate palette strobe.
  - `Sonic2EndingProvider` wired to DEZ boss ending trigger.
  - Rewritten for ROM parity with ObjCA/ObjCC, DPLC player sprites, tornado visibility.
  - `Sonic1EndingProvider` refactored to use shared `EndingProvider` interface.
- Added demo playback functionality with enhancements and routing to objects.
- Systematic TODO resolution pass: water heights, monitor effects, distortion table, sliding spikes,
  dual collision, Yadrin spiky-top collision, water slide control lockout, LZ rumbling SFX,
  boss flag wiring to AIZ pattern animations, plus TODO/FIXME coverage tests with disassembly validation.
- Various object fixes: PointPokey positioning, MCZRotPlatforms child accumulation, signpost/screen
  locking, object loading improvements, ROM-accurate bumper/bonus block/rising pillar/diagonal spring physics.

### Super Sonic and Per-Game Physics

- Added cross-game physics abstraction:
  - `PhysicsProfile`, `PhysicsFeatureSet`, `PhysicsModifiers`, `PhysicsProvider`, and `CollisionModel`.
  - Validation tests for profile behavior, collision model differences, spindash gating, and speed capping.
- Implemented Sonic 2 Super Sonic flow:
  - Base state machine via `SuperState`/`SuperStateController`.
  - Integration into playable sprite/game loop/module plumbing.
  - ROM-based animation loading, ROM-exact palette cycling, and S2 constants wiring.
  - Invulnerability/enemy-destruction behavior and shield/power-up interaction guards.
  - Debug toggle support and Super Sonic stars object support.
- Added Sonic 3K Super Sonic controller stub/hook points for future parity work.
- Added cross-game Super Sonic delegation to S1 and S2 game modules via `CrossGameFeatureProvider`,
  including palette, audio, and renderer integration, invincibility, and S3K slope animation offset.

### Sonic 3K Bring-Up (AIZ-Focused)

- Extended Sonic 3K bootstrap/audio readiness (voice/sfx index fixes, ROM loading fixes, SoundTestApp support).
- Implemented Angel Island intro cinematic pipeline:
  - AIZ event wiring and intro state-machine objects (`AizPlaneIntroInstance`, Knuckles cutscene objects,
    emerald scatter, wave/plane/glow/booster children).
  - Intro art loading/caching and terrain swap integration.
- Added AIZ gameplay object work with parity-focused fixes:
  - Ride vines and giant ride vines.
  - Hollow tree traversal and reveal/tilemap support.
  - Multiple parity fixes (angle bytes, state retention, endianness, momentum, despawn guards) plus regressions.
- Added AIZ miniboss object set and child components.
- Added initial S3K badnik framework and first wired badnik implementations.
- Added S3K shield object implementations and fixed deferred PLC loading after AIZ intro.
- Added Sonic 3K title card manager/mappings and S3K pattern/palette animation work.
- Implemented S3K water system:
  - Game-agnostic `WaterDataProvider` and `DynamicWaterHandler` interfaces.
  - `ThresholdTableWaterHandler` for table-driven water zones.
  - `Sonic3kWaterDataProvider` with static heights, dynamic handlers, and underwater palette loading.
  - `Sonic1WaterDataProvider` migration to the new provider architecture.
  - Wired into LevelManager and S3K zone features, deprecated game-specific water loading methods.
  - Correct water threshold tables, `setMeanDirect`, zone scope, and starting heights matching ROM.
  - S3K water locked flag, shake timer, LBZ2 pipe plug handler.
  - AIZ2 Knuckles water exclusion, raise speed inheritance, `update()` overshoot fixes.
- Implemented seamless AIZ fire transition flow (`S3kSeamlessMutationExecutor`).
- AIZ miniboss cutscene and barrel shot child updates.
- Expanded AIZ scroll handler work (`SwScrlAiz`).

### PLC, Art Loading, and Tooling

- Major PLC and sprite-pattern refactor across S1/S2/S3K pipelines.
- Added/expanded PLC systems:
  - `Sonic2PlcLoader`, `Sonic2PlcArtRegistry`, and broader S3K PLC loading paths.
  - Shared sprite/mapping loader use (`S1SpriteDataLoader`, `S2SpriteDataLoader`, `S3kSpriteDataLoader`).
- Expanded ROM/disassembly tooling:
  - Object profile abstractions per game (`Sonic1ObjectProfile`, `Sonic2ObjectProfile`, `Sonic3kObjectProfile`).
  - Shared-ID handling in S3K object checklist generation.
  - PLC cross-referencing in `RomOffsetFinder`/`DisassemblySearchTool` and `ObjectDiscoveryTool`.

### Audio, Stability, and Engine Hardening

- Audio updates:
  - Music/SFX catalog refactor to enum-driven paths.
  - PSG GPGX hybrid parity work and tests.
  - S3K pitch wrapping and SFX index fixes.
  - YM2612/SMPS fixes (including SSG-EG active-count leak and loop counter bounds).
  - Thread-safety fixes in SMPS/audio backend paths and output mixing saturation safeguards.
- Engine hardening and safety:
  - ROM read synchronization and bounds checks.
  - Kosinski/resource loading safety limits.
  - Graphics cleanup fixes (resource leaks, reset-state gaps, allocation reductions).
  - Additional stability fixes across water/drowning handling, invulnerability timing, and debug movement modifiers.
- Performance passes across level/render/audio hot paths and internal debug profiling updates.
- Fixed SFX channel replacement: kill old SFX track on shared channel to prevent priority lock.
- Synth-core review fixes: resource safety, encapsulation, dead code cleanup.
- HTZ earthquake fixes: descending through floor, tile display, rising lava subtype 4 hurt behaviour.
- Consolidated duplicate sine/cosine tables to `TrigLookupTable`.
- Fixed cross-game features breaking layer switchers.
- Fixed special stage transition softlocks and S1 results fade type.

### Test and Quality Coverage

- Added `83` new test files across this range, including:
  - Sonic 1 special stage, object, badnik, boss, and routing regressions.
  - Sonic 3K AIZ intro/state timeline/hollow tree traversal parity regressions.
  - Title screen audio regression coverage.
  - PSG/YM2612 and per-game physics/profile parity checks.
- Expanded headless and subsystem-focused tests in support of object/event/audio refactors.
- Added 21 headless bug reproduction tests for 17 reported S1/S2 bugs.
- JUnit 5 migration: deleted 54 self-verifying tests, replaced with parameterized tests.
- Parallelized test execution with 8 forked JVMs.
- Test grouping by level: merged headless tests sharing the same level load into groups
  (EHZ1: 4→1, ARZ1: 3→1, CNZ1: 3→1, HTZ1: 2→1, AIZ1: 2→1, GHZ1: 6→1), eliminating 14 redundant
  level loads.
- Added TODO/FIXME coverage tests with disassembly validation.

### Cross-Game Feature Donation

Implemented cross-game feature donation system: a donor game (S2 or S3K) provides player sprites,
spindash dust, physics, palettes, and SFX while the base game (e.g. S1) handles levels, collision,
objects, and music. Enabled via `CROSS_GAME_FEATURES_ENABLED` and `CROSS_GAME_SOURCE` config keys.

- `CrossGameFeatureProvider` singleton: opens donor ROM as secondary ROM (no module detection
  side-effect), creates game-specific art loaders (`Sonic2PlayerArt`/`Sonic3kPlayerArt`,
  `Sonic2DustArt`), builds hybrid `PhysicsFeatureSet` (spindash from donor, everything else S1),
  loads donor character palette, initializes donor audio.
- `RenderContext` palette isolation: base game occupies palette lines 0-3, each donor gets its own
  block of 4 lines (4-7, 8-11, etc.) via static registry with `getOrCreateDonor()`.
  `uploadDonorPalettes()` pushes donor palettes to GPU. `getDonorContexts()` for iteration.
- `GameId` enum with `fromCode()` for type-safe donor identification.
- `RomManager.getSecondaryRom()` opens donor ROM without triggering game module detection.
- `LevelManager` art loading paths (`initPlayerSpriteArt`, `initSpindashDust`, `initTailsTails`)
  check `CrossGameFeatureProvider.isActive()` and delegate to donor art providers, attaching
  donor `RenderContext` to each `PlayerSpriteRenderer`.
- `Engine` initialization gates sidekick spawning on `GameModule.supportsSidekick()` or
  `CrossGameFeatureProvider.isActive()`, with cleanup on shutdown.
- GPU palette texture dynamically resized via `RenderContext.getTotalPaletteLines()`. All shaders
  (`shader_the_hedgehog`, `shader_tilemap`, `shader_water`, `shader_sprite_priority`,
  `shader_instanced_priority`, `shader_cnz_slots`) updated from hardcoded `/4.0` to
  `/TotalPaletteLines` uniform.
- Underwater palette derivation for donor sprites:
  - `RenderContext.deriveUnderwaterPalette()` synthesizes donor underwater colors using the base
    game's global average per-channel color shift ratio (not per-index, which would mismatch
    palette layouts across games).
  - `GraphicsManager.cacheUnderwaterPaletteTexture()` extended to populate donor palette rows
    automatically from the base game's normal-to-underwater shift.
- Donor SMPS driver config for correct SFX playback:
  - `SmpsSequencerConfig` threaded through `AudioManager.registerDonorLoader()` (4-arg overload),
    stored per donor game in `donorConfigs` map.
  - `AudioBackend.playSfxSmps()` 4-arg overload accepting explicit config; `LWJGLAudioBackend`
    uses donor config when provided, falling back to base game config.
  - `CrossGameFeatureProvider.initializeDonorAudio()` passes `donorProfile.getSequencerConfig()`.
- Donor audio overlay in `AudioManager`: `donorLoaders`, `donorDacData`, `donorSoundBindings` maps;
  `playSfx()` falls through to donor path when base game sound map has no entry.
- S3K Tails tail appendage support: `CrossGameFeatureProvider.hasSeparateTailsTailArt()` and
  `loadTailsTailArt()` delegate to donor's `Sonic3kPlayerArt` for separate Obj05 tail art.
  `LevelManager.initTailsTails()` checks donor game module when cross-game is active, selecting
  correct art loading path and `ANI_SELECTION_S3K` animation tables.
- SFX re-trigger fix in `SmpsDriver`: re-triggering the same SFX ID now replaces the old sequencer
  instead of competing for the same FM/PSG channels (prevents priority lock ping-pong with S1/S2
  jump SFX priority 0x80).
- Tests: `TestRenderContext` (9 tests covering palette isolation, line allocation, reset,
  underwater palette derivation), `TestDonorAudioRouting` (donor SFX routing and sequencer config),
  `TestGameId`, `TestHybridPhysicsFeatureSet`, `TestSidekickGating`.

### Master Title Screen

- Implemented `MasterTitleScreen` (404 lines): engine-wide title screen displayed on startup before
  entering game-specific title flow. PNG-based background, animated clouds, title emblem, and game
  selection text rendered via `TexturedQuadRenderer` and `PixelFont`.
- New rendering infrastructure: `PngTextureLoader` (85 lines), `TexturedQuadRenderer` (139 lines),
  `PixelFont` (144 lines), `shader_rgba_texture` vertex/fragment shaders.
- Configurable via `TITLE_SCREEN_ON_STARTUP` config key (default: enabled).

### Sonic 1 Fixes and Improvements

- Fixed Sonic spawning 5px underneath terrain on level reset by restoring standing radii in
  `AbstractPlayableSprite` respawn path (ROM: `Obj01_Init` unconditionally sets `y_radius=$13`).
- Object collision fixes: `ObjectManager` solid overlap test now always uses `airHalfHeight`
  matching ROM behaviour (d3 is overwritten by playerYRadius before read). Added
  `Sonic1ButtonObjectInstance` and `Sonic1MzBrickObjectInstance` collision support.
  `TestHeadlessSonic1ObjectCollision` (291 lines) regression test added.
- Fixed edge balance mode for S1 (single balance state, force face edge) while preserving S2's
  4-state extended balance. `PhysicsFeatureSet.extendedEdgeBalance` gates behaviour.
  `TestEdgeBalance` (91 lines) and `TestHeadlessSonic1EdgeBalance` (369 lines) added.
- Fixed MZ2 push block: longer blocks no longer get pushed "out of the way" when Sonic pushes them
  against walls. `SolidContact` improvements. `TestHeadlessMZ2PushBlockGap` (132 lines) added.
- SBZ fixes: Flamethrower positioning corrected for vflip/hflip variants. StomperDoor objects fixed.
  Junction now locks the player correctly. SBZ3 water oscillation implemented.
- LZ fixes: Wind tunnels now play correct player animation. Breakable poles play correct animation.
  Water splash effect implemented (`Sonic1SplashObjectInstance`).
- Demo playback now sent to objects (`AbstractPlayableSprite` demo input routing).
- Push stability fixes for solid objects. `TestHeadlessSonic1PushStability` (220 lines) added.
- Outro/credits improvements (`Sonic1CreditsManager`, `FadeManager` enhancements).
- `TestSbz1CreditsDemoBug` (162 lines) and `TestS1FlamethrowerObjectRendering` (58 lines) added.
- S1 "fast" mode SMPS sequencer support.
- S1 outro improvements: disable control on outro, change 'back to main menu' key.
- S1 ending sequence flowers fix.
- S1 object collision fixes.

### Sonic 2 Fixes

- Fixed badnik palette lines (Spiny now uses palette line 1 matching `make_art_tile`), signpost
  frame order corrected to match `obj0D_a.asm` ROM mapping order, CPZ stair block / MTZ platform
  art sheet rebuilt with hand-crafted mappings (ROM mappings reference level art tiles).
- Swinging platform art loading fix for non-S2 games.
- S2 ending cutscene parity: DEZ white fade (not black), star field background, pilot visibility,
  BG scroll compensation, DPLC player sprites, tornado visibility, falling timing.
- Prevented DEZ Robot despawn during defeat ending sequence.
- Fixed DEZ boss visual and collision issues (multiple passes).
- Fixed S2 credits visual accuracy: ROM-correct font, mappings, and player detection.
- Fixed S2 `Sonic2LevelEventManager` zone constants alignment with `ZoneRegistry`.

### Physics and Collision Fixes

- Fixed solid object edge jitter: `SolidContacts` snaps player to resolved edge on static solids
  to prevent subpixel accumulation. Push-driven objects opt in to ROM-style subpixel preservation
  via `SolidObjectProvider.preservesEdgeSubpixelMotion()`.
- S1 slope crest sensor guard: prefer floor-class probe over wall-class probe at crest transitions,
  preventing one-frame wall/air mode flips.
  `TestHeadlessStaticObjectPushStability` (208 lines) and
  `TestSonic1GhzSlopeTopDiagnostic` (519 lines) added.
- Sonic no longer jumps if the player holds jump while airborne via a non-jump (spring, slope
  launch, etc.).
- Various physics tweaks aimed at S1: physics modifiers cleanup, `FadeManager` fade-to-black
  transitions no longer flash back to "off" briefly before fade-in begins.
- Fixed results screen rendering issue for both S1 and S2.

### Package Rename

- Renamed root package from `uk.co.jamesj999.sonic` to `com.openggf` across the entire codebase.
  All source files, test files, and references updated.

### Profile-Driven Level Loading

- Introduced `LevelInitProfile` abstraction with `InitStep` and `StaticFixup` primitives for
  declarative, ROM-aligned level loading.
- Implemented per-game profiles (`Sonic1LevelInitProfile`, `Sonic2LevelInitProfile`,
  `Sonic3kLevelInitProfile`) with 13 finer-grained ROM-aligned steps each.
- `LevelLoadContext` provides shared state across load steps.
- `LevelManager.loadLevel()` routed through profile steps; old fallback path removed.
- Per-step timing and logging for load diagnostics.
- Profile-driven teardown and per-test reset replaces `TestEnvironment` and `GameContext.forTesting()`.
- `CHARACTER_APPEAR` phase uses `Map_Sonic`/`Map_Tails` Float2 animation.

### Testability Refactor

- `GameContext` holder with `production()` and `forTesting()` factories for singleton lifecycle.
- `SharedLevel` for reusable level loading across test classes.
- `HeadlessTestFixture` builder pattern for test setup, with 14 test classes converted.
- `TestEnvironment.resetAll()` delegates to `GameContext.forTesting()` for consistent teardown.

### Docs and Planning

- Added release-planning/implementation docs for unified level events, Super Sonic, and AIZ intro work.
- Added cross-game donation fixes design doc and implementation plan.
- Added `docs/CONFIGURATION.md` with full config key reference.
- Expanded disassembly/reference and skill documentation used for parity-driven object/boss implementation workflows.
- Added DEZ boss fixes design and implementation plans.
- Added Sonic 2 credits and ending sequence design and implementation plans.
- Added cross-game Super Sonic design and implementation plan.
- Added S3K water system design and implementation plan.
- Added testability improvement design (GameContext + HeadlessTestFixture) and implementation plan.
- Added headless test level grouping design and implementation plan.
- Added profile-driven level loading plans (Phase 3 and Phase 4).
- Added ending parallax background design and implementation plan.
- Added level editor design and implementation plan.
- Added ROM-driven init profiles design and implementation plan.

## v0.3.20260206

366 commits, 541 files changed, ~99,000 lines added.

### Multi-Game Architecture

- Complete engine refactor to support multiple Sonic games through a provider-based abstraction layer
  - `GameModule` interface defines 15+ provider methods for all game-specific behaviour
  - `GameModuleRegistry` singleton holds the active game module
  - `RomDetectionService` auto-detects ROM type via registered `RomDetector` implementations
- New provider interfaces: `ZoneRegistry`, `ObjectRegistry`, `ObjectArtProvider`, `ZoneArtProvider`,
  `ScrollHandlerProvider`, `ZoneFeatureProvider`, `RomOffsetProvider`, `SpecialStageProvider`,
  `BonusStageProvider`, `DebugModeProvider`, `DebugOverlayProvider`, `TitleCardProvider`,
  `LevelEventProvider`, `ResultsScreen`, `MiniGameProvider`
- `GameServices` facade for centralised access to `gameState()`, `timers()`, `rom()`, `debugOverlay()`
- NoOp implementations for optional providers (`NoOpBonusStageProvider`, `NoOpSpecialStageProvider`, etc.)
- Sonic 2 fully migrated to provider architecture (`Sonic2GameModule` and all provider implementations)
- `Sonic2Constants.java` expanded by 663+ lines of ROM offset constants
- `Sonic2ObjectIds.java` expanded with 118 new object type ID constants

### Tails (Miles Prower) - Playable Character

- `Tails.java` playable sprite: shorter height (30px vs Sonic's 32px), adjusted sensor offsets (±15px vs ±19px), otherwise identical physics
- `TailsCpuController.java` ROM-accurate AI follower with 5-state machine: `INIT`, `NORMAL` (input replay), `FLYING` (helicopter chase), `PANIC` (spindash escape), `SPAWNING` (respawn wait)
- Input replay system: Tails replays Sonic's recorded inputs from 17 frames ago via position/status history buffer
- AI overrides: direction correction when >16px off, forced jumps when Sonic is 32+ pixels above, spindash escape every 128 frames when stuck >120 frames
- Despawn after 300 frames off-screen, respawn 192 pixels above Sonic when safe
- `TailsTailsController.java` (Obj05): separate rotating tails animation with 10 states (Blank, Swish, Flick, Directional, Spindash, Skidding, Pushing, Hanging)
- Art loaded from ROM at `0x64320` (uncompressed, `0xB8C0` bytes) with separate mappings and reversed mappings
- Configurable via `SIDEKICK_CHARACTER_CODE` in config.json: `"tails"` (default), `""` to disable, `"sonic"` for Sonic clone
- Can be spawned as main player character or as CPU-controlled sidekick
- Flying mode bypasses normal physics, using direct position updates for aerial chase
- Per-player riding state: solid object contacts refactored to `IdentityHashMap` so Sonic and Tails can independently ride different platforms (13 files updated)
- Test: `TestTailsCpuController` covering state transitions, input replay, distance gating, despawn/respawn

### Sonic 1 Initial Support (23 new files, 3,729 lines)

- ROM auto-detection via `Sonic1RomDetector` (header-based)
- `Sonic1.java` game entry point with level loading and data decompression from S1 ROM
- `Sonic1Level.java` implementing S1-specific level data format (different structure from S2)
- `Sonic1ZoneRegistry` covering all 7 zones: Green Hill, Marble, Spring Yard, Labyrinth, Star Light, Scrap Brain, Final
- `Sonic1Constants.java` with verified ROM addresses for S1 REV01
- `Sonic1PlayerArt.java` loading player sprites with S1-specific mapping format
- Parallax scroll handlers for all 7 zones (`SwScrlGhz`, `SwScrlMz`, `SwScrlSyz`, `SwScrlLz`, `SwScrlSlz`, `SwScrlSbz`, `SwScrlFz`)
- `Sonic1PatternAnimator` for S1 tile animation scripts (waterfall, flowers, lava, conveyors)
- `Sonic1PaletteCycler` for S1 zone-specific palette cycling
- `Sonic1AudioProfile` and `Sonic1SmpsData` for S1 ROM audio playback via SMPS driver
- `Sonic1ObjectRegistry` and `Sonic1ObjectPlacement` stubs for S1 object format parsing
- `Sonic1LevelSelectManager` (394 lines): 21-item vertical menu with zone/act selection, wrap-around navigation, sound test
- `Sonic1LevelSelectDataLoader` and `Sonic1LevelSelectConstants` for ROM-based graphics and layout
- `LevelSelectProvider` interface extracted for game-agnostic level select support
- `Sonic1TitleCardManager` (468 lines), `Sonic1TitleCardMappings` (306 lines): S1-specific title card rendering
- `Sonic1ObjectArtProvider` for S1 HUD rendering (life icons, ring display)
- Tests: `TestGhzChunkDiagnostic` (GHZ chunk loading), `Sonic1PlayerArtTest` (player sprite loading)

### Physics Engine

#### Core Physics Rewrite
- Complete physics rewrite in `PlayableSpriteMovement` (1,814 lines, replacing 1,134-line predecessor)
- Movement modes now explicitly mirror ROM state machine: `Obj01_MdNormal` (ground walking), `Obj01_MdRoll` (ground rolling), `Obj01_MdAir`/`MdJump` (airborne)
- ROM-accurate slope resistance/repulsion formulas with correct angle offset (0x20) and mask (0xC0)
- Slope repel minimum speed threshold (0x280) matching ROM `Sonic_SlopeRepel`
- Rolling physics: dedicated roll deceleration (0x20), controlled roll constants, minimum start roll speed gating
- Spindash fully reimplemented using ROM speed table (`s2.asm:37294`) indexed by `spindash_counter >> 8`
- Spindash counter charging/decay logic matching ROM `Sonic_UpdateSpindash`
- Fixed subpixel accuracy: subpixels were not being used correctly in velocity/position calculations
- Near-apex air drag implemented (when -1024 <= ySpeed < 0), matching ROM `Sonic_MdJump` behaviour
- Upward velocity cap added at -0xFC0
- Roll height adjustment fixed (5px to 10px) for all roll-mode transitions, preventing visual "fall" on transition
- ROM-identical angle/quadrant selection table in `TrigLookupTable` (256 entries from `misc/angles.bin`)
- `calcAngle()` method exactly matching ROM `CalcAngle` routine (s2.asm:4033-4076)
- Jump angle calculation and slope angle assist/repel gating adjustments

#### Player Mechanics
- Pinball mode flag (`pinballMode`) preventing rolling from being cleared on landing; gives boost instead of stopping at speed 0. Used by CNZ tubes, blue balls, launcher springs. Preserved through launcher spring bounces
- Ledge balance animation with 4 balance states matching ROM (BALANCE through BALANCE4) based on proximity and facing direction (s2.asm:36246-36373)
- Look up/down delay counter (`lookDelayCounter`) matching ROM `Sonic_Look_delay_counter` timing
- Spring control lock fixed to only apply when grounded (was incorrectly locking controls in air)
- Run animation starts the moment left/right are pressed
- Three distinct control lock types matching ROM: `objectControlled` (blocks all input), `moveLocked` (blocks directional but allows jump), `springing` (blocks grounded directional)
- Signpost walk-off fix: control lock no longer cancels forced input, allowing Sonic to properly walk off-screen after act end
- Position history buffer (64 entries) for camera lag and spindash compensation

#### Collision System
- New unified `CollisionSystem` (214 lines) orchestrating a 3-phase pipeline:
  1. Terrain probes (ground/ceiling/wall sensors via `TerrainCollisionManager`)
  2. Solid object resolution (platforms, moving solids via `ObjectManager.SolidContacts`)
  3. Post-resolution adjustments (ground mode, headroom checks)
- Supports trace recording via `CollisionTrace` interface for debugging and testing
- `GroundSensor` rewrite (437 lines): separated vertical scanning (floor/ceiling) from horizontal scanning (walls), ROM-accurate negative metric handling, full-tile edge detection with previous-tile lookback, horizontal wall scanning with regress/extend states
- Collision order fix: solid objects now processed before terrain, preventing objects from being overridden
- Sensor adjustment timing changed to earlier in the tick
- Collision path reset on level switch to prevent falling through levels on wrong layer
- Ceiling collision improvements: better ceiling sensors on walls/ceilings, angle-based landing detection, ceiling mode (0x80) correctly adjusts only Y velocity
- Wall pushing fix
- Solid object landing now resets ground mode and angle (matching solid tile landing)
- New `ObjectTerrainUtils` (296 lines) for game object terrain collision (floor, ceiling, left wall, right wall), mirroring ROM `ObjCheckFloorDist`

### Camera

- Complete vertical scroll rewrite matching ROM behaviour:
  - Y position bias system (`Camera_Y_pos_bias`) with default value of 96
  - Look up bias (200) with gradual 2px/frame increment
  - Look down bias (8) with gradual 2px/frame decrement
  - Bias easing back to default at 2px/frame
  - Grounded scroll speed cap: 2px (looking), 6px (normal), 16px (fast, inertia >= 0x800)
- Airborne camera uses +/-32px window around current bias matching ROM `ScrollVerti` airborne path
- Horizontal scroll delay (`horizScrollDelayFrames`) replaces old `framesBehind` system. Matches ROM where `ScrollHoriz` checks `Horiz_scroll_delay_val` but `ScrollVerti` does not
- Rolling height compensation: camera subtracts 5px from Y delta when rolling (1px for Tails)
- Spindash camera fixed to use horizontal scroll delay rather than full camera freeze
- Screen shake system: `shakeOffsetX`/`shakeOffsetY` with `getXWithShake()`/`getYWithShake()` for rendering (used by HTZ earthquake)
- Boundary clamping to `minX`/`minY` (was only clamping to 0)
- Full freeze (death/cutscenes) now separate from horizontal scroll delay

### Water System

- Complete `WaterSystem` (462 lines): water level loaded from ROM at correct height, water oscillation in CPZ2 via `OscillationManager`, water surface sprites rendering in front of solid tiles
- `WaterSurfaceManager` (282 lines) for surface sprite management
- Water surface sprites appear for CPZ2, ARZ1, and ARZ2
- Water entry/exit detection based on player centre Y vs water surface
- Underwater physics: speed halving on water entry (xSpeed/2, ySpeed/4), halved acceleration/deceleration/max speed, corrected jump height, corrected hurt gravity and launch amount
- `DrowningController` (289 lines): 30-second air timer with frame-accurate countdown, warning chimes at air levels 25/20/15, drowning countdown music at air level 12, countdown number bubbles (5/4/3/2/1/0), breathing bubble spawning, music restart on water exit or air replenishment, air bubble collection with 35-frame control lock
- Water collision aligned with oscillating visual position in CPZ2
- HUD text no longer turns red on water levels
- Special Stage results no longer overwrite water surface sprite

### Boss Fights

#### Boss Framework (game-agnostic)
- `AbstractBossInstance` (530 lines) base class: hit points, invincibility frames, state machine, defeat sequences, camera locking, explosion cascades
- `AbstractBossChild` (109 lines) base class for multi-component boss sub-objects
- `BossChildComponent` interface (45 lines) and `BossStateContext` (72 lines) for shared state
- `BossExplosionObjectInstance` for shared boss explosion effects
- `CameraBounds` for boss arena camera locking

#### Implemented Bosses
- **EHZ Boss** (Drill Car, Obj56) - `Sonic2EHZBossInstance` with 6 child components: ground vehicle, propeller, spike drill, vehicle top, wheels, animations helper
- **CPZ Boss** (Water Dropper, Obj5D) - `Sonic2CPZBossInstance` with 14 child components: container (extend, floor), dripper, falling parts, flame, gunk hazard, pipes (pump, segment), pump, Robotnik sprite, smoke puffs, animations helper
- **HTZ Boss** (Lava Flamethrower, Obj52) - `Sonic2HTZBossInstance` with flamethrower, lava ball projectiles, smoke particles. Lava bubble spawned on ground impact
- **CNZ Boss** (Electricity, Obj51) - `Sonic2CNZBossInstance` with electric ball projectiles, animations helper
- **ARZ Boss** (Hammer/Arrow, Obj89) - `Sonic2ARZBossInstance` with arrow projectiles, eye tracking component, destructible pillars
- **Egg Prison** (Obj3E) - End-of-act capsule with button, animal escape sequence, and destruction

### Badniks (15+ New Enemies)

#### CPZ
- **Spiny** (Obj A5) - Wall-crawling spike enemy
- **Spiny on Wall** (Obj A6) - Ceiling variant
- **Grabber** (Obj A7) - Descends to capture player

#### ARZ
- **ChopChop** (Obj 91) - Piranha fish that lunges at player
- **Whisp** (Obj 8C) - Floating dragonfly enemy
- **Grounder** (Obj 8D/8E) - Mole that hides behind breakable wall, throws rock projectiles
  - GrounderWallInstance (Obj 8F) - Breakable wall
  - GrounderRockProjectile (Obj 90) - Rock projectiles

#### HTZ
- **Rexon** (Obj 94/96) - Multi-segment lava-dwelling serpent
  - RexonHeadObjectInstance (Obj 97) - Shootable head segment
- **Sol** (Obj 95) - Fireball-shooting enemy with SolFireballObjectInstance
- **Spiker** (Obj 92) - Drill badnik with SpikerDrillObjectInstance (Obj 93) projectile

#### CNZ
- **Crawl** (Obj C8) - Bouncing boxing glove enemy

#### MCZ
- **Crawlton** (Obj 9E) - Snake that lunges with trailing body segments
- **Flasher** (Obj A3) - Firefly that flashes invulnerability

#### Badnik Framework
- Enhanced `AbstractBadnikInstance` base class
- Improved `AnimalObjectInstance` escape behaviour
- Enhanced `BadnikProjectileInstance` framework
- `PointsObjectInstance` moved to objects package (score popup display)

### Game Objects (50+ New)

#### Platforms and Moving Objects
- **SwingingPlatformObjectInstance** (Obj15) - Chain-suspended pendulum platform (OOZ, ARZ, MCZ)
- **SwingingPformObjectInstance** (Obj82) - ARZ swinging vine platform
- **CPZPlatformObjectInstance** (Obj19) - CPZ rotating/moving platforms
- **ARZPlatformObjectInstance** (Obj18) - ARZ-specific platform
- **MTZPlatformObjectInstance** (Obj6B) - Multi-purpose platform with 12 movement subtypes
- **SidewaysPformObjectInstance** (Obj7A) - CPZ/MCZ horizontal moving platform
- **MCZRotPformsObjectInstance** (Obj6A) - MCZ wooden crate / MTZ rotating platforms
- **ARZRotPformsObjectInstance** (Obj83) - 3 platforms orbiting centre
- **CollapsingPlatformObjectInstance** (Obj1F) - OOZ/MCZ/ARZ collapsing platform
- **SeesawObjectInstance** (Obj14) + **SeesawBallObjectInstance** - HTZ catapult seesaw with ball physics
- **HTZLiftObjectInstance** (Obj16) - HTZ zipline/diagonal lift
- **ElevatorObjectInstance** (ObjD5) - CNZ vertical moving elevator
- **CNZBigBlockObjectInstance** (ObjD4) - CNZ 64x64 oscillating platform
- **CNZRectBlocksObjectInstance** (ObjD2) - CNZ flashing "caterpillar" blocks
- **InvisibleBlockObjectInstance** (Obj74) - Invisible solid block

#### Hazards and Traps
- **RisingLavaObjectInstance** (Obj30) - HTZ invisible solid lava platform during earthquakes
- **LavaMarkerObjectInstance** (Obj31) - HTZ/MTZ invisible lava hazard collision zone
- **LavaBubbleObjectInstance** (Obj20) - Lava bubble visual effects
- **SmashableGroundObjectInstance** (Obj2F) - HTZ breakable rock platform
- **FallingPillarObjectInstance** (Obj23) - ARZ pillar that drops lower section
- **RisingPillarObjectInstance** (Obj2B) - ARZ pillar that rises and launches player
- **ArrowShooterObjectInstance** (Obj22) + **ArrowProjectileInstance** - ARZ arrow shooter trap
- **StomperObjectInstance** (Obj2A) - MCZ ceiling crusher
- **MCZBrickObjectInstance** (Obj75) - MCZ pushable/breakable brick
- **SlidingSpikesObjectInstance** (Obj76) - MCZ spike block sliding from wall
- **TippingFloorObjectInstance** (Obj0B) - CPZ tipping floor
- **BreakableBlockObjectInstance** (Obj32) - CPZ metal blocks / HTZ breakable rocks
- **BlueBallsObjectInstance** (Obj1D) - CPZ chemical droplet hazard
- **BombPrizeObjectInstance** (ObjD3) - CNZ slot machine bomb/spike penalty

#### Interactive Objects
- **SpringboardObjectInstance** (Obj40) - Pressure/lever spring (CPZ, ARZ, MCZ)
- **SpringHelper** - Shared spring velocity calculations
- **SpeedBoosterObjectInstance** (Obj1B) - CPZ/CNZ speed booster pad
- **ForcedSpinObjectInstance** (Obj84) - CNZ/HTZ forced spin (pinball mode trigger)
- **LauncherSpringObjectInstance** (Obj85) - CNZ pressure launcher spring
- **PipeExitSpringObjectInstance** (Obj7B) - CPZ warp tube exit spring
- **BarrierObjectInstance** (Obj2D) - One-way rising barrier (CPZ/HTZ/MTZ/ARZ/DEZ)
- **EggPrisonObjectInstance** (Obj3E) - End-of-act capsule with button, animal escape, destruction
- **SkidDustObjectInstance** - Skid dust particles
- **SplashObjectInstance** - Water splash effect

#### CPZ-Specific Objects
- **CPZSpinTubeObjectInstance** (Obj1E, 895 lines) - Full tube transport system
- **CPZStaircaseObjectInstance** (Obj78) - 4-piece triggered elevator platform
- **CPZPylonObjectInstance** (Obj7C) - Decorative background pylon

#### CNZ-Specific Objects
- **BumperObjectInstance** (Obj44) - Standard round bumper
- **HexBumperObjectInstance** (ObjD7) - Hexagonal bumper
- **BonusBlockObjectInstance** (ObjD8) - Drop target / bonus block (colour-changing, scoring)
- **FlipperObjectInstance** (Obj86) - Pinball flipper
- **CNZConveyorBeltObjectInstance** (Obj72) - Invisible velocity conveyor zone
- **PointPokeyObjectInstance** (ObjD6) - Cage that captures player and awards points
- **RingPrizeObjectInstance** (ObjDC) - Slot machine ring reward
- **CNZBumperManager** (574 lines) - Full bumper system with ROM-accurate bounce physics, 6 bumper types
- **CNZSlotMachineManager** (608 lines) + **CNZSlotMachineRenderer** (549 lines) - Complete slot machine system

#### ARZ-Specific Objects
- **BubbleGeneratorObjectInstance** (Obj24) - Spawns breathable bubbles underwater
- **BubbleObjectInstance** / **BreathingBubbleInstance** - Rising and breathable air bubbles
- **LeavesGeneratorObjectInstance** (Obj2C) + **LeafParticleObjectInstance** - Falling leaves on contact

#### MCZ-Specific Objects
- **VineSwitchObjectInstance** (Obj7F) - Pull switch triggering ButtonVine
- **MovingVineObjectInstance** (Obj80) - Vine pulley transport
- **MCZDrawbridgeObjectInstance** (Obj81) - Rotatable drawbridge triggered by VineSwitch
- **ButtonVineTriggerManager** - MCZ-specific vine routing

### Zone Improvements

#### Emerald Hill Zone (EHZ)
- Full boss fight (Act 2) with multi-component child objects
- Art used as base for HTZ overlay system

#### Chemical Plant Zone (CPZ)
- Full water implementation with oscillation in CPZ2
- Cycling palette implementation (water shimmer, chemical bubbles)
- Spin tubes, staircase platforms, blue balls, speed boosters, breakable blocks
- Spiny, Grabber, and Crawl badniks
- Full boss fight with multi-component gunk dropper
- Multiple collision and positioning fixes (tubes, staircases, platforms, blue balls)

#### Aquatic Ruin Zone (ARZ)
- Water surface sprites for ARZ1 and ARZ2
- Arrow shooters, swinging platforms, rotating platforms, rising/falling pillars
- ChopChop, Whisp, and Grounder badniks
- Leaves generator and leaf particle objects
- Full boss fight with hammer, arrows, and destructible pillars
- Collision fix: boss no longer attackable from the floor

#### Casino Night Zone (CNZ)
- Full bumper system with 6 bumper types and ROM-accurate bounce physics
- Complete slot machine system with shader rendering
- Flipper system (multiple rounds of fixes)
- New parallax scroll handler
- Conveyor belts, elevators, big blocks, rect blocks, point pokey, bonus blocks
- Crawl badnik
- Full boss fight with electric balls
- Physics fix: slopes no longer get stuck

#### Hill Top Zone (HTZ)
- Level resource overlay system: loads EHZ base data with HTZ-specific pattern overlays at byte offset 0x3F80 and block overlays at 0x0980. Shared chunks and collision indices
- Full earthquake system: dual architecture with `Camera_BG_Y_offset` (224-320) for BG vertical scroll and `SwScrl_RippleData` (0-3px) for screen jitter
- Earthquake trigger coordinates: Act 1 camera X >= 0x1800, Y >= 0x400; Act 2 camera X >= 0x14C0
- Rising lava with invisible solid platform, lava markers, lava bubble effects
- HTZ dynamic art loaded from ROM instead of disassembly files
- New parallax scroll handler with correct BG rendering
- Seesaws with ball physics, smashable ground, lifts, launcher springs, barriers
- Rexon, Sol, and Spiker badniks
- Full boss fight with lava flamethrower

#### Mystic Cave Zone (MCZ)
- Crawlton and Flasher badniks
- Bricks, drawbridges, vine switches, moving vines, rotating platforms, stompers, sliding spikes

#### Sky Chase Zone (SCZ)
- `SwScrlScz` (207 lines): ROM-accurate scroll handler with Tornado-driven camera movement
- BG X advances at 0.5px/frame via 16.16 fixed-point accumulator, BG Y always 0
- Act 1 phase system: fly right → descend → resume right, triggered by camera position thresholds

#### Oil Ocean Zone (OOZ)
- Full multi-layer parallax background with oil surface effects (`SwScrlOoz`, 395 lines)

#### Metropolis Zone (MTZ)
- MTZ platform with 12 movement subtypes

#### General Level System
- `LevelEventManager` massively expanded (+1,026 lines): dynamic camera boundaries, boss arenas, zone-specific event triggers, HTZ earthquake coordination
- `OscillationManager` extracted into proper abstraction (drives water oscillation, platform cycles)
- `ParallaxManager` expanded (+249 lines) with enhanced scroll offset calculations
- `BackgroundRenderer` reworked (+324 lines)
- Palette cycling system rewritten: `Sonic2PaletteCycler` (578 lines) with per-zone scripts from ROM
- Tile animation system rewritten: `Sonic2PatternAnimator` (343 lines) with ROM-based scripts
- `Sonic2LevelAnimationManager` consolidating both pattern animation and palette cycling

### Level Resource Overlay System (New)

- `LevelResourcePlan` (221 lines) - Declarative resource loading with overlay composition
- `LoadOp` (49 lines) - Individual load operations with ROM address, compression type, destination offset
- `ResourceLoader` (175 lines) - Performs loading with copy-on-write overlay pattern
- `CompressionType` enum - Nemesis, Kosinski, Enigma, Saxman, Uncompressed
- `Sonic2LevelResourcePlans` (108 lines) - Factory for zone-specific resource plans
- Overlays never mutate cached data (copy-on-write pattern)
- Tests: `LevelResourceOverlayTest` (333 lines)

### Graphics and Rendering

#### Backend Migration
- Complete migration from JOGL to LWJGL for both graphics and audio backends (multiple commits)
- GLFW window management replaces previous windowing system
- Initially tried OpenGL 4.1 core profile, settled on OpenGL 2.1 compatibility profile for broader hardware support
- Fixed shader loading when packaged as JAR
- DPI-aware window scaling via `GLFW_SCALE_TO_MONITOR`

#### GPU Rendering Pipeline
- **Pattern atlas system**: all 8x8 tile patterns uploaded to a single GPU texture (`PatternAtlas`, 326 lines) with multi-atlas fallback and buffer pooling
- **GPU tilemap renderer** (`TilemapGpuRenderer`, 198 lines): dedicated `TilemapShaderProgram` (172 lines) and `TilemapTexture` (78 lines) for GPU-side tile lookup. Covers background, water, and foreground layers. Configurable fallback to CPU rendering
- **Instanced sprite batching** (`InstancedPatternRenderer`, 696 lines): per-instance attributes with `glDrawArraysInstanced`. Enabled by default when supported, automatic fallback to existing batcher. Includes instanced water shader sync
- **Shared fullscreen quad VBO** (`QuadRenderer`, 50 lines): replaces all immediate-mode fullscreen quads across tilemap, parallax, fade, and special stage renderers
- **Priority rendering** (`TilePriorityFBO`, 177 lines): framebuffer object for tile priority bit rendering, enabling correct sprite-behind-tile ordering via GPU
- **Pattern lookup buffer** (`PatternLookupBuffer`, 70 lines): GPU-side pattern index lookup for tilemap shader

#### New Shaders
- `shader_tilemap.glsl` (132 lines) - GPU tilemap lookup and rendering
- `shader_water.glsl` (105 lines) - Water surface effects with palette-based tinting
- `shader_instanced.vert` (27 lines) - Instanced sprite vertex shader
- `shader_instanced_priority.glsl` (93 lines) - Instanced rendering with priority bit
- `shader_sprite_priority.glsl` (91 lines) - Sprite-behind-tile priority rendering
- `shader_cnz_slots.glsl` (131 lines) - CNZ slot machine display
- `shader_debug_text.frag`/`shader_debug_text.vert` (69 lines) - Debug text glyph rendering
- `shader_debug_color.vert`, `shader_basic.vert`, `shader_fullscreen.vert` - Utility shaders

#### UI Render Pipeline
- `UiRenderPipeline` (104 lines): ordered rendering phases (Scene, HUD Overlay, Fade pass)
- `RenderPhase` enum, `RenderCommand` interface, `RenderOrderRecorder` for testing

#### Debug Overlay Rendering
- Batched glyph rendering using GPU-accelerated glyph atlas texture
- Multi-size fonts with smooth anti-aliased outlines
- Proper viewport-space projection and DPI scaling
- Crisp texture filtering, correct Y-flip orientation
- Glyph atlas size increased to 1024x1024
- Bold font for SMALL and MEDIUM debug text sizes, capped at 32pt maximum
- `DebugPrimitiveRenderer` (72 lines) with `DebugColorShaderProgram` for collision/sensor overlays
- Collision overlay accessible via backtick key

#### Other Rendering Changes
- `FadeManager` rewritten for LWJGL compatibility
- `ScreenshotCapture` (231 lines) for visual regression testing
- Slot machine rendering moved from CPU to shader
- VBO sprite rendering

### Audio Engine

#### YM2612 FM Synthesis
- Complete rewrite based on Genesis-Plus-GX (GPGX) reference: SIN_HBITS/ENV_HBITS changed from 12 to 10, LFO changed from 1024-step sine to 128-step inverted triangle, TL table restructured, output clipping changed to asymmetric GPGX-style (+8191/-8192)
- `ENV_QUIET` threshold: when envelope exceeds threshold, operator output forced to 0, causing feedback buffer to naturally decay (matching real hardware)
- SSG-EG (SSG envelope generator) support
- Phase generator detune overflow matching Nemesis-verified real hardware behaviour (DT_BITS = 17)
- Internal sample rate output: YM2612 can output at CLOCK/144 (~53267 Hz) with proper band-limited resampling via `BlipDeltaBuffer` (330 lines) and `BlipResampler` (200 lines)
- Fixed operator routing order and TL position in voice format
- Fixed voice format parsing that was causing corruption and muted instruments
- Fixed low output volume
- Multiple rounds of accuracy improvements (5+ commits)

#### PSG (SN76489)
- New Experimental (Off by Default) PSG implementation with anti-aliasing
- `PsgChipGPGX` (378 lines) added as alternate implementation based on Genesis-Plus-GX (reference for future noise channel work)
- Clock divider fixed to 32.0, Noise Mode 3 corrected
- Clock speed and period calculation fixed
- Extensive spindash release SFX fixes (PSG modulation, note-off, tone bleed, noise channel)
- Default to original PSG after noise channel issues, keeping GPGX as reference

#### SMPS Driver
- Fixed frequency wrapping for high notes
- Fixed E7 command handling
- Fixed octave shifts during modulation/detune
- Fixed fill/gate time logic causing audio desync
- Fixed missing noise channel
- Refactored driver locking to prevent concurrent modification between SFX and music
- `SmpsSequencerConfig` abstraction: configurable per-game (Sonic 1 vs Sonic 2 differences in instrument loading, pitch offsets, noise channel handling)
- Sonic 1 SMPS driver accuracy fixes:
  - PSG envelope 1-based indexing: S1 `subq.w #1,d0` before table lookup; VoiceIndex=0 means no envelope
  - FM voice operator order conversion: S1 (Op4,Op3,Op2,Op1) swapped to engine's S2 format (Op4,Op2,Op3,Op1) on load
  - PC-relative pointer addressing: S1 F6/F7/F8 commands use `dc.w loc-*-1` offsets vs S2 absolute Z80 addresses
  - TIMEOUT tempo mode: S1 uses countdown-based tempo (extend durations on wrap) vs S2 accumulator overflow
  - PSG base note: S1 PSGSetFreq subtracts 0x81 (table starts at C), so `getPsgBaseNoteOffset()` returns 0
  - SFX tempo bypass: S1 SFX have normalTempo=0; skip duration extension in TIMEOUT mode when sfxMode=true
  - First-frame tempo processing matching S1 DOTEMPO behaviour

#### Sound Effects and Music
- Fixed extra life music restore (multiple playbacks no longer break original music)
- Fixed SFX-over-music priority and channel management
- Spindash release SFX: extensive multi-commit effort (14+ commits) fixing looping, noise timing, tone bleed, modulation enable, artifact prevention, overlapping playback. Invalid FM transpose value patched
- Level select: music fade on transitions, ring sound removed, double-fade fixed
- Gloop sound toggle moved from Z80 driver to `BlueBallsObjectInstance`

#### Audio Backend
- Migrated from JOAL to LWJGL OpenAL (`LWJGLAudioBackend`, 427 lines). Includes `WavDecoder` for WAV file support
- Fixed audio quality degradation in LWJGL backend
- Audio latency reduced to 16ms (one frame)
- Window minimize/restore handling: pauses audio so music doesn't play in background

#### Audio Performance
- Eliminated per-sample allocations in VirtualSynthesizer, SmpsSequencer, SmpsDriver scratch buffers
- Audio engine performance optimisations verified via regression tests

### Manager Consolidation Refactor

#### ObjectManager
- `ObjectManager.java` grew from ~200 to ~1,917 lines, absorbing 4 removed managers as inner classes:
  - `ObjectManager.Placement` (was `ObjectPlacementManager`) - Spawn windowing, remembered objects
  - `ObjectManager.SolidContacts` (was `SolidObjectManager`, -455 lines) - Riding, landing, ceiling, side collision
  - `ObjectManager.TouchResponses` (was `TouchResponseManager`, -195 lines) - Enemy bounce, hurt, category detection
  - `ObjectManager.PlaneSwitchers` (was `PlaneSwitcherManager`, -143 lines) - Plane switching logic

#### RingManager
- Consolidated from 3 separate managers as inner classes:
  - `RingManager.RingPlacement` (was `RingPlacementManager`, -93 lines) - Collection state, sparkle animation
  - `RingManager.RingRenderer` (was `RingRenderManager`, -114 lines) - Ring rendering with cached patterns
  - `RingManager.LostRingPool` (was `LostRingManager`, -304 lines) - Lost ring physics, object pooling

#### PlayableSprite Controller
- `PlayableSpriteController` (38 lines) coordinator owned by `AbstractPlayableSprite`:
  - `PlayableSpriteMovement` (1,814 lines, replaces `PlayableSpriteMovementManager`)
  - `PlayableSpriteAnimation` (renamed from `PlayableSpriteAnimationManager`)
  - `SpindashDustController` (renamed from `SpindashDustManager`)
  - `DrowningController` (289 lines, new)
- Removed `SpriteCollisionManager` (-131 lines)

#### CollisionSystem
- `CollisionSystem` (214 lines) unifying terrain probes and solid object collision
- `CollisionTrace` (40 lines), `RecordingCollisionTrace` (121 lines), `NoOpCollisionTrace` (25 lines), `CollisionEvent` (44 lines)

#### Animation System
- `Sonic2LevelAnimationManager` consolidating `AnimatedPatternManager` and `AnimatedPaletteManager`
- `Sonic2PatternAnimator` renamed from `Sonic2AnimatedPatternManager`
- `Sonic2PaletteCycler` (578 lines) replacing `Sonic2PaletteCycleManager` (-143 lines)

### Object System Framework

- `ObjectArtKeys` - Game-agnostic art key constants
- `MultiPieceSolidProvider` interface for objects with multiple solid collision pieces
- `SlopedSolidProvider` interface for sloped solid objects
- Enhanced `AbstractObjectInstance` (+55 lines), `ObjectInstance` interface (+34 lines)
- `ObjectRenderManager` significantly enhanced rendering pipeline
- `ObjectArtData` enhanced art loading (+169 lines)
- `HudRenderManager` enhanced HUD rendering (+155 lines)
- `SolidObjectProvider` extended interface
- Game-specific art loading pattern: `Sonic2ObjectArt`, `Sonic2ObjectArtProvider`, `Sonic2ObjectArtKeys`
- LayerSwitcher (Obj03) handled by PlaneSwitchers subsystem, not as rendered object

### Level Select

- `LevelSelectManager` (762 lines) - Full level select screen with keyboard navigation, zone/act selection, music playback
- `LevelSelectDataLoader` (485 lines) - Loads graphics, fonts, and preview images from ROM
- `LevelSelectConstants` (240 lines) - ROM addresses and layout data
- Palette loaded from ROM
- Menu background: `MenuBackgroundAnimator`, `MenuBackgroundDataLoader`, `MenuBackgroundRenderer` (292 lines total)
- Sound test integration via shared `Sonic2SoundTestCatalog`
- Configurable via `LEVEL_SELECT_ENABLED` config key
- Palette reset on returning to level select from gameplay
- Music fade on level select transitions

### Testing Infrastructure

#### HeadlessTestRunner
- `HeadlessTestRunner` (137 lines): physics/collision integration tests without OpenGL context
- `stepFrame(up, down, left, right, jump)` to simulate one frame with input
- `stepIdleFrames(n)` for stepping multiple idle frames
- Calls `Camera.updatePosition()`, `LevelEventManager.update()`, `ParallaxManager.update()` each frame

#### Physics and Collision Tests
- `TestHeadlessWallCollision` (133 lines) - Ground collision and walking physics
- `TestPlayableSpriteMovement` (1,483 lines) - Comprehensive movement physics tests
- `CollisionSystemTest` (460 lines) - Unified collision pipeline
- `WaterPhysicsTest` (250 lines) - Underwater physics
- `WaterSystemTest` (178 lines) - Water level system

#### Zone-Specific Tests
- `TestCNZCeilingStateExit` (403 lines), `TestCNZFlipperLaunch` (190 lines), `TestCNZForcedSpinTunnel` (193 lines), `SwScrlCnzTest` (454 lines)
- `TestHTZBossArtPalette`, `TestHTZBossChildObjects` (181 lines), `TestHTZBossEventRoutine9`, `TestHTZBossTouchResponse` (133 lines)
- `TestHTZInvisibleWallBug` (731 lines), `TestHTZRisingLavaDisassemblyParity` (115 lines), `TestSwScrlHtzEarthquakeMode` (86 lines), `TestHtzSpringLoop` (197 lines)
- `SwScrlOozTest` (487 lines), `TestOozAnimation` (248 lines), `TestPaletteCycling` (101 lines)

#### Visual Regression Tests
- `VisualRegressionTest` (393 lines) - Screenshot comparison testing
- `VisualReferenceGenerator` (265 lines) - Generate reference screenshots
- `ScreenshotCapture` (231 lines) - Headless screenshot capture
- Reference images for EHZ, CPZ, CNZ, HTZ, MCZ

#### Audio Regression Tests
- `AudioRegressionTest` (370 lines) - Audio output comparison
- `AudioReferenceGenerator` (302 lines) - Generate reference audio
- `AudioBenchmark` (109 lines) - Audio performance benchmarks
- Reference WAVs for EHZ/CPZ/HTZ music, jump/ring/spring/spindash SFX
- `TestSmpsSequencerInstrumentLoading` - SMPS instrument loading verification

#### Other Tests
- `TestSignpostWalkOff` - Signpost walk-off regression test
- `TestTailsCpuController` - Tails AI state machine and input replay
- `TestObjectManagerLifecycle` (108 lines), `TestObjectPlacementManager` (40 lines), `TestSolidObjectManager` (148 lines)
- `BossStateContextTest` (220 lines), `FadeManagerTest` (535 lines), `RenderOrderTest` (181 lines)
- `PatternAtlasFallbackTest` (33 lines), `TestSpriteManagerRender` (211 lines)
- `LevelResourceOverlayTest` (333 lines)

#### Test Annotation Framework
- `@RequiresRom(SonicGame.SONIC_1)` annotation for tests needing a real ROM file
- `@RequiresGameModule(SonicGame.SONIC_1)` annotation for tests needing a game module without ROM
- `RequiresRomRule` JUnit rule with per-game ROM resolution and auto-detection
- `SonicGame` enum: `SONIC_1`, `SONIC_2`, `SONIC_3K`
- `RomCache` for shared ROM instances across test classes

### Performance Optimisations

- Pre-allocated command lists in `LevelManager` (collisionCommands, sensorCommands, cameraBoundsCommands) using `.clear()` instead of `new ArrayList<>()` each frame
- ObjectManager and SpriteManager pre-bucketing with dirty flag to avoid re-sorting every frame
- `Sonic2SpecialStageRenderer` PatternDesc reuse instead of per-frame allocation
- Lost ring object pooling to reduce allocations during ring scatter
- Reduced per-frame allocations in collision, rendering, and audio hot paths
- Debug overlay buffer reuse
- Per-sample allocation elimination in audio synthesis pipeline
- `PerformanceProfiler` with memory stats (GC and allocation timers), Ctrl+P copies all stats to clipboard
- General memory allocation reduction passes across the engine

### GraalVM Native Build Support

- GraalVM Native Image plugin configuration in `pom.xml`
- GitHub Actions release workflow (`.github/workflows/release.yml`, 128 lines) and CI workflow
- GraalVM configuration files: `native-image.properties`, `reflect-config.json`, `resource-config.json`, `jni-config.json`
- LWJGL migration (both graphics and audio) as prerequisite for GraalVM compatibility
- `run.cmd` for Windows execution

### Tooling

#### RomOffsetFinder Enhancements
- `verify <label>` command - Verifies calculated offset against actual ROM data
- `verify-batch [type]` command - Batch verify all items of a type (shows [OK], [!!] mismatch, [??] not found)
- `export <type> [prefix]` command - Export verified offsets as Java constants
- Offset validation for searched items
- Multi-game support via `--game` flag: `--game s1`, `--game s2` (default), `--game s3k`
- `GameProfile` with per-game anchor offsets, label prefixes, ROM filenames, and disasm paths
- Auto-detection from disassembly path (`s1disasm` → S1, `skdisasm` → S3K)
- Expanded anchor offsets in `RomOffsetCalculator` for improved accuracy
- `CompressionTestTool` auto-detect compression type at offset
- Kosinski Moduled (KosM) decompression support in `KosinskiReader.decompressModuled()` — container format wrapping multiple standard Kosinski modules with 16-byte aligned padding, used extensively by Sonic 3&K art assets
- Palette macro parsing support from disassembly

#### New Tools
- `WaterHeightFinder` (114 lines) - Finds water height data in ROM
- `AudioSfxExporter` (261 lines) - Exports SFX audio data
- `SoundTestApp` refactored to use shared `Sonic2SoundTestCatalog` with channel mute/solo support

#### Claude Skills
- `.claude/skills/implement-object/SKILL.md` (410 lines) - Guided object implementation workflow
- `.claude/skills/implement-boss/skill.md` (332 lines) - Boss implementation workflow
- `.claude/skills/s2disasm-guide/skill.md` (302 lines) - Disassembly reference guide

### Other Changes

- Per-game ROM configuration: `SONIC_1_ROM`, `SONIC_2_ROM`, `SONIC_3K_ROM` config keys with `DEFAULT_ROM` selector; `ROM_FILENAME` removed entirely
- Pause functionality (default key: Enter) and frame step when paused (default key: Q)
- `ConfigMigrationService` (95 lines) for evolving configuration format
- `GameStateManager` (104 lines) for score, lives, emeralds state management
- Window minimize/restore: pauses audio and rendering to prevent background playback and catch-up
- `docs/KNOWN_DISCREPANCIES.md` (161 lines) documenting intentional divergences from original ROM
- Old markdown docs archived to `docs/archive/`
- `AGENTS.md` and `CLAUDE.md` extensively updated with architecture documentation

---

## v0.2.20260117

Improvements and fixes across the board. Special stages are now implemented, feature complete with
a few known issues. Physics have been improved, parallax backgrounds implemented and complete for
EHZ, CPZ, ARZ and MCZ. Some sound improvements, title cards, level 'outros' etc.

## v0.1.20260110

Now vaguely resembles the actual Sonic 2 game. Real collision and graphics data is loaded from the
Sonic 2 ROM and rendered on screen. The majority of the physics are in place, although it is far
from perfect. A system for loading game objects has been created, along with an implementation for
most of the objects and Badniks in Emerald Hill Zone. Rings are implemented, life and score tracking
is implemented. SoundFX and music are implemented. Everything has room for improvement, but this
now resembles a playable game.

## V0.05 (2015-04-09)

Little more than a tech demo. Sonic is able to run and jump and collide with terrain in a reasonably
correct way. No graphics have yet been implemented so it's a moving white box on a black background.

## V0.01 (Pre-Alpha) (Unreleased; first documented 2013-05-22)

A moving black box. This version will be complete when we have an unskinned box that can traverse
terrain in the same way Sonic would in the original game.
