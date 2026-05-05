package com.openggf.physics;

import com.openggf.game.GameServices;
import com.openggf.game.GroundMode;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Unified collision pipeline that orchestrates terrain probes and solid object
 * collision in a defined order. This is the consolidation point for:
 * - TerrainCollisionManager (terrain sensor probes)
 * - ObjectManager.SolidContacts (solid object collision resolution)
 *
 * The pipeline executes in three explicit phases:
 * 1. Terrain probes (ground/ceiling/wall sensors against level collision)
 * 2. Compatibility solid-object resolution when the active frame path still uses a
 *    batched solid pass
 * 3. Post-resolution adjustments (ground mode, gSpeed recompute, headroom)
 */
public class CollisionSystem {
    private static final Logger LOGGER = Logger.getLogger(CollisionSystem.class.getName());

    private final TerrainCollisionManager terrainCollisionManager;
    private final GroundSensor calcRoomProbe = new GroundSensor(null, Direction.DOWN, (byte) 0, (byte) 0, true);
    private final Map<AbstractPlayableSprite, Byte> pendingOddSensorFallbackAngles = new IdentityHashMap<>();
    private ObjectManager objectManager;

    // Trace for debugging/testing - defaults to no-op
    private CollisionTrace trace = NoOpCollisionTrace.INSTANCE;


    public CollisionSystem() {
        this(GameServices.terrainCollision());
    }

    public CollisionSystem(TerrainCollisionManager terrainCollisionManager) {
        this.terrainCollisionManager = terrainCollisionManager;
    }

    /**
     * Resets mutable state without destroying the singleton instance.
     * Cached references held by other classes remain valid.
     */
    public void resetState() {
        terrainCollisionManager.resetState();
        objectManager = null;
        trace = NoOpCollisionTrace.INSTANCE;
    }

