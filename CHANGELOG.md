# Changelog

All notable changes to the OpenGGF project are documented in this file.

## Unreleased

### v0.6.prerelease (Current development snapshot)

- **S2 badnik child spawn safety: migrated direct `objectManager.addDynamicObject()`
  calls to `spawnFreeChild()` for `BalkiryBadnikInstance`, `AsteronBadnikInstance`,
  and `AquisBadnikInstance`.** Follow-up to the prior S1 badnik migration covering
  the three S2 badniks explicitly named alongside the S1 ones in the F1b
  architectural-fix review. Balkiry's jet-exhaust child, Asteron's
  explosion + 5-spike-projectile burst, and Aquis's bullet projectile were all
  calling `services().objectManager().addDynamicObject(child)` directly,
  bypassing `AbstractObjectInstance.CONSTRUCTION_CONTEXT`. Routing through
  `spawnFreeChild(Supplier)` sets the construction-time `ObjectServices`
  ThreadLocal before the child factory runs and preserves the ROM-equivalent
  `FindFreeObj` (low-slot) allocation semantics that the prior
  `addDynamicObject` path had. Slot ordering, child types, and spawn timing are
  byte-for-byte identical; the only behavioral difference is that child
  constructors may now safely call `services()`. The full S2 test suite stays
  on its prior baseline (4119 passed, 44 failed, 23 errors — pre-existing,
  unrelated `TestSonic1SBZEvents` etc. configuration failures, identical
  before and after the change). Other S1/S2 object instances still call
  `objectManager.addDynamicObject` directly (~63 S1 callers plus several S2
  badniks such as Octus/Slicer/Shellcracker/Sol/Turtloid) and will be migrated
  in subsequent passes; this commit covers only the badniks explicitly listed
  in the F1b architectural review.
- **S1 badnik child spawn safety: migrated direct `objectManager.addDynamicObject()`
  calls to `spawnFreeChild()` for `Sonic1BallHogBadnikInstance`,
  `Sonic1BombBadnikInstance`, `Sonic1CaterkillerBadnikInstance`, and
  `Sonic1OrbinautBadnikInstance`.** The four S1 badniks that ROM-spawn projectile,
  fuse/explosion/shrapnel, body-segment, or orbiting-spike children were calling
  `services().objectManager().addDynamicObject(child)` directly. That bypasses
  `AbstractObjectInstance.CONSTRUCTION_CONTEXT`, so any child constructor that
  invokes `services()` would throw `IllegalStateException` when reached through
  these spawn paths. Routing through the inherited `spawnFreeChild(Supplier)`
  helper sets the construction-time `ObjectServices` ThreadLocal before the child
  factory runs, preserves the ROM-equivalent `FindFreeObj` (low-slot) allocation
  semantics that the prior `addDynamicObject` call had, and keeps the existing
  `allocateSlotAfter` chains for Bomb/Caterkiller/Orbinaut intact (the helper is
  a no-op when a slot is already pre-assigned). Slot ordering, child types, and
  spawn timing are byte-for-byte the same; the only behavioral difference is that
  child constructors may now safely call `services()`. Per-object regression tests
  (`TestSonic1CaterkillerBodyChaining`, `TestSonic1LabyrinthObjectsBasic`) and the
  S1 trace replays (`TestS1Ghz1TraceReplay`, `TestS1Mz1TraceReplay`,
  `TestS1Credits00Ghz1TraceReplay`, `TestS1Credits06Sbz2TraceReplay`,
  `TestS1Credits07Ghz1bTraceReplay`) all stay green; the five pre-existing credits
  trace failures (Mz2/Syz3/Lz3/Slz3/Sbz1) are unchanged in count and first-error
  frame relative to the baseline. Other S1 object instances still call
  `objectManager.addDynamicObject` directly and will be migrated in subsequent
  passes; the listed badniks are the highest-risk subset because they are the
  ones explicitly named in the F1b architectural-fix task and the ones whose
  child types are most likely to gain `services()`-using constructors next.
