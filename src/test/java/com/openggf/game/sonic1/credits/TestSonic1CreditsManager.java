package com.openggf.game.sonic1.credits;

import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * ROM-free tests for Sonic 1 credits timing state helpers.
 */
public class TestSonic1CreditsManager {

    @Test
    public void testGameplayOnlyRunsInPlayingAndFadeoutStates() throws Exception {
        Sonic1CreditsManager manager = new Sonic1CreditsManager();

        setState(manager, Sonic1CreditsManager.State.DEMO_LOAD_DELAY);
        assertFalse(manager.shouldRunDemoGameplay());

        setState(manager, Sonic1CreditsManager.State.DEMO_FADE_IN);
        assertFalse(manager.shouldRunDemoGameplay());

        setState(manager, Sonic1CreditsManager.State.DEMO_PLAYING);
        assertTrue(manager.shouldRunDemoGameplay());

        setState(manager, Sonic1CreditsManager.State.DEMO_FADING_OUT);
        assertTrue(manager.shouldRunDemoGameplay());
    }

    @Test
    public void testSpriteOverlayOnlyRunsDuringDemoFadeIn() throws Exception {
        Sonic1CreditsManager manager = new Sonic1CreditsManager();

        setState(manager, Sonic1CreditsManager.State.DEMO_LOAD_DELAY);
        assertFalse(manager.shouldRenderDemoSpritesOverFade());

        setState(manager, Sonic1CreditsManager.State.DEMO_FADE_IN);
        assertTrue(manager.shouldRenderDemoSpritesOverFade());

        setState(manager, Sonic1CreditsManager.State.DEMO_PLAYING);
        assertFalse(manager.shouldRenderDemoSpritesOverFade());

        setState(manager, Sonic1CreditsManager.State.DEMO_FADING_OUT);
        assertFalse(manager.shouldRenderDemoSpritesOverFade());
    }

    @Test
    public void testFrozenSceneOnlyAdvancesDuringHiddenLoadDelay() throws Exception {
        Sonic1CreditsManager manager = new Sonic1CreditsManager();

        setState(manager, Sonic1CreditsManager.State.DEMO_LOAD_DELAY);
        assertTrue(manager.shouldAdvanceFrozenDemoScene());

        setState(manager, Sonic1CreditsManager.State.DEMO_FADE_IN);
        assertFalse(manager.shouldAdvanceFrozenDemoScene());

        setState(manager, Sonic1CreditsManager.State.DEMO_PLAYING);
        assertFalse(manager.shouldAdvanceFrozenDemoScene());
    }

    @Test
    public void testDemoInputMaskIsSuppressedUntilGameplayStarts() throws Exception {
        Sonic1CreditsManager manager = new Sonic1CreditsManager();
        setField(manager, "demoInputPlayer", new DemoInputPlayer(new byte[]{0x08, 0x05, 0x00, 0x00}));

        setState(manager, Sonic1CreditsManager.State.DEMO_LOAD_DELAY);
        assertEquals(0, manager.getDemoInputMask());

        setState(manager, Sonic1CreditsManager.State.DEMO_FADE_IN);
        assertEquals(0, manager.getDemoInputMask());

        setState(manager, Sonic1CreditsManager.State.DEMO_PLAYING);
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, manager.getDemoInputMask());
    }

    @Test
    public void testTextPacingTableMatchesCreditsCount() {
        assertEquals(Sonic1CreditsDemoData.TOTAL_CREDITS, Sonic1CreditsDemoData.TEXT_PACING_DELAY_FRAMES.length);
        assertEquals(0, Sonic1CreditsDemoData.TEXT_PACING_DELAY_FRAMES[Sonic1CreditsDemoData.TOTAL_CREDITS - 1]);
        assertEquals(4, Sonic1CreditsDemoData.DEMO_LOAD_DELAY_FRAMES);
    }

    private static void setState(Sonic1CreditsManager manager, Sonic1CreditsManager.State state) throws Exception {
        setField(manager, "state", state);
    }

    private static void setField(Sonic1CreditsManager manager, String fieldName, Object value) throws Exception {
        Field field = Sonic1CreditsManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(manager, value);
    }
}
