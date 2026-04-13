package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;
import java.util.logging.Logger;

/**
 * Obj_RobotnikShip2 (subtype 5) — Eggman's ship cockpit for the HCZ2 end boss.
 * ROM: sonic3k.asm line 136355.
 *
 * <p>Follows the parent boss at offset (0, +0x0C) each frame, rendering
 * Map_RobotnikShip frame 5 (the full ship body). Spawns an inline Eggman
 * head that animates between frames 0/1, shows frame 2 on hit, and frame 3
 * on defeat.
 *
 * <p>State machine (5 routines, stride 2):
 * <ol start="0">
 *   <li>INIT (0): set mapping_frame = subtype (5). Advance to MAIN.</li>
 *   <li>MAIN (2): follow parent position each frame.</li>
 *   <li>WAIT_DEFEAT (4): wait for parent defeatSignal (bit 4 of $38).</li>
 *   <li>READY (6): rise toward Camera_Y + 0x40. When reached, set x_vel = 0x300.</li>
 *   <li>ESCAPE (8): fly offscreen rightward. Timer 0x100, then delete.</li>
 * </ol>
 *
 * <p>The Eggman head (Obj_RobotnikHead, line 136028) is rendered inline at
 * offset (0, -0x1C) from the ship position. It animates frames 0–1 at delay 5,
 * shows frame 2 when the boss is hurt, and frame 3 when defeated.
 */
public class HczEndBossRobotnikShip extends AbstractBossChild {
    private static final Logger LOG = Logger.getLogger(HczEndBossRobotnikShip.class.getName());

    // =========================================================================
    // State machine routines (ROM: stride 2)
    // =========================================================================
    private static final int ROUTINE_INIT = 0;
    private static final int ROUTINE_MAIN = 2;
    private static final int ROUTINE_WAIT_DEFEAT = 4;
    private static final int ROUTINE_READY = 6;
    private static final int ROUTINE_ESCAPE = 8;

    // =========================================================================
    // Ship constants
    // =========================================================================
    /** Map_RobotnikShip frame index for subtype 5 (full ship body). */
    private static final int SHIP_FRAME = 5;
    /** Offset from boss center to ship position. */
    private static final int CHILD_OFFSET_X = 0;
    private static final int CHILD_OFFSET_Y = 0x0C;

    // =========================================================================
    // Head constants (Obj_RobotnikHead, ROM line 136028)
    // =========================================================================
    /** Offset from ship position to head position. */
    private static final int HEAD_OFFSET_X = 0;
    private static final int HEAD_OFFSET_Y = -0x1C;
    /** Normal head animation: alternates frames 0 and 1. */
    private static final int HEAD_FRAME_IDLE_A = 0;
    private static final int HEAD_FRAME_IDLE_B = 1;
    /** Head frame when boss is hurt (invulnerable). */
    private static final int HEAD_FRAME_HURT = 2;
    /** Head frame when boss is defeated. */
    private static final int HEAD_FRAME_DEFEATED = 3;
    /** Animation delay between idle frames (ROM: $1D duration = 5). */
    private static final int HEAD_ANIM_DELAY = 5;

    // =========================================================================
    // Escape constants (ROM: Routine 6 and 8)
    // =========================================================================
    /** Target Y = Camera_Y + 0x40 during READY rise. */
    private static final int READY_TARGET_OFFSET = 0x40;
    /** Rise velocity during READY phase. */
    private static final int READY_RISE_VEL = -0x100;
    /** Horizontal escape velocity (ROM: x_vel = $300). */
    private static final int ESCAPE_X_VEL = 0x300;
    /** Escape timer before self-deletion (ROM: 0x100 frames). */
    private static final int ESCAPE_TIMER = 0x100;

    // =========================================================================
    // Instance state
    // =========================================================================
    private final HczEndBossInstance boss;
    private int routine;

    /** Subpixel-precision position for smooth movement during escape. */
    private int xFixed;
    private int yFixed;
    private int xVel;
    private int yVel;
    private int timer;

    /** Head animation counter. */
    private int headAnimCounter;
    /** Current head idle frame (0 or 1). */
    private int headIdleFrame;

    // =========================================================================
    // Constructor
    // =========================================================================

    public HczEndBossRobotnikShip(HczEndBossInstance boss) {
        super(boss, "HCZEndBossRobotnikShip", 3, 0);
        this.boss = boss;
        this.routine = ROUTINE_INIT;
        this.headAnimCounter = 0;
        this.headIdleFrame = HEAD_FRAME_IDLE_A;
    }

    // =========================================================================
    // Update
    // =========================================================================

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (!beginUpdate(frameCounter)) {
            return;
        }

        switch (routine) {
            case ROUTINE_INIT -> updateInit();
            case ROUTINE_MAIN -> updateMain();
            case ROUTINE_WAIT_DEFEAT -> updateWaitDefeat();
            case ROUTINE_READY -> updateReady();
            case ROUTINE_ESCAPE -> updateEscape();
            default -> { }
        }

