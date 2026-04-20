package com.openggf.game.sonic3k.objects;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.debug.DebugRenderContext;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Object 0x5B — MGZ Top Platform / Top Launcher.
 *
 * <p>ROM: {@code Obj_MGZTopPlatform} (sonic3k.asm:71475-72040).
 *
 * <p>Full port of the platform's state machines:
 * <ul>
 *   <li>{@code sub_34EEC} — per-player state machine with states 0 (landing
 *       detection), 2 (approach), 4 (grabbed), 6 (post-release).</li>
 *   <li>Airborne body (loc_34C98): gravity + MoveSprite2 + ground probe
 *       (sub_3526A). Airborne bit is only cleared when the floor probe
 *       actually snaps, not on every contact.</li>
 *   <li>Ground-ride (loc_34D1E): angle-driven {@code x_vel/y_vel} from
 *       {@code ground_vel}, wall probe, MoveSprite2, floor angle follow.</li>
 *   <li>Arc-travel (sub_35868): sine-interpolated teleport between waypoints
 *       read at {@code word_35784}/{@code word_357F6}.</li>
 *   <li>{@code sub_35202} post-sync: snaps grabbed player's X and Y to the
 *       platform after every frame's motion, preserving the ROM's ride feel.</li>
 * </ul>
 *
 * <p>Subtype != 0 spawns (from {@code Obj_MGZTopLauncher}, ID 0x5C) run the
 * solid collision + standing state machine but no autonomous motion.
 */
public class MGZTopPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {
    private static final String ART_KEY = Sonic3kObjectArtKeys.MGZ_TOP_PLATFORM;

    // ROM: move.w #$280, priority(a0)
    private static final int PRIORITY_BUCKET = 5;

    // ROM: move.b #$18, width_pixels(a0); move.b #$C, height_pixels(a0)
    private static final int WIDTH_PIXELS = 0x18;
    private static final int HEIGHT_PIXELS = 0x0C;
    // ROM sub_3526A: airborne probe uses y_radius=$1F with a $13 y_pos offset (loc_34C98).
    private static final int AIRBORNE_Y_RADIUS = 0x1F;
    private static final int AIRBORNE_Y_OFFSET = 0x13;
    // ROM sub_34E6E / sub_3526A radii.
    private static final int GROUND_X_RADIUS = 0x0A;
    private static final int GROUND_Y_RADIUS = 0x0C;
    private static final int WALL_X_RADIUS = 0x18;
    private static final int WALL_Y_OFFSET = 0x0C;

    // Animation ROM: mapping_frame = ($24 >> 3) & 1
    private static final int ANIM_TIMER_SHIFT = 3;

    // Grab radius ROM: cmpi.w #$10, d0; bhs (skip) — biased by $0F if player was right of centre.
    private static final int GRAB_RADIUS = 0x10;
    private static final int GRAB_ENTRY_BIAS = 0x0F;

    // Jump-launch ROM: move.w #-$680, y_vel(a1)
    private static final short LAUNCH_Y_VEL = (short) -0x680;

    // Player mini-motion (sub_35504): d5=$C accel, d4=$80 skid, d6=$600 max.
    private static final int PLAYER_ACCEL = 0x0C;
    private static final int PLAYER_SKID = 0x80;
    private static final int PLAYER_MAX_SPEED = 0x600;

    // Lateral-launch gate / y-kick constants (ROM loc_35130/loc_350A6..).
    private static final int LATERAL_SOFT_CAP = 0x200;
    private static final int LATERAL_Y_KICK = 8;
    private static final int LATERAL_Y_MIN = -0x100;

    // Dash-on-landing thresholds ROM: cmpi.w #$40 / #$100 / #$800 / #$C00.
    private static final int DASH_THRESHOLD = 0x40;
    private static final int DASH_SPEED_LOW = 0x800;
    private static final int DASH_SPEED_HIGH = 0xC00;
    private static final int DASH_HIGH_THRESHOLD = 0x100;

    // Gravity ROM: addi.w #8 / $200 cap.
    private static final int GRAVITY = 8;
    private static final int MAX_Y_VEL = 0x200;
    private static final int RELEASE_GRAVITY = 0x38;

    // Ground-ride slope clamp ROM: cmpi.b #$1E (sub_34E6E).
    private static final int MAX_GROUND_ANGLE = 0x1E;

    // Waypoint table layout: count word (count - 1) + N × 16-byte entries.
    private static final int WAYPOINT_MATCH_WINDOW = 0x20;
    private static final int WAYPOINT_MATCH_HALF = 0x10;
    private static final int WAYPOINT_WORDS_PER_ENTRY = 8;

    /** Per-player sub_34EEC state. */
    private static final class PlayerGrabState {
        int routine;            // (a4): 0 / 2 / 4 / 6
        int entrySideBias;      // 1(a4): 0 = no bias; $F = came from right
        boolean standingNow;    // set by onSolidContact each frame
        boolean jumpHeldAtGrab; // edge-detect for release
        boolean grabbed;        // true while routine == 4 (fast check)
        int xSub;
        int ySub;
    }

    private record ProbeResult(int distance, int angle) {
    }

    private int currentSubtype;
    private boolean bodyDriven;

    // Platform state (ROM a0 fields) ===================================
    private int posX;
    private int posY;
    private int homeX;          // $30(a0)
    private int homeY;          // $32(a0)
    private int groundVel;       // ground_vel(a0)
    private int xVel;            // x_vel(a0)
    private int yVel;            // y_vel(a0)
    private int angle;           // angle(a0), signed byte wrapped to 0..$FF
    private int timer;           // $24(a0)
    private int rolling;         // $34(a0)
    private int arcMode;         // $35(a0)
    private int arcProgress;     // $3C(a0)
    private int arcLimit;        // $2E(a0)
    private int arcFlagsHi;      // $3E(a0)
    private int arcFlagsLo;      // $3F(a0)
    private int arcTimer;        // $3A(a0)
    private int arcDataIndex;    // decoded form of $36(a0)
    private int arcXSub;         // 16.16 subpixel used by sub_35868 linear legs
    private int arcYSub;         // 16.16 subpixel used by sub_35868 linear legs
    private int[] activeWaypointTable;
    private boolean airborne;    // status bit 1
    private boolean carryLatched; // ROM: $2D(a0), consumed by next body update
    private boolean nextCarryLatched;
    /**
     * Set when {@code sub_3519A} has flipped the platform into post-release drift
     * mode ({@code loc_34D92}). The platform then only runs {@code MoveSprite} and
     * the off-screen test; state machines and physics are disabled.
     */
    private boolean releasedFlight;
    private final SubpixelMotion.State motion;

    // Per-player state.
    private final Map<PlayableEntity, PlayerGrabState> playerStates = new IdentityHashMap<>();

    // Waypoints (lazy).
    private int[] waypointAct1;
    private int[] waypointAct2;
    private int firstActivatedWaypointEntryStart = -1;
    private int lastActivatedWaypointEntryStart = -1;
    private int waypointActivationCount;

