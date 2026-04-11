package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameStateManager;
import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that the S3K big ring (Obj_SSEntryRing) is NOT interactable
 * during its initial growing animation, matching the ROM's behaviour.
 * <p>
 * ROM reference: sonic3k.asm lines 128257-128262
 * <pre>
 * SSEntryRing_Main:
 *     jsr (Animate_Raw).l
 *     ...
 *     cmpi.b #8,mapping_frame(a0)
 *     blo.s  locret_61708        ; mapping_frame &lt; 8 â†’ no collision
 * </pre>
 * <p>
 * Formation animation: delay=4, 9 frame bytes {0,0,1,2,3,4,5,6,7}.
 * ROM's Animate_Raw starts with timer=0, so the first call immediately
 * advances (reads the 2nd byte). This gives 8 advances Ã— (delay+1)=5
 * frames each = <b>40 game frames</b> of formation (mapping_frame 0-7).
 * <p>
 * On frame 41 the 9th advance exceeds the array â†’ transition to idle,
 * mapping_frame becomes 10 (first idle frame), collision is enabled.
 */
public class TestSonic3kSSEntryRingFormation {

    /** Ring placed at screen centre â€” well within default camera bounds. */
    private static final int RING_X = 160;
    private static final int RING_Y = 112;

    /** ROM: formation delay=4, Animate_Raw gives delay+1 = 5 frames per anim step. */
    private static final int FRAMES_PER_ANIM_STEP = 5;

    /**
     * ROM: 8 advances before the $F8 command (reads frame bytes 2-9, i.e.
     * FORMATION_FRAMES[1] through FORMATION_FRAMES[8]).
     * The first byte (FORMATION_FRAMES[0]) is the initial frame set before
     * Animate_Raw first runs.
     */
    private static final int FORMATION_ADVANCE_COUNT = 8;

    /** Total formation duration in game frames: 8 advances Ã— 5 frames each = 40. */
    private static final int FORMATION_TOTAL_FRAMES = FORMATION_ADVANCE_COUNT * FRAMES_PER_ANIM_STEP; // 40

    private GameStateManager gameState;
    private ObjectServices services;

    @BeforeEach
    public void setUp() {
        TestEnvironment.resetAll();
        gameState = new GameStateManager();
        gameState.resetSession();
        services = new TestObjectServices().withGameState(gameState);

        // Ensure camera bounds include the ring position (default is 0,0,320,224)
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
    }

