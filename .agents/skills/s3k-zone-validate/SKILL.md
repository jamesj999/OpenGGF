---
name: s3k-zone-validate
description: Use when validating S3K zone feature implementations — captures stable-retro reference screenshots and compares against engine output using image recognition for feature presence verification.
---

# Validate S3K Zone Features

Validate zone feature implementations by visual comparison between the original game (via stable-retro) and the engine. This skill uses **image recognition** (agent visual analysis) rather than pixel-perfect diffing -- the engine's rendering pipeline differs enough from VDP hardware that exact pixel matching is unreliable, but feature presence, layer structure, and animation behaviour are visually verifiable.

## Inputs

$ARGUMENTS: Zone abbreviation and optional feature filter. Examples:
- `"HCZ"` -- validate all features for Hydrocity Zone
- `"LBZ parallax"` -- validate only parallax for Launch Base Zone
- `"AIZ palette"` -- validate only palette cycling for Angel Island Zone
- `"CNZ1 events"` -- validate events/camera for Carnival Night Zone Act 1

Zone abbreviations: AIZ, HCZ, MGZ, CNZ, FBZ, ICZ, LBZ, MHZ, SOZ, LRZ, HPZ, SSZ, DEZ, DDZ.

## Prerequisites

- **Python 3.8+** with `stable-retro` and `numpy` installed
- **S3K ROM** imported into stable-retro (`python -m stable_retro.import /path/to/rom/dir/`)
- **Engine built** (`mvn package`)
- **Zone analysis spec** at `docs/s3k-zones/{zone}-analysis.md` (produced by `s3k-zone-analysis` skill) -- used to determine which features to validate

### One-time setup

```bash
pip install stable-retro numpy Pillow
python -m stable_retro.import /path/to/directory/containing/s3k/rom/
```

The ROM filename must match what stable-retro expects for `SonicAndKnuckles-Genesis`. Verify import:
```bash
python -c "import retro; env = retro.make('SonicAndKnuckles-Genesis'); print('OK'); env.close()"
```

## Validation Strategy by Feature Type

| Feature | Validation Method | What to Check |
|---------|------------------|---------------|
| Parallax | Visual comparison | Distinct background layers, different scroll speeds at different camera positions, no seam glitches |
| Palette cycling | Visual comparison (time series) | Colors changing over time, correct hue range (e.g. water blue-green, lava red-orange), cycling speed |
| Animated tiles | Visual comparison (time series) | Art tiles updating (waterfalls, conveyors), correct frame count, animation speed |
| Events/camera | Headless test + visual | Camera locks at correct positions, boundary changes, screen shake |
| Events/boss | Visual comparison | Boss arena setup, floor/ceiling placement, camera lock boundaries |
| Act transitions | Headless test | Correct destination zone/act, camera position after transition |

## Process

### Step 1: Capture Reference Screenshots

Use stable-retro to boot the zone, wait for the title card to clear, then capture screenshots at key positions. Save to `tools/retro/validate_output/{zone}/reference/`.

Create and run this Python script (adjust zone/act state names as needed):

