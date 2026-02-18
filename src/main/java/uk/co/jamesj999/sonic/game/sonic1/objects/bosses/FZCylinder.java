package uk.co.jamesj999.sonic.game.sonic1.objects.bosses;

import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1ObjectIds;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectArtKeys;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.SolidObjectParams;
import uk.co.jamesj999.sonic.level.objects.SolidObjectProvider;
import uk.co.jamesj999.sonic.level.objects.boss.AbstractBossChild;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0x84 — FZ Crushing Cylinder.
 * ROM: _incObj/84 FZ Eggman's Cylinders.asm
 *
 * 4 instances with subtypes 0, 2, 4, 6:
 *   0: bottom-left  (boss_fz_x + $80,  boss_fz_y + $110)
 *   2: bottom-right  (boss_fz_x + $100, boss_fz_y + $110)
 *   4: top-left     (boss_fz_x + $40,  boss_fz_y - $50)
 *   6: top-right    (boss_fz_x + $C0,  boss_fz_y - $50)
 *
 * Bottom cylinders (subtypes 0-2) extend downward (objoff_29 = -1).
 * Top cylinders (subtypes 4-6) extend upward (objoff_29 = +1).
 *
 * Extension uses 32-bit fixed-point offset (objoff_3C):
 *   Bottom: subtract $8000/frame, boost -$28000 when near limit (-$10)
 *   Top: add $8000/frame, boost +$28000 when near limit (+$10)
 *   Range: -$A0 to +$A0
 *
 * SolidObject params: d1=$2B, d2=$60, d3=$61
 */
public class FZCylinder extends AbstractBossChild implements SolidObjectProvider {

    // Position data from EggmanCylinder_PosData
    private static final int[][] CYLINDER_POS = {
            {Sonic1Constants.BOSS_FZ_X + 0x80,  Sonic1Constants.BOSS_FZ_Y + 0x110}, // subtype 0
            {Sonic1Constants.BOSS_FZ_X + 0x100, Sonic1Constants.BOSS_FZ_Y + 0x110}, // subtype 2
            {Sonic1Constants.BOSS_FZ_X + 0x40,  Sonic1Constants.BOSS_FZ_Y - 0x50},  // subtype 4
            {Sonic1Constants.BOSS_FZ_X + 0xC0,  Sonic1Constants.BOSS_FZ_Y - 0x50},  // subtype 6
    };

    // SolidObject params: d1=$2B, d2=$60, d3=$61
    private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(0x2B, 0x60, 0x61);

    private final int subtype;      // 0, 2, 4, or 6
    private final boolean isBottom;  // subtypes 0-2 are bottom, 4-6 are top
    private final int baseX;
    private final int baseY;         // objoff_38 — base Y position

    // Extension state
    private int direction;           // objoff_29: -1 = extending (bottom), +1 = extending (top), 0 = idle
    private int extensionFixed;      // objoff_3C: 32-bit fixed-point extension offset
    private boolean active;          // obRoutine >= 4
    private boolean drivesBossPosition; // ROM: objoff_30 < 0 branch drives boss X/Y
    private int currentFrame;

    private final LevelManager levelManager;

    public FZCylinder(Sonic1FZBossInstance parent, LevelManager levelManager, int subtype) {
        super(parent, "FZ Cylinder " + subtype, 3, Sonic1ObjectIds.EGGMAN_CYLINDER);
        this.levelManager = levelManager;
        this.subtype = subtype;
        this.isBottom = subtype <= 2;

        // Initialize position from PosData
        int index = subtype >> 1;
        this.baseX = CYLINDER_POS[index][0];
        this.baseY = CYLINDER_POS[index][1];
        this.currentX = baseX;
        this.currentY = baseY;

        this.direction = 0;
        this.extensionFixed = 0;
        this.active = false;
        this.drivesBossPosition = false;
        this.currentFrame = 0;
    }

    /**
     * Activate this cylinder for extension.
     * @param dir -1 for bottom (extending down), +1 for top (extending up)
     */
    public void activate(int dir) {
        this.direction = dir;
        this.active = true;
        // ROM: The first selected cylinder gets objoff_30=-1 and drives parent X/Y.
        this.drivesBossPosition = dir < 0;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (!beginUpdate(frameCounter)) return;

        if (!active) {
            // ROM: Routine 2 — idle, clear extension
            extensionFixed = 0;
        } else {
            // ROM: Routine 4 — extending/retracting
            if (isBottom) {
                updateBottomCylinder();
            } else {
                updateTopCylinder();
            }
        }

        // Calculate display Y from base + extension offset
        // ROM: loc_1A4EA — objoff_38 + objoff_3C -> obY
        int extensionPixels = extensionFixed >> 16;
        currentY = baseY + extensionPixels;
        currentX = baseX;

        // ROM: loc_1A514 — host cylinder updates parent X/Y while active.
        if (active && drivesBossPosition) {
            Sonic1FZBossInstance fzParent = (Sonic1FZBossInstance) parent;
            int yOffset = isBottom ? -0xA : 0xE;
            fzParent.syncPositionFromCylinder(currentX, currentY + yOffset);
        }

        // Calculate frame (ROM: loc_1A524 - loc_1A55C)
        calculateFrame();

        updateDynamicSpawn();
    }

