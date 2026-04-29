------------------------------------------------------------------------------
-- s3k_trace_recorder.lua
-- BizHawk Lua script for recording Sonic 3 & Knuckles frame-by-frame physics
-- state during BK2 movie playback.
--
-- Usage:
--   1. Open BizHawk with a locked-on Sonic 3 & Knuckles ROM
--   2. Load a BK2 movie file
--   3. Tools > Lua Console > load this script
--   4. Play the movie -- recording starts automatically when gameplay begins
--   5. Stop the movie or close the script to finalise output files
--
-- v2.0 changes: added subpixel, routine, camera, rings, status_byte columns
-- to physics.csv for faster divergence debugging. Object proximity tracking
-- logs nearby objects every frame instead of only new appearances every 4.
-- v2.1 changes: scan the full OST, emit slot_dump events on object appearance
-- for slot allocation comparison, add frame counters to physics.csv, and log
-- richer aux events for object/routine diagnostics.
-- v2.2 changes: add stand_on_obj to physics.csv and richer routine-change
-- snapshots for hurt/death debugging.
-- v3.0-s3k changes: rename gameplay counter column to
-- gameplay_frame_counter and add vblank_counter plus lag_counter.
-- v3.2-s3k changes: record initial RNG_seed in metadata so Random_Number
-- consumers replay with the same inherited movie state.
-- v5.0-s3k changes: append first-sidekick state to each physics row so
-- replay can keep unrecorded Tails drift from perturbing Sonic's world.
-- v5.1-s3k changes: emit the pre-trace Tails CPU globals so replay can
-- resume CNZ1's scripted carry from the ROM routine that frame 0 reached.
-- v5.2-s3k changes: emit mappable pre-trace object_state_snapshot events
-- using S3K object ids for replay hydration of live state such as CNZ
-- balloon random bob phases.
-- v5.3-s3k changes: emit pre-trace CPU/object snapshots on the first
-- recorded physics frame so snapshot state and trace frame 0 share the same
-- end-of-frame ROM instant.
-- v5.4-s3k changes: write pre_trace_osc_frames from Level_frame_counter so
-- seeded replays restore the ROM's global oscillation phase.
-- v6.0-s3k changes: emit per-frame cpu_state events with the full Tails CPU
-- global block plus Ctrl_2_logical so engine SidekickCpuController state can
-- be hydrated each frame in trace replay (closes the visibility gap that
-- blocked CNZ1 trace F1740 root-cause analysis -- the trace's sidekick OST
-- routine byte is not Tails_CPU_routine and Ctrl_2_logical was never recorded).
-- Bumps trace_schema 5 -> 6.
-- v6.1-s3k changes: emit per-frame oscillation_state events with the full
-- Oscillating_table contents ($42 bytes at $FFFFFE6E) and Level_frame_counter
-- so trace replay can ROM-verify global oscillator phase. Closes the
-- visibility gap that blocks CNZ1 F850 root-cause analysis where the engine's
-- OscillationManager.getByte(LIFT_OSC_OFFSET=$14) reads osc[5] one tick
-- behind ROM's Oscillating_table+$16 high byte.
-- v6.2-s3k changes: emit per-frame object_state events (one per nearby OST
-- slot within OBJECT_PROXIMITY of either player) and interact_state events
-- (per player) with object_control byte (offset $2A) plus interact slot
-- resolved from offset $42. Used to root-cause AIZ F2667 spike-vs-Tails
-- collision divergence: ROM's SolidObject_cont path early-exits when
-- object_control bit 7 is set on the player (sonic3k.asm:41439).
-- v6.3-s3k changes: (1) Fix recorder bug -- interact_state's
-- "object_control" field was reading offset $2A (status byte) instead of
-- offset $2E (real object_control). The new emission also splits status
-- and status_secondary into their own JSON fields so consumers don't
-- have to disambiguate. (2) Add CNZ wire cage diagnostic events:
-- cage_state (per cage object, per frame, with per-player phase/state
-- bytes plus cage status) and cage_execution (per frame, with the list
-- of cage-routine branches the CPU entered along with M68K register
-- state at entry). Used to root-cause CNZ1 trace F2222 release-cooldown
-- divergence where the engine fires Tails's release one frame earlier
-- than ROM despite both seeing Ctrl_2_pressed_logical=$48.
-- v6.5-s3k changes: add comparison-only sidekick diagnostics for the
-- current AIZ/CNZ trace frontiers: tails_cpu_normal_step (Tails CPU
-- normal-follow branch/input/path state; sonic3k.asm:26702-26705,
-- 26717-26741, 27798-27805, 28103-28122, 27957-28017) and
-- sidekick_interact_object (Tails interact pointer/object snapshot;
-- sonic3k.asm:28407-28451, 43758-43810, 46481-46549, 46602-46631,
-- 46709-46743, 46749-46789, 46929-46950). Diagnostic-only; no CSV
-- schema change.
-- v6.6-s3k changes: add AIZ boundary/tree diagnostics around the F4679
-- sidekick divergence. The event captures Camera_min_X/Y and Camera_max_X/Y,
-- Tails pre/post Tails_Check_Screen_Boundaries, AIZTree_SetPlayerPos
-- pre/post, and end-of-frame post-move state. ROM frame order and relevant
-- routines: sonic3k.asm:7884-7898, 38298-38316, 38961-38974,
-- 43776-43810, 28407-28451. Diagnostic-only; no CSV schema change.
------------------------------------------------------------------------------

-----------------
--- Constants ---
-----------------

local OUTPUT_DIR = "trace_output/"
local HEADLESS = true
local MOVIE_FRAME_SAFETY_MARGIN = 30
local TRACE_PROFILE = os.getenv("OGGF_S3K_TRACE_PROFILE") or "gameplay_unlock"
local BK2_FRAME_COUNT = tonumber(os.getenv("OGGF_BK2_FRAME_COUNT") or "")
local BIZHAWK_VERSION = "2.11"
local GENESIS_CORE = "Genplus-gx"
local S3K_ROM_CHECKSUM = "C5B1C655C19F462ADE0AC4E17A844D10"

-- Sonic 3 & Knuckles 68K RAM addresses (BizHawk mainmemory strips $FF0000)
local ADDR_GAME_MODE        = 0xF600
local ADDR_CTRL1            = 0xF604
local ADDR_CTRL1_DUP        = 0xF602
local ADDR_CTRL1_LOCKED     = 0xF7CA
local ADDR_RING_COUNT       = 0xFE20
local ADDR_CAMERA_X         = 0xEE78
local ADDR_CAMERA_Y         = 0xEE7C
local ADDR_ZONE             = 0xFE10
local ADDR_ACT              = 0xFE11
local ADDR_PLAYER_MODE      = 0xFF08
local ADDR_APPARENT_ACT     = 0xEE4F
local ADDR_EVENTS_FG_5      = 0xEEC6
local ADDR_LEVEL_STARTED_FLAG = 0xF711

-- Player_1 ($FFFFB000) uses 32-bit positions: high word = pixel, low word = subpixel.
local PLAYER_BASE           = 0xB000
local OFF_X_POS             = 0x10
local OFF_X_SUB             = 0x12
local OFF_Y_POS             = 0x14
local OFF_Y_SUB             = 0x16
local OFF_X_VEL             = 0x18
local OFF_Y_VEL             = 0x1A
local OFF_INERTIA           = 0x1C
local OFF_RADIUS_Y          = 0x1E
local OFF_RADIUS_X          = 0x1F
local OFF_ANIM_ID           = 0x20
local OFF_STATUS            = 0x2A
local OFF_STATUS_SECONDARY  = 0x2B
local OFF_OBJECT_CONTROL    = 0x2E
local OFF_ROUTINE           = 0x05
local OFF_ANGLE             = 0x26
local OFF_STICK_CONVEX      = 0x3C
local OFF_STAND_ON_OBJ      = 0x42   -- interact: RAM address of stood-on object
local OFF_CTRL_LOCK         = 0x32

local ROUTINE_HURT          = 0x04
local ROUTINE_DEATH         = 0x06

local STATUS_FACING_LEFT    = 0x01
local STATUS_IN_AIR         = 0x02
local STATUS_ROLLING        = 0x04
local STATUS_ON_OBJECT      = 0x08
local STATUS_ROLL_JUMP      = 0x10
local STATUS_PUSHING        = 0x20
local STATUS_UNDERWATER     = 0x40

-- Object table: S3K OST starts at Player_1 and uses $4A-byte entries.
local OBJ_TABLE_START       = 0xB000
local OBJ_SLOT_SIZE         = 0x4A
local OBJ_TOTAL_SLOTS       = 110
local OBJ_DYNAMIC_START     = 3
local OBJ_DYNAMIC_COUNT     = 90
local SIDEKICK_BASE         = OBJ_TABLE_START + OBJ_SLOT_SIZE
local OBJ_CNZ_BALLOON       = 0x00031754
local OBJ_ID_CNZ_BALLOON    = 0x41

local ADDR_FRAMECOUNT       = 0xFE04
-- Oscillating_table address ($FFFFFE6E in S3K RAM, $42 bytes total).
-- First word ($FE6E/$FE6F) is the control bitfield (Osc_Data $0000 first dc.w),
-- followed by 16 (value, delta) word pairs. Computed from
-- docs/skdisasm/sonic3k.constants.asm sequential `ds.b` walk: RAM_start
-- $FFFF0000 -> CrossResetRAM at $FFFFFE00 -> Oscillating_table at offset $6E.
local ADDR_OSC_TABLE        = 0xFE6E
local OSC_TABLE_SIZE        = 0x42
local ADDR_VBLA_WORD        = 0xFE0E
local ADDR_LAG_FRAME_COUNT  = 0xF628
local ADDR_RNG_SEED         = 0xF636
-- Tails CPU global block. Layout from sonic3k.constants.asm:618-626:
--   $F700 Tails_CPU_interact     (word) - RAM addr of object Tails stood on
--   $F702 Tails_CPU_idle_timer   (word) - counts down while Ctrl_2 idle
--   $F704 Tails_CPU_flight_timer (word) - counts up while respawning
--   $F706 (unused)
--   $F708 Tails_CPU_routine      (word) - current Tails AI routine index
--   $F70A Tails_CPU_target_X     (word)
--   $F70C Tails_CPU_target_Y     (word)
--   $F70E Tails_CPU_auto_fly_timer  (byte)
--   $F70F Tails_CPU_auto_jump_flag  (byte)
local ADDR_TAILS_CPU_INTERACT       = 0xF700
local ADDR_TAILS_CPU_IDLE_TIMER     = 0xF702
local ADDR_TAILS_CPU_FLIGHT_TIMER   = 0xF704
local ADDR_TAILS_CPU_ROUTINE        = 0xF708
local ADDR_TAILS_CPU_TARGET_X       = 0xF70A
local ADDR_TAILS_CPU_TARGET_Y       = 0xF70C
local ADDR_TAILS_CPU_AUTO_FLY_TIMER = 0xF70E
local ADDR_TAILS_CPU_AUTO_JUMP_FLAG = 0xF70F
-- Legacy aliases (kept for compatibility with the historical pre-trace snapshot
-- field names; the new per-frame snapshot uses the disassembly-correct names).
local ADDR_TAILS_CONTROL_COUNTER = ADDR_TAILS_CPU_IDLE_TIMER
local ADDR_TAILS_RESPAWN_COUNTER = ADDR_TAILS_CPU_FLIGHT_TIMER
local ADDR_TAILS_INTERACT_ID     = ADDR_TAILS_CPU_AUTO_FLY_TIMER
local ADDR_TAILS_CPU_JUMPING     = ADDR_TAILS_CPU_AUTO_JUMP_FLAG
-- Ctrl_2_logical block. Layout from sonic3k.constants.asm:589-591:
--   $F66A Ctrl_2_held_logical    (byte)
--   $F66B Ctrl_2_pressed_logical (byte)
local ADDR_CTRL2_HELD_LOGICAL    = 0xF66A
local ADDR_CTRL2_PRESSED_LOGICAL = 0xF66B
-- Tails normal-follow delay tables. Addresses derived from
-- docs/skdisasm/sonic3k.constants.asm:331-333 and 368.
local ADDR_STAT_TABLE        = 0xE400
local ADDR_POS_TABLE         = 0xE500
local ADDR_POS_TABLE_INDEX   = 0xEE26

local INPUT_UP    = 0x01
local INPUT_DOWN  = 0x02
local INPUT_LEFT  = 0x04
local INPUT_RIGHT = 0x08
local INPUT_JUMP  = 0x10

local GAMEMODE_SEGA       = 0x00  -- verified from GameModes entry 0 label <Sega_Screen>       (sonic3k.asm:431)
local GAMEMODE_TITLE      = 0x04  -- verified from GameModes entry 1 label <Title_Screen>      (sonic3k.asm:432)
local GAMEMODE_LEVEL_SEL  = 0x28  -- verified from GameModes entry 10 label <LevelSelect_S2Options> (sonic3k.asm:441; reached from title via sonic3k.asm:6617)
local GAMEMODE_LEVEL      = 0x0C  -- already defined in recorder; re-stated here for doc cross-ref (sonic3k.asm:434)
-- Transitional level-load modes: the engine sets bit 6 ($40) or bit 7 ($80) on top of
-- $0C while a new level is being assembled (title -> level handoff, act change,
-- between-zone reload). These map to $4C and $8C respectively. The mask $0F
-- isolates the underlying Game_Mode entry so we can detect the level family
-- regardless of which transitional bit is currently set.
local GAMEMODE_MASK       = 0x0F

