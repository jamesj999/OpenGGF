package uk.co.jamesj999.sonic.game.sonic2.objects.bosses;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AudioConstants;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds;
import uk.co.jamesj999.sonic.game.sonic2.objects.EggPrisonObjectInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.ExplosionObjectInstance;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.boss.AbstractBossChild;
import uk.co.jamesj999.sonic.level.objects.boss.AbstractBossInstance;
import uk.co.jamesj999.sonic.level.objects.boss.BossChildComponent;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.physics.ObjectTerrainUtils;
import uk.co.jamesj999.sonic.physics.TerrainCheckResult;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * EHZ Act 2 Boss (Object 0x56) - Drill car boss with 6 child components.
 * ROM Reference: s2.asm:62743 (Obj56_Init)
 *
 * State Machine (routine_secondary):
 * - SUB0: Diagonal approach to X=0x29D0
 * - SUB2: Descend to Y=0x41E, wait 60 frames (with tertiary sub-states)
 * - SUB4: Active battle - oscillate between X boundaries
 * - SUB6: Defeated falling with explosions
 * - SUB8: Idle after fall (12 frames)
 * - SUBA: Flying off sequence (with tertiary sub-states)
 */
public class Sonic2EHZBossInstance extends AbstractBossInstance {

    // State machine constants
    private static final int SUB0_APPROACH_DIAGONAL = 0x00;
    private static final int SUB2_DESCEND_VERTICAL = 0x02;
    private static final int SUB4_ACTIVE_BATTLE = 0x04;
    private static final int SUB6_DEFEATED_FALLING = 0x06;
    private static final int SUB8_IDLE_POST_FALL = 0x08;
    private static final int SUBA_FLYING_OFF = 0x0A;

    // Position constants
    private static final int INITIAL_X = 0x29D0;
    private static final int INITIAL_Y = 0x0426;
    private static final int START_X = 0x2AF0;
    private static final int START_Y = 0x02F8;
    private static final int TARGET_Y = 0x041E;
    private static final int BOUNDARY_LEFT = 0x28A0;
    private static final int BOUNDARY_RIGHT = 0x2B08;
    private static final int CAMERA_MAX_X_TARGET = 0x2AB0;

    // Velocity constants (8.8 fixed-point)
    private static final int VELOCITY_LEFT = -0x200;
    private static final int VELOCITY_UP_FLEE = -1;
    private static final int VELOCITY_RIGHT_FLEE = 6;

    // Physics constants
    private static final int GRAVITY = 0x38; // ObjectMoveAndFall gravity (8.8)
    private static final int MAIN_Y_RADIUS = 0x14;

    // Timing constants
    private static final int DESCEND_WAIT_FRAMES = 60;
    private static final int POST_FALL_WAIT_FRAMES = 12;
    private static final int FLEE_UP_DURATION = 96;
    private static final int DEFEAT_TIMER_START = 0xB3;
    private static final int FLOOR_Y = 0x48C; // Boss floor during defeat

    // Custom memory offsets (objoff_XX pattern from ROM)
    private static final int OBJOFF_FLAGS = 0x2D;
    private static final int OBJOFF_WHEEL_Y_ACCUM = 0x2E;
    private static final int OBJOFF_INITIAL_X = 0x30;
    private static final int OBJOFF_INITIAL_Y = 0x38;

    // Bitflags for OBJOFF_FLAGS
    private static final int FLAG_GROUNDED = 0x01;
    private static final int FLAG_ACTIVE = 0x02;
    private static final int FLAG_FLYING_OFF = 0x04;
    private static final int FLAG_SPIKE_SEPARATED = 0x08;
    private static final int FLAG_FINISHED = 0x10;

    // Wheel Y accumulator
    private int wheelYAccumulator;
    private int waitTimer;
    private int defeatTimer;
    private int currentFrameCounter;