- **S1 badnik/object subpixel math: migrated to shared `SubpixelMotion` helper.**
  ~17 Sonic 1 badnik and object instances each maintained their own private
  `xSubpixel` / `ySubpixel` int fields and reimplemented 16:8 (`<<8`) or 16.16
  (`<<16`) ROM fixed-point integration inline (`pos = (px << 8) | sub; pos +=
  vel; ...`). All occurrences in `game.sonic1.objects` (Crabmeat, Caterkiller
  head + body, Cannonball, Chopper, Motobug, Newtron, Roller, Yadrin, Orbinaut
  + spike, BallHog, Gargoyle fireball, GirderBlock, LavaBall, LavaGeyser,
  LavaWall, PushBlock) now consolidate the accumulators into a single
  `SubpixelMotion.State motion` field per class and call
  `SubpixelMotion.moveSprite` / `moveSprite2` / `moveX` / `speedToPos` /
  `speedToPosY` for the integration. Existing (sometimes ROM-divergent)
  semantics are preserved verbatim -- e.g. Cannonball/BallHog still apply
  gravity *before* the Y move via a manual pre-increment + `moveSprite2`,
  Gargoyle's fireball X-only path remains numerically identical for its
  `±$200` velocity, and PushBlock's slow-sink direct 16.16 add and per-axis
  velocity guards stay byte-for-byte the same. Pre-existing baseline test
  failures (S1 trace replays, S3K trace replays, etc.) are unchanged in count
  and first-error frame, confirming zero regression.
- **HCZ wall chase: migrated BG-high overlay render and active flag off
  `LevelManager` / `GameStateManager`.** The S3K-only inline render method
  `LevelManager.renderBgHighPriorityOverlay()` (and its caller in
  `LevelManager.draw()`) and the matching
  `GameStateManager.bgHighPriorityOverlayActive` field were a zone-specific
  leak in shared infrastructure -- the same architectural concern just fixed
  for HTZ in the previous commit. The overlay was extracted into a new
  `HczWallChaseBgOverlayEffect` (`com.openggf.game.sonic3k.render`) that
  registers itself at the `AFTER_SPRITES` stage from
  `Sonic3kZoneFeatureProvider`. The active flag storage moved into
  `Sonic3kHCZEvents.wallChaseBgOverlayActive` (now the canonical source for
  `HczZoneRuntimeState.wallChaseBgOverlayActive()`); a private
  `setWallChaseBgOverlayActive(boolean)` setter encapsulates the
  activation/deactivation transitions previously written through
  `gameState().setBgHighPriorityOverlayActive(...)`. The
  `bgHighPriorityOverlayActive` field plus its getter/setter on
  `GameStateManager` are gone. HCZ-specific reference counts in
  `LevelManager.java` and `GameStateManager.java` dropped to comments only
  (no runtime references).
- **HTZ earthquake: migrated BG-high overlay render and active flag off
  `LevelManager` / `GameStateManager`.** The HTZ-only inline render method
  `LevelManager.renderHtzEarthquakeBgHighOverlay()` (and its caller in
  `LevelManager.draw()`) and the matching `GameStateManager.htzScreenShakeActive`
  field were a long-standing zone-specific leak in shared infrastructure. The
  shared `SpecialRenderEffectRegistry` framework already exists for exactly
  this case (CNZ slot overlay, AIZ battleship, water surface), so the overlay
  was extracted into a new `HtzEarthquakeBgOverlayEffect`
  (`com.openggf.game.sonic2.render`) that registers itself at the
  `AFTER_FOREGROUND` stage from `Sonic2ZoneFeatureProvider`. The active flag
  storage moved into `Sonic2HTZEvents.earthquakeActive` (now the canonical
  source for `HtzRuntimeState.earthquakeActive()`); a new
  `setEarthquakeActive(boolean)` setter encapsulates the screen-shake-active
  + tilemap-invalidation side effects that previously lived in
  `ParallaxManager.setHtzScreenShake`. `ParallaxManager.setHtzScreenShake` and
  the `htzScreenShakeActive` getter/setter on `GameStateManager` are gone.
  `LevelTilemapManager` now consults a generic
  `ZoneRuntimeState.requiresFullWidthBgTilemap()` default method (overridden
  by `HtzRuntimeStateView`) instead of reading the HTZ flag from
  `GameStateManager`. Htz reference counts dropped from 7→0 in
  `LevelManager.java` and from 5→0 in `GameStateManager.java`.
- **WaterSystem: moved per-game visual oscillation behind `WaterDataProvider`.**
  `WaterSystem.getVisualWaterLevelY()` previously hard-coded
  `gameId == GameId.S2 && zoneId == ZONE_ID_CPZ` and `gameId == GameId.S1 &&
  (zoneId == LZ || (zoneId == SBZ && actId == 2))` branches in shared
  infrastructure -- a direct violation of the feature-flag/provider rule.
  Added a new `int getVisualWaterLevelOffset(int zoneId, int actId)` default
  method to `WaterDataProvider` (default returns 0). `Sonic2WaterDataProvider`
  overrides for CPZ to apply oscillator-0 bobbing centered at -8 (~ring
  height); `Sonic1WaterDataProvider` overrides for LZ and SBZ3 with the ROM's
  `oscillation >> 1` LZWaterFeatures.asm formula; S3K provider keeps the
  default 0 (no oscillation). `WaterSystem` now resolves the provider via
  `GameServices.module().getWaterDataProvider()` and adds the offset to the
  base level. `getGameId()` count in `WaterSystem` dropped from 1 to 0; the
  unused `GameId` and `OscillationManager` imports were removed.