    @AfterEach
    public void tearDown() {
        clearConstructionContext();
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    public void ringStartsInFormingState() {
        Sonic3kSSEntryRingObjectInstance ring = createRing(0);
        assertTrue(ring.isForming(), "Ring should start in forming state");
        assertTrue(ring.isMainState(), "Ring should be in MAIN state");
        assertEquals(0, ring.getMappingFrame(), "Initial mapping frame should be 0");
    }

    @Test
    public void ringIsNotInteractableDuringEntireFormation() {
        Sonic3kSSEntryRingObjectInstance ring = createRing(0);

        // Step through every frame of the formation animation.
        // The ring must remain in forming state (mapping_frame < 8) throughout.
        for (int frame = 1; frame <= FORMATION_TOTAL_FRAMES; frame++) {
            ring.update(frame, null);
            assertTrue(ring.isForming(), "Ring should be forming at frame " + frame
                            + " (mapping_frame=" + ring.getMappingFrame() + ")");
            assertTrue(ring.getMappingFrame() < 8, "mapping_frame should be < 8 during formation at frame " + frame);
        }
    }

    @Test
    public void ringBecomesInteractableAfterFormation() {
        Sonic3kSSEntryRingObjectInstance ring = createRing(0);

        // Advance through entire formation
        for (int frame = 1; frame <= FORMATION_TOTAL_FRAMES; frame++) {
            ring.update(frame, null);
        }

        // One more frame should transition to idle (mapping_frame >= 8)
        ring.update(FORMATION_TOTAL_FRAMES + 1, null);
        assertFalse(ring.isForming(), "Ring should no longer be forming after formation completes");
        assertTrue(ring.getMappingFrame() >= 8, "mapping_frame should be >= 8 in idle");
    }

    @Test
    public void formationAnimationTimingMatchesRom() {
        Sonic3kSSEntryRingObjectInstance ring = createRing(0);

        // ROM anim 0: delay=4, frame bytes = {0, 0, 1, 2, 3, 4, 5, 6, 7}
        // First byte is the initial frame; Animate_Raw reads bytes 2-9 on advances.
        // Each advance produces a mapping frame displayed for delay+1 = 5 game frames.
        // Expected mapping frames after each 5-frame group:
        int[] expectedFrames = {0, 1, 2, 3, 4, 5, 6, 7};

        int gameFrame = 0;
        for (int step = 0; step < expectedFrames.length; step++) {
            for (int tick = 0; tick < FRAMES_PER_ANIM_STEP; tick++) {
                gameFrame++;
                ring.update(gameFrame, null);
            }
            assertEquals(expectedFrames[step], ring.getMappingFrame(), "Mapping frame after step " + step + " (game frame " + gameFrame + ")");
        }
        // Verify we've consumed exactly the formation duration
        assertEquals(FORMATION_TOTAL_FRAMES, gameFrame, "Should have consumed exactly FORMATION_TOTAL_FRAMES");
    }

    @Test
    public void formationDoesNotAdvanceWhileOffScreen() {
        Sonic3kSSEntryRingObjectInstance ring = createRing(0);

        // Move camera bounds so ring is off-screen
        AbstractObjectInstance.updateCameraBounds(500, 500, 820, 724, 0);

        // Step 20 frames â€” ring should NOT advance because it's off-screen
        for (int frame = 1; frame <= 20; frame++) {
            ring.update(frame, null);
        }
        assertEquals(0, ring.getMappingFrame(), "mapping_frame should not advance while off-screen");
        assertTrue(ring.isForming(), "Ring should still be forming while off-screen");

        // Move camera back so ring is on-screen
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);

        // Now the formation should start advancing
        ring.update(21, null);
        // After 1 frame on-screen, mapping_frame is still 0 (timer counting down)
        assertEquals(0, ring.getMappingFrame(), "First on-screen frame: still on initial mapping frame");
        assertTrue(ring.isForming(), "Ring should still be forming after 1 on-screen frame");
    }

    /**
     * Core test: a player standing inside the collision box must NOT trigger
     * the ring during the entire 40-frame formation period.
     * <p>
     * This exercises the full collision path (player present, alive, not in
     * debug mode, centre within SSEntry_Range box) and verifies the ROM's
     * {@code cmpi.b #8,mapping_frame(a0) / blo.s locret} gate blocks it.
     */
    @Test
    public void playerInsideCollisionBoxCannotTriggerDuringFormation() {
        Sonic3kSSEntryRingObjectInstance ring = createRing(0);
        AbstractPlayableSprite player = createMockPlayerAt(RING_X, RING_Y);

        // Step through every frame of formation with the player overlapping
        for (int frame = 1; frame <= FORMATION_TOTAL_FRAMES; frame++) {
            ring.update(frame, player);
            assertTrue(ring.isForming(), "Ring should still be forming at frame " + frame
                            + " (mapping_frame=" + ring.getMappingFrame() + ")");
            assertTrue(ring.isMainState(), "Ring should remain in MAIN state at frame " + frame);
            assertFalse(ring.isDestroyed(), "Ring should not be destroyed during formation at frame " + frame);
        }
    }

    /**
     * After the 40-frame formation completes, a player inside the collision
     * box MUST trigger the ring. Uses the "all emeralds collected" path
     * (awards 50 rings and destroys) to avoid needing camera/flash services.
     */
    @Test
    public void playerInsideCollisionBoxTriggerAfterFormation() {
        // Set up all-emeralds path so onTouched() takes the simple destroy route
        gameState.configureSpecialStageProgress(7, 7);
        for (int i = 0; i < 7; i++) {
            gameState.markEmeraldCollected(i);
        }
        assertTrue(gameState.hasAllEmeralds(), "Precondition: all emeralds collected");

        Sonic3kSSEntryRingObjectInstance ring = createRing(0);
        AbstractPlayableSprite player = createMockPlayerAt(RING_X, RING_Y);

        // Advance through entire formation
        for (int frame = 1; frame <= FORMATION_TOTAL_FRAMES; frame++) {
            ring.update(frame, player);
        }
        assertTrue(ring.isForming(), "Ring should still be forming after exactly FORMATION_TOTAL_FRAMES");

        // One more frame transitions to idle (mapping_frame >= 8) â†’ collision fires
        ring.update(FORMATION_TOTAL_FRAMES + 1, player);
        assertFalse(ring.isForming(), "Ring should no longer be forming");
        // With all emeralds, onTouched awards 50 rings and destroys the ring
        assertTrue(ring.isDestroyed(), "Ring should be destroyed after player triggered it");
    }

