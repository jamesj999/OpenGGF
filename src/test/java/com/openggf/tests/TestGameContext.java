package com.openggf.tests;

import com.openggf.GameContext;
import com.openggf.camera.Camera;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.timer.TimerManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link GameContext} holder object and its factory methods.
 */
public class TestGameContext {

    @Before
    public void setUp() {
        // Ensure clean Camera state before each test so results
        // don't depend on test execution order.
        Camera.getInstance().resetState();
    }

    @Test
    public void productionWrapsExistingSingletons() {
        GameContext ctx = GameContext.production();
        assertSame(Camera.getInstance(), ctx.camera());
        assertSame(LevelManager.getInstance(), ctx.levelManager());
        assertSame(SpriteManager.getInstance(), ctx.spriteManager());
        assertSame(CollisionSystem.getInstance(), ctx.collisionSystem());
        assertSame(GraphicsManager.getInstance(), ctx.graphicsManager());
        assertSame(TimerManager.getInstance(), ctx.timerManager());
        assertSame(WaterSystem.getInstance(), ctx.waterSystem());
    }

    @Test
    public void forTestingResetsCamera() {
        Camera.getInstance().setFrozen(true);
        assertTrue("Camera should be frozen before reset", Camera.getInstance().getFrozen());
        GameContext ctx = GameContext.forTesting();
        assertFalse("Camera should be unfrozen after forTesting()", ctx.camera().getFrozen());
    }

    @Test
    public void forTestingReturnsSpriteManagerSingleton() {
        GameContext ctx = GameContext.forTesting();
        assertSame("spriteManager() should return the SpriteManager singleton",
                SpriteManager.getInstance(), ctx.spriteManager());
    }
}