- **PhysicsFeatureSet: replaced game-id branches in `LevelManager` with feature flags.**
  `LevelManager` had three branches that dispatched on game identity: two
  copies of `gameModule.getGameId() == GameId.S3K` to opt the S3K respawn-
  table latch in (line 917 in level load, line 4441 in act-transition
  rebind), and one `activeModule instanceof Sonic1GameModule` arm in
  `objectsExecuteAfterPlayerPhysics()` that bridged S1 onto the post-physics
  object-execution path (its collisionModel is UNIFIED, so the prior
  `DUAL_PATH || instanceof S1` test added S1 explicitly). Per CLAUDE.md's
  "never use game-name if/else chains -- always use feature flags" rule,
  promoted both to `PhysicsFeatureSet` fields: `permanentRespawnTableLatch`
  (true for S3K only, cite sonic3k.asm:20953 `bset #7,status(a1)` in
  `Touch_EnemyNormal`) and `objectsExecuteAfterPlayerPhysics` (true for S1/S2/S3K
  per the 2026-04-18-solid-ordering-rom-accuracy plan). `LevelManager` now
  reads both flags through `gameModule.getPhysicsProvider().getFeatureSet()`,
  the `Sonic1GameModule` import is gone, and the `getGameId()` count in
  `LevelManager` dropped from 3 to 1 (the one remaining use is in unrelated
  diagnostic logging). `CrossGameFeatureProvider` and
  `TestHybridPhysicsFeatureSet` propagate both new fields from the base
  game; `TestPhysicsProfile` adds regression cases asserting the per-game
  values.
- **GameServices: unified `hasRuntime()` predicate with `gameplayModeOrNull()`,
  migrated `bonusStage()` accessor off `RuntimeManager.getCurrent()`.**
  `GameServices.hasRuntime()` previously checked
  `RuntimeManager.getActiveRuntime() != null` while `gameplayModeOrNull()` and
  every `*OrNull()` accessor checked
  `SessionManager.getCurrentGameplayMode() != null && mode.getCamera() != null`.
  After `RuntimeManager.parkCurrent()`, those two predicates disagreed:
  `hasRuntime()` returned `false` while `gameplayModeOrNull()` could still
  return non-null (the gameplay mode lives on past the runtime in
  `SessionManager`). Code that guarded on `hasRuntime()` and then read via the
  `*OrNull()` accessors could read parked-context state. Unified
  `hasRuntime()` to delegate to `gameplayModeOrNull() != null` so both
  predicates always agree.
  Separately, `GameServices.bonusStage()` previously called
  `requireRuntime()` -> `RuntimeManager.getCurrent()`, which has a side
  effect: when the current runtime's `GameplayModeContext` no longer matches
  `SessionManager.getCurrentGameplayMode()` it calls `current.destroy()` and
  clears `current`. Calling `bonusStage()` during a mode transition could
  silently destroy a live runtime. Migrated the active bonus-stage provider
  field off `GameRuntime` onto `GameplayModeContext` (gameplay-scoped
  lifetime, transferred across `parkCurrent`/`resumeParked`); `GameRuntime.
  getActiveBonusStageProvider()` and `setActiveBonusStageProvider()` now
  delegate to the mode context for source compatibility. `GameServices.
  bonusStage()` and `bonusStageOrNull()` now resolve through
  `requireGameplayMode(...)` / `gameplayModeOrNull()` and never call
  `RuntimeManager.getCurrent()`. `requireRuntime(...)` is now unused inside
  `GameServices`, marked `@Deprecated`. New tests cover the predicate-
  equivalence invariant across no-runtime/active/parked/destroy transitions
  and verify that repeated `bonusStage()` calls do not destroy the active
  runtime. Architectural fix Task B1.
- **Engine: reset GL state before post-fade `CREDITS_DEMO` sprite pass.**
  In `Engine.display()`, the credits-demo branch that re-renders sprites
  on top of the fade overlay (`shouldRenderDemoSpritesOverFade()`) now
  invokes `GraphicsManager.resetForFixedFunction()` before the sprite
  pass. The fade shader binds a program and toggles blend/depth state,
  and although `FadeManager` restores blend on its own, the subsequent
  sprite pass should not inherit the fade pass's leftover shader/texture
  bindings. Architectural fix Task A3.