```python
#!/usr/bin/env python3
"""Capture S3K reference screenshots for zone validation."""

import os
import sys
import numpy as np

try:
    from PIL import Image
except ImportError:
    print("ERROR: Pillow not installed. Run: pip install Pillow")
    sys.exit(1)

import retro

# ── Configuration ──────────────────────────────────────────────────
ZONE = "$ZONE_ABBREV"          # e.g. "HCZ"
ACT = 1                        # 1 or 2
GAME = "SonicAndKnuckles-Genesis"

# S3K RAM addresses (68k memory, offset from 0xFF0000)
ADDR_CAMERA_X = 0xEE78         # Camera_X_pos (word)
ADDR_CAMERA_Y = 0xEE7C         # Camera_Y_pos (word)
ADDR_GAME_MODE = 0xF600        # Game_Mode
ADDR_ZONE = 0xEE4E             # Current_ZoneAndAct (byte = zone)
ADDR_ACT = 0xEE4F              # Current_ZoneAndAct + 1 (byte = act)
ADDR_TITLE_CARD = 0xEF8A       # Title card display counter

# Zone indices (matching S3K ROM)
ZONE_IDS = {
    "AIZ": 0x00, "HCZ": 0x01, "MGZ": 0x02, "CNZ": 0x03,
    "FBZ": 0x04, "ICZ": 0x05, "LBZ": 0x06, "MHZ": 0x07,
    "SOZ": 0x08, "LRZ": 0x09, "HPZ": 0x0A, "SSZ": 0x0B,
    "DEZ": 0x0C, "DDZ": 0x0D,
}

# Capture positions: (camera_x, camera_y, label)
# Adjust these per-zone to hit interesting parallax/event areas.
CAPTURE_POSITIONS = [
    (None, None, "level_start"),        # wherever the player spawns
    (0x0400, None, "mid_level"),        # ~1024px into the level
    (0x0800, None, "deep_level"),       # ~2048px into the level
]

# ── Helpers ────────────────────────────────────────────────────────
def read_word(ram, addr):
    """Read big-endian 16-bit word from 68k RAM."""
    return (ram[addr] << 8) | ram[addr + 1]

def save_frame(env, path):
    """Capture current frame and save as PNG."""
    obs = env.get_screen()
    img = Image.fromarray(obs)
    img.save(path)
    print(f"  Saved: {path} ({img.size[0]}x{img.size[1]})")

def wait_frames(env, n, action=None):
    """Advance n frames with optional action (default: no input)."""
    if action is None:
        action = env.action_space.sample() * 0  # zero action
    for _ in range(n):
        env.step(action)

# ── Main ───────────────────────────────────────────────────────────
def main():
    zone_id = ZONE_IDS.get(ZONE)
    if zone_id is None:
        print(f"Unknown zone: {ZONE}")
        sys.exit(1)

    out_dir = f"tools/retro/validate_output/{ZONE.lower()}{ACT}/reference"
    os.makedirs(out_dir, exist_ok=True)

    # Try to use a savestate if one exists for this zone
    state_name = f"{ZONE}{ACT}"
    try:
        env = retro.make(GAME, state=state_name)
        print(f"Loaded savestate: {state_name}")
    except Exception:
        # Fall back to default start and let the game reach the zone
        env = retro.make(GAME)
        print(f"No savestate '{state_name}' found -- using default start.")
        print("You may need to create a savestate for this zone first.")
        print("See: python -m retro.import --game SonicAndKnuckles-Genesis state.state")

    env.reset()

    # Wait for title card to clear (up to 300 frames = 5 seconds)
    print("Waiting for title card to clear...")
    for i in range(300):
        wait_frames(env, 1)
        ram = env.get_ram()
        # Title card counter at 0 or level fully loaded
        title_card = ram[ADDR_TITLE_CARD] if ADDR_TITLE_CARD < len(ram) else 0
        if i > 120 and title_card == 0:
            break

    # Extra settling frames for palette/animation init
    wait_frames(env, 30)

    # Capture at each position
    for cam_x, cam_y, label in CAPTURE_POSITIONS:
        if cam_x is not None:
            # Walk right to approximate the target camera position
            # (stable-retro does not allow direct RAM writes easily,
            #  so we simulate by holding right)
            ram = env.get_ram()
            current_x = read_word(ram, ADDR_CAMERA_X)
            target_x = cam_x

            if target_x > current_x:
                # Hold right until camera reaches target
                right_action = np.zeros(env.action_space.shape, dtype=np.int32)
                # Button mapping varies by core; typically: B=0, A=1, ..., RIGHT=7
                # For Genesis: UP=4, DOWN=5, LEFT=6, RIGHT=7
                right_action[7] = 1  # RIGHT
                max_walk_frames = 3000
                for _ in range(max_walk_frames):
                    env.step(right_action)
                    ram = env.get_ram()
                    cx = read_word(ram, ADDR_CAMERA_X)
                    if cx >= target_x:
                        break
                # Settle
                wait_frames(env, 10)

        # Capture main frame
        path = os.path.join(out_dir, f"{label}.png")
        save_frame(env, path)

        # For time-dependent features, capture a sequence
        if label == "level_start":
            for t in range(4):
                wait_frames(env, 15)
                path_t = os.path.join(out_dir, f"{label}_t{t}.png")
                save_frame(env, path_t)

    # Capture palette cycling sequence (8 frames over ~2 seconds)
    print("Capturing palette cycling sequence...")
    for i in range(8):
        wait_frames(env, 15)
        path = os.path.join(out_dir, f"palette_cycle_{i:02d}.png")
        save_frame(env, path)

    env.close()
    print(f"\nReference screenshots saved to {out_dir}/")
    print(f"Total files: {len(os.listdir(out_dir))}")

if __name__ == "__main__":
    main()
```

