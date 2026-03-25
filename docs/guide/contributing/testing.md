# Testing

This page covers the engine's test infrastructure and how to write tests for new features.

## Running Tests

```bash
# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=TestCollisionLogic

# Run a single test method
mvn test -Dtest=TestCollisionLogic#testSlopeAngle

# Run with full Maven output (disable silent extension)
mvn test -Dmse=off
```

Tests are configured for parallel execution across 8 JVM forks. This significantly speeds
up the full test suite but means tests must be independent of each other.

## ROM-Dependent Tests

Many tests require ROM files to load level data, object art, or audio. These tests
**skip gracefully** when ROMs are absent, so CI and contributors without ROMs can still
run the rest of the suite.

The convention is to check for ROM availability using `RomTestUtils`:

```java
import static com.openggf.tests.RomTestUtils.ensureRomAvailable;

@Test
void testEHZCollision() {
    File romFile = ensureRomAvailable();
    if (romFile == null) {
        return;  // Skip: ROM not present
    }
    // ... test logic
}
```

For S3K tests, the ROM path can also be specified via system property:
```bash
mvn test -Ds3k.rom.path="Sonic and Knuckles & Sonic 3 (W) [!].gen"
```

## HeadlessTestFixture

The `HeadlessTestFixture` provides a builder-pattern API for setting up a test level
without a GPU window. It initializes the game module, loads level data, and provides
frame-stepping controls.

### Basic Usage

```java
@Test
void testPlayerLandsOnGround() {
    HeadlessTestFixture fixture = HeadlessTestFixture.builder()
            .withZoneAndAct(ZONE_EHZ, 0)
            .startPosition((short) 0x100, (short) 0x200)
            .build();

    // Step 60 frames (1 second at 60fps)
    fixture.stepIdleFrames(60);

    // Assert the player landed on the ground
    AbstractPlayableSprite player = fixture.sprite();
    assertTrue(player.isOnGround());
    assertTrue(player.getY() > 0x200);  // Fell from starting position
}
```

### Setting Up Specific Scenarios

```java
// Set up fixture with specific start position
HeadlessTestFixture fixture = HeadlessTestFixture.builder()
        .withZoneAndAct(ZONE_HTZ, act)
        .startPosition((short) 0x500, (short) 0x300)
        .build();

// Snap camera to player
fixture.camera().setX(0x500 - 160);
fixture.camera().setY(0x300 - 112);
fixture.camera().updatePosition(true);  // Snap (no smooth scroll)

// Step frames with per-frame inspection
for (int i = 0; i < 120; i++) {
    fixture.stepFrame(false, false, false, false, false);  // no input
    if (fixture.sprite().getX() >= targetX) {
        break;
    }
}
```

### What HeadlessTestFixture Does

The fixture calls the same update sequence as the real game loop:
1. `Camera.updatePosition()` -- Update camera before level events.
2. `LevelEventManager.update()` -- Process dynamic boundaries, zone-specific triggers.
3. `ParallaxManager.update()` -- Calculate scroll offsets.
4. Object `update()` calls for all active objects.

This means level events, camera boundaries, and object interactions all work in tests
the same way they do in the real game.

## Test Organisation

Tests are grouped by the systems they exercise:

```
src/test/java/com/openggf/
  physics/         -- Physics and collision tests
  level/           -- Level loading, object spawning tests
  audio/           -- Audio regression tests
  game/
    sonic1/        -- S1-specific tests
    sonic2/        -- S2-specific tests
    sonic3k/       -- S3K-specific tests
```

### Naming Conventions

- `Test<Feature>.java` -- Unit tests for a specific feature.
- `Test<Zone><Feature>.java` -- Tests for zone-specific behavior (e.g., `TestHTZEarthquake`).
- `Test<Object>Instance.java` -- Tests for a specific object type.

## Physics Integration Tests

Physics tests verify that the engine produces the same player positions and velocities as
the original game for specific scenarios.

### Pattern: Step-Frame Position Assertion

```java
@Test
void testRollingDownSlope() {
    HeadlessTestFixture fixture = HeadlessTestFixture.builder()
            .withZoneAndAct(ZONE_EHZ, 0)
            .startPosition((short) 0x800, (short) 0x340)
            .build();

    // Set initial speed
    fixture.sprite().setGroundSpeed(0x200);

    fixture.stepIdleFrames(30);

    // Player should have accelerated down the slope
    int speed = fixture.sprite().getGroundSpeed();
    assertTrue(speed > 0x200, "Player should accelerate on downhill slope");
}
```

### Pattern: Collision Edge Case

```java
@Test
void testWallCollisionAtSpeed() {
    HeadlessTestFixture fixture = HeadlessTestFixture.builder()
            .withZoneAndAct(ZONE_EHZ, 0)
            .startPosition((short) (wallX - 100), (short) wallY)
            .build();

    fixture.sprite().setGroundSpeed(0xC00);  // 12 pixels/frame

    fixture.stepIdleFrames(30);

    // Player should stop at the wall, not pass through
    assertTrue(fixture.sprite().getX() <= wallX);
    assertEquals(0, fixture.sprite().getGroundSpeed());
}
```

## Visual Regression Tests

Visual regression tests capture rendered frames and compare them against reference images.
They verify that changes to the rendering pipeline or art loading do not introduce visual
regressions.

These tests require ROM files and a GPU context. They typically:
1. Load a level and set the camera to a known position.
2. Render one or more frames.
3. Compare the rendered output against a stored reference image.
4. Fail if the pixel difference exceeds a threshold.

Visual regression tests are slower and more environment-sensitive than headless tests.
They are primarily used for validating art loading, palette, and rendering pipeline changes.

## Audio Regression Tests

Audio tests verify that the SMPS sequencer produces correct output for specific songs.
The approach:

1. Load a music track from the ROM.
2. Run the sequencer for N frames.
3. Capture the register writes or audio samples.
4. Compare against expected output (from SMPSPlay or a known-good capture).

These tests catch regressions in tempo calculation, note mapping, modulation, and channel
state management.

## Tips

- **Keep tests independent.** Tests run in parallel across multiple JVM forks. Do not
  depend on state from another test.
- **Skip gracefully without ROMs.** Always check `RomTestUtils.ensureRomAvailable()` before
  loading ROM data. Never let a test fail just because a ROM is absent.
- **Use constants from the disassembly.** When asserting positions or velocities, use the
  same hex values that appear in the ASM. This makes it easier to trace failures back to
  the source.
- **Test behavior, not implementation.** Assert that the player lands on the ground, not
  that a specific internal method was called.

## Next Steps

- [Dev Setup](dev-setup.md) -- Build and run configuration
- [Tutorial: Implement an Object](tutorial-implement-object.md) -- Testing is step 7
