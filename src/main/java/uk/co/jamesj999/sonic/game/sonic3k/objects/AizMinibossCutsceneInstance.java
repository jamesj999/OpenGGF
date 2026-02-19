package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kLevelEventManager;
import uk.co.jamesj999.sonic.game.sonic3k.events.Sonic3kAIZEvents;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.boss.AbstractBossChild;
import uk.co.jamesj999.sonic.level.objects.boss.AbstractBossInstance;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * AIZ Act 1 Miniboss Cutscene (Object 0x90).
 * ROM: s3.asm loc_68508 (Obj_AIZMinibossCutscene)
 *
 * <p>This is the fire-breathing tree stump robot near the end of AIZ Act 1.
 * It is a scripted set-piece that cannot be destroyed (collision_property=0x60).
 * After a timed sequence, it exits and triggers the fire transition via Events_fg_5.
 *
 * <p>State machine:
 * <ul>
 *   <li>0 INIT: setup attributes, Boss_flag=1</li>
 *   <li>2 WAIT_TRIGGER: poll Camera_X_pos >= 0x2F10</li>
 *   <li>4 CAMERA_WAIT: 180-frame countdown after trigger</li>
 *   <li>6 DESCEND: descend at y_vel=0x100 for 175 frames</li>
 *   <li>8 SWING: oscillate via Swing_UpAndDown for 127 frames</li>
 *   <li>10 PRE_EXIT: 16-frame pause with explosion</li>
 *   <li>12 EXIT: fly right at x_vel=0x400 for 288 frames</li>
 *   <li>14 CLEANUP: clear Boss_flag, restore music, delete self</li>
 * </ul>
 */
public class AizMinibossCutsceneInstance extends AbstractBossInstance {
    private static final Logger LOG = Logger.getLogger(AizMinibossCutsceneInstance.class.getName());

    // --- State constants ---
    private static final int STATE_INIT = 0;
    private static final int STATE_WAIT_TRIGGER = 2;
    private static final int STATE_CAMERA_WAIT = 4;
    private static final int STATE_DESCEND = 6;
    private static final int STATE_SWING = 8;
    private static final int STATE_PRE_EXIT = 10;
    private static final int STATE_EXIT = 12;
    private static final int STATE_CLEANUP = 14;

    // --- ROM constants ---
    /** Camera X threshold to trigger boss appearance. */
    private static final int TRIGGER_X = 0x2F10;
    /** Timer after trigger before boss descends. */
    private static final int CAMERA_WAIT_TIMER = 180;
    /** Timer for descent phase. */
    private static final int DESCEND_TIMER = 0xAF; // 175
    /** Descent velocity (8.8 fixed-point). */
    private static final int DESCEND_VELOCITY = 0x100;
    /** Timer for swing phase. */
    private static final int SWING_TIMER = 0x7F; // 127
    /** Timer for pre-exit explosion. */
    private static final int PRE_EXIT_TIMER = 0x10; // 16
    /** Timer for exit phase (Act 1). */
    private static final int EXIT_TIMER = 0x120; // 288
    /** Exit velocity. */
    private static final int EXIT_VELOCITY = 0x400;
    /** collision_property for unkillable boss (96 HP). */
    private static final int COLLISION_PROPERTY = 0x60;

    // Custom memory flag for barrel activation
    private static final int FLAG_ADDR = 0x38;

    // --- Debris spawn data (from ChildObjDat_6906A) ---
    private static final int[] DEBRIS_X_OFFSETS = {-0x20, -0x68, -0x10, -0x58, -8, -0x50};
    private static final int[] DEBRIS_Y_OFFSETS = {0x310, 0x310, 0x31C, 0x31C, 0x328, 0x328};
    private static final int[] DEBRIS_VELOCITIES = {0x200, 0x200, 0x180, 0x180, 0x100, 0x100};
    private static final int[] DEBRIS_FRAMES = {0, 0, 1, 1, 2, 2};