- **S1 credits-demo bootstrap: removed trace-derived starting-pose override.**
  `Sonic1CreditsDemoBootstrap.applyStartingPose` previously forced a
  per-demo `setAnimationId` (WALK for demo 0, WAIT for demos 1-7) and
  `setDirection(RIGHT)` whose values were derived from frame-zero trace
  recordings rather than from the ROM. This was a spec violation of the
  CLAUDE.md "Trace Replay Tests" comparison-only invariant — the bootstrap
  must be ROM-derived only. `applyStartingPose` is deleted entirely; the
  engine's natural post-spawn init and first `Sonic_Animate` pass now drive
  the frame-zero pose. The credits-demo tests retain the same 3-pass /
  5-fail profile (failures are pre-existing engine bugs at frame 221+, now
  documented under "Sonic 1 credits demo trace replay divergences" in
  `docs/KNOWN_DISCREPANCIES.md`). The class-level Javadoc citation in
  `Sonic1CreditsDemoBootstrap` was also incorrect (pointed at
  `Level_ChkDemo` at sonic.asm:2987-2990 — a timer/restart check, not the
  demo bootstrap); corrected to `EndingDemoLoad` (sonic.asm:3827) and
  `EndDemo_LampVar` (sonic.asm:3879). The same incorrect line numbers in
  `Sonic1CreditsDemoData` (4171/4176) are corrected in step.
- **Trace replay: hardened invariant guard and removed S1 credits-demo
  hydration.** `AbstractCreditsDemoTraceReplayTest.applyFrameZeroPlayerSnapshot`
  and `setupLzDemoState` previously read `TraceEvent.StateSnapshot.fields()`
  and `TraceFrame.rings()/cameraX()/cameraY()` on frame 0 and wrote ~10
  player/camera fields back into the engine — exactly the per-frame
  comparison-only invariant violation that CLAUDE.md "Trace Replay Tests"
  forbids. `TestTraceReplayInvariantGuard` did not catch this because its
  forbidden-string list missed the new patterns. The two debug probes
  (`DebugS1Credits03LzDoorProbe`, `DebugS1Credits05SbzJunctionProbe`)
  inherited the same anti-pattern. Replaced the hydration with a
  deterministic constants-only `Sonic1CreditsDemoBootstrap` helper that
  applies the LZ Act 3 lamppost state from `Sonic1CreditsDemoData`
  constants. (The starting-pose override added in this commit was itself a
  spec violation and has been removed in the follow-up bullet above.) The LZ ring count is set to 0 (matching
  ROM `Lamp_LoadInfo` in `_incObj/79 Lamppost.asm`, which loads
  `v_lamp_rings` then immediately clears `v_rings` to 0) instead of the
  `LZ_LAMP_RINGS=13` table value that ROM loads but never keeps. The guard
  now rejects: any `applyFrameZeroPlayerSnapshot(`/`applyCustomRadii(` call,
  any `fields.get("...")` snapshot read, any `frameZero != null` /
  `recordedRings = frameZero` / `recordedCamera...` local-variable binding
  that downstream-feeds engine setters, and a generic regex
  `\.set[A-Z]\w*\([^)]*\b(state|frame|snapshot|sn)\.\w+` that catches setter
  calls reading directly from a trace-side identifier. All 8 S1 credits demo
  trace replay tests retain their pre-existing pass/fail profile after the
  cleanup (3 pass, 5 fail on long-standing engine divergences unrelated to
  this task).
- **S3K: HCZ object art now ROM-only — eliminated `docs/skdisasm/` runtime
  reads.** `Sonic3kObjectArtProvider` previously parsed three HCZ object
  mapping tables (`Map_HCZMiniboss`, `Map_HCZEndBoss`, `Map_HCZWaterWall`) by
  reading `.asm` source files from `docs/skdisasm/Levels/HCZ/Misc Object
  Data/` at runtime via `Files.readAllLines`, violating the project's "ROM
  only for runtime assets" hard rule and silently degrading to invisible
  sprites whenever the disassembly tree was absent (CI / fresh clones). All
  three call sites now use `S3kSpriteDataLoader.loadMappingFrames` with
  ROM-verified table-base addresses
  (`MAP_HCZ_MINIBOSS_ADDR=0x3629E0`,
  `MAP_HCZ_END_BOSS_ADDR=0x3634D4`,
  `MAP_HCZ_WATERWALL_ADDR=0x22EE10`); the existing
  `MAP_HCZ_MINIBOSS_ADDR=0x362A28` constant was incorrect (pointed at the
  first frame body rather than the offset table) and is now corrected. The
  old asm-include parser, the duplicate-frame workaround for shared
  `Frame_362BB0` labels (no longer needed because ROM-based reading of
  duplicate offsets yields duplicate frames naturally), and the three `Path`
  constants under `docs/` are removed.
