package com.openggf.level.objects;

import com.openggf.game.GameServices;

/**
 * Legacy {@link ObjectServices} bridge for call sites that still build an
 * {@link ObjectManager} without passing explicit services.
 *
 * <p>Runtime-owned dependencies are resolved through strict {@link GameServices}
 * accessors, so callers must create a gameplay runtime first.</p>
 */
public final class BootstrapObjectServices extends DefaultObjectServices {

    public BootstrapObjectServices() {
        super(GameServices.level(),
                GameServices.camera(),
                GameServices.gameState(),
                GameServices.sprites(),
                GameServices.fade(),
                GameServices.water(),
                GameServices.parallax());
    }
}
