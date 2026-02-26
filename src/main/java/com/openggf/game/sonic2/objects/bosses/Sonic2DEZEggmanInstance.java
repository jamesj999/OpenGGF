package com.openggf.game.sonic2.objects.bosses;

import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * DEZ Eggman Transition Object (ObjC6 State2).
 * ROM Reference: s2.asm:81568-81696 (ObjC6, State2 sub-states)
 *
 * Robotnik runs from the Silver Sonic arena window area to the Death Egg Robot
 * cockpit, then jumps in. This bridges the gap between the two DEZ boss fights.
 *
 * State Machine (routine_secondary, 5 sub-states):
 * - 0: Init — set position, advance immediately
 * - 2: WaitForPlayer — check proximity (|distance| < $5C), then show surprised frame
 * - 4: Pause — wait $18 frames, then start running
 * - 6: RunRight — x_vel=$200, animate running (frames 2,3,4), until X >= $810
 * - 8: JumpIntoCockpit — x_vel=$80, y_vel=-$200, gravity $10/frame, $50 frame timer
 *
 * Animation: Ani_objC5_objC6 anim 0 = speed 5, frames {2, 3, 4} looping.
 * Art: Combined RobotnikUpper + RobotnikRunning + RobotnikLower, mappings ObjC6_MapUnc_3D0EE.
 */
public class Sonic2DEZEggmanInstance extends AbstractObjectInstance {

    // ========================================================================
    // STATE CONSTANTS
    // ========================================================================

    private static final int STATE_INIT = 0;
    private static final int STATE_WAIT_PLAYER = 2;
    private static final int STATE_PAUSE = 4;
    private static final int STATE_RUN = 6;
    private static final int STATE_JUMP = 8;

    // ========================================================================
    // POSITION & VELOCITY CONSTANTS (from ROM)
    // ========================================================================

    /** ROM: move.w #$3F8,x_pos(a1) */
    private static final int SPAWN_X = 0x3F8;
    /** ROM: move.w #$160,y_pos(a1) */
    private static final int SPAWN_Y = 0x160;

    /** ROM: addi.w #$5C,d2 / cmpi.w #$B8,d2 — proximity check radius */
    private static final int PROXIMITY_RADIUS = 0x5C;

    /** ROM: move.w #$18,objoff_2A(a0) — pause duration */
    private static final int PAUSE_TIMER = 0x18;

    /** ROM: move.w #$200,x_vel(a0) — running speed (8.8 fixed point: 2.0 px/frame) */
    private static final int RUN_VELOCITY = 0x200;

    /** ROM: cmpi.w #$810,x_pos(a0) — X threshold to start jump */
    private static final int JUMP_THRESHOLD_X = 0x810;

    /** ROM: move.w #$80,x_vel(a0) — jump horizontal speed */
    private static final int JUMP_X_VEL = 0x80;

    /** ROM: move.w #-$200,y_vel(a0) — jump initial vertical speed */
    private static final int JUMP_Y_VEL = -0x200;

    /** ROM: addi.w #$10,y_vel(a0) — gravity per frame */
    private static final int GRAVITY = 0x10;

    /** ROM: move.w #$50,objoff_2A(a0) — jump duration */
    private static final int JUMP_TIMER = 0x50;

    // ========================================================================
    // MAPPING FRAME INDICES (from ObjC6_MapUnc_3D0EE / objC6_a.asm)
    // ========================================================================

    /** Frame 0: Standing/facing player */
    private static final int FRAME_STANDING = 0;
    /** Frame 1: Surprised/startled */
    private static final int FRAME_SURPRISED = 1;
    /** Frame 2: Jumping pose */
    private static final int FRAME_JUMP = 2;
    /** Frames 2-4: Running cycle (used by Ani_objC5_objC6 anim 0) */
    private static final int[] RUNNING_FRAMES = { 2, 3, 4 };
    /** ROM: Ani_objC5_objC6 anim 0 speed = 5 (change every 6th frame) */
    private static final int RUNNING_ANIM_SPEED = 5;