- **Runtime ownership migration: GameServices decoupled from GameRuntime
  façade.** `GameServices` now resolves all gameplay-scoped manager accessors
  through `SessionManager.getCurrentGameplayMode()` directly rather than via
  `RuntimeManager.getCurrent()`/`GameRuntime.getX()`. Migrated ~58 mechanical
  call sites across 27 files (engine top-level, level/sprite/graphics, S2/S3K
  game-specific, plus tests) from `RuntimeManager.getCurrent().getX()` and
  `runtime.getX()` patterns to the appropriate `GameServices.X()` accessors.
  After the change, the `GameRuntime` façade still exists as a lifecycle
  handle but is no longer load-bearing for production gameplay code; the only
  remaining `GameRuntime` references are foundational (constructor parameters
  for `DefaultObjectServices`/`RuntimeSaveContext`, the
  `TraceReplayFixture.runtime()` interface contract, lifecycle methods on
  `RuntimeManager`, and tests that legitimately exercise runtime instance
  identity). Tests that asserted "post-`destroyCurrent` GameServices throws"
  were updated to also call `SessionManager.clear()` since the new lifecycle
  is "destroy runtime → managers reset, but gameplay-mode context still
  alive; clear session → gameplay-mode context gone, GameServices throws".
- **Runtime ownership migration: gameplay state split by lifetime.**
  Per `docs/superpowers/specs/2026-04-07-runtime-ownership-migration-design.md`,
  the design's load-bearing split is now in place: `WorldSession` owns the
  durable world data (active `GameModule`, the loaded `Level` including its
  `MutableLevel` layout, and zone/act metadata); `GameplayModeContext` owns
  the disposable gameplay-scoped managers (Camera, Timer, GameState, Fade,
  Rng, SolidExecution, Water, Parallax, TerrainCollision, Collision, Sprite,
  LevelManager) and the runtime-shared registries (`ZoneRuntimeRegistry`,
  `PaletteOwnershipRegistry`, `AnimatedTileChannelGraph`,
  `SpecialRenderEffectRegistry`, `AdvancedRenderModeController`,
  `ZoneLayoutMutationPipeline`). `GameRuntime` is now a thin coordinator
  façade whose getters delegate to the gameplay mode context — its 18
  manager fields are gone. `LevelManager` keeps a write-through cache for
  zone/act/level reads but `WorldSession` is the source of truth, so a
  freshly-constructed `LevelManager` after editor exit re-inherits the
  loaded level automatically. `GameplayModeContext.initializeFreshGameplayState()`
  resets the design's "non-preserved" counters (score, timer, checkpoint)
  on editor exit. New tests
  `editorRoundTrip_preservesWorldSessionAndResetsGameplayCounters` and
  `editorRoundTrip_preservesMutableLevelMutations` verify the editor enter/exit
  round trip preserves world data + a `MutableLevel` mutation while resetting
  session counters. `LevelManager.restoreInheritedLevel()` is added as a
  building block for the future "drop `RuntimeManager.parkCurrent`" cleanup.
  The empty `EngineContext` stub is removed (its role is already played by
  `EngineServices`). Eliminating the `GameRuntime` façade entirely (51 file
  refs) and replacing the parking flow with direct teardown remain mechanical
  follow-ups, not blocking.
- **Force-snap camera centres on `sprite_x - 160`, matching ROM.**
  `Camera.updatePosition(force=true)` previously placed the camera at
  `sprite.getCentreX() - 152` (the midpoint of the 144-160 horizontal
  scroll deadzone). The ROM's level-load routine (s1disasm
  `_inc/LevelSizeLoad & BgScrollSpeed.asm:111`, s2.asm:14787,
  sonic3k.asm:38241) snaps to `MainCharacter.x_pos - $A0` (160) — the
  right edge of the deadzone — before clamping to level bounds. The
  off-by-8 error showed up as a +8 px engine `camera_x` at frame 0 in
  six S1 credits demo trace replays (Mz2, Syz3, Slz3, Sbz1, Sbz2,
  Ghz1b) plus S3K MGZ, but was hidden whenever the snap was clamped to
  the left boundary (S1 GHZ1, S1 MZ1, S2 EHZ1, S3K AIZ all start
  near `x=0` so the clamp masked the bug). With the formula corrected,
  S1 Credits06 Sbz2 and S1 Credits07 Ghz1b pass cleanly; the remaining
  S1 credits demos and S3K MGZ no longer report `camera_x` as the first
  error and instead surface downstream parity issues for follow-up.
