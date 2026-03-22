package com.openggf.game.sonic1.objects.bosses;

import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0x48 — Eggman's swinging ball on a chain (GHZ boss).
 * ROM: _incObj/48 Eggman's Swinging Ball.asm
 *
 * Creates a chain of 6 links + 1 ball. The ball swings on the chain
 * using sine/cosine positioning relative to the Eggman ship.
 *
 * Chain link positions are calculated via GBall_PosData offsets:
 * {0x00, 0x10, 0x20, 0x30, 0x40, 0x60}
 *
 * Swing mechanics (Obj48_Move):
 * - Initial angle: 0x4080 (word — high byte = angle byte)
 * - Swing parameter starts at -0x200, increments/decrements by 8/frame
 * - Direction toggles at +0x200 / -0x200
 * - angle += swingParam each frame
 *
 * The ball (last link) has collision type 0x81 (enemy, size index 1).
 */
public class GHZBossWreckingBall extends AbstractBossChild
        implements TouchResponseProvider {

    // Chain link Y-offset data (GBall_PosData)
    private static final int[] CHAIN_OFFSETS = {0x00, 0x10, 0x20, 0x30, 0x40, 0x60};
    private static final int CHAIN_LINK_COUNT = 6; // links (not counting ball)
    private static final int TOTAL_ELEMENTS = CHAIN_LINK_COUNT + 1; // 6 links + 1 ball

    // Swing constants
    private static final int INITIAL_ANGLE = 0x4080; // Word $4080 — high byte $40 = CalcSine angle
    private static final int INITIAL_SWING_PARAM = -0x200;
    private static final int SWING_INCREMENT = 8;
    private static final int SWING_LIMIT = 0x200;

    // Collision: obColType = $81 (enemy category $80, size index 1)
    private static final int BALL_COLLISION_FLAGS = 0x81;


    // Swing state
    private int angle;          // obAngle — swing angle (byte)
    private int swingParam;     // objoff_3E — swing velocity
    private boolean swingForward; // objoff_3D — direction toggle

    // Chain extension state (GBall_Base routine)
    private final int[] chainExtension; // objoff_3C per link — current extension toward target
    private boolean chainFullyExtended;

    // Vertical offset from parent (objoff_32) — grows from 0 to 0x20
    private int verticalOffset;

    // Anchor position (synced from parent ship's objoff_30/objoff_38)
    private int anchorX;
    private int anchorY;

    // Per-element positions for rendering
    private final int[] elementX;
    private final int[] elementY;

    // Ball frame toggle (alternates every frame per GBall_ChkVanish)
    private int ballFrame; // 0 or 1 (toggles between check1 and shiny)

    private boolean parentDefeated;

    public GHZBossWreckingBall(AbstractBossInstance parent) {
        super(parent, "GHZBall", 5, Sonic1ObjectIds.BOSS_BALL);
        

        // Initialize swing
        this.angle = INITIAL_ANGLE;
        this.swingParam = INITIAL_SWING_PARAM;
        this.swingForward = false; // starts going forward (incrementing)

        // Initialize chain extension
        this.chainExtension = new int[TOTAL_ELEMENTS];
        this.chainFullyExtended = false;
        this.verticalOffset = 0;

        // Initialize element positions
        this.elementX = new int[TOTAL_ELEMENTS];
        this.elementY = new int[TOTAL_ELEMENTS];

        this.ballFrame = 1; // Start on check1 frame (frame 1 in Map_GBall)
        this.parentDefeated = false;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (!shouldUpdate(frameCounter)) {
            return;
        }

        // Check if parent boss is defeated
        if (parent.getState().defeated || (parent.getState().renderFlags & 0x80) != 0) {
            // ROM: sub_17C2A checks obStatus(a1) bit 7 — if set, convert to explosion
            parentDefeated = true;
            setDestroyed(true);
            return;
        }

        // Sync anchor position from parent's fixed-point position
        anchorX = parent.getX();
        anchorY = parent.getY();

        // Extend chain (GBall_Base routine 2)
        if (!chainFullyExtended) {
            extendChain();
        }

        // Grow vertical offset from 0 to 0x20 (objoff_32)
        if (verticalOffset < 0x20) {
            verticalOffset++;
        }

        // Update swing
        if (chainFullyExtended) {
            updateSwing();
        }

        // Animate ball frame — toggles every frame (GBall_ChkVanish)
        // ROM: tst.b obFrame / bne .set0 / addq #1,d0 — frame 0→1, non-zero→0
        ballFrame = (ballFrame == 0) ? 1 : 0;

        // Calculate all element positions via sine/cosine
        calculateChainPositions();

        // Update our position to ball position (last element) for collision
        currentX = elementX[TOTAL_ELEMENTS - 1];
        currentY = elementY[TOTAL_ELEMENTS - 1];
        updateDynamicSpawn();
    }

    /**
     * Extend chain links toward their target offsets (GBall_Base).
     * Each link's extension grows by 1 per frame until it matches the target.
     */
    private void extendChain() {
        boolean allReached = true;
        for (int i = 0; i < TOTAL_ELEMENTS; i++) {
            int target = CHAIN_OFFSETS[Math.min(i, CHAIN_OFFSETS.length - 1)];
            if (chainExtension[i] < target) {
                chainExtension[i]++;
                allReached = false;
            }
        }

        // Check if fully extended AND parent is in combat state (ob2ndRout >= 6)
        if (allReached && parent.getState().routineSecondary >= 4) {
            chainFullyExtended = true;
        }
    }

    /**
     * Update swing angle (Obj48_Move).
     * ROM: Increments/decrements swingParam by 8 each frame,
     * adds swingParam to angle, toggles direction at limits.
     */
    private void updateSwing() {
        if (!swingForward) {
            // Going forward: increment swing param
            swingParam += SWING_INCREMENT;
            if (swingParam >= SWING_LIMIT) {
                swingForward = true; // toggle direction
            }
        } else {
            // Going backward: decrement swing param
            swingParam -= SWING_INCREMENT;
            if (swingParam <= -SWING_LIMIT) {
                swingForward = false; // toggle direction
            }
        }

        // Add swing param to angle (word addition, take low byte)
        angle = (angle + swingParam) & 0xFFFF;
    }

    /**
     * Calculate chain element positions via Swing_Move2.
     * ROM: CalcSine on angle byte, then for each element:
     *   Y = anchorY + verticalOffset + (sin * extension) >> 8
     *   X = anchorX + (cos * extension) >> 8
     */
    private void calculateChainPositions() {
        // Get byte angle (high byte of word, or just use low byte for CalcSine)
        int angleForSine = (angle >> 8) & 0xFF;

        int sinVal = TrigLookupTable.sinHex(angleForSine);
        int cosVal = TrigLookupTable.cosHex(angleForSine);

        int baseY = anchorY + verticalOffset;
        int baseX = anchorX;

        for (int i = 0; i < TOTAL_ELEMENTS; i++) {
            int ext = chainExtension[i];
            // ROM: muls.w d0,d4 / asr.l #8,d4 (sin * extension >> 8)
            int yOff = (sinVal * ext) >> 8;
            // ROM: muls.w d1,d5 / asr.l #8,d5 (cos * extension >> 8)
            int xOff = (cosVal * ext) >> 8;

            elementY[i] = baseY + yOff;
            elementX[i] = baseX + xOff;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (parentDefeated || isDestroyed()) {
            return;
        }

        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }

        boolean flipped = (parent.getState().renderFlags & 1) != 0;

        // Render chain links (elements 0-5) using swing GHZ chain link art
        PatternSpriteRenderer chainRenderer = renderManager.getRenderer(ObjectArtKeys.SWING_GHZ);
        if (chainRenderer != null && chainRenderer.isReady()) {
            for (int i = 0; i < CHAIN_LINK_COUNT; i++) {
                // Frame 1 = chain link in Map_Swing_GHZ
                // ROM: make_art_tile(ArtTile_GHZ_MZ_Swing,0,0) — palette 0 for boss chain
                chainRenderer.drawFrameIndex(1, elementX[i], elementY[i], flipped, false, 0);
            }
        }

        // Render ball (element 6) using GHZ ball art
        PatternSpriteRenderer ballRenderer = renderManager.getRenderer(ObjectArtKeys.BOSS_BALL);
        if (ballRenderer != null && ballRenderer.isReady()) {
            // Ball alternates between frames 0 (shiny) and 1 (check1)
            ballRenderer.drawFrameIndex(ballFrame, elementX[CHAIN_LINK_COUNT], elementY[CHAIN_LINK_COUNT],
                    flipped, false);
        }
    }

    // --- TouchResponseProvider for ball collision ---

    @Override
    public int getCollisionFlags() {
        if (parentDefeated || isDestroyed() || !chainFullyExtended) {
            return 0;
        }
        // ROM: obColType = $81 (enemy category, size index 1)
        return BALL_COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }
}
