package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link HeadlessTestFixture} builder.
 * <p>
 * These tests verify that the fixture correctly sets up a playable sprite,
 * camera, and headless runner, and that separate fixture instances do not
 * interfere with each other.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestHeadlessTestFixture {

    @ClassRule
    public static RequiresRomRule romRule = new RequiresRomRule();

    private static SharedLevel shared;

    @BeforeClass
    public static void loadLevel() throws Exception {
        shared = SharedLevel.load(SonicGame.SONIC_2, 0, 0); // EHZ Act 1
    }

    @AfterClass
    public static void cleanup() {
        if (shared != null) shared.dispose();
    }

    @Test
    public void testFixtureCreatesSprite() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withSharedLevel(shared)
                .startPosition((short) 96, (short) 655)
                .build();

        AbstractPlayableSprite sprite = fixture.sprite();
        assertNotNull("Fixture should create a non-null sprite", sprite);
        assertEquals("Sprite X should match start position", 96, sprite.getX());
        assertEquals("Sprite Y should match start position", 655, sprite.getY());
    }

    @Test
    public void testFixtureSetsUpCamera() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withSharedLevel(shared)
                .startPosition((short) 96, (short) 655)
                .build();

        Camera camera = fixture.camera();
        assertNotNull("Fixture should provide a non-null camera", camera);
        assertFalse("Camera should not be frozen after fixture build", camera.getFrozen());
        assertTrue("Camera maxX should be non-zero after fixture build", camera.getMaxX() > 0);
    }

    @Test
    public void testFixtureCanStepFrames() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withSharedLevel(shared)
                .startPosition((short) 96, (short) 655)
                .build();

        short initialX = fixture.sprite().getX();

        // Hold right for 10 frames
        for (int i = 0; i < 10; i++) {
            fixture.stepFrame(false, false, false, true, false);
        }

        short finalX = fixture.sprite().getX();
        assertTrue("Sprite should have moved right after 10 frames. "
                + "Initial=" + initialX + ", Final=" + finalX,
                finalX > initialX);
        assertEquals("Frame counter should be 10", 10, fixture.frameCount());
    }

    @Test
    public void testTwoFixturesDoNotInterfere() {
        // Note: building fixture2 calls resetPerTest(), which clears SpriteManager.
        // After fixture2 is built, fixture1 is no longer usable. Each fixture
        // "owns" the singleton state between its build() and the next build().

        // Use the same known-good EHZ1 spawn point so the sprite is grounded
        HeadlessTestFixture fixture1 = HeadlessTestFixture.builder()
                .withSharedLevel(shared)
                .startPosition((short) 96, (short) 655)
                .build();

        // Step fixture1 so it moves right
        for (int i = 0; i < 10; i++) {
            fixture1.stepFrame(false, false, false, true, false);
        }
        short fixture1X = fixture1.sprite().getX();
        assertTrue("Fixture1 should have moved from start", fixture1X > 96);

        // Create a second fixture at the same start position
        HeadlessTestFixture fixture2 = HeadlessTestFixture.builder()
                .withSharedLevel(shared)
                .startPosition((short) 96, (short) 655)
                .build();

        // Fixture2 should have its own sprite at the original position,
        // unaffected by fixture1's movement
        assertEquals("Fixture2 sprite should be at its own start X",
                96, fixture2.sprite().getX());
        assertEquals("Fixture2 sprite should be at its own start Y",
                655, fixture2.sprite().getY());
        assertEquals("Fixture2 frame counter should be 0", 0, fixture2.frameCount());

        // Fixture2's sprite is a different object than fixture1's
        assertNotSame("Fixtures should have different sprite instances",
                fixture1.sprite(), fixture2.sprite());
    }
}