local ZONE_NAMES = {
    [0x00] = "aiz",
    [0x01] = "hcz",
    [0x02] = "mgz",
    [0x03] = "cnz",
    [0x04] = "fbz",
    [0x05] = "icz",
    [0x06] = "lbz",
    [0x07] = "mhz",
    [0x08] = "soz",
    [0x09] = "lrz",
    [0x0A] = "ssz",
    [0x0B] = "dez",
    [0x0C] = "ddz",
    [0x0D] = "hpz",
}

local SNAPSHOT_INTERVAL = 60
local OBJECT_PROXIMITY = 160

-- CNZ Wire Cage (Obj_CNZWireCage / sub_338C4) per-frame execution hook
-- ROM addresses. Verified from sonic3k.asm label positions:
--   sub_338C4   = 0x338C4 (per-player cage handler entry)
--   loc_339A0   = 0x339A0 (mounted-mode entry: tst.b 1(a2); bne loc_33ADE)
--   loc_33ADE   = 0x33ADE (released-cooldown entry: andi.w #$70,d5; beq loc_33B1E)
--   loc_33B1E   = 0x33B1E (in-vertical-range continue branch)
--   loc_33B62   = 0x33B62 (release-and-unmount cleanup)
-- The cage main routine entry per frame is loc_3385E = 0x3385E (set by
-- move.l #loc_3385E,(a0) on init at 0x33854 / sonic3k.asm:69827).
-- Used to root-cause CNZ1 trace F2222 cage release-cooldown divergence
-- where engine fires release one frame ahead of ROM.
local CAGE_HOOK_LOC_3385E = 0x3385E
local CAGE_HOOK_SUB_338C4 = 0x338C4
local CAGE_HOOK_LOC_339A0 = 0x339A0
local CAGE_HOOK_LOC_33ADE = 0x33ADE
local CAGE_HOOK_LOC_33B1E = 0x33B1E
local CAGE_HOOK_LOC_33B62 = 0x33B62
-- Tails CPU normal-follow and path diagnostics.
local V65 = {
    TAILS_CPU_HOOK_LOC_13DD0 = 0x13DD0,
    TAILS_CPU_HOOK_LOC_13EB8 = 0x13EB8,
    TAILS_PATH_HOOK_LOC_14A0A = 0x14A0A,
    TAILS_PATH_HOOK_LOC_14B7A = 0x14B7A,
    normal_step = nil,
    tails_cpu_hooks_registered = false,
}

V66 = {
    -- Hook addresses resolved from labels in docs/skdisasm/sonic3k.asm:
    -- Tails_Check_Screen_Boundaries / locret_14F4A / loc_14F56 / loc_14F5C
    -- at sonic3k.asm:28407-28451.
    TAILS_BOUNDARY_ENTRY = 0x14F08,
    TAILS_BOUNDARY_RETURN = 0x14F4A,
    TAILS_BOUNDARY_KILL = 0x14F56,
    TAILS_BOUNDARY_CLAMP = 0x14F5C,
    -- AIZTree_SetPlayerPos and the instruction immediately after y_vel write
    -- from sonic3k.asm:43776-43810.
    AIZ_TREE_SET_PLAYER_POS_ENTRY = 0x1F912,
    AIZ_TREE_SET_PLAYER_POS_POST_YVEL = 0x1F982,
    CAMERA_MIN_X = 0xEE14,
    CAMERA_MAX_X = 0xEE16,
    CAMERA_MIN_Y = 0xEE18,
    CAMERA_MAX_Y = 0xEE1A,
    boundary_state = nil,
    hooks_registered = false,
}

-----------------
--- State     ---
-----------------

local started = false
local finished = false
local trace_frame = 0
local bk2_frame_offset = 0
local start_x = 0
local start_y = 0
local start_zone_id = 0
local start_zone_name = "unknown"
local start_act = 0
local start_rng_seed = 0
local start_gameplay_frame_counter = 0

local prev_status = 0
local prev_routine = 0
local prev_ctrl_lock = 0
local prev_player_mode = nil

local known_objects = {}
local emitted_checkpoints = {}
local last_zone_act_state_key = nil
local prev_zone_id_for_transition = nil
local prev_act_for_transition = nil
local pre_trace_snapshots_written = false

-- Per-frame accumulator for cage execution hits. Reset at start of each
-- on_frame_end and flushed as cage_execution events. We can't write directly
-- to aux_file from within the memoryexecute callback because we'd interleave
-- with the main scan loop, and we want one summary event per frame.
local cage_hits = {}

-- Track cage state byte (1(a2)) per player across frames. Emit a
-- cage_state_change event when a transition is detected. We poll this
-- inside on_frame_end since memoryexecute can't read $30(a0) without
-- running CPU. The state bytes are at OST slot offset $31 (P1) and $35 (P2).
local prev_cage_state_p1 = -1
local prev_cage_state_p2 = -1
local prev_cage_status = -1

-- Per-frame accumulator for Tails velocity-write hits (v6.4-s3k schema).
-- Hooked via event.onmemorywrite at Tails's x_vel/y_vel addresses. Each
-- hit captures the M68K PC at the write site so we can identify exactly
-- which ROM routine wrote the value. Flushed once per frame as a single
-- velocity_write event listing all writers in temporal order.
local tails_xvel_writes = {}
local tails_yvel_writes = {}

local physics_file = nil
local aux_file = nil

-----------------
--- Helpers   ---
-----------------

local function read_speed(base, offset)
    return mainmemory.read_s16_be(base + offset)
end

local function rom_joypad_to_mask(raw)
    local mask = raw & 0x0F
    if (raw & 0x70) ~= 0 then
        mask = mask + INPUT_JUMP
    end
    return mask
end

local function hex(val, width)
    width = width or 4
    if val < 0 then
        val = val + 0x10000
    end
    return string.format("%0" .. width .. "X", val)
end

local function angle_to_ground_mode(angle)
    if angle <= 0x1F or angle >= 0xE0 then return 0 end
    if angle >= 0x20 and angle <= 0x5F then return 1 end
    if angle >= 0x60 and angle <= 0x9F then return 2 end
    return 3
end

local function write_aux(json_str)
    if aux_file then
        aux_file:write(json_str .. "\n")
        aux_file:flush()
    end
end

local function json_quote(value)
    return '"' .. tostring(value)
        :gsub("\\", "\\\\")
        :gsub('"', '\\"') .. '"'
end

local function json_int_or_null(value)
    if value == nil then
        return "null"
    end
    return tostring(value)
end

local function read_object_code(slot)
    local addr = OBJ_TABLE_START + (slot * OBJ_SLOT_SIZE)
    return mainmemory.read_u32_be(addr)
end

local function interact_addr_to_slot(addr)
    if addr == 0 then
        return 0
    end
    local max_addr = OBJ_TABLE_START + (OBJ_TOTAL_SLOTS * OBJ_SLOT_SIZE)
    if addr < OBJ_TABLE_START or addr >= max_addr then
        return 0
    end
    local delta = addr - OBJ_TABLE_START
    if (delta % OBJ_SLOT_SIZE) ~= 0 then
        return 0
    end
    return math.floor(delta / OBJ_SLOT_SIZE)
end

local function read_stand_on_slot_for(base)
    local interact_addr = mainmemory.read_u16_be(base + OFF_STAND_ON_OBJ)
    return interact_addr_to_slot(interact_addr)
end

local function read_stand_on_slot()
    return read_stand_on_slot_for(PLAYER_BASE)
end

local function is_aiz_end_to_end_profile()
    return TRACE_PROFILE == "aiz_end_to_end"
end

local function is_level_gated_reset_aware_profile()
    return TRACE_PROFILE == "level_gated_reset_aware"
end

local function should_discard_and_reset(game_mode)
    if not is_level_gated_reset_aware_profile() then
        return false
    end
    if not started then
        return false
    end
    -- The only transitions that legitimately pull the player out of
    -- Game_Mode 0x0C mid-recording without a normal level-completion
    -- path are (a) pause + A back to title, or (b) soft power-on
    -- loop. Both surface as Game_Mode in {SEGA, TITLE, LEVEL_SEL}.
    return game_mode == GAMEMODE_SEGA
        or game_mode == GAMEMODE_TITLE
        or game_mode == GAMEMODE_LEVEL_SEL
end

local function is_level_family_mode(game_mode)
    -- The Game_Mode entry for Level is $0C. The engine ORs $40 or $80 into the
    -- byte during level-load handoff (title -> level, between-zone reload,
    -- act change), yielding $4C or $8C. Masking away those transitional bits
    -- gives the underlying Game_Mode entry, which must be $0C for any of
    -- {$0C, $4C, $8C} to match. Using this instead of player_x/y avoids the
    -- title-screen latch problem where player coords retain non-zero values
    -- from the title demo.
    return (game_mode & GAMEMODE_MASK) == GAMEMODE_LEVEL
end

local function should_start_recording(game_mode)
    if is_aiz_end_to_end_profile() then
        -- Start the moment Game_Mode first transitions OUT of SEGA/TITLE
        -- into the level-load family (0x0C / 0x4C / 0x8C). This captures
        -- the AIZ1 vine-drop intro from its first frame while discarding
        -- any preceding title-screen frames where player coords latch
        -- non-zero demo values.
        return movie.isloaded() and is_level_family_mode(game_mode)
    end
    local ctrl_lock_timer = mainmemory.read_u16_be(PLAYER_BASE + OFF_CTRL_LOCK)
    local ctrl_locked = mainmemory.read_u8(ADDR_CTRL1_LOCKED)
    return game_mode == GAMEMODE_LEVEL and ctrl_lock_timer == 0 and ctrl_locked == 0
end

local function emit_zone_act_state(frame, actual_zone_id, actual_act, apparent_act, game_mode)
    local key = table.concat({
        tostring(actual_zone_id),
        tostring(actual_act),
        tostring(apparent_act),
        tostring(game_mode)
    }, "|")
    if key == last_zone_act_state_key then
        return
    end
    last_zone_act_state_key = key
    write_aux(string.format(
        '{"frame":%d,"event":"zone_act_state","actual_zone_id":%s,"actual_act":%s,"apparent_act":%s,"game_mode":%s}',
        frame,
        json_int_or_null(actual_zone_id),
        json_int_or_null(actual_act),
        json_int_or_null(apparent_act),
        json_int_or_null(game_mode)))
end

local function emit_checkpoint_once(frame, name, actual_zone_id, actual_act, apparent_act, game_mode, notes)
    if emitted_checkpoints[name] then
        return false
    end
    emitted_checkpoints[name] = true
    local notes_field = notes ~= nil and (',"notes":' .. json_quote(notes)) or ""
    write_aux(string.format(
        '{"frame":%d,"event":"checkpoint","name":"%s","actual_zone_id":%s,"actual_act":%s,"apparent_act":%s,"game_mode":%s%s}',
        frame,
        name,
        json_int_or_null(actual_zone_id),
        json_int_or_null(actual_act),
        json_int_or_null(apparent_act),
        json_int_or_null(game_mode),
        notes_field))
    return true
end

local function emit_s3k_semantic_events(frame)
    local actual_zone_id = mainmemory.read_u8(ADDR_ZONE)
    local actual_act = mainmemory.read_u8(ADDR_ACT)
    local apparent_act = mainmemory.read_u8(ADDR_APPARENT_ACT)
    local game_mode = mainmemory.read_u8(ADDR_GAME_MODE)
    local move_lock = mainmemory.read_u16_be(PLAYER_BASE + OFF_CTRL_LOCK)
    local ctrl_locked = mainmemory.read_u8(ADDR_CTRL1_LOCKED)
    local events_fg_5 = mainmemory.read_u16_be(ADDR_EVENTS_FG_5)
    local level_started = mainmemory.read_u8(ADDR_LEVEL_STARTED_FLAG)

    emit_zone_act_state(frame, actual_zone_id, actual_act, apparent_act, game_mode)

    if is_level_gated_reset_aware_profile() then
        -- CNZ end-to-end aux checkpoints. Minimum-viable set:
        -- gameplay_start (first armed frame in CNZ gameplay),
        -- act_transition_to_cnz2 (edge from CNZ1 -> CNZ2), and
        -- gameplay_end (emitted from the finalisation path; see main loop).
        -- Deferred until we add MaxX / Knuckles routine RAM constants:
        -- cnz1_miniboss_arena_lock, cnz2_knuckles_cutscene_start,
        -- cnz2_endboss_arena_lock.
        if actual_zone_id == 3
            and game_mode == GAMEMODE_LEVEL
            and level_started ~= 0
            and move_lock == 0
            and ctrl_locked == 0 then
            emit_checkpoint_once(frame, "gameplay_start", actual_zone_id, actual_act, apparent_act, game_mode, nil)
        end
        if prev_zone_id_for_transition == 3
            and prev_act_for_transition == 0
            and actual_zone_id == 3
            and actual_act == 1 then
            emit_checkpoint_once(frame, "act_transition_to_cnz2", actual_zone_id, actual_act, apparent_act, game_mode, nil)
        end
    end

    prev_zone_id_for_transition = actual_zone_id
    prev_act_for_transition = actual_act

    if not is_aiz_end_to_end_profile() then
        return
    end

    if frame == 0 then
        emit_checkpoint_once(frame, "intro_begin", actual_zone_id, actual_act, apparent_act, game_mode, nil)
    end
    if level_started ~= 0 and game_mode == GAMEMODE_LEVEL and move_lock == 0 and ctrl_locked == 0 then
        emit_checkpoint_once(frame, "gameplay_start", actual_zone_id, actual_act, apparent_act, game_mode, nil)
    end
    if actual_zone_id == 0 and actual_act == 0 and events_fg_5 ~= 0 then
        emit_checkpoint_once(frame, "aiz1_intro_refresh_begin", actual_zone_id, actual_act, apparent_act, game_mode, nil)
    end
    if actual_zone_id == 0 and actual_act == 1 and apparent_act == 0 then
        emit_checkpoint_once(frame, "aiz2_reload_resume", actual_zone_id, actual_act, apparent_act, game_mode, nil)
    end
    if actual_zone_id == 0 and actual_act == 1 and move_lock == 0 and ctrl_locked == 0 then
        emit_checkpoint_once(frame, "aiz2_main_gameplay", actual_zone_id, actual_act, apparent_act, game_mode, nil)
    end
    if actual_zone_id == 1 and actual_act == 0 and move_lock == 0 and ctrl_locked == 0 then
        emit_checkpoint_once(frame, "hcz_handoff_complete", actual_zone_id, actual_act, apparent_act, game_mode, nil)
    end
end

-----------------
--- Recording ---
-----------------

local function reset_recording_state()
    -- Abandon any in-progress recording buffers and reset all frame
    -- counters so the next level-gameplay entry starts fresh.
    if physics_file then physics_file:close() end
    if aux_file then aux_file:close() end
    physics_file = nil
    aux_file = nil
    started = false
    trace_frame = 0
    bk2_frame_offset = 0
    start_x = 0
    start_y = 0
    start_zone_id = 0
    start_zone_name = "unknown"
    start_act = 0
    start_rng_seed = 0
    start_gameplay_frame_counter = 0
    prev_status = 0
    prev_routine = 0
    prev_ctrl_lock = 0
    prev_player_mode = nil
    known_objects = {}
    emitted_checkpoints = {}
    last_zone_act_state_key = nil
    prev_zone_id_for_transition = nil
    prev_act_for_transition = nil
    pre_trace_snapshots_written = false
    cage_hits = {}
    tails_xvel_writes = {}
    tails_yvel_writes = {}
    V65.normal_step = nil
    V66.boundary_state = nil
    os.remove(OUTPUT_DIR .. "physics.csv")
    os.remove(OUTPUT_DIR .. "aux_state.jsonl")
    os.remove(OUTPUT_DIR .. "metadata.json")
end

local function open_files()
    physics_file = io.open(OUTPUT_DIR .. "physics.csv", "w")
    aux_file = io.open(OUTPUT_DIR .. "aux_state.jsonl", "w")

    physics_file:write("frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,"
        .. "x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,"
        .. "vblank_counter,lag_counter,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,"
        .. "sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,"
        .. "sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,"
        .. "sidekick_status_byte,sidekick_stand_on_obj\n")
    physics_file:flush()
end

local function write_metadata()
    local meta_file = io.open(OUTPUT_DIR .. "metadata.json", "w")
    local fixture_notes = ""
    local rom_checksum = S3K_ROM_CHECKSUM
    if is_aiz_end_to_end_profile() then
        fixture_notes = "AIZ intro through HCZ handoff end-to-end fixture"
    elseif is_level_gated_reset_aware_profile() and start_zone_name == "cnz" then
        fixture_notes = "CNZ1+CNZ2 Sonic+Tails playthrough from level-select BK2 (pause+A reset from AIZ)"
    end
    meta_file:write("{\n")
    meta_file:write('  "game": "s3k",\n')
    meta_file:write('  "zone": "' .. start_zone_name .. '",\n')
    meta_file:write('  "zone_id": ' .. start_zone_id .. ',\n')
    meta_file:write('  "act": ' .. (start_act + 1) .. ',\n')
    meta_file:write('  "bk2_frame_offset": ' .. bk2_frame_offset .. ',\n')
    meta_file:write('  "trace_frame_count": ' .. trace_frame .. ',\n')
    meta_file:write('  "pre_trace_osc_frames": ' .. start_gameplay_frame_counter .. ',\n')
    meta_file:write('  "start_x": "0x' .. hex(start_x) .. '",\n')
    meta_file:write('  "start_y": "0x' .. hex(start_y) .. '",\n')
    meta_file:write('  "characters": ["sonic", "tails"],\n')
    meta_file:write('  "main_character": "sonic",\n')
    meta_file:write('  "sidekicks": ["tails"],\n')
    meta_file:write('  "rng_seed": "0x' .. hex(start_rng_seed, 8) .. '",\n')
    meta_file:write('  "recording_date": "' .. os.date("%Y-%m-%d") .. '",\n')
    meta_file:write('  "lua_script_version": "6.6-s3k",\n')
    -- trace_schema: csv schema is unchanged from 5. v5 CSV + new per-frame
    -- cpu_state, oscillation_state, object_state, and interact_state aux
    -- events are detected by parsers via aux_schema_extras rather than a
    -- schema bump. v6.3 adds cage_state_per_frame and cage_execution.
    -- v6.4 adds velocity_write_per_frame (Tails x_vel/y_vel write hooks
    -- with M68K PC for CNZ1 F3649 root-cause). v6.5 adds
    -- tails_cpu_normal_step_per_frame and sidekick_interact_object_per_frame.
    -- v6.6 adds aiz_boundary_state_per_frame for AIZ F4679 tree/boundary
    -- pre/post visibility. All diagnostic-only.
    meta_file:write('  "trace_schema": 5,\n')
    meta_file:write('  "csv_version": 5,\n')
    local aux_schema_extras
    if is_aiz_end_to_end_profile() then
        aux_schema_extras = '["cpu_state_per_frame", "oscillation_state_per_frame", "object_state_per_frame", "interact_state_per_frame", "velocity_write_per_frame", "tails_cpu_normal_step_per_frame", "sidekick_interact_object_per_frame", "aiz_boundary_state_per_frame"]'
    else
        aux_schema_extras = '["cpu_state_per_frame", "oscillation_state_per_frame", "object_state_per_frame", "interact_state_per_frame", "cage_state_per_frame", "cage_execution_per_frame", "velocity_write_per_frame", "tails_cpu_normal_step_per_frame", "sidekick_interact_object_per_frame"]'
    end
    meta_file:write('  "aux_schema_extras": ' .. aux_schema_extras .. ',\n')
    meta_file:write('  "trace_profile": "' .. TRACE_PROFILE .. '",\n')
    meta_file:write('  "bizhawk_version": "' .. BIZHAWK_VERSION .. '",\n')
    meta_file:write('  "genesis_core": "' .. GENESIS_CORE .. '",\n')
    meta_file:write('  "rom_checksum": "' .. rom_checksum .. '",\n')
    meta_file:write('  "notes": ' .. json_quote(fixture_notes) .. '\n')
    meta_file:write("}\n")
    meta_file:close()
    print(string.format("Metadata written. Zone: %s Act %d, Trace frames: %d",
        start_zone_name, start_act + 1, trace_frame))
end

local function write_tails_cpu_snapshot()
    if not aux_file then return end

    write_aux(string.format(
        '{"frame":-1,"vfc":%d,"event":"cpu_state_snapshot","character":"tails",'
            .. '"control_counter":%d,"respawn_counter":%d,"cpu_routine":%d,'
            .. '"target_x":"0x%04X","target_y":"0x%04X","interact_id":"0x%02X","jumping":%d}',
        mainmemory.read_u16_be(ADDR_FRAMECOUNT),
        mainmemory.read_u16_be(ADDR_TAILS_CONTROL_COUNTER),
        mainmemory.read_u16_be(ADDR_TAILS_RESPAWN_COUNTER),
        mainmemory.read_u16_be(ADDR_TAILS_CPU_ROUTINE),
        mainmemory.read_u16_be(ADDR_TAILS_CPU_TARGET_X),
        mainmemory.read_u16_be(ADDR_TAILS_CPU_TARGET_Y),
        mainmemory.read_u8(ADDR_TAILS_INTERACT_ID),
        mainmemory.read_u8(ADDR_TAILS_CPU_JUMPING)))
end

-- Per-frame oscillation_state event. Emitted once per recorded trace frame
-- with the full Oscillating_table contents ($42 bytes) plus the running
-- Level_frame_counter so engine replay can ROM-verify global oscillator
-- phase. Bytes are encoded as a single hex string for compactness.
-- Reads the table AFTER the frame's OscillateNumDo has run (recorder reads
-- happen in `on_frame_end`, after BizHawk's emu.frameadvance() ticked the
-- ROM's full main loop including OscillateNumDo).
local function write_oscillation_per_frame()
    if not aux_file then return end

    local parts = {}
    for i = 0, OSC_TABLE_SIZE - 1 do
        parts[#parts + 1] = string.format('%02X', mainmemory.read_u8(ADDR_OSC_TABLE + i))
    end

    write_aux(string.format(
        '{"frame":%d,"vfc":%d,"event":"oscillation_state","level_frame_counter":%d,"osc_table":"%s"}',
        trace_frame,
        mainmemory.read_u16_be(ADDR_FRAMECOUNT),
        mainmemory.read_u16_be(ADDR_FRAMECOUNT),
        table.concat(parts)))
end

-- Per-frame CPU state event. Emitted once per recorded trace frame so engine
-- replay can hydrate SidekickCpuController state at known ROM-checkpoint
-- instants. Captures the full Tails CPU global block plus Ctrl_2_logical
-- (held + pressed) which together determine the AI's per-frame decisions.
local function write_tails_cpu_per_frame()
    if not aux_file then return end

    write_aux(string.format(
        '{"frame":%d,"vfc":%d,"event":"cpu_state","character":"tails",'
            .. '"interact":"0x%04X","idle_timer":%d,"flight_timer":%d,'
            .. '"cpu_routine":%d,"target_x":"0x%04X","target_y":"0x%04X",'
            .. '"auto_fly_timer":%d,"auto_jump_flag":%d,'
            .. '"ctrl2_held":"0x%02X","ctrl2_pressed":"0x%02X"}',
        trace_frame,
        mainmemory.read_u16_be(ADDR_FRAMECOUNT),
        mainmemory.read_u16_be(ADDR_TAILS_CPU_INTERACT),
        mainmemory.read_u16_be(ADDR_TAILS_CPU_IDLE_TIMER),
        mainmemory.read_u16_be(ADDR_TAILS_CPU_FLIGHT_TIMER),
        mainmemory.read_u16_be(ADDR_TAILS_CPU_ROUTINE),
        mainmemory.read_u16_be(ADDR_TAILS_CPU_TARGET_X),
        mainmemory.read_u16_be(ADDR_TAILS_CPU_TARGET_Y),
        mainmemory.read_u8(ADDR_TAILS_CPU_AUTO_FLY_TIMER),
        mainmemory.read_u8(ADDR_TAILS_CPU_AUTO_JUMP_FLAG),
        mainmemory.read_u8(ADDR_CTRL2_HELD_LOGICAL),
        mainmemory.read_u8(ADDR_CTRL2_PRESSED_LOGICAL)))
end

local function snapshot_object_id_for_code(obj_code)
    if obj_code == OBJ_CNZ_BALLOON then
        return OBJ_ID_CNZ_BALLOON
    end
    return nil
end

local function build_object_fields(addr)
    local parts = {}
    for off = 0, OBJ_SLOT_SIZE - 1 do
        parts[#parts + 1] = string.format('"off_%02X":"0x%02X"', off, mainmemory.read_u8(addr + off))
    end

    parts[#parts + 1] = string.format('"x_pos":"0x%04X"', mainmemory.read_u16_be(addr + OFF_X_POS))
    parts[#parts + 1] = string.format('"x_sub":"0x%04X"', mainmemory.read_u16_be(addr + OFF_X_SUB))
    parts[#parts + 1] = string.format('"y_pos":"0x%04X"', mainmemory.read_u16_be(addr + OFF_Y_POS))
    parts[#parts + 1] = string.format('"y_sub":"0x%04X"', mainmemory.read_u16_be(addr + OFF_Y_SUB))

    local x_vel_raw = mainmemory.read_s16_be(addr + OFF_X_VEL)
    if x_vel_raw < 0 then x_vel_raw = x_vel_raw + 0x10000 end
    parts[#parts + 1] = string.format('"x_vel":"0x%04X"', x_vel_raw)

    local y_vel_raw = mainmemory.read_s16_be(addr + OFF_Y_VEL)
    if y_vel_raw < 0 then y_vel_raw = y_vel_raw + 0x10000 end
    parts[#parts + 1] = string.format('"y_vel":"0x%04X"', y_vel_raw)

    parts[#parts + 1] = string.format('"render_flags":"0x%02X"', mainmemory.read_u8(addr + 0x04))
    parts[#parts + 1] = string.format('"status":"0x%02X"', mainmemory.read_u8(addr + OFF_STATUS))
    parts[#parts + 1] = string.format('"routine":"0x%02X"', mainmemory.read_u8(addr + OFF_ROUTINE))
    parts[#parts + 1] = string.format('"mapping_frame":"0x%02X"', mainmemory.read_u8(addr + 0x22))
    parts[#parts + 1] = string.format('"anim":"0x%02X"', mainmemory.read_u8(addr + 0x20))
    parts[#parts + 1] = string.format('"anim_frame":"0x%02X"', mainmemory.read_u8(addr + 0x23))
    parts[#parts + 1] = string.format('"anim_frame_timer":"0x%02X"', mainmemory.read_u8(addr + 0x24))
    parts[#parts + 1] = string.format('"angle":"0x%02X"', mainmemory.read_u8(addr + OFF_ANGLE))
    parts[#parts + 1] = string.format('"subtype":"0x%02X"', mainmemory.read_u8(addr + 0x2C))
    parts[#parts + 1] = string.format('"collision_flags":"0x%02X"', mainmemory.read_u8(addr + 0x28))
    parts[#parts + 1] = string.format('"collision_property":"0x%02X"', mainmemory.read_u8(addr + 0x29))
    return "{" .. table.concat(parts, ",") .. "}"
end

local function write_object_snapshots()
    if not aux_file then return end

    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
    local count = 0
    for slot = OBJ_DYNAMIC_START, OBJ_TOTAL_SLOTS - 1 do
        local addr = OBJ_TABLE_START + (slot * OBJ_SLOT_SIZE)
        local obj_code = mainmemory.read_u32_be(addr)
        local object_id = snapshot_object_id_for_code(obj_code)
        if object_id ~= nil then
            write_aux(string.format(
                '{"frame":-1,"vfc":%d,"event":"object_state_snapshot",'
                    .. '"slot":%d,"object_type":"0x%02X","object_code":"0x%08X","fields":%s}',
                vfc, slot, object_id, obj_code, build_object_fields(addr)))
            count = count + 1
        end
    end
    print(string.format("Wrote %d pre-trace object_state_snapshot events.", count))
end

local function read_character_trace_state(base)
    local present = mainmemory.read_u32_be(base) ~= 0
    if not present then
        return {
            present = 0,
            x = 0,
            y = 0,
            x_speed = 0,
            y_speed = 0,
            g_speed = 0,
            angle = 0,
            air = 0,
            rolling = 0,
            ground_mode = 0,
            x_sub = 0,
            y_sub = 0,
            routine = 0,
            status = 0,
            stand_on_obj = 0,
        }
    end

    local status = mainmemory.read_u8(base + OFF_STATUS)
    local angle = mainmemory.read_u8(base + OFF_ANGLE)
    local air = (status & STATUS_IN_AIR) ~= 0
    local rolling = (status & STATUS_ROLLING) ~= 0

    return {
        present = 1,
        x = mainmemory.read_u16_be(base + OFF_X_POS),
        y = mainmemory.read_u16_be(base + OFF_Y_POS),
        x_speed = read_speed(base, OFF_X_VEL),
        y_speed = read_speed(base, OFF_Y_VEL),
        g_speed = read_speed(base, OFF_INERTIA),
        angle = angle,
        air = air and 1 or 0,
        rolling = rolling and 1 or 0,
        ground_mode = air and 0 or angle_to_ground_mode(angle),
        x_sub = mainmemory.read_u16_be(base + OFF_X_SUB),
        y_sub = mainmemory.read_u16_be(base + OFF_Y_SUB),
        routine = mainmemory.read_u8(base + OFF_ROUTINE),
        status = status,
        stand_on_obj = read_stand_on_slot_for(base),
    }
end

local function close_files()
    if physics_file then
        physics_file:close()
        physics_file = nil
    end
    if aux_file then
        aux_file:close()
        aux_file = nil
    end
end

local function build_slot_dump()
    local entries = {}
    local dynamic_end = OBJ_DYNAMIC_START + OBJ_DYNAMIC_COUNT
    for slot = OBJ_DYNAMIC_START, dynamic_end - 1 do
        local obj_code = read_object_code(slot)
        if obj_code ~= 0 then
            entries[#entries + 1] = string.format("[%d,\"0x%08X\"]", slot, obj_code)
        end
    end
    return "[" .. table.concat(entries, ",") .. "]"
end

local function scan_objects(player_x, player_y)
    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
    local any_appeared = false

    for slot = 1, OBJ_TOTAL_SLOTS - 1 do
        local addr = OBJ_TABLE_START + (slot * OBJ_SLOT_SIZE)
        local obj_code = mainmemory.read_u32_be(addr)
        local prev_code = known_objects[slot] or 0

        if obj_code ~= 0 and obj_code ~= prev_code then
            local obj_x = mainmemory.read_u16_be(addr + OFF_X_POS)
            local obj_y = mainmemory.read_u16_be(addr + OFF_Y_POS)
            local extra = ""
            if obj_code == OBJ_CNZ_BALLOON then
                local obj_angle = mainmemory.read_u8(addr + OFF_ANGLE)
                local base_y = mainmemory.read_u16_be(addr + 0x32)
                extra = string.format(',"angle":"0x%02X","base_y":"0x%04X"', obj_angle, base_y)
            end
            write_aux(string.format(
                '{"frame":%d,"vfc":%d,"event":"object_appeared","slot":%d,"object_type":"0x%08X","x":"0x%04X","y":"0x%04X"%s}',
                trace_frame, vfc, slot, obj_code, obj_x, obj_y, extra))
            any_appeared = true
        end

        if obj_code == 0 and prev_code ~= 0 then
            write_aux(string.format(
                '{"frame":%d,"vfc":%d,"event":"object_removed","slot":%d,"object_type":"0x%08X"}',
                trace_frame, vfc, slot, prev_code))
        end

        if obj_code ~= 0 then
            local obj_x = mainmemory.read_u16_be(addr + OFF_X_POS)
            local obj_y = mainmemory.read_u16_be(addr + OFF_Y_POS)
            local dx = math.abs(obj_x - player_x)
            local dy = math.abs(obj_y - player_y)
            if dx <= OBJECT_PROXIMITY and dy <= OBJECT_PROXIMITY then
                local obj_status = mainmemory.read_u8(addr + OFF_STATUS)
                local obj_routine = mainmemory.read_u8(addr + OFF_ROUTINE)
                local extra = ""
                if obj_code == OBJ_CNZ_BALLOON then
                    local obj_angle = mainmemory.read_u8(addr + OFF_ANGLE)
                    local base_y = mainmemory.read_u16_be(addr + 0x32)
                    extra = string.format(',"angle":"0x%02X","base_y":"0x%04X"', obj_angle, base_y)
                end
                write_aux(string.format(
                    '{"frame":%d,"vfc":%d,"event":"object_near","slot":%d,"type":"0x%08X",'
                    .. '"x":"0x%04X","y":"0x%04X","routine":"0x%02X","status":"0x%02X"%s}',
                    trace_frame, vfc, slot, obj_code, obj_x, obj_y, obj_routine, obj_status, extra))
            end
        end

        known_objects[slot] = obj_code
    end

    if any_appeared then
        local dump = build_slot_dump()
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"slot_dump","slots":%s}',
            trace_frame, vfc, dump))
    end
end

local function write_state_snapshot()
    local ctrl_lock = mainmemory.read_u16_be(PLAYER_BASE + OFF_CTRL_LOCK)
    local anim_id = mainmemory.read_u8(PLAYER_BASE + OFF_ANIM_ID)
    local status = mainmemory.read_u8(PLAYER_BASE + OFF_STATUS)
    local routine = mainmemory.read_u8(PLAYER_BASE + OFF_ROUTINE)
    local y_radius = mainmemory.read_s8(PLAYER_BASE + OFF_RADIUS_Y)
    local x_radius = mainmemory.read_s8(PLAYER_BASE + OFF_RADIUS_X)
    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)

    write_aux(string.format(
        '{"frame":%d,"vfc":%d,"event":"state_snapshot","control_locked":%s,"anim_id":%d,'
        .. '"status_byte":"0x%02X","routine":"0x%02X","y_radius":%d,"x_radius":%d,'
        .. '"on_object":%s,"pushing":%s,"underwater":%s,'
        .. '"roll_jumping":%s}',
        trace_frame,
        vfc,
        ctrl_lock > 0 and "true" or "false",
        anim_id,
        status,
        routine,
        y_radius,
        x_radius,
        ((status & STATUS_ON_OBJECT) ~= 0) and "true" or "false",
        ((status & STATUS_PUSHING) ~= 0) and "true" or "false",
        ((status & STATUS_UNDERWATER) ~= 0) and "true" or "false",
        ((status & STATUS_ROLL_JUMP) ~= 0) and "true" or "false"
    ))
end

local function emit_player_mode_event()
    local player_mode = mainmemory.read_u16_be(ADDR_PLAYER_MODE)
    if prev_player_mode == nil or player_mode ~= prev_player_mode then
        local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"player_mode_set","mode":%d}',
            trace_frame, vfc, player_mode))
        prev_player_mode = player_mode
    end
end

local function check_mode_changes(status, routine)
    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)

    local was_air = (prev_status & STATUS_IN_AIR) ~= 0
    local is_air = (status & STATUS_IN_AIR) ~= 0
    if was_air ~= is_air then
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"mode_change","field":"air","from":%d,"to":%d}',
            trace_frame, vfc, was_air and 1 or 0, is_air and 1 or 0))
        write_state_snapshot()
    end

    local was_rolling = (prev_status & STATUS_ROLLING) ~= 0
    local is_rolling = (status & STATUS_ROLLING) ~= 0
    if was_rolling ~= is_rolling then
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"mode_change","field":"rolling","from":%d,"to":%d}',
            trace_frame, vfc, was_rolling and 1 or 0, is_rolling and 1 or 0))
    end

    local was_on_obj = (prev_status & STATUS_ON_OBJECT) ~= 0
    local is_on_obj = (status & STATUS_ON_OBJECT) ~= 0
    if was_on_obj ~= is_on_obj then
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"mode_change","field":"on_object","from":%d,"to":%d}',
            trace_frame, vfc, was_on_obj and 1 or 0, is_on_obj and 1 or 0))
    end

    local ctrl_lock = mainmemory.read_u16_be(PLAYER_BASE + OFF_CTRL_LOCK)
    local was_locked = prev_ctrl_lock > 0
    local is_locked = ctrl_lock > 0
    if was_locked ~= is_locked then
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"mode_change","field":"control_locked","from":%d,"to":%d}',
            trace_frame, vfc, was_locked and 1 or 0, is_locked and 1 or 0))
    end
    prev_ctrl_lock = ctrl_lock

    if routine ~= prev_routine then
        local stand_on_obj = read_stand_on_slot()
        local sonic_x = mainmemory.read_u16_be(PLAYER_BASE + OFF_X_POS)
        local sonic_y = mainmemory.read_u16_be(PLAYER_BASE + OFF_Y_POS)
        local sonic_xvel = mainmemory.read_s16_be(PLAYER_BASE + OFF_X_VEL)
        local sonic_yvel = mainmemory.read_s16_be(PLAYER_BASE + OFF_Y_VEL)
        local sonic_inertia = mainmemory.read_s16_be(PLAYER_BASE + OFF_INERTIA)

        local obj_context = ""
        if stand_on_obj > 0 and stand_on_obj < OBJ_TOTAL_SLOTS then
            local obj_addr = OBJ_TABLE_START + (stand_on_obj * OBJ_SLOT_SIZE)
            local obj_code = mainmemory.read_u32_be(obj_addr)
            local obj_x = mainmemory.read_u16_be(obj_addr + OFF_X_POS)
            local obj_y = mainmemory.read_u16_be(obj_addr + OFF_Y_POS)
            local obj_routine = mainmemory.read_u8(obj_addr + OFF_ROUTINE)
            obj_context = string.format(
                ',"stand_obj_slot":%d,"stand_obj_type":"0x%08X","stand_obj_x":"0x%04X",'
                .. '"stand_obj_y":"0x%04X","stand_obj_routine":"0x%02X"',
                stand_on_obj, obj_code, obj_x, obj_y, obj_routine)
        end

        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"routine_change","from":"0x%02X","to":"0x%02X",'
            .. '"sonic_x":"0x%04X","sonic_y":"0x%04X","x_vel":%d,"y_vel":%d,"inertia":%d,'
            .. '"status":"0x%02X","stand_on_obj":%d%s}',
            trace_frame, vfc, prev_routine, routine,
            sonic_x, sonic_y, sonic_xvel, sonic_yvel, sonic_inertia,
            status, stand_on_obj, obj_context))

        if routine == ROUTINE_HURT or routine == ROUTINE_DEATH then
            write_state_snapshot()
        end
    end
    prev_routine = routine
