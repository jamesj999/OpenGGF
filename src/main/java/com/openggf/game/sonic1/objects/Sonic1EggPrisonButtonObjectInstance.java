package com.openggf.game.sonic1.objects;

import com.openggf.debug.DebugRenderContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Sonic 1 EggPrison button sub-object (subtype 1 from Pri_Var).
 * <p>
 * Reference: docs/s1disasm/_incObj/3E Prison Capsule.asm - Pri_Switched (routine 4)
 * <p>
 * SolidObject collision: d1=$17 (23), d2=8, d3=8
 * Animation: Ani_Pri .switchflash - alternates frames 1 and 3 at delay 2.
 * When boss is defeated (v_bossstatus changes) and Sonic lands on button,
 * triggers the capsule opening sequence via parent callback.
 */
public class Sonic1EggPrisonButtonObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // From disassembly: move.w #$17,d1 / moveq #8,d2 / moveq #8,d3
    private static final int HALF_WIDTH = 0x17;
    private static final int HALF_HEIGHT = 8;

    // From Pri_Var: subtype 1 priority = 5
    private static final int PRIORITY = 5;

    // Button depression: addq.w #8,obY(a0) in Pri_Switched
    private static final int DEPRESS_DISTANCE = 8;

    // Animation: .switchflash: dc.b 2, 1, 3, afEnd
    // Frame delay 2, alternates between mapping frames 1 and 3
    private static final int ANIM_DELAY = 2;
    private static final int FRAME_SWITCH_1 = 1;
    private static final int FRAME_SWITCH_2 = 3;

    private final int baseY;
    private int currentY;
    private boolean triggered;
    private Sonic1EggPrisonObjectInstance parent;
    private boolean parentResolved;
    private int animTimer;
    private int currentFrame = FRAME_SWITCH_1;

    /**
     * Standalone constructor for factory creation (subtype 1 placement entry).
     * Parent body is resolved on first update by scanning active objects.
     */
    public Sonic1EggPrisonButtonObjectInstance(ObjectSpawn spawn) {
        super(spawn, "EggPrison Button");
        this.baseY = spawn.y();
        this.currentY = spawn.y();
        this.triggered = false;
        this.animTimer = ANIM_DELAY;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // Resolve parent body on first update
        if (!parentResolved) {
            resolveParent();
        }

        // Animate switch flash (always runs)
        animTimer--;
        if (animTimer < 0) {
            animTimer = ANIM_DELAY;
            currentFrame = (currentFrame == FRAME_SWITCH_1) ? FRAME_SWITCH_2 : FRAME_SWITCH_1;
        }
    }

    /**
     * Scans active objects for the EggPrison body (subtype 0) at the same X position.
     * Registers this button with the body so onButtonTriggered() can fire.
     */
    private void resolveParent() {
        parentResolved = true;
        ObjectManager objectManager = services().objectManager();
        if (objectManager == null) {
            return;
        }
        for (var obj : objectManager.getActiveObjects()) {
            if (obj instanceof Sonic1EggPrisonObjectInstance body && !obj.isDestroyed()) {
                this.parent = body;
                body.registerButton(this);
                return;
            }
        }
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(
                HALF_WIDTH,
                HALF_HEIGHT,
                HALF_HEIGHT,
                0,
                currentY - spawn.y()
        );
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite sprite) {
        return true;
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (!triggered && contact.standing() && player.getYSpeed() >= 0) {
            triggered = true;
            currentY = baseY + DEPRESS_DISTANCE;

            if (parent != null) {
                parent.onButtonTriggered();
            }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services().renderManager();
        PatternSpriteRenderer renderer = renderManager != null
                ? renderManager.getEggPrisonRenderer()
                : null;

        if (renderer == null || !renderer.isReady()) {
            return;
        }

        renderer.drawFrameIndex(currentFrame, spawn.x(), currentY, false, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        ctx.drawRect(spawn.x(), currentY, HALF_WIDTH, HALF_HEIGHT, 0.9f, 0.2f, 0.2f);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    public void detachFromParent() {
        this.parent = null;
    }

    public void destroyButton() {
        this.parent = null;
        setDestroyed(true);
    }
}