    private final AizMinibossSwingMotion swingMotion = new AizMinibossSwingMotion();
    private int stateTimer;
    private int savedCameraMaxX;

    public AizMinibossCutsceneInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, levelManager, "AIZMinibossCutscene");
    }

    @Override
    protected void initializeBossState() {
        state.routine = STATE_INIT;
        state.hitCount = COLLISION_PROPERTY;
        stateTimer = 0;
        savedCameraMaxX = 0;
    }

    @Override
    protected int getInitialHitCount() {
        return COLLISION_PROPERTY;
    }

    @Override
    protected boolean usesDefeatSequencer() {
        return false;
    }

    @Override
    protected int getCollisionSizeIndex() {
        return 0x0C;
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        // Cosmetic only - boss can't be defeated
    }

    @Override
    protected int getPaletteLineForFlash() {
        return 2; // ROM: Pal_AIZMiniboss flash on palette line 2
    }

    @Override
    protected void updateBossLogic(int frameCounter, AbstractPlayableSprite player) {
        switch (state.routine) {
            case STATE_INIT -> updateInit();
            case STATE_WAIT_TRIGGER -> updateWaitTrigger();
            case STATE_CAMERA_WAIT -> updateCameraWait();
            case STATE_DESCEND -> updateDescend();
            case STATE_SWING -> updateSwing();
            case STATE_PRE_EXIT -> updatePreExit();
            case STATE_EXIT -> updateExit();
            case STATE_CLEANUP -> updateCleanup();
        }
    }

    // --- State handlers ---

    private void updateInit() {
        // Save current camera max X, set Boss_flag
        Camera camera = Camera.getInstance();
        savedCameraMaxX = camera.getMaxX();

        Sonic3kAIZEvents events = getAizEvents();
        if (events != null) {
            events.setBossFlag(true);
        }

        state.routine = STATE_WAIT_TRIGGER;
        LOG.info("AIZ Miniboss: initialized, waiting for trigger at X=0x" +
                Integer.toHexString(TRIGGER_X));
    }

    private void updateWaitTrigger() {
        Camera camera = Camera.getInstance();
        int cameraX = camera.getX();

        if (cameraX >= TRIGGER_X) {
            // Spawn 6 debris pieces
            spawnDebris(camera);

            // Lock camera
            camera.setMinX((short) TRIGGER_X);
            camera.setMaxX((short) TRIGGER_X);

            // Start timer
            stateTimer = CAMERA_WAIT_TIMER;
            state.routine = STATE_CAMERA_WAIT;
            LOG.info("AIZ Miniboss: triggered at cameraX=0x" + Integer.toHexString(cameraX));
        }
    }

    private void updateCameraWait() {
        stateTimer--;
        if (stateTimer <= 0) {
            var objectManager = levelManager.getObjectManager();

            // Spawn structural children (body + arm)
            spawnChild(new AizMinibossBodyChild(this), objectManager);
            spawnChild(new AizMinibossArmChild(this), objectManager);

            // Spawn 3 flame barrels
            for (int i = 0; i < 3; i++) {
                spawnChild(new AizMinibossFlameBarrelChild(this, i), objectManager);
            }

            // Set descent velocity
            state.yVel = DESCEND_VELOCITY;
            stateTimer = DESCEND_TIMER;

            // TODO: Play mus_Miniboss when S3K audio IDs are defined

            state.routine = STATE_DESCEND;
            LOG.info("AIZ Miniboss: spawned children, descending");
        }
    }

    /** Register a child with both the boss lifecycle and the ObjectManager render/collision pass. */
    private void spawnChild(AbstractBossChild child,
                            uk.co.jamesj999.sonic.level.objects.ObjectManager objectManager) {
        childComponents.add(child);
        if (objectManager != null) {
            objectManager.addDynamicObject(child);
        }
    }

    private void updateDescend() {
        // Move with velocity
        state.yFixed += (state.yVel << 8);
        state.updatePositionFromFixed();

        stateTimer--;
        if (stateTimer <= 0) {
            // Activate flame barrels
            setCustomFlag(FLAG_ADDR, getCustomFlag(FLAG_ADDR) | 0x02);

            // Setup swing motion - reset yVel before oscillation (ROM: Swing_Setup1 sets y_vel=speed)
            state.yVel = 0;
            swingMotion.setup1();
            stateTimer = SWING_TIMER;

            state.routine = STATE_SWING;
            LOG.info("AIZ Miniboss: swing phase started");
        }
    }

    private void updateSwing() {
        swingMotion.update(state);

        // Move with velocity
        state.yFixed += (state.yVel << 8);
        state.updatePositionFromFixed();

        stateTimer--;
        if (stateTimer <= 0) {
            stateTimer = PRE_EXIT_TIMER;
            state.routine = STATE_PRE_EXIT;

            // Spawn explosion effect
            spawnDefeatExplosion();
        }
    }

    private void updatePreExit() {
        stateTimer--;
        if (stateTimer <= 0) {
            // Set Events_fg_5 to trigger fire transition
            Sonic3kAIZEvents events = getAizEvents();
            if (events != null) {
                events.setEventsFg5(true);
            }

            // Begin exit: fly right
            state.xVel = EXIT_VELOCITY;
            state.yVel = 0;
            stateTimer = EXIT_TIMER;

            state.routine = STATE_EXIT;
            LOG.info("AIZ Miniboss: exiting, Events_fg_5 set");
        }
    }

    private void updateExit() {
        // Fly right
        state.xFixed += (state.xVel << 8);
        state.updatePositionFromFixed();

        stateTimer--;
        if (stateTimer <= 0) {
            state.routine = STATE_CLEANUP;
        }
    }

    private void updateCleanup() {
        // Clear Boss_flag
        Sonic3kAIZEvents events = getAizEvents();
        if (events != null) {
            events.setBossFlag(false);
        }

        // Restore camera boundaries (ROM: Camera_stored_max_X_pos)
        Camera camera = Camera.getInstance();
        camera.setMinX((short) 0);
        camera.setMaxX((short) savedCameraMaxX);

        // TODO: Restore level music when S3K audio IDs are defined
        // TODO: Load_PLC(PLC_Monitors)

        setDestroyed(true);
        LOG.info("AIZ Miniboss: cleaned up and destroyed");
    }

    // --- Helper methods ---

    private void spawnDebris(Camera camera) {
        var objectManager = levelManager.getObjectManager();
        if (objectManager == null) return;

        int cameraX = camera.getX();
        for (int i = 0; i < 6; i++) {
            int debrisX = cameraX + DEBRIS_X_OFFSETS[i];
            int debrisY = DEBRIS_Y_OFFSETS[i];
            AizMinibossDebrisChild debris = new AizMinibossDebrisChild(
                    debrisX, debrisY, DEBRIS_VELOCITIES[i], DEBRIS_FRAMES[i]);
            objectManager.addDynamicObject(debris);
        }
    }

    private Sonic3kAIZEvents getAizEvents() {
        return Sonic3kLevelEventManager.getInstance().getAizEvents();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Don't render during init/wait states or after cleanup
        if (state.routine <= STATE_CAMERA_WAIT || isDestroyed()) {
            return;
        }

        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.AIZ_MINIBOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Main boss head/top frame (frame index depends on state)
        boolean hFlip = state.xVel > 0; // face direction of movement
        renderer.drawFrameIndex(getMappingFrame(), state.x, state.y, hFlip, false);
    }

    private int getMappingFrame() {
        return switch (state.routine) {
            case STATE_DESCEND -> 4;    // descending frame
            case STATE_SWING -> 5;      // active/swinging frame
            case STATE_PRE_EXIT -> 6;   // pre-exit frame
            case STATE_EXIT -> 7;       // flying away frame
            default -> 4;
        };
    }
}
