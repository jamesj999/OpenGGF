package com.openggf.game.sonic2.objects.bosses;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.Sonic2LevelEventManager;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * WFZ Laser Platform Boss (Object 0xC5).
 * ROM Reference: s2.asm ObjC5
 *
 * Wing Fortress Zone boss. A hovering laser case that bounces horizontally,
 * opens to lower a laser shooter, fires a downward laser beam, and spawns
 * floating platforms. The boss arena also has Robotnik watching from a fixed
 * position with his own platform.
 *
 * State Machine (16 sub-routines):
 * - $00: Init - camera max Y=$442, store x_pos, bounds x +/- $60, HP=8
 * - $02: Wait for player within $40px horizontally
 * - $04: Spawn walls, laser shooter, platform releaser, Robotnik. Descend.
 * - $06: Descend. Timer expires -> timer=$60, play boss music.
 * - $08: Set direction toward player, signal platform releaser. Timer=$70.
 * - $0A: Bounce between left/right bounds. Timer expires -> open animation.
 * - $0C: Animate opening. On complete -> advance.
 * - $0E: Signal laser shooter to descend. Timer=$0E.
 * - $10: Move laser shooter down 1px/frame for 14 frames.
 * - $12: Enable collision ($06), timer=$40.
 * - $14: Wait for timer -> spawn laser.
 * - $16: Wait for laser signal. Bounce with laser. Timer=$80.
 * - $18: Stop laser. Retract shooter. Delete laser. Clear collision.
 * - $1A: Close animation.
 * - $1C: Loop back to $08.
 * - $1E: Defeat - explosions, $EF frames, then WFZ music, camera Y=$720, delete.
 *
 * 8 child object types (inner classes):
 * 1. LaserWall - solid wall at x +/- $88, y+$60
 * 2. PlatformReleaser - spawns platforms cyclically
 * 3. FloatingPlatform - player can stand on, oscillates
 * 4. PlatformHurt - invisible damage collider below platform
 * 5. LaserShooter - lowered/raised during laser phases
 * 6. Laser - extends downward in 5 stages
 * 7. WFZRobotnik - fixed position, watches fight
 * 8. RobotnikPlatform - follows Robotnik Y+$26
 */
public class Sonic2WFZBossInstance extends AbstractBossInstance {

    // State machine routine constants (16 sub-routines, $00-$1E)
    private static final int ROUTINE_INIT = 0x00;
    private static final int ROUTINE_WAIT_PLAYER = 0x02;
    private static final int ROUTINE_SPAWN_CHILDREN = 0x04;
    private static final int ROUTINE_DESCEND = 0x06;
    private static final int ROUTINE_SET_DIRECTION = 0x08;
    private static final int ROUTINE_BOUNCE = 0x0A;
    private static final int ROUTINE_OPEN_ANIM = 0x0C;
    private static final int ROUTINE_SIGNAL_SHOOTER = 0x0E;
    private static final int ROUTINE_LOWER_SHOOTER = 0x10;
    private static final int ROUTINE_ENABLE_COLLISION = 0x12;
    private static final int ROUTINE_SPAWN_LASER = 0x14;
    private static final int ROUTINE_BOUNCE_WITH_LASER = 0x16;
    private static final int ROUTINE_RETRACT_SHOOTER = 0x18;
    private static final int ROUTINE_CLOSE_ANIM = 0x1A;
    private static final int ROUTINE_LOOP = 0x1C;
    private static final int ROUTINE_DEFEAT = 0x1E;

    // Position and boundary constants
    /** Camera max Y during boss fight (ROM: move.w #$442,(Camera_Max_Y_pos).w) */
    private static final int CAMERA_MAX_Y_BOSS = 0x442;
    /** Camera max Y after defeat (ROM: move.w #$720,(Camera_Max_Y_pos).w) */
    private static final int CAMERA_MAX_Y_DEFEAT = 0x720;
    /** Left/right bound offset from spawn X (ROM: x +/- $60) */
    private static final int BOUND_OFFSET_X = 0x60;
    /** Wall offset from spawn X (ROM: x +/- $88) */
    private static final int WALL_OFFSET_X = 0x88;
    /** Wall Y offset from spawn Y (ROM: y + $60) */
    private static final int WALL_OFFSET_Y = 0x60;
    /** Player proximity threshold (ROM: $40 pixels) */
    private static final int PLAYER_PROXIMITY = 0x40;

    // Velocity constants (8.8 fixed-point)
    /** Descent speed (ROM: move.w #$40,y_vel(a0)) */
    private static final int DESCENT_SPEED = 0x40;
    /** Bounce speed (ROM: move.w #$100,x_vel(a0)) or -$100 */
    private static final int BOUNCE_SPEED = 0x100;
    /** Laser bounce speed (ROM: move.w #$80,x_vel(a0)) or -$80 */
    private static final int LASER_BOUNCE_SPEED = 0x80;