end

-- Per-frame OBJECT STATE events. Emit one event per OST slot within
-- OBJECT_PROXIMITY of either Player_1 or Player_2 each frame. Captures
-- routine pointer first byte, status byte (offset $22), subtype (offset
-- $1C), x_pos / y_pos / x_radius / y_radius. For Obj_Spikes ($24090)
-- the status byte's bit 3 is p1_standing and bit 4 is p2_standing per
-- sonic3k.constants.asm "standing_mask = p1_standing|p2_standing".
local function write_object_states_per_frame(player1_x, player1_y, player2_present,
                                              player2_x, player2_y)
    if not aux_file then return end
    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
    for slot = 1, OBJ_TOTAL_SLOTS - 1 do
        local addr = OBJ_TABLE_START + (slot * OBJ_SLOT_SIZE)
        local obj_code = mainmemory.read_u32_be(addr)
        if obj_code ~= 0 then
            local obj_x = mainmemory.read_u16_be(addr + OFF_X_POS)
            local obj_y = mainmemory.read_u16_be(addr + OFF_Y_POS)
            local near_p1 = math.abs(obj_x - player1_x) <= OBJECT_PROXIMITY
                and math.abs(obj_y - player1_y) <= OBJECT_PROXIMITY
            local near_p2 = player2_present
                and math.abs(obj_x - player2_x) <= OBJECT_PROXIMITY
                and math.abs(obj_y - player2_y) <= OBJECT_PROXIMITY
            if near_p1 or near_p2 then
                local status_byte = mainmemory.read_u8(addr + OFF_STATUS)
                local subtype = mainmemory.read_u8(addr + 0x2C)
                local x_radius = mainmemory.read_u8(addr + OFF_RADIUS_X)
                local y_radius = mainmemory.read_u8(addr + OFF_RADIUS_Y)
                local routine_byte = mainmemory.read_u8(addr + OFF_ROUTINE)
                write_aux(string.format(
                    '{"frame":%d,"vfc":%d,"event":"object_state","slot":%d,'
                        .. '"object_code":"0x%08X","routine":"0x%02X",'
                        .. '"status":"0x%02X","subtype":"0x%02X",'
                        .. '"x":"0x%04X","y":"0x%04X",'
                        .. '"x_radius":%d,"y_radius":%d}',
                    trace_frame, vfc, slot, obj_code, routine_byte,
                    status_byte, subtype, obj_x, obj_y,
                    x_radius, y_radius))
            end
        end
    end
