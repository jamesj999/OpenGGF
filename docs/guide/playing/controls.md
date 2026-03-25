# Controls Reference

All controls are keyboard-based. Key bindings can be changed in `config.json`
(see [Configuration](configuration.md)).

## Gameplay

| Key | Action |
|-----|--------|
| Arrow Keys | Move left/right, look up, crouch/roll |
| Space | Jump |
| Enter | Pause / unpause |
| Q | Advance one frame (while paused) |

## Zone Navigation

These shortcuts let you move through the game quickly during development or exploration.

| Key | Action |
|-----|--------|
| Z | Cycle to the next zone |
| X | Cycle to the next act within the current zone |

## Debug Overlays

These toggle visual debug information drawn over the game scene. They require
`DEBUG_VIEW_ENABLED` to be `true` in config (it is by default).

| Key | Overlay |
|-----|---------|
| F1 | **Debug text** -- Player position, velocity, angle, and state information |
| F2 | **Shortcuts** -- On-screen reference for available key bindings |
| F3 | **Player panel** -- Detailed player state readout |
| F4 | **Sensor labels** -- Collision sensor ray positions and directions |
| F5 | **Object labels** -- Names and positions of active objects |
| F6 | **Camera bounds** -- Current camera boundary rectangle |
| F7 | **Player bounds** -- Player collision bounding box |
| F9 | **Ring bounds** -- Ring collision areas |
| F10 | **Plane switchers** -- Plane switcher trigger zones |
| F11 | **Touch response** -- Object touch/collision areas |
| F12 | **Art viewer** -- Loaded sprite art atlas |

## Debug Mode

| Key | Action |
|-----|--------|
| D | Toggle free-fly debug mode (move camera freely with arrow keys) |
| C | Teleport to the last activated checkpoint |

## Super Sonic / Emerald Debug

| Key | Action |
|-----|--------|
| E | Instantly award all Chaos Emeralds |
| U | Toggle Super Sonic transformation (requires all emeralds) |

## Special Stage Debug

These keys are only active during a Special Stage.

| Key | Action |
|-----|--------|
| Tab | Enter / exit Special Stage mode |
| End | Complete the current Special Stage (award emerald) |
| Delete | Fail the current Special Stage |
| F12 | Toggle Special Stage sprite debug viewer |
| F3 | Cycle Special Stage plane visibility debug modes |
