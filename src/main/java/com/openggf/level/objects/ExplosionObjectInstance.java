package com.openggf.level.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.game.PlayableEntity;

import java.util.List;
import java.util.logging.Logger;

public class ExplosionObjectInstance extends AbstractObjectInstance {
    private static final Logger LOGGER = Logger.getLogger(ExplosionObjectInstance.class.getName());
    private final ObjectRenderManager renderManager;
    private int pendingSfxId = -1;
    private int animTimer = 0;
    private int animFrame = 0;
    // S1 disassembly: obTimeFrame = 7, counts 7→0 then advances = 8 game frames per sprite frame
    private static final int ANIM_DELAY = 8;
    private static final int MAX_FRAME = 4;

    public ExplosionObjectInstance(int id, int x, int y, ObjectRenderManager renderManager) {
        this(id, x, y, renderManager, -1);
    }

    /**
     * Creates an explosion with an optional sound effect.
     * ROM: Explosion objects play their SFX in the init routine (e.g. sfx_Break, sfx_Bomb).
     *
     * @param sfxId SFX ID to play on creation, or -1 for no sound
     */
    public ExplosionObjectInstance(int id, int x, int y, ObjectRenderManager renderManager, int sfxId) {
        super(new ObjectSpawn(x, y, id, 0, 0, false, 0), "Explosion");
        this.renderManager = renderManager;
        this.pendingSfxId = sfxId;
        playPendingSfxIfPossible();
    }

    @Override
    public void setServices(ObjectServices services) {
        super.setServices(services);
        playPendingSfxIfPossible();
    }

    private void playPendingSfxIfPossible() {
        if (pendingSfxId < 0) {
            return;
        }
        try {
            ObjectServices ctx = tryServices();
            if (ctx != null) {
                ctx.playSfx(pendingSfxId);
                pendingSfxId = -1;
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to play explosion sound: " + e.getMessage());
        }
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        // Animation
        animTimer++;
        if (animTimer >= ANIM_DELAY) {
            animTimer = 0;
            animFrame++;
            if (animFrame > MAX_FRAME) {
                setDestroyed(true);
            }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || animFrame > MAX_FRAME)
            return;
        renderManager.getExplosionRenderer().drawFrameIndex(animFrame, spawn.x(), spawn.y(), false, false);
    }
}
