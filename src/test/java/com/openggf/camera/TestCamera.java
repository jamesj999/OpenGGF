package com.openggf.camera;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.openggf.sprites.Sprite;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.GameServices;
import com.openggf.tests.TestEnvironment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the Camera class, covering player following logic,
 * boundary constraints, and position calculations.
 */
public class TestCamera {

    private Camera camera;
    private AbstractPlayableSprite mockSprite;

    @BeforeEach
    public void setUp() throws Exception {
        // Reset all singletons for test isolation
        TestEnvironment.resetAll();
        camera = GameServices.camera();

        // Create a mock sprite for testing
        mockSprite = mock(AbstractPlayableSprite.class);
        when(mockSprite.getCentreX()).thenReturn((short) 160);
        when(mockSprite.getCentreY()).thenReturn((short) 112);
        when(mockSprite.getCentreX(anyInt())).thenReturn((short) 160);
        when(mockSprite.getCentreY(anyInt())).thenReturn((short) 112);
        when(mockSprite.getX()).thenReturn((short) 160);
        when(mockSprite.getY()).thenReturn((short) 112);
        when(mockSprite.getAir()).thenReturn(false);
        when(mockSprite.getYSpeed()).thenReturn((short) 0);
        when(mockSprite.getGSpeed()).thenReturn((short) 0);

        camera.setFocusedSprite(mockSprite);

        // Set up level bounds
        camera.setMinX((short) 0);
        camera.setMinY((short) 0);
        camera.setMaxX((short) 6000);
        camera.setMaxY((short) 1000);
    }

    // ==================== Basic Position Tests ====================

    @Test
    public void testSetFocusedSpriteInitializesPosition() {
        AbstractPlayableSprite newSprite = mock(AbstractPlayableSprite.class);
        when(newSprite.getX()).thenReturn((short) 500);
        when(newSprite.getY()).thenReturn((short) 300);

        camera.setFocusedSprite(newSprite);

        assertEquals(500, camera.getX(), "Camera X should match sprite X");
        assertEquals(300, camera.getY(), "Camera Y should match sprite Y");
    }

    @Test
    public void testForceUpdateCentersPlayerOnScreen() {
        // Force update should position camera so player appears at screen position (152, 96)
        when(mockSprite.getCentreX()).thenReturn((short) 1000);
        when(mockSprite.getCentreY()).thenReturn((short) 500);

        camera.updatePosition(true);

        // Camera position = sprite centre - screen offset
        // Screen offset for "look at" point is (152, 96)
        assertEquals(1000 - 152, camera.getX(), "Force update should center player horizontally");
        assertEquals(500 - 96, camera.getY(), "Force update should center player vertically");
    }

    // ==================== Horizontal Following Tests ====================

    @Test
    public void testCameraFollowsPlayerMovingRight() {
        // Position player to the right of camera center threshold (160)
        camera.setX((short) 0);
        when(mockSprite.getCentreX()).thenReturn((short) 200); // 200 - 0 = 200, which is > 160 threshold

        camera.updatePosition();

        assertTrue(camera.getX() > 0, "Camera should move right when player exceeds right threshold");
    }

    @Test
    public void testCameraFollowsPlayerMovingLeft() {
        // Position camera ahead of player
        camera.setX((short) 200);
        when(mockSprite.getCentreX()).thenReturn((short) 300); // 300 - 200 = 100, which is < 144 threshold

        camera.updatePosition();

        assertTrue(camera.getX() < 200, "Camera should move left when player is below left threshold");
    }

    @Test
    public void testCameraDoesNotMoveWhenPlayerInDeadzone() {
        // Player is within the 144-160 horizontal deadzone
        camera.setX((short) 0);
        when(mockSprite.getCentreX()).thenReturn((short) 150); // 150 - 0 = 150, which is between 144 and 160

        short initialX = camera.getX();
        camera.updatePosition();

        assertEquals(initialX, camera.getX(), "Camera should not move horizontally when player is in deadzone");
    }

    @Test
    public void testCameraMaxHorizontalSpeed() {
        // Player is far to the right - camera should cap movement at 16px/frame
        camera.setX((short) 0);
        when(mockSprite.getCentreX()).thenReturn((short) 500); // Very far right

        camera.updatePosition();

        assertEquals(16, camera.getX(), "Camera horizontal movement should be capped at 16px");
    }

