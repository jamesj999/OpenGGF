package com.openggf.game.sonic1.objects;

import com.openggf.game.GameServices;
import com.openggf.graphics.GLCommand;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Sonic 1 Object 0x08 - Water splash (LZ).
 * Spawned when the player enters or exits water.
 * <p>
 * ROM reference: docs/s1disasm/_incObj/08 Water Splash.asm
 * <p>
 * Copies player X position on spawn, tracks water surface Y each frame.
 * Plays 3-frame animation (4 game ticks per frame) then deletes itself.
 * Uses Nem_Splash art at ArtTile_LZ_Splash ($259), palette line 2.
 */
public class Sonic1SplashObjectInstance extends AbstractObjectInstance {

    // Ani_Splash: dc.b 4, 0, 1, 2, afRoutine
    private static final int FRAME_COUNT = 3;
    private static final int FRAME_DELAY = 4; // duration byte value from animation script

    private final int posX;
    private int posY;
    private int animTimer;
    private int frameIndex;

    /**
     * Creates a splash at the player's X position and the water surface Y.
     *
     * @param playerX player centre X at time of water entry/exit
     * @param waterY  water surface Y position
     */
    public Sonic1SplashObjectInstance(int playerX, int waterY) {
        super(new ObjectSpawn(playerX, waterY, 0x08, 0, 0, false, 0), "Splash");
        this.posX = playerX;
        this.posY = waterY;
        this.animTimer = FRAME_DELAY;
        this.frameIndex = 0;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // ROM (Spla_Display): move.w (v_waterpos1).w,obY(a0)
        // Track water surface Y each frame
        var lm = GameServices.level();
        if (services().currentLevel() != null) {
            WaterSystem waterSystem = WaterSystem.getInstance();
            posY = waterSystem.getVisualWaterLevelY(
                    GameServices.level().getFeatureZoneId(), GameServices.level().getFeatureActId());
        }

        // AnimateSprite with Ani_Splash: duration 4, frames 0/1/2, afRoutine
        animTimer--;
        if (animTimer < 0) {
            animTimer = FRAME_DELAY;
            frameIndex++;
            if (frameIndex >= FRAME_COUNT) {
                setDestroyed(true);
            }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || frameIndex >= FRAME_COUNT) {
            return;
        }

        PatternSpriteRenderer renderer = getRenderer(ObjectArtKeys.LZ_SPLASH);
        if (renderer == null) return;

        renderer.drawFrameIndex(frameIndex, posX, posY, false, false);
    }

    @Override
    public int getPriorityBucket() {
        return 1; // obPriority = 1 from ROM
    }
}
