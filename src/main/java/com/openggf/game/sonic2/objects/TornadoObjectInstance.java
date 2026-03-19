package com.openggf.game.sonic2.objects;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.debug.DebugRenderContext;
import com.openggf.game.sonic2.Sonic2LevelEventManager;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Map;
import com.openggf.level.ParallaxManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import com.openggf.debug.DebugColor;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Object 0xB2 - Tornado (SCZ/WFZ scripted biplane sequence).
 *
 * Disassembly reference:
 * - ObjB2: docs/s2disasm/s2.asm (ObjB2, loc_3A79E onward)
 * - ObjC3 smoke child behavior: docs/s2disasm/s2.asm (ObjC3)
 */
public class TornadoObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // ------------------------------------------------------------------------
    // Subtypes / routines (routine = subtype - 0x4E)
    // ------------------------------------------------------------------------

    private static final int SUBTYPE_SCZ_MAIN = 0x50;      // routine 2
    private static final int SUBTYPE_WFZ_START = 0x52;     // routine 4
    private static final int SUBTYPE_WFZ_END = 0x54;       // routine 6
    private static final int SUBTYPE_INVISIBLE_GRABBER = 0x56; // routine 8
    private static final int SUBTYPE_BLINKER = 0x58;       // routine A
    private static final int SUBTYPE_UNUSED_MOVER = 0x5A;  // routine C
    private static final int SUBTYPE_THRUSTER = 0x5C;      // routine E

    private static final int ROUTINE_SCZ_MAIN = 0x02;
    private static final int ROUTINE_WFZ_START = 0x04;
    private static final int ROUTINE_WFZ_END = 0x06;
    private static final int ROUTINE_INVISIBLE_GRABBER = 0x08;
    private static final int ROUTINE_BLINKER = 0x0A;
    private static final int ROUTINE_UNUSED_MOVER = 0x0C;
    private static final int ROUTINE_THRUSTER = 0x0E;

    // ------------------------------------------------------------------------
    // Shared movement / collision constants
    // ------------------------------------------------------------------------

    private static final SolidObjectParams TORNADO_SOLID_PARAMS = new SolidObjectParams(0x1B, 8, 9);
    private static final int SCZ_CAMERA_FINISH_X = 0x1400;
    private static final int SCZ_PLAYER_FINISH_X = 0x1568;
    private static final int SCZ_PLAYER_PUSH_MARGIN = 0x11;
    private static final int SCZ_CAMERA_MAX_OFFSET = 0x40;

    private static final int VERTICAL_LIMIT_UP_OFFSET = 0x34;
    private static final int VERTICAL_LIMIT_DOWN_OFFSET = 0xA8;

    private static final int PLAYER_HORIZONTAL_CLAMP = 0x10;
    private static final int PLAYER_INERTIA_CLAMP = 0x900;
    private static final int OFFSCREEN_DISTANCE = 0x280;

    // ------------------------------------------------------------------------
    // WFZ start constants (routine 4)
    // ------------------------------------------------------------------------

    private static final int WFZ_START_MAIN_TIMER = 0xC0;
    private static final int WFZ_SHOT_DOWN_TIMER = 0x60;
    private static final int WFZ_SMOKE_PERIOD = 0x0E;
    private static final int WFZ_SCATTER_SFX_MASK = 0x1F;

    // ------------------------------------------------------------------------
    // WFZ end constants (routine 6)
    // ------------------------------------------------------------------------

    private static final int WFZ_WAIT_PLAYER_Y = 0x5EC;
    private static final int WFZ_WAIT_FRAMES = 0x40;
    private static final int WFZ_LEADER_EDGE_X = 0x2E30;
    private static final int WFZ_PLANE_WAIT_BG_X = 0x380;
    private static final int WFZ_PREPARE_TO_JUMP_FRAMES = 0x30;
    private static final int WFZ_JUMP_TIMER_NORMAL = 0x38;
    private static final int WFZ_JUMP_TIMER_SUPER = 0x28;
    private static final int WFZ_LANDED_WAIT_FRAMES = 0x100;
    private static final int WFZ_JUMP_TO_SHIP_START = 0x437;
    private static final int WFZ_JUMP_TO_SHIP_END = 0x447;
    private static final int WFZ_SPAWN_EXTRA_CHILDREN_AT = 0x460;
    private static final int WFZ_START_DEZ_AT = 0x9C0;

    // Docking velocity script (word_3AC16 / byte_3AC2A).
    private static final int[] WFZ_DOCK_THRESHOLDS = {
            0x1E0, 0x260, 0x2A0, 0x2C0, 0x300, 0x3A0, 0x3F0, 0x460, 0x4A0, 0x580
    };
    private static final byte[] WFZ_DOCK_VELOCITY_BYTES = {
            (byte) 0xFF, (byte) 0xFF,
            0x01, 0x00,
            0x00, 0x01,
            0x01, (byte) 0xFF,
            0x01, 0x01,
            0x01, (byte) 0xFF,
            (byte) 0xFF, 0x01,
            (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, 0x01,
            (byte) 0xFE, 0x00,
            0x00, 0x00
    };

    // Level layout patch written when Sonic lands on the plane in WFZ end.
    private static final int LAYOUT_WIDTH = 256;
    private static final int[] LAYOUT_PATCH_OFFSETS = {0x0D2, 0x1D2, 0xBD6, 0xCD6};
    private static final int[][] LAYOUT_PATCH_BYTES = {
            {0x50, 0x1F, 0x00, 0x25},
            {0x25, 0x00, 0x1F, 0x50},
            {0x50, 0x1F, 0x00, 0x25},
            {0x25, 0x00, 0x1F, 0x50}
    };

    // Animation scripts (Ani_objB2_a / Ani_objB2_b).
    private static final int[] MAIN_ANIM_A = {0, 1, 2, 3};
    private static final int[] MAIN_ANIM_B = {4, 5, 6, 7};
    private static final int[] THRUSTER_ANIM = {1, 2};

    // Input masks for scripted control (Ctrl_1_Logical writes).
    private static final int INPUT_RIGHT = AbstractPlayableSprite.INPUT_RIGHT;
    private static final int INPUT_UP = AbstractPlayableSprite.INPUT_UP;
    private static final int INPUT_DOWN = AbstractPlayableSprite.INPUT_DOWN;
    private static final int INPUT_JUMP = AbstractPlayableSprite.INPUT_JUMP;

    // ------------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------------

    private final int subtype;
    private final TornadoObjectInstance parent;

    private int routine;
    private int routineSecondary;

    private int currentX;
    private int currentY;
    private int xPosFixed8;
    private int yPosFixed8;
    private int xVel;
    private int yVel;

    private int mappingFrame;
    private int animId;
    private int animFrameIndex;

    // SCZ movement helpers (objoff_2E/$2F/$30/$31/$38 equivalents).
    private boolean standingTransition;
    private boolean moveVertActive;
    private boolean moveVert2Active;
    private int moveVertTimer;
    private int smoothOffsetX;
    private boolean lastMainStanding;

    // WFZ start / end scratch fields.
    private int scriptTimer;
    private int smokeSpawnTimer;
    private int leaderWaitCounter;
    private int jumpTimer;
    private int dockVelocityIndex;

    // Render/solid flags for current frame.
    private boolean renderThisFrame;
    private boolean solidActive;
    private boolean highPriority;

    // Script control ownership/cleanup.
    private boolean ownsPlayerControl;
    private boolean sczTransitionRequested;
    private boolean dezTransitionRequested;
    private boolean levelLayoutPatched;
    private boolean spawnedWfzDockChildren;
    private boolean grabberTriggered;
    private boolean blinkerVisible;

    private TornadoObjectInstance thrusterFollowerChild;

    public TornadoObjectInstance(ObjectSpawn spawn) {
        this(spawn, null);
    }

    private TornadoObjectInstance(ObjectSpawn spawn, TornadoObjectInstance parent) {
        super(spawn, "Tornado");
        this.subtype = spawn.subtype() & 0xFF;
        this.parent = parent;

        this.currentX = spawn.x();
        this.currentY = spawn.y();
        syncFixedFromPosition();

        // ROM init: routine = subtype - $4E
        this.routine = Math.max(0, subtype - 0x4E);
        this.routineSecondary = 0;
        this.mappingFrame = 0;
        this.animId = 0;
        this.animFrameIndex = 0;
        this.blinkerVisible = true;
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public boolean isHighPriority() {
        return highPriority;
    }

    @Override
    public int getPriorityBucket() {
        if (routine == ROUTINE_THRUSTER) {
            return RenderPriority.clamp(3);
        }
        if (routine == ROUTINE_UNUSED_MOVER) {
            return RenderPriority.clamp(6);
        }
        if (routine == ROUTINE_INVISIBLE_GRABBER) {
            return RenderPriority.clamp(RenderPriority.MIN);
        }
        return RenderPriority.clamp(4);
    }

    @Override
    public boolean isPersistent() {
        return routine == ROUTINE_SCZ_MAIN || routine == ROUTINE_WFZ_START || routine == ROUTINE_WFZ_END;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        renderThisFrame = false;
        solidActive = false;
        highPriority = false;

        switch (routine) {
            case ROUTINE_SCZ_MAIN -> updateSczMain(player);
            case ROUTINE_WFZ_START -> {
                updateWfzStart(frameCounter, player);
                applyDeleteOffScreenCulling();
            }
            case ROUTINE_WFZ_END -> updateWfzEnd(player);
            case ROUTINE_INVISIBLE_GRABBER -> updateInvisibleGrabber(player);
            case ROUTINE_BLINKER -> updateBlinker();
            case ROUTINE_UNUSED_MOVER -> updateUnusedMover();
            case ROUTINE_THRUSTER -> updateThrusterFollower();
            default -> {
                // Unknown subtype: keep object inert.
            }
        }
    }

    // ------------------------------------------------------------------------
    // Routine 2: ObjB2_Main_SCZ
    // ------------------------------------------------------------------------

    private void updateSczMain(AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }

        advanceMainAnimation();
        renderThisFrame = true;
        solidActive = true;
        highPriority = player.isHighPriority();

        boolean mainStandingNow = isMainPlayerStanding(player);
        standingTransition = (mainStandingNow != lastMainStanding);
        lastMainStanding = mainStandingNow;

        moveWithPlayer(player, mainStandingNow);
        moveObeyPlayer(player, mainStandingNow);

        // ROM: Camera_Min_X_pos = Camera_X_pos
        Camera camera = Camera.getInstance();
        int cameraX = camera.getX();
        camera.setMinX((short) cameraX);

        // Keep player near front edge of camera while riding Tornado.
        int playerX = player.getCentreX();
        if (playerX <= cameraX + SCZ_PLAYER_PUSH_MARGIN) {
            player.setCentreX((short) (playerX + 1));
            playerX++;
        }

        if (cameraX >= SCZ_CAMERA_FINISH_X) {
            if (playerX >= SCZ_PLAYER_FINISH_X) {
                if (!sczTransitionRequested) {
                    LevelManager.getInstance().requestZoneAndAct(Sonic2ZoneConstants.ZONE_WFZ, 0, true);
                    sczTransitionRequested = true;
                }
            } else {
                applyScriptInput(player, INPUT_RIGHT, true);
            }
            camera.setMaxX((short) cameraX);
            return;
        }

        clearScriptInput(player);
        camera.setMaxX((short) (cameraX - SCZ_CAMERA_MAX_OFFSET));
    }

    // ------------------------------------------------------------------------
    // Routine 4: ObjB2_Main_WFZ_Start
    // ------------------------------------------------------------------------

    private void updateWfzStart(int frameCounter, AbstractPlayableSprite player) {
        advanceMainAnimation();
        renderThisFrame = true;
        solidActive = true;
        highPriority = true;

        switch (routineSecondary) {
            case 0 -> {
                // ObjB2_Main_WFZ_Start_init
                routineSecondary = 2;
                scriptTimer = WFZ_START_MAIN_TIMER;
                xVel = 0x100;
            }
            case 2 -> {
                // ObjB2_Main_WFZ_Start_main
                scriptTimer--;
                if (scriptTimer >= 0) {
                    objectMove();
                    applyTornadoParallaxVelocity();
                    clampClosestPlayerHorizontal();
                    return;
                }
                routineSecondary = 4;
                scriptTimer = WFZ_SHOT_DOWN_TIMER;
                smokeSpawnTimer = 1;
                xVel = 0x100;
                yVel = 0x100;
            }
            case 4 -> {
                // ObjB2_Main_WFZ_Start_shot_down
                if ((frameCounter & WFZ_SCATTER_SFX_MASK) == 0) {
                    AudioManager.getInstance().playSfx(Sonic2Sfx.RING_SPILL.id);
                }

                scriptTimer--;
                if (scriptTimer >= 0) {
                    alignPlaneAndSolid();
                    updateShotDownSmoke();
                    return;
                }

                routineSecondary = 6;
                releasePlayersFromPlatform();
            }
            case 6 -> {
                // ObjB2_Main_WFZ_Start_fall_down
                objectMove();         // Extra ObjectMove before shared align path (ROM exact flow)
                alignPlaneAndSolid();
                updateShotDownSmoke();
            }
            default -> {
                // No-op.
            }
        }
    }

    private void updateShotDownSmoke() {
        smokeSpawnTimer--;
        if (smokeSpawnTimer != 0) {
            return;
        }
        smokeSpawnTimer = WFZ_SMOKE_PERIOD;
        spawnSmokeObject();
    }

    // ------------------------------------------------------------------------
    // Routine 6: ObjB2_Main_WFZ_End
    // ------------------------------------------------------------------------

    private void updateWfzEnd(AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }

        // WFZ end uses forced scripted controls throughout.
        applyScriptInput(player, 0, true);
        advanceMainAnimation();
        highPriority = true;

        switch (routineSecondary) {
            case 0 -> wfzWaitLeaderPosition(player);
            case 2 -> wfzMoveLeaderEdge(player);
            case 4 -> wfzWaitForPlane(player);
            case 6 -> wfzPrepareToJump(player);
            case 8 -> wfzJumpToPlane(player);
            case 0x0A -> wfzLandedOnPlane(player);
            case 0x0C -> wfzApproachingShip(player);
            case 0x0E -> wfzJumpToShip(player);
            case 0x10 -> wfzDockOnDez();
            default -> {
                // Unknown state.
            }
        }
    }

    private void wfzWaitLeaderPosition(AbstractPlayableSprite player) {
        if (player.getCentreY() < WFZ_WAIT_PLAYER_Y) {
            return;
        }

        leaderWaitCounter++;
        if (leaderWaitCounter < WFZ_WAIT_FRAMES) {
            return;
        }

        routineSecondary = 2;
        currentX = 0x2E58;
        currentY = 0x66C;
        syncFixedFromPosition();
        applyWaitingAnimation(player);

        // LoadChildObject sequence from ObjB2_Wait_Leader_position.
        TornadoObjectInstance child56 = spawnTornadoChild(SUBTYPE_INVISIBLE_GRABBER, 0x3118, 0x03F0);
        if (child56 != null) {
            // No additional setup.
        }
        spawnTornadoChild(SUBTYPE_BLINKER, 0x3070, 0x03B0);
        spawnTornadoChild(SUBTYPE_BLINKER, 0x3070, 0x0430);
        thrusterFollowerChild = spawnTornadoChild(SUBTYPE_THRUSTER, 0x0000, 0x0000);
    }

    private void wfzMoveLeaderEdge(AbstractPlayableSprite player) {
        if (player.getCentreX() < WFZ_LEADER_EDGE_X) {
            applyScriptInput(player, INPUT_RIGHT, true);
            return;
        }

        routineSecondary = 4;
        applyScriptInput(player, 0, true);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        applyWaitingAnimation(player);
    }

    private void wfzWaitForPlane(AbstractPlayableSprite player) {
        int bgX = ParallaxManager.getInstance().getCameraBgXOffset();
        if (bgX < WFZ_PLANE_WAIT_BG_X) {
            applyWaitingAnimation(player);
            return;
        }

        routineSecondary = 6;
        xVel = 0x100;
        yVel = -0x100;
        scriptTimer = 0;
        applyWaitingAnimation(player);
    }

    private void wfzPrepareToJump(AbstractPlayableSprite player) {
        applyWaitingAnimation(player);
        scriptTimer++;
        if (scriptTimer == WFZ_PREPARE_TO_JUMP_FRAMES) {
            routineSecondary = 8;
            jumpTimer = player.isSuperSonic() ? WFZ_JUMP_TIMER_SUPER : WFZ_JUMP_TIMER_NORMAL;
        }

        alignPlaneAndSolid();
        renderThisFrame = true;
    }

    private void wfzJumpToPlane(AbstractPlayableSprite player) {
        scriptTimer++;
        jumpTimer--;
        if (jumpTimer >= 0) {
            applyScriptInput(player, INPUT_RIGHT | INPUT_JUMP, true);
        } else {
            applyScriptInput(player, 0, true);
        }

        alignPlaneAndSolid();
        renderThisFrame = true;

        if (isMainPlayerStanding(player)) {
            routineSecondary = 0x0A;
            jumpTimer = 0x20;
            applyJumpToPlaneLayoutPatch();
        }
    }

    private void wfzLandedOnPlane(AbstractPlayableSprite player) {
        scriptTimer++;
        if (scriptTimer >= WFZ_LANDED_WAIT_FRAMES) {
            routineSecondary = 0x0C;
            if (thrusterFollowerChild != null) {
                thrusterFollowerChild.routineSecondary = 2;
            }
        }

        // Keep Sonic centered on Tornado while dock sequence starts.
        player.setCentreX((short) currentX);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setAir(false);
        player.setRolling(false);
        applyWaitingAnimation(player);

        alignPlaneAndSolid();
        renderThisFrame = true;
    }

    private void wfzApproachingShip(AbstractPlayableSprite player) {
        applyWaitingAnimation(player);
        if (scriptTimer >= WFZ_JUMP_TO_SHIP_START) {
            routineSecondary = 0x0E;
        }
        wfzJumpToShipCommon();
    }

    private void wfzJumpToShip(AbstractPlayableSprite player) {
        applyWaitingAnimation(player);
        wfzJumpToShipCommon();
    }

    private void wfzJumpToShipCommon() {
        if (scriptTimer < WFZ_JUMP_TO_SHIP_END) {
            AbstractPlayableSprite player = getMainPlayer();
            applyScriptInput(player, INPUT_JUMP, true);
        } else {
            AbstractPlayableSprite player = getMainPlayer();
            applyScriptInput(player, 0, true);
        }

        if (scriptTimer >= WFZ_SPAWN_EXTRA_CHILDREN_AT && !spawnedWfzDockChildren) {
            spawnedWfzDockChildren = true;
            Sonic2LevelEventManager.getInstance().setEventRoutine(6);
            routineSecondary = 0x10;
            spawnTornadoChild(SUBTYPE_BLINKER, 0x3090, 0x03D0);
            spawnTornadoChild(SUBTYPE_BLINKER, 0x30C0, 0x03F0);
            spawnTornadoChild(SUBTYPE_BLINKER, 0x3090, 0x0410);
        }

        wfzDockOnDez();
    }

    private void wfzDockOnDez() {
        if (scriptTimer >= WFZ_START_DEZ_AT) {
            if (!dezTransitionRequested) {
                LevelManager.getInstance().requestZoneAndAct(Sonic2ZoneConstants.ZONE_DEZ, 0, true);
                dezTransitionRequested = true;
            }
            return;
        }

        scriptTimer++;

        int thresholdIndex = dockVelocityIndex / 2;
        if (thresholdIndex < WFZ_DOCK_THRESHOLDS.length
                && scriptTimer >= WFZ_DOCK_THRESHOLDS[thresholdIndex]) {
            dockVelocityIndex += 2;
            if (dockVelocityIndex + 1 < WFZ_DOCK_VELOCITY_BYTES.length) {
                // ROM move.b to x_vel/y_vel writes signed integer speed into the high byte.
                xVel = WFZ_DOCK_VELOCITY_BYTES[dockVelocityIndex] << 8;
                yVel = WFZ_DOCK_VELOCITY_BYTES[dockVelocityIndex + 1] << 8;
            }
        }

        alignPlaneAndSolid();
        renderThisFrame = true;
    }

    // ------------------------------------------------------------------------
    // Routine 8: ObjB2_Invisible_grabber
    // ------------------------------------------------------------------------

    private void updateInvisibleGrabber(AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }

        if (routineSecondary < 4) {
            if (!grabberTriggered && checkGrabberCollision(player)) {
                grabberTriggered = true;
                routineSecondary = 4;

                Camera.getInstance().setYPosBias((short) ((224 / 2) + 8));

                player.setXSpeed((short) 0);
                player.setYSpeed((short) 0);
                player.setCentreX((short) (currentX - 0x10));
                player.setDirection(Direction.LEFT);
                player.setAir(false);
                player.setRolling(false);
                player.setAnimationId(Sonic2AnimationIds.HANG);
                player.setAnimationFrameIndex(0);
                player.setAnimationTick(0);
                player.setObjectControlled(true);
                player.setControlLocked(true);
                ownsPlayerControl = true;
            }
            return;
        }

        // loc_3ACF2: keep player attached while hanging.
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setCentreX((short) (currentX - 0x10));
    }

    private boolean checkGrabberCollision(AbstractPlayableSprite player) {
        int dx = Math.abs(player.getCentreX() - currentX);
        int dy = Math.abs(player.getCentreY() - currentY);
        return dx <= 0x10 && dy <= 0x20;
    }

    // ------------------------------------------------------------------------
    // Routine A: loc_3AD0C (blinking child)
    // ------------------------------------------------------------------------

    private void updateBlinker() {
        // ROM toggles status.npc.misc and displays every other frame.
        blinkerVisible = !blinkerVisible;
        renderThisFrame = blinkerVisible;
    }

    // ------------------------------------------------------------------------
    // Routine C: loc_3AD2A (simple mover)
    // ------------------------------------------------------------------------

    private void updateUnusedMover() {
        objectMove();
        if (checkMarkObjGone()) {
            setDestroyed(true);
            renderThisFrame = false;
            return;
        }
        renderThisFrame = true;
    }

    // ------------------------------------------------------------------------
    // Routine E: loc_3AD42 (thruster follower child)
    // ------------------------------------------------------------------------

    private void updateThrusterFollower() {
        if (parent == null || parent.isDestroyed()) {
            setDestroyed(true);
            return;
        }

        currentX = parent.currentX - 0x0C;
        currentY = parent.currentY + 0x28;
        syncFixedFromPosition();

        renderThisFrame = true;
        if (routineSecondary >= 2) {
            advanceThrusterAnimation();
        }
    }

    // ------------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------------

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!renderThisFrame || isDestroyed()) {
            return;
        }

        String key = resolveRenderArtKey();
        if (key == null) {
            return;
        }

        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(key);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        renderer.drawFrameIndex(mappingFrame, currentX, currentY, false, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        if (routine == ROUTINE_INVISIBLE_GRABBER) {
            ctx.drawRect(currentX, currentY, 0x10, 0x20, 0.9f, 0.2f, 0.9f);
        } else {
            ctx.drawCross(currentX, currentY, 6, 0.3f, 1.0f, 1.0f);
        }
        ctx.drawWorldLabel(currentX, currentY, -1,
                String.format("B2 sub%02X r%X s%X f%d", subtype, routine, routineSecondary, mappingFrame),
                DebugColor.CYAN);
    }

    // ------------------------------------------------------------------------
    // SolidObject interfaces
    // ------------------------------------------------------------------------

    @Override
    public SolidObjectParams getSolidParams() {
        return TORNADO_SOLID_PARAMS;
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return solidActive;
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // ROM uses direct SolidObject calls from ObjB2 routines. The unified pipeline
        // handles contact resolution; this callback is intentionally no-op.
    }

    // ------------------------------------------------------------------------
    // Cleanup
    // ------------------------------------------------------------------------

    @Override
    public void onUnload() {
        if (!ownsPlayerControl) {
            return;
        }
        AbstractPlayableSprite player = getMainPlayer();
        if (player != null) {
            player.clearForcedInputMask();
            player.setControlLocked(false);
            if (player.isObjectControlled()) {
                player.setObjectControlled(false);
            }
        }
        ownsPlayerControl = false;
    }

    // ------------------------------------------------------------------------
    // Helpers: movement / script control / child spawning
    // ------------------------------------------------------------------------

    private void moveWithPlayer(AbstractPlayableSprite player, boolean mainStandingNow) {
        if (mainStandingNow) {
            moveVert();
            applyVerticalLimit();
            objectMove();
            applyTornadoParallaxVelocity();
            return;
        }

        int anchorX = player.getCentreX();
        if (standingTransition) {
            Orientation orientation = getOrientationToClosestPlayer(player);
            smoothOffsetX = orientation.signedDistanceX();
            anchorX = orientation.target().getCentreX();
        }

        if (smoothOffsetX != 0) {
            smoothOffsetX += (smoothOffsetX < 0) ? 1 : -1;
        }

        currentX = anchorX + smoothOffsetX;
        syncFixedFromPosition();
        applyTornadoParallaxVelocity();
    }

    private void moveObeyPlayer(AbstractPlayableSprite player, boolean mainStandingNow) {
        if (mainStandingNow) {
            if (!moveVertActive) {
                yVel = 0;
                int requestedVel = 0;
                if (player.isDownPressed()) {
                    requestedVel = 0x80;
                } else if (player.isUpPressed()) {
                    requestedVel = -0x80;
                }
                if (requestedVel != 0) {
                    yVel = requestedVel;
                    applyVerticalLimit();
                    objectMove();
                }
            }
        }

        // ROM: ObjB2_Move_obbey_player (loc_3AE94) moves TORNADO to follow PLAYER.
        // move.w x_pos(a1),d1 / add.w d3,d1 / move.w d1,x_pos(a0)
        // d3 = ±16 based on player orientation relative to tornado.
        // The TORNADO follows the PLAYER (not vice versa).
        if (mainStandingNow) {
            Orientation orientation = getOrientationToClosestPlayer(player);
            if (orientation.absDistanceX() >= PLAYER_HORIZONTAL_CLAMP
                    && Math.abs(orientation.target().getGSpeed()) < PLAYER_INERTIA_CLAMP) {
                int targetX = orientation.target().getCentreX()
                        + (orientation.playerIsRight() ? -PLAYER_HORIZONTAL_CLAMP : PLAYER_HORIZONTAL_CLAMP);
                currentX = targetX;
                syncFixedFromPosition();

                // Refresh SolidContacts tracking position so the follow delta isn't
                // double-applied as a riding delta. In the ROM, SolidObject runs inline
                // before the horizontal follow, so it never sees this delta.
                ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
                if (objectManager != null) {
                    objectManager.refreshRidingTrackingPosition(this);
                }
            }
        }
        if (mainStandingNow) {
            return;
        }

        moveVert2();
    }

    private void moveVert() {
        if (!moveVertActive) {
            if (!standingTransition) {
                return;
            }
            moveVertActive = true;
            moveVert2Active = false;
            yVel = 0x200;
            moveVertTimer = 0x14;
        }

        moveVertTimer--;
        if (moveVertTimer < 0) {
            moveVertActive = false;
            yVel = 0;
            return;
        }

        if (yVel > -0x100) {
            yVel -= 0x20;
        }
    }

    private void moveVert2() {
        if (!moveVert2Active) {
            if (!standingTransition) {
                return;
            }
            moveVert2Active = true;
            moveVertActive = false;
            yVel = 0x200;
            moveVertTimer = 0x2B;
        }

        moveVertTimer--;
        if (moveVertTimer < 0) {
            moveVert2Active = false;
            yVel = 0;
            return;
        }

        if (yVel > -0x100) {
            yVel -= 0x20;
        }
        applyVerticalLimit();
        objectMove();
    }

    private void applyVerticalLimit() {
        if (yVel == 0) {
            return;
        }

        int cameraY = Camera.getInstance().getY();
        if (yVel < 0) {
            int upper = cameraY + VERTICAL_LIMIT_UP_OFFSET;
            if (currentY < upper) {
                yVel = 0;
            }
            return;
        }

        int lower = cameraY + VERTICAL_LIMIT_DOWN_OFFSET;
        if (currentY >= lower) {
            yVel = 0;
        }
    }

    private void clampClosestPlayerHorizontal() {
        AbstractPlayableSprite main = getMainPlayer();
        if (main == null) {
            return;
        }
        Orientation orientation = getOrientationToClosestPlayer(main);
        if (orientation.absDistanceX() < PLAYER_HORIZONTAL_CLAMP) {
            return;
        }
        int targetX = currentX + (orientation.playerIsRight() ? PLAYER_HORIZONTAL_CLAMP : -PLAYER_HORIZONTAL_CLAMP);
        orientation.target().setCentreX((short) targetX);
    }

    private void alignPlaneAndSolid() {
        objectMove();
        applyTornadoParallaxVelocity();
        solidActive = true;
    }

    private void applyTornadoParallaxVelocity() {
        ParallaxManager pm = ParallaxManager.getInstance();
        int vx = pm.getTornadoVelocityX();
        int vy = pm.getTornadoVelocityY();
        currentX += vx;
        currentY += vy;
        xPosFixed8 += (vx << 8);
        yPosFixed8 += (vy << 8);
    }

    private void objectMove() {
        xPosFixed8 += xVel;
        yPosFixed8 += yVel;
        currentX = xPosFixed8 >> 8;
        currentY = yPosFixed8 >> 8;
    }

    private void applyDeleteOffScreenCulling() {
        if (!checkMarkObjGone()) {
            return;
        }
        setDestroyed(true);
        renderThisFrame = false;
        solidActive = false;
    }

    /**
     * Replicates Obj_DeleteOffScreen/MarkObjGone X-range deletion:
     * ((x_pos & $FF80) - Camera_X_pos_coarse) > $280 (unsigned compare).
     */
    private boolean checkMarkObjGone() {
        Camera camera = Camera.getInstance();
        if (camera == null) {
            return false;
        }
        int cameraXCoarse = camera.getX() & 0xFF80;
        int objectXCoarse = currentX & 0xFF80;
        int diff = (objectXCoarse - cameraXCoarse) & 0xFFFF;
        return diff > OFFSCREEN_DISTANCE;
    }

    private void syncFixedFromPosition() {
        xPosFixed8 = currentX << 8;
        yPosFixed8 = currentY << 8;
    }

    private void advanceMainAnimation() {
        int[] script = (animId == 0) ? MAIN_ANIM_A : MAIN_ANIM_B;
        mappingFrame = script[animFrameIndex];
        animFrameIndex++;
        if (animFrameIndex >= script.length) {
            animFrameIndex = 0;
        }
    }

    private void advanceThrusterAnimation() {
        mappingFrame = THRUSTER_ANIM[animFrameIndex];
        animFrameIndex++;
        if (animFrameIndex >= THRUSTER_ANIM.length) {
            animFrameIndex = 0;
        }
    }

    private void applyWaitingAnimation(AbstractPlayableSprite player) {
        player.setAnimationId(Sonic2AnimationIds.WAIT);
        player.setAnimationFrameIndex(0);
        player.setAnimationTick(0);
    }

    private void applyScriptInput(AbstractPlayableSprite player, int forcedMask, boolean lock) {
        if (player == null) {
            return;
        }
        player.setControlLocked(lock);
        if (forcedMask == 0) {
            player.clearForcedInputMask();
        } else {
            player.setForcedInputMask(forcedMask & (INPUT_UP | INPUT_DOWN | INPUT_RIGHT | INPUT_JUMP));
        }
        if (lock || forcedMask != 0) {
            ownsPlayerControl = true;
        }
    }

    private void clearScriptInput(AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }
        player.clearForcedInputMask();
        player.setControlLocked(false);
    }

    private boolean isMainPlayerStanding(AbstractPlayableSprite player) {
        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
        return objectManager != null && objectManager.isRidingObject(player, this);
    }

    private void releasePlayersFromPlatform() {
        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
        if (objectManager == null) {
            return;
        }

        AbstractPlayableSprite main = getMainPlayer();
        if (main != null && objectManager.isRidingObject(main, this)) {
            objectManager.clearRidingObject(main);
            main.setOnObject(false);
            main.setAir(true);
        }

        for (AbstractPlayableSprite sidekick : SpriteManager.getInstance().getSidekicks()) {
            if (objectManager.isRidingObject(sidekick, this)) {
                objectManager.clearRidingObject(sidekick);
                sidekick.setOnObject(false);
                sidekick.setAir(true);
            }
        }
    }

    private Orientation getOrientationToClosestPlayer(AbstractPlayableSprite mainPlayer) {
        AbstractPlayableSprite closest = mainPlayer;
        int signedMain = currentX - mainPlayer.getCentreX();
        int absMain = Math.abs(signedMain);

        // ROM: Obj_GetOrientationToPlayer always considers both players,
        // returning the closest one. No zone-specific filtering.
        for (AbstractPlayableSprite sidekick : SpriteManager.getInstance().getSidekicks()) {
            if (!sidekick.getDead()) {
                int signedSidekick = currentX - sidekick.getCentreX();
                int absSidekick = Math.abs(signedSidekick);
                if (absSidekick < absMain) {
                    return new Orientation(sidekick, signedSidekick);
                }
            }
        }

        return new Orientation(closest, signedMain);
    }

    private void spawnSmokeObject() {
        ObjectManager manager = LevelManager.getInstance().getObjectManager();
        if (manager == null) {
            return;
        }
        ObjectSpawn smokeSpawn = new ObjectSpawn(
                currentX, currentY, 0xC3, 0x90, spawn.renderFlags(), false, spawn.rawYWord());
        manager.addDynamicObject(new TornadoSmokeObjectInstance(smokeSpawn));
    }

    private TornadoObjectInstance spawnTornadoChild(int childSubtype, int x, int y) {
        ObjectManager manager = LevelManager.getInstance().getObjectManager();
        if (manager == null) {
            return null;
        }

        ObjectSpawn childSpawn = new ObjectSpawn(
                x, y, Sonic2ObjectIds.TORNADO, childSubtype, spawn.renderFlags(), false, spawn.rawYWord());
        TornadoObjectInstance child = new TornadoObjectInstance(childSpawn, this);
        manager.addDynamicObject(child);
        return child;
    }

    private void applyJumpToPlaneLayoutPatch() {
        if (levelLayoutPatched) {
            return;
        }

        LevelManager levelManager = LevelManager.getInstance();
        Level level = levelManager.getCurrentLevel();
        if (level == null) {
            return;
        }
        Map map = level.getMap();
        if (map == null) {
            return;
        }

        boolean wroteAny = false;
        for (int i = 0; i < LAYOUT_PATCH_OFFSETS.length; i++) {
            int baseOffset = LAYOUT_PATCH_OFFSETS[i];
            int[] bytes = LAYOUT_PATCH_BYTES[i];
            for (int j = 0; j < bytes.length; j++) {
                int offset = baseOffset + j;
                int x = offset % LAYOUT_WIDTH;
                int y = offset / LAYOUT_WIDTH;
                try {
                    map.setValue(0, x, y, (byte) bytes[j]);
                    wroteAny = true;
                } catch (IllegalArgumentException ignored) {
                    // Some layouts may differ; keep behavior best-effort.
                }
            }
        }

        if (wroteAny) {
            levelManager.invalidateForegroundTilemap();
        }
        if (wroteAny) {
            levelLayoutPatched = true;
        }
    }

    private AbstractPlayableSprite getMainPlayer() {
        return Camera.getInstance().getFocusedSprite();
    }

    private String resolveRenderArtKey() {
        return switch (subtype) {
            case SUBTYPE_SCZ_MAIN, SUBTYPE_WFZ_START, SUBTYPE_WFZ_END -> Sonic2ObjectArtKeys.TORNADO;
            case SUBTYPE_BLINKER -> Sonic2ObjectArtKeys.WFZ_THRUST;
            case SUBTYPE_UNUSED_MOVER -> Sonic2ObjectArtKeys.CLOUDS;
            case SUBTYPE_THRUSTER -> Sonic2ObjectArtKeys.TORNADO_THRUSTER;
            default -> null;
        };
    }

    private record Orientation(AbstractPlayableSprite target, int signedDistanceX) {
        int absDistanceX() {
            return Math.abs(signedDistanceX);
        }

        boolean playerIsRight() {
            return signedDistanceX < 0;
        }
    }

    // ------------------------------------------------------------------------
    // ObjC3-like smoke child used by WFZ start shot-down state.
    // ------------------------------------------------------------------------

    private static final class TornadoSmokeObjectInstance extends AbstractObjectInstance {
        private static final int ANIM_DELAY = 7;
        private static final int MAX_FRAME = 4;
        private static final int PRIORITY = 5;

        private int currentX;
        private int currentY;
        private int xPosFixed8;
        private int yPosFixed8;
        private int xVel;
        private int yVel;
        private int mappingFrame;
        private int frameTimer;

        private TornadoSmokeObjectInstance(ObjectSpawn spawn) {
            super(spawn, "TornadoSmoke");

            int randomOffset = ThreadLocalRandom.current().nextInt(8) * 4; // RNG_seed & $1C
            this.currentX = spawn.x() - randomOffset;
            this.currentY = spawn.y() + 0x10;
            this.xPosFixed8 = currentX << 8;
            this.yPosFixed8 = currentY << 8;
            this.xVel = -0x100;
            this.yVel = -0x100;
            this.mappingFrame = 0;
            this.frameTimer = ANIM_DELAY;
        }

        @Override
        public int getX() {
            return currentX;
        }

        @Override
        public int getY() {
            return currentY;
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(PRIORITY);
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            xPosFixed8 += xVel;
            yPosFixed8 += yVel;
            currentX = xPosFixed8 >> 8;
            currentY = yPosFixed8 >> 8;

            frameTimer--;
            if (frameTimer >= 0) {
                return;
            }

            frameTimer = ANIM_DELAY;
            mappingFrame++;
            if (mappingFrame > MAX_FRAME) {
                setDestroyed(true);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed()) {
                return;
            }

            ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.EXPLOSION);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(mappingFrame, currentX, currentY, false, false);
        }

        @Override
        public void appendDebugRenderCommands(DebugRenderContext ctx) {
            ctx.drawCross(currentX, currentY, 3, 1.0f, 0.4f, 0.2f);
            ctx.drawWorldLabel(currentX, currentY, -1, "C3 f" + mappingFrame, DebugColor.ORANGE);
        }
    }
}