    // ==================== Vertical Following Tests ====================

    @Test
    public void testCameraFollowsPlayerInAir() {
        when(mockSprite.getAir()).thenReturn(true);
        camera.setY((short) 0);
        when(mockSprite.getCentreY()).thenReturn((short) 200); // > 160 threshold for air

        camera.updatePosition();

        assertTrue(camera.getY() > 0, "Camera should follow player vertically when in air");
    }

    @Test
    public void testCameraFollowsPlayerOnGround() {
        when(mockSprite.getAir()).thenReturn(false);
        when(mockSprite.getYSpeed()).thenReturn((short) 0);
        camera.setY((short) 0);
        when(mockSprite.getCentreY()).thenReturn((short) 200); // > 96 threshold for ground

        camera.updatePosition();

        assertTrue(camera.getY() > 0, "Camera should follow player vertically when on ground");
    }

    @Test
    public void testCameraVerticalSpeedIncreasesWithHighInertia() {
        // ROM: s2.asm:18166-18168 - uses inertia (gSpeed), not ySpeed
        // When inertia >= 0x800 (2048), use 16px cap instead of 6px
        when(mockSprite.getAir()).thenReturn(false);
        when(mockSprite.getGSpeed()).thenReturn((short) 0x800); // Inertia threshold for fast scroll
        camera.setY((short) 0);
        when(mockSprite.getCentreY()).thenReturn((short) 200);

        camera.updatePosition();

        // With high inertia (>= 0x800), tolerance is 16 instead of 6
        assertTrue(camera.getY() >= 16, "Camera should move faster vertically when player has high ground speed");
    }

    @Test
    public void testCameraSlowScrollWhenBiasNotDefault() {
        // ROM: s2.asm:18164-18184 - when bias != 96, use 2px cap (slow scroll)
        when(mockSprite.getAir()).thenReturn(false);
        when(mockSprite.getGSpeed()).thenReturn((short) 0x1000); // High inertia (would be 16px normally)
        camera.setY((short) 0);
        camera.setYPosBias((short) 0); // Bias not default (looking up)
        when(mockSprite.getCentreY()).thenReturn((short) 200);

        camera.updatePosition();

        // With non-default bias, tolerance is capped at 2px regardless of inertia
        assertEquals(2, camera.getY(), "Camera should use slow scroll (2px) when bias is not default");
    }

    @Test
    public void testCameraMediumScrollWithLowInertia() {
        // ROM: s2.asm:18169-18175 - when bias == 96 and inertia < 0x800, use 6px cap
        when(mockSprite.getAir()).thenReturn(false);
        when(mockSprite.getGSpeed()).thenReturn((short) 0x400); // Low inertia (< 0x800)
        camera.setY((short) 0);
        when(mockSprite.getCentreY()).thenReturn((short) 200);

        camera.updatePosition();

        // With low inertia and default bias, tolerance is 6px
        assertEquals(6, camera.getY(), "Camera should use medium scroll (6px) when inertia is low");
    }

    // ==================== Boundary Tests ====================

    @Test
    public void testCameraCannotGoBelowZeroX() {
        camera.setX((short) 10);
        when(mockSprite.getCentreX()).thenReturn((short) 0); // Try to push camera left

        camera.updatePosition();

        assertTrue(camera.getX() >= 0, "Camera X should not go below 0");
    }

    @Test
    public void testCameraCannotGoBelowZeroY() {
        camera.setY((short) 10);
        when(mockSprite.getCentreY()).thenReturn((short) 0);

        camera.updatePosition();

        assertTrue(camera.getY() >= 0, "Camera Y should not go below 0");
    }

    @Test
    public void testCameraCannotExceedMaxX() {
        camera.setMaxX((short) 1000);
        camera.setX((short) 990);
        when(mockSprite.getCentreX()).thenReturn((short) 2000); // Try to push camera beyond max

        camera.updatePosition();

        assertTrue(camera.getX() <= 1000, "Camera X should not exceed maxX");
    }

    @Test
    public void testWrappedHorizontalBoundsDoNotForceBackwardClamp() {
        camera.setMinX((short) 146);
        camera.setMaxX((short) 106); // Wrapped range (ObjB2 SCZ writes Camera_X - $40)
        when(mockSprite.getCentreX()).thenReturn((short) 304); // Forced target X = 152
        when(mockSprite.getCentreY()).thenReturn((short) 200);

        camera.updatePosition(true);

        assertEquals(152, camera.getX(), "Wrapped bounds should not clamp to max when max < min");
    }