    /**
     * Bottom cylinder extension logic (subtypes 0-2).
     * ROM: loc_1A598 (retracting) / loc_1A5D4 (extending)
     */
    private void updateBottomCylinder() {
        Sonic1FZBossInstance fzParent = (Sonic1FZBossInstance) parent;

        if (direction == 0) {
            // Retracting (ROM: loc_1A598 — direction cleared, returning to base)
            if (fzParent.isBossDefeated()) {
                // ROM: BossDefeated — spawn explosions
                // ROM: subi.l #$10000,objoff_3C — counter-force
                extensionFixed -= 0x10000;
            }

            // ROM: addi.l #$20000,objoff_3C — retract toward zero
            extensionFixed += 0x20000;

            // ROM: bcc.s locret — check for overflow past zero
            if (extensionFixed >= 0) {
                // Retraction complete
                extensionFixed = 0;
                active = false;
                drivesBossPosition = false;
                fzParent.onCylinderDone();
            }
        } else {
            // Extending (ROM: loc_1A5D4)
            // ROM: cmpi.w #-$10,objoff_3C — boost when near limit
            if ((extensionFixed >> 16) <= -0x10) {
                extensionFixed -= 0x28000; // ROM: subi.l #$28000
            }

            // ROM: subi.l #$8000,objoff_3C — base extension speed
            extensionFixed -= 0x8000;

            // ROM: cmpi.w #-$A0,objoff_3C — check fully extended
            if ((extensionFixed >> 16) <= -0xA0) {
                extensionFixed = (-0xA0) << 16; // Clamp
                direction = 0; // ROM: clr.b objoff_29 — start retracting
            }
        }
    }

    /**
     * Top cylinder extension logic (subtypes 4-6).
     * ROM: loc_1A604 (retracting) / loc_1A646 (extending)
     */
    private void updateTopCylinder() {
        Sonic1FZBossInstance fzParent = (Sonic1FZBossInstance) parent;

        if (direction == 0) {
            // Retracting (ROM: loc_1A604)
            if (fzParent.isBossDefeated()) {
                // ROM: BossDefeated + addi.l #$10000
                extensionFixed += 0x10000;
            }

            // ROM: subi.l #$20000,objoff_3C — retract toward zero
            extensionFixed -= 0x20000;

            // ROM: bcc.s locret — check for underflow past zero
            if (extensionFixed <= 0) {
                extensionFixed = 0;
                active = false;
                drivesBossPosition = false;
                fzParent.onCylinderDone();
            }
        } else {
            // Extending (ROM: loc_1A646)
            // ROM: cmpi.w #$10,objoff_3C — boost when near limit
            if ((extensionFixed >> 16) >= 0x10) {
                extensionFixed += 0x28000; // ROM: addi.l #$28000
            }

            // ROM: addi.l #$8000,objoff_3C — base extension speed
            extensionFixed += 0x8000;

            // ROM: cmpi.w #$A0,objoff_3C — check fully extended
            if ((extensionFixed >> 16) >= 0xA0) {
                extensionFixed = 0xA0 << 16; // Clamp
                direction = 0; // ROM: clr.b objoff_29 — start retracting
            }
        }
    }

    /**
     * Calculate display frame from extension offset.
     * ROM: loc_1A524 - loc_1A55C
     *   Bottom: frame = (neg_offset - 8) >> 4, clamped 0-8
     *   Top: frame = (offset - $27) >> 4, clamped 0-8
     */
    private void calculateFrame() {
        int offset = extensionFixed >> 16;
        int frame = 0;

        if (offset < 0) {
            // Bottom cylinder frame calculation
            int negOffset = -offset;
            negOffset -= 8;
            if (negOffset > 0) {
                frame = 1 + (negOffset >> 4);
            }
        } else if (offset > 0) {
            // Top cylinder frame calculation
            offset -= 0x27;
            if (offset > 0) {
                frame = 1 + (offset >> 4);
            }
        }

        // Clamp frame to mapping range (0-10 for extension, 11 for control panel)
        if (frame > 10) frame = 10;
        currentFrame = frame;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null) return;

        PatternSpriteRenderer cylRenderer = renderManager.getRenderer(ObjectArtKeys.FZ_CYLINDER);
        if (cylRenderer == null || !cylRenderer.isReady()) return;

        // ROM: Top cylinders (subtype > 2) have bset #1,obRender — vertical flip
        // _incObj/84 line 49-51: cmpi.b #2,obSubtype / ble.s / bset #1,obRender
        cylRenderer.drawFrameIndex(currentFrame, currentX, currentY, false, !isBottom);

        // Also draw control panel (frame 11) at base position for bottom cylinders
        // ROM: This is part of the boss's sub-object rendering
    }

    @Override
    public int getPriorityBucket() {
        return 3; // ROM: obPriority = 3
    }
}
