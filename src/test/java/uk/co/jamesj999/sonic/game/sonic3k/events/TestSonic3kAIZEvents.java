package uk.co.jamesj999.sonic.game.sonic3k.events;

import org.junit.Test;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kLoadBootstrap;
import static org.junit.Assert.*;

public class TestSonic3kAIZEvents {

    @Test
    public void initWithIntroSkipDoesNotSpawnIntroObject() {
        Camera camera = Camera.getInstance();
        var events = new Sonic3kAIZEvents(camera,
                new Sonic3kLoadBootstrap(Sonic3kLoadBootstrap.Mode.AIZ1_GAMEPLAY_AFTER_INTRO));
        events.init(0);
        // No crash, no spawn (ObjectManager is null in test)
        assertEquals(0, events.getEventRoutine());
    }

    @Test
    public void initForAct1WithNoneBootstrapRequestsIntro() {
        Camera camera = Camera.getInstance();
        var events = new Sonic3kAIZEvents(camera, Sonic3kLoadBootstrap.NONE);
        // When bootstrap is NONE and act is 0, intro should be requested
        assertTrue(events.shouldSpawnIntro(0));
    }

    @Test
    public void initForAct2DoesNotRequestIntro() {
        Camera camera = Camera.getInstance();
        var events = new Sonic3kAIZEvents(camera, Sonic3kLoadBootstrap.NONE);
        assertFalse(events.shouldSpawnIntro(1));
    }
}
