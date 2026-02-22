package com.openggf.game.sonic2.objects.badniks;

import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Turtloid Jet Exhaust (0x9C) - Animated jet flame on the back of the Turtloid.
 * Follows parent Turtloid position and animates between two flame frames.
 *
 * Based on disassembly Obj9C (s2.asm:74480-74523).
 * Animation: Ani_obj9A = dc.b 1, 6, 7, $FF (alternates frames 6 and 7, speed 1).
 */
public class TurtloidJetInstance extends AbstractObjectInstance {

    // Animation: frames 6 and 7 from shared Turtloid sheet, speed = 1 (every other frame)
    private static final int JET_FRAME_1 = 6;
    private static final int JET_FRAME_2 = 7;
    private static final int ANIM_SPEED = 1; // Ani_obj9A: dc.b 1, ...

    private final TurtloidBadnikInstance parent;
    private int currentX;
    private int currentY;
    private int animFrame;
    private int animTimer;

    public TurtloidJetInstance(ObjectSpawn spawn, TurtloidBadnikInstance parent) {
        super(spawn, "TurtloidJet");
        this.parent = parent;
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.animFrame = JET_FRAME_1;
        this.animTimer = ANIM_SPEED;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (parent.isParentDestroyed()) {
            setDestroyed(true);
            return;
        }

        // Follow parent position exactly (Obj9C copies parent pos in ROM)
        currentX = parent.getParentX();
        currentY = parent.getParentY();

        // Animate jet exhaust: alternate between frames 6 and 7
        animTimer--;
        if (animTimer < 0) {
            animTimer = ANIM_SPEED;
            animFrame = (animFrame == JET_FRAME_1) ? JET_FRAME_2 : JET_FRAME_1;
        }
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(currentX, currentY, spawn.objectId(),
                spawn.subtype(), spawn.renderFlags(), spawn.respawnTracked(), spawn.rawYWord());
    }

    @Override
    public int getX() { return currentX; }

    @Override
    public int getY() { return currentY; }

    @Override
    public int getPriorityBucket() {
        // ROM: priority = 5 (Obj9C_SubObjData)
        return RenderPriority.clamp(5);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.TURTLOID);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Jet exhaust frames 6-7 from shared Turtloid sheet
        renderer.drawFrameIndex(animFrame, currentX, currentY, false, false);
    }
}
