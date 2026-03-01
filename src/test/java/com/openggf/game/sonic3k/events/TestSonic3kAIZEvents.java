package com.openggf.game.sonic3k.events;

import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.Sonic3kLoadBootstrap;
import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;

import static org.junit.Assert.*;

public class TestSonic3kAIZEvents {

    @Test
    public void initWithIntroSkipDoesNotSpawnIntroObject() {
        Camera camera = Camera.getInstance();
        var events = new Sonic3kAIZEvents(camera,
                new Sonic3kLoadBootstrap(Sonic3kLoadBootstrap.Mode.SKIP_INTRO, null));
        events.init(0);
        // No crash, no spawn (ObjectManager is null in test)
        assertEquals(0, events.getEventRoutine());
    }

    @Test
    public void initForAct1WithNormalBootstrapRequestsIntro() {
        Camera camera = Camera.getInstance();
        var events = new Sonic3kAIZEvents(camera, Sonic3kLoadBootstrap.NORMAL);
        // When bootstrap is NORMAL and act is 0, intro should be requested
        assertTrue(events.shouldSpawnIntro(0));
    }

    @Test
    public void initForAct2DoesNotRequestIntro() {
        Camera camera = Camera.getInstance();
        var events = new Sonic3kAIZEvents(camera, Sonic3kLoadBootstrap.NORMAL);
        assertFalse(events.shouldSpawnIntro(1));
    }

    @Test
    public void eventsFg5StartsFireTransitionAndRequestsSeamlessFlow() {
        LevelManager.getInstance().resetState();
        Camera camera = Camera.getInstance();
        camera.setX((short) 0x2F10);
        camera.setY((short) 0x0200);

        var events = new Sonic3kAIZEvents(camera, Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        events.setEventsFg5(true);

        events.update(0, 0);
        assertTrue(events.isFireTransitionActive());
        assertFalse(events.isAct2TransitionRequested());

        for (int i = 1; i < 320 && !events.isAct2TransitionRequested(); i++) {
            events.update(0, i);
        }

        assertTrue(events.isAct2TransitionRequested());
        SeamlessLevelTransitionRequest request = LevelManager.getInstance().consumeSeamlessTransitionRequest();
        assertNotNull(request);
        assertEquals(SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL, request.type());
        assertEquals(0, request.targetZone());
        assertEquals(1, request.targetAct());
        assertFalse(request.preserveMusic());
        assertFalse(request.showInLevelTitleCard());
        assertTrue(request.musicOverrideId() >= 0);
    }

    @Test
    public void fireTransitionRequestsMutationBeforeActReload() {
        LevelManager levelManager = LevelManager.getInstance();
        levelManager.resetState();

        Camera camera = Camera.getInstance();
        camera.setX((short) 0x2F10);
        camera.setY((short) 0x0200);

        var events = new Sonic3kAIZEvents(camera, Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        events.setEventsFg5(true);

        boolean sawMutation = false;
        for (int i = 0; i < 260 && !events.isAct2TransitionRequested(); i++) {
            events.update(0, i);
            SeamlessLevelTransitionRequest request = levelManager.consumeSeamlessTransitionRequest();
            if (request == null) {
                continue;
            }
            if (request.type() == SeamlessLevelTransitionRequest.TransitionType.MUTATE_ONLY) {
                sawMutation = true;
                break;
            }
            fail("Expected mutation stage before reload transition");
        }

        assertTrue("Expected MUTATE_ONLY request during fire transition", sawMutation);
    }
}