**Before running:** Edit the `ZONE` and `ACT` variables at the top. Adjust `CAPTURE_POSITIONS` based on the zone analysis spec -- target positions where parallax layers are most visible, where events trigger, and where animated tiles appear.

**Savestate creation:** If no savestate exists for the zone, create one:
1. Run `python -m retro.interactive --game SonicAndKnuckles-Genesis`
2. Navigate to the desired zone/act using level select (hold A on title screen)
3. Once gameplay starts, save state
4. Import the `.state` file

### Step 2: Capture Engine Screenshots

Run the engine for the same zone and capture at equivalent camera positions. The engine has built-in screenshot support via `ScreenshotCapture`.

**Option A: Manual capture** -- Run the engine, navigate to the zone, use debug mode (D key) to position the camera, and press the screenshot key:

```bash
java -jar target/sonic-engine-0.4.prerelease-jar-with-dependencies.jar
```

Save screenshots to `tools/retro/validate_output/{zone}/engine/`.

**Option B: Headless capture** -- If the zone supports headless testing, use `HeadlessTestRunner` to position the camera and capture via `ScreenshotCapture`:

```java
// Example: capture at specific camera positions
HeadlessTestRunner runner = new HeadlessTestRunner(sprite);
runner.stepIdleFrames(120); // wait for title card
Camera camera = Camera.getInstance();
camera.setX(0x0400);
camera.updatePosition(true);
// ... capture via ScreenshotCapture or BufferedImage export
```

Note: Headless mode has no OpenGL context, so full visual captures require running the engine with a window. Use headless tests for behavioural validation (camera positions, boundaries) and windowed mode for visual validation.

**Naming convention:** Match the reference filenames exactly:
```
tools/retro/validate_output/{zone}{act}/reference/level_start.png
tools/retro/validate_output/{zone}{act}/reference/mid_level.png
tools/retro/validate_output/{zone}{act}/engine/level_start.png
tools/retro/validate_output/{zone}{act}/engine/mid_level.png
```

### Step 3: Compare Using Image Recognition

Present both the reference and engine screenshots to the agent for visual analysis. For each feature, ask the specific check questions below.

**How to present images:** Use the Read tool to view each PNG file. Show pairs side by side (reference then engine) for the same camera position.

#### Parallax Checks

For each capture position, compare reference vs engine:

1. **Layer count:** Are the same number of distinct background layers visible? (e.g., distant mountains, mid-ground hills, near-ground details)
2. **Scroll differentiation:** At different camera positions, do layers move at visibly different rates? Compare the relative positions of background elements between `level_start.png` and `mid_level.png`.
3. **Seam/glitch check:** Are there any visible horizontal seams, tearing, or repeated tile artifacts in the engine background that are not present in the reference?
4. **Vertical scroll:** Does the background shift vertically when the camera moves down? Compare vertical positions of background elements.
5. **Water split (if applicable):** If the zone has water, does the background change at the water line? Is the underwater portion visually distinct?

#### Palette Cycling Checks

Compare the `palette_cycle_00.png` through `palette_cycle_07.png` series:

