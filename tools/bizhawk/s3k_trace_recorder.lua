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
    meta_file:write('  "lua_script_version": "6.0-s3k",\n')
    -- trace_schema: csv schema is unchanged from 5. v5 CSV + new per-frame
    -- cpu_state aux events are detected by parsers via the absence/presence
    -- of the cpu_state event in aux_state.jsonl rather than a schema bump.
    meta_file:write('  "trace_schema": 5,\n')
    meta_file:write('  "csv_version": 5,\n')
    meta_file:write('  "aux_schema_extras": ["cpu_state_per_frame"],\n')
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
    -- hydrate engine SidekickCpuController state from authoritative ROM values
    -- and rule out CPU-state drift as a source of divergence.
    write_tails_cpu_per_frame()

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
print(string.format("S3K Trace Recorder v5.4-s3k loaded. Profile=%s. Waiting for %s...", TRACE_PROFILE, wait_desc))

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
