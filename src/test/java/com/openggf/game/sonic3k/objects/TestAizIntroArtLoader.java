package com.openggf.game.sonic3k.objects;

import com.openggf.game.RuntimeManager;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
public class TestAizIntroArtLoader {
    private SharedLevel sharedLevel;

    @BeforeEach
    public void setUp() throws Exception {
        AizIntroArtLoader.reset();
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
    }

    @AfterEach
    public void tearDown() {
        AizIntroArtLoader.reset();
        if (sharedLevel != null) {
            sharedLevel.dispose();
        }
    }

    @Test
    public void planeRendererCanBeCachedAfterServiceBackedLoadCompletes() {
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());

        AizIntroArtLoader.loadAllIntroArt(services);

        PatternSpriteRenderer renderer = AizIntroArtLoader.getPlaneRenderer(services);
        assertNotNull(renderer);
        assertTrue(renderer.isReady());
    }
}