end

local function write_sidekick_interact_object_state(player2_present)
    if not aux_file then return end
    if not player2_present then return end

    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
    local interact_addr = mainmemory.read_u16_be(SIDEKICK_BASE + OFF_STAND_ON_OBJ)
    local interact_slot = interact_addr_to_slot(interact_addr)
    local tails_status = mainmemory.read_u8(SIDEKICK_BASE + OFF_STATUS)
    local tails_object_control = mainmemory.read_u8(SIDEKICK_BASE + OFF_OBJECT_CONTROL)
    local tails_render_flags = mainmemory.read_u8(SIDEKICK_BASE + 0x04)
    local tails_on_object = (tails_status & STATUS_ON_OBJECT) ~= 0

    local obj_addr = OBJ_TABLE_START + (interact_slot * OBJ_SLOT_SIZE)
    local object_code = 0
    local object_routine = 0
    local object_status = 0
    local object_x = 0
    local object_y = 0
    local object_subtype = 0
    local object_render_flags = 0
    local object_object_control = 0
    local object_active = false
    local object_destroyed = true
    local object_p1_standing = false
    local object_p2_standing = false

    if interact_slot > 0 and interact_slot < OBJ_TOTAL_SLOTS then
        object_code = mainmemory.read_u32_be(obj_addr)
        object_routine = mainmemory.read_u8(obj_addr + OFF_ROUTINE)
        object_status = mainmemory.read_u8(obj_addr + OFF_STATUS)
        object_x = mainmemory.read_u16_be(obj_addr + OFF_X_POS)
        object_y = mainmemory.read_u16_be(obj_addr + OFF_Y_POS)
        object_subtype = mainmemory.read_u8(obj_addr + 0x2C)
        object_render_flags = mainmemory.read_u8(obj_addr + 0x04)
        object_object_control = mainmemory.read_u8(obj_addr + OFF_OBJECT_CONTROL)
        object_active = object_code ~= 0
        object_destroyed = object_code == 0
        object_p1_standing = (object_status & 0x08) ~= 0
        object_p2_standing = (object_status & 0x10) ~= 0
    end

    write_aux(string.format(
        '{"frame":%d,"vfc":%d,"event":"sidekick_interact_object","character":"tails",'
            .. '"interact":"0x%04X","interact_slot":%d,'
            .. '"tails_render_flags":"0x%02X","tails_object_control":"0x%02X",'
            .. '"tails_status":"0x%02X","tails_on_object":%s,'
            .. '"object_code":"0x%08X","object_routine":"0x%02X",'
            .. '"object_status":"0x%02X","object_x":"0x%04X","object_y":"0x%04X",'
            .. '"object_subtype":"0x%02X","object_render_flags":"0x%02X",'
            .. '"object_object_control":"0x%02X","object_active":%s,'
            .. '"object_destroyed":%s,"object_p1_standing":%s,'
            .. '"object_p2_standing":%s}',
        trace_frame, vfc,
        interact_addr, interact_slot,
        tails_render_flags, tails_object_control,
        tails_status, tostring(tails_on_object),
        object_code, object_routine,
        object_status, object_x, object_y,
        object_subtype, object_render_flags,
        object_object_control, tostring(object_active),
        tostring(object_destroyed), tostring(object_p1_standing),
        tostring(object_p2_standing)))
