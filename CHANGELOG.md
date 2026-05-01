# Changelog

All notable changes to the OpenGGF project are documented in this file.

## Unreleased

### v0.6.prerelease (Current development snapshot)

- **AIZ Mini-boss F7552 — sidekick hurt-airborne boundary clamp now
  matches ROM order (MOVE before BOUNDARY).** ROM `Obj01_Hurt`
  (s2.asm:37820-37834), `Sonic_Hurt` (s1disasm/_incObj/01
  Sonic.asm:1791-1804), and S3K `loc_122D8`/`loc_156D6`
  (sonic3k.asm:24449-24467, 29194-29209) all run
  `MoveSprite_TestGravity2`/`ObjectMove`/`SpeedToPos` BEFORE
  `Sonic_LevelBound`/`Tails_Check_Screen_Boundaries` for routine 4
  (hurt). The engine's `PlayableSpriteMovement.modeAirborne` ran the
  boundary check pre-move for both normal and hurt airborne paths,
  which lost one frame of lateral motion against
  `Camera_max_X_pos+$128` during hurt knockback. AIZ Mini-boss F7552
  trace expected `tails_x=0x1208, tails_x_speed=0x0000` and engine
  produced `tails_x=0x1207, tails_x_speed=0x0200` (off-by-one px,
  one frame behind on the right-edge clamp). Engine now reorders
  the hurt airborne path: `doObjectMoveAndFall` → underwater
  gravity reduction → `updateSensors` → `doLevelCollision`
  (Sonic_HurtStop equivalent) → `doLevelBoundary`. AIZ first-error
  advances 7552 → 7660 (errors 977 → 975). CNZ first-error at F7919
  unchanged. S1 GHZ / S1 MZ1 / S2 EHZ trace replays remain GREEN.

- **Trace visualizer ghost characters.** Test-mode visual trace sessions now
  render grayscale, distance-faded ghost copies of the traced main character
  and first sidekick during desyncs. Ghosts hydrate only render state from the
  trace, keep isolated sidekick-style DPLC banks so their animation/art state
  cannot corrupt real players or sidekicks, share the mirrored character's
  render bucket and tile-priority layer, and draw behind the live characters.

- **CNZ F=621 Clamer re-fire — ROM dispatch path narrowed; F=621
  fire mechanism requires recorder extension to localise
  (doc-only, round 2).** Continues from the prior F619 dispatch
  surfacing round. ROM-side trace established that
  `Check_PlayerCollision` (sonic3k.asm:179904-179916) consumes
  `collision_property(a0)` written by `Touch_Special`
  (sonic3k.asm:21162-21194) inside `Touch_Loop` — it is NOT a
  geometric overlap test. The spring child re-adds itself to
  `Collision_response_list` only when running `loc_890AA` (via
  `jmp Child_DrawTouch_Sprite` at sonic3k.asm:185962); the
  cooldown `loc_890C8` (sonic3k.asm:185965-185968) does not call
  `Add_SpriteToCollisionResponseList`. Under that schedule the
  spring child should be absent from the F=620-populated list
  that Sonic's F=621 `TouchResponse` walks — yet the trace
  records ROM firing the spring at F=621. Two candidate
  hypotheses (`$2E` cooldown counter init non-zero, or
  `collision_property` being written by an alternate dispatcher
  such as `HyperTouch_Special` at sonic3k.asm:21401-21402)
  documented; neither matches the observed F=619/F=621 cadence
  without additional recorder data. Engine probes inside
  `ObjectManager.processMultiRegionTouch` and
  `ClamerObjectInstance.update` confirmed no engine Clamer
  instance is active in the F=619-625 window of the engine run;
  the existing `SPRING_RELATCH_COLLISION_FLAGS = $40|$12`
  widening papers over the F=621 dispatch divergence at a
  different player position than ROM. No code change landed:
  recorder needs extension to capture per-frame
  `Collision_response_list` membership and each object's
  `collision_property` byte at the moment Sonic's
  `TouchResponse` runs. Probes reverted before commit. Trace
  replay baselines preserved: CNZ stable at F7919/2757, AIZ
  stable at F7552/977, S1/S2 PASS. Comparison-only invariant
  preserved.
- **S3K AIZ F7552 round-4 audit — regenerated trace inspected, divergence
  isolated to boundary clamp + `Tails_DoLevelCollision` wall push pair
  (doc-only).** With the regenerated v6.13-s3k AIZ fixture's new
  `terrain_wall_sensor_per_frame` events at F7549-F7560 in hand, ROM's
  F7552 Tails state advances `0x1207 -> 0x1208` via TWO sequential
  writes: (1) `Tails_Check_Screen_Boundaries` `loc_14F5C` boundary clamp
  to `0x1207` (recorded as `pc=0x14F60 val=0x1207` in
  `position_write_per_frame`), then (2) a `Tails_DoLevelCollision` wall
  push of `+1` to `0x1208` (uses `add.w/sub.w`, not `move.w`, so it
  doesn't appear in `position_write` events the v6.13 recorder hooks).
  Engine fires NEITHER write — `tails_x` simply integrates from
  `0x1205` to `0x1207` via `MoveSprite`, and end-of-frame state has
  `tails_x=0x1207, tails_x_speed=0x0200, tails_x_sub` non-zero. Engine
  `doLevelBoundary()` reads `camera.getMaxX() = 0x4640` (raw
  `LevelSizes.AIZ2 xend`); ROM's `Camera_max_X_pos` at F7552 is
  effectively `~0x10DF` (right-edge of the AIZ Mini-boss arena). Round
  4 located one ROM `move.l` write that hits `Camera_max_X_pos`
  ($00100010 longword at sonic3k.asm:104758-104759 in
  `AIZ1_AIZ2_Transition`), but the trace's `aiz2_reload_resume`
  checkpoint may take a different path. Round 5 plan: extend the
  recorder's `aiz_boundary_state_per_frame` (currently F4660-F4679
  only) to ALSO cover F7549-F7560, using the same multi-window pattern
  v6.13 added for `velocity_write_per_frame` /
  `position_write_per_frame`, then the next regen makes ROM's
  `Camera_min/max_X_pos` at F7552 directly visible and the engine fix
  becomes a `Sonic3kAIZEvents.updateAiz2SonicResize2` line that calls
  `camera().setMaxX(<that value>)` in lockstep with the existing
  `setMinX(0xF50)` lock. No engine code changed this round, no trace
  fixture change. Cross-game baselines: S3K AIZ first-error stable at
  F7552/977 errors, S3K CNZ stable at F7919. Comparison-only invariant
  preserved.