    public Sonic2EHZBossInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, levelManager, "EHZ Boss");
    }

    @Override
    protected void initializeBossState() {
        // Store initial position
        setCustomFlag(OBJOFF_INITIAL_X, INITIAL_X);
        setCustomFlag(OBJOFF_INITIAL_Y, INITIAL_Y);

        // CRITICAL: Initialize flags to 0 (no flags set during approach)
        setCustomFlag(OBJOFF_FLAGS, 0);

        // Initialize state machine
        state.routineSecondary = SUB0_APPROACH_DIAGONAL;
        state.routineTertiary = 0;

        // Initialize position
        state.x = START_X;
        state.y = START_Y;
        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;

        // Initialize wheel accumulator
        wheelYAccumulator = 0;
        waitTimer = 0;
        defeatTimer = 0;

        // Spawn child components
        spawnChildComponents();
    }

    private void spawnChildComponents() {
        List<AbstractBossChild> spawned = List.of(
                new EHZBossVehicleTop(this),
                new EHZBossGroundVehicle(this),
                new EHZBossPropeller(this),
                new EHZBossWheel(this, 0, 0x1C, 2),   // Left foreground: +28
                new EHZBossWheel(this, 1, -0x0C, 2),  // Right foreground: -12
                new EHZBossWheel(this, 2, -0x2C, 3),  // Back wheel: -44
                new EHZBossSpike(this)
        );

        for (AbstractBossChild child : spawned) {
            childComponents.add(child);
            if (levelManager.getObjectManager() != null) {
                levelManager.getObjectManager().addDynamicObject(child);
            }
        }
    }

    @Override
    protected void updateBossLogic(int frameCounter, AbstractPlayableSprite player) {
        currentFrameCounter = frameCounter;
        // Run state machine
        switch (state.routineSecondary) {
            case SUB0_APPROACH_DIAGONAL -> updateSub0ApproachDiagonal();
            case SUB2_DESCEND_VERTICAL -> updateSub2DescendVertical();
            case SUB4_ACTIVE_BATTLE -> updateSub4ActiveBattle();
            case SUB6_DEFEATED_FALLING -> updateSub6DefeatedFalling();
            case SUB8_IDLE_POST_FALL -> updateSub8IdlePostFall();
            case SUBA_FLYING_OFF -> updateSubAFlyingOff();
        }
    }

    // ROM: s2.asm:62922-62934 (loc_2F27C - SUB0: Approaching diagonally)
    private void updateSub0ApproachDiagonal() {
        // ROM: s2.asm:62926 - subi_.w #1,x_pos(a0)
        state.x--;
        // ROM: s2.asm:62927 - addi_.w #1,y_pos(a0)
        state.y++;
        syncFixedFromPosition();

        // ROM: s2.asm:62924 - cmpi.w #$29D0,x_pos(a0)
        if (state.x <= INITIAL_X) {
            state.x = INITIAL_X;
            syncFixedFromPosition();
            state.routineSecondary = SUB2_DESCEND_VERTICAL;
            state.routineTertiary = 0;
        }
    }

    // ROM: s2.asm:62937-62969 (loc_2F2A8 - SUB2: Descending vertically/waiting)
    private void updateSub2DescendVertical() {
        switch (state.routineTertiary) {
            case 0 -> {
                // ROM: s2.asm:62948-62959 (loc_2F2BA - Sub2_0: moving down)
                // ROM: s2.asm:62949 - cmpi.w #$41E,y_pos(a0)
                // Descending: move 1 pixel down per frame
                // ROM: s2.asm:62951 - addi_.w #1,y_pos(a0)
                state.y++;
                syncFixedFromPosition();

                if (state.y >= TARGET_Y) {
                    state.y = TARGET_Y;
                    syncFixedFromPosition();
                    // ROM: s2.asm:62956 - addq.b #2,objoff_2C(a0)
                    state.routineTertiary = 2;
                    // ROM: s2.asm:62958 - move.w #60,objoff_2A(a0)
                    waitTimer = DESCEND_WAIT_FRAMES;
                    // ROM: s2.asm:62957 - bset #0,objoff_2D(a0)
                    setCustomFlag(OBJOFF_FLAGS, getCustomFlag(OBJOFF_FLAGS) | FLAG_GROUNDED);
                }
            }
            case 2 -> {
                // ROM: s2.asm:62962-62969 (loc_2F2E0 - Sub2_2: waiting)
                // ROM: s2.asm:62963 - subi_.w #1,objoff_2A(a0)
                waitTimer--;
                if (waitTimer < 0) {
                    // ROM: s2.asm:62965 - move.w #-$200,x_vel(a0)
                    state.routineSecondary = SUB4_ACTIVE_BATTLE;
                    state.xVel = VELOCITY_LEFT;
                    // ROM: s2.asm:62968 - bset #1,objoff_2D(a0)
                    setCustomFlag(OBJOFF_FLAGS, getCustomFlag(OBJOFF_FLAGS) | FLAG_ACTIVE);
                }
            }
        }
    }

    // ROM: s2.asm:62972-62986 (loc_2F304 - SUB4: Moving back and forth)
    private void updateSub4ActiveBattle() {
        // ROM: s2.asm:62974 - bsr.w loc_2F484 (boundary check)
        if (state.x <= BOUNDARY_LEFT || state.x >= BOUNDARY_RIGHT) {
            state.renderFlags ^= 1;
            state.xVel = -state.xVel;
        }

        // ROM: s2.asm:62975-62978 - Calculate Y position from wheel support
        state.y = (wheelYAccumulator >> 1) - 0x14;
        state.yFixed = state.y << 16;
        wheelYAccumulator = 0;

        // ROM: s2.asm:62980-62985 - Apply velocity (16.16 fixed-point)
        state.xFixed += (state.xVel << 8);
        state.updatePositionFromFixed();
    }

    // ROM: s2.asm:62989-63007 (loc_2F336 - SUB6: Boss defeated, falling/lying on ground)
    private void updateSub6DefeatedFalling() {
        defeatTimer--;
        if (defeatTimer < 0) {
            state.xVel = 0;
            state.routineSecondary = SUB8_IDLE_POST_FALL;
            defeatTimer = -0x26;
            waitTimer = POST_FALL_WAIT_FRAMES;
            return;
        }

        if ((currentFrameCounter & 7) == 0) {
            spawnDefeatExplosion();
        }

        applyObjectMoveAndFall();
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(state.x, state.y, MAIN_Y_RADIUS);
        if (floor.hasCollision() && floor.distance() < 0) {
            state.y += floor.distance();
            state.yFixed = state.y << 16;
            state.yVel = 0;
        }
    }

    // ROM: s2.asm:63010-63015 (loc_2F374 - SUB8: Boss idle for $C frames)
    private void updateSub8IdlePostFall() {
        // ROM: s2.asm:63011 - subq.w #1,objoff_2A(a0)
        waitTimer--;
        if (waitTimer < 0) {
            // ROM: s2.asm:63013 - addq.b #2,routine_secondary(a0)
            state.routineSecondary = SUBA_FLYING_OFF;
            // ROM: s2.asm:63014 - move.b #0,objoff_2C(a0)
            state.routineTertiary = 0;
        }
    }

    // ROM: s2.asm:63018-63100 (loc_2F38A - SUBA: Flying off, moving camera)
    private void updateSubAFlyingOff() {
        switch (state.routineTertiary) {
            case 0 -> {
                // ROM: s2.asm:63031-63057 (loc_2F3A2 - SubA_0: Initialize propeller)
                // ROM: s2.asm:63032 - bclr #0,objoff_2D(a0)
                // Clear grounded flag
                setCustomFlag(OBJOFF_FLAGS, getCustomFlag(OBJOFF_FLAGS) & ~FLAG_GROUNDED);
                reloadPropeller();

                state.routineTertiary = 2;
                // ROM: s2.asm:63052 - move.w #$32,objoff_2A(a0)
                waitTimer = 0x32;
                AudioManager.getInstance().playMusic(Sonic2AudioConstants.MUS_EMERALD_HILL);
            }
            case 2 -> {
                // ROM: s2.asm:63060-63068 (loc_2F424 - SubA_2: Waiting)
                // ROM: s2.asm:63061 - subi_.w #1,objoff_2A(a0)
                waitTimer--;
                if (waitTimer < 0) {
                    state.routineTertiary = 4;
                    // ROM: s2.asm:63063 - bset #2,objoff_2D(a0)
                    setCustomFlag(OBJOFF_FLAGS, getCustomFlag(OBJOFF_FLAGS) | FLAG_FLYING_OFF);
                    // ROM: s2.asm:63064 - move.w #$60,objoff_2A(a0)
                    waitTimer = FLEE_UP_DURATION;
                }
            }
            case 4 -> {
                // ROM: s2.asm:63071-63087 (loc_2F442 - SubA_4: Flying off)
                waitTimer--;
                if (waitTimer < 0) {
                    state.renderFlags |= 1;
                    state.x += VELOCITY_RIGHT_FLEE;
                } else {
                    state.y += VELOCITY_UP_FLEE;
                }
                syncFixedFromPosition();

                Camera camera = Camera.getInstance();
                if (camera.getMaxX() < CAMERA_MAX_X_TARGET) {
                    camera.setMaxXTarget((short) (camera.getMaxX() + 2));
                } else if (!isOnScreen()) {
                    // Don't spawn EggPrison - level already has one at end
                    // Don't destroy boss - let ground parts persist
                    // Mark as finished so we stop rendering flying parts
                    setCustomFlag(OBJOFF_FLAGS, getCustomFlag(OBJOFF_FLAGS) | FLAG_FINISHED);
                }
            }
        }
    }

    private void syncFixedFromPosition() {
        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;
    }

    private void applyObjectMoveAndFall() {
        state.xFixed += (state.xVel << 8);
        state.yFixed += (state.yVel << 8);
        state.yVel += GRAVITY;
        state.updatePositionFromFixed();
    }

    private void spawnDefeatExplosion() {
        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        int random = ThreadLocalRandom.current().nextInt(0x10000);
        int xOffset = ((random & 0xFF) >> 2) - 0x20;
        int yOffset = (((random >> 8) & 0xFF) >> 2) - 0x20;
        ExplosionObjectInstance explosion = new ExplosionObjectInstance(
                Sonic2ObjectIds.BOSS_EXPLOSION,
                state.x + xOffset,
                state.y + yOffset,
                renderManager);
        levelManager.getObjectManager().addDynamicObject(explosion);
    }

    private void reloadPropeller() {
        for (BossChildComponent child : childComponents) {
            if (child instanceof EHZBossPropeller propeller && !propeller.isDestroyed()) {
                propeller.reload();
                return;
            }
        }
        EHZBossPropeller propeller = new EHZBossPropeller(this);
        childComponents.add(propeller);
        if (levelManager.getObjectManager() != null) {
            levelManager.getObjectManager().addDynamicObject(propeller);
        }
    }

    private void spawnEggPrison() {
        if (levelManager.getObjectManager() == null) {
            return;
        }
        ObjectSpawn prisonSpawn = new ObjectSpawn(
                INITIAL_X,
                FLOOR_Y - 0x20,
                Sonic2ObjectIds.EGG_PRISON,
                0,
                0,
                false,
                0);
        EggPrisonObjectInstance prisonInstance = new EggPrisonObjectInstance(prisonSpawn, "Egg Prison");
        levelManager.getObjectManager().addDynamicObject(prisonInstance);
    }

    @Override
    protected int getInitialHitCount() {
        return 8; // Standard Sonic 2 boss hit count
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        // No-op: spike separation handled by spike component.
    }

    @Override
    protected int getCollisionSizeIndex() {
        // ROM: s2.asm:62753 - move.b #$F,collision_flags(a0)
        // Touch_Sizes table index $0F (s2.asm:84590): 24x24 pixels
        return 0x0F;
    }

    @Override
    protected boolean usesDefeatSequencer() {
        return false;
    }

    @Override
    protected void onDefeatStarted() {
        // ROM: s2.asm:63149-63162 (loc_2F4EE - boss defeated)
        // ROM: s2.asm:63152 - move.b #6,routine_secondary(a0)
        state.routineSecondary = SUB6_DEFEATED_FALLING;
        // ROM: s2.asm:63154 - move.w #-$180,y_vel(a0)
        state.yVel = -0x180;
        // ROM: s2.asm:63153 - move.w #0,x_vel(a0)
        state.xVel = 0;
        defeatTimer = DEFEAT_TIMER_START;

        // Clear FLAG_ACTIVE and set FLAG_SPIKE_SEPARATED
        int flags = getCustomFlag(OBJOFF_FLAGS);
        flags &= ~FLAG_ACTIVE;  // Clear FLAG_ACTIVE (boss no longer active)
        flags |= FLAG_SPIKE_SEPARATED;  // Set spike separated flag
        setCustomFlag(OBJOFF_FLAGS, flags);

        for (BossChildComponent child : childComponents) {
            if (child instanceof EHZBossVehicleTop top) {
                top.setFlyingOff();
                break;
            }
        }
    }

    /**
     * Called by wheels to contribute their Y position.
     */
    public void addToWheelYAccumulator(int wheelY) {
        wheelYAccumulator += wheelY;
    }

    public int getInitialY() {
        return INITIAL_Y;
    }

    @Override
    public int getCollisionFlags() {
        // ROM: SUB0 sets collision_flags to 0 every frame (s2.asm:62922)
        // ROM: SUB2 doesn't change collision_flags (stays 0)
        // ROM: SUB2→SUB4 transition sets collision_flags to $F (s2.asm:62992)
        // ROM: SUB4 keeps collision_flags at $F unless hit

        // Explicitly check state - no collision during defeat sequence
        if (state.routineSecondary >= 0x06) {  // SUB6 or later (defeated/fleeing)
            return 0;
        }

        // Check defeated FIRST
        if (state.defeated) {
            return 0;
        }

        // Check invulnerable
        if (state.invulnerable) {
            return 0;
        }

        // Check FLAG_ACTIVE
        int flags = getCustomFlag(OBJOFF_FLAGS);
        boolean active = (flags & FLAG_ACTIVE) != 0;

        if (!active) {
            return 0;
        }

        // Active and vulnerable - enable collision
        return 0xC0 | (getCollisionSizeIndex() & 0x3F);
    }

    @Override
    public int getPriorityBucket() {
        return 4;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Don't render main body if finished/off-screen
        if ((getCustomFlag(OBJOFF_FLAGS) & FLAG_FINISHED) != 0) {
            return;
        }

        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getEHZBossRenderer();
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Render main flying vehicle bottom (frame 15)
        boolean flipped = (state.renderFlags & 1) != 0;
        renderer.drawFrameIndex(15, state.x, state.y, flipped, false);
    }
}
