# Configuration

The engine reads settings from `config.json` in the working directory (next to the JAR).
If the file does not exist, built-in defaults are used. This page answers common setup
questions. For the full reference of every key, see the
[Configuration Reference](../../../CONFIGURATION.md).

---

## How do I change the window size?

Set `SCREEN_WIDTH` and `SCREEN_HEIGHT` to the window dimensions you want in OS pixels.
For pixel-perfect 2x scaling of the native 320x224 resolution:

```json
{
  "SCREEN_WIDTH": 640,
  "SCREEN_HEIGHT": 448
}
```

For 3x:

```json
{
  "SCREEN_WIDTH": 960,
  "SCREEN_HEIGHT": 672
}
```

Do not change `SCREEN_WIDTH_PIXELS` or `SCREEN_HEIGHT_PIXELS` unless you understand the
implications -- these control the logical resolution, not the window size.

## How do I skip the title screen?

To skip the master title screen (game picker) and boot directly into a game:

```json
{
  "MASTER_TITLE_SCREEN_ON_STARTUP": false,
  "DEFAULT_ROM": "s2"
}
```

To also skip the game's own title screen and go straight to gameplay:

```json
{
  "MASTER_TITLE_SCREEN_ON_STARTUP": false,
  "TITLE_SCREEN_ON_STARTUP": false,
  "DEFAULT_ROM": "s2"
}
```

## How do I start on a specific zone?

Enable the level select screen:

```json
{
  "LEVEL_SELECT_ON_STARTUP": true
}
```

This opens the game's level select menu instead of starting from the first zone.

## How do I play as Tails?

Set the sidekick character:

```json
{
  "SIDEKICK_CHARACTER_CODE": "tails"
}
```

Tails will appear as a CPU-controlled follower alongside Sonic. Set to `""` (empty) to
disable.

## How do I enable cross-game features?

Cross-game feature donation lets a donor game provide player sprites, spindash, and
SFX while you play a different base game. For example, playing Sonic 1 with Sonic 2's
sprites and spindash:

```json
{
  "CROSS_GAME_FEATURES_ENABLED": true,
  "CROSS_GAME_SOURCE": "s2"
}
```

Both the base game ROM and the donor game ROM must be present.

## How do I change controls?

Key bindings accept either GLFW key codes (integers) or human-readable names. The following
formats all work:

- `81`
- `"81"`
- `"Q"`
- `"SPACE"`
- `"LEFT_SHIFT"`
- `"GLFW_KEY_F9"`

Invalid names log a warning and fall back to the default binding for that action. If you want
the raw numeric values, find the code for your preferred key in the
[GLFW key reference](https://www.glfw.org/docs/latest/group__keys.html).

Common key codes:

| Key | Code | Key | Code |
|-----|------|-----|------|
| Arrow Up | 265 | Space | 32 |
| Arrow Down | 264 | Enter | 257 |
| Arrow Left | 263 | Tab | 258 |
| Arrow Right | 262 | Escape | 256 |
| A | 65 | Z | 90 |
| S | 83 | X | 88 |

Example: rebind jump to the A key:

```json
{
  "JUMP": "A"
}
```

See [Controls](controls.md) for the full list of bindable actions.

## How do I enable the editor overlay?

```json
{
  "EDITOR_ENABLED": true
}
```

With that enabled, press `Shift+Tab` during gameplay to enter the experimental editor overlay,
and press `Shift+Tab` again to resume playtesting.

## How do I mute audio?

```json
{
  "AUDIO_ENABLED": false
}
```

## How do I switch between NTSC and PAL?

```json
{
  "REGION": "PAL"
}
```

PAL runs at 50 Hz instead of 60 Hz and affects audio timing. The default is `"NTSC"`.

## How do I use different ROM filenames?

If your ROM files have different names from the defaults:

```json
{
  "SONIC_1_ROM": "my-sonic1.bin",
  "SONIC_2_ROM": "my-sonic2.bin",
  "SONIC_3K_ROM": "my-s3k.bin"
}
```

Paths are relative to the working directory.

## How do I skip S3K intro cutscenes?

```json
{
  "S3K_SKIP_INTROS": true
}
```

This skips sequences like the AIZ biplane intro and boots straight into gameplay.
