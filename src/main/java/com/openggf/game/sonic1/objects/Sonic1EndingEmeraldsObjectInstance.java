package com.openggf.game.sonic1.objects;

import com.openggf.graphics.GLCommand;

import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.List;

/**
 * Object 88 - Chaos emeralds on the ending sequence.
 * <p>
 * Six emerald instances orbit around a center point (Sonic's position),
 * expanding outward and rising until the radius reaches maximum.
 * <p>
 * Each emerald has:
 * <ul>
 *   <li>{@code origX/origY} - center of the orbit circle</li>
 *   <li>{@code radius} - expanding from 0 to {@code $2000} in {@code $20} increments (word)</li>
 *   <li>{@code rotationSpeed} - rotation accumulator, also 0 to {@code $2000} (word)</li>
 *   <li>{@code personalAngle} - accumulated angle (word); high byte used for sine lookup</li>
 * </ul>
 * <p>
 * Orbital math from ECha_Move:
 * <pre>
 *   angle_byte = (personalAngle >> 8) & 0xFF
 *   x = origX + (radius_hi * cos(angle_byte)) >> 8
 *   y = origY + (radius_hi * sin(angle_byte)) >> 8
 * </pre>
 * <p>
 * Reference: docs/s1disasm/_incObj/88 Ending Sequence Emeralds.asm
 */
public class Sonic1EndingEmeraldsObjectInstance extends AbstractObjectInstance {

    /** All live emerald instances for bulk destruction. */
    private static final List<Sonic1EndingEmeraldsObjectInstance> ALL_EMERALDS = new ArrayList<>();

    // ========================================================================
    // ROM Constants
    // ========================================================================

    /** Render priority: 1 (from ECha_LoadLoop: move.b #1,obPriority). */
    private static final int PRIORITY = 1;

    /** Radius/rotation expansion increment per frame (ROM: addi.w #$20). */
    private static final int EXPAND_STEP = 0x20;

    /** Maximum radius/rotation (ROM: cmpi.w #$2000). */
    private static final int MAX_VALUE = 0x2000;

    /** Target Y for rising (ROM: cmpi.w #$140,echa_origY). */
    private static final int TARGET_Y = 0x140;

    // ========================================================================
    // State
    // ========================================================================

    private final PatternSpriteRenderer renderer;
    private final int frameId;

    private int origX;
    private int origY;
    private int radius;        // word: 0 → $2000
    private int rotationSpeed; // word (echa_angle): 0 → $2000
    private int personalAngle; // word (obAngle): accumulated, high byte = actual angle

    private int currentX;
    private int currentY;

    /**
     * @param centerX   initial orbit center X (Sonic's X)
     * @param centerY   initial orbit center Y (Sonic's Y)
     * @param angleOffset initial angle byte (0x00-0xFF), placed in high byte of personalAngle
     * @param frame     mapping frame index (1-6 for the 6 emerald colors)
     */
    public Sonic1EndingEmeraldsObjectInstance(int centerX, int centerY, int angleOffset, int frame) {
        super(null, "EndChaos");
        this.origX = centerX;
        this.origY = centerY;
        // ROM: move.b d3,obAngle(a1) — store in the byte, but we track as word with
        // the initial offset in the high byte position for consistency
        this.personalAngle = (angleOffset & 0xFF) << 8;
        this.frameId = frame;
        this.currentX = centerX;
        this.currentY = centerY;

        ObjectRenderManager renderManager = services().renderManager();
        this.renderer = renderManager != null ? renderManager.getRenderer(ObjectArtKeys.END_EMERALDS) : null;

        synchronized (ALL_EMERALDS) {
            ALL_EMERALDS.add(this);
        }
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }
        updateOrbit();
    }

    /**
     * ECha_Move: Update orbital position, expand radius, advance rotation, rise.
     */
    private void updateOrbit() {
        // Accumulate rotation: obAngle += echa_angle
        personalAngle = (personalAngle + rotationSpeed) & 0xFFFF;

        // Get angle byte from high byte of personalAngle
        // ROM: move.b obAngle(a0),d0 — on 68000 big-endian, byte read of word = high byte
        int angleByte = (personalAngle >> 8) & 0xFF;

        // ROM: jsr (CalcSine).l — d0 = sin, d1 = cos
        int sin = TrigLookupTable.sinHex(angleByte);
        int cos = TrigLookupTable.cosHex(angleByte);

        // ROM: move.b echa_radius(a0),d4 — high byte of radius word
        int radiusByte = (radius >> 8) & 0xFF;

        // ROM: muls.w d4,d1 / asr.l #8,d1 — X offset = (cos * radius_hi) >> 8
        int offsetX = (cos * radiusByte) >> 8;
        // ROM: muls.w d4,d0 / asr.l #8,d0 — Y offset = (sin * radius_hi) >> 8
        int offsetY = (sin * radiusByte) >> 8;

        currentX = origX + offsetX;
        currentY = origY + offsetY;

        // ECha_Expand: grow radius
        if (radius < MAX_VALUE) {
            radius += EXPAND_STEP;
        }

        // ECha_Rotate: grow rotation speed
        if (rotationSpeed < MAX_VALUE) {
            rotationSpeed += EXPAND_STEP;
        }

        // ECha_Rise: raise center Y
        if (origY > TARGET_Y) {
            origY--;
        }
    }

    /** Check if this emerald's radius has reached maximum ($2000). */
    public boolean hasReachedMaxRadius() {
        return radius >= MAX_VALUE;
    }

    /** Destroy all active emerald instances (ROM: Obj87_ClrObjRam loop). */
    public static void destroyAllEmeralds() {
        synchronized (ALL_EMERALDS) {
            for (Sonic1EndingEmeraldsObjectInstance em : ALL_EMERALDS) {
                em.setDestroyed(true);
            }
            ALL_EMERALDS.clear();
        }
    }

    // ========================================================================
    // Rendering
    // ========================================================================

    @Override
    public int getPriorityBucket() {
        return PRIORITY;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(frameId, currentX, currentY, false, false);
    }
}