    @Test
    public void collectedRingIsDestroyedImmediately() {
        // Mark bit 3 as collected
        gameState.markSpecialRingCollected(3);

        Sonic3kSSEntryRingObjectInstance ring = createRing(3);
        // ensureInitialized() is called lazily on the first update(), not in the constructor.
        // One update call triggers the collected-state check and sets destroyed=true.
        ring.update(1, null);
        assertTrue(ring.isDestroyed(), "Collected ring should be immediately destroyed");
    }

    @Test
    public void idleAnimationLoops() {
        Sonic3kSSEntryRingObjectInstance ring = createRing(0);

        // Advance through formation (40 frames). On frame 41, the 9th advance
        // triggers the transition to idle: mapping_frame = 10, timer = 6.
        for (int frame = 1; frame <= FORMATION_TOTAL_FRAMES; frame++) {
            ring.update(frame, null);
        }
        // Frame FORMATION_TOTAL_FRAMES+1 triggers the transition advance
        ring.update(FORMATION_TOTAL_FRAMES + 1, null);
        assertEquals(10, ring.getMappingFrame(), "First idle frame should be 10");

        // Now in idle animation. Idle: delay=6, frames={10,9,8,11}, loop.
        // Each idle step lasts delay+1 = 7 game frames.
        // The first idle frame (10) was set by the transition and is held for
        // 7 game frames (timer=6 + 1 underflow frame = ticks 41-47).
        // We're at tick 41 now, so 6 more frames to finish the first idle step.
        int gameFrame = FORMATION_TOTAL_FRAMES + 1;
        for (int tick = 0; tick < 6; tick++) {
            gameFrame++;
            ring.update(gameFrame, null);
        }
        assertEquals(10, ring.getMappingFrame(), "mapping_frame should still be 10 during first idle countdown");

        // Next group: advance to mapping_frame 9
        int[] expectedIdleFrames = {9, 8, 11, 10, 9}; // rest of first loop + start of second
        for (int step = 0; step < expectedIdleFrames.length; step++) {
            for (int tick = 0; tick < 7; tick++) {
                gameFrame++;
                ring.update(gameFrame, null);
            }
            assertEquals(expectedIdleFrames[step], ring.getMappingFrame(), "Idle mapping frame at step " + step);
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Creates a Mockito mock of {@link AbstractPlayableSprite} positioned at
     * the given centre coordinates. The mock is alive (not dead), not in debug
     * mode, and has a no-op {@code addRings} to allow the all-emeralds path.
     */
    private AbstractPlayableSprite createMockPlayerAt(int cx, int cy) {
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) cx);
        when(player.getCentreY()).thenReturn((short) cy);
        when(player.getDead()).thenReturn(false);
        when(player.isDebugMode()).thenReturn(false);
        // addRings is void â€” Mockito defaults to no-op, no stub needed
        return player;
    }

    private Sonic3kSSEntryRingObjectInstance createRing(int subtype) {
        setConstructionContext(services);
        try {
            ObjectSpawn spawn = new ObjectSpawn(RING_X, RING_Y, 0x85, subtype, 0, false, 0);
            Sonic3kSSEntryRingObjectInstance ring = new Sonic3kSSEntryRingObjectInstance(spawn);
            ring.setServices(services);
            return ring;
        } finally {
            clearConstructionContext();
        }
    }

    @SuppressWarnings("unchecked")
    private static void setConstructionContext(ObjectServices svc) {
        try {
            Field field = AbstractObjectInstance.class.getDeclaredField("CONSTRUCTION_CONTEXT");
            field.setAccessible(true);
            ((ThreadLocal<Object>) field.get(null)).set(svc);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void clearConstructionContext() {
        try {
            Field field = AbstractObjectInstance.class.getDeclaredField("CONSTRUCTION_CONTEXT");
            field.setAccessible(true);
            ((ThreadLocal<Object>) field.get(null)).remove();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}