- **Trace replay now validates camera position pixel-for-pixel.** The
  BizHawk trace recorders (`tools/bizhawk/s{1,2,3k}_trace_recorder.lua`)
  already capture ROM `Camera_X_pos` / `Camera_Y_pos` each frame, but
  `TraceBinder` only displayed them as diagnostic context — divergent
  engine camera scrolling was silently ignored. `TraceBinder.compareFrame`
  now produces `camera_x` / `camera_y` field comparisons whenever both
  ROM trace and engine diagnostics recorded coordinates, with both sides
  masked `& 0xFFFF` to align ROM's u16 representation with the engine
  `Camera.getX()/getY()` short return value across the sign boundary.
  `ToleranceConfig` gains `cameraWarn` / `cameraError` (default 1/1, so
  any mismatch is an ERROR) and a `withCameraTolerances(warn, error)`
  opt-out for explicit per-test relaxation; the default is unchanged
  pixel-perfect. `EngineDiagnostics` now stores `cameraY` alongside
  `cameraX` and exposes a `formattedWithCamera(x, y, text)` factory so
  `AbstractTraceReplayTest`'s precollapsed-context wrapper retains
  numerics for comparison. The new comparator path enabled `S2 EHZ1`
  trace replay end-to-end and surfaced previously-hidden S3K camera
  divergences (AIZ/CNZ/MGZ/HCZ replay tests now show `camera_x` or
  `camera_y` deltas at specific frames, e.g. AIZ1 frame 289 reports
  `camera_y` expected `0x0396`, actual `0x0390`) for follow-up triage.
- **S2 EHZ trace replay — Tails frame 3644 slope-resist parity fix.**
  Engine `doSlopeResist` previously applied slope force to all games
  whenever `g_speed == 0` and `|slope_force| >= 0x0D`, mirroring S3K
  `Player_SlopeResist` (sonic3k.asm:23830-23856) which branches to
  `loc_11DDC` when stationary and applies the force conditionally.
  S1/S2 ROM (s1disasm/_incObj/01 Sonic.asm:1243-1244;
  s2.asm:37394-37395, 40249-40250) instead returns unconditionally on
  `tst.w inertia(a0) / beq.s` — a stationary S1/S2 player on a steep
  slope stays put. Gated the at-rest kick behind a new
  `PhysicsFeatureSet.slopeResistAppliesAtZeroInertia` flag (true for
  S3K, false for S1/S2). `TestS2Ehz1TraceReplay.replayMatchesTrace`
  goes from 26 errors at frame 3644 (Tails decelerated to `g_speed=0`
  on angle 0xD0, ROM kept her stationary while engine slid her back
  down the loop) to a full pass; S3K trace replays unaffected (S3K
  flag is `true`, behaviour unchanged).