    // Timing constants
    /** Pre-boss-music timer (ROM: move.b #$5A,objoff_2A) */
    private static final int DESCEND_TIMER = 0x5A;
    /** Boss music delay (ROM: move.b #$60,objoff_2A) */
    private static final int MUSIC_DELAY_TIMER = 0x60;
    /** Bounce timer (ROM: move.b #$70,objoff_2A) */
    private static final int BOUNCE_TIMER = 0x70;
    /** Shooter lower timer (ROM: move.b #$0E,objoff_2A) */
    private static final int SHOOTER_LOWER_TIMER = 0x0E;
    /** Collision enable wait timer (ROM: move.b #$40,objoff_2A) */
    private static final int COLLISION_WAIT_TIMER = 0x40;
    /** Laser bounce timer (ROM: move.b #$80,objoff_2A) */
    private static final int LASER_BOUNCE_TIMER = 0x80;
    /** Defeat explosion timer (ROM: move.w #$EF,objoff_32) */
    private static final int DEFEAT_EXPLOSION_TIMER = 0xEF;
    /** Invulnerability duration (ROM: move.b #$20,objoff_30) */
    private static final int INVULN_DURATION = 0x20;

    // Collision constants
    /** Boss collision flags when hittable (ROM: collision_flags=$06) */
    private static final int COLLISION_HITTABLE = 0x06;

    // Rendering frame indices (from ROM mappings ObjC5_MapUnc_3CCD8)
    private static final int FRAME_CASE_CLOSED = 0;
    private static final int FRAME_CASE_OPEN_1 = 1;
    private static final int FRAME_CASE_OPEN_2 = 2;
    private static final int FRAME_CASE_OPEN_3 = 3;
    private static final int FRAME_LASER_SHOOTER = 4;
    private static final int FRAME_PLATFORM_RELEASER = 5;
    // Frame 6 is unused
    private static final int FRAME_PLATFORM = 7;
    // Frames 8-11 are platform anim frames
    private static final int FRAME_WALL = 0x0C;
    private static final int FRAME_LASER_BASE = 0x0D;
    // Laser extension stages: frames $0E-$12

    // Internal state
    private int actionTimer;
    private int defeatTimer;
    private int currentFrame;
    private boolean facingLeft;
    private int spawnX; // Original spawn X position (for bounds calculation)
    private int leftBound;
    private int rightBound;
    private boolean collisionActive;
    private int openAnimFrame;

    // Child component references
    private WFZLaserWall leftWall;
    private WFZLaserWall rightWall;
    private WFZPlatformReleaser platformReleaser;
    private WFZLaserShooter laserShooter;
    private WFZLaser laser;
    private WFZRobotnik robotnik;

