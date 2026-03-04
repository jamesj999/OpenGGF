package com.openggf.game.sonic2.events;

import com.openggf.camera.Camera;
import com.openggf.game.sonic2.OilSurfaceManager;

/**
 * Oil Ocean Zone events.
 * ROM: LevEvents_OOZ (s2.asm:20938-21035)
 * Also handles oil surface (Obj07) and oil slides (OilSlides routine).
 */
public class Sonic2OOZEvents extends Sonic2ZoneEvents {
    private OilSurfaceManager oilManager;

    public Sonic2OOZEvents(Camera camera) {
        super(camera);
    }

    @Override
    public void init(int act) {
        super.init(act);
        oilManager = new OilSurfaceManager();
    }

    @Override
    public void update(int act, int frameCounter) {
        if (oilManager != null) {
            var player = camera.getFocusedSprite();
            if (player != null) {
                oilManager.update(player);
            }
        }
    }
}
