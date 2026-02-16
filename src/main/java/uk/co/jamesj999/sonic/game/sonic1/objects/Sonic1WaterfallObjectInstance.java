package uk.co.jamesj999.sonic.game.sonic1.objects;

import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.WaterSystem;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectArtKeys;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Sonic 1 Object 0x65 - Waterfalls (LZ).
 * <p>
 * ROM reference: docs/s1disasm/_incObj/65 Waterfalls.asm
 */
public class Sonic1WaterfallObjectInstance extends AbstractObjectInstance {

    private static final int ROUTINE_ANIMATE = 2;
    private static final int ROUTINE_CHECK_DELETE = 4;
    private static final int ROUTINE_ON_WATER = 6;
    private static final int ROUTINE_SPECIAL = 8;

    private static final int SPLASH_FRAME_START = 9;
    private static final int SPLASH_FRAME_END = 11;
    private static final int SPLASH_FRAME_DURATION = 5;

    private int routine;
    private int x;
    private int y;
    private int mappingFrame;
    private int animTimer;

    private boolean highPriority;
    private int priorityBucket;

    public Sonic1WaterfallObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Waterfall");
        this.x = spawn.x();
        this.y = spawn.y();
        this.mappingFrame = 0;
        this.animTimer = SPLASH_FRAME_DURATION;
        this.highPriority = false;
        this.priorityBucket = 1;

        initFromSubtype();
    }

    private void initFromSubtype() {
        int subtype = spawn.subtype() & 0xFF;

        // bpl.s .under80 / bset #7,obGfx(a0)
        highPriority = (subtype & 0x80) != 0;

        mappingFrame = subtype & 0x0F;
        routine = ROUTINE_CHECK_DELETE;

        if (mappingFrame == SPLASH_FRAME_START) {
            // Splash pieces render in front.
            priorityBucket = RenderPriority.MIN;
            routine = ROUTINE_ANIMATE;

            // $49 subtype follows water height.
            if ((subtype & 0x40) != 0) {
                routine = ROUTINE_ON_WATER;
            }
            // $A9 subtype has extra gfx bit behavior tied to layout.
            if ((subtype & 0x20) != 0) {
                routine = ROUTINE_SPECIAL;
            }
        }
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(
                x,
                y,
                spawn.objectId(),
                spawn.subtype(),
                spawn.renderFlags(),
                spawn.respawnTracked(),
                spawn.rawYWord());
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        switch (routine) {
            case ROUTINE_ON_WATER -> {
                y = getWaterLevel() - 0x10;
                animateSplash();
            }
            case ROUTINE_SPECIAL -> {
                // The ROM toggles an obGfx bit from level layout state.
                // Keep this behavior approximate: keep splash animation active.
                animateSplash();
            }
            case ROUTINE_ANIMATE -> animateSplash();
            case ROUTINE_CHECK_DELETE -> {
                // RememberState persistence handled by object manager.
            }
            default -> {
            }
        }
    }

    private void animateSplash() {
        if (mappingFrame < SPLASH_FRAME_START || mappingFrame > SPLASH_FRAME_END) {
            mappingFrame = SPLASH_FRAME_START;
        }

        animTimer--;
        if (animTimer >= 0) {
            return;
        }

        animTimer = SPLASH_FRAME_DURATION;
        mappingFrame++;
        if (mappingFrame > SPLASH_FRAME_END) {
            mappingFrame = SPLASH_FRAME_START;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.LZ_WATERFALL);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        boolean hFlip = (spawn.renderFlags() & 0x1) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;
        renderer.drawFrameIndex(mappingFrame, x, y, hFlip, vFlip);
    }

    @Override
    public boolean isHighPriority() {
        return highPriority;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(priorityBucket);
    }

    @Override
    public boolean isPersistent() {
        return !isDestroyed() && isOnScreenX(192);
    }

    private int getWaterLevel() {
        LevelManager lm = LevelManager.getInstance();
        if (lm == null || lm.getCurrentLevel() == null) {
            return y;
        }
        WaterSystem waterSystem = WaterSystem.getInstance();
        return waterSystem.getVisualWaterLevelY(lm.getRomZoneId(), lm.getCurrentAct());
    }
}
