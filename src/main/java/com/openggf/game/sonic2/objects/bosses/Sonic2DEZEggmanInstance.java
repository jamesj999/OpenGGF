package com.openggf.game.sonic2.objects.bosses;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
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

    /** ROM: move.w #$3F8,x_pos(a1) — solid wall child spawn X (ObjC6_State2_State1) */
    private static final int WALL_X = 0x3F8;
    /** ROM: move.w #$160,y_pos(a1) — solid wall child spawn Y (ObjC6_State2_State1) */
    private static final int WALL_Y = 0x160;
    /** ROM: solid wall half-width = $13 (19px) */
    private static final int WALL_HALF_WIDTH = 0x13;
    /** ROM: solid wall half-height = $20 (32px) */
    private static final int WALL_HALF_HEIGHT = 0x20;

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

    // Exhaust puff timer (ROM: loc_3CFB0 area, ObjC6 subtype $AA)
    private int puffTimer;

    // Reference to the Death Egg Robot for boarding signal
    private Sonic2DeathEggRobotInstance deathEggRobot;

    // Barrier wall child (ObjC6 subtype $A8)
    private BarrierWall barrierWall;

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    /**
     * Create a new DEZ Eggman transition object.
     *
     * @param spawnX initial X position (ROM layout: $440)
     * @param spawnY initial Y position (ROM layout: $168)
     */
    public Sonic2DEZEggmanInstance(int spawnX, int spawnY) {
        super(new ObjectSpawn(spawnX, spawnY, 0xC6, 0xA6, 0, false, 0), "DEZ Eggman");
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
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
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
     * ROM: ObjC6_State2_State1 — spawn solid wall child at ($3F8, $160), advance.
     */
    private void updateInit() {
        // ROM: Spawn solid wall child (ObjC6 subtype $A8) at ($3F8, $160)
        // using ObjC6_MapUnc_3D1DE (construction stripes), priority 1,
        // solid dimensions: half-width=$13, half-height=$20/$20.
        // This blocks the player from running past Eggman.
        barrierWall = new BarrierWall(WALL_X, WALL_Y);
        if (GameServices.level() != null && services().objectManager() != null) {
            services().objectManager().addDynamicObject(barrierWall);
        }

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
        // TODO: ROM's Obj_GetOrientationToPlayer considers both players and picks
        // the closest. When 2P/sidekick support is added, also check the secondary
        // character and use the smaller |dx|.
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
        if (timer < 0) {
            routineSecondary = STATE_RUN;
            xVel = RUN_VELOCITY;
            currentFrame = RUNNING_FRAMES[0];
            animFrameIndex = 0;
            animTimer = RUNNING_ANIM_SPEED;
            puffTimer = 0x10;

            // ROM: When Eggman starts running, signal barrier wall to begin opening
            if (barrierWall != null) {
                barrierWall.signalEggmanRunning();
            }
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
        // ROM: cmpi.w #$810,x_pos(a0) — check jump threshold FIRST
        if (currentX >= JUMP_THRESHOLD_X) {
            // ROM: Always set jump frame and clear running velocity when threshold reached
            currentFrame = FRAME_JUMP;
            xVel = 0;

            // ROM: btst #render_flags.on_screen,render_flags(a0) — only transition when on-screen
            if (isOnScreenX()) {
                routineSecondary = STATE_JUMP;
                xVel = JUMP_X_VEL;
                yVel = JUMP_Y_VEL;
                timer = JUMP_TIMER;
                // ROM: bset #status.npc.p1_standing,status(a0) — signal boarding NOW
                signalBoarding();
            }
            // Return before puff timer — ROM does not decrement timer during $810 wait
            return;
        }

        // ROM: Keep Eggman ahead of the player (loc_3CFB0)
        // ROM: addi.w #$50,d2 / cmpi.w #$A0,d2 / blo.s — unsigned range check
        // Checks if (dx + $50) unsigned < $A0, equivalent to |dx| < $50
        if (player != null) {
            int dx = currentX - player.getCentreX();
            int shifted = dx + 0x50;
            if (shifted >= 0 && shifted < 0xA0) {
                currentX = player.getCentreX() + 0x50;
                xFixed = currentX << 16;
            }
        }

        // Exhaust puff timer (ROM: loc_3CFB0 area)
        puffTimer--;
        if (puffTimer < 0) {
            puffTimer = 0x20;
            // TODO: ROM spawns exhaust puff child (ObjC6 subtype $AA, frame 5,
            // x_vel=-$100, y_offset=-$18, timer=$08 [9 frames, bmi at -1],
            // y_vel=0 with gravity $10/frame)
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
        if (timer < 0) {
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
        if (GameServices.level() != null && services().objectManager() != null) {
            for (var obj : services().objectManager().getActiveObjects()) {
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
            lm = GameServices.level();
        } catch (Exception e) {
            return;
        }
        if (GameServices.level() == null) return;

        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) return;

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.DEZ_EGGMAN);
        if (renderer == null || !renderer.isReady()) return;

        // Eggman always faces right (running away from player)
        renderer.drawFrameIndex(currentFrame, currentX, currentY, false, false);
    }

    // ========================================================================
    // BARRIER WALL INNER CLASS (ObjC6 subtype $A8)
    // ========================================================================

    /**
     * Solid barrier wall spawned by Eggman at ($3F8, $160) during ObjC6_State2_State1.
     * ROM: Uses ObjC6_MapUnc_3D1DE (construction stripes), priority 1,
     * solid dimensions: half-width=$13, half-height=$20/$20.
     *
     * State machine:
     * - State1: Acts as solid wall. Polls parent Eggman's misc flag.
     *           When Eggman starts running, advances to State2.
     * - State2: Still solid. Plays opening animation (speed=1, frames {0,1,2,3},
     *           $FA terminator advances to State3).
     * - State3: Clears player pushing flag, deletes itself.
     */
    static class BarrierWall extends AbstractObjectInstance implements SolidObjectProvider {

        private static final int WALL_STATE_SOLID = 0;
        private static final int WALL_STATE_OPENING = 2;
        private static final int WALL_STATE_DELETE = 4;

        /** Opening animation: frames {0,1,2,3}, speed=1 (change every 2nd frame) */
        private static final int[] OPENING_FRAMES = { 0, 1, 2, 3 };
        private static final int OPENING_ANIM_SPEED = 1;

        private final int wallX;
        private final int wallY;
        private int wallState;
        private boolean eggmanRunning;

        // Opening animation state
        private int openingFrameIndex;
        private int openingAnimTimer;

        BarrierWall(int x, int y) {
            super(new ObjectSpawn(x, y, 0xC6, 0xA8, 0, false, 0), "DEZ Barrier Wall");
            this.wallX = x;
            this.wallY = y;
            this.wallState = WALL_STATE_SOLID;
            this.eggmanRunning = false;
            this.openingFrameIndex = 0;
            this.openingAnimTimer = OPENING_ANIM_SPEED;
        }

        /** Called by parent Eggman when he starts running */
        void signalEggmanRunning() {
            this.eggmanRunning = true;
        }

        @Override
        public int getX() {
            return wallX;
        }

        @Override
        public int getY() {
            return wallY;
        }

        @Override
        public boolean isPersistent() {
            return true;
        }

        @Override
        public int getPriorityBucket() {
            return 1; // ROM: priority 1
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
            if (isDestroyed()) return;

            switch (wallState) {
                case WALL_STATE_SOLID -> {
                    // Wait for Eggman's running signal
                    if (eggmanRunning) {
                        wallState = WALL_STATE_OPENING;
                        openingFrameIndex = 0;
                        openingAnimTimer = OPENING_ANIM_SPEED;
                    }
                }
                case WALL_STATE_OPENING -> {
                    // Play opening animation
                    openingAnimTimer--;
                    if (openingAnimTimer < 0) {
                        openingAnimTimer = OPENING_ANIM_SPEED;
                        openingFrameIndex++;
                        if (openingFrameIndex >= OPENING_FRAMES.length) {
                            // ROM: $FA terminator advances to State3
                            wallState = WALL_STATE_DELETE;
                        }
                    }
                }
                case WALL_STATE_DELETE -> {
                    // ROM: State3 — clear player pushing flag, delete self
                    if (player != null) {
                        player.setPushing(false);
                    }
                    setDestroyed(true);
                }
            }
        }

        @Override
        public SolidObjectParams getSolidParams() {
            return new SolidObjectParams(WALL_HALF_WIDTH, WALL_HALF_HEIGHT, WALL_HALF_HEIGHT);
        }

        @Override
        public boolean isSolidFor(PlayableEntity playerEntity) {
            AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
            return !isDestroyed() && wallState != WALL_STATE_DELETE;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed() || wallState == WALL_STATE_DELETE) return;

            LevelManager lm;
            try {
                lm = GameServices.level();
            } catch (Exception e) {
                return;
            }
            if (GameServices.level() == null) return;

            ObjectRenderManager renderManager = services().renderManager();
            if (renderManager == null) return;

            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.DEZ_WALL);
            if (renderer == null || !renderer.isReady()) return;

            // Frame 0 = full wall (4 segments), frames 1-3 = progressively smaller during opening
            int frame = (wallState == WALL_STATE_OPENING)
                    ? OPENING_FRAMES[Math.min(openingFrameIndex, OPENING_FRAMES.length - 1)]
                    : 0;
            renderer.drawFrameIndex(frame, wallX, wallY, false, false);
        }
    }
}
