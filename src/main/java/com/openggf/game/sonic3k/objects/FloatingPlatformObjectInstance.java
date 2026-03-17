package com.openggf.game.sonic3k.objects;

import com.openggf.game.OscillationManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0x51 - Floating Platform (Sonic 3 &amp; Knuckles).
 * <p>
 * A top-solid moving platform used in AIZ, HCZ, and MGZ. Supports 13 movement
 * patterns selected by subtype bits [3:0], with platform size selected by bits [5:4].
 * <p>
 * Subtype encoding:
 * <ul>
 *   <li>Bits [5:4]: size index (0-2) into byte_254FA size table</li>
 *   <li>Bits [3:0]: movement type (0-12) from FloatingPlatformIndex</li>
 * </ul>
 * <p>
 * Movement types:
 * <ul>
 *   <li>0: Stationary - gentle sine-bob when player stands</li>
 *   <li>1: Horizontal64 - oscillation range 64px</li>
 *   <li>2: Horizontal128 - oscillation range 128px</li>
 *   <li>3: Vertical64 - oscillation range 64px</li>
 *   <li>4: Vertical128 - oscillation range 128px</li>
 *   <li>5: DiagonalUp - horizontal 128px + half vertical (up-right)</li>
 *   <li>6: DiagonalDown - inverted horizontal + half vertical (down-right)</li>
 *   <li>7: Rising - weight-triggered rise to target</li>
 *   <li>8-11: Square32/96/160/224 - square orbit state machines</li>
 *   <li>12: Horizontal256 - slow accumulating sweep</li>
 * </ul>
 * <p>
 * ROM references: Obj_FloatingPlatform (sonic3k.asm), byte_254FA, FloatingPlatformIndex,
 * Platform_Stationary (line 50190), sub_24FDE.
 */