    @Test
    public void testCameraCannotExceedMaxY() {
        camera.setMaxY((short) 500);
        camera.setY((short) 490);
        when(mockSprite.getCentreY()).thenReturn((short) 1000);

        camera.updatePosition();

        assertTrue(camera.getY() <= 500, "Camera Y should not exceed maxY");
    }

    // ==================== Freeze/Unfreeze Tests (Spindash) ====================

    @Test
    public void testFrozenCameraDoesNotFollowPlayer() {
        camera.setFrozen(true);
        short initialX = camera.getX();
        short initialY = camera.getY();

        when(mockSprite.getCentreX()).thenReturn((short) 1000);
        when(mockSprite.getCentreY()).thenReturn((short) 500);
        camera.updatePosition();

        assertEquals(initialX, camera.getX(), "Frozen camera should not move X");
        assertEquals(initialY, camera.getY(), "Frozen camera should not move Y");
    }

    @Test
    public void testUnfreezeAllowsCameraToFollow() {
        camera.setFrozen(true);
        camera.updatePosition();
        camera.setFrozen(false);

        when(mockSprite.getCentreX()).thenReturn((short) 500);
        camera.updatePosition();

        // Camera should now be able to move
        assertFalse(camera.getFrozen(), "Camera should not be frozen after setFrozen(false)");
    }

    @Test
    public void testFreezeStateAccessors() {
        assertFalse(camera.getFrozen(), "Camera should not be frozen initially");

        camera.setFrozen(true);
        assertTrue(camera.getFrozen(), "Camera should be frozen after setFrozen(true)");

        camera.setFrozen(false);
        assertFalse(camera.getFrozen(), "Camera should not be frozen after setFrozen(false)");
    }

    // ==================== isOnScreen Tests ====================

    @Test
    public void testSpriteOnScreenReturnsTrueWhenVisible() {
        camera.setX((short) 0);
        camera.setY((short) 0);

        Sprite visibleSprite = mock(Sprite.class);
        when(visibleSprite.getX()).thenReturn((short) 100);
        when(visibleSprite.getY()).thenReturn((short) 100);

        assertTrue(camera.isOnScreen(visibleSprite), "Sprite within camera bounds should be on screen");
    }

    @Test
    public void testSpriteOffScreenReturnsFalseWhenLeftOfCamera() {
        camera.setX((short) 500);
        camera.setY((short) 0);

        Sprite offscreenSprite = mock(Sprite.class);
        when(offscreenSprite.getX()).thenReturn((short) 100);
        when(offscreenSprite.getY()).thenReturn((short) 100);

        assertFalse(camera.isOnScreen(offscreenSprite), "Sprite left of camera should not be on screen");
    }

    @Test
    public void testSpriteOffScreenReturnsFalseWhenRightOfCamera() {
        camera.setX((short) 0);
        camera.setY((short) 0);

        // Camera width is typically 320, so sprite at 400 should be off screen
        Sprite offscreenSprite = mock(Sprite.class);
        when(offscreenSprite.getX()).thenReturn((short) 400);
        when(offscreenSprite.getY()).thenReturn((short) 100);

        // This depends on screen width - sprite at 400 with camera at 0 and width 320 is off screen
        // The isOnScreen check is: spriteX <= x + width, so 400 <= 0 + 320 is false
        assertFalse(camera.isOnScreen(offscreenSprite), "Sprite right of camera should not be on screen");
    }

    // ==================== Increment Tests ====================

    @Test
    public void testIncrementX() {
        camera.setX((short) 100);
        camera.incrementX((short) 50);
        assertEquals(150, camera.getX(), "incrementX should add to current X");

        camera.incrementX((short) -30);
        assertEquals(120, camera.getX(), "incrementX should subtract when negative");
    }

    @Test
    public void testIncrementY() {
        camera.setY((short) 100);
        camera.incrementY((short) 50);
        assertEquals(150, camera.getY(), "incrementY should add to current Y");

        camera.incrementY((short) -30);
        assertEquals(120, camera.getY(), "incrementY should subtract when negative");
    }
}