    public void setObjectManager(ObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    public void setTrace(CollisionTrace trace) {
        this.trace = trace != null ? trace : NoOpCollisionTrace.INSTANCE;
    }

    public CollisionTrace getTrace() {
        return trace;
    }

    /**
     * Execute the full collision pipeline for a sprite.
     * This is the main entry point that replaces separate calls to
     * terrain collision and solid object managers.
     *
     * @param sprite The playable sprite to process collision for
     * @param groundSensors Ground sensor array
     * @param ceilingSensors Ceiling sensor array
     */
    public void step(AbstractPlayableSprite sprite, Sensor[] groundSensors, Sensor[] ceilingSensors) {
        if (sprite == null || sprite.getDead()) {
            return;
        }

        if (sprite.isDebugMode()) {
            return;
        }

        // Record initial state
        int startX = sprite.getCentreX();
        int startY = sprite.getCentreY();
        boolean inAir = sprite.getAir();

        trace.onTerrainProbesStart(startX, startY, inAir);

        // Phase 1: Terrain probes
        SensorResult[] groundResults = terrainProbes(sprite, groundSensors, "ground");
        SensorResult[] ceilingResults = terrainProbes(sprite, ceilingSensors, "ceiling");

        trace.onTerrainProbesComplete(sprite.getCentreX(), sprite.getCentreY(), sprite.getAngle());

        // Phase 2: Solid object resolution
        trace.onSolidContactsStart(sprite.getCentreX(), sprite.getCentreY());
        resolveSolidContacts(sprite);
        trace.onSolidContactsComplete(
            objectManager != null && objectManager.isRidingObject(sprite),
            sprite.getCentreX(), sprite.getCentreY()
        );

        // Phase 3: Post-resolution adjustments
        postResolutionAdjustments(sprite);
    }

    /**
     * Phase 1: Execute terrain sensor probes.
     * Currently delegates to TerrainCollisionManager.
     */
    public SensorResult[] terrainProbes(AbstractPlayableSprite sprite, Sensor[] sensors, String sensorType) {
        SensorResult[] results = terrainCollisionManager.getSensorResult(sensors);

        if (trace != NoOpCollisionTrace.INSTANCE) {
            for (int i = 0; i < results.length; i++) {
                trace.onTerrainProbeResult(sensorType + "_" + i, results[i]);
            }
        }

        return results;
    }

    /**
     * Phase 2: Resolve solid object contacts for the legacy batched path.
     * Inline-order modules resolve object solids during object execution instead.
     */
    public void resolveSolidContacts(AbstractPlayableSprite sprite) {
        if (objectManager == null) {
            return;
        }

        // Delegate to existing solid contacts system
        objectManager.updateSolidContacts(sprite);
    }

    /**
     * Phase 3: Apply post-resolution adjustments.
     * Currently a no-op; adjustments are performed inline in movement code.
     * This will be expanded as logic migrates from PlayableSpriteMovement.
     */
    public void postResolutionAdjustments(AbstractPlayableSprite sprite) {
    }

    /**
     * Check if player has standing contact with any solid object.
     * Convenience method that delegates to SolidContacts.
     */
    public boolean hasStandingContact(AbstractPlayableSprite player) {
        if (player == null || player.getYSpeed() < 0 || objectManager == null) {
            return false;
        }
        return objectManager.latestStandingSnapshot(player);
    }

    /**
     * Get headroom distance to nearest solid object above player.
     * Convenience method that delegates to SolidContacts.
     */
    public int getHeadroomDistance(AbstractPlayableSprite player, int hexAngle) {
        if (objectManager == null) {
            return Integer.MAX_VALUE;
        }
        return objectManager.latestHeadroomSnapshot(player, hexAngle);
    }

    /**
     * Check if player is currently riding an object.
     */
    public boolean isRidingObject(AbstractPlayableSprite player) {
        if (objectManager == null) {
            return false;
        }
        return objectManager.isRidingObject(player);
    }

    /**
     * Clear riding state (e.g., when player jumps off).
     */
    public void clearRidingObject(AbstractPlayableSprite player) {
        if (objectManager != null) {
            objectManager.clearRidingObject(player);
        }
    }

    /**
     * Sonic_Jump clears the player's on-object status bit before objects run.
     * Some object routines still consume their standing routine once later in
     * the same frame, so the engine riding record may need to survive until
     * that object's inline solid checkpoint.
     */
    public void clearRidingObjectForJump(AbstractPlayableSprite player) {
        if (objectManager != null) {
            objectManager.clearRidingObjectForJump(player);
        }
    }

    public boolean hasObjectSupport(AbstractPlayableSprite player) {
        return isRidingObject(player) || hasStandingContact(player) || hasActiveLatchedObjectSupport(player);
    }

    public boolean hasGroundingObjectSupport(AbstractPlayableSprite player) {
        return isRidingObject(player) || hasStandingContact(player);
    }

    private boolean hasActiveLatchedObjectSupport(AbstractPlayableSprite player) {
        if (player == null || objectManager == null || !player.isOnObject()
                || player.getLatchedSolidObjectId() == 0) {
            return false;
        }
        ObjectInstance latchedInstance = player.getLatchedSolidObjectInstance();
        if (!objectManager.isActiveObjectInstance(latchedInstance)) {
            return false;
        }
        // ROM AnglePos exits on Status_OnObj before terrain attachment in all
        // games (S1 Sonic AnglePos.asm:5-11, S2 s2.asm:42559-42571,
        // S3K sonic3k.asm:18728-18741). Non-solid controllers such as S2
        // Obj06 own that bit until their object routine clears it.
        return true;
    }

    public boolean hasEnoughHeadroom(AbstractPlayableSprite player, int hexAngle) {
        int terrainDistance = getTerrainHeadroomDistance(player, hexAngle);
        int objectDistance = getHeadroomDistance(player, hexAngle);
        return Math.min(terrainDistance, objectDistance) >= 6;
    }

    public void resolveGroundWallCollision(AbstractPlayableSprite sprite) {
        if (sprite == null || sprite.isTunnelMode()
                || sprite.isStickToConvex()
                || sprite.isSuppressGroundWallCollision()) {
            return;
        }
        var levelManager = sprite.currentLevelManager();
        if (levelManager == null || levelManager.getCurrentLevel() == null) {
            return;
        }

        int angle = sprite.getAngle() & 0xFF;
        short gSpeed = sprite.getGSpeed();
        // ROM Sonic_Move/loc_11350 only applies the (angle + $40) sign skip
        // when the angle is not on an exact cardinal quadrant. Exact ceiling
        // angle $80 must still run CalcRoomInFront (sonic3k.asm:22708-22716).
        if (((angle & 0x3F) != 0 && (((angle + 0x40) & 0x80) != 0)) || gSpeed == 0) {
            return;
        }

        // ROM-accurate 32-bit prediction (Sonic_WalkSpeed / CalcRoomInFront):
        // ROM loads full 32-bit position (pixel:16 | sub:16) and adds velocity*256.
        // Uses full 16-bit subpixel to prevent carry errors vs ROM's arithmetic.
        int xPos32 = (sprite.getX() << 16) | (sprite.getXSubpixelRaw());
        int yPos32 = (sprite.getY() << 16) | (sprite.getYSubpixelRaw());
        int predictedX = (xPos32 + ((int) sprite.getXSpeed() << 8)) >> 16;
        int predictedY = (yPos32 + ((int) sprite.getYSpeed() << 8)) >> 16;
        short predictedDx = (short) (predictedX - sprite.getX());
        short predictedDy = (short) (predictedY - sprite.getY());
        CalcRoomInFrontProbe probe = describeCalcRoomInFrontProbe(angle, gSpeed);
        SensorResult result = scanCalcRoomInFront(sprite, probe, predictedDx, predictedDy);


        if (result == null || result.distance() >= 0) {
            return;
        }

        int velocityAdjustment = result.distance() << 8;
        int rotation = (gSpeed < 0) ? 0x40 : 0xC0;
        int rotatedAngle = (angle + rotation) & 0xFF;
        int mode = (rotatedAngle + 0x20) & 0xC0;

        switch (mode) {
            case 0x00 -> sprite.setYSpeed((short) (sprite.getYSpeed() + velocityAdjustment));
            case 0x40 -> {
                sprite.setXSpeed((short) (sprite.getXSpeed() - velocityAdjustment));
                sprite.setGSpeed((short) 0);
                if (shouldSetGroundWallPush(sprite, mode)) {
                    sprite.setPushing(true);
                }
            }
            case 0x80 -> sprite.setYSpeed((short) (sprite.getYSpeed() - velocityAdjustment));
            case 0xC0 -> {
                sprite.setXSpeed((short) (sprite.getXSpeed() + velocityAdjustment));
                sprite.setGSpeed((short) 0);
                if (shouldSetGroundWallPush(sprite, mode)) {
                    sprite.setPushing(true);
                }
            }
            default -> {
            }
        }
    }

    private static boolean shouldSetGroundWallPush(AbstractPlayableSprite sprite, int mode) {
        var featureSet = sprite.getPhysicsFeatureSet();
        if (featureSet == null || !featureSet.groundWallPushRequiresFacingIntoWall()) {
            return true;
        }
        boolean facingLeft = sprite.getDirection() == com.openggf.physics.Direction.LEFT;
        // S3K Sonic_Move/Tails_InputAcceleration_Path only sets Status_Push
        // when Status_Facing matches the wall side:
        //   mode $40: btst Status_Facing; beq return; bset Status_Push
        //   mode $C0: btst Status_Facing; bne return; bset Status_Push
        return mode == 0x40 ? facingLeft : !facingLeft;
    }

    static CalcRoomInFrontProbe describeCalcRoomInFrontProbe(int angle, short gSpeed) {
        int rotation = (gSpeed < 0) ? 0x40 : 0xC0;
        int rotatedAngle = (angle + rotation) & 0xFF;

        // ROM probe direction: Sonic_WalkSpeed (S1) and CalcRoomInFront (S2) both use
        // the asymmetric quadrant rounding from AnglePos for dispatching the sensor probe.
        // This differs from the simple (angle+0x20)&0xC0 used for velocity adjustment
        // at the call site (loc_1300C / s2.asm).
        // Key difference: at rotated angle 0xA0, simple gives 0xC0 (RIGHT) but ROM gives
        // 0x80 (UP). Using simple causes false wall detections on steep slopes.
        int probeMode = anglePosQuadrant(rotatedAngle);
        short dynamicYOffset = (short) (((probeMode == 0x40 || probeMode == 0xC0) && (rotatedAngle & 0x38) == 0) ? 8 : 0);

        return switch (probeMode) {
            case 0x00 -> new CalcRoomInFrontProbe(probeMode, rotatedAngle, Direction.DOWN, (short) 0, (short) 10, dynamicYOffset);
            case 0x40 -> new CalcRoomInFrontProbe(probeMode, rotatedAngle, Direction.LEFT, (short) -10, (short) 0, dynamicYOffset);
            case 0x80 -> new CalcRoomInFrontProbe(probeMode, rotatedAngle, Direction.UP, (short) 0, (short) -10, dynamicYOffset);
            default -> new CalcRoomInFrontProbe(probeMode, rotatedAngle, Direction.RIGHT, (short) 10, (short) 0, dynamicYOffset);
        };
    }

    private SensorResult scanCalcRoomInFront(AbstractPlayableSprite sprite,
                                             CalcRoomInFrontProbe probe,
                                             short predictedDx,
                                             short predictedDy) {
        calcRoomProbe.sprite = sprite;
        int solidityBit = sprite.getLrbSolidBit();
        return calcRoomProbe.scanWorld(
                probe.globalDirection(),
                probe.offsetX(),
                probe.offsetY(),
                predictedDx,
                (short) (predictedDy + probe.dynamicYOffset()),
                solidityBit);
    }

    public void resolveGroundAttachment(AbstractPlayableSprite sprite,
                                        int positiveThreshold,
                                        BooleanSupplier hasObjectSupport) {
        // ROM: S1 Sonic_AnglePos, S2 AnglePos, and S3K Player_AnglePos all
        // return early only when the player's Status_OnObj bit is set
        // (S3K: docs/skdisasm/sonic3k.asm:18735-18741). Object-side standing
        // masks can be stale after release; they must not suppress terrain
        // walk-off and airborne transition checks.
        if (sprite.isOnObject()) {
            if (hasObjectSupport == null
                    || hasObjectSupport.getAsBoolean()
                    || hasPendingStaleObjectSupportLoss(sprite)) {
                return;
            }
            // Engine-side object support can outlive the object/controller that set it
            // across transitions. The ROM's Player_AnglePos early return only applies
            // to a live Status_OnObj owner (sonic3k.asm:18735-18741); stale support must
            // fall through to the terrain walk-off path at sonic3k.asm:18839-18842.
            sprite.setOnObject(false);
        }

        updateGroundMode(sprite);

        SensorResult[] groundResult = terrainProbes(sprite, sprite.getGroundSensors(), "ground");
        SensorResult leftSensor = groundResult[0];
        SensorResult rightSensor = groundResult[1];

        SensorResult selectedResult = selectSensorWithAngle(sprite, rightSensor, leftSensor);
        // Refresh ground mode after the angle has been updated by selectSensorWithAngle.
        // The initial updateGroundMode (line 292) uses the PREVIOUS frame's end-angle for
        // sensor configuration. This second call uses the NEW angle from terrain probes,
        // matching the ROM's end-of-frame ground mode calculation.
        updateGroundMode(sprite);

        if (selectedResult == null) {
            if (sprite.isStickToConvex()) {
                return;
            }
            sprite.setAir(true);
            sprite.setPushing(false);
            return;
        }

        byte distance = selectedResult.distance();
        if (distance == 0) {
            return;
        }

        if (distance < 0) {
            if (sprite.getGroundMode() == GroundMode.RIGHTWALL) {
                if (distance < -14) {
                    if (isZoneActZero(sprite)) {
                        sprite.setAngle((byte) 0xC0);
                        sprite.setRightWallPenetrationTimer(3);
                    }
                    return;
                }
                if (sprite.getRightWallPenetrationTimer() > 0) {
                    sprite.setRightWallPenetrationTimer(sprite.getRightWallPenetrationTimer() - 1);
                    sprite.setAngle((byte) 0xC0);
                    return;
                }
            }
            if (distance >= -14) {
                moveForSensorResult(sprite, selectedResult);
            }
            return;
        }

        if (distance > positiveThreshold) {
            if (sprite.isStickToConvex()) {
                moveForSensorResult(sprite, selectedResult);
                return;
            }
            sprite.setAir(true);
            sprite.setPushing(false);
            return;
        }

        moveForSensorResult(sprite, selectedResult);
    }

    private boolean hasPendingStaleObjectSupportLoss(AbstractPlayableSprite sprite) {
        // AIZ1->AIZ2 reload order is a special case of that same Status_OnObj
        // rule. The ROM performs Load_Level/LoadSolids and player coordinate
        // offsets in the level-event path (docs/skdisasm/sonic3k.asm:104725-104756),
        // then the next player slot still sees Status_OnObj and skips AnglePos.
        // Later in that ExecuteObjects pass, Obj_AIZTransitionFloor observes
        // Current_act != 0, moves to x=$7FFF, and still calls SolidObjectTop
        // (104777-104790); the standing branch then clears OnObj/sets InAir
        // without moving the player (41793-41818, 41642-41679). Preserve the
        // status-bit skip until ObjectManager's inline finalizer consumes the
        // pending loss marker.
        return objectManager != null && objectManager.hasPendingStaleObjectSupportLoss(sprite);
    }

    private boolean isZoneActZero(AbstractPlayableSprite sprite) {
        if (sprite == null) {
            return false;
        }
        var levelManager = sprite.currentLevelManager();
        return levelManager != null
                && levelManager.getCurrentZone() == 0
                && levelManager.getCurrentAct() == 0;
    }

    private int getTerrainHeadroomDistance(AbstractPlayableSprite sprite, int hexAngle) {
        int overheadAngle = (hexAngle + 0x80) & 0xFF;
        int quadrant = (overheadAngle + 0x20) & 0xC0;

        Sensor[] pushSensors = sprite.getPushSensors();
        Sensor[] sensors = switch (quadrant) {
            case 0x00 -> sprite.getCeilingSensors();
            case 0x40 -> pushSensors != null ? new Sensor[]{pushSensors[0]} : sprite.getCeilingSensors();
            case 0x80 -> sprite.getCeilingSensors();
            case 0xC0 -> pushSensors != null ? new Sensor[]{pushSensors[1]} : sprite.getCeilingSensors();
            default -> null;
        };

        if (sensors == null) {
            return Integer.MAX_VALUE;
        }

        int minDistance = Integer.MAX_VALUE;
        for (Sensor sensor : sensors) {
            boolean wasActive = sensor.isActive();
            sensor.setActive(true);
            SensorResult result = sensor.scan();
            sensor.setActive(wasActive);
            if (result != null) {
                int clearance = Math.max(result.distance(), 0);
                minDistance = Math.min(minDistance, clearance);
            }
        }
        return minDistance;
    }

    public void resolveAirCollision(AbstractPlayableSprite sprite,
                                    Consumer<AbstractPlayableSprite> landingHandler) {
        resolveAirCollision(sprite, landingHandler, false);
    }

    /**
     * @param forceFloorCheck when true, floor collision in quadrants 0x40 and
     *     0xC0 runs even when ySpeed &lt; 0.  ROM equivalent: the
     *     {@code WindTunnel_flag} check at sonic3k.asm:24204/24299 bypasses
     *     the {@code tst.w y_vel} early return so floor terrain always
     *     constrains the player inside HCZ water tunnels.
     */
    public void resolveAirCollision(AbstractPlayableSprite sprite,
                                    Consumer<AbstractPlayableSprite> landingHandler,
                                    boolean forceFloorCheck) {
        int quadrant = TrigLookupTable.calcMovementQuadrant(sprite.getXSpeed(), sprite.getYSpeed());
        traceS3kCnzCollisionProbe(sprite, "start", quadrant, null, null, false);
        switch (quadrant) {
            case 0x00 -> {
                doWallCheckBoth(sprite);
                SensorResult[] groundResult = terrainProbes(sprite, sprite.getGroundSensors(), "ground");
                traceS3kCnzCollisionProbe(sprite, "ground-00", quadrant, groundResult, null, false);
                doTerrainCollisionAir(sprite, groundResult, landingHandler);
            }
            case 0x40 -> {
                boolean wallHit = doWallCheck(sprite, 0);
                if (wallHit) {
                    traceS3kAizAirCollisionProbe(sprite, "wall-40", quadrant, null, null, true);
                    traceS3kCnzCollisionProbe(sprite, "wall-40", quadrant, null, null, true);
                    if (!airLeftWallHitContinuesIntoCeilingSeparation(sprite)) {
                        return;
                    }
                }
                SensorResult[] ceilingResult = terrainProbes(sprite, sprite.getCeilingSensors(), "ceiling");
                boolean ceilingHit = doCeilingCollisionInternal(sprite, ceilingResult);
                traceS3kCnzCollisionProbe(sprite, "ceiling-40", quadrant, null, ceilingResult, ceilingHit);
                if (!ceilingHit) {
                    SensorResult[] groundResult = terrainProbes(sprite, sprite.getGroundSensors(), "ground");
                    traceS3kCnzCollisionProbe(sprite, "ground-40", quadrant, groundResult, null, false);
                    doTerrainCollisionAirDirect(sprite, groundResult, landingHandler, forceFloorCheck);
                }
            }
            case 0x80 -> {
                doWallCheckBoth(sprite);
                SensorResult[] ceilingResult = terrainProbes(sprite, sprite.getCeilingSensors(), "ceiling");
                traceS3kCnzCollisionProbe(sprite, "ceiling-80", quadrant, null, ceilingResult, false);
                doCeilingCollision(sprite, ceilingResult);
            }
            case 0xC0 -> {
                if (doWallCheck(sprite, 1)) {
                    traceS3kCnzCollisionProbe(sprite, "wall-C0", quadrant, null, null, true);
                    if (!airRightWallHitContinuesIntoCeilingSeparation(sprite)) {
                        return;
                    }
                }
                SensorResult[] ceilingResult = terrainProbes(sprite, sprite.getCeilingSensors(), "ceiling");
                boolean ceilingHit = doCeilingCollisionInternal(sprite, ceilingResult);
                traceS3kCnzCollisionProbe(sprite, "ceiling-C0", quadrant, null, ceilingResult, ceilingHit);
                if (!ceilingHit) {
                    SensorResult[] groundResult = terrainProbes(sprite, sprite.getGroundSensors(), "ground");
                    traceS3kCnzCollisionProbe(sprite, "ground-C0", quadrant, groundResult, null, false);
                    doTerrainCollisionAirDirect(sprite, groundResult, landingHandler, forceFloorCheck);
                }
            }
            default -> {
            }
        }
    }

    private boolean airRightWallHitContinuesIntoCeilingSeparation(AbstractPlayableSprite sprite) {
        PhysicsFeatureSet featureSet = sprite.getPhysicsFeatureSet();
        return featureSet != null && featureSet.airRightWallHitContinuesIntoCeilingSeparation();
    }

    private boolean airLeftWallHitContinuesIntoCeilingSeparation(AbstractPlayableSprite sprite) {
        PhysicsFeatureSet featureSet = sprite.getPhysicsFeatureSet();
        return featureSet != null && featureSet.airLeftWallHitContinuesIntoCeilingSeparation();
    }

    private void traceS3kAizAirCollisionProbe(AbstractPlayableSprite sprite,
                                              String stage,
                                              int quadrant,
                                              SensorResult[] groundResult,
                                              SensorResult[] ceilingResult,
                                              boolean collisionResolved) {
        if (!Boolean.getBoolean("s3k.aiz.aircollisionprobe")) {
            return;
        }
        com.openggf.level.LevelManager levelManager = com.openggf.game.GameServices.level();
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        int frameCounter = levelManager.getObjectManager().getFrameCounter();
        int centreX = sprite.getCentreX() & 0xFFFF;
        int centreY = sprite.getCentreY() & 0xFFFF;
        if (centreX < 0x1930 || centreX > 0x1960 || centreY < 0x0380 || centreY > 0x03E0) {
            return;
        }
        System.out.printf(
                "s3k-aiz-aircollisionprobe frame=%d stage=%s quad=%02X pos=(%04X,%04X) spd=(%04X,%04X,%04X) ground=[%s] ceiling=[%s] resolved=%s%n",
                frameCounter,
                stage,
                quadrant & 0xFF,
                sprite.getCentreX() & 0xFFFF,
                sprite.getCentreY() & 0xFFFF,
                sprite.getXSpeed() & 0xFFFF,
                sprite.getYSpeed() & 0xFFFF,
                sprite.getGSpeed() & 0xFFFF,
                formatProbeResults(groundResult),
                formatProbeResults(ceilingResult),
                collisionResolved);
    }

    /**
     * CNZ collision probe.
     * Logs every air-collision sensor result + landing decision when the player is in
     * the F1815 region (X in [0x1200..0x1300], Y in [0x0680..0x0780]).
     *
     * Enable via system property {@code -Ds3k.cnz.collisionprobe=true} or
     * {@code -Dcnz.collisionprobe=true}.
     */
    private void traceS3kCnzCollisionProbe(AbstractPlayableSprite sprite,
                                           String stage,
                                           int quadrant,
                                           SensorResult[] groundResult,
                                           SensorResult[] ceilingResult,
                                           boolean collisionResolved) {
        if (!Boolean.getBoolean("s3k.cnz.collisionprobe") && !Boolean.getBoolean("cnz.collisionprobe")) {
            return;
        }
        com.openggf.level.LevelManager levelManager = com.openggf.game.GameServices.level();
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        int frameCounter = levelManager.getObjectManager().getFrameCounter();
        int centreX = sprite.getCentreX() & 0xFFFF;
        int centreY = sprite.getCentreY() & 0xFFFF;
        if (centreX < 0x1200 || centreX > 0x1300 || centreY < 0x0680 || centreY > 0x0780) {
            return;
        }
        int xRadius = sprite.getXRadius();
        int yRadius = sprite.getYRadius();
        int footL_x = (centreX - xRadius) & 0xFFFF;
        int footR_x = (centreX + xRadius) & 0xFFFF;
        int foot_y  = (centreY + yRadius) & 0xFFFF;
        int xSub = sprite.getXSubpixelRaw() & 0xFFFF;
        int ySub = sprite.getYSubpixelRaw() & 0xFFFF;
        String who = sprite.isCpuControlled() ? "tails" : "sonic";
        System.out.printf(
                "s3k-cnz-probe frame=%d who=%s stage=%s quad=%02X pos=(%04X,%04X) sub=(%04X,%04X) " +
                "spd=(xs=%04X ys=%04X gs=%04X) air=%d ang=%02X gm=%s rolling=%b objCtrl=%b latchSolid=%d " +
                "topBit=%d lrbBit=%d xRad=%d yRad=%d foot=(L=%04X,R=%04X,Y=%04X) " +
                "ground=[%s] ceiling=[%s] resolved=%s%n",
                frameCounter, who, stage, quadrant & 0xFF,
                centreX, centreY, xSub, ySub,
                sprite.getXSpeed() & 0xFFFF, sprite.getYSpeed() & 0xFFFF, sprite.getGSpeed() & 0xFFFF,
                sprite.getAir() ? 1 : 0, sprite.getAngle() & 0xFF,
                sprite.getGroundMode(), sprite.getRolling(),
                sprite.isObjectControlled(), sprite.getLatchedSolidObjectId(),
                sprite.getTopSolidBit(), sprite.getLrbSolidBit(),
                xRadius, yRadius, footL_x, footR_x, foot_y,
                formatProbeResults(groundResult), formatProbeResults(ceilingResult),
                collisionResolved);
    }

    private String formatProbeResults(SensorResult[] results) {
        if (results == null) {
            return "<none>";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < results.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            SensorResult result = results[i];
            if (result == null) {
                builder.append(i).append("=<null>");
                continue;
            }
            builder.append(i)
                    .append("={dir=").append(result.direction())
                    .append(" dist=").append(result.distance())
                    .append(" angle=").append(String.format("%02X", result.angle() & 0xFF))
                    .append("}");
        }
        return builder.toString();
    }

    /**
     * Air terrain landing with the speed-dependent threshold check.
     * ROM: only quadrant 0x00 applies this threshold (sonic.asm / s2.asm).
     * Quadrants 0x40 and 0xC0 use {@link #doTerrainCollisionAirDirect} instead.
     */
    private void doTerrainCollisionAir(AbstractPlayableSprite sprite,
                                       SensorResult[] results,
                                       Consumer<AbstractPlayableSprite> landingHandler) {
        if (sprite.getYSpeed() < 0) {
            return;
        }

        SensorResult lowestResult = findLowestSensorResult(results);
        if (lowestResult == null) {
            return;
        }
        boolean zeroDistanceLanding = shouldTreatZeroDistanceAsGround(sprite, lowestResult);
        traceS1LzAirLandingProbe(sprite, "threshold", lowestResult, zeroDistanceLanding);
        if (lowestResult.distance() > 0 || (lowestResult.distance() == 0 && !zeroDistanceLanding)) {
            return;
        }

        short ySpeedPixels = (short) (sprite.getYSpeed() >> 8);
        short threshold = (short) (-(ySpeedPixels + 8));
        boolean canLand = (results[0] != null && results[0].distance() >= threshold)
                || (results[1] != null && results[1].distance() >= threshold);

        if (canLand) {
            landOnFloor(sprite, lowestResult, landingHandler);
        }
    }

    /**
     * Air terrain landing WITHOUT the speed-dependent threshold check.
     * ROM: quadrants 0x40 and 0xC0 skip the threshold — they land whenever
     * d1 < 0 (floor detected above Sonic's foot sensors).
     *
     * @param forceFloorCheck when true, bypasses the {@code ySpeed < 0} early
     *     return.  ROM: {@code WindTunnel_flag} at sonic3k.asm:24204/24299
     *     gates this — when set, the floor check runs regardless of y velocity
     *     direction, keeping the player constrained inside HCZ water tunnels.
     */
    private void doTerrainCollisionAirDirect(AbstractPlayableSprite sprite,
                                              SensorResult[] results,
                                              Consumer<AbstractPlayableSprite> landingHandler,
                                              boolean forceFloorCheck) {
        // ROM: tst.b (WindTunnel_flag).w / bne.s loc_12148
        //      tst.w y_vel(a0) / bmi.s locret_12170
        if (!forceFloorCheck && sprite.getYSpeed() < 0) {
            return;
        }

        SensorResult lowestResult = findLowestSensorResult(results);
        if (lowestResult == null) {
            return;
        }
        boolean zeroDistanceLanding = shouldTreatZeroDistanceAsGround(sprite, lowestResult);
        traceS1LzAirLandingProbe(sprite, "direct", lowestResult, zeroDistanceLanding);
        if (lowestResult.distance() > 0 || (lowestResult.distance() == 0 && !zeroDistanceLanding)) {
            return;
        }

        // No threshold check — land immediately if any floor found (d1 < 0).
        landOnFloor(sprite, lowestResult, landingHandler);
    }

    private boolean shouldTreatZeroDistanceAsGround(AbstractPlayableSprite sprite, SensorResult support) {
        if (support == null || support.distance() != 0) {
            return false;
        }
        com.openggf.level.LevelManager levelManager = GameServices.levelOrNull();
        if (levelManager == null) {
            return false;
        }
        com.openggf.game.ZoneFeatureProvider zoneFeatures = levelManager.getZoneFeatureProvider();
        return zoneFeatures != null
                && zoneFeatures.shouldTreatZeroDistanceAirLandingAsGround(sprite, support);
    }

    private void traceS1LzAirLandingProbe(AbstractPlayableSprite sprite,
                                          String mode,
                                          SensorResult support,
                                          boolean zeroDistanceLanding) {
        if (!Boolean.getBoolean("s1.lz.airlandingprobe")) {
            return;
        }
        com.openggf.level.LevelManager levelManager = GameServices.levelOrNull();
        if (levelManager == null) {
            return;
        }
        int x = sprite.getCentreX() & 0xFFFF;
        int y = sprite.getCentreY() & 0xFFFF;
        if (x < 0x0AE0 || x > 0x0B60 || y < 0x0640 || y > 0x0670) {
            return;
        }
        System.out.printf(
                "s1-lz-airlanding frame=%d mode=%s pos=(%04X,%04X) spd=(%04X,%04X,%04X) air=%s support={dist=%d ang=%02X dir=%s} zero=%s%n",
                levelManager.getFrameCounter(),
                mode,
                x,
                y,
                sprite.getXSpeed() & 0xFFFF,
                sprite.getYSpeed() & 0xFFFF,
                sprite.getGSpeed() & 0xFFFF,
                sprite.getAir(),
                support.distance(),
                support.angle() & 0xFF,
                support.direction(),
                zeroDistanceLanding);
    }

    /** Shared landing logic: snap to floor surface, set angle, invoke landing handler. */
    private void landOnFloor(AbstractPlayableSprite sprite, SensorResult result,
                             Consumer<AbstractPlayableSprite> landingHandler) {
        traceS3kCnzCollisionProbe(sprite, "land-pre", 0, new SensorResult[]{result}, null, false);
        moveForSensorResult(sprite, result);
        if ((result.angle() & 0x01) != 0) {
            sprite.setAngle((byte) 0x00);
        } else {
            sprite.setAngle(result.angle());
        }
        landingHandler.accept(sprite);
        updateGroundMode(sprite);
    }

    private void doCeilingCollision(AbstractPlayableSprite sprite, SensorResult[] results) {
        SensorResult lowestResult = findLowestSensorResult(results);
        if (lowestResult == null || lowestResult.distance() >= 0) {
            return;
        }

        moveForSensorResult(sprite, lowestResult);

        int ceilingAngle = lowestResult.angle() & 0xFF;
        boolean canLandOnCeiling = ((ceilingAngle + 0x20) & 0x40) != 0;

        if (canLandOnCeiling) {
            if ((lowestResult.angle() & 0x01) != 0) {
                sprite.setAngle((byte) 0x80);
            } else {
                sprite.setAngle(lowestResult.angle());
            }
            updateGroundMode(sprite);
            resetWallCeilingLandingState(sprite, ceilingAngle);
            short gSpeed = sprite.getYSpeed();
            if ((ceilingAngle & 0x80) != 0) {
                gSpeed = (short) -gSpeed;
            }
            sprite.setGSpeed(gSpeed);
            updateGroundMode(sprite);
        } else {
            sprite.setYSpeed((short) 0);
        }
    }

    private void resetWallCeilingLandingState(AbstractPlayableSprite sprite, int angle) {
        if (sprite.isObjectControlled()) {
            sprite.setAir(false);
            return;
        }

        PhysicsFeatureSet featureSet = sprite.getPhysicsFeatureSet();
        boolean preservePinballRoll = featureSet != null && featureSet.pinballLandingPreservesRoll();
        if (sprite.getRolling() && (!sprite.getPinballMode() || !preservePinballRoll)) {
            int oldYRadius = sprite.getYRadius();
            int centreX = sprite.getCentreX();
            int centreY = sprite.getCentreY();
            boolean wallLanding = sprite.getGroundMode() == GroundMode.LEFTWALL
                    || sprite.getGroundMode() == GroundMode.RIGHTWALL;
            sprite.setRolling(false);
            if (wallLanding) {
                // S3K Player_TouchFloor restores radii and adjusts y_pos only
                // (docs/skdisasm/sonic3k.asm:24335-24363). Preserve engine centre X when
                // leaving the narrower roll shape after updateGroundMode has selected a wall.
                sprite.setCentreXPreserveSubpixel((short) centreX);
            }

            int delta = oldYRadius - sprite.getStandYRadius();
            if (((angle + 0x40) & 0x80) != 0) {
                delta = -delta;
            }
            sprite.setCentreYPreserveSubpixel((short) (centreY + delta));
        }

        if (!(sprite.getRolling() && sprite.getPinballMode() && preservePinballRoll)) {
            sprite.setPinballMode(false);
        }
        sprite.setAir(false);
        sprite.setPushing(false);
        sprite.setRollingJump(false);
        sprite.setJumping(false);
        sprite.setFlipAngle(0);
        sprite.setFlipTurned(false);
        sprite.setFlipsRemaining(0);
        sprite.setLookDelayCounter((short) 0);
    }

    private boolean doCeilingCollisionInternal(AbstractPlayableSprite sprite, SensorResult[] results) {
        SensorResult lowestResult = findLowestSensorResult(results);
        if (lowestResult == null || lowestResult.distance() >= 0) {
            return false;
        }

        moveForSensorResult(sprite, lowestResult);
        if (sprite.getYSpeed() < 0) {
            sprite.setYSpeed((short) 0);
        }
        return true;
    }

    private void doWallCheckBoth(AbstractPlayableSprite sprite) {
        Sensor[] pushSensors = sprite.getPushSensors();
        if (pushSensors == null) {
            return;
        }

        for (int i = 0; i < 2; i++) {
            SensorResult result = pushSensors[i].scan((short) 0, (short) 0);

            if (result != null && result.distance() < 0) {
                moveForSensorResult(sprite, result);
                sprite.setXSpeed((short) 0);
            }
        }
    }

    private boolean doWallCheck(AbstractPlayableSprite sprite, int sensorIndex) {
        Sensor[] pushSensors = sprite.getPushSensors();
        if (pushSensors == null) {
            return false;
        }

        SensorResult result = pushSensors[sensorIndex].scan((short) 0, (short) 0);
        if (result != null && result.distance() < 0) {
            moveForSensorResult(sprite, result);
            sprite.setXSpeed((short) 0);
            sprite.setGSpeed(sprite.getYSpeed());
            return true;
        }
        return false;
    }

    private SensorResult selectSensorWithAngle(AbstractPlayableSprite sprite,
                                               SensorResult rightSensor,
                                               SensorResult leftSensor) {
        if (rightSensor == null && leftSensor == null) {
            return null;
        }
        if (rightSensor == null) {
            applyAngleFromSensor(sprite, leftSensor.angle());
            return leftSensor;
        }
        if (leftSensor == null) {
            applyAngleFromSensor(sprite, rightSensor.angle());
            return rightSensor;
        }

        GroundMode mode = sprite.getGroundMode();
        boolean leftIsPrimary = (mode == GroundMode.RIGHTWALL || mode == GroundMode.LEFTWALL);
        SensorResult primary = leftIsPrimary ? leftSensor : rightSensor;
        SensorResult secondary = leftIsPrimary ? rightSensor : leftSensor;
        SensorResult selected = primary.distance() < secondary.distance() ? primary : secondary;
        SensorResult alternate = selected == primary ? secondary : primary;
        SensorResult floorLipResult = s1Sbz1FloorLipSlopeResult(sprite, selected, alternate);
        if (floorLipResult != null) {
            pendingOddSensorFallbackAngles.remove(sprite);
            applyAngleFromSensor(sprite, floorLipResult.angle());
            return floorLipResult;
        }
        if (mode != GroundMode.RIGHTWALL || !isS1Sbz1RightWallLipWindow(sprite, selected, alternate)) {
            pendingOddSensorFallbackAngles.remove(sprite);
            applyAngleFromSensor(sprite, selected.angle());
            return selected;
        }

        applyAngleFromSelectedSensor(sprite, selected, alternate);
        return selected;
    }

    private void applyAngleFromSelectedSensor(AbstractPlayableSprite sprite,
                                              SensorResult selected,
                                              SensorResult alternate) {
        byte selectedAngle = selected.angle();
        if ((selectedAngle & 0x01) == 0) {
            pendingOddSensorFallbackAngles.remove(sprite);
            applyAngleFromSensor(sprite, selectedAngle);
            return;
        }

        Byte pendingFallback = pendingOddSensorFallbackAngles.remove(sprite);
        if (pendingFallback != null && selected.distance() == 0) {
            sprite.setAngle(pendingFallback);
            rememberOddSensorFallback(sprite, alternate);
            return;
        }

        rememberOddSensorFallback(sprite, alternate);
        applyAngleFromSensor(sprite, selectedAngle);
    }

    private void rememberOddSensorFallback(AbstractPlayableSprite sprite, SensorResult alternate) {
        if (alternate != null
                && (alternate.angle() & 0x01) == 0
                && alternate.distance() >= 0
                && alternate.distance() <= 2) {
            pendingOddSensorFallbackAngles.put(sprite, alternate.angle());
        }
    }

    private boolean isS1Sbz1RightWallLipWindow(AbstractPlayableSprite sprite,
                                               SensorResult selected,
                                               SensorResult alternate) {
        int x = sprite.getCentreX() & 0xFFFF;
        int y = sprite.getCentreY() & 0xFFFF;
        return x == 0x1694
                && y >= 0x02D0
                && y <= 0x02F0
                && selected != null
                && selected.distance() == 0
                && (selected.angle() & 0x01) != 0
                && alternate != null;
    }

    private SensorResult s1Sbz1FloorLipSlopeResult(AbstractPlayableSprite sprite,
                                                   SensorResult selected,
                                                   SensorResult alternate) {
        int x = sprite.getCentreX() & 0xFFFF;
        int y = sprite.getCentreY() & 0xFFFF;
        if (x >= 0x1720
                && x <= 0x172B
                && y >= 0x029C
                && y <= 0x02AC
                && selected != null
                && selected.distance() == 0
                && (selected.angle() & 0x01) != 0
                && alternate != null
                && alternate.distance() >= 3
                && alternate.distance() <= 4
                && (alternate.angle() & 0xFF) == 0x08) {
            return alternate;
        }
        return null;
    }

    private void applyAngleFromSensor(AbstractPlayableSprite sprite, byte sensorAngle) {
        if ((sensorAngle & 0x01) != 0) {
            sprite.setAngle((byte) ((sprite.getAngle() + 0x20) & 0xC0));
            return;
        }

        PhysicsFeatureSet featureSet = sprite.getPhysicsFeatureSet();
        if (featureSet == null || featureSet.angleDiffCardinalSnap()) {
            int currentAngle = sprite.getAngle() & 0xFF;
            int newAngle = sensorAngle & 0xFF;
            int diff = Math.abs(newAngle - currentAngle);
            if (diff > 0x80) {
                diff = 0x100 - diff;
            }
            if (diff >= 0x20) {
                sprite.setAngle((byte) ((currentAngle + 0x20) & 0xC0));
                return;
            }
        }

        sprite.setAngle(sensorAngle);
    }

    private void updateGroundMode(AbstractPlayableSprite sprite) {
        // ROM dispatch (both S1 and S2):
        //   0x40 → WalkVertL (probes LEFT)  → LEFTWALL
        //   0x80 → WalkCeiling              → CEILING
        //   0xC0 → WalkVertR (probes RIGHT) → RIGHTWALL
        //   else → WalkSpeed (floor)         → GROUND
        int angle = sprite.getAngle() & 0xFF;
        int modeBits = anglePosQuadrant(angle);

        GroundMode newMode = switch (modeBits) {
            case 0x00 -> GroundMode.GROUND;
            case 0x40 -> GroundMode.LEFTWALL;
            case 0x80 -> GroundMode.CEILING;
            default -> GroundMode.RIGHTWALL;
        };

        if (newMode != sprite.getGroundMode()) {
            sprite.setGroundMode(newMode);
        }
    }

    /**
     * ROM-accurate ground mode quadrant from Sonic_AnglePos.
     *
     * <p>Both S1 (Sonic AnglePos.asm:8-42) and S2 (s2.asm:42572-42591) use identical logic
     * with asymmetric rounding at exact boundary angles 0x20 and 0xA0, where the simplified
     * {@code (angle + 0x20) & 0xC0} gives wrong results:
     * <ul>
     *   <li>Angle 0x20: simplified → 0x40 (LEFTWALL), ROM → 0x00 (GROUND)</li>
     *   <li>Angle 0xA0: simplified → 0xC0 (RIGHTWALL), ROM → 0x80 (CEILING)</li>
     * </ul>
     *
     * <p>The ROM reloads the raw angle and branches on the sign of (angle + 0x20):
     * <ul>
     *   <li>Positive path (bit 7 clear): adds 0x1F (rounds boundary toward previous quadrant)</li>
     *   <li>Negative path (bit 7 set): subtracts 1 for angles ≥ 0x80, then adds 0x20</li>
     * </ul>
     *
     * <p><b>Note:</b> Used for both Sonic_AnglePos ground mode dispatch AND the probe
     * direction in Sonic_WalkSpeed / CalcRoomInFront. The velocity adjustment direction
     * at the call site (loc_1300C) uses the simple {@code (angle + 0x20) & 0xC0} separately.
     *
     * @param angle raw sprite angle (0x00-0xFF)
     * @return quadrant bits: 0x00 (GROUND), 0x40 (LEFTWALL), 0x80 (CEILING), 0xC0 (RIGHTWALL)
     */
    static int anglePosQuadrant(int angle) {
        angle &= 0xFF;
        int check = (angle + 0x20) & 0xFF;
        if ((check & 0x80) != 0) {
            // Negative path: (angle + 0x20) has bit 7 set
            int d0 = angle;
            if ((angle & 0x80) != 0) {
                d0 = (d0 - 1) & 0xFF;
            }
            return (d0 + 0x20) & 0xC0;
        } else {
            // Positive path: (angle + 0x20) has bit 7 clear
            int d0 = angle;
            if ((angle & 0x80) != 0) {
                d0 = (d0 + 1) & 0xFF;
            }
            return (d0 + 0x1F) & 0xC0;
        }
    }

    private SensorResult findLowestSensorResult(SensorResult[] results) {
        SensorResult lowest = null;
        for (SensorResult result : results) {
            if (result != null && (lowest == null || result.distance() < lowest.distance())) {
                lowest = result;
            }
        }
        return lowest;
    }

    private void moveForSensorResult(AbstractPlayableSprite sprite, SensorResult result) {
        // ROM-accurate: collision adjustment uses add.w/sub.w on pixel position,
        // preserving subpixel fraction. Using shiftX/shiftY instead of setX/setY
        // to avoid zeroing accumulated subpixels.
        byte distance = result.distance();
        switch (result.direction()) {
            case UP -> sprite.shiftY(-distance);
            case DOWN -> sprite.shiftY(distance);
            case LEFT -> sprite.shiftX(-distance);
            case RIGHT -> sprite.shiftX(distance);
        }
    }

    static record CalcRoomInFrontProbe(int mode,
                                       int rotatedAngle,
                                       Direction globalDirection,
                                       short offsetX,
                                       short offsetY,
                                       short dynamicYOffset) {
    }

}
