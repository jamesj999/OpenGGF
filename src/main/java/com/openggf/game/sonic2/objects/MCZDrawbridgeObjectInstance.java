package com.openggf.game.sonic2.objects;

import com.openggf.audio.AudioManager;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.DebugOverlayToggle;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.ButtonVineTriggerManager;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
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
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0x81 - MCZ Drawbridge (Mystic Cave Zone).
 * <p>
 * A long rotatable bridge that opens/closes when triggered by a ButtonVine switch.
 * The drawbridge consists of 8 stacked log segments that rotate as a unit.
 * <p>
 * <b>Disassembly Reference:</b> s2.asm lines 56420-56617 (Obj81 code)
 * <p>
 * <b>Subtype encoding:</b>
 * <ul>
 *   <li>Bits 0-3: Switch ID (0-15) - which ButtonVine_Trigger to monitor</li>
 * </ul>
 * <p>
 * <b>Status flags:</b>
 * <ul>
 *   <li>X-flip (bit 0): Initial direction (-0x100 vs +0x100)</li>
 *   <li>Y-flip (bit 1): Inverted vertical orientation</li>
 * </ul>
 * <p>
 * <b>Rotation mechanics (from disassembly):</b>
 * <ul>
 *   <li>Angle stored as signed byte, range -0x40 to +0x40 (or 0x00 to 0x80 when rotating)</li>
 *   <li>Direction value (objoff_34) is ±0x100, but only high byte added to angle</li>
 *   <li>Rotation completes when angle reaches 0x00 or 0x80 (signed: 0 or -128)</li>
 * </ul>
 * <p>
 * <b>Collision:</b>
 * <ul>
 *   <li>When up (vertical): width = 8 pixels</li>
 *   <li>When down (horizontal): width = 64 pixels</li>
 * </ul>
 */
