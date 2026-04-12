# Configuration Reference

All settings live in `config.json` in the working directory (next to the JAR). If absent, the
bundled `src/main/resources/config.json` is used as the default. Keys are enum names from
`SonicConfiguration`; values are JSON strings, numbers, or booleans.

Key bindings use **GLFW key codes** (integers). See the
[GLFW key token reference](https://www.glfw.org/docs/latest/group__keys.html) for the full list.
Common values are shown in the tables below.

---

## Display

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `SCREEN_WIDTH_PIXELS` | int | `320` | Logical pixel width — the Mega Drive native horizontal resolution. Changing this stretches/crops the rendered scene. |
| `SCREEN_HEIGHT_PIXELS` | int | `224` | Logical pixel height — the Mega Drive native vertical resolution (224 for NTSC, 240 for PAL). |
| `SCREEN_WIDTH` | int | `640` | Actual window width in OS pixels. Set to `SCREEN_WIDTH_PIXELS × scale` for pixel-perfect output. |
| `SCREEN_HEIGHT` | int | `448` | Actual window height in OS pixels. |
| `SCALE` | double | `1.0` | Additional rendering scale factor applied on top of the window dimensions. |
| `FPS` | int | `60` | Target frames per second. Affects game speed — use `60` for NTSC, `50` for PAL. |

---

## ROM Files

Paths are relative to the working directory (where the JAR is launched).

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `DEFAULT_ROM` | string | `"s1"` | Which game to boot: `"s1"`, `"s2"`, or `"s3k"`. Selects the corresponding ROM key below. |
| `SONIC_1_ROM` | string | `"Sonic The Hedgehog (W) (REV01) [!].gen"` | Filename of the Sonic 1 ROM. |
| `SONIC_2_ROM` | string | `"Sonic The Hedgehog 2 (W) (REV01) [!].gen"` | Filename of the Sonic 2 ROM. |
| `SONIC_3K_ROM` | string | `"Sonic 3 & Knuckles (W) [!].gen"` | Filename of the Sonic 3&K ROM. |

---

## Startup Flow

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `MASTER_TITLE_SCREEN_ON_STARTUP` | bool | `true` | Show the master title / game-selection screen on launch. When `false`, boots directly into the game set by `DEFAULT_ROM`. |
| `TITLE_SCREEN_ON_STARTUP` | bool | `true` | Show the game-specific title screen (e.g. Sonic 2 title screen) before gameplay. Ignored when `MASTER_TITLE_SCREEN_ON_STARTUP` is true and game selection is pending. |
| `LEVEL_SELECT_ON_STARTUP` | bool | `false` | Jump straight to the level select screen instead of the title screen. Useful for development. |
| `S3K_SKIP_INTROS` | bool | `false` | (S3K only) Skip zone intro sequences such as the AIZ biplane cutscene and boot straight into playable gameplay. |

---

## Characters

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `MAIN_CHARACTER_CODE` | string | `"sonic"` | Identity of the player-controlled character. Currently only `"sonic"` is supported. |
| `SIDEKICK_CHARACTER_CODE` | string | `""` | CPU-controlled sidekick spawned alongside the main character. Set to `"tails"` to enable Tails AI, `"sonic"` to clone the player, or `""` (empty) to disable. |

---

## Audio

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `AUDIO_ENABLED` | bool | `true` | Master switch for all audio output (music and SFX). |
| `REGION` | string | `"NTSC"` | Hardware region: `"NTSC"` (60 Hz) or `"PAL"` (50 Hz). Affects SMPS tempo timing and DAC sample rates. |
| `DAC_INTERPOLATE` | bool | `true` | Apply linear interpolation to DAC (drum) samples. Reduces aliasing noise for a smoother sound. |
| `AUDIO_INTERNAL_RATE_OUTPUT` | bool | `false` | Output audio at the YM2612 internal sample rate (~53 kHz) rather than the system rate. Useful for bit-accurate captures; may cause issues on some audio drivers. |
| `PSG_NOISE_SHIFT_EVERY_TOGGLE` | bool | `true` | PSG noise LFSR clock behaviour. `true` = shift on every polarity toggle (MAME-style, brighter noise); `false` = shift on positive edges only (Genesis Plus GX / libvgm style, darker noise). |
| `FM6_DAC_OFF` | bool | `true` | Silence FM channel 6 whenever a DAC note is active. Matches the SMPSPlay parity hack used in Sonic 2; prevents FM bleed audible during percussion. |

---

## Debug

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `DEBUG_VIEW_ENABLED` | bool | `true` | Eagerly initialise the debug overlay subsystem. Required for any runtime debug keys to function. Does not show anything on-screen until debug mode is activated. |
| `DEBUG_COLLISION_VIEW_ENABLED` | bool | `false` | Draw collision sensor rays and solid object outlines over the scene at all times. |

---

## Key Bindings

Key bindings accept any of the following formats:

| Format | Example | Notes |
|--------|---------|-------|
| GLFW numeric code | `81` | Traditional format |
| Numeric string | `"81"` | Same as above, as a string |
| Key name | `"Q"` | Human-readable, case-insensitive |
| Named key | `"SPACE"`, `"ENTER"`, `"F9"` | Special keys by name |
| Modifier key | `"LEFT_SHIFT"`, `"RIGHT_CONTROL"` | Modifier keys |
| GLFW prefix | `"GLFW_KEY_Q"` | Full GLFW constant name (prefix stripped) |

Invalid key names log a warning and fall back to the default binding for that key.

The tables below list each key's name, default code, and the human-readable key name for the default.

### Gameplay Controls

| Key | Default | Key Name | Description |
|-----|---------|----------|-------------|
| `UP` | `265` | ↑ Arrow | Look up / enter tubes. |
| `DOWN` | `264` | ↓ Arrow | Crouch / roll / spindash charge. |
| `LEFT` | `263` | ← Arrow | Move left. |
| `RIGHT` | `262` | → Arrow | Move right. |
| `JUMP` | `32` | Space | Jump / action button. |
| `PAUSE_KEY` | `257` | Enter | Pause / unpause the game. |
| `FRAME_STEP_KEY` | `81` | Q | Advance one frame while paused. |

### Debug Navigation

| Key | Default | Key Name | Description |
|-----|---------|----------|-------------|
| `NEXT_ACT` | `88` | X | Skip to the next act within the current zone. |
| `NEXT_ZONE` | `90` | Z | Skip to the first act of the next zone. |
| `DEBUG_MODE_KEY` | `68` | D | Toggle free-fly debug movement mode (requires `DEBUG_VIEW_ENABLED`). |
| `DEBUG_LAST_CHECKPOINT_KEY` | `67` | C | Teleport the player to the most recently activated checkpoint. |
| `LEVEL_SELECT_KEY` | `298` | F9 | Open the level select screen at runtime. |
| `TEST` | `84` | T | Generic test button used during development. |

### Super Sonic / Emerald Debug

| Key | Default | Key Name | Description |
|-----|---------|----------|-------------|
| `SUPER_SONIC_DEBUG_KEY` | `85` | U | Toggle Super Sonic transformation (requires `DEBUG_VIEW_ENABLED` and all emeralds). |
| `GIVE_EMERALDS_KEY` | `69` | E | Instantly award all Chaos Emeralds (debug shortcut). |

### Special Stage Debug

These keys are only active while a Special Stage is running.

| Key | Default | Key Name | Description |
|-----|---------|----------|-------------|
| `SPECIAL_STAGE_KEY` | `258` | Tab | Enter / exit Special Stage mode (debug). |
| `SPECIAL_STAGE_COMPLETE_KEY` | `269` | End | Complete the current Special Stage and award the emerald. |
| `SPECIAL_STAGE_FAIL_KEY` | `261` | Delete | Fail the current Special Stage without awarding the emerald. |
| `SPECIAL_STAGE_SPRITE_DEBUG_KEY` | `301` | F12 | Toggle the Special Stage sprite debug viewer. |
| `SPECIAL_STAGE_PLANE_DEBUG_KEY` | `292` | F3 | Cycle Special Stage plane visibility debug modes. |

---

## Example `config.json`

```json
{
  "DEFAULT_ROM": "s2",
  "SONIC_1_ROM": "Sonic The Hedgehog (W) (REV01) [!].gen",
  "SONIC_2_ROM": "Sonic The Hedgehog 2 (W) (REV01) [!].gen",
  "SONIC_3K_ROM": "Sonic 3 & Knuckles (W) [!].gen",
  "SCREEN_WIDTH_PIXELS": 320,
  "SCREEN_HEIGHT_PIXELS": 224,
  "SCREEN_WIDTH": 640,
  "SCREEN_HEIGHT": 448,
  "FPS": 60,
  "MASTER_TITLE_SCREEN_ON_STARTUP": true,
  "TITLE_SCREEN_ON_STARTUP": true,
  "LEVEL_SELECT_ON_STARTUP": false,
  "S3K_SKIP_INTROS": false,
  "SIDEKICK_CHARACTER_CODE": "",
  "AUDIO_ENABLED": true,
  "REGION": "NTSC",
  "DAC_INTERPOLATE": true,
  "FM6_DAC_OFF": true,
  "AUDIO_INTERNAL_RATE_OUTPUT": false,
  "PSG_NOISE_SHIFT_EVERY_TOGGLE": true,
  "DEBUG_VIEW_ENABLED": true,
  "DEBUG_COLLISION_VIEW_ENABLED": false,
  "UP": "UP",
  "DOWN": "DOWN",
  "LEFT": "LEFT",
  "RIGHT": "RIGHT",
  "JUMP": "SPACE",
  "PAUSE_KEY": "ENTER",
  "FRAME_STEP_KEY": "Q"
}
```