        updateHeadAnimation();
        updateDynamicSpawn();
    }

    // =========================================================================
    // Routine handlers
    // =========================================================================

    /**
     * ROM routine 0: Init — set mapping_frame = subtype (5), advance to MAIN.
     */
    private void updateInit() {
        // Follow parent immediately
        currentX = boss.getState().x + CHILD_OFFSET_X;
        currentY = boss.getState().y + CHILD_OFFSET_Y;
        xFixed = currentX << 16;
        yFixed = currentY << 16;
        routine = ROUTINE_MAIN;
    }

    /**
     * ROM routine 2: Main — follow parent position each frame.
     * Transitions to WAIT_DEFEAT when defeatSignal is set.
     */
    private void updateMain() {
        currentX = boss.getState().x + CHILD_OFFSET_X;
        currentY = boss.getState().y + CHILD_OFFSET_Y;
        xFixed = currentX << 16;
        yFixed = currentY << 16;

        if (boss.isDefeatSignal()) {
            routine = ROUTINE_WAIT_DEFEAT;
        }
    }

    /**
     * ROM routine 4: Wait for defeat — continues following parent, waits for
     * defeat signal to stabilize (boss explosion phase complete).
     * ROM: checks bit 4 of $38 (defeatSignal). When set, advance to READY.
     *
     * <p>Since the boss sets defeatSignal in onDefeatStarted(), we transition
     * immediately. The ship continues to follow the parent during the defeat
     * explosion phase, then detaches to rise when the boss starts fleeing.
     */
    private void updateWaitDefeat() {
        currentX = boss.getState().x + CHILD_OFFSET_X;
        currentY = boss.getState().y + CHILD_OFFSET_Y;
        xFixed = currentX << 16;
        yFixed = currentY << 16;

        // Transition to READY when parent has started fleeing
        // (i.e., the defeat explosion phase is done and the boss body is fleeing)
        if (boss.getState().routine >= 16) { // ROUTINE_FLEE = 16
            xVel = 0;
            yVel = READY_RISE_VEL;
            routine = ROUTINE_READY;
        }
    }

    /**
     * ROM routine 6: Ready — rise upward toward Camera_Y + 0x40.
     * When the target Y is reached, set x_vel = 0x300 and advance to ESCAPE.
     */
    private void updateReady() {
        // Apply velocity
        yFixed += yVel << 8;
        currentY = yFixed >> 16;

        int targetY = services().camera().getY() + READY_TARGET_OFFSET;
        if (currentY <= targetY) {
            currentY = targetY;
            yFixed = currentY << 16;
            xVel = ESCAPE_X_VEL;
            yVel = 0;
            timer = ESCAPE_TIMER;
            routine = ROUTINE_ESCAPE;
        }
    }

    /**
     * ROM routine 8: Escape — fly offscreen rightward. Timer 0x100, then delete.
     */
    private void updateEscape() {
        // Apply velocity
        xFixed += xVel << 8;
        currentX = xFixed >> 16;

        timer--;
        if (timer <= 0) {
            setDestroyed(true);
        }
    }

    // =========================================================================
    // Head animation (Obj_RobotnikHead logic, inline)
    // =========================================================================

    /**
     * Updates the Eggman head animation state.
     * ROM: Obj_RobotnikHead alternates frames 0/1 at delay 5 normally,
     * shows frame 2 when boss is hurt, frame 3 when defeated.
     */
    private void updateHeadAnimation() {
        if (boss.getState().defeated) {
            // Defeated: lock to frame 3
            headIdleFrame = HEAD_FRAME_DEFEATED;
            return;
        }

        if (boss.getState().invulnerable) {
            // Hurt: show frame 2 while invulnerable
            headIdleFrame = HEAD_FRAME_HURT;
            headAnimCounter = 0;
            return;
        }

        // Normal idle: alternate frames 0 and 1
        headAnimCounter++;
        if (headAnimCounter >= HEAD_ANIM_DELAY) {
            headAnimCounter = 0;
            headIdleFrame = (headIdleFrame == HEAD_FRAME_IDLE_A)
                    ? HEAD_FRAME_IDLE_B
                    : HEAD_FRAME_IDLE_A;
        }
    }

    /**
     * Returns the current head frame index for rendering.
     */
    private int getHeadFrame() {
        if (boss.getState().defeated) {
            return HEAD_FRAME_DEFEATED;
        }
        if (boss.getState().invulnerable) {
            return HEAD_FRAME_HURT;
        }
        return headIdleFrame;
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }

        PatternSpriteRenderer shipRenderer = getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        if (shipRenderer == null || !shipRenderer.isReady()) {
            return;
        }

        // Ship body: frame 5 at ship position
        shipRenderer.drawFrameIndex(SHIP_FRAME, currentX, currentY, false, false);

        // Eggman head: at offset from ship position
        int headX = currentX + HEAD_OFFSET_X;
        int headY = currentY + HEAD_OFFSET_Y;
        shipRenderer.drawFrameIndex(getHeadFrame(), headX, headY, false, false);
    }
}