- **Trace Test Mode — pause-time camera focus visualiser.** While
  paused during a live trace session, the user can now cycle the
  camera between up to five focus targets using the configured P1
  LEFT/RIGHT keys: `Default` (the camera position at pause entry),
  `Sidekick (Eng)` / `Sidekick (Trace)` (centred on the engine's
  first sidekick or the recorded ROM-trace sidekick position), and
  `Main (Eng)` / `Main (Trace)` (centred on the engine's main
  playable sprite or the trace's recorded position). Trace variants
  are skipped when their position equals the engine's; sidekick
  options are skipped when no engine sidekick is spawned; main
  options are skipped when the main player is despawned. The active
  focus is shown in the top-right HUD as `Camera: <Mode>` with a
  `<- -> Cycle Cameras` hint. On unpause, the camera snaps back to
  its pre-pause position; gameplay determinism is preserved across
  frame-step (camera is restored before the step runs and re-applied
  after). The controller mutates only `Camera.setX/setY` — it never
  calls `updatePosition` or any other manager update path, so no
  object placement, parallax, or trace-recording state is disturbed.
- **CNZ Trace F8123 — CNZ bumper misses sidekick Tails touch at
  pixel-edge overlap (diagnosis only).** After the F7923 Clamer cprop
  fix landed, the next CNZ first error is at F8123 (2683 errors). Tails
  is following Sonic in `Tails_CPU_routine=6` (`loc_13D4A`,
  sonic3k.asm:26656), airborne+rolling (`status=0x07`) at
  `(x_pos=0x0F05, y_pos=0x0472)` with `(x_vel=0x00D7, y_vel=0x0268)`.
  The CNZ stationary bumper at slot 14 (`object_code=0x00032EAA =
  loc_32EAA`, sonic3k.asm:68850-68886) sits at `(0x0F00, 0x0488)` with
  `width_pixels=$10, height_pixels=$10`, so its top edge is exactly at
  Tails' bottom edge (`y=0x0480`). ROM treats this exact-edge contact
  as a hit, runs `sub_32F56` (sonic3k.asm:68950-68992):
  `x_vel = sin(arctan(bumper-player)+frame&3) × -$700 / 256` and
  `y_vel = cos(...) × -$700 / 256`, plus `bset Status_InAir`,
  `bclr Status_RollJump`, `bclr Status_Push`, `clr.b jumping`, then
  spawns `Obj_EnemyScore` (`loc_2CD0C`, sonic3k.asm:61375). Three
  evidence lines from `aux_state.jsonl` confirm this is the path:
  (1) an `object_appeared` event at F8123 for slot 7 with
  `object_type=0x0002CCE0` (`Obj_EnemyScore`) at the bumper's
  `(0x0F00, 0x0488)`; (2) the next `Obj_EnemyScore` spawn at F8150 (27
  frames later, the orbit-period gap) confirming the bumper as the
  spawn source; (3) ROM `tails_x_speed` jumps `0x00D7 → 0x0230`,
  `tails_y_speed` jumps `0x0268 → -0x06A5` -- discontinuous changes
  incompatible with `Tails_InputAcceleration_Freespace` drag plus
  `MoveSprite_TestGravity` air physics, but consistent with the bumper
  full sin/cos/-$700 reseed. Engine `tails_x_speed` ends at `0x00BF`
  (`= 0x00D7 - 0x18` air drag) and `tails_y_speed` at `0x02A0`
  (`= 0x0268 + 0x38` gravity), i.e. the sidekick's frame-end state is
  just the airborne-roll physics with no bumper bounce applied. The
  divergence is upstream of `applyBounce` (which is sin/cos/-$700
  correct) -- the engine's per-frame near-object scan window for the
  sidekick appears to drop the stationary CNZ bumper before the touch
  test runs (Sonic at `(0x0DE5, 0x0309)` is >600px from the bumper
  while Tails' AABB edge overlaps it). Documented in
  `docs/S3K_KNOWN_BUGS.md` with three engine-side fix candidates.
  CNZ first-error stable at F8123 this round. AIZ first-error at
  F8927 unchanged. S1 GHZ / S1 MZ1 / S2 EHZ trace replays remain GREEN.
- **AIZ Trace F8927 — diagnosis-only entry for the next first
  trace error after the F7660 swing-bounce fix landed.** Trace
  shows Sonic rolling+airborne (`status=0x06`,
  `status_secondary=0x11` Fire Shield) descending into the AIZ2
  boss-arena entrance with `x_speed` capped at `0x0179` from F8923
  through F8926; at F8927 the ROM zeroes `x_speed` and freezes
  `x_pos` at `0x1208`, while the engine retains `0x0179` and drifts
  ahead, producing 896 cascading errors over the next ~340 frames
  including a phantom land at F8942. ROM frames F8931 onward show
  the canonical "rolling-air sliding into a flush right-side wall"
  signature: `x_speed` cycles 0 → 0x18 → 0x30 → 0x48 → 0x60 → 0
  every 5 frames with a sub-x snap pushback, matching
  `SonicKnux_DoLevelCollision`'s `CheckRightWallDist` arm
  (sonic3k.asm:24061-24065 -- "stop Sonic since he hit a wall"
  `move.w #0,x_vel(a0)`). The engine never observes that wall
  hit. Three candidate root causes (missing terrain solid bit at
  the boss-arena right wall, quadrant-routing skip in our
  `DoLevelCollision` equivalent, or an x_radius-vs-fixed-`+10`
  probe-offset mismatch matching the player path's
  `addi.w #$A,d3` at sonic3k.asm:20195) are documented; the most
  testable is the probe-offset hypothesis since rolling drops
  `x_radius` from 9 to 7. No engine change in this round; only a
  documented diagnosis. AIZ first-error stays at F8927 (errors
  896). CNZ first-error at F7923 unchanged. S1 GHZ / S1 MZ1 / S2
  EHZ trace replays remain GREEN.
- **CNZ1 Trace F7923 — Clamer latched-cprop fired on wrong player
  (FIXED).** ROM `Touch_Special.loc_103FA` (sonic3k.asm:21186-21194)
  accumulates per-touch into the spring-child's
  `collision_property(a1)` byte with a player-identity-dependent
  increment: `+1` for Player_1 (Sonic), `+2` for Player_2 (sidekick
  Tails). `Check_PlayerCollision` (sonic3k.asm:179904-179924) then
  masks `& 3` and indexes `word_85890 = [P1, P1, P2, P2]` to pick the
  launch target before clearing the byte. The engine's
  `ClamerObjectInstance` was collapsing this to a single boolean
  `springCprop`, so when the post-cooldown latch fired the engine
  always launched the primary `playerEntity` passed into `update()`
  (Sonic) instead of resolving the byte to the actual toucher. At
  F7923 the engine launched Sonic into the air with the spring's
  triplicate `-0x0800` write while ROM had Sonic still on the ground
  and was re-firing the same spring on Tails. Replaced the boolean
  with the ROM cprop byte; `onTouchResponse` increments by `+1` for
  primary, `+2` for `playerEntity.isCpuControlled()` (Tails), and the
  two latch-fire branches in `advanceSpringRoutine` resolve the
  target via `cprop & 3` (`1 → primary`, `2 or 3 → first sidekick
  from services().sidekicks()`). Cprop is cleared on consumption to
  mirror `clr.b collision_property(a0)`. CNZ first-error advances
  F7923 -> F8123 (2767 -> 2683 errors); AIZ first-error stable at
  F8927; S1/S2 trace replays unaffected; `TestClamerObjectInstance`
  GREEN.
- **AIZ Mini-boss F7660 — `Swing_UpAndDown` peak bounce-back ROM
  parity restored.** ROM `Swing_UpAndDown` (sonic3k.asm:177851-177879)
  applies a bounce-back at the swing apex: when the velocity reaches
  `±maxSpeed`, the routine flips direction (`bset/bclr #0,$38(a0)`),
  negates `d0`, and falls into `loc_84812` which adds the now-opposite
  `d0` back to `d1` in the same frame, so the stored peak velocity is
  `±maxSpeed ∓ accel`, not the clamped extreme. The engine's
  `AizMinibossSwingMotion.update()` was clamping the peak to
  `±maxSpeed` (skipping the `loc_84812` step), so the swing apex held
  the extreme velocity for one extra frame each half-cycle and the
  swing drifted ~6 frames out of phase with ROM by trace F7660.
  With the drifted swing the engine's miniboss y was 3 units low
  vs ROM at F7660, which let the engine see the boss/Sonic AABB
  overlap one frame ahead of ROM. ROM boss `Touch_ChkHurt`
  (sonic3k.asm:20911-20915) negates `x_vel`, `y_vel`, and
  `ground_vel` on a boss hit, so the ahead-by-one-frame detection
  flipped Sonic's `g_speed`/`x_speed`/`y_speed` signs at F7660 in
  the engine while ROM still showed them positive (ROM bounced at
  F7661). Engine now applies the ROM bounce-back step
  (`vel += accel` at the up peak, `vel -= accel` at the down peak)
  so the swing apex matches ROM cycle-for-cycle. AIZ first-error
  advances 7660 → 8927 (errors 975 → 896). CNZ first-error at F7923
  unchanged. S1 GHZ / S1 MZ1 / S2 EHZ trace replays remain GREEN.
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
- **CNZ F=621 Clamer re-fire — Touch_Special cprop latch landed (round 4).**
  `ClamerObjectInstance` now models the ROM spring child's `(a0) =
  loc_890AA -> loc_890C8 -> loc_890D0 -> loc_890AA` cycle (sonic3k.asm:185953-185973)
  with a three-state machine (LIVE / COOLDOWN_DRAIN / COOLDOWN_DONE)
  plus a `springCprop` boolean mirroring `collision_property(a0)`
  (sonic3k.asm:21162-21194). Touch on a cooldown frame latches the
  cprop byte; the next non-cooldown spring update consumes it and
  fires (matches the ROM F=619/F=621 fire schedule recorded in the
  v6.15-s3k CNZ aux events). Spring rect uses ROM-correct cflags
  `$D7` (`$40 | $17`, 8x8) at all times -- the engine-only
  `SPRING_RELATCH_COLLISION_FLAGS = $40 | $12` widening and the
  `springReenableFrame` mechanism are removed. Adds
  `usesS3kTouchSpecialPropertyResponse()` override so the engine
  decoder routes `cflags=$D7` through SPECIAL via the
  Touch_Special property index list (sonic3k.asm:21165-21194),
  consistent with `CnzBalloonInstance`. `TestS3kCnzTraceReplay`
  first error advances F7919 -> F7923; F=619-625 zero errors.
  `TestS3kAizTraceReplay` first error stable at F7552. S1 GHZ /
  S1 MZ1 / S2 EHZ trace replays GREEN. `TestClamerObjectInstance`
  6/6 GREEN.

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