public class FloatingPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final Logger LOG = Logger.getLogger(FloatingPlatformObjectInstance.class.getName());

    // Priority: $180 = bucket 3 (ROM: move.w #$180,priority(a0))
    private static final int PRIORITY = 3;

    // ===== Size table (byte_254FA): 3-byte entries =====
    // Each entry: halfWidth, halfHeight, mappingFrame
    private static final int[][] SIZE_TABLE = {
            {0x20, 0x20, 0},  // Index 0: 32x32, frame 0
            {0x18, 0x0C, 0},  // Index 1: 24x12, frame 0
            {0x20, 0x14, 0},  // Index 2: 32x20, frame 0
    };

    // ===== Square path oscillation config per type =====
    // Each entry: {oscValueOffset, oscDeltaOffset}
    // S3K oscillating table offsets (disasm offset - 2 = OscMgr offset)
    private static final int[][] SQUARE_OSC_CONFIG = {
            {0x28, 0x2A},  // Type 8: Square32
            {0x2C, 0x2E},  // Type 9: Square96
            {0x30, 0x32},  // Type 10: Square160
            {0x34, 0x36},  // Type 11: Square224
    };

    // ===== Zone-specific configuration =====

    /** Per-zone configuration record. */
    private record ZoneConfig(String artKey, int artTileBase) {}

    private static final ZoneConfig AIZ1_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.FLOATING_PLATFORM_AIZ1, 0x03F7);

    private static final ZoneConfig AIZ2_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.FLOATING_PLATFORM_AIZ2, 0x0440);

    private static final ZoneConfig HCZ_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.FLOATING_PLATFORM_HCZ, 0x041D);

    private static final ZoneConfig MGZ_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.FLOATING_PLATFORM_MGZ, 0x0001);

    // ===== Instance state =====

    private final ZoneConfig config;
    private final int halfWidth;
    private final int halfHeight;
    private final int mappingFrame;
    private final int moveType;
    private final boolean xFlip;

    private int x;
    private int y;
    private final int baseX;  // objoff_30: saved X position
    private final int baseY;  // objoff_32: saved Y position
    private ObjectSpawn dynamicSpawn;

    // Stationary bob state (type 0)
    // $3A(a0): bob angle, incremented/decremented by 4 per frame
    private int bobAngle;

    // Rising state (type 7)
    private int yVel;       // y_vel subpixel velocity
    private boolean rising; // activated when player stands

    // Square path state (types 8-11)
    // $2E(a0) & 3: current quadrant (0-3)
    private int squareQuadrant;

    // Horizontal256 state (type 12)
    // $40(a0): velocity accumulator, $36(a0): position accumulator
    // $3C(a0): direction flag (0 = accelerating positive, 1 = decelerating)
    private int sweepVelocity;
    private int sweepPosition;
    private int sweepDirection;

    public FloatingPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "FloatingPlatform");

        // Extract size index from subtype bits [5:4]
        int sizeIndex = (spawn.subtype() >> 4) & 0x03;
        if (sizeIndex >= SIZE_TABLE.length) {
            sizeIndex = SIZE_TABLE.length - 1;
        }

        this.halfWidth = SIZE_TABLE[sizeIndex][0];
        this.halfHeight = SIZE_TABLE[sizeIndex][1];
        this.mappingFrame = SIZE_TABLE[sizeIndex][2];

        // Movement type from bits [3:0]
        this.moveType = spawn.subtype() & 0x0F;

        // X-flip from render flags (status bit 0)
        this.xFlip = (spawn.renderFlags() & 0x01) != 0;

        // Store base positions for oscillation reference
        this.baseX = spawn.x();
        this.baseY = spawn.y();
        this.x = baseX;
        this.y = baseY;

        // Resolve zone-specific art config
        this.config = resolveConfig();

        refreshDynamicSpawn();
    }

    // ===== SolidObjectProvider =====

    @Override
    public SolidObjectParams getSolidParams() {
        // ROM: addq.w #1,d3 before SolidObjectTop call (sonic3k.asm:50840)
        return new SolidObjectParams(halfWidth, halfHeight, halfHeight + 1);
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return !isDestroyed();
    }

    // ===== SolidObjectListener =====

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // Platform state is driven via ObjectManager standing checks.
        // Rising platform (type 7) activation is handled in update() via isStanding().
    }

    // ===== ObjectInstance =====

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return dynamicSpawn != null ? dynamicSpawn : spawn;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        applyMovement();
        refreshDynamicSpawn();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (config == null || config.artKey == null) {
            appendDebug(commands);
            return;
        }

        ObjectRenderManager renderManager = getRenderManager();
        if (renderManager == null) {
            appendDebug(commands);
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(config.artKey);
        if (renderer == null || !renderer.isReady()) {
            appendDebug(commands);
            return;
        }

        renderer.drawFrameIndex(mappingFrame, x, y, xFlip, false);
    }

    // ===== Movement dispatch =====

    /**
     * Applies movement based on move type (subtype &amp; 0x0F).
     * Ported from FloatingPlatformIndex (sonic3k.asm).
     */
    private void applyMovement() {
        switch (moveType) {
            case 0 -> applyStationaryBob();
            case 1 -> applyHorizontalOscillation(0x08, 0x40);   // Osc[2], amplitude 64
            case 2 -> applyHorizontalOscillation(0x1C, 0x80);   // Osc[7], amplitude 128
            case 3 -> applyVerticalOscillation(0x08, 0x40);      // Osc[2], amplitude 64
            case 4 -> applyVerticalOscillation(0x1C, 0x80);      // Osc[7], amplitude 128
            case 5 -> applyDiagonalUp();
            case 6 -> applyDiagonalDown();
            case 7 -> applyRising();
            case 8, 9, 10, 11 -> applySquarePath(moveType - 8);
            case 12 -> applyHorizontal256();
            default -> { /* Unknown type - stationary */ }
        }
    }

    // ===== Movement type 0: Stationary bob =====

    /**
     * Platform_Stationary (sonic3k.asm lines 50190-50211).
     * <p>
     * Gentle sine-bob in Y when player stands on the platform.
     * Uses a byte angle field ($3A) that increments by +4/frame while standing
     * and decrements by -4 while not standing, clamped to [0, 0x40].
     * Position offset = GetSineCosine(angle) >> 6 (max ~4px).
     */
    private void applyStationaryBob() {
        boolean standing = isStanding();

        if (standing) {
            // Increment angle toward 0x40
            bobAngle += 4;
            if (bobAngle > 0x40) {
                bobAngle = 0x40;
            }
        } else {
            // Decrement angle toward 0
            bobAngle -= 4;
            if (bobAngle < 0) {
                bobAngle = 0;
            }
        }

        // GetSineCosine: 256-entry quarter-wave table, angle 0x40 = peak (sin = 0x100)
        // Result >> 6 gives max displacement of 4 pixels
        int sineValue = getSine(bobAngle);
        int displacement = sineValue >> 6;

        y = baseY + displacement;
    }

    // ===== Movement types 1-2: Horizontal oscillation =====

    /**
     * Shared horizontal oscillation subroutine (sub_24FDE).
     * <p>
     * ROM: reads oscillation byte, applies xFlip polarity, subtracts from baseX.
     * <pre>
     *   d0 = Oscillating_table+offset (high byte)
     *   if status_bit0 (xFlip): d0 = -d0 + amplitude
     *   x_pos = objoff_30 - d0
     * </pre>
     *
     * @param oscOffset OscillationManager byte offset
     * @param amplitude amplitude parameter for inverted phase
     */
    private void applyHorizontalOscillation(int oscOffset, int amplitude) {
        int oscValue = OscillationManager.getByte(oscOffset);

        // ROM: sub_24FDE (sonic3k.asm:50229-50239)
        // btst #0,status(a0) / beq.s loc_24FEA / neg.w d0 / add.w d1,d0
        if (xFlip) {
            oscValue = (-oscValue + amplitude) & 0xFF;
        }

        // ROM: add.w d0,d1 / move.w d1,x_pos(a0) (line 50237)
        x = baseX + oscValue;
    }

    // ===== Movement types 3-4: Vertical oscillation =====

    /**
     * Vertical oscillation - same as horizontal but applied to Y.
     *
     * @param oscOffset OscillationManager byte offset
     * @param amplitude amplitude parameter for inverted phase
     */
    private void applyVerticalOscillation(int oscOffset, int amplitude) {
        int oscValue = OscillationManager.getByte(oscOffset);

        if (xFlip) {
            oscValue = (-oscValue + amplitude) & 0xFF;
        }

        y = baseY - oscValue;
    }

    // ===== Movement type 5: Diagonal up =====

    /**
     * DiagonalUp movement (sonic3k.asm lines 50261-50279).
     * <p>
     * Uses osc offset 0x1C for X (amplitude 128), then halves the raw osc byte
     * with lsr.b #1 and applies to Y (amplitude 64). Both X and Y use the same
     * oscillation source, creating a diagonal trajectory.
     */
    private void applyDiagonalUp() {
        int oscByte = OscillationManager.getByte(0x1C);

        // X component: full amplitude via sub_24FDE (add.w for X)
        int oscX = oscByte;
        if (xFlip) {
            oscX = (-oscX + 0x80) & 0xFF;
        }
        x = baseX + oscX;

        // Y component: halved oscillation value (lsr.b #1,d0), sub.w for Y
        int oscY = (oscByte >> 1) & 0xFF;
        if (xFlip) {
            oscY = (-oscY + 0x40) & 0xFF;
        }
        y = baseY - oscY;
    }

    // ===== Movement type 6: Diagonal down =====

    /**
     * DiagonalDown movement (sonic3k.asm lines 50281-50292).
     * <p>
     * Same as DiagonalUp but the X oscillation byte is negated before the
     * xFlip check (neg.w d0; add.w d1,d0), inverting the X phase. Y moves
     * the same as DiagonalUp.
     */
    private void applyDiagonalDown() {
        int oscByte = OscillationManager.getByte(0x1C);

        // X component: negated phase (neg.w d0; add.w d1,d0 before sub_24FDE)
        // Then sub_24FDE applies xFlip and add.w for X
        int oscX = (-oscByte + 0x80) & 0xFF;
        if (xFlip) {
            oscX = (-oscX + 0x80) & 0xFF;
        }
        x = baseX + oscX;

        // Y component: halved, same as DiagonalUp (sub.w for Y)
        int oscY = (oscByte >> 1) & 0xFF;
        if (xFlip) {
            oscY = (-oscY + 0x40) & 0xFF;
        }
        y = baseY - oscY;
    }

    // ===== Movement type 7: Rising =====

    /**
     * Weight-triggered rising platform (sonic3k.asm lines 50294-50335).
     * <p>
     * When player stands on the platform, it begins rising toward a target
     * (baseY - 0x80). Accelerates at +/-8 per frame, creating a bouncing
     * overshoot motion around the target. Platform never resets.
     */
    private void applyRising() {
        if (!rising && isStanding()) {
            rising = true;
        }

        if (!rising) {
            return;
        }

        // Apply velocity to position (ObjectMove equivalent)
        // ROM: y_pos += y_vel (sign-extended word addition)
        y += (short) yVel >> 8;

        // Target position: baseY - 0x80 (128 pixels above spawn)
        int target = baseY - 0x80;

        // Accelerate toward target
        // ROM: cmp.w y_pos(a0),d0 / bhs.s + / neg.w d1
        // When target >= y (platform is above target), accel is positive (push down)
        // When target < y (platform is below target), accel is negative (push up)
        int accel = 8;
        if (y > target) {
            accel = -8;
        }
        yVel += accel;
    }

    // ===== Movement types 8-11: Square paths =====

    /**
     * Square orbit path (sonic3k.asm lines 50337-50413).
     * <p>
     * Uses a 4-quadrant state machine that advances when the oscillation delta
     * word crosses zero. Each quadrant moves the platform in one cardinal
     * direction (right, down, left, up) around the spawn point.
     * <p>
     * The oscillation value byte provides displacement within each quadrant.
     * When the delta crosses zero (getWord(deltaOffset) == 0), the platform
     * snaps to the base axis and advances to the next quadrant.
     *
     * @param configIndex index into SQUARE_OSC_CONFIG (0-3 for types 8-11)
     */
    private void applySquarePath(int configIndex) {
        if (configIndex < 0 || configIndex >= SQUARE_OSC_CONFIG.length) {
            return;
        }

        int oscValueOffset = SQUARE_OSC_CONFIG[configIndex][0];
        int oscDeltaOffset = SQUARE_OSC_CONFIG[configIndex][1];

        // Read oscillation value (displacement) and delta (for zero-crossing)
        int oscValue = OscillationManager.getByte(oscValueOffset);
        int oscDelta = OscillationManager.getWord(oscDeltaOffset);

        // Check for zero-crossing: advance quadrant when delta == 0
        if (oscDelta == 0) {
            squareQuadrant = (squareQuadrant + 1) & 0x03;
        }

        // Apply displacement based on current quadrant
        // ROM: quadrant 0=+X, 1=+Y, 2=-X, 3=-Y
        switch (squareQuadrant) {
            case 0 -> {
                x = baseX + oscValue;
                y = baseY;
            }
            case 1 -> {
                x = baseX;
                y = baseY + oscValue;
            }
            case 2 -> {
                x = baseX - oscValue;
                y = baseY;
            }
            case 3 -> {
                x = baseX;
                y = baseY - oscValue;
            }
        }
    }

    // ===== Movement type 12: Horizontal256 =====

    /**
     * Slow accumulating horizontal sweep (sonic3k.asm lines 50415-50455).
     * <p>
     * Uses a velocity accumulator ($40) and position accumulator ($36) to create
     * a slow, smooth back-and-forth sweep covering approximately 256 pixels.
     * Direction flag ($3C) controls acceleration polarity.
     * <p>
     * Algorithm:
     * <pre>
     *   if direction == 0: velocity += 4
     *   else:              velocity -= 4
     *   position += velocity
     *   if position >= 0x8000: direction = 1, velocity = 0
     *   if position < 0:       direction = 0, velocity = 0
     *   x = baseX + (position >> 8)
     * </pre>
     */
    private void applyHorizontal256() {
        if (sweepDirection == 0) {
            sweepVelocity += 4;
        } else {
            sweepVelocity -= 4;
        }

        sweepPosition += sweepVelocity;

        // Check bounds and reverse
        if (sweepPosition >= 0x8000) {
            sweepPosition = 0x7FFF;
            sweepDirection = 1;
            sweepVelocity = 0;
        } else if (sweepPosition < 0) {
            sweepPosition = 0;
            sweepDirection = 0;
            sweepVelocity = 0;
        }

        // ROM (sonic3k.asm:50360-50368): position byte → pixel displacement
        // btst #0,status(a0) / beq.s loc_250FE / neg.w d0 / add.w d2,d0
        int displacement = (sweepPosition >> 8) & 0xFF;
        if (xFlip) {
            displacement = (-displacement + 0x7F) & 0xFF;
        }

        // ROM: add.w $30(a0),d0 (line 50369)
        x = baseX + displacement;
    }

    // ===== Helpers =====

    /**
     * ROM GetSineCosine equivalent for the stationary bob.
     * The ROM uses a 256-entry quarter-wave lookup table where angle 0x40 = peak.
     * We compute the equivalent: sin(angle * PI / 128) * 256.
     *
     * @param angle ROM-style angle (0-0xFF, but we only use 0-0x40 for bob)
     * @return sine value in the range [-256, 256]
     */
    private static int getSine(int angle) {
        return (int) (Math.sin(angle * Math.PI / 128.0) * 256.0);
    }

    /**
     * Checks if any player is currently riding (standing on) this platform.
     */
    private boolean isStanding() {
        try {
            LevelManager manager = LevelManager.getInstance();
            if (manager != null && manager.getObjectManager() != null) {
                return manager.getObjectManager().isAnyPlayerRiding(this);
            }
        } catch (Exception e) {
            // Safe fallback for test environments
        }
        return false;
    }

    private void refreshDynamicSpawn() {
        if (dynamicSpawn == null || dynamicSpawn.x() != x || dynamicSpawn.y() != y) {
            dynamicSpawn = new ObjectSpawn(
                    x, y,
                    spawn.objectId(), spawn.subtype(), spawn.renderFlags(),
                    spawn.respawnTracked(), spawn.rawYWord());
        }
    }

    private static ObjectRenderManager getRenderManager() {
        try {
            LevelManager lm = LevelManager.getInstance();
            return lm != null ? lm.getObjectRenderManager() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolves zone-specific configuration from the current level.
     * Falls back to AIZ1 config if zone cannot be determined.
     */
    private static ZoneConfig resolveConfig() {
        try {
            LevelManager lm = LevelManager.getInstance();
            if (lm != null) {
                int zone = lm.getCurrentZone();
                int act = lm.getCurrentAct();
                if (zone == Sonic3kZoneIds.ZONE_AIZ) {
                    return act == 0 ? AIZ1_CONFIG : AIZ2_CONFIG;
                }
                if (zone == Sonic3kZoneIds.ZONE_HCZ) {
                    return HCZ_CONFIG;
                }
                if (zone == Sonic3kZoneIds.ZONE_MGZ) {
                    return MGZ_CONFIG;
                }
                LOG.warning("FloatingPlatform: unknown zone 0x"
                        + Integer.toHexString(zone) + ", defaulting to AIZ1 config");
            }
        } catch (Exception e) {
            LOG.fine("Could not resolve zone config: " + e.getMessage());
        }
        return AIZ1_CONFIG;
    }

    // ===== Debug rendering =====

    private void appendDebug(List<GLCommand> commands) {
        int left = x - halfWidth;
        int right = x + halfWidth;
        int top = y - halfHeight;
        int bottom = y + halfHeight;

        appendLine(commands, left, top, right, top);
        appendLine(commands, right, top, right, bottom);
        appendLine(commands, right, bottom, left, bottom);
        appendLine(commands, left, bottom, left, top);
    }

    private void appendLine(List<GLCommand> commands, int x1, int y1, int x2, int y2) {
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                0.2f, 0.8f, 0.5f, x1, y1, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                0.2f, 0.8f, 0.5f, x2, y2, 0, 0));
    }
}