end

-- Per-frame INTERACT STATE events. Emit one per active player capturing
-- the interact field (offset $42, RAM address of the object the player
-- is "linked" to via RideObject_SetRide or loc_1E154) resolved to OST
-- slot index, the player's status byte (offset $2A), the player's
-- status_secondary byte (offset $2B), and the player's object_control
-- byte (offset $2E). When object_control bit 7 is set on a player, ROM
-- SolidObject_cont (sonic3k.asm:41439) takes the bmi.w loc_1E0A2 branch
-- which skips side-push velocity zeroing -- the very mechanism the engine
-- ObjectManager.SolidContacts side-path must mirror in S3K. Used to
-- diagnose AIZ F2667 spike-vs-Tails divergence and CNZ F2222 cage
-- release-cooldown branch divergence.
--
-- v6.3-s3k fix: previously this routine read offset $2A and labelled it
-- "object_control" -- that was actually the status byte (Status_OnObj
-- bit 3, etc). The CNZ cage diagnosis at F2222 needs the real
-- object_control byte (offset $2E) so we can distinguish between cage
-- riders ($42 bit 1 set), CPU-controlled ($80 bit 7 set), or normal
-- physics paths.
local function write_interact_state_per_frame(player2_present)
    if not aux_file then return end
    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
    local p1_interact_addr = mainmemory.read_u16_be(PLAYER_BASE + OFF_STAND_ON_OBJ)
    local p1_interact_slot = interact_addr_to_slot(p1_interact_addr)
    local p1_status = mainmemory.read_u8(PLAYER_BASE + OFF_STATUS)
    local p1_status_secondary = mainmemory.read_u8(PLAYER_BASE + OFF_STATUS_SECONDARY)
    local p1_object_control = mainmemory.read_u8(PLAYER_BASE + OFF_OBJECT_CONTROL)
    write_aux(string.format(
        '{"frame":%d,"vfc":%d,"event":"interact_state","character":"sonic",'
            .. '"interact":"0x%04X","interact_slot":%d,"status":"0x%02X",'
            .. '"status_secondary":"0x%02X","object_control":"0x%02X"}',
        trace_frame, vfc, p1_interact_addr, p1_interact_slot,
        p1_status, p1_status_secondary, p1_object_control))
    if player2_present then
        local p2_interact_addr = mainmemory.read_u16_be(SIDEKICK_BASE + OFF_STAND_ON_OBJ)
        local p2_interact_slot = interact_addr_to_slot(p2_interact_addr)
        local p2_status = mainmemory.read_u8(SIDEKICK_BASE + OFF_STATUS)
        local p2_status_secondary = mainmemory.read_u8(SIDEKICK_BASE + OFF_STATUS_SECONDARY)
        local p2_object_control = mainmemory.read_u8(SIDEKICK_BASE + OFF_OBJECT_CONTROL)
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"interact_state","character":"tails",'
                .. '"interact":"0x%04X","interact_slot":%d,"status":"0x%02X",'
                .. '"status_secondary":"0x%02X","object_control":"0x%02X"}',
            trace_frame, vfc, p2_interact_addr, p2_interact_slot,
            p2_status, p2_status_secondary, p2_object_control))
    end
end

-- =====================================================================
-- CNZ Wire Cage execution hooks (v6.3-s3k)
-- =====================================================================
-- Diagnostic hooks for the CNZ cage's per-player handler (sub_338C4 and
-- the mounted/cooldown/release branches). For each hit we capture the
-- M68K register state at entry: a0 (cage object addr), a1 (player addr),
-- a2 (per-player state addr), d5 (input mask), d6 (player_standing_bit).
-- We also read the cage's state byte 1(a2) directly via mainmemory at the
-- captured a2 address, because that's what the cage's mounted-mode
-- branches at loc_339A0/loc_33ADE test against.
--
-- The hooks accumulate to cage_hits[] which is flushed at the start of
-- each on_frame_end into a single cage_execution event listing each
-- branch entered with its register state. This minimises aux file size
-- (typically ~6 hits/frame when both players interact with one cage)
-- while preserving the per-branch diagnostic data needed for CNZ1
-- F2222 release-cooldown root-cause analysis.

local function cage_record_hit(branch)
    if not aux_file then return end
    if not started then return end
    local a0 = emu.getregister("M68K A0") or 0
    local a1 = emu.getregister("M68K A1") or 0
    local a2 = emu.getregister("M68K A2") or 0
    local d5 = emu.getregister("M68K D5") or 0
    local d6 = emu.getregister("M68K D6") or 0
    local pc = emu.getregister("M68K PC") or 0

    local cage_addr = a0 % 0x10000
    local player_addr = a1 % 0x10000
    local state_addr = a2 % 0x10000
    local d5_w = d5 % 0x10000
    local d6_b = d6 % 0x100

    -- 1(a2) is the per-player state byte. Read directly from mainmemory.
    -- Bizhawk mainmemory addresses strip the leading $FF0000 prefix.
    local state_byte = 0xFF
    if state_addr >= 0x0000 and state_addr <= 0xFFFE then
        state_byte = mainmemory.read_u8(state_addr + 1)
    end

    -- Read a few key player fields directly so we can correlate with
    -- the cage's branch decisions: status (0x2A), object_control (0x2E).
    local player_status = 0xFF
    local player_obj_ctrl = 0xFF
    if player_addr >= 0x0000 and player_addr <= 0xFFFF - 0x4A then
        player_status = mainmemory.read_u8(player_addr + OFF_STATUS)
        player_obj_ctrl = mainmemory.read_u8(player_addr + OFF_OBJECT_CONTROL)
    end

    -- Cage status byte at OST offset $2A.
    local cage_status = 0xFF
    if cage_addr >= 0x0000 and cage_addr <= 0xFFFF - 0x4A then
        cage_status = mainmemory.read_u8(cage_addr + OFF_STATUS)
    end

    table.insert(cage_hits, {
        branch = branch,
        pc = pc,
        cage_addr = cage_addr,
        player_addr = player_addr,
        state_addr = state_addr,
        d5 = d5_w,
        d6 = d6_b,
        state_byte = state_byte,
        player_status = player_status,
        player_obj_ctrl = player_obj_ctrl,
        cage_status = cage_status,
    })
end

-- =====================================================================
-- Tails velocity-write hooks (v6.4-s3k)
-- =====================================================================
-- Hooks every byte/word write to Tails's x_vel ($FFB062-$FFB063) and
-- y_vel ($FFB064-$FFB065) RAM addresses. For each write we capture the
-- M68K PC at the writing instruction so we can identify exactly which
-- ROM code path produced the value. Flushed once per frame as a single
-- velocity_write event in on_frame_end (after CPU-state and cage events
-- so the schema is grouped logically).
--
-- Diagnostic-only: never feeds engine state. Used to root-cause the
-- CNZ1 trace F3649 divergence where ROM Tails x_speed jumps from
-- -$48 to -$0A00 in one frame but the engine only computes -$60.

-- event.onmemorywrite uses the M68K full bus address (with $FF high byte for
-- $FFFF0000-$FFFFFFFF Genesis work RAM), not the bus-truncated mainmemory
-- offset. See docs/BizHawk-2.11-win-x64/Lua/Genesis/Earthworm Jim 2.lua for
-- the precedent (`event.onmemorywrite(..., 0xffa1d4)` for M68K $FFFFA1D4).
local M68K_RAM_BASE         = 0xFF0000
local TAILS_XVEL_LO_ADDR    = M68K_RAM_BASE + SIDEKICK_BASE + OFF_X_VEL       -- 0xFFB062
local TAILS_XVEL_HI_ADDR    = M68K_RAM_BASE + SIDEKICK_BASE + OFF_X_VEL + 1   -- 0xFFB063
local TAILS_YVEL_LO_ADDR    = M68K_RAM_BASE + SIDEKICK_BASE + OFF_Y_VEL       -- 0xFFB064
local TAILS_YVEL_HI_ADDR    = M68K_RAM_BASE + SIDEKICK_BASE + OFF_Y_VEL + 1   -- 0xFFB065