1. **Colour change:** Do colours visibly change between frames in the engine sequence? Identify which elements cycle (water, lava, lights, etc.).
2. **Hue range:** Does the engine cycle through the same approximate colour range as the reference? (e.g., water should cycle through blues/teals, not reds)
3. **Cycling speed:** Does the engine appear to cycle at roughly the same rate? (same number of distinct colour states across 8 captures)
4. **Affected area:** Is the cycling applied to the correct screen regions? (e.g., only water tiles, not the entire background)

#### Animated Tile Checks

Compare the `level_start_t0.png` through `level_start_t3.png` time series:

1. **Animation presence:** Do tile graphics change between frames in the engine? (waterfalls flowing, conveyors moving, flowers swaying)
2. **Frame count:** Does the engine show a similar number of distinct animation frames as the reference?
3. **Animation speed:** Do tiles appear to update at roughly the same rate?
4. **Tile location:** Are the animated tiles in the correct positions relative to static level geometry?

#### Event/Camera Checks

For zones with camera events (boss arenas, dynamic boundaries):

1. **Camera lock:** Does the camera stop scrolling at the correct horizontal/vertical position?
2. **Arena boundaries:** Is the locked playfield the correct width/height compared to reference?
3. **Triggered objects:** Do any objects (walls, platforms, boss) appear when the event triggers?
4. **Post-event resume:** After the event completes, does scrolling resume normally?

### Step 4: Report Results

After comparing all features, produce a validation report using this template:

```
## S3K Zone Validation Report: {ZONE} Act {ACT}

Date: {date}
Engine build: {commit hash}
Reference: stable-retro SonicAndKnuckles-Genesis

### Feature Results

| Feature | Status | Notes |
|---------|--------|-------|
| Parallax - layer count | PASS/LIKELY/FAIL/SKIP | {detail} |
| Parallax - scroll rates | PASS/LIKELY/FAIL/SKIP | {detail} |
| Parallax - no glitches | PASS/LIKELY/FAIL/SKIP | {detail} |
| Palette cycling - presence | PASS/LIKELY/FAIL/SKIP | {detail} |
| Palette cycling - hue range | PASS/LIKELY/FAIL/SKIP | {detail} |
| Palette cycling - speed | PASS/LIKELY/FAIL/SKIP | {detail} |
| Animated tiles - presence | PASS/LIKELY/FAIL/SKIP | {detail} |
| Animated tiles - speed | PASS/LIKELY/FAIL/SKIP | {detail} |
| Events - camera locks | PASS/LIKELY/FAIL/SKIP | {detail} |
| Events - boss arena | PASS/LIKELY/FAIL/SKIP | {detail} |
| Act transition | PASS/LIKELY/FAIL/SKIP | {detail} |

### Summary

{X} PASS / {Y} LIKELY / {Z} FAIL / {W} SKIP

### Issues Found

- {description of any FAIL items with details}

### Recommendations

- {next steps to fix FAIL items}
```

Save the report to `docs/s3k-zones/{zone}-validation.md`.

## Confidence Levels

| Level | Meaning | When to Use |
|-------|---------|-------------|
| **PASS** | Feature confirmed present and correct | Visual comparison shows clear match; headless test passes with correct values |
| **LIKELY** | Feature appears present but cannot fully confirm | Layers visible but scroll rates hard to judge; colours cycling but exact palette unclear |
| **FAIL** | Feature missing, wrong, or significantly different | Background is flat when reference shows parallax; colours not cycling; camera does not lock |
| **SKIP** | Feature not applicable or not testable | Zone has no palette cycling; boss arena not yet implemented; no savestate available |

Use **LIKELY** liberally -- visual comparison inherently has uncertainty. Reserve **PASS** for cases where the match is unambiguous. Use **FAIL** only when there is a clear, visible discrepancy.

## Headless Test Validation

For behavioural features with deterministic outcomes (camera locks, boundary changes, act transitions), write Java headless tests that verify exact values. These complement visual comparison with precise numeric checks.