public class MCZDrawbridgeObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final Logger LOGGER = Logger.getLogger(MCZDrawbridgeObjectInstance.class.getName());

    // Number of log segments in the drawbridge (child sprites)
    private static final int NUM_LOG_SEGMENTS = 8;

    // Angle completion targets
    // ROM: tst.b angle(a0) / beq.s loc_2A154 (angle == 0)
    // ROM: cmpi.b #$80,angle(a0) / bne.s loc_2A180 (angle == 0x80)
    private static final int ANGLE_COMPLETE_ZERO = 0x00;
    private static final int ANGLE_COMPLETE_180 = 0x80;  // -128 as signed byte

    // Collision parameters
    // When up (vertical): narrow collision (width_pixels=$08)
    // ROM: move.b #8,width_pixels(a0) at init
    private static final SolidObjectParams PARAMS_UP = new SolidObjectParams(8, 64, 65);
    // When down (horizontal): wide collision (width_pixels=$40)
    // ROM: move.b #$40,width_pixels(a0) when bridge is down
    private static final SolidObjectParams PARAMS_DOWN = new SolidObjectParams(64, 8, 9);

    // Initial Y offset when spawned (bridge starts offset from spawn position)
    // ROM: subi.w #$48,y_pos(a0) (line 56457)
    private static final int INITIAL_Y_OFFSET = -0x48;

    // X position offset when bridge is down
    // ROM: addi.w #$48,x_pos(a0) or subi.w #$48,x_pos(a0)
    private static final int DOWN_X_OFFSET = 0x48;

    // Debug state
    private static final boolean DEBUG_VIEW_ENABLED = SonicConfigurationService.getInstance()
            .getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED);
    private static final DebugOverlayManager OVERLAY_MANAGER = GameServices.debugOverlay();

    // State variables
    private final int switchId;           // ButtonVine trigger ID (0-15)
    private final int originalX;          // Original spawn X position (objoff_30)
    private final int originalY;          // Original spawn Y position (objoff_32)
    private final int direction;          // Movement direction: -0x100 (left) or +0x100 (right) (objoff_34)
    private final boolean xFlipped;       // X-flip status flag
    private final boolean yFlipped;       // Y-flip status flag

    // Angle as 16-bit word (only high byte used for rotation)
    // ROM: angle(a0) is a word but rotation logic treats it as byte
    private int angle;                    // Current rotation angle (signed byte range)
    private boolean isMoving;             // objoff_36: True when bridge is rotating
    private boolean bridgeDown;           // True when bridge has completed lowering

    // Current collision X position (can differ from original when down)
    private int collisionX;
    private int collisionY;

    // Positions of the 8 log segments (world coordinates)
    private final int[] logX = new int[NUM_LOG_SEGMENTS];
    private final int[] logY = new int[NUM_LOG_SEGMENTS];

    public MCZDrawbridgeObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);

        // Extract switch ID from subtype (bits 0-3)
        // ROM: move.b subtype(a0),d0 / andi.w #$F,d0
        this.switchId = spawn.subtype() & 0x0F;

        // Store original position for reset (objoff_30 and objoff_32)
        // ROM: move.w x_pos(a0),objoff_30(a0) / move.w y_pos(a0),objoff_32(a0)
        this.originalX = spawn.x();
        this.originalY = spawn.y();

        // Check flip flags from render_flags/status
        // ROM: btst #0,render_flags(a0) for X-flip, btst #1 for Y-flip
        this.xFlipped = (spawn.renderFlags() & 0x01) != 0;
        this.yFlipped = (spawn.renderFlags() & 0x02) != 0;

        // Set direction based on X-flip (objoff_34)
        // ROM (line 56471-56474): move.w #$100,d1 / btst #0,status(a0) / beq.s + / neg.w d1
        // ROM (line 56476): move.w d1,objoff_34(a0)
        this.direction = xFlipped ? -0x100 : 0x100;

        // Set initial angle based on Y-flip
        // ROM (line 56458-56463): move.b #-$40,angle(a0) / btst #1,status(a0) / beq.s +
        //                        / addi.w #$90,y_pos(a0) / move.b #$40,angle(a0)
        if (yFlipped) {
            this.angle = 0x40;  // +64
            // Y-flipped bridges start lower (add 0x90 to Y)
            this.collisionY = originalY + 0x90 + INITIAL_Y_OFFSET;
        } else {
            this.angle = -0x40;  // -64 (0xC0 as unsigned byte)
            this.collisionY = originalY + INITIAL_Y_OFFSET;
        }

        // Initial collision X position
        this.collisionX = originalX;

        // State flags
        this.isMoving = false;
        this.bridgeDown = false;

        LOGGER.fine(() -> String.format(
                "MCZDrawbridge init: pos=(%d,%d), switchId=%d, direction=%d, xFlip=%b, yFlip=%b, angle=%d",
                originalX, originalY, switchId, direction, xFlipped, yFlipped, angle));

        // Calculate initial segment positions
        updateSegmentPositions();
    }

    @Override
    public int getX() {
        return collisionX;
    }

    @Override
    public int getY() {
        return collisionY;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        // Check ButtonVine trigger
        // ROM (line 56498-56501): lea (ButtonVine_Trigger).w,a2 / moveq #0,d0
        //                         / move.b subtype(a0),d0 / btst #0,(a2,d0.w)
        boolean triggered = ButtonVineTriggerManager.getTrigger(switchId);

        if (triggered && !isMoving && !bridgeDown) {
            // Start lowering the bridge
            // ROM (line 56502-56504): tst.b objoff_36(a0) / bne.s + / move.b #1,objoff_36(a0)
            isMoving = true;
            AudioManager.getInstance().playSfx(Sonic2Sfx.DRAWBRIDGE_MOVE.id);

            // Special case: if X-flip is set, immediately adjust X position
            // ROM (line 56508-56511): cmpi.b #status.npc.no_balancing|status.npc.x_flip,status(a0)
            //                         / bne.s + / move.w objoff_30(a0),x_pos(a0) / subi.w #$48,x_pos(a0)
            if (xFlipped) {
                collisionX = originalX - DOWN_X_OFFSET;
            }
        }

        if (isMoving) {
            // ROM checks completion BEFORE incrementing angle:
            // ROM (line 56516-56520): tst.b angle(a0) / beq.s loc_2A154
            //                         / cmpi.b #$80,angle(a0) / bne.s loc_2A180
            int unsignedAngle = angle & 0xFF;
            if (unsignedAngle == ANGLE_COMPLETE_ZERO || unsignedAngle == ANGLE_COMPLETE_180) {
                // Bridge reached horizontal target
                isMoving = false;
                bridgeDown = true;
                AudioManager.getInstance().playSfx(Sonic2Sfx.DRAWBRIDGE_DOWN.id);

                // ROM: move.w objoff_32(a0),y_pos(a0) - restore Y to original
                collisionY = originalY;

                // ROM: move.w #$48,d1 / cmpi.b #$80,angle(a0) / bne.s + / neg.w d1
                //      move.w objoff_30(a0),x_pos(a0) / add.w d1,x_pos(a0)
                int xOffset = (unsignedAngle == ANGLE_COMPLETE_180) ? -DOWN_X_OFFSET : DOWN_X_OFFSET;
                if (!xFlipped) {
                    collisionX = originalX + xOffset;
                }
            } else {
                // Still rotating: increment angle
                // ROM (line 56534-56536): move.w objoff_34(a0),d0 / add.w d0,angle(a0)
                int angleStep = direction >> 8;  // High byte of direction (±1)
                angle = (byte)(angle + angleStep);
            }
        }

        // Update segment positions based on current angle
        updateSegmentPositions();
    }

    /**
     * Update the positions of all 8 log segments based on the current rotation angle.
     * Uses sine/cosine to calculate positions along the rotating bridge.
     * <p>
     * ROM Reference: s2.asm lines 56599-56638 (loc_2A1EA subroutine)
     * <p>
     * The ROM uses 16.16 fixed-point accumulation per segment:
     * <pre>
     *   CalcSine → d0=sin (±256), d1=cos (±256)
     *   swap d0/d1 → shift to high word (×65536)
     *   asr.l #4  → net step per segment = sin×4096 (fixed-point)
     *   Each iteration: accumulated >> 16 gives integer offset
     *   Result: segment i offset = (i+1) × sin / 16
     * </pre>
     * With sin=256 at 90°: 256/16 = 16px per segment (one LOG_SPACING).
     * The sign/direction is encoded in the sine/cosine via the angle value.
     * <p>
     * Base position is objoff_30/objoff_32 (original spawn position, NOT the
     * collision position which has a -$48 offset).
     */
    private void updateSegmentPositions() {
        int sineAngle = angle & 0xFF;

        int sin = calcSine(sineAngle);
        int cos = calcCosine(sineAngle);

        // ROM: move.w objoff_30(a0),d3 / move.w objoff_32(a0),d2
        // Base is the ORIGINAL spawn position (not the collision position)
        int baseX = originalX;
        int baseY = originalY;

        for (int i = 0; i < NUM_LOG_SEGMENTS; i++) {
            int step = i + 1;
            // ROM: accumulated = step × (sin << 12), integer = accumulated >> 16 = step × sin >> 4
            int offsetX = (cos * step) >> 4;
            int offsetY = (sin * step) >> 4;

            logX[i] = baseX + offsetX;
            logY[i] = baseY + offsetY;
        }
    }

    /**
     * Calculate sine value for angle (0-255 maps to 0-360 degrees).
     */
    private int calcSine(int angle) {
        return TrigLookupTable.sinHex(angle);
    }

    /**
     * Calculate cosine value for angle (0-255 maps to 0-360 degrees).
     */
    private int calcCosine(int angle) {
        return TrigLookupTable.cosHex(angle);
    }

    // SolidObjectProvider implementation

    @Override
    public SolidObjectParams getSolidParams() {
        // Return different collision params based on bridge state
        return bridgeDown ? PARAMS_DOWN : PARAMS_UP;
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;  // Bridge is only solid from top
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return !isDestroyed();
    }

    // SolidObjectListener implementation

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // No special handling needed
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Draw debug overlay
        if (isDebugViewEnabled()) {
            appendDebug(commands);
        }

        // Get renderer from art provider
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.MCZ_DRAWBRIDGE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Render each log segment using frame index 1 (frame 0 is empty/pivot)
        // ROM: sub-sprites use mapping frame 1 without flip - orientation is
        // handled by the sine/cosine position calculation, not by flipping tiles
        for (int i = 0; i < NUM_LOG_SEGMENTS; i++) {
            renderer.drawFrameIndex(1, logX[i], logY[i], false, false);
        }
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(5);  // Priority 5 from disassembly
    }

    private void appendDebug(List<GLCommand> commands) {
        // Draw pivot point (yellow)
        int pivotX = originalX;
        int pivotY = originalY + INITIAL_Y_OFFSET;
        if (yFlipped) {
            pivotY += 0x90;
        }
        appendLine(commands, pivotX - 4, pivotY, pivotX + 4, pivotY, 1.0f, 1.0f, 0.0f);
        appendLine(commands, pivotX, pivotY - 4, pivotX, pivotY + 4, 1.0f, 1.0f, 0.0f);

        // Draw log segment positions (cyan crosses)
        for (int i = 0; i < NUM_LOG_SEGMENTS; i++) {
            appendLine(commands, logX[i] - 4, logY[i], logX[i] + 4, logY[i], 0.0f, 1.0f, 1.0f);
            appendLine(commands, logX[i], logY[i] - 4, logX[i], logY[i] + 4, 0.0f, 1.0f, 1.0f);
        }

        // Draw collision bounds (green)
        SolidObjectParams params = getSolidParams();
        int halfWidth = params.halfWidth();
        int airHalfHeight = params.airHalfHeight();
        int groundHalfHeight = params.groundHalfHeight();

        int left = collisionX - halfWidth;
        int right = collisionX + halfWidth;
        int top = collisionY - airHalfHeight;
        int bottom = collisionY + groundHalfHeight;

        appendLine(commands, left, top, right, top, 0.0f, 1.0f, 0.0f);
        appendLine(commands, right, top, right, bottom, 0.0f, 0.7f, 0.0f);
        appendLine(commands, right, bottom, left, bottom, 0.0f, 0.7f, 0.0f);
        appendLine(commands, left, bottom, left, top, 0.0f, 0.7f, 0.0f);

        // Draw angle indicator
        String angleText = String.format("A:%02X", angle & 0xFF);
        // (Text rendering not available in GLCommand, but could add later)
    }

    private void appendLine(List<GLCommand> commands, int x1, int y1, int x2, int y2, float r, float g, float b) {
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x1, y1, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x2, y2, 0, 0));
    }

    private boolean isDebugViewEnabled() {
        return DEBUG_VIEW_ENABLED && OVERLAY_MANAGER.isEnabled(DebugOverlayToggle.OVERLAY);
    }
}