-- Frame-range filter for velocity-write capture. Tails physics writes
-- velocity 1-3+ times per frame, which on a 42k-frame trace would add
-- ~100MB+ to the aux stream. Restrict to a window around the known
-- divergence frame (CNZ1 F3649). Operators wanting full trace coverage
-- can override via OGGF_S3K_VELOCITY_WRITE_RANGE env var (format
-- "<start>-<end>"; e.g. "0-99999" for full coverage). When unset, no
-- velocity_write events are recorded outside the default window.
local VELOCITY_WRITE_FRAME_START = 3640
local VELOCITY_WRITE_FRAME_END = 3660

local _vw_range = os.getenv("OGGF_S3K_VELOCITY_WRITE_RANGE")
if _vw_range and _vw_range ~= "" then
    local s, e = _vw_range:match("^(%d+)%-(%d+)$")
    if s and e then
        VELOCITY_WRITE_FRAME_START = tonumber(s)
        VELOCITY_WRITE_FRAME_END = tonumber(e)
    end
end

local function _vw_in_window()
    return trace_frame >= VELOCITY_WRITE_FRAME_START
        and trace_frame <= VELOCITY_WRITE_FRAME_END
end

local function tails_xvel_record_hit()
    if not aux_file then return end
    if not started then return end
    if not _vw_in_window() then return end
    local pc = emu.getregister("M68K PC") or 0
    -- Read the value AFTER the write (mainmemory reflects post-write state).
    local val = mainmemory.read_s16_be(SIDEKICK_BASE + OFF_X_VEL)
    if val < 0 then val = val + 0x10000 end
    table.insert(tails_xvel_writes, {pc = pc, val = val})
end

local function tails_yvel_record_hit()
    if not aux_file then return end
    if not started then return end
    if not _vw_in_window() then return end
    local pc = emu.getregister("M68K PC") or 0
    local val = mainmemory.read_s16_be(SIDEKICK_BASE + OFF_Y_VEL)
    if val < 0 then val = val + 0x10000 end
    table.insert(tails_yvel_writes, {pc = pc, val = val})
end

local velocity_hooks_registered = false

local function register_velocity_hooks()
    if velocity_hooks_registered then return end
    velocity_hooks_registered = true

    -- Hook BOTH the low and high bytes of each velocity word so we
    -- catch byte-granularity writes (rare in Tails physics but possible
    -- in low-level ROM code). The same callback fires for either byte
    -- because the post-write read is a u16 of the full word; consecutive
    -- byte writes in the same instruction will fire twice but we only
    -- record the final state per hit.
    event.onmemorywrite(tails_xvel_record_hit, TAILS_XVEL_LO_ADDR)
    event.onmemorywrite(tails_xvel_record_hit, TAILS_XVEL_HI_ADDR)
    event.onmemorywrite(tails_yvel_record_hit, TAILS_YVEL_LO_ADDR)
    event.onmemorywrite(tails_yvel_record_hit, TAILS_YVEL_HI_ADDR)

    print(string.format(
        "Tails velocity-write hooks registered: x_vel=0x%04X-0x%04X, y_vel=0x%04X-0x%04X, frame_window=[%d,%d]",
        TAILS_XVEL_LO_ADDR, TAILS_XVEL_HI_ADDR,
        TAILS_YVEL_LO_ADDR, TAILS_YVEL_HI_ADDR,
        VELOCITY_WRITE_FRAME_START, VELOCITY_WRITE_FRAME_END))
end

local function flush_tails_velocity_writes()
    if not aux_file then return end
    if #tails_xvel_writes == 0 and #tails_yvel_writes == 0 then return end
    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
    local x_parts = {}
    for _, hit in ipairs(tails_xvel_writes) do
        x_parts[#x_parts + 1] = string.format(
            '{"pc":"0x%05X","val":"0x%04X"}', hit.pc, hit.val)
    end
    local y_parts = {}
    for _, hit in ipairs(tails_yvel_writes) do
        y_parts[#y_parts + 1] = string.format(
            '{"pc":"0x%05X","val":"0x%04X"}', hit.pc, hit.val)
    end
    write_aux(string.format(
        '{"frame":%d,"vfc":%d,"event":"velocity_write","character":"tails",'
            .. '"x_vel_writes":[%s],"y_vel_writes":[%s]}',
        trace_frame, vfc,
        table.concat(x_parts, ","), table.concat(y_parts, ",")))
    tails_xvel_writes = {}
    tails_yvel_writes = {}
end

-- =====================================================================
-- Tails CPU normal-step hooks (v6.5-s3k)
-- =====================================================================
-- Focused diagnostics for the current CNZ F3905 frontier. These hooks
-- capture the ROM-side state around:
--   loc_13DD0/loc_13EB8 Tails CPU delayed input generation
--     (sonic3k.asm:26702-26705, 26717-26741)
--   loc_14A0A/loc_14B7A Tails_InputAcceleration_Path right-input and
--     post-path state (sonic3k.asm:27798-27805, 28103-28122,
--     27957-28017)
-- The event is comparison-only report context and must never feed replay
-- state.

function V65.u16(value)
    if value < 0 then
        return value + 0x10000
    end
    return value & 0xFFFF
end

function V65.hook_a0_is_tails()
    local a0 = emu.getregister("M68K A0") or 0
    return (a0 % 0x10000) == SIDEKICK_BASE
end

function V65.tails_cpu_step()
    if V65.normal_step == nil or V65.normal_step.frame ~= trace_frame then
        V65.normal_step = {
            frame = trace_frame,
            character = "tails",
            status = mainmemory.read_u8(SIDEKICK_BASE + OFF_STATUS),
            object_control = mainmemory.read_u8(SIDEKICK_BASE + OFF_OBJECT_CONTROL),
            ground_vel = V65.u16(mainmemory.read_s16_be(SIDEKICK_BASE + OFF_INERTIA)),
            x_vel = V65.u16(mainmemory.read_s16_be(SIDEKICK_BASE + OFF_X_VEL)),
            delayed_stat = 0,
            delayed_input = 0,
            loc_13dd0_branch = "not_seen",
            ctrl2_logical = mainmemory.read_u16_be(ADDR_CTRL2_HELD_LOGICAL),
            ctrl2_held_logical = mainmemory.read_u8(ADDR_CTRL2_HELD_LOGICAL),
            path_pre_ground_vel = 0,
            path_pre_x_vel = 0,
            path_pre_status = 0,
            path_post_ground_vel = 0,
            path_post_x_vel = 0,
            path_post_status = 0,
        }
    end
    return V65.normal_step
end

function V65.tails_cpu_record_loc_13dd0()
    if not aux_file then return end
    if not started then return end
    if not V65.hook_a0_is_tails() then return end
    local step = V65.tails_cpu_step()
    local delayed_index = (mainmemory.read_u16_be(ADDR_POS_TABLE_INDEX) - 0x44) & 0xFF
    step.delayed_input = mainmemory.read_u16_be(ADDR_STAT_TABLE + delayed_index)
    step.delayed_stat = mainmemory.read_u8(ADDR_STAT_TABLE + delayed_index + 2)
    local leader_status = mainmemory.read_u8(PLAYER_BASE + OFF_STATUS)
    local leader_ground_vel = mainmemory.read_s16_be(PLAYER_BASE + OFF_INERTIA)
    if (leader_status & STATUS_ON_OBJECT) ~= 0 then
        step.loc_13dd0_branch = "leader_on_object"
    elseif leader_ground_vel >= 0x400 then
        step.loc_13dd0_branch = "leader_fast"
    else
        step.loc_13dd0_branch = "fallthrough_sub20"
    end
end

function V65.tails_cpu_record_loc_13eb8()
    if not aux_file then return end
    if not started then return end
    if not V65.hook_a0_is_tails() then return end
    local step = V65.tails_cpu_step()
    local d1 = emu.getregister("M68K D1") or step.delayed_input
    step.delayed_input = d1 & 0xFFFF
    step.status = mainmemory.read_u8(SIDEKICK_BASE + OFF_STATUS)
    step.object_control = mainmemory.read_u8(SIDEKICK_BASE + OFF_OBJECT_CONTROL)
    step.ground_vel = V65.u16(mainmemory.read_s16_be(SIDEKICK_BASE + OFF_INERTIA))
    step.x_vel = V65.u16(mainmemory.read_s16_be(SIDEKICK_BASE + OFF_X_VEL))
end

function V65.tails_cpu_record_path_pre()
    if not aux_file then return end
    if not started then return end
    if not V65.hook_a0_is_tails() then return end
    local step = V65.tails_cpu_step()
    step.ctrl2_logical = mainmemory.read_u16_be(ADDR_CTRL2_HELD_LOGICAL)
    step.ctrl2_held_logical = mainmemory.read_u8(ADDR_CTRL2_HELD_LOGICAL)
    step.path_pre_ground_vel = V65.u16(mainmemory.read_s16_be(SIDEKICK_BASE + OFF_INERTIA))
    step.path_pre_x_vel = V65.u16(mainmemory.read_s16_be(SIDEKICK_BASE + OFF_X_VEL))
    step.path_pre_status = mainmemory.read_u8(SIDEKICK_BASE + OFF_STATUS)
end

function V65.tails_cpu_record_path_post()
    if not aux_file then return end
    if not started then return end
    if not V65.hook_a0_is_tails() then return end
    local step = V65.tails_cpu_step()
    step.path_post_ground_vel = V65.u16(mainmemory.read_s16_be(SIDEKICK_BASE + OFF_INERTIA))
    step.path_post_x_vel = V65.u16(mainmemory.read_s16_be(SIDEKICK_BASE + OFF_X_VEL))
    step.path_post_status = mainmemory.read_u8(SIDEKICK_BASE + OFF_STATUS)
end

function V65.register_tails_cpu_normal_step_hooks()
    if V65.tails_cpu_hooks_registered then return end
    V65.tails_cpu_hooks_registered = true

    event.onmemoryexecute(V65.tails_cpu_record_loc_13dd0, V65.TAILS_CPU_HOOK_LOC_13DD0)
    event.onmemoryexecute(V65.tails_cpu_record_loc_13eb8, V65.TAILS_CPU_HOOK_LOC_13EB8)
    event.onmemoryexecute(V65.tails_cpu_record_path_pre, V65.TAILS_PATH_HOOK_LOC_14A0A)
    event.onmemoryexecute(V65.tails_cpu_record_path_post, V65.TAILS_PATH_HOOK_LOC_14B7A)

    print(string.format(
        "Tails CPU normal-step hooks registered: loc_13DD0=0x%05X, loc_13EB8=0x%05X, loc_14A0A=0x%05X, loc_14B7A=0x%05X",
        V65.TAILS_CPU_HOOK_LOC_13DD0, V65.TAILS_CPU_HOOK_LOC_13EB8,
        V65.TAILS_PATH_HOOK_LOC_14A0A, V65.TAILS_PATH_HOOK_LOC_14B7A))
end

function V65.flush_tails_cpu_normal_step()
    if not aux_file then return end
    if V65.normal_step == nil or V65.normal_step.frame ~= trace_frame then
        return
    end
    local step = V65.normal_step
    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
    write_aux(string.format(
        '{"frame":%d,"vfc":%d,"event":"tails_cpu_normal_step","character":"tails",'
            .. '"status":"0x%02X","object_control":"0x%02X",'
            .. '"ground_vel":"0x%04X","x_vel":"0x%04X",'
            .. '"delayed_stat":"0x%02X","delayed_input":"0x%04X",'
            .. '"loc_13dd0_branch":"%s","ctrl2_logical":"0x%04X",'
            .. '"ctrl2_held_logical":"0x%02X",'
            .. '"path_pre_ground_vel":"0x%04X","path_pre_x_vel":"0x%04X",'
            .. '"path_pre_status":"0x%02X",'
            .. '"path_post_ground_vel":"0x%04X","path_post_x_vel":"0x%04X",'
            .. '"path_post_status":"0x%02X"}',
        trace_frame, vfc,
        step.status, step.object_control,
        step.ground_vel, step.x_vel,
        step.delayed_stat, step.delayed_input,
        step.loc_13dd0_branch, step.ctrl2_logical,
        step.ctrl2_held_logical,
        step.path_pre_ground_vel, step.path_pre_x_vel,
        step.path_pre_status,
        step.path_post_ground_vel, step.path_post_x_vel,
        step.path_post_status))
    V65.normal_step = nil
end

-- =====================================================================
-- AIZ boundary/tree hooks (v6.6-s3k)
-- =====================================================================
-- Focused diagnostics for the AIZ F4679 sidekick divergence. The ROM level
-- loop runs Process_Sprites before DeformBgLayer/ScreenEvents
-- (sonic3k.asm:7884-7898), DeformBgLayer moves camera then runs resize
-- events (sonic3k.asm:38298-38316), AIZ resize writes Camera_min_X_pos=$2D80
-- (sonic3k.asm:38961-38974), AIZTree_SetPlayerPos can reposition a player
-- and write x_vel/y_vel (sonic3k.asm:43776-43810), and
-- Tails_Check_Screen_Boundaries can clamp/kill using camera bounds
-- (sonic3k.asm:28407-28451). These hooks expose ROM-side order only; replay
-- must never hydrate engine state from this aux event.

local AIZ_BOUNDARY_FRAME_START = tonumber(os.getenv("OGGF_S3K_AIZ_BOUNDARY_FRAME_START") or "4660")
local AIZ_BOUNDARY_FRAME_END = tonumber(os.getenv("OGGF_S3K_AIZ_BOUNDARY_FRAME_END") or "4690")

