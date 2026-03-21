package com.openggf.game.sonic1.objects;

import com.openggf.camera.Camera;
import com.openggf.game.sonic1.Sonic1SwitchManager;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.OscillationManager;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 18 - Platforms (GHZ, SYZ, SLZ).
 * <p>
 * Top-solid platforms with multiple movement subtypes including stationary,
 * horizontal/vertical oscillation, falling, and switch-activated rising.
 * Platforms nudge downward when stood on (sine-based spring effect).
 * <p>
 * Subtype (low nybble) controls movement behavior:
 * <ul>
 *   <li>0x00: Stationary</li>
 *   <li>0x01: Horizontal oscillation (self-driven, right-to-left start)</li>
 *   <li>0x02: Vertical oscillation (self-driven, down start)</li>
 *   <li>0x03: Fall when stood on (30-frame delay, transitions to 0x04)</li>
 *   <li>0x04: Falling (gravity 0x38, 32-frame countdown then drop player)</li>
 *   <li>0x05: Horizontal oscillation (self-driven, left-to-right start)</li>
 *   <li>0x06: Vertical oscillation (self-driven, up start)</li>
 *   <li>0x07: Switch-activated rising (high nybble = switch index)</li>
 *   <li>0x08: Rising after switch (rises 0x200 pixels at 2px/frame)</li>
 *   <li>0x0A: Vertical oscillation (self-driven, half amplitude)</li>
 *   <li>0x0B: Vertical oscillation (global oscillator, down start)</li>
 *   <li>0x0C: Vertical oscillation (global oscillator, up start)</li>
 * </ul>
 * <p>
 * Reference: docs/s1disasm/_incObj/18 Platforms.asm
 */
