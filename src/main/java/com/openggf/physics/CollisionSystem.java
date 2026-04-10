package com.openggf.physics;

import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.GroundMode;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

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
 * 2. Solid object resolution (platforms, moving solids, sloped solids)
 * 3. Post-resolution adjustments (ground mode, gSpeed recompute, headroom)
 */
public class CollisionSystem {
    private static final Logger LOGGER = Logger.getLogger(CollisionSystem.class.getName());

    private static CollisionSystem bootstrapInstance;

    private final TerrainCollisionManager terrainCollisionManager;
    private final GroundSensor calcRoomProbe = new GroundSensor(null, Direction.DOWN, (byte) 0, (byte) 0, true);
    private ObjectManager objectManager;

    // Trace for debugging/testing - defaults to no-op
    private CollisionTrace trace = NoOpCollisionTrace.INSTANCE;


    public CollisionSystem() {
        this(GameServices.terrainCollision());
    }

    public CollisionSystem(TerrainCollisionManager terrainCollisionManager) {
        this.terrainCollisionManager = terrainCollisionManager;
    }

    public static synchronized CollisionSystem getInstance() {
        var runtime = RuntimeManager.getCurrent();
        if (runtime != null) {
            return runtime.getCollisionSystem();
        }
        if (bootstrapInstance == null) {
            bootstrapInstance = new CollisionSystem();
        }
        return bootstrapInstance;
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
     * Phase 2: Resolve solid object contacts.
     * Currently delegates to ObjectManager.SolidContacts.
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
        if (objectManager == null) {
            return false;
        }
        return objectManager.hasStandingContact(player);
    }

    /**
     * Get headroom distance to nearest solid object above player.
     * Convenience method that delegates to SolidContacts.
     */
    public int getHeadroomDistance(AbstractPlayableSprite player, int hexAngle) {
        if (objectManager == null) {
            return Integer.MAX_VALUE;
        }
        return objectManager.getHeadroomDistance(player, hexAngle);
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

    public boolean hasObjectSupport(AbstractPlayableSprite player) {
        return isRidingObject(player) || hasStandingContact(player);
    }

    public boolean hasEnoughHeadroom(AbstractPlayableSprite player, int hexAngle) {
        int terrainDistance = getTerrainHeadroomDistance(player, hexAngle);
        int objectDistance = getHeadroomDistance(player, hexAngle);
        return Math.min(terrainDistance, objectDistance) >= 6;
    }

    public void resolveGroundWallCollision(AbstractPlayableSprite sprite) {
        if (sprite == null || sprite.isTunnelMode() || sprite.isStickToConvex()) {
            return;
        }
        var levelManager = sprite.currentLevelManager();
        if (levelManager == null || levelManager.getCurrentLevel() == null) {
            return;
        }

        int angle = sprite.getAngle() & 0xFF;
        short gSpeed = sprite.getGSpeed();
        int angleCheck = (angle + 0x40) & 0xFF;
        if ((angleCheck & 0x80) != 0 || gSpeed == 0) {
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
                sprite.setPushing(true);
            }
            case 0x80 -> sprite.setYSpeed((short) (sprite.getYSpeed() - velocityAdjustment));
            case 0xC0 -> {
                sprite.setXSpeed((short) (sprite.getXSpeed() + velocityAdjustment));
                sprite.setGSpeed((short) 0);
                sprite.setPushing(true);
            }
            default -> {
            }
        }
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
        // ROM: Sonic_AnglePos runs unconditionally — even when standing on an object.
        // However, terrain floor snapping and angle updates conflict with the platform's
        // own Y positioning (MvSonicOnPtfm). The ROM resolves this by running AnglePos
        // first (probing terrain), then the platform overrides Y via MvSonicOnPtfm.
        // The engine can't replicate this order (platform riding runs after physics).
        // Skip the full terrain attachment when on an object — the platform handles Y,
        // and the X carry comes from the riding deltaX mechanism.
        if (sprite.isOnObject()) {
            return;
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
            if (!hasObjectSupport.getAsBoolean()) {
                sprite.setAir(true);
                sprite.setPushing(false);
            }
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
            if (!hasObjectSupport.getAsBoolean()) {
                sprite.setAir(true);
                sprite.setPushing(false);
            }
            return;
        }

        moveForSensorResult(sprite, selectedResult);
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
        int quadrant = TrigLookupTable.calcMovementQuadrant(sprite.getXSpeed(), sprite.getYSpeed());
        switch (quadrant) {
            case 0x00 -> {
                doWallCheckBoth(sprite);
                SensorResult[] groundResult = terrainProbes(sprite, sprite.getGroundSensors(), "ground");
                doTerrainCollisionAir(sprite, groundResult, landingHandler);
            }
            case 0x40 -> {
                if (doWallCheck(sprite, 0)) {
                    return;
                }
                SensorResult[] ceilingResult = terrainProbes(sprite, sprite.getCeilingSensors(), "ceiling");
                if (!doCeilingCollisionInternal(sprite, ceilingResult)) {
                    SensorResult[] groundResult = terrainProbes(sprite, sprite.getGroundSensors(), "ground");
                    doTerrainCollisionAirDirect(sprite, groundResult, landingHandler);
                }
            }
            case 0x80 -> {
                doWallCheckBoth(sprite);
                SensorResult[] ceilingResult = terrainProbes(sprite, sprite.getCeilingSensors(), "ceiling");
                doCeilingCollision(sprite, ceilingResult);
            }
            case 0xC0 -> {
                if (doWallCheck(sprite, 1)) {
                    return;
                }
                SensorResult[] ceilingResult = terrainProbes(sprite, sprite.getCeilingSensors(), "ceiling");
                if (!doCeilingCollisionInternal(sprite, ceilingResult)) {
                    SensorResult[] groundResult = terrainProbes(sprite, sprite.getGroundSensors(), "ground");
                    doTerrainCollisionAirDirect(sprite, groundResult, landingHandler);
                }
            }
            default -> {
            }
        }
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

        if (lowestResult == null || lowestResult.distance() >= 0) {
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
     */
    private void doTerrainCollisionAirDirect(AbstractPlayableSprite sprite,
                                              SensorResult[] results,
                                              Consumer<AbstractPlayableSprite> landingHandler) {
        if (sprite.getYSpeed() < 0) {
            return;
        }

        SensorResult lowestResult = findLowestSensorResult(results);
        if (lowestResult == null || lowestResult.distance() >= 0) {
            return;
        }

        // No threshold check — land immediately if any floor found (d1 < 0).
        landOnFloor(sprite, lowestResult, landingHandler);
    }

    /** Shared landing logic: snap to floor surface, set angle, invoke landing handler. */
    private void landOnFloor(AbstractPlayableSprite sprite, SensorResult result,
                             Consumer<AbstractPlayableSprite> landingHandler) {
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
            sprite.setAir(false);
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
        applyAngleFromSensor(sprite, selected.angle());
        return selected;
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
