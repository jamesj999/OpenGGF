package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.sprites.managers.SpriteManager;

/**
 * Bootstrap-only {@link ObjectServices} bridge for pre-runtime construction paths.
 *
 * <p>This is intentionally narrow infrastructure: once a gameplay {@code GameRuntime}
 * exists, {@link ObjectManager} must switch to {@link DefaultObjectServices} backed by
 * that runtime instead of re-reading bootstrap singletons.</p>
 */
final class BootstrapObjectServices extends DefaultObjectServices {

    BootstrapObjectServices() {
        super(LevelManager.getInstance(),
                Camera.getInstance(),
                GameStateManager.getInstance(),
                SpriteManager.getInstance(),
                FadeManager.getInstance(),
                WaterSystem.getInstance(),
                ParallaxManager.getInstance());
    }
}
