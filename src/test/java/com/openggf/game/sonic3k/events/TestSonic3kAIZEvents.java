package com.openggf.game.sonic3k.events;

import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.Sonic3kLoadBootstrap;
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
}