    public Sonic2WFZBossInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, levelManager, "WFZ Boss");
    }

    @Override
    protected void initializeBossState() {
        // ROM: ObjC5_Init
        spawnX = state.x;
        leftBound = spawnX - BOUND_OFFSET_X;
        rightBound = spawnX + BOUND_OFFSET_X;
        currentFrame = FRAME_CASE_CLOSED;
        facingLeft = false;
        collisionActive = false;
        actionTimer = 0;
        defeatTimer = 0;
        openAnimFrame = 0;

        // ROM: Camera max Y = $442
        Camera camera = Camera.getInstance();
        camera.setMaxY((short) CAMERA_MAX_Y_BOSS);

        // Advance to wait-for-player
        state.routine = ROUTINE_WAIT_PLAYER;
    }

    @Override
    protected void updateBossLogic(int frameCounter, AbstractPlayableSprite player) {
        switch (state.routine) {
            case ROUTINE_WAIT_PLAYER -> updateWaitPlayer(player);
            case ROUTINE_SPAWN_CHILDREN -> updateSpawnChildren();
            case ROUTINE_DESCEND -> updateDescend();
            case ROUTINE_SET_DIRECTION -> updateSetDirection(player);
            case ROUTINE_BOUNCE -> updateBounce();
            case ROUTINE_OPEN_ANIM -> updateOpenAnim();
            case ROUTINE_SIGNAL_SHOOTER -> updateSignalShooter();
            case ROUTINE_LOWER_SHOOTER -> updateLowerShooter();
            case ROUTINE_ENABLE_COLLISION -> updateEnableCollision();
            case ROUTINE_SPAWN_LASER -> updateSpawnLaser();
            case ROUTINE_BOUNCE_WITH_LASER -> updateBounceWithLaser();
            case ROUTINE_RETRACT_SHOOTER -> updateRetractShooter();
            case ROUTINE_CLOSE_ANIM -> updateCloseAnim();
            case ROUTINE_LOOP -> updateLoop();
            case ROUTINE_DEFEAT -> updateDefeat(frameCounter);
        }

        // Apply velocity for movement phases
        if (state.routine >= ROUTINE_DESCEND && state.routine <= ROUTINE_BOUNCE_WITH_LASER) {
            state.applyVelocity();
        }
    }

    // ========================================================================
    // Routine $02: Wait for player within $40px horizontally
    // ========================================================================

    private void updateWaitPlayer(AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }
        int dx = Math.abs(player.getCentreX() - state.x);
        if (dx <= PLAYER_PROXIMITY) {
            state.routine = ROUTINE_SPAWN_CHILDREN;
        }
    }

    // ========================================================================
    // Routine $04: Spawn walls, laser shooter, platform releaser, Robotnik
    // ========================================================================

    private void updateSpawnChildren() {
        if (levelManager.getObjectManager() != null) {
            spawnChildObjects();
        }

        // Set descent velocity and timer
        state.yVel = DESCENT_SPEED;
        state.xVel = 0;
        actionTimer = DESCEND_TIMER;

        // Fade music
        AudioManager.getInstance().fadeOutMusic();

        state.routine = ROUTINE_DESCEND;
    }

    private void spawnChildObjects() {
        // 1. Left wall at x - $88, y + $60
        leftWall = new WFZLaserWall(this, spawnX - WALL_OFFSET_X, state.y + WALL_OFFSET_Y);
        childComponents.add(leftWall);
        levelManager.getObjectManager().addDynamicObject(leftWall);

        // 2. Right wall at x + $88, y + $60
        rightWall = new WFZLaserWall(this, spawnX + WALL_OFFSET_X, state.y + WALL_OFFSET_Y);
        childComponents.add(rightWall);
        levelManager.getObjectManager().addDynamicObject(rightWall);

        // 3. Laser shooter (follows parent)
        laserShooter = new WFZLaserShooter(this);
        childComponents.add(laserShooter);
        levelManager.getObjectManager().addDynamicObject(laserShooter);

        // 4. Platform releaser (follows parent X)
        platformReleaser = new WFZPlatformReleaser(this);
        childComponents.add(platformReleaser);
        levelManager.getObjectManager().addDynamicObject(platformReleaser);

        // 5. Robotnik at fixed position ($2C60, $4E6)
        robotnik = new WFZRobotnik(this);
        childComponents.add(robotnik);
        levelManager.getObjectManager().addDynamicObject(robotnik);
    }

    // ========================================================================
    // Routine $06: Descend
    // ========================================================================

    private void updateDescend() {
        actionTimer--;
        if (actionTimer <= 0) {
            state.yVel = 0;
            actionTimer = MUSIC_DELAY_TIMER;
            // Play boss music
            AudioManager.getInstance().playMusic(Sonic2Music.BOSS.id);
            state.routine = ROUTINE_SET_DIRECTION;
        }
    }

    // ========================================================================
    // Routine $08: Set horizontal direction toward player
    // ========================================================================

    private void updateSetDirection(AbstractPlayableSprite player) {
        if (player != null) {
            if (player.getCentreX() < state.x) {
                state.xVel = -BOUNCE_SPEED;
                facingLeft = true;
            } else {
                state.xVel = BOUNCE_SPEED;
                facingLeft = false;
            }
        }

        // Signal platform releaser to start spawning
        if (platformReleaser != null) {
            platformReleaser.signalStart();
        }

        actionTimer = BOUNCE_TIMER;
        state.routine = ROUTINE_BOUNCE;
    }

    // ========================================================================
    // Routine $0A: Bounce between left/right bounds
    // ========================================================================

    private void updateBounce() {
        // Bounce at bounds
        bounceAtBounds();

        actionTimer--;
        if (actionTimer <= 0) {
            // Start open animation
            state.xVel = 0;
            openAnimFrame = 0;
            state.routine = ROUTINE_OPEN_ANIM;
        }
    }

    private void bounceAtBounds() {
        if (state.x <= leftBound) {
            state.x = leftBound;
            state.xFixed = state.x << 16;
            state.xVel = Math.abs(state.xVel);
            facingLeft = false;
        } else if (state.x >= rightBound) {
            state.x = rightBound;
            state.xFixed = state.x << 16;
            state.xVel = -Math.abs(state.xVel);
            facingLeft = true;
        }
    }

    // ========================================================================
    // Routine $0C: Open animation
    // ========================================================================

    private void updateOpenAnim() {
        openAnimFrame++;
        if (openAnimFrame <= 3) {
            currentFrame = openAnimFrame; // Frames 1, 2, 3 (opening stages)
        }
        if (openAnimFrame >= 4) {
            currentFrame = FRAME_CASE_OPEN_3;
            state.routine = ROUTINE_SIGNAL_SHOOTER;
            actionTimer = SHOOTER_LOWER_TIMER;
        }
    }

    // ========================================================================
    // Routine $0E: Signal laser shooter to descend
    // ========================================================================

    private void updateSignalShooter() {
        // Laser shooter descends
        state.routine = ROUTINE_LOWER_SHOOTER;
        actionTimer = SHOOTER_LOWER_TIMER;
    }

    // ========================================================================
    // Routine $10: Move laser shooter down 1px/frame for 14 frames
    // ========================================================================

    private void updateLowerShooter() {
        if (laserShooter != null) {
            laserShooter.moveDown();
        }
        actionTimer--;
        if (actionTimer <= 0) {
            state.routine = ROUTINE_ENABLE_COLLISION;
            actionTimer = COLLISION_WAIT_TIMER;
            collisionActive = true;
        }
    }

    // ========================================================================
    // Routine $12: Enable collision, timer=$40
    // ========================================================================

    private void updateEnableCollision() {
        actionTimer--;
        if (actionTimer <= 0) {
            state.routine = ROUTINE_SPAWN_LASER;
        }
    }

    // ========================================================================
    // Routine $14: Spawn laser
    // ========================================================================

    private void updateSpawnLaser() {
        // Spawn laser child
        if (levelManager.getObjectManager() != null && laser == null) {
            laser = new WFZLaser(this);
            childComponents.add(laser);
            levelManager.getObjectManager().addDynamicObject(laser);
        }

        // Set bounce velocity toward player (using slower laser bounce speed)
        state.xVel = facingLeft ? -LASER_BOUNCE_SPEED : LASER_BOUNCE_SPEED;
        actionTimer = LASER_BOUNCE_TIMER;
        state.routine = ROUTINE_BOUNCE_WITH_LASER;
    }

    // ========================================================================
    // Routine $16: Bounce with laser
    // ========================================================================

    private void updateBounceWithLaser() {
        bounceAtBounds();

        // Check if laser is fully extended (signals parent)
        if (laser != null && laser.isFullyExtended()) {
            // Laser is active, continue bouncing
        }

        actionTimer--;
        if (actionTimer <= 0) {
            state.routine = ROUTINE_RETRACT_SHOOTER;
            actionTimer = SHOOTER_LOWER_TIMER;
            state.xVel = 0;
        }
    }

    // ========================================================================
    // Routine $18: Retract shooter, delete laser, clear collision
    // ========================================================================

    private void updateRetractShooter() {
        if (laserShooter != null) {
            laserShooter.moveUp();
        }

        // Delete laser
        if (laser != null) {
            laser.setDestroyed(true);
            laser = null;
        }

        collisionActive = false;

        actionTimer--;
        if (actionTimer <= 0) {
            state.routine = ROUTINE_CLOSE_ANIM;
            openAnimFrame = 3;
        }
    }

    // ========================================================================
    // Routine $1A: Close animation
    // ========================================================================

    private void updateCloseAnim() {
        openAnimFrame--;
        if (openAnimFrame >= 0) {
            currentFrame = openAnimFrame;
        }
        if (openAnimFrame <= 0) {
            currentFrame = FRAME_CASE_CLOSED;
            state.routine = ROUTINE_LOOP;
        }
    }

    // ========================================================================
    // Routine $1C: Loop back to $08
    // ========================================================================

    private void updateLoop() {
        state.routine = ROUTINE_SET_DIRECTION;
    }

    // ========================================================================
    // Routine $1E: Defeat
    // ========================================================================

    private void updateDefeat(int frameCounter) {
        defeatTimer--;

        if (defeatTimer < 0) {
            // ROM: Play WFZ music, camera max Y=$720, delete
            AudioManager.getInstance().playMusic(Sonic2Music.WING_FORTRESS.id);
            Camera camera = Camera.getInstance();
            camera.setMaxY((short) CAMERA_MAX_Y_DEFEAT);
            // Advance event routine
            Sonic2LevelEventManager eventManager = Sonic2LevelEventManager.getInstance();
            eventManager.setEventRoutine(eventManager.getEventRoutine() + 2);
            GameServices.gameState().setCurrentBossId(0);
            setDestroyed(true);
            return;
        }

        // Spawn explosions
        if (defeatTimer % EXPLOSION_INTERVAL == 0) {
            spawnDefeatExplosion();
        }
    }

    // ========================================================================
    // Collision overrides
    // ========================================================================

    @Override
    public int getCollisionFlags() {
        if (state.invulnerable || state.defeated) {
            return 0;
        }
        // ROM: collision_flags=$06 only during phases $12-$16
        if (collisionActive) {
            return COLLISION_HITTABLE;
        }
        return 0;
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_HITTABLE;
    }

    @Override
    protected int getInitialHitCount() {
        return DEFAULT_HIT_COUNT;
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        // WFZ boss uses standard hit flash
    }

    @Override
    protected int getInvulnerabilityDuration() {
        return INVULN_DURATION;
    }

    @Override
    protected int getPaletteLineForFlash() {
        // ROM: WFZ boss flashes palette line 2 (not line 1)
        return 2;
    }

    @Override
    protected boolean usesDefeatSequencer() {
        return false; // Custom defeat logic in routine $1E
    }

    @Override
    protected void onDefeatStarted() {
        state.routine = ROUTINE_DEFEAT;
        defeatTimer = DEFEAT_EXPLOSION_TIMER;
        state.xVel = 0;
        state.yVel = 0;
        collisionActive = false;

        // Signal children about defeat
        if (leftWall != null) {
            leftWall.signalDefeat();
        }
        if (rightWall != null) {
            rightWall.signalDefeat();
        }
        if (platformReleaser != null) {
            platformReleaser.signalDefeat();
        }
        if (laserShooter != null) {
            laserShooter.setDestroyed(true);
        }
        if (laser != null) {
            laser.setDestroyed(true);
            laser = null;
        }
        if (robotnik != null) {
            robotnik.signalDefeat();
        }
    }

    @Override
    public int getPriorityBucket() {
        return 4;
    }

    // ========================================================================
    // Rendering
    // ========================================================================

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(
                Sonic2ObjectArtKeys.WFZ_BOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        renderer.drawFrameIndex(currentFrame, state.x, state.y, facingLeft, false);
    }

    // ========================================================================
    // Accessors for tests
    // ========================================================================

    public int getCurrentRoutine() {
        return state.routine;
    }

    public int getDefeatTimer() {
        return defeatTimer;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public int getActionTimer() {
        return actionTimer;
    }

    public boolean isCollisionActive() {
        return collisionActive;
    }

    public int getLeftBound() {
        return leftBound;
    }

    public int getRightBound() {
        return rightBound;
    }

    public int getSpawnX() {
        return spawnX;
    }

    // ========================================================================
    // Child Objects
    // ========================================================================

    /**
     * Laser Wall child (subtype $94, routine $04).
     * Solid wall at x +/- $88 from spawn. mapping_frame=$0C.
     * Width $13, Y radius $40, height $80.
     * On defeat: flash then fade-delete in 4 cycles.
     */
    static class WFZLaserWall extends AbstractBossChild {
        private boolean defeatSignaled;
        private int defeatFlashTimer;
        private static final int FLASH_CYCLES = 4;
        private static final int FLASH_DURATION = 8;

        WFZLaserWall(Sonic2WFZBossInstance parent, int wallX, int wallY) {
            super(parent, "Laser Wall", 4, Sonic2ObjectIds.WFZ_BOSS);
            this.currentX = wallX;
            this.currentY = wallY;
            this.defeatSignaled = false;
            this.defeatFlashTimer = 0;
        }

        void signalDefeat() {
            defeatSignaled = true;
            defeatFlashTimer = FLASH_CYCLES * FLASH_DURATION;
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) {
                return;
            }
            if (defeatSignaled) {
                defeatFlashTimer--;
                if (defeatFlashTimer <= 0) {
                    setDestroyed(true);
                }
            }
            updateDynamicSpawn();
        }

        @Override
        public void syncPositionWithParent() {
            // Walls are fixed position, do not follow parent
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed()) {
                return;
            }
            // Flash visibility during defeat (toggle on/off every 2 frames)
            if (defeatSignaled && (defeatFlashTimer & 0x02) != 0) {
                return;
            }
            ObjectRenderManager renderManager =
                    LevelManager.getInstance().getObjectRenderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(
                    Sonic2ObjectArtKeys.WFZ_BOSS);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(FRAME_WALL, currentX, currentY, false, false);
        }
    }

    /**
     * Platform Releaser child (subtype $96, routine $06).
     * mapping_frame=5, follows laser case X.
     * After signal: timer=$40, y_vel=$40, move down.
     * Spawns platforms cyclically (max 3, interval $80 frames).
     * On defeat: spawn explosions then delete.
     */
    static class WFZPlatformReleaser extends AbstractBossChild {
        private static final int PLATFORM_SPAWN_INTERVAL = 0x80;
        private static final int MAX_PLATFORMS = 3;
        private static final int MOVE_DOWN_TIMER = 0x40;
        private static final int MOVE_DOWN_SPEED = 0x40;

        private boolean started;
        private boolean defeatSignaled;
        private int spawnTimer;
        private int platformCount;
        private int moveDownTimer;
        private int yFixed;

        WFZPlatformReleaser(Sonic2WFZBossInstance parent) {
            super(parent, "Platform Releaser", 4, Sonic2ObjectIds.WFZ_BOSS);
            this.started = false;
            this.defeatSignaled = false;
            this.spawnTimer = 0;
            this.platformCount = 0;
            this.moveDownTimer = 0;
            this.yFixed = currentY << 16;
        }

        void signalStart() {
            if (!started) {
                started = true;
                spawnTimer = PLATFORM_SPAWN_INTERVAL;
                moveDownTimer = MOVE_DOWN_TIMER;
            }
        }

        void signalDefeat() {
            defeatSignaled = true;
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) {
                return;
            }

            if (defeatSignaled) {
                // Spawn explosion then delete
                setDestroyed(true);
                return;
            }

            // Follow parent X position
            if (parent != null && !parent.isDestroyed()) {
                currentX = parent.getX();
            }

            if (started) {
                // Move down phase
                if (moveDownTimer > 0) {
                    moveDownTimer--;
                    yFixed += (MOVE_DOWN_SPEED << 8);
                    currentY = yFixed >> 16;
                }

                // Spawn platforms cyclically
                spawnTimer--;
                if (spawnTimer <= 0 && platformCount < MAX_PLATFORMS) {
                    spawnPlatform();
                    spawnTimer = PLATFORM_SPAWN_INTERVAL;
                }
            }

            updateDynamicSpawn();
        }

        private void spawnPlatform() {
            LevelManager lm = LevelManager.getInstance();
            if (lm.getObjectManager() == null) {
                return;
            }
            Sonic2WFZBossInstance wfzParent = (Sonic2WFZBossInstance) parent;
            WFZFloatingPlatform platform = new WFZFloatingPlatform(wfzParent, currentX, currentY);
            wfzParent.childComponents.add(platform);
            lm.getObjectManager().addDynamicObject(platform);
            platformCount++;
        }

        @Override
        public void syncPositionWithParent() {
            if (parent != null && !parent.isDestroyed()) {
                currentX = parent.getX();
                // Y follows its own movement, not parent
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed()) {
                return;
            }
            ObjectRenderManager renderManager =
                    LevelManager.getInstance().getObjectRenderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(
                    Sonic2ObjectArtKeys.WFZ_BOSS);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(FRAME_PLATFORM_RELEASER, currentX, currentY, false, false);
        }
    }

    /**
     * Floating Platform child (subtype $98, routine $08).
     * anim=3, mapping_frame=7. Player can stand on it (PlatformObject).
     * Descend y_vel=$100 for $60 frames, then go left x_vel=-$100, timer=$60.
     * Reverse x_vel every $C0 frames, oscillate Y by +/- $04.
     * Spawns PlatformHurt child below it.
     * On defeat: explode, delete hurt child.
     */
    static class WFZFloatingPlatform extends AbstractBossChild {
        private static final int DESCEND_SPEED = 0x100;
        private static final int DESCEND_DURATION = 0x60;
        private static final int HORIZONTAL_SPEED = 0x100;
        private static final int HORIZONTAL_TIMER = 0x60;
        private static final int REVERSE_INTERVAL = 0xC0;
        private static final int Y_OSCILLATION = 0x04;

        private int phase; // 0=descend, 1=horizontal movement
        private int phaseTimer;
        private int reverseTimer;
        private int xVel;
        private int yVel;
        private int xFixed;
        private int yFixed;
        private int oscillateDir; // +1 or -1
        private WFZPlatformHurt hurtChild;

        WFZFloatingPlatform(Sonic2WFZBossInstance parent, int startX, int startY) {
            super(parent, "Floating Platform", 4, Sonic2ObjectIds.WFZ_BOSS);
            this.currentX = startX;
            this.currentY = startY;
            this.xFixed = startX << 16;
            this.yFixed = startY << 16;
            this.phase = 0;
            this.phaseTimer = DESCEND_DURATION;
            this.reverseTimer = REVERSE_INTERVAL;
            this.xVel = 0;
            this.yVel = DESCEND_SPEED;
            this.oscillateDir = 1;

            // Spawn hurt child
            spawnHurtChild(parent);
        }

        private void spawnHurtChild(Sonic2WFZBossInstance wfzParent) {
            LevelManager lm = LevelManager.getInstance();
            if (lm.getObjectManager() == null) {
                return;
            }
            hurtChild = new WFZPlatformHurt(wfzParent, this);
            wfzParent.childComponents.add(hurtChild);
            lm.getObjectManager().addDynamicObject(hurtChild);
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) {
                return;
            }

            switch (phase) {
                case 0 -> {
                    // Descend phase
                    yFixed += (yVel << 8);
                    currentY = yFixed >> 16;
                    phaseTimer--;
                    if (phaseTimer <= 0) {
                        phase = 1;
                        xVel = -HORIZONTAL_SPEED;
                        yVel = 0;
                        phaseTimer = HORIZONTAL_TIMER;
                        reverseTimer = REVERSE_INTERVAL;
                    }
                }
                case 1 -> {
                    // Horizontal movement with Y oscillation
                    xFixed += (xVel << 8);
                    currentX = xFixed >> 16;

                    // Y oscillation
                    currentY += (Y_OSCILLATION * oscillateDir);
                    yFixed = currentY << 16;

                    reverseTimer--;
                    if (reverseTimer <= 0) {
                        xVel = -xVel;
                        oscillateDir = -oscillateDir;
                        reverseTimer = REVERSE_INTERVAL;
                    }
                }
            }

            // Update hurt child position
            if (hurtChild != null && !hurtChild.isDestroyed()) {
                hurtChild.syncToParentPlatform(currentX, currentY);
            }

            updateDynamicSpawn();
        }

        @Override
        public void syncPositionWithParent() {
            // Platform moves independently
        }

        @Override
        public boolean isDestroyed() {
            // Destroy when parent boss is defeated
            if (parent != null && parent.getState().defeated) {
                if (hurtChild != null) {
                    hurtChild.setDestroyed(true);
                }
                setDestroyed(true);
            }
            return super.isDestroyed();
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed()) {
                return;
            }
            ObjectRenderManager renderManager =
                    LevelManager.getInstance().getObjectRenderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(
                    Sonic2ObjectArtKeys.WFZ_BOSS);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(FRAME_PLATFORM, currentX, currentY, false, false);
        }
    }

    /**
     * Platform Hurt child (subtype $9A, routine $0A).
     * Invisible damage collider, collision_flags=$98.
     * Follows parent platform, offset Y+$0C.
     * Delete when parent signals defeat.
     */
    static class WFZPlatformHurt extends AbstractBossChild {
        private static final int Y_OFFSET = 0x0C;
        private static final int COLLISION_FLAGS = 0x98;

        private final WFZFloatingPlatform platformParent;

        WFZPlatformHurt(Sonic2WFZBossInstance bossParent, WFZFloatingPlatform platformParent) {
            super(bossParent, "Platform Hurt", 4, Sonic2ObjectIds.WFZ_BOSS);
            this.platformParent = platformParent;
            syncToParentPlatform(platformParent.getCurrentX(), platformParent.getCurrentY());
        }

        void syncToParentPlatform(int platformX, int platformY) {
            this.currentX = platformX;
            this.currentY = platformY + Y_OFFSET;
        }

        public int getCollisionFlags() {
            return COLLISION_FLAGS;
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) {
                return;
            }
            if (platformParent.isDestroyed()) {
                setDestroyed(true);
            }
            updateDynamicSpawn();
        }

        @Override
        public void syncPositionWithParent() {
            if (platformParent != null && !platformParent.isDestroyed()) {
                syncToParentPlatform(platformParent.getCurrentX(), platformParent.getCurrentY());
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // Invisible - no rendering
        }
    }

    /**
     * Laser Shooter child (subtype $9C, routine $0C).
     * mapping_frame=4, follows parent position.
     * Controlled by parent (lowered/raised during laser phases).
     */
    static class WFZLaserShooter extends AbstractBossChild {
        private int yOffset;

        WFZLaserShooter(Sonic2WFZBossInstance parent) {
            super(parent, "Laser Shooter", 3, Sonic2ObjectIds.WFZ_BOSS);
            this.yOffset = 0;
        }

        void moveDown() {
            yOffset++;
        }

        void moveUp() {
            if (yOffset > 0) {
                yOffset--;
            }
        }

        int getYOffset() {
            return yOffset;
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) {
                return;
            }
            syncPositionWithParent();
            updateDynamicSpawn();
        }

        @Override
        public void syncPositionWithParent() {
            if (parent != null && !parent.isDestroyed()) {
                this.currentX = parent.getX();
                this.currentY = parent.getY() + yOffset;
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed()) {
                return;
            }
            ObjectRenderManager renderManager =
                    LevelManager.getInstance().getObjectRenderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(
                    Sonic2ObjectArtKeys.WFZ_BOSS);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(FRAME_LASER_SHOOTER, currentX, currentY, false, false);
        }
    }

    /**
     * Laser child (subtype $9E, routine $0E).
     * mapping_frame=$0D, extends downward in 5 stages.
     * Stage data: {frame $0E,col $86}, {$0F,$AB}, {$10,$AC}, {$11,$AD}, {$12,$AE}
     * Flickers via toggle each frame.
     * Signals parent when fully extended.
     */
    static class WFZLaser extends AbstractBossChild {
        private static final int[][] LASER_STAGES = {
                {0x0E, 0x86},
                {0x0F, 0xAB},
                {0x10, 0xAC},
                {0x11, 0xAD},
                {0x12, 0xAE}
        };
        private static final int STAGE_FRAME_DURATION = 4; // Frames per stage

        private int currentStage;
        private int stageTimer;
        private int currentMappingFrame;
        private boolean flickerToggle;
        private boolean fullyExtended;

        WFZLaser(Sonic2WFZBossInstance parent) {
            super(parent, "Laser", 3, Sonic2ObjectIds.WFZ_BOSS);
            this.currentStage = 0;
            this.stageTimer = STAGE_FRAME_DURATION;
            this.currentMappingFrame = FRAME_LASER_BASE;
            this.flickerToggle = false;
            this.fullyExtended = false;
        }

        boolean isFullyExtended() {
            return fullyExtended;
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) {
                return;
            }
            syncPositionWithParent();

            // Flicker toggle each frame
            flickerToggle = !flickerToggle;

            // Advance through stages
            if (!fullyExtended) {
                stageTimer--;
                if (stageTimer <= 0) {
                    currentStage++;
                    if (currentStage >= LASER_STAGES.length) {
                        currentStage = LASER_STAGES.length - 1;
                        fullyExtended = true;
                    }
                    stageTimer = STAGE_FRAME_DURATION;
                }
                currentMappingFrame = LASER_STAGES[currentStage][0];
            }

            updateDynamicSpawn();
        }

        @Override
        public void syncPositionWithParent() {
            if (parent != null && !parent.isDestroyed()) {
                Sonic2WFZBossInstance wfzParent = (Sonic2WFZBossInstance) parent;
                this.currentX = parent.getX();
                // Position below the laser shooter
                if (wfzParent.laserShooter != null) {
                    this.currentY = wfzParent.laserShooter.getCurrentY();
                } else {
                    this.currentY = parent.getY();
                }
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed()) {
                return;
            }
            // Flicker: only render every other frame
            if (flickerToggle) {
                return;
            }
            ObjectRenderManager renderManager =
                    LevelManager.getInstance().getObjectRenderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(
                    Sonic2ObjectArtKeys.WFZ_BOSS);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(currentMappingFrame, currentX, currentY, false, false);
        }
    }

    /**
     * Robotnik child (subtype $A0, routine $10).
     * Uses ObjC6 mappings (0x3D0EE), fixed at ($2C60, $4E6).
     * Spawns RobotnikPlatform child.
     * On defeat: timer=$C0, move down 1px/frame, then delete.
     */
    static class WFZRobotnik extends AbstractBossChild {
        private static final int ROBOTNIK_X = 0x2C60;
        private static final int ROBOTNIK_Y = 0x04E6;
        private static final int DEFEAT_MOVE_TIMER = 0xC0;

        private boolean defeatSignaled;
        private int defeatTimer;
        private WFZRobotnikPlatform robotnikPlatform;

        WFZRobotnik(Sonic2WFZBossInstance parent) {
            super(parent, "WFZ Robotnik", 4, Sonic2ObjectIds.WFZ_BOSS);
            this.currentX = ROBOTNIK_X;
            this.currentY = ROBOTNIK_Y;
            this.defeatSignaled = false;
            this.defeatTimer = 0;

            // Spawn Robotnik platform child
            spawnPlatformChild(parent);
        }

        private void spawnPlatformChild(Sonic2WFZBossInstance wfzParent) {
            LevelManager lm = LevelManager.getInstance();
            if (lm.getObjectManager() == null) {
                return;
            }
            robotnikPlatform = new WFZRobotnikPlatform(wfzParent, this);
            wfzParent.childComponents.add(robotnikPlatform);
            lm.getObjectManager().addDynamicObject(robotnikPlatform);
        }

        void signalDefeat() {
            defeatSignaled = true;
            defeatTimer = DEFEAT_MOVE_TIMER;
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) {
                return;
            }

            if (defeatSignaled) {
                defeatTimer--;
                if (defeatTimer > 0) {
                    // Move down 1px/frame
                    currentY++;
                } else {
                    setDestroyed(true);
                    if (robotnikPlatform != null) {
                        robotnikPlatform.setDestroyed(true);
                    }
                }
            }

            // Update platform position
            if (robotnikPlatform != null && !robotnikPlatform.isDestroyed()) {
                robotnikPlatform.syncToRobotnik(currentX, currentY);
            }

            updateDynamicSpawn();
        }

        @Override
        public void syncPositionWithParent() {
            // Robotnik is at fixed position
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed()) {
                return;
            }
            ObjectRenderManager renderManager =
                    LevelManager.getInstance().getObjectRenderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(
                    Sonic2ObjectArtKeys.WFZ_ROBOTNIK);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(0, currentX, currentY, false, false);
        }
    }

    /**
     * Robotnik Platform child (subtype $A2, routine $12).
     * Uses FloatingPlatform mappings (0x3CEBC), follows Robotnik Y+$26.
     */
    static class WFZRobotnikPlatform extends AbstractBossChild {
        private static final int Y_OFFSET = 0x26;
        private final WFZRobotnik robotnikParent;

        WFZRobotnikPlatform(Sonic2WFZBossInstance bossParent, WFZRobotnik robotnikParent) {
            super(bossParent, "Robotnik Platform", 5, Sonic2ObjectIds.WFZ_BOSS);
            this.robotnikParent = robotnikParent;
            syncToRobotnik(robotnikParent.getCurrentX(), robotnikParent.getCurrentY());
        }

        void syncToRobotnik(int robotnikX, int robotnikY) {
            this.currentX = robotnikX;
            this.currentY = robotnikY + Y_OFFSET;
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (!beginUpdate(frameCounter)) {
                return;
            }
            if (robotnikParent.isDestroyed()) {
                setDestroyed(true);
            }
            updateDynamicSpawn();
        }

        @Override
        public void syncPositionWithParent() {
            if (robotnikParent != null && !robotnikParent.isDestroyed()) {
                syncToRobotnik(robotnikParent.getCurrentX(), robotnikParent.getCurrentY());
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed()) {
                return;
            }
            ObjectRenderManager renderManager =
                    LevelManager.getInstance().getObjectRenderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(
                    Sonic2ObjectArtKeys.WFZ_ROBOTNIK_PLATFORM);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(0, currentX, currentY, false, false);
        }
    }
}