### Camera Lock Verification Template

```java
@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestS3k{Zone}{Act}CameraLock {

    @Test
    void cameraLocksAtBossArena() {
        // Boot S3K zone
        HeadlessTestRunner runner = HeadlessTestRunner.bootS3kZone(
            ZONE_INDEX, ACT_INDEX);

        // Advance to near the boss trigger position
        Camera camera = Camera.getInstance();
        AbstractLevelEventManager events =
            GameModuleRegistry.getCurrent()
                .getLevelEventProvider().createEventManager();

        // Walk right until camera reaches trigger threshold
        while (camera.getX() < BOSS_TRIGGER_X - 32) {
            runner.stepFrame(false, false, false, true, false); // hold right
        }

        // Step a few more frames to trigger the event
        runner.stepIdleFrames(10);

        // Verify camera lock
        int lockedX = camera.getX();
        int lockedMinX = camera.getMinX();
        int lockedMaxX = camera.getMaxX();

        assertEquals(EXPECTED_LOCK_MIN_X, lockedMinX,
            "Camera left boundary after boss trigger");
        assertEquals(EXPECTED_LOCK_MAX_X, lockedMaxX,
            "Camera right boundary after boss trigger");

        // Verify the camera stays locked for additional frames
        runner.stepIdleFrames(30);
        assertEquals(lockedX, camera.getX(),
            "Camera X should not change while locked");
    }
}
```

### Boundary Change Verification Template

```java
@Test
void dynamicBoundaryExpandsAfterEvent() {
    HeadlessTestRunner runner = HeadlessTestRunner.bootS3kZone(
        ZONE_INDEX, ACT_INDEX);

    // Record initial boundaries
    Camera camera = Camera.getInstance();
    int initialMaxY = camera.getMaxY();

    // Trigger the event (e.g., walk past a threshold)
    while (camera.getX() < EVENT_TRIGGER_X) {
        runner.stepFrame(false, false, false, true, false);
    }
    runner.stepIdleFrames(5);

    // Verify boundary changed
    int newMaxY = camera.getMaxY();
    assertTrue(newMaxY > initialMaxY,
        "Bottom boundary should expand after event trigger: was "
        + initialMaxY + ", now " + newMaxY);
    assertEquals(EXPECTED_NEW_MAX_Y, newMaxY,
        "New bottom boundary should match disassembly value");
}
```

## Common Mistakes

1. **Wrong camera positions.** The reference and engine screenshots must show approximately the same part of the level. If the camera X/Y differs significantly, parallax layers will be in different positions and the comparison is meaningless. Verify camera coordinates in both captures before comparing.

2. **Expecting pixel-perfect match.** The engine uses OpenGL shaders for tilemap rendering while the original uses VDP hardware. Slight sub-pixel differences, interpolation artifacts, and timing differences are normal. Focus on feature presence, not exact pixel values.

3. **Missing time-dependent features.** Palette cycling and animated tiles only show up over time. A single frame capture will not reveal whether cycling is working. Always capture a time series (multiple frames with delays between them).

4. **Forgetting to build.** Run `mvn package` before capturing engine screenshots. Stale JARs will show old behaviour and produce false negatives.

5. **Title card obscuring captures.** Both the reference and engine need time for the title card to clear. Wait at least 120 frames (~2 seconds) before capturing. The Python script handles this automatically; for engine captures, wait for the title card animation to complete.

6. **Savestate mismatch.** If the stable-retro savestate was created with a different ROM revision or in a different emulator, RAM layouts may differ and the zone may not load correctly. Always verify the zone loaded correctly by checking the first capture visually.

7. **Comparing act 1 reference against act 2 engine (or vice versa).** Many zones have different parallax, palette, and event configurations per act. Always verify the act number matches.

8. **Ignoring foreground differences.** Object placement and player position will differ between reference and engine. Focus comparison on the **background layers, colour cycling, and camera behaviour** -- not on Sonic's position or ring layout.