    public MGZTopPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "MGZTopPlatform");
        this.currentSubtype = spawn.subtype() & 0xFF;
        this.bodyDriven = (currentSubtype == 0);
        this.posX = spawn.x();
        this.posY = spawn.y();
        this.homeX = posX;
        this.homeY = posY;
        this.motion = new SubpixelMotion.State(posX, posY, 0, 0, 0, 0);
    }

    @Override public int getX() { return posX; }
    @Override public int getY() { return posY; }
    @Override public int getPriorityBucket() { return RenderPriority.clamp(PRIORITY_BUCKET); }

    // =============================================================
    // Solid collision
    // =============================================================

    @Override
    public SolidObjectParams getSolidParams() {
        // ROM loc_34F04: d1 = width+$B; d2 = height; d3 = height+1.
        return new SolidObjectParams(WIDTH_PIXELS + 0x0B, HEIGHT_PIXELS, HEIGHT_PIXELS + 1);
    }

    @Override
    public boolean isSolidFor(PlayableEntity player) {
        // ROM state 4 grab: bclr #Status_OnObj / bset #Status_InAir on player, and
        // bclr d6,status(a0) on platform -> effectively disables solid coupling for
        // the grabbed player. We mirror by dropping solidity for that player.
        PlayerGrabState s = playerStates.get(player);
        return s == null || !s.grabbed;
    }

    /**
     * Read-only seam for headless parity tests to detect MGZ carried-player ownership
     * without reflective access to {@code playerStates}.
     */
    public boolean isPlayerGrabbed(PlayableEntity player) {
        PlayerGrabState state = playerStates.get(player);
        return state != null && state.grabbed;
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        if (playerEntity == null) {
            return;
        }
        PlayerGrabState state = playerStates.computeIfAbsent(playerEntity, k -> new PlayerGrabState());
        if (contact.standing()) {
            state.standingNow = true;
            // ROM state 0 -> 2 transition (loc_34F2A): entrySide = (platform < player) ? 1 : 0.
            // I.e., player to the right of platform -> bias = $F.
            if (state.routine == 0) {
                int platformMinusPlayer = posX - playerEntity.getCentreX();
                state.entrySideBias = (platformMinusPlayer < 0) ? GRAB_ENTRY_BIAS : 0;
            }
        }
        // ROM SolidObjectFull: while the wall-cling bit is set (grabbed), a horizontal
        // push records a side-contact flag and a ceiling hit records a top-contact
        // flag. The grabbed state machine reads and clears these the same frame to
        // zero ground_vel / y_vel.
        if (!(playerEntity instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        if (!sprite.isWallCling()) {
            return;
        }
        if (contact.touchSide() && contact.movingInto()) {
            sprite.setWallClingSideContact(true);
        }
        if (contact.touchBottom()) {
            sprite.setWallClingTopContact(true);
        }
    }

    // =============================================================
    // Main update
    // =============================================================

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (isDestroyed()) {
            return;
        }

        // ROM loc_34D92: post-release drift. MoveSprite + anim + offscreen test only.
        if (releasedFlight) {
            runReleasedFlight();
            return;
        }

        AbstractPlayableSprite primary = (playerEntity instanceof AbstractPlayableSprite p) ? p : null;
        AbstractPlayableSprite sidekick = resolveSidekick();

        // ROM loc_34C54: sub_34EEC is invoked for P1 then P2 BEFORE platform motion.
        if (primary != null) {
            runPlayerStateMachine(primary, true);
        }
        if (sidekick != null) {
            runPlayerStateMachine(sidekick, false);
        }

        // A P1 jump-launch inside the state machine may have flipped the platform to
        // post-release drift; honour that immediately (ROM: sub_3519A overwrites (a0)).
        if (releasedFlight) {
            runReleasedFlight();
            return;
        }

        // Platform body — subtype != 0 stays passive until Obj_MGZTopLauncher releases it.
        if (bodyDriven) {
            if (arcMode != 0) {
                runArcTravel();
            } else if (airborne) {
                runAirborne();
            } else {
                runGroundPath();
            }
        }

        // ROM loc_34D62: sub_35202 for both players (post-motion snap).
        nextCarryLatched = false;
        if (primary != null) snapGrabbedPlayer(primary);
        if (sidekick != null) snapGrabbedPlayer(sidekick);
        carryLatched = nextCarryLatched;

        // Reset per-frame standing flags; SolidContacts will re-assert next tick.
        for (PlayerGrabState ps : playerStates.values()) {
            ps.standingNow = false;
        }
        updateDynamicSpawn(posX, posY);
    }

    /**
     * ROM loc_34D92: drift with current xVel/yVel (no gravity, no state machines).
     * The ROM teleports off-screen to {@code $7F00} and then defers to
     * {@code Sprite_OnScreen_Test} for deletion; we simply destroy once the
     * platform leaves the camera window.
     */
    private void runReleasedFlight() {
        movePlatform(true, RELEASE_GRAVITY);
        timer = (timer + 4) & 0xFFFF;
        updateDynamicSpawn(posX, posY);
        if (!isChkObjectVisible()) {
            setDestroyed(true);
        }
    }

    // =============================================================
    // Platform body paths
    // =============================================================

    /**
     * ROM loc_34D0E..loc_34D5E: on-ground branch. Runs every frame — {@code sub_34E6E}
     * (floor-follow) fires unconditionally so an idle platform stays attached to the
     * track; {@code sub_34DBC}/{@code sub_35666} internally gate on {@code ground_vel}.
     */
    private void runGroundPath() {
        // ROM loc_34D1E: angle-driven velocities from ground_vel. With groundVel == 0
        // this produces zero velocity and MoveSprite2 is a no-op, but the floor probe
        // below still executes.
        int a = angle & 0xFF;
        int sin = TrigLookupTable.sinHex(a);
        int cos = TrigLookupTable.cosHex(a);
        xVel = (cos * groundVel) >> 8;
        yVel = (sin * groundVel) >> 8;

        if (groundVel != 0) {
            applyGroundWallResponse();
        }
        movePlatform(false, 0);

        // ROM tst.b $2D(a0): a carried/standing player suppresses the floor probe.
        if (!carryLatched) {
            // ROM sub_34E6E: floor-follow — ALWAYS runs (even with ground_vel == 0).
            probeGroundAngle();
        }
        timer = (timer + 4) & 0xFFFF;

        if (groundVel == 0) {
            rolling = 0;
        }

        // ROM sub_35666: waypoint check. Internally gates on rolling != 0.
        if (rolling != 0) {
            checkWaypoints();
        }
    }

    /** ROM loc_34C98..loc_34CD0: airborne branch (status bit 1). */
    private void runAirborne() {
        // Gravity ROM: cmpi.w #$200; addi.w #8.
        if (yVel < MAX_Y_VEL) {
            yVel += GRAVITY;
            if (yVel > MAX_Y_VEL) {
                yVel = MAX_Y_VEL;
            }
        }

        movePlatform(false, 0);
        switch (TrigLookupTable.calcMovementQuadrant((short) xVel, (short) yVel)) {
            case 0x40 -> runAirborneRight();
            case 0x80 -> runAirborneUp();
            case 0xC0 -> runAirborneLeft();
            default -> runAirborneDown();
        }

        if (carryLatched) {
            yVel = 0;
            xVel = 0;
            airborne = false;
            return;
        }
        if (!airborne) {
            int absX = Math.abs(xVel);
            if (absX >= DASH_THRESHOLD) {
                int speed = (absX < DASH_HIGH_THRESHOLD) ? DASH_SPEED_LOW : DASH_SPEED_HIGH;
                if (xVel < 0) speed = -speed;
                xVel = speed;
                groundVel = speed;
                rolling = 1;
            }
        }

    }

    /** ROM sub_35868: sine-arced teleport between waypoints. */
    private void runArcTravel() {
        if (arcMode != 2) {
            arcTimer--;
            if (arcTimer >= 0) {
                SubpixelMotion.State arcMotion = new SubpixelMotion.State(
                        posX,
                        posY,
                        arcXSub,
                        arcYSub,
                        xVel,
                        yVel);
                SubpixelMotion.speedToPos(arcMotion);
                posX = arcMotion.x;
                posY = arcMotion.y;
                arcXSub = arcMotion.xSub;
                arcYSub = arcMotion.ySub;
                timer = (timer + 4) & 0xFFFF;
                return;
            }
            if (arcMode == 3) {
                if ((byte) arcFlagsHi < 0) {
                    groundVel = -groundVel;
                }
                arcMode = 0;
                syncArcSubpixelsToMotion();
                timer = (timer + 4) & 0xFFFF;
                return;
            }
            arcProgress = 0;
            arcMode = 2;
            if (activeWaypointTable == null || arcDataIndex >= activeWaypointTable.length) {
                arcMode = 0;
                syncArcSubpixelsToMotion();
                timer = (timer + 4) & 0xFFFF;
                return;
            }
            arcLimit = activeWaypointTable[arcDataIndex++];
            if ((arcFlagsLo & 0x7F) != 0) {
                arcProgress = arcLimit;
            }
        }

        int phase = arcProgress << 1;
        if ((byte) arcFlagsLo < 0) {
            phase = -phase;
        }
        int sin = TrigLookupTable.sinHex(phase & 0xFF);
        posX = homeX + ((sin * 0x5800) >> 16);
        posY = homeY + arcProgress;

        int step = (Math.abs(groundVel) == DASH_SPEED_HIGH) ? 3 : 2;
        if ((arcFlagsLo & 0x7F) != 0) {
            arcProgress -= step;
            if (arcProgress > 0) {
                timer = (timer + 4) & 0xFFFF;
                return;
            }
        } else {
            arcProgress += step;
            if (arcProgress < arcLimit) {
                timer = (timer + 4) & 0xFFFF;
                return;
            }
        }

        arcMode = 3;
        if (activeWaypointTable == null || arcDataIndex + 1 >= activeWaypointTable.length) {
            arcMode = 0;
            syncArcSubpixelsToMotion();
            timer = (timer + 4) & 0xFFFF;
            return;
        }
        int tailX = activeWaypointTable[arcDataIndex++];
        int tailY = activeWaypointTable[arcDataIndex++];
        computeArcLinearVelocity(tailX, tailY);
        timer = (timer + 4) & 0xFFFF;
    }

    // =============================================================
    // Per-player state machine (sub_34EEC)
    // =============================================================

    private void runPlayerStateMachine(AbstractPlayableSprite player, boolean isPrimary) {
        PlayerGrabState state = playerStates.computeIfAbsent(player, k -> new PlayerGrabState());

        // ROM loc_34FBC safety-release: if player is dead/hurt/in debug mode, fall
        // through to loc_3500A which releases the grab and, for P1, invokes sub_3519A
        // (flip platform to released-flight drift). Same exit path as a jump-launch.
        if (state.routine == 4 && (player.getDead() || player.isHurt() || player.isDebugMode())) {
            releasePlayer(player, state, false);
            if (isPrimary) {
                enterReleasedFlight();
            }
            return;
        }

        switch (state.routine) {
            case 0 -> playerStateLanding(player, state);
            case 2 -> playerStateApproach(player, state);
            case 4 -> playerStateGrabbed(player, state, isPrimary);
            case 6 -> playerStateReleased(state);
            default -> state.routine = 0;
        }
    }

    /** ROM loc_34F04: state 0 — wait for standing, then advance to state 2. */
    private void playerStateLanding(AbstractPlayableSprite player, PlayerGrabState state) {
        if (state.standingNow) {
            state.routine = 2;
            // ROM loc_34F2A falls through into loc_34F6A on the same object tick.
            // Without that immediate approach/grab check, the passive launcher child
            // can miss the centre-crossing window and never arm the stand launcher.
            playerStateApproach(player, state);
        }
    }

    /** ROM loc_34F4C: state 2 — wait for player to cross centre, then grab. */
    private void playerStateApproach(AbstractPlayableSprite player, PlayerGrabState state) {
        // ROM loc_34F6A: if standing bit was cleared (player no longer on top),
        // state resets to 0 — but the grab-radius check (loc_34F72) still runs.
        if (!state.standingNow) {
            state.routine = 0;
        }
        int dx = player.getCentreX() - posX;
        // ROM: cmpi.w #$10, d0; bhs locret (unsigned compare).
        int d0 = (dx + state.entrySideBias) & 0xFFFF;
        if (d0 < GRAB_RADIUS) {
            grabPlayer(player, state);
        }
    }

    /**
     * ROM loc_34F84 grab initiation:
     * <ul>
     *   <li>{@code move.w x_pos(a0), x_pos(a1)} — snap player X.</li>
     *   <li>{@code move.b default_y_radius+$18, y_radius(a1)} — stretch (engine handles).</li>
     *   <li>{@code bset #0, object_control(a1)}.</li>
     *   <li>{@code bset #Status_InAir(a1) / bclr #Status_OnObj(a1)}.</li>
     *   <li>{@code bclr d6, status(a0)} — platform clears its own standing bit.</li>
     *   <li>{@code addq.b #2, (a4)} — state -> 4.</li>
     * </ul>
     *
     * <p>ROM does NOT modify player Y here — sub_35202 will snap it at end of frame.
     */
    private void grabPlayer(AbstractPlayableSprite player, PlayerGrabState state) {
        player.setCentreX((short) posX);
        // ROM sets object_control bit 7 during MGZ carry, so normal movement/collision
        // paths stop entirely while the platform owns the player.
        player.setObjectControlled(true);
        player.setControlLocked(false);
        player.setOnObject(false);
        player.setAir(true);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.applyCustomRadii(player.getXRadius(), player.getStandYRadius() + 0x18);
        ObjectServices svc = tryServices();
        if (svc != null && svc.objectManager() != null) {
            svc.objectManager().clearRidingObject(player);
        }
        player.setWallCling(true);
        player.setWallClingSideContact(false);
        player.setWallClingTopContact(false);
        state.jumpHeldAtGrab = player.isJumpPressed();
        state.grabbed = true;
        state.routine = 4;
        state.xSub = 0;
        state.ySub = 0;
    }

    /** ROM loc_34FBC: state 4. Jump / ride / centering. */
    private void playerStateGrabbed(AbstractPlayableSprite player, PlayerGrabState state, boolean isPrimary) {
        // ROM: jump (A|B|C) -> launch + release.
        boolean jumpPressed = player.isJumpPressed();
        if (state.jumpHeldAtGrab) {
            if (!jumpPressed) {
                state.jumpHeldAtGrab = false;
            }
        } else if (jumpPressed) {
            launchPlayerVertically(player, state, isPrimary);
            return;
        }

        // ROM loc_35028: if L/R held, facing is left to the normal input path; otherwise,
        // if the platform has x_vel, face per its sign (Facing bit reflects platform drift).
        // The player is object-controlled so the normal facing-from-gSpeed update doesn't
        // run — we set it explicitly here.
        if (player.isLeftPressed() && !player.isRightPressed()) {
            player.setDirection(Direction.LEFT);
        } else if (player.isRightPressed() && !player.isLeftPressed()) {
            player.setDirection(Direction.RIGHT);
        } else if (xVel != 0) {
            player.setDirection(xVel < 0 ? Direction.LEFT : Direction.RIGHT);
        }

        if (!isPrimary) {
            // ROM P2 exits here (rts in loc_35048).
            return;
        }

        // ROM loc_3505C: P1-only motion path.
        // sub_35504 (mini ground motion: accumulate ground_vel from input).
        runPlayerGroundMotion(player);
        moveGrabbedPlayer(player, state);

        if (player.getAnimationId() == Sonic3kAnimationIds.SPRING.id()) {
            xVel = player.getXSpeed();
            yVel = player.getYSpeed();
            player.setYSpeed((short) (player.getYSpeed() + RELEASE_GRAVITY));
            airborne = true;
            rolling = 0;
            return;
        }

        if (player.consumeWallClingSideContact()) {
            groundVel = 0;
            xVel = 0;
        }
        if (player.consumeWallClingTopContact()) {
            yVel = 0;
        }

        // ROM loc_350C8 copies the platform's current movement back to the player
        // before the centering math changes the platform velocity again.
        player.setXSpeed((short) xVel);
        player.setYSpeed((short) yVel);
        if (rolling == 0) {
            applyCenteringOrLateralLaunch(player);
        }
    }

    /** ROM sub_35504: accumulate player ground_vel from input (accel $C, skid $80, max $600). */
    private void runPlayerGroundMotion(AbstractPlayableSprite player) {
        int gSpeed = player.getGSpeed();
        boolean right = player.isRightPressed();
        boolean left = player.isLeftPressed();

        if (left && !right) {
            if (gSpeed > 0) {
                // Skid ROM sub_3555C loc_35596: sub.w d4, d0 (=$80).
                gSpeed -= PLAYER_SKID;
            } else {
                // Accel ROM loc_35578: sub.w d5, d0 (=$C).
                gSpeed -= PLAYER_ACCEL;
                if (gSpeed < -PLAYER_MAX_SPEED) gSpeed = -PLAYER_MAX_SPEED;
            }
        } else if (right && !left) {
            if (gSpeed < 0) {
                gSpeed += PLAYER_SKID;
            } else {
                gSpeed += PLAYER_ACCEL;
                if (gSpeed > PLAYER_MAX_SPEED) gSpeed = PLAYER_MAX_SPEED;
            }
        } else {
            // ROM loc_35524..loc_3554E friction toward 0 (uses d5 = $C).
            if (gSpeed > 0) {
                gSpeed -= PLAYER_ACCEL;
                if (gSpeed < 0) gSpeed = 0;
            } else if (gSpeed < 0) {
                gSpeed += PLAYER_ACCEL;
                if (gSpeed > 0) gSpeed = 0;
            }
        }
        player.setGSpeed((short) gSpeed);
        // ROM loc_3554E: x_vel = ground_vel; y_vel = 0.
        player.setXSpeed((short) gSpeed);
        player.setYSpeed((short) 0);
    }

    /**
     * ROM loc_350A6..loc_35170: centering / off-centre launch.
     *
     * <ul>
     *   <li>{@code d0 = player.x - platform.x}.</li>
     *   <li>{@code d0 > 0}: if {@code xVel < $200}, {@code xVel += 4*d0}. Then if
     *       {@code xVel >= 0} (bmi skip): {@code yVel -= 8} and, if {@code yVel > -$100},
     *       {@code yVel += -xVel/16} (i.e. subtract |xVel|/16). Set airborne.</li>
     *   <li>{@code d0 < 0}: mirror. Gate on {@code xVel > -$200}; y-kick condition is
     *       {@code xVel < 0} (bpl skip); {@code yVel += xVel/16} (xVel is negative).</li>
     *   <li>{@code d0 == 0}: decelerate {@code xVel} by 1 toward 0 and clear upward y_vel.</li>
     * </ul>
     */
    private void applyCenteringOrLateralLaunch(AbstractPlayableSprite player) {
        int dx = player.getCentreX() - posX;
        if (dx == 0) {
            // ROM loc_35170.
            int timerDelta = 1;
            if (xVel > 0) {
                xVel -= 1;
                timerDelta = -1;
            } else if (xVel < 0) {
                xVel += 1;
            }
            timer = (timer + timerDelta) & 0xFFFF;
            if (yVel < 0) yVel = 0;
            return;
        }

        if (dx > 0) {
            // ROM loc_35130: x_vel gate.
            if (xVel < LATERAL_SOFT_CAP) {
                xVel += dx * 4;
            }
            timer = (timer + dx) & 0xFFFF;
            // ROM loc_35148: y-kick only when x_vel >= 0 (bmi skip).
            if (xVel >= 0) {
                yVel -= LATERAL_Y_KICK;
                if (yVel > LATERAL_Y_MIN) {
                    // ROM: neg x_vel; asr.w #4; add -> y_vel += -xVel/16.
                    int sub = xVel >> 4;
                    yVel -= sub;
                    if (yVel < LATERAL_Y_MIN) {
                        yVel = LATERAL_Y_MIN;
                    }
                }
            }
        } else {
            // dx < 0 (ROM fall-through at loc_350C8 ... loc_35128).
            if (xVel > -LATERAL_SOFT_CAP) {
                xVel += dx * 4; // dx is negative
            }
            timer = (timer + dx) & 0xFFFF;
            // ROM loc_3510A: y-kick only when x_vel < 0 (bpl skip).
            if (xVel < 0) {
                yVel -= LATERAL_Y_KICK;
                if (yVel > LATERAL_Y_MIN) {
                    // ROM: asr.w #4 on negative xVel -> still negative. y_vel += xVel/16.
                    int add = xVel >> 4; // negative
                    yVel += add;
                    if (yVel < LATERAL_Y_MIN) {
                        yVel = LATERAL_Y_MIN;
                    }
                }
            }
        }

        // ROM loc_35128/loc_35168: bset #1, status -> airborne.
        airborne = true;
    }

    private void launchPlayerVertically(AbstractPlayableSprite player, PlayerGrabState state,
                                        boolean isPrimary) {
        releasePlayer(player, state, true);
        // ROM: move.w #-$680, y_vel(a1); jumping=1; y_radius=$E; x_radius=7; anim=ROLL;
        // bset #Status_Roll; sfx_Jump.
        player.setYSpeed(LAUNCH_Y_VEL);
        player.setXSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setJumping(true);
        player.setAir(true);
        player.setRolling(true);
        player.applyRollingRadii(false);
        player.setAnimationId(Sonic3kAnimationIds.ROLL.id());
        playSfx(Sonic3kSfx.JUMP);

        // ROM loc_3500A: beq.w sub_3519A — only P1 flips the platform to drift mode
        // (loc_34D92). P2 jump-launch only releases that player; the platform
        // continues running its state machines.
        if (isPrimary) {
            enterReleasedFlight();
        }
    }

    /**
     * ROM sub_3519A: overwrite the platform's update routine with {@code loc_34D92}
     * and force both players' slot-state to 6. Called when P1 jump-launches or when
     * {@code sub_35202}'s edge-case path fires.
     */
    private void enterReleasedFlight() {
        releasedFlight = true;
        carryLatched = false;
        nextCarryLatched = false;
        for (Map.Entry<PlayableEntity, PlayerGrabState> entry : playerStates.entrySet()) {
            PlayerGrabState ps = entry.getValue();
            if (entry.getKey() instanceof AbstractPlayableSprite player) {
                if (ps.routine == 4) {
                    releasePlayer(player, ps, true);
                } else if (ps.routine != 0) {
                    player.setOnObject(false);
                    ps.routine = 6;
                    ps.grabbed = false;
                    ps.entrySideBias = 0;
                }
            } else {
                ps.routine = 6;
                ps.grabbed = false;
                ps.entrySideBias = 0;
            }
        }
    }

    /** ROM sub_3519A / loc_34D92 reset; we only clear the state bits. */
    private void playerStateReleased(PlayerGrabState state) {
        state.routine = 0;
        state.entrySideBias = 0;
        state.grabbed = false;
    }

    private void releasePlayer(AbstractPlayableSprite player, PlayerGrabState state, boolean airborneRelease) {
        player.setObjectControlled(false);
        player.setControlLocked(false);
        player.setOnObject(false);
        player.setForcedAnimationId(-1);
        player.restoreDefaultRadii();
        player.clearWallClingState();
        player.suppressNextJumpPress();
        if (airborneRelease) {
            player.setAir(true);
        }
        ObjectServices svc = tryServices();
        if (svc != null && svc.objectManager() != null) {
            svc.objectManager().clearRidingObject(player);
        }
        state.entrySideBias = 0;
        state.xSub = 0;
        state.ySub = 0;
        state.routine = 6;
        state.grabbed = false;
    }

    // =============================================================
    // sub_35202 — post-motion snap for grabbed players
    // =============================================================

    /**
     * ROM sub_35202 (state 4, !Status_OnObj branch):
     * <pre>
     *   y_pos(a1) = (y_pos(a0) - $C) - default_y_radius(a1)
     *   x_pos(a1) = x_pos(a0)
     *   $2D(a0) = 0
     * </pre>
     */
    private void snapGrabbedPlayer(AbstractPlayableSprite player) {
        PlayerGrabState state = playerStates.get(player);
        if (state == null || state.routine < 4) {
            return;
        }
        if (state.routine != 4) {
            return;
        }
        ObjectInstance riding = currentRidingObject(player);
        if (riding instanceof SinkingMudObjectInstance && riding != this) {
            enterReleasedFlight();
            return;
        }
        if (player.isOnObject() && riding == this) {
            // ROM sub_35202 Status_OnObj branch:
            //   x_pos(a1) = x_pos(a0)                 ; player X follows platform X
            //   y_pos(a0) = y_pos(a1) + default_y_radius + $D
            int defaultYR = player.getStandYRadius();
            player.setCentreX((short) posX);
            posY = player.getCentreY() + defaultYR + 0x0D;
            nextCarryLatched = true;
            return;
        }
        int platformTop = posY - HEIGHT_PIXELS;
        int defaultYR = player.getStandYRadius();
        short snapY = (short) (platformTop - defaultYR);
        short snapX = (short) posX;
        player.setCentreX(snapX);
        player.setCentreY(snapY);
        // ROM obj_control bit 0 skips Sonic_Modes, so speeds never drive the
        // grabbed player. Engine-side, modeAirborne still runs each frame;
        // zeroing speeds here prevents a frame of drift before the next snap.
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        nextCarryLatched = false;
    }

    // =============================================================
    // Terrain probes
    // =============================================================

    private void applyGroundWallResponse() {
        int rotation = (groundVel < 0) ? 0x40 : 0xC0;
        int rotatedAngle = (angle + rotation) & 0xFF;
        ProbeResult probe = runGroundWallProbe(rotatedAngle);
        if (probe == null || probe.distance >= 0) {
            return;
        }

        int velocityAdjustment = probe.distance << 8;
        int mode = (rotatedAngle + 0x20) & 0xC0;
        switch (mode) {
            case 0x00 -> yVel += velocityAdjustment;
            case 0x40 -> {
                if ((((probe.angle & 0xFF) + 0x30) & 0xFF) < 0x60) {
                    return;
                }
                xVel -= velocityAdjustment;
                yVel = 0;
                groundVel = 0;
            }
            case 0x80 -> yVel -= velocityAdjustment;
            default -> {
                if ((((probe.angle & 0xFF) + 0x30) & 0xFF) < 0x60) {
                    return;
                }
                xVel += velocityAdjustment;
                yVel = 0;
                groundVel = 0;
            }
        }
    }

    /**
     * ROM sub_34DBC → sub_F6B4: single-point probe in the rotated-angle direction,
     * at the predicted next-frame position (x_pos + x_vel, y_pos + y_vel). The
     * dispatch targets (sub_F828 floor, CheckCeilingDist_WithRadius, loc_FAA4 right
     * wall, loc_FDC8 left wall) all use x_radius=$18 for the probe offset —
     * y_radius=$13 is never actually consumed by these single-sensor variants.
     */
    private ProbeResult runGroundWallProbe(int rotatedAngle) {
        int predictedX = predictCoordinate(posX, motion.xSub, xVel);
        int predictedY = predictCoordinate(posY, motion.ySub, yVel) - WALL_Y_OFFSET;
        int probeMode = anglePosQuadrant(rotatedAngle);
        return switch (probeMode) {
            case 0x00 -> toProbeResult(
                    ObjectTerrainUtils.checkFloorDist(predictedX, predictedY, WALL_X_RADIUS), 0x00);
            case 0x40 -> toProbeResult(
                    ObjectTerrainUtils.checkLeftWallDist(predictedX - WALL_X_RADIUS, predictedY), 0x40);
            case 0x80 -> toProbeResult(
                    ObjectTerrainUtils.checkCeilingDist(predictedX, predictedY, WALL_X_RADIUS), 0x80);
            default -> toProbeResult(
                    ObjectTerrainUtils.checkRightWallDist(predictedX + WALL_X_RADIUS, predictedY), 0xC0);
        };
    }

    private static int predictCoordinate(int pos, int sub, int vel) {
        int total = (sub & 0xFF) + (vel & 0xFF);
        return pos + (vel >> 8) + (total >> 8);
    }

    private ProbeResult toProbeResult(TerrainCheckResult result, int fallbackAngle) {
        if (result == null || !result.foundSurface()) {
            return null;
        }
        int probeAngle = result.angle() & 0xFF;
        if ((probeAngle & 1) != 0) {
            probeAngle = fallbackAngle & 0xFF;
        }
        return new ProbeResult(result.distance(), probeAngle);
    }

    private static int anglePosQuadrant(int angle) {
        angle &= 0xFF;
        int check = (angle + 0x20) & 0xFF;
        if ((check & 0x80) != 0) {
            int d0 = angle;
            if ((angle & 0x80) != 0) {
                d0 = (d0 - 1) & 0xFF;
            }
            return (d0 + 0x20) & 0xC0;
        }
        int d0 = angle;
        if ((angle & 0x80) != 0) {
            d0 = (d0 + 1) & 0xFF;
        }
        return (d0 + 0x1F) & 0xC0;
    }

    /**
     * ROM sub_34E6E: floor-follow on the ground path. Uses {@code y_radius = $C}
     * (platform body half-height) so the probe bottom lands exactly at the
     * platform's feet — no y_pos offset needed.
     */
    private void probeGroundAngle() {
        ProbeResult floor = probeGroundFloor();
        if (floor == null) {
            // ROM: if Sonic_CheckFloor returns no floor, d1 is large positive which
            // falls into the drop-gap test below and sets airborne.
            airborne = true;
            rolling = 0;
            return;
        }
        angle = clampGroundAngle(floor.angle);
        int dist = floor.distance;
        if (dist == 0) {
            return;
        }
        if (dist < 0) {
            // ROM: cmpi.w #-$E, d1; blt.s locret_34EB8 — skip if overlap > 14.
            if (dist < -0x0E) return;
            posY += dist;
        } else {
            // ROM loc_34EBA: drop gap capped at min(|x_vel.byte| + 4, $E).
            int maxDrop = Math.min(Math.abs((byte) xVel) + 4, 0x0E);
            if (dist <= maxDrop) {
                posY += dist;
            } else {
                airborne = true;
                rolling = 0;
            }
        }
    }

    private static int clampGroundAngle(int byteValue) {
        int signed = (byte) byteValue;
        if (signed > MAX_GROUND_ANGLE) signed = MAX_GROUND_ANGLE;
        else if (signed < -MAX_GROUND_ANGLE) signed = -MAX_GROUND_ANGLE;
        return signed & 0xFF;
    }

    // =============================================================
    // Waypoint arc (sub_35666)
    // =============================================================

    private void checkWaypoints() {
        int[] table = waypointTableForCurrentAct();
        if (table == null || table.length < 2) return;
        int count = table[0];
        for (int e = 0; e <= count; e++) {
            int i = 1 + e * WAYPOINT_WORDS_PER_ENTRY;
            if (i + (WAYPOINT_WORDS_PER_ENTRY - 1) >= table.length) break;
            int triggerX = table[i];
            int triggerY = table[i + 1];
            int dxWin = (triggerX - posX) + WAYPOINT_MATCH_HALF;
            if (dxWin < 0 || dxWin >= WAYPOINT_MATCH_WINDOW) continue;
            int dyWin = (triggerY - posY) + WAYPOINT_MATCH_HALF;
            if (dyWin < 0 || dyWin >= WAYPOINT_MATCH_WINDOW) continue;
            int flagByte = (table[i + 2] >> 8) & 0xFF;
            int probe = ((flagByte & 0x7F) != 0) ? -groundVel : groundVel;
            if (probe < 0) continue;
            activateArc(table, i);
            return;
        }
    }

    private void activateArc(int[] table, int entryStart) {
        arcMode = 1;
        // ROM stores the whole word at $3E(a0). sub_35868 then uses:
        // - $3F(a0) (low byte) for the arc direction/progress logic
        // - $3E(a0) (high byte) for the final ground_vel sign flip after the tail
        // - move.b 4(a1),d2 (high byte) for the trigger-direction gate in sub_35666
        arcFlagsHi = (table[entryStart + 2] >> 8) & 0xFF;
        arcFlagsLo = table[entryStart + 2] & 0xFF;
        int destX = table[entryStart + 3];
        int destY = table[entryStart + 4];
        int deltaY = table[entryStart + 5];
        if ((arcFlagsLo & 0x7F) != 0) {
            destY -= deltaY;
        }
        homeX = (short) destX;
        homeY = (short) destY;
        activeWaypointTable = table;
        if (firstActivatedWaypointEntryStart < 0) {
            firstActivatedWaypointEntryStart = entryStart;
        }
        lastActivatedWaypointEntryStart = entryStart;
        waypointActivationCount++;
        arcDataIndex = entryStart + 5;
        arcProgress = 0;
        arcLimit = 0;
        arcTimer = 0;
        syncMotionSubpixelsToArc();
        computeArcLinearVelocity(destX, destY);
    }

    private int[] waypointTableForCurrentAct() {
        int act = currentAct();
        if (act == 0) {
            if (waypointAct1 == null) {
                waypointAct1 = readWaypointTable(Sonic3kConstants.MGZ_TOP_PLATFORM_WAYPOINTS_ACT1_ADDR);
            }
            return waypointAct1;
        }
        if (waypointAct2 == null) {
            waypointAct2 = readWaypointTable(Sonic3kConstants.MGZ_TOP_PLATFORM_WAYPOINTS_ACT2_ADDR);
        }
        return waypointAct2;
    }

    private int currentAct() {
        ObjectServices svc = tryServices();
        if (svc == null || svc.levelManager() == null) return 0;
        try {
            return svc.levelManager().getCurrentAct();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private int[] readWaypointTable(int romAddr) {
        ObjectServices svc = tryServices();
        if (svc == null || svc.romManager() == null) return null;
        try {
            Rom rom = svc.romManager().getRom();
            if (rom == null) return null;
            RomByteReader reader = RomByteReader.fromRom(rom);
            int count = reader.readU16BE(romAddr);
            int entries = count + 1;
            int[] out = new int[1 + entries * WAYPOINT_WORDS_PER_ENTRY];
            out[0] = count;
            int base = romAddr + 2;
            for (int i = 0; i < entries * WAYPOINT_WORDS_PER_ENTRY; i++) {
                out[1 + i] = reader.readS16BE(base + i * 2);
            }
            return out;
        } catch (IOException ignored) {
            return null;
        }
    }

    private void runAirborneDown() {
        resolveLeftWall(false);
        resolveRightWall(false);
        ProbeResult floor = probeAirborneFloor();
        if (floor == null) {
            return;
        }
        int dist = floor.distance;
        if (dist >= 0 || dist < -0x0E) {
            return;
        }
        landFromAirDefault(floor);
    }

    private void runAirborneRight() {
        resolveLeftWall(true);

        ProbeResult ceiling = probeAirborneCornerCeiling();
        if (ceiling != null && ceiling.distance < 0) {
            posY -= ceiling.distance;
            if (yVel < 0) {
                yVel = 0;
            }
            return;
        }

        if (yVel < 0) {
            return;
        }
        ProbeResult floor = probeAirborneFloor();
        if (floor != null && floor.distance < 0) {
            posY += floor.distance;
            angle = clampGroundAngle(floor.angle);
            airborne = false;
            yVel = 0;
            groundVel = xVel;
        }
    }

    private void runAirborneUp() {
        resolveLeftWall(false);
        resolveRightWall(false);

        ProbeResult ceiling = probeAirborneCeiling();
        if (ceiling == null || ceiling.distance >= 0) {
            return;
        }
        posY -= ceiling.distance;
        if (((ceiling.angle + 0x20) & 0x40) == 0) {
            yVel = 0;
            return;
        }
        angle = clampGroundAngle(ceiling.angle);
        airborne = false;
        groundVel = yVel;
        if ((byte) ceiling.angle < 0) {
            groundVel = -groundVel;
        }
    }

    private void runAirborneLeft() {
        resolveRightWall(true);

        ProbeResult ceiling = probeAirborneCornerCeiling();
        if (ceiling != null && ceiling.distance < 0) {
            posY -= ceiling.distance;
            if (yVel < 0) {
                yVel = 0;
            }
            return;
        }

        if (yVel < 0) {
            return;
        }
        ProbeResult floor = probeAirborneFloor();
        if (floor != null && floor.distance < 0) {
            posY += floor.distance;
            angle = clampGroundAngle(floor.angle);
            airborne = false;
            yVel = 0;
            groundVel = xVel;
        }
    }

    private void resolveRightWall(boolean steepWallTransfersToGround) {
        ProbeResult wall = probeAirborneRightWall();
        if (wall == null || wall.distance >= 0) {
            return;
        }
        posX += wall.distance;
        if (steepWallTransfersToGround && (((wall.angle + 0x30) & 0xFF) >= 0x60)) {
            xVel = 0;
            groundVel = yVel;
            return;
        }
        xVel = 0;
    }

    private void resolveLeftWall(boolean steepWallTransfersToGround) {
        ProbeResult wall = probeAirborneLeftWall();
        if (wall == null || wall.distance >= 0) {
            return;
        }
        posX -= wall.distance;
        if (steepWallTransfersToGround && (((wall.angle + 0x30) & 0xFF) >= 0x60)) {
            xVel = 0;
            groundVel = yVel;
            return;
        }
        xVel = 0;
    }

    // ROM sub_3526A wall probes: sub_FA1A / sub_FD32 are PAIRED wall checks at
    // (x_pos ± x_radius, y_pos ± y_radius). x_radius=$18, y_radius=$C at these call
    // sites, and y_pos is already posY - $13 (from the loc_34C98 shift).
    private ProbeResult probeAirborneRightWall() {
        int midY = posY - AIRBORNE_Y_OFFSET;
        return chooseDeeperProbe(
                ObjectTerrainUtils.checkRightWallDist(posX + WALL_X_RADIUS, midY - WALL_Y_OFFSET),
                ObjectTerrainUtils.checkRightWallDist(posX + WALL_X_RADIUS, midY + WALL_Y_OFFSET),
                0xC0);
    }

    private ProbeResult probeAirborneLeftWall() {
        int midY = posY - AIRBORNE_Y_OFFSET;
        return chooseDeeperProbe(
                ObjectTerrainUtils.checkLeftWallDist(posX - WALL_X_RADIUS, midY - WALL_Y_OFFSET),
                ObjectTerrainUtils.checkLeftWallDist(posX - WALL_X_RADIUS, midY + WALL_Y_OFFSET),
                0x40);
    }

    private ProbeResult probeGroundFloor() {
        return chooseDeeperProbe(
                ObjectTerrainUtils.checkFloorDist(posX + GROUND_X_RADIUS, posY, GROUND_Y_RADIUS),
                ObjectTerrainUtils.checkFloorDist(posX - GROUND_X_RADIUS, posY, GROUND_Y_RADIUS),
                0x00);
    }

    private ProbeResult probeAirborneFloor() {
        return chooseDeeperProbe(
                ObjectTerrainUtils.checkFloorDist(posX + GROUND_X_RADIUS, posY - AIRBORNE_Y_OFFSET, AIRBORNE_Y_RADIUS),
                ObjectTerrainUtils.checkFloorDist(posX - GROUND_X_RADIUS, posY - AIRBORNE_Y_OFFSET, AIRBORNE_Y_RADIUS),
                0x00);
    }

    // ROM sub_3526A ceiling probe: sub_FB5A is a PAIRED check at y = y_pos - y_radius
    // with sensors at x ± (x_radius - 2). x_radius=$A here, so sensors at x ± 8; probe
    // Y resolves to (posY - $13) - $1F = posY - $32.
    private ProbeResult probeAirborneCeiling() {
        int cornerX = GROUND_X_RADIUS - 2;
        return chooseDeeperProbe(
                ObjectTerrainUtils.checkCeilingDist(posX + cornerX, posY - AIRBORNE_Y_OFFSET, AIRBORNE_Y_RADIUS),
                ObjectTerrainUtils.checkCeilingDist(posX - cornerX, posY - AIRBORNE_Y_OFFSET, AIRBORNE_Y_RADIUS),
                0x80);
    }

    private ProbeResult probeAirborneCornerCeiling() {
        // ROM loc_3536E / loc_3547A call the same sub_FB5A as the straight-up case.
        return probeAirborneCeiling();
    }

    private ProbeResult chooseDeeperProbe(TerrainCheckResult primary, TerrainCheckResult secondary, int fallbackAngle) {
        ProbeResult first = toProbeResult(primary, fallbackAngle);
        ProbeResult second = toProbeResult(secondary, fallbackAngle);
        if (first == null) return second;
        if (second == null) return first;
        return (second.distance <= first.distance) ? second : first;
    }

    private void landFromAirDefault(ProbeResult floor) {
        posY += floor.distance;
        angle = clampGroundAngle(floor.angle);
        airborne = false;

        int floorAngle = floor.angle & 0xFF;
        if (((floorAngle + 0x20) & 0x40) != 0) {
            xVel = 0;
            if (yVel > -0x40) {
                yVel = -0x40;
            }
            groundVel = yVel;
        } else if (((floorAngle + 0x10) & 0x20) != 0) {
            yVel >>= 1;
            groundVel = yVel;
        } else {
            yVel = 0;
            groundVel = xVel;
        }

        if ((byte) floorAngle < 0) {
            groundVel = -groundVel;
        }
    }

    private void computeArcLinearVelocity(int destX, int destY) {
        int absGroundVel = Math.abs(groundVel);
        int d2 = absGroundVel;
        int d3 = absGroundVel;
        if (d2 == 0) {
            d2 = DASH_SPEED_LOW;
            d3 = DASH_SPEED_LOW;
        }

        int dx = destX - posX;
        if (dx < 0) {
            d2 = -d2;
        }
        int dy = destY - posY;
        if (dy < 0) {
            d3 = -d3;
        }

        if (Math.abs(dy) >= Math.abs(dx)) {
            int duration = (d3 == 0) ? 0 : (int) ((((long) dy) << 16) / d3);
            int calcXVel = (dx == 0 || duration == 0) ? 0 : (int) ((((long) dx) << 16) / duration);
            xVel = (short) calcXVel;
            yVel = (short) d3;
            arcTimer = arcTimerByteFromWord(Math.abs(duration));
            return;
        }

        int duration = (d2 == 0) ? 0 : (int) ((((long) dx) << 16) / d2);
        int calcYVel = (dy == 0 || duration == 0) ? 0 : (int) ((((long) dy) << 16) / duration);
        yVel = (short) calcYVel;
        xVel = (short) d2;
        arcTimer = arcTimerByteFromWord(Math.abs(duration));
    }

    private static int arcTimerByteFromWord(int durationWord) {
        // ROM writes the 16-bit duration to $3A(a0), then decrements the byte at
        // $3A(a0). On 68000 that is the high byte of the stored word.
        return (durationWord >> 8) & 0xFF;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void onUnload() {
        releaseAllPlayers();
    }

    @Override
    public void setDestroyed(boolean destroyed) {
        if (destroyed && !isDestroyed()) {
            releaseAllPlayers();
        }
        super.setDestroyed(destroyed);
    }

    // =============================================================
    // Render
    // =============================================================

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(ART_KEY);
        if (renderer == null) return;
        int frame = (timer >> ANIM_TIMER_SHIFT) & 1;
        renderer.drawFrameIndex(frame, posX, posY, false, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        if (ctx == null) return;
        ctx.drawRect(posX, posY, WIDTH_PIXELS + 0x0B, HEIGHT_PIXELS,
                bodyDriven ? 0.2f : 0.6f,
                airborne ? 0.4f : 0.9f,
                rolling != 0 ? 0.4f : 0.9f);
    }

    // =============================================================
    // Helpers
    // =============================================================

    private AbstractPlayableSprite resolveSidekick() {
        ObjectServices svc = tryServices();
        if (svc == null) return null;
        List<PlayableEntity> sks = svc.sidekicks();
        for (PlayableEntity sk : sks) {
            if (sk instanceof AbstractPlayableSprite p) {
                return p;
            }
        }
        return null;
    }

    private void playSfx(Sonic3kSfx sfx) {
        ObjectServices svc = tryServices();
        if (svc != null) {
            svc.playSfx(sfx.id);
        }
    }

    private void movePlatform(boolean withGravity, int gravity) {
        motion.x = posX;
        motion.y = posY;
        motion.xVel = (short) xVel;
        motion.yVel = (short) yVel;
        if (withGravity) {
            SubpixelMotion.moveSprite(motion, gravity);
        } else {
            SubpixelMotion.moveSprite2(motion);
        }
        posX = motion.x;
        posY = motion.y;
        xVel = (short) motion.xVel;
        yVel = (short) motion.yVel;
    }

    private void moveGrabbedPlayer(AbstractPlayableSprite player, PlayerGrabState state) {
        SubpixelMotion.State playerMotion = new SubpixelMotion.State(
                player.getCentreX(),
                player.getCentreY(),
                state.xSub,
                state.ySub,
                player.getXSpeed(),
                player.getYSpeed());
        SubpixelMotion.moveSprite2(playerMotion);
        state.xSub = playerMotion.xSub;
        state.ySub = playerMotion.ySub;
        player.setCentreX((short) playerMotion.x);
        player.setCentreY((short) playerMotion.y);
        clampGrabbedPlayerToLevel(player);
    }

    private void releaseAllPlayers() {
        for (Map.Entry<PlayableEntity, PlayerGrabState> entry : playerStates.entrySet()) {
            PlayerGrabState state = entry.getValue();
            state.grabbed = false;
            state.routine = 0;
            state.entrySideBias = 0;
            if (entry.getKey() instanceof AbstractPlayableSprite player) {
                player.setObjectControlled(false);
                player.setControlLocked(false);
                player.setOnObject(false);
                player.setForcedAnimationId(-1);
                player.restoreDefaultRadii();
                player.clearWallClingState();
                ObjectServices svc = tryServices();
                if (svc != null && svc.objectManager() != null) {
                    svc.objectManager().clearRidingObject(player);
                }
            }
        }
    }

    void syncFromLauncher(int newX, int newY) {
        posX = newX;
        posY = newY;
        homeX = newX;
        homeY = newY;
    }

    void advanceAnimationTimer(int delta) {
        timer = (timer + delta) & 0xFFFF;
    }

    boolean isAnyPlayerGrabbed() {
        for (PlayerGrabState state : playerStates.values()) {
            if (state.routine == 4) {
                return true;
            }
        }
        return false;
    }

    void activateFromLauncher(int launchVelocity) {
        currentSubtype = 0;
        bodyDriven = true;
        groundVel = launchVelocity;
        xVel = launchVelocity;
        arcMode = 0;
        firstActivatedWaypointEntryStart = -1;
        lastActivatedWaypointEntryStart = -1;
        waypointActivationCount = 0;
        syncMotionSubpixelsToArc();
        rolling = 1;
        airborne = false;
    }

    private void syncMotionSubpixelsToArc() {
        arcXSub = (motion.xSub & 0xFF) << 8;
        arcYSub = (motion.ySub & 0xFF) << 8;
    }

    private void syncArcSubpixelsToMotion() {
        motion.xSub = (arcXSub >> 8) & 0xFF;
        motion.ySub = (arcYSub >> 8) & 0xFF;
    }

    private ObjectInstance currentRidingObject(AbstractPlayableSprite player) {
        ObjectServices svc = tryServices();
        if (svc == null || svc.objectManager() == null) {
            return null;
        }
        return svc.objectManager().getRidingObject(player);
    }

    private void clampGrabbedPlayerToLevel(AbstractPlayableSprite player) {
        if (player == null || player.currentLevelManager() == null
                || player.currentLevelManager().getCurrentLevel() == null) {
            return;
        }
        int minX = player.currentLevelManager().getCurrentLevel().getMinX() + player.getXRadius();
        int maxX = player.currentLevelManager().getCurrentLevel().getMaxX() - player.getXRadius();
        int minY = player.currentLevelManager().getCurrentLevel().getMinY() + player.getYRadius();
        int maxY = player.currentLevelManager().getCurrentLevel().getMaxY() - player.getYRadius();
        int clampedX = Math.max(minX, Math.min(maxX, player.getCentreX()));
        int clampedY = Math.max(minY, Math.min(maxY, player.getCentreY()));
        player.setCentreX((short) clampedX);
        player.setCentreY((short) clampedY);
    }
}