public class Sonic1PlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // From disassembly: move.b #$20,obActWid(a0)
    private static final int HALF_WIDTH = 0x20;

    // Platform surface height for SolidObjectParams (platform is thin)
    private static final int HALF_HEIGHT = 0x08;

    // From disassembly: move.b #4,obPriority(a0)
    private static final int PRIORITY = 4;

    // Nudge physics: move.w #$400,d1
    private static final int NUDGE_AMPLITUDE = 0x400;
    // From disassembly: cmpi.b #$40,objoff_38(a0)
    private static final int NUDGE_MAX_ANGLE = 0x40;
    // From disassembly: addq.b #4,objoff_38(a0) / subq.b #4,objoff_38(a0)
    private static final int NUDGE_ANGLE_STEP = 4;

    // Type 03: move.w #30,objoff_3A(a0)
    private static final int FALL_STAND_DELAY = 30;
    // Type 04: move.w #32,objoff_3A(a0) (countdown before dropping player)
    private static final int FALL_COUNTDOWN = 32;
    // Type 04: addi.w #$38,obVelY(a0)
    private static final int FALL_GRAVITY = 0x38;
    // Type 04: addi.w #$E0,d0 (delete threshold below bottom boundary)
    private static final int FALL_DELETE_OFFSET = 0xE0;

    // Type 07: move.w #60,objoff_3A(a0)
    private static final int SWITCH_DELAY = 60;
    // Type 08: subi.w #$200,d0 (rise distance)
    private static final int RISE_DISTANCE = 0x200;
    // Type 08: subq.w #2,objoff_2C(a0) (rise speed)
    private static final int RISE_SPEED = 2;

    // Oscillation indices (offset into OscillationManager data after bitfield)
    // v_oscillate+$1A -> data offset 0x18 (oscillator 6: freq=8, amp=0x40)
    private static final int OSC_SELF_DRIVEN = 0x18;
    // v_oscillate+$E -> data offset 0x0C (oscillator 3: freq=2, amp=0x30)
    private static final int OSC_GLOBAL = 0x0C;

    private final LevelManager levelManager;
    private final int zoneIndex;

    // Dynamic position
    private int x;
    private int y;

    // Saved base positions (objoff_32 = spawn X, objoff_34 = spawn Y)
    private final int baseX;
    private final int baseY;
    // objoff_2C: working Y position (modified by vertical movement + nudge)
    private int workingY;

    // Movement subtype (low nybble of obSubtype)
    private int moveType;

    // Nudge angle (objoff_38): 0 = no nudge, increases to $40 while stood on
    private int nudgeAngle;

    // Timer (objoff_3A): multi-purpose timer for types 03, 04, 07
    private int timer;

    // Velocity for falling platform (type 04)
    private int yVelocity;
    // Fractional Y for type 04 falling (16.16 fixed point: high 16 = Y, low 16 = subpixel)
    private int yFrac;

    // Mapping frame: 0 = small platform, 1 = large column (GHZ only, subtype 0x0A)
    private int mappingFrame;

    // Whether player is currently standing on this platform (obStatus bit 3)
    private boolean playerStanding;

    // Cached oscillator value from previous frame (obAngle/objoff_26).
    // Self-driven movement reads this, then .chgmotion stores current value for next frame.
    private int cachedOscillator;

    // When true, platform is in routine 8 (Plat_Action) — nudge angle is frozen.
    // This happens after type 04 timer expires and detaches the player.
    private boolean inFallingRoutine;

    private ObjectSpawn dynamicSpawn;

    public Sonic1PlatformObjectInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, "Platform");
        this.levelManager = levelManager;
        this.zoneIndex = levelManager.getRomZoneId();

        this.baseX = spawn.x();
        this.baseY = spawn.y();
        this.workingY = spawn.y();
        this.x = spawn.x();
        this.y = spawn.y();

        // SLZ forces subtype 3 (fall-when-stood-on) regardless of placed subtype.
        // Disasm: move.b #3,obSubtype(a0) — overwrites full byte before frame selection.
        int effectiveSubtype;
        if (zoneIndex == Sonic1Constants.ZONE_SLZ) {
            effectiveSubtype = 3;
        } else {
            effectiveSubtype = spawn.subtype() & 0xFF;
        }
        this.moveType = effectiveSubtype & 0x0F;

        // Frame selection: full effective subtype == 0x0A uses frame 1 (large column), others use frame 0.
        // Disasm: cmpi.b #$A,d0 compares full byte after SLZ override.
        this.mappingFrame = effectiveSubtype == 0x0A ? 1 : 0;

        this.nudgeAngle = 0;
        this.timer = 0;
        this.yVelocity = 0;
        this.yFrac = 0;
        this.playerStanding = false;
        // Disasm: move.w #$80,obAngle(a0) — writes word $0080 at offset $26.
        // On 68000 big-endian, byte at $26 = $00, byte at $27 = $80.
        // Movement reads move.b obAngle(a0),d1 (byte at $26) = $00.
        this.cachedOscillator = 0x00;
        this.inFallingRoutine = false;

        refreshDynamicSpawn();
    }

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
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // Check if player is standing on us via ObjectManager
        playerStanding = isPlayerRiding();

        if (inFallingRoutine) {
            // Routine 8 (Plat_Action): nudge angle is frozen — no increment/decrement.
            // Only Plat_Move and Plat_Nudge execute.
        } else if (playerStanding) {
            // Routine 4 (Plat_Action2): increment nudge angle toward max (addq.b #4,objoff_38)
            if (nudgeAngle < NUDGE_MAX_ANGLE) {
                nudgeAngle += NUDGE_ANGLE_STEP;
            }
        } else {
            // Routine 2 (Plat_Solid): decrement nudge angle toward 0 (subq.b #4,objoff_38)
            if (nudgeAngle > 0) {
                nudgeAngle -= NUDGE_ANGLE_STEP;
            }
        }

        // Apply movement
        applyMovement(player);

        // Apply nudge (sine-based vertical offset)
        applyNudge();

        refreshDynamicSpawn();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(ObjectArtKeys.PLATFORM);
        if (renderer == null) return;

        renderer.drawFrameIndex(mappingFrame, x, y, false, false);
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(HALF_WIDTH, HALF_HEIGHT, HALF_HEIGHT);
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // Standing state is managed via isPlayerRiding() check in update()
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return !isDestroyed();
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public boolean isPersistent() {
        // out_of_range uses objoff_32 (spawn X), not current X
        return !isDestroyed() && isOnScreenX(baseX, 320);
    }

    /**
     * Nudge: CalcSine(objoff_38) * $400 >> 16, added to workingY.
     * Creates a downward spring effect when player stands on the platform.
     */
    private void applyNudge() {
        int sineValue = calcSine(nudgeAngle);
        // muls.w d1,d0 / swap d0 = (sine * $400) >> 16
        int nudgeOffset = (sineValue * NUDGE_AMPLITUDE) >> 16;
        y = workingY + nudgeOffset;
    }

    /**
     * Dispatches to the correct movement handler based on moveType.
     */
    private void applyMovement(AbstractPlayableSprite player) {
        switch (moveType) {
            case 0x00 -> { /* Stationary */ }
            case 0x01 -> moveHorizontalSelfDriven(false);
            case 0x02 -> moveVerticalSelfDriven(false, false);
            case 0x03 -> moveFallOnStand();
            case 0x04 -> moveFalling(player);
            case 0x05 -> moveHorizontalSelfDriven(true);
            case 0x06 -> moveVerticalSelfDriven(true, false);
            case 0x07 -> moveSwitchActivated();
            case 0x08 -> moveRising();
            // 0x09 maps to type00 (stationary) in the jump table
            case 0x09 -> { /* Stationary */ }
            case 0x0A -> moveVerticalSelfDriven(false, true);
            case 0x0B -> moveVerticalGlobal(false);
            case 0x0C -> moveVerticalGlobal(true);
        }
    }

    /**
     * Types 01 and 05: Horizontal oscillation using self-driven oscillator (v_oscillate+$1A).
     * Type 01: d1 = obAngle - $40 (rightward start)
     * Type 05: d1 = -obAngle + $40 (leftward start)
     * Uses cached oscillator value (obAngle) for 1-frame delay, then stores current value.
     */
    private void moveHorizontalSelfDriven(boolean reversed) {
        int d1;
        if (reversed) {
            // .type05: neg.b d1 / addi.b #$40,d1
            d1 = (byte) (-cachedOscillator + 0x40);
        } else {
            // .type01: subi.b #$40,d1
            d1 = (byte) (cachedOscillator - 0x40);
        }
        x = baseX + d1;
        // .chgmotion: move.b (v_oscillate+$1A).w,objoff_26(a0)
        cachedOscillator = OscillationManager.getByte(OSC_SELF_DRIVEN);
    }

    /**
     * Types 02, 06, 0A: Vertical oscillation using self-driven oscillator (v_oscillate+$1A).
     * Type 02: d1 = obAngle - $40 (downward start)
     * Type 06: d1 = -obAngle + $40 (upward start)
     * Type 0A: same as 02 but with half amplitude (asr.w #1,d1)
     * Uses cached oscillator value (obAngle) for 1-frame delay, then stores current value.
     */
    private void moveVerticalSelfDriven(boolean reversed, boolean halfAmplitude) {
        int d1;
        if (reversed) {
            // .type06: neg.b d1 / addi.b #$40,d1
            d1 = (byte) (-cachedOscillator + 0x40);
        } else {
            // .type02: subi.b #$40,d1
            d1 = (byte) (cachedOscillator - 0x40);
        }
        if (halfAmplitude) {
            // .type0A: asr.w #1,d1
            d1 >>= 1;
        }
        workingY = baseY + d1;
        // .chgmotion: move.b (v_oscillate+$1A).w,objoff_26(a0)
        cachedOscillator = OscillationManager.getByte(OSC_SELF_DRIVEN);
    }

    /**
     * Types 0B and 0C: Vertical oscillation using global oscillator (v_oscillate+$E).
     * Type 0B: d1 = globalOsc - $30 (downward start)
     * Type 0C: d1 = -globalOsc + $30 (upward start)
     * These read directly from global oscillator (not cached), but still fall through
     * to .chgmotion which updates the cached oscillator from self-driven source.
     */
    private void moveVerticalGlobal(boolean reversed) {
        int motionVar = OscillationManager.getByte(OSC_GLOBAL);
        int d1;
        if (reversed) {
            // .type0C: neg.b d1 / addi.b #$30,d1
            d1 = (byte) (-motionVar + 0x30);
        } else {
            // .type0B: subi.b #$30,d1
            d1 = (byte) (motionVar - 0x30);
        }
        workingY = baseY + d1;
        // .chgmotion: move.b (v_oscillate+$1A).w,objoff_26(a0)
        // Types 0B/0C fall through .type02_move -> .chgmotion
        cachedOscillator = OscillationManager.getByte(OSC_SELF_DRIVEN);
    }

    /**
     * Type 03: Fall when stood on, with delay.
     * When player stands on platform, start 30-frame countdown.
     * When countdown expires, set 32-frame fall timer and transition to type 04.
     */
    private void moveFallOnStand() {
        if (timer > 0) {
            // .type03_wait: subq.w #1,objoff_3A / bne .type03_nomove
            timer--;
            if (timer == 0) {
                // Timer expired: start falling sequence
                timer = FALL_COUNTDOWN;
                moveType = 0x04; // addq.b #1,obSubtype(a0)
            }
            return;
        }

        // btst #3,obStatus(a0) - check if player is standing
        if (playerStanding) {
            timer = FALL_STAND_DELAY; // move.w #30,objoff_3A(a0)
        }
    }

    /**
     * Type 04: Falling platform with gravity.
     * Counts down timer, then detaches player and transitions to routine 8 (Plat_Action).
     * Uses 16.16 fixed-point for position (move.l objoff_2C / asl.l #8).
     * Deletes when below bottom boundary + $E0.
     */
    private void moveFalling(AbstractPlayableSprite player) {
        if (timer > 0) {
            timer--;
            if (timer == 0) {
                // Timer expired: detach player if standing
                if (playerStanding && player != null) {
                    // bset #1,obStatus(a1) - set player airborne
                    player.setAir(true);
                    // bclr #3,obStatus(a1) - clear player standing-on-object
                    // bclr #3,obStatus(a0) - clear object standing flag
                    var objectManager = levelManager.getObjectManager();
                    if (objectManager != null) {
                        objectManager.clearRidingObject(player);
                    }
                    // move.w obVelY(a0),obVelY(a1) - transfer platform velocity to player
                    player.setYSpeed((short) yVelocity);
                    playerStanding = false;
                }
                // move.b #8,obRoutine(a0) — transition to routine 8 (Plat_Action)
                // In routine 8, nudge angle is frozen (no increment/decrement).
                inFallingRoutine = true;
            }
        }

        // Apply gravity to 32-bit position (objoff_2C as 16.16 fixed point)
        // move.l objoff_2C(a0),d3
        int yPos32 = (workingY << 16) | (yFrac & 0xFFFF);
        // move.w obVelY(a0),d0 / ext.l d0 / asl.l #8,d0 / add.l d0,d3
        int vel32 = (int) (short) yVelocity;
        yPos32 += vel32 << 8;
        // move.l d3,objoff_2C(a0)
        workingY = yPos32 >> 16;
        yFrac = yPos32 & 0xFFFF;
        // addi.w #$38,obVelY(a0)
        yVelocity += FALL_GRAVITY;

        // Check delete: cmp.w objoff_2C(a0),d0 / bhs.s .locret
        int bottomBoundary = getBottomBoundary();
        if (workingY > bottomBoundary + FALL_DELETE_OFFSET) {
            // move.b #6,obRoutine(a0) — Plat_Delete.
            // Clear the active spawn as well so the platform doesn't recreate
            // immediately while still inside the placement window.
            destroyWithWindowGatedRespawn();
        }
    }

    /**
     * Type 07: Switch-activated rising.
     * High nybble of subtype indexes into f_switch table.
     * When switch is pressed, start 60-frame delay, then transition to type 08.
     */
    private void moveSwitchActivated() {
        if (timer > 0) {
            // .type07_wait: subq.w #1,objoff_3A / bne .type07_nomove
            timer--;
            if (timer == 0) {
                moveType = 0x08; // addq.b #1,obSubtype(a0)
            }
            return;
        }

        // Check switch state: lsr.w #4,d0 / tst.b (a2,d0.w)
        int switchIndex = (spawn.subtype() >> 4) & 0x0F;
        if (Sonic1SwitchManager.getInstance().isPressed(switchIndex)) {
            timer = SWITCH_DELAY;
        }
    }

    /**
     * Type 08: Rising after switch activation.
     * Rises at 2px/frame until it has moved $200 pixels above baseY.
     * Then transitions to type 00 (stationary).
     * Disasm uses bne (not-equal), so exact match is required.
     */
    private void moveRising() {
        // subq.w #2,objoff_2C(a0)
        workingY -= RISE_SPEED;

        // subi.w #$200,d0 / cmp.w objoff_2C(a0),d0 / bne.s .type08_nostop
        int targetY = baseY - RISE_DISTANCE;
        if (workingY == targetY) {
            moveType = 0x00; // clr.b obSubtype(a0) -> type 00 (stationary)
        }
    }

    /**
     * Object 18 uses DeleteObject once the faller passes v_limitbtm2+$E0.
     * Keep the spawn suppressed until it exits the placement window, matching
     * ROM-style "don't instantly respawn in place" behavior.
     */
    private void destroyWithWindowGatedRespawn() {
        if (!isDestroyed() && levelManager != null) {
            var objectManager = levelManager.getObjectManager();
            if (objectManager != null) {
                objectManager.removeFromActiveSpawns(spawn);
            }
        }
        setDestroyed(true);
    }

    /**
     * Get the level's bottom boundary (v_limitbtm2 equivalent).
     */
    private int getBottomBoundary() {
        var camera = Camera.getInstance();
        return camera != null ? camera.getMaxY() : 0x700;
    }

    /**
     * Check if the object is within out-of-range distance from camera using spawn X.
     * Matches the S1 out_of_range macro: round both positions to $80, compare distance
     * against 128+320+192 = 640.
     */
    private boolean isOnScreenX(int objectX, int range) {
        var camera = Camera.getInstance();
        if (camera == null) {
            return true;
        }
        int objRounded = objectX & 0xFF80;
        int camRounded = (camera.getX() - 128) & 0xFF80;
        int distance = (objRounded - camRounded) & 0xFFFF;
        // out_of_range: cmpi.w #128+320+192,d0 / bhi.s exit
        return distance <= (128 + 320 + 192);
    }

    private void refreshDynamicSpawn() {
        if (dynamicSpawn == null || dynamicSpawn.x() != x || dynamicSpawn.y() != y) {
            dynamicSpawn = buildSpawnAt(x, y);
        }
    }

    /**
     * Mega Drive CalcSine for angles 0 to $40 (0 to 90 degrees).
     * Returns 8.8 fixed-point value: 0 at angle 0, 256 ($100) at angle $40.
     */
    private static int calcSine(int angle) {
        if (angle <= 0) return 0;
        if (angle > NUDGE_MAX_ANGLE) angle = NUDGE_MAX_ANGLE;
        double radians = (angle * Math.PI) / (2.0 * NUDGE_MAX_ANGLE);
        return (int) (Math.sin(radians) * 256);
    }
}
