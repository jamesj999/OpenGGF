package com.openggf.tests;

import com.openggf.GameContext;
import com.openggf.camera.Camera;
import com.openggf.level.LevelManager;
import com.openggf.physics.CollisionSystem;
import com.openggf.sprites.managers.SpriteManager;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link GameContext} holder object and its factory methods.
 */
public class TestGameContext {

    @Test
    public void productionWrapsExistingSingletons() {
        GameContext ctx = GameContext.production();
        assertSame(Camera.getInstance(), ctx.camera());
        assertSame(LevelManager.getInstance(), ctx.levelManager());
        assertSame(SpriteManager.getInstance(), ctx.spriteManager());
        assertSame(CollisionSystem.getInstance(), ctx.collisionSystem());
    }

    @Test
    public void forTestingResetsCamera() {
        Camera.getInstance().setFrozen(true);
        assertTrue("Camera should be frozen before reset", Camera.getInstance().getFrozen());
        GameContext ctx = GameContext.forTesting();
        assertFalse("Camera should be unfrozen after forTesting()", ctx.camera().getFrozen());
    }

    @Test
    public void forTestingResetsSpriteManager() {
        GameContext ctx = GameContext.forTesting();
        assertNotNull("SpriteManager should not be null after forTesting()", ctx.spriteManager());
    }
}