    // ========================================================================
    // INTERNAL STATE
    // ========================================================================

    private int routineSecondary;
    private int currentX;
    private int currentY;
    private int xFixed; // 16.16 fixed-point X
    private int yFixed; // 16.16 fixed-point Y
    private int xVel;   // 8.8 velocity
    private int yVel;   // 8.8 velocity
    private int timer;
    private int currentFrame;

    // Animation state
    private int animFrameIndex; // Index into RUNNING_FRAMES
    private int animTimer;      // Countdown for frame changes

    // Reference to the Death Egg Robot for boarding signal
    private Sonic2DeathEggRobotInstance deathEggRobot;

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    /**
     * Create a new DEZ Eggman transition object.
     *
     * @param spawnX initial X position (ROM: $3F8)
     * @param spawnY initial Y position (ROM: $160)
     */
    public Sonic2DEZEggmanInstance(int spawnX, int spawnY) {
        super(new ObjectSpawn(spawnX, spawnY, 0xC6, 0xA4, 0, false, 0), "DEZ Eggman");
        this.currentX = spawnX;
        this.currentY = spawnY;
        this.xFixed = spawnX << 16;
        this.yFixed = spawnY << 16;
        this.xVel = 0;
        this.yVel = 0;
        this.routineSecondary = STATE_INIT;
        this.currentFrame = FRAME_STANDING;
        this.animFrameIndex = 0;
        this.animTimer = RUNNING_ANIM_SPEED;
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public boolean isPersistent() {
        return true; // Must stay active across screen transitions
    }

    @Override
    public int getPriorityBucket() {
        return 5; // Same priority as Robotnik in other boss contexts
    }

    /**
     * Set the Death Egg Robot reference so we can signal boarding.
     */
    public void setDeathEggRobot(Sonic2DeathEggRobotInstance robot) {
        this.deathEggRobot = robot;
    }

    // ========================================================================
    // UPDATE
    // ========================================================================

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) return;