function V66.u16(value)
    if value < 0 then
        return value + 0x10000
    end
    return value & 0xFFFF
end

function V66.in_window()
    if trace_frame < AIZ_BOUNDARY_FRAME_START or trace_frame > AIZ_BOUNDARY_FRAME_END then
        return false
    end
    return mainmemory.read_u8(ADDR_ZONE) == 0
end

function V66.a0_is_tails()
    local a0 = emu.getregister("M68K A0") or 0
    return (a0 % 0x10000) == SIDEKICK_BASE
end

function V66.a1_is_tails()
    local a1 = emu.getregister("M68K A1") or 0
    return (a1 % 0x10000) == SIDEKICK_BASE
end

function V66.snapshot_character(base)
    return {
        x = mainmemory.read_u16_be(base + OFF_X_POS),
        y = mainmemory.read_u16_be(base + OFF_Y_POS),
        x_vel = V66.u16(mainmemory.read_s16_be(base + OFF_X_VEL)),
        y_vel = V66.u16(mainmemory.read_s16_be(base + OFF_Y_VEL)),
    }
end

function V66.current()
    if V66.boundary_state == nil or V66.boundary_state.frame ~= trace_frame then
        local current = V66.snapshot_character(SIDEKICK_BASE)
        V66.boundary_state = {
            frame = trace_frame,
            character = "tails",
            camera_min_x = mainmemory.read_u16_be(V66.CAMERA_MIN_X),
            camera_max_x = mainmemory.read_u16_be(V66.CAMERA_MAX_X),
            camera_min_y = mainmemory.read_u16_be(V66.CAMERA_MIN_Y),
            camera_max_y = mainmemory.read_u16_be(V66.CAMERA_MAX_Y),
            tree_pre = current,
            tree_post = current,
            boundary_pre = current,
            boundary_post = current,
            boundary_action = "not_seen",
            post_move = current,
            seen = false,
        }
    end
    return V66.boundary_state
end

function V66.refresh_camera(state)
    state.camera_min_x = mainmemory.read_u16_be(V66.CAMERA_MIN_X)
    state.camera_max_x = mainmemory.read_u16_be(V66.CAMERA_MAX_X)
    state.camera_min_y = mainmemory.read_u16_be(V66.CAMERA_MIN_Y)
    state.camera_max_y = mainmemory.read_u16_be(V66.CAMERA_MAX_Y)
end

function V66.record_tree_pre()
    if not aux_file then return end
    if not started then return end
    if not V66.in_window() then return end
    if not V66.a1_is_tails() then return end
    local state = V66.current()
    V66.refresh_camera(state)
    state.tree_pre = V66.snapshot_character(SIDEKICK_BASE)
    state.seen = true
end

function V66.record_tree_post()
    if not aux_file then return end
    if not started then return end
    if not V66.in_window() then return end
    if not V66.a1_is_tails() then return end
    local state = V66.current()
    state.tree_post = V66.snapshot_character(SIDEKICK_BASE)
    state.seen = true
end

function V66.record_boundary_pre()
    if not aux_file then return end
    if not started then return end
    if not V66.in_window() then return end
    if not V66.a0_is_tails() then return end
    local state = V66.current()
    V66.refresh_camera(state)
    state.boundary_pre = V66.snapshot_character(SIDEKICK_BASE)
    state.boundary_action = "none"
    state.seen = true
end

function V66.record_boundary_clamp()
    if not aux_file then return end
    if not started then return end
    if not V66.in_window() then return end
    if not V66.a0_is_tails() then return end
    local state = V66.current()
    local d0 = emu.getregister("M68K D0") or 0
    state.boundary_action = string.format("x_clamp_%04X", d0 & 0xFFFF)
    state.seen = true
end

function V66.record_boundary_kill()
    if not aux_file then return end
    if not started then return end
    if not V66.in_window() then return end
    if not V66.a0_is_tails() then return end
    local state = V66.current()
    state.boundary_action = "kill"
    state.boundary_post = V66.snapshot_character(SIDEKICK_BASE)
    state.seen = true
end

function V66.record_boundary_return()
    if not aux_file then return end
    if not started then return end
    if not V66.in_window() then return end
    if not V66.a0_is_tails() then return end
    local state = V66.current()
    state.boundary_post = V66.snapshot_character(SIDEKICK_BASE)
    state.seen = true
end

function V66.format_state(prefix, values)
    return string.format(
        '"%s_x":"0x%04X","%s_y":"0x%04X","%s_x_vel":"0x%04X","%s_y_vel":"0x%04X"',
        prefix, values.x, prefix, values.y, prefix, values.x_vel, prefix, values.y_vel)
end

function V66.flush_aiz_boundary_state()
    if not aux_file then return end
    if V66.boundary_state == nil or V66.boundary_state.frame ~= trace_frame then
        return
    end
    local state = V66.boundary_state
    if not state.seen then
        V66.boundary_state = nil
        return
    end
    state.post_move = V66.snapshot_character(SIDEKICK_BASE)
    V66.refresh_camera(state)
    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
    write_aux(string.format(
        '{"frame":%d,"vfc":%d,"event":"aiz_boundary_state","character":"tails",'
            .. '"camera_min_x":"0x%04X","camera_max_x":"0x%04X",'
            .. '"camera_min_y":"0x%04X","camera_max_y":"0x%04X",'
            .. '%s,%s,%s,%s,"boundary_action":"%s",%s}',
        trace_frame, vfc,
        state.camera_min_x, state.camera_max_x,
        state.camera_min_y, state.camera_max_y,
        V66.format_state("tree_pre", state.tree_pre),
        V66.format_state("tree_post", state.tree_post),
        V66.format_state("boundary_pre", state.boundary_pre),
        V66.format_state("boundary_post", state.boundary_post),
        state.boundary_action,
        V66.format_state("post_move", state.post_move)))
    V66.boundary_state = nil
end

function V66.register_aiz_boundary_hooks()
    if V66.hooks_registered then return end
    V66.hooks_registered = true

    event.onmemoryexecute(V66.record_tree_pre, V66.AIZ_TREE_SET_PLAYER_POS_ENTRY)
    event.onmemoryexecute(V66.record_tree_post, V66.AIZ_TREE_SET_PLAYER_POS_POST_YVEL)
    event.onmemoryexecute(V66.record_boundary_pre, V66.TAILS_BOUNDARY_ENTRY)
    event.onmemoryexecute(V66.record_boundary_return, V66.TAILS_BOUNDARY_RETURN)
    event.onmemoryexecute(V66.record_boundary_kill, V66.TAILS_BOUNDARY_KILL)
    event.onmemoryexecute(V66.record_boundary_clamp, V66.TAILS_BOUNDARY_CLAMP)

    print(string.format(
        "AIZ boundary hooks registered: tree=0x%05X/0x%05X, boundary=0x%05X/0x%05X/0x%05X/0x%05X, frame_window=[%d,%d]",
        V66.AIZ_TREE_SET_PLAYER_POS_ENTRY, V66.AIZ_TREE_SET_PLAYER_POS_POST_YVEL,
        V66.TAILS_BOUNDARY_ENTRY, V66.TAILS_BOUNDARY_RETURN,
        V66.TAILS_BOUNDARY_KILL, V66.TAILS_BOUNDARY_CLAMP,
        AIZ_BOUNDARY_FRAME_START, AIZ_BOUNDARY_FRAME_END))
end

local cage_hooks_registered = false

local function register_cage_hooks()
    if cage_hooks_registered then return end
    cage_hooks_registered = true

    -- Hook the entry of each branch we care about. The execution order
    -- is: 3385E -> sub_338C4 -> [loc_339A0|capture-attempt] -> [loc_33ADE
    -- |loc_33B1E|release-jump-path]. We hook the LATER branches (after
    -- the test-and-branch decisions) so the hit record captures the
    -- branch the cage actually took.
    --
    -- IMPORTANT: hooks fire on PRE-fetch of the instruction at the address.
    -- Register state captures the pre-instruction register file.
    event.onmemoryexecute(function() cage_record_hit("sub_338C4_entry") end,
        CAGE_HOOK_SUB_338C4)
    event.onmemoryexecute(function() cage_record_hit("loc_339A0_mounted") end,
        CAGE_HOOK_LOC_339A0)
    event.onmemoryexecute(function() cage_record_hit("loc_33ADE_cooldown") end,
        CAGE_HOOK_LOC_33ADE)
    event.onmemoryexecute(function() cage_record_hit("loc_33B1E_continue") end,
        CAGE_HOOK_LOC_33B1E)
    event.onmemoryexecute(function() cage_record_hit("loc_33B62_release") end,
        CAGE_HOOK_LOC_33B62)

    print(string.format(
        "Cage execution hooks registered: sub_338C4=0x%05X, loc_339A0=0x%05X, "
            .. "loc_33ADE=0x%05X, loc_33B1E=0x%05X, loc_33B62=0x%05X",
        CAGE_HOOK_SUB_338C4, CAGE_HOOK_LOC_339A0, CAGE_HOOK_LOC_33ADE,
        CAGE_HOOK_LOC_33B1E, CAGE_HOOK_LOC_33B62))
end

local function flush_cage_hits()
    if not aux_file then return end
    if #cage_hits == 0 then return end
    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
    local parts = {}
    for _, hit in ipairs(cage_hits) do
        parts[#parts + 1] = string.format(
            '{"branch":"%s","pc":"0x%05X","cage_addr":"0x%04X",'
                .. '"player_addr":"0x%04X","state_addr":"0x%04X",'
                .. '"d5":"0x%04X","d6":"0x%02X","state_byte":"0x%02X",'
                .. '"player_status":"0x%02X","player_obj_ctrl":"0x%02X",'
                .. '"cage_status":"0x%02X"}',
            hit.branch, hit.pc, hit.cage_addr, hit.player_addr,
            hit.state_addr, hit.d5, hit.d6, hit.state_byte,
            hit.player_status, hit.player_obj_ctrl, hit.cage_status)
    end
    write_aux(string.format(
        '{"frame":%d,"vfc":%d,"event":"cage_execution","hits":[%s]}',
        trace_frame, vfc, table.concat(parts, ",")))
    cage_hits = {}
end

-- Per-frame poll for cage state-byte transitions. Walks all OST slots
-- looking for the cage routine pointer at offset 0. The cage's first-frame
-- init writes 0x0003385E (loc_3385E, the per-frame entry) to offset 0;
-- before init runs offset 0 is 0x00033836 (Obj_CNZWireCage init entry).
-- We accept either as the cage signature. Emits a cage_state event
-- per active cage with the per-player phase/state bytes ($30/$31 for P1,
-- $34/$35 for P2) plus the cage's status byte and position.
local CAGE_INIT_PTR = 0x00033836       -- Obj_CNZWireCage (label at sonic3k.asm:69813)
local CAGE_FRAME_PTR = 0x0003385E      -- loc_3385E (per-frame entry at sonic3k.asm:69829)

local function emit_cage_state_per_frame()
    if not aux_file then return end
    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
    for slot = 0, OBJ_TOTAL_SLOTS - 1 do
        local addr = OBJ_TABLE_START + (slot * OBJ_SLOT_SIZE)
        local code = mainmemory.read_u32_be(addr)
        if code == CAGE_INIT_PTR or code == CAGE_FRAME_PTR then
            local p1_state = mainmemory.read_u8(addr + 0x31)
            local p2_state = mainmemory.read_u8(addr + 0x35)
            local cage_status = mainmemory.read_u8(addr + OFF_STATUS)
            local p1_phase = mainmemory.read_u8(addr + 0x30)
            local p2_phase = mainmemory.read_u8(addr + 0x34)
            local cage_x = mainmemory.read_u16_be(addr + OFF_X_POS)
            local cage_y = mainmemory.read_u16_be(addr + OFF_Y_POS)
            local cage_subtype = mainmemory.read_u8(addr + 0x2C)
            -- Always emit the cage_state event for diagnostics (small
            -- volume because there are typically 1-2 active cages near
            -- the players).
            write_aux(string.format(
                '{"frame":%d,"vfc":%d,"event":"cage_state","slot":%d,'
                    .. '"x":"0x%04X","y":"0x%04X","subtype":"0x%02X",'
                    .. '"status":"0x%02X","p1_phase":"0x%02X",'
                    .. '"p1_state":"0x%02X","p2_phase":"0x%02X",'
                    .. '"p2_state":"0x%02X"}',
                trace_frame, vfc, slot, cage_x, cage_y, cage_subtype,
                cage_status, p1_phase, p1_state, p2_phase, p2_state))
        end
    end
end

-----------------
--- Main Loop ---
-----------------

local function on_frame_end()
    if finished then
        return
    end

    if HEADLESS and started then
        if BK2_FRAME_COUNT ~= nil and BK2_FRAME_COUNT > 0
            and (bk2_frame_offset + trace_frame) >= BK2_FRAME_COUNT then
            print(string.format(
                "Reached BK2 input end at trace frame %d (bk2 offset %d, input frames %d). Finalising.",
                trace_frame, bk2_frame_offset, BK2_FRAME_COUNT))
            finished = true
            return
        end
        if not movie.isloaded() then
            print(string.format(
                "Movie unloaded at trace frame %d (emu frame %d). Finalising before memory access.",
                trace_frame, emu.framecount()))
            finished = true
            return
        end
    end

    local game_mode = mainmemory.read_u8(ADDR_GAME_MODE)

    if should_discard_and_reset(game_mode) then
        print(string.format(
            "level_gated_reset_aware: detected soft-reset (Game_Mode=0x%02X) at trace frame %d. Discarding recording and re-arming.",
            game_mode, trace_frame))
        reset_recording_state()
        return
    end

    if is_level_gated_reset_aware_profile() and started then
        local current_zone_id = mainmemory.read_u8(ADDR_ZONE)
        if current_zone_id ~= start_zone_id then
            print(string.format(
                "level_gated_reset_aware: zone-leave detected (zone %d -> %d) at trace frame %d. Finalising.",
                start_zone_id, current_zone_id, trace_frame))
            finished = true
            return
        end
    end

    if not started then
        if should_start_recording(game_mode) then
            started = true
            bk2_frame_offset = emu.framecount()
            start_x = mainmemory.read_u16_be(PLAYER_BASE + OFF_X_POS)
            start_y = mainmemory.read_u16_be(PLAYER_BASE + OFF_Y_POS)
            start_zone_id = mainmemory.read_u8(ADDR_ZONE)
            start_act = mainmemory.read_u8(ADDR_ACT)
            start_rng_seed = mainmemory.read_u32_be(ADDR_RNG_SEED)
            start_gameplay_frame_counter = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
            start_zone_name = ZONE_NAMES[start_zone_id] or string.format("unknown_%02x", start_zone_id)

            open_files()
            write_metadata()
            print(string.format("Trace recording started at BizHawk frame %d, zone %s act %d, pos (%04X, %04X)",
                bk2_frame_offset, start_zone_name, start_act + 1, start_x, start_y))
            if is_aiz_end_to_end_profile() and bk2_frame_offset > 0 then
                local prefix = bk2_frame_offset > MOVIE_FRAME_SAFETY_MARGIN and "WARNING" or "Note"
                print(string.format(
                    "%s: aiz_end_to_end recording armed after movie frame 0 (offset %d); metadata preserves bk2_frame_offset.",
                    prefix,
                    bk2_frame_offset))
            end
            if movie.isloaded() then
                print(string.format("Movie length: %d frames", movie.length()))
            end
            if not is_aiz_end_to_end_profile() then
                return
            end
        end
        if not started then
            return
        end
    end

    if not is_aiz_end_to_end_profile()
        and not is_level_gated_reset_aware_profile()
        and game_mode ~= GAMEMODE_LEVEL then
        print("Left level gameplay at trace frame " .. trace_frame .. ". Finalising.")
        finished = true
        return
    end

    if HEADLESS and movie.isloaded() then
        local movie_length = movie.length()
        local end_frame_limit = movie_length
        local allow_post_movie_tail = false
        if BK2_FRAME_COUNT ~= nil and BK2_FRAME_COUNT > end_frame_limit then
            end_frame_limit = BK2_FRAME_COUNT
            allow_post_movie_tail = true
        end
        if end_frame_limit > 0 and (bk2_frame_offset + trace_frame) >= end_frame_limit then
            print(string.format(
                "Reached configured movie end at trace frame %d (bk2 offset %d, limit %d). Finalising.",
                trace_frame, bk2_frame_offset, end_frame_limit))
            finished = true
            return
        end
        if not allow_post_movie_tail and movie.mode() == "FINISHED" then
            print(string.format(
                "Movie playback finished at trace frame %d (emu frame %d). Finalising.",
                trace_frame, emu.framecount()))
            finished = true
            return
        end
    end

    if not pre_trace_snapshots_written then
        write_tails_cpu_snapshot()
        write_object_snapshots()
        pre_trace_snapshots_written = true
        -- v6.1-s3k: capture Level_frame_counter at the moment the first
        -- physics row is recorded. The engine's seeded-frame-0 mode
        -- teleports sprite state to trace frame 0 without running its
        -- own LevelLoop, so OscillationManager must be pre-advanced by
        -- this many ticks for the engine's first natural tick to land
        -- on the same lfc as ROM's trace frame 1. Profiles that arm
        -- and immediately return (level_gated_reset_aware) record the
        -- NEXT BizHawk frame as trace frame 0, so this value is one
        -- larger than start_gameplay_frame_counter. Profiles that arm
        -- and continue recording the arm-frame (aiz_end_to_end) record
        -- the SAME frame as trace frame 0, so this value matches the
        -- arm-time lfc. Capturing here unifies both paths.
        start_gameplay_frame_counter = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
    end

    local x = mainmemory.read_u16_be(PLAYER_BASE + OFF_X_POS)
    local y = mainmemory.read_u16_be(PLAYER_BASE + OFF_Y_POS)
    local x_sub = mainmemory.read_u16_be(PLAYER_BASE + OFF_X_SUB)
    local y_sub = mainmemory.read_u16_be(PLAYER_BASE + OFF_Y_SUB)
    local x_speed = read_speed(PLAYER_BASE, OFF_X_VEL)
    local y_speed = read_speed(PLAYER_BASE, OFF_Y_VEL)
    local g_speed = read_speed(PLAYER_BASE, OFF_INERTIA)
    local angle = mainmemory.read_u8(PLAYER_BASE + OFF_ANGLE)
    local status = mainmemory.read_u8(PLAYER_BASE + OFF_STATUS)
    local routine = mainmemory.read_u8(PLAYER_BASE + OFF_ROUTINE)

    local camera_x = mainmemory.read_u16_be(ADDR_CAMERA_X)
    local camera_y = mainmemory.read_u16_be(ADDR_CAMERA_Y)
    local rings = mainmemory.read_u16_be(ADDR_RING_COUNT)

    local air = (status & STATUS_IN_AIR) ~= 0
    local rolling = (status & STATUS_ROLLING) ~= 0
    local ground_mode = air and 0 or angle_to_ground_mode(angle)

    local raw_input = mainmemory.read_u8(ADDR_CTRL1)
    local input_mask = rom_joypad_to_mask(raw_input)

    local function uhex(val)
        if val < 0 then return val + 0x10000 end
        return val
    end

    local gameplay_frame_counter = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
    local stand_on_obj = read_stand_on_slot()
    local vblank_counter = mainmemory.read_u16_be(ADDR_VBLA_WORD)
    local lag_counter = mainmemory.read_u16_be(ADDR_LAG_FRAME_COUNT)
    local sidekick = read_character_trace_state(SIDEKICK_BASE)

    physics_file:write(string.format(
        "%04X,%04X,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d,%04X,%04X,%02X,%04X,%04X,%04X,%02X,%04X,%02X,%04X,%04X,"
            .. "%d,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d,%04X,%04X,%02X,%02X,%02X\n",
        trace_frame, input_mask, x, y,
        uhex(x_speed), uhex(y_speed), uhex(g_speed),
        angle,
        air and 1 or 0,
        rolling and 1 or 0,
        ground_mode,
        x_sub, y_sub,
        routine,
        camera_x, camera_y,
        rings,
        status,
        gameplay_frame_counter,
        stand_on_obj,
        vblank_counter,
        lag_counter,
        sidekick.present,
        sidekick.x,
        sidekick.y,
        uhex(sidekick.x_speed),
        uhex(sidekick.y_speed),
        uhex(sidekick.g_speed),
        sidekick.angle,
        sidekick.air,
        sidekick.rolling,
        sidekick.ground_mode,
        sidekick.x_sub,
        sidekick.y_sub,
        sidekick.routine,
        sidekick.status,
        sidekick.stand_on_obj))

    if trace_frame % 60 == 0 then
        physics_file:flush()
    end
    if trace_frame % 300 == 0 then
        write_metadata()
    end

    emit_s3k_semantic_events(trace_frame)
    emit_player_mode_event()
    check_mode_changes(status, routine)
    prev_status = status

    -- Per-frame CPU snapshot (v6 schema). Always emit so the trace replay can
    -- compare SidekickCpuController state against authoritative ROM values and
    -- rule out CPU-state drift as a source of divergence. Diagnostic-only.
    write_tails_cpu_per_frame()

    -- Focused Tails CPU normal-follow step diagnostics (v6.5 schema).
    -- memoryexecute hooks collect branch/input/path state during the ROM
    -- frame, and this flush emits one comparison-only event for the report.
    V65.flush_tails_cpu_normal_step()

    -- Focused AIZ tree/boundary diagnostics (v6.6 schema). Hook callbacks
    -- capture ROM-side pre/post state during Process_Sprites, and frame-end
    -- flushing keeps the aux stream aligned to the comparison sample.
    V66.flush_aiz_boundary_state()

    -- Per-frame oscillation snapshot (v6.1 schema). Always emit so the trace
    -- replay can ROM-verify the global oscillator phase used by HoverFan,
    -- platforms, and other oscillating objects.
    write_oscillation_per_frame()

    -- Per-frame OBJECT STATE / INTERACT STATE (v6.2 schema). Emitted as
    -- diagnostics for ROM-side spike object state and player object_control
    -- / interact fields at AIZ F2667-style player-vs-spike divergent frames.
    local sk_present = sidekick.present == 1
    write_object_states_per_frame(x, y, sk_present, sidekick.x, sidekick.y)
    write_interact_state_per_frame(sk_present)
    write_sidekick_interact_object_state(sk_present)

    -- Per-frame CNZ wire cage state (v6.3 schema). Emits one cage_state
    -- event per active cage object (per OST slot containing 0x0001365C)
    -- with the cage's status, both player phase/state bytes, and pos.
    -- Used to root-cause CNZ1 trace F2222 release-cooldown divergence.
    emit_cage_state_per_frame()

    -- Per-frame CNZ wire cage execution hits (v6.3 schema). The
    -- memoryexecute hooks accumulate hits during frame processing into
    -- cage_hits[]; we flush them as a single event per frame so the
    -- aux stream stays tidy. Each hit captures the cage branch entered
    -- (sub_338C4 entry, loc_339A0 mounted, loc_33ADE cooldown,
    -- loc_33B1E continue, loc_33B62 release) plus register state
    -- (a0/a1/a2/d5/d6) and the per-player state byte and player
    -- object_control. This lets us trace exactly which path the cage
    -- took for each player on each frame.
    flush_cage_hits()

    -- Per-frame Tails velocity-write hits (v6.4-s3k schema). The
    -- onmemorywrite hooks accumulate every write to Tails x_vel/y_vel
    -- during frame processing; we flush as a single velocity_write
    -- event per frame. Each hit records the M68K PC of the writing
    -- instruction plus the value written. Used to identify the ROM
    -- code path that sets Tails x_vel = -$0A00 at CNZ1 F3649 where
    -- the engine only reaches -$60 (a 1-frame phase shift).
    flush_tails_velocity_writes()

    if trace_frame % SNAPSHOT_INTERVAL == 0 then
        write_state_snapshot()
    end

    scan_objects(x, y)

    trace_frame = trace_frame + 1
end

os.execute("mkdir \"" .. OUTPUT_DIR .. "\" 2>NUL")

local HEADLESS_VISIBLE = false
if HEADLESS then
    emu.limitframerate(false)
    client.speedmode(6400)
    if not HEADLESS_VISIBLE then
        client.invisibleemulation(true)
    end
end

local wait_desc
if is_aiz_end_to_end_profile() then
    wait_desc = "BK2 frame 0"
elseif is_level_gated_reset_aware_profile() then
    wait_desc = "level gameplay (Game_Mode=0x0C, reset-aware discards on soft-reset to title)"
else
    wait_desc = "level gameplay (Game_Mode=0x0C, controls unlocked)"
end
print(string.format("S3K Trace Recorder v6.6-s3k loaded. Profile=%s. Waiting for %s...", TRACE_PROFILE, wait_desc))

-- Register the CNZ wire cage execution hooks. Done once at script load
-- before the main loop runs so the memoryexecute callbacks are armed for
-- every frame the script processes. Only active when 'started' so we
-- don't accumulate hits during pre-trace level loading.
register_cage_hooks()

-- Register Tails velocity-write hooks. Same lifetime model as cage hooks.
register_velocity_hooks()

-- Register focused Tails CPU normal-step hooks. Same lifetime model as cage hooks.
V65.register_tails_cpu_normal_step_hooks()

-- Register focused AIZ tree/boundary hooks. Same lifetime model as cage hooks.
V66.register_aiz_boundary_hooks()

while true do
    on_frame_end()

    if finished then
        print("Recording complete. Writing final output...")
        if is_level_gated_reset_aware_profile() and aux_file then
            local end_zone = mainmemory.read_u8(ADDR_ZONE)
            local end_act = mainmemory.read_u8(ADDR_ACT)
            local end_apparent_act = mainmemory.read_u8(ADDR_APPARENT_ACT)
            local end_game_mode = mainmemory.read_u8(ADDR_GAME_MODE)
            emit_checkpoint_once(trace_frame, "gameplay_end",
                end_zone, end_act, end_apparent_act, end_game_mode, nil)
        end
        if physics_file then physics_file:flush() end
        write_metadata()
        close_files()
        print(string.format("Trace finalised: %s act %d, %d frames.",
            start_zone_name, start_act + 1, trace_frame))
        if HEADLESS then
            client.exit()
        end
        break
    end

    if client.ispaused() then
        client.unpause()
    end

    emu.frameadvance()
end