        switch (routineSecondary) {
            case STATE_INIT -> updateInit();
            case STATE_WAIT_PLAYER -> updateWaitPlayer(player);
            case STATE_PAUSE -> updatePause();
            case STATE_RUN -> updateRun(player);
            case STATE_JUMP -> updateJump();
        }
    }

    /**
     * State 0: Init.
     * ROM: ObjC6_State2_State1 — spawn child, set position, advance.
     * We skip child spawning (parent object handles that in ROM) and just advance.
     */
    private void updateInit() {
        routineSecondary = STATE_WAIT_PLAYER;
        currentFrame = FRAME_STANDING;
    }

    /**
     * State 2: Wait for player proximity.
     * ROM: ObjC6_State2_State2 — Obj_GetOrientationToPlayer, check |distance| < $5C.
     * When triggered, show surprised frame and set pause timer.
     */
    private void updateWaitPlayer(AbstractPlayableSprite player) {
        if (player == null) return;

        int dx = currentX - player.getCentreX();
        // ROM: addi.w #$5C,d2 / cmpi.w #$B8,d2 / blo.s
        // This checks if (dx + $5C) unsigned < $B8, equivalent to |dx| < $5C
        int shifted = dx + PROXIMITY_RADIUS;
        if (shifted >= 0 && shifted < (PROXIMITY_RADIUS * 2)) {
            // Player is close enough — show surprised reaction
            routineSecondary = STATE_PAUSE;
            timer = PAUSE_TIMER;
            currentFrame = FRAME_SURPRISED;
        }
    }

    /**
     * State 4: Pause (surprised reaction).
     * ROM: ObjC6_State2_State3 — count down $18 frames, then start running.
     */
    private void updatePause() {
        timer--;
        if (timer <= 0) {
            routineSecondary = STATE_RUN;
            xVel = RUN_VELOCITY;
            currentFrame = RUNNING_FRAMES[0];
            animFrameIndex = 0;
            animTimer = RUNNING_ANIM_SPEED;
        }
    }

    /**
     * State 6: Run right.
     * ROM: ObjC6_State2_State4 — move at x_vel=$200, animate (frames 2,3,4).
     * When X >= $810, transition to jump and signal boarding.
     *
     * ROM: At loc_3CFC0, when x_pos >= $810, the boarding signal is set
     * (bset #status.npc.p1_standing) at the START of the jump, not when
     * the jump completes. ObjC7's head child polls this flag.
     */
    private void updateRun(AbstractPlayableSprite player) {
        // ROM: cmpi.w #$810,x_pos(a0) — check jump threshold
        // ROM: btst #render_flags.on_screen,render_flags(a0) — only advance when on-screen
        if (currentX >= JUMP_THRESHOLD_X && isOnScreen()) {
            // Reached cockpit area — start jump
            routineSecondary = STATE_JUMP;
            currentFrame = FRAME_JUMP;
            xVel = JUMP_X_VEL;
            yVel = JUMP_Y_VEL;
            timer = JUMP_TIMER;
            // ROM: bset #status.npc.p1_standing,status(a0) — signal boarding NOW
            signalBoarding();
            return;
        }

        // ROM: Keep Eggman ahead of the player (loc_3CFB0)
        // When player catches up within $50 pixels, snap Eggman to player.x + $50
        if (player != null) {
            int dx = currentX - player.getCentreX();
            if (dx < 0x50) {
                currentX = player.getCentreX() + 0x50;
                xFixed = currentX << 16;
            }
        }

        // Apply movement (ObjectMove)
        applyVelocity();

        // Animate running cycle (Ani_objC5_objC6 anim 0: speed 5, frames 2,3,4)
        animTimer--;
        if (animTimer < 0) {
            animTimer = RUNNING_ANIM_SPEED;
            animFrameIndex++;
            if (animFrameIndex >= RUNNING_FRAMES.length) {
                animFrameIndex = 0;
            }
            currentFrame = RUNNING_FRAMES[animFrameIndex];
        }
    }



    /**
     * State 8: Jump into cockpit.
     * ROM: ObjC6_State2_State5 — apply gravity, count down $50 frames.
     * Boarding was already signaled at the start of the jump.
     * On timer expiry, despawn.
     */
    private void updateJump() {
        timer--;
        if (timer <= 0) {
            setDestroyed(true);
            return;
        }

        // Apply gravity
        yVel += GRAVITY;

        // Apply movement
        applyVelocity();
    }

    /**
     * Apply velocity to fixed-point position and update pixel position.
     * Matches ROM ObjectMove pattern used by AbstractBossInstance.
     */
    private void applyVelocity() {
        xFixed += (xVel << 8);
        yFixed += (yVel << 8);
        currentX = xFixed >> 16;
        currentY = yFixed >> 16;
    }

    /**
     * Signal the Death Egg Robot that Eggman has boarded the cockpit.
     * ROM: bset #status.npc.p1_standing,status(a0) — sets a flag that ObjC7's
     * head child polls via (DEZ_Eggman).w reference.
     */
    private void signalBoarding() {
        if (deathEggRobot != null) {
            deathEggRobot.setEggmanBoarded();
            return;
        }
        // Fallback: search active objects for the Death Egg Robot
        LevelManager lm = LevelManager.getInstance();
        if (lm != null && lm.getObjectManager() != null) {
            for (var obj : lm.getObjectManager().getActiveObjects()) {
                if (obj instanceof Sonic2DeathEggRobotInstance robot) {
                    robot.setEggmanBoarded();
                    return;
                }
            }
        }
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;

        LevelManager lm;
        try {
            lm = LevelManager.getInstance();
        } catch (Exception e) {
            return;
        }
        if (lm == null) return;

        ObjectRenderManager renderManager = lm.getObjectRenderManager();
        if (renderManager == null) return;

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.DEZ_EGGMAN);
        if (renderer == null || !renderer.isReady()) return;

        // Eggman always faces right (running away from player)
        renderer.drawFrameIndex(currentFrame, currentX, currentY, false, false);
    }
}
