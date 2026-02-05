package uk.co.jamesj999.sonic.game.sonic2.objects;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.debug.DebugOverlayManager;
import uk.co.jamesj999.sonic.debug.DebugOverlayToggle;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.ButtonVineTriggerManager;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AudioConstants;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.SolidContact;
import uk.co.jamesj999.sonic.level.objects.SolidObjectListener;
import uk.co.jamesj999.sonic.level.objects.SolidObjectParams;
import uk.co.jamesj999.sonic.level.objects.SolidObjectProvider;
import uk.co.jamesj999.sonic.level.objects.ObjectSpriteSheet;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.level.render.SpriteMappingFrame;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

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

    // Spacing between log segments (16 pixels each)
    private static final int LOG_SPACING = 0x10;

    // Angle range: -0x40 to +0x40 (represents ~90 degree rotation)
    private static final int ANGLE_UP = -0x40;       // -64: Bridge is up (vertical)
    private static final int ANGLE_DOWN = 0x40;      // +64: Bridge is down (horizontal)
    private static final int ANGLE_STEP = 2;         // Rotation speed per frame

    // Collision parameters
    // When up (vertical): narrow collision
    private static final SolidObjectParams PARAMS_UP = new SolidObjectParams(8, 64, 65);
    // When down (horizontal): wide collision
    private static final SolidObjectParams PARAMS_DOWN = new SolidObjectParams(64, 8, 9);

    // Initial Y offset when spawned (bridge starts offset from spawn position)
    private static final int INITIAL_Y_OFFSET = -0x48;

    // Debug state
    private static final boolean DEBUG_VIEW_ENABLED = SonicConfigurationService.getInstance()
            .getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED);
    private static final DebugOverlayManager OVERLAY_MANAGER = GameServices.debugOverlay();

    // ROM-accurate 256-entry sine table (values from -256 to +256)
    private static final short[] SINE_TABLE = {
            0, 6, 12, 18, 25, 31, 37, 43, 49, 56, 62, 68, 74, 80, 86, 92,
            97, 103, 109, 115, 120, 126, 131, 136, 142, 147, 152, 157, 162, 167, 171, 176,
            181, 185, 189, 193, 197, 201, 205, 209, 212, 216, 219, 222, 225, 228, 231, 234,
            236, 238, 241, 243, 244, 246, 248, 249, 251, 252, 253, 254, 254, 255, 255, 255,
            256, 255, 255, 255, 254, 254, 253, 252, 251, 249, 248, 246, 244, 243, 241, 238,
            236, 234, 231, 228, 225, 222, 219, 216, 212, 209, 205, 201, 197, 193, 189, 185,
            181, 176, 171, 167, 162, 157, 152, 147, 142, 136, 131, 126, 120, 115, 109, 103,
            97, 92, 86, 80, 74, 68, 62, 56, 49, 43, 37, 31, 25, 18, 12, 6,
            0, -6, -12, -18, -25, -31, -37, -43, -49, -56, -62, -68, -74, -80, -86, -92,
            -97, -103, -109, -115, -120, -126, -131, -136, -142, -147, -152, -157, -162, -167, -171, -176,
            -181, -185, -189, -193, -197, -201, -205, -209, -212, -216, -219, -222, -225, -228, -231, -234,
            -236, -238, -241, -243, -244, -246, -248, -249, -251, -252, -253, -254, -254, -255, -255, -255,
            -256, -255, -255, -255, -254, -254, -253, -252, -251, -249, -248, -246, -244, -243, -241, -238,
            -236, -234, -231, -228, -225, -222, -219, -216, -212, -209, -205, -201, -197, -193, -189, -185,
            -181, -176, -171, -167, -162, -157, -152, -147, -142, -136, -131, -126, -120, -115, -109, -103,
            -97, -92, -86, -80, -74, -68, -62, -56, -49, -43, -37, -31, -25, -18, -12, -6,
            0, 6, 12, 18, 25, 31, 37, 43, 49, 56, 62, 68, 74, 80, 86, 92,
            97, 103, 109, 115, 120, 126, 131, 136, 142, 147, 152, 157, 162, 167, 171, 176,
            181, 185, 189, 193, 197, 201, 205, 209, 212, 216, 219, 222, 225, 228, 231, 234,
            236, 238, 241, 243, 244, 246, 248, 249, 251, 252, 253, 254, 254, 255, 255, 255
    };

    // State variables
    private final int switchId;           // ButtonVine trigger ID (0-15)
    private final int originalX;          // Original spawn X position
    private final int originalY;          // Original spawn Y position
    private final int direction;          // Movement direction: -0x100 (left) or +0x100 (right)
    private final boolean yFlipped;       // Y-flip status flag

    private int angle;                    // Current rotation angle (-0x40 to +0x40)
    private boolean isMoving;             // True when bridge is rotating
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

        // Store original position for reset
        this.originalX = spawn.x();
        this.originalY = spawn.y();

        // Check flip flags from render_flags
        // ROM: btst #0,render_flags(a0) for X-flip, btst #1 for Y-flip
        boolean xFlip = (spawn.renderFlags() & 0x01) != 0;
        this.yFlipped = (spawn.renderFlags() & 0x02) != 0;

        // Set direction based on X-flip
        // ROM: move.w #$100,d1 / btst #0,status(a0) / beq.s + / neg.w d1
        this.direction = xFlip ? -0x100 : 0x100;

        // Set initial angle based on Y-flip
        // ROM: move.b #-$40,angle(a0) / btst #1,status(a0) / beq.s + / move.b #$40,angle(a0)
        this.angle = yFlipped ? ANGLE_DOWN : ANGLE_UP;

        // Initial collision position
        this.collisionX = originalX;
        this.collisionY = originalY + INITIAL_Y_OFFSET;

        // State flags
        this.isMoving = false;
        this.bridgeDown = false;

        LOGGER.fine(() -> String.format(
                "MCZDrawbridge init: pos=(%d,%d), switchId=%d, direction=%d, yFlipped=%b, angle=%d",
                originalX, originalY, switchId, direction, yFlipped, angle));

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
        // ROM: btst #0,(ButtonVine_Trigger,d0.w) / beq.s Obj81_Anim
        boolean triggered = ButtonVineTriggerManager.getTrigger(switchId);

        if (triggered && !isMoving && !bridgeDown) {
            // Start lowering the bridge
            isMoving = true;
            AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_DRAWBRIDGE_MOVE);
        }

        if (isMoving) {
            // Rotate the bridge
            // ROM: addi.b #$48,d1 / add.b d1,angle(a0)
            // The ROM uses larger steps, but we normalize to ANGLE_STEP for smoother animation
            int targetAngle = yFlipped ? ANGLE_UP : ANGLE_DOWN;

            if (angle < targetAngle) {
                angle += ANGLE_STEP;
                if (angle > targetAngle) {
                    angle = targetAngle;
                }
            } else if (angle > targetAngle) {
                angle -= ANGLE_STEP;
                if (angle < targetAngle) {
                    angle = targetAngle;
                }
            }

            // Check if rotation is complete
            if (angle == targetAngle) {
                isMoving = false;
                bridgeDown = true;
                AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_DRAWBRIDGE_DOWN);

                // Update collision position when bridge is down
                // ROM: move.w objoff_30(a0),x_pos(a0) / addi.w #$48,x_pos(a0)
                collisionX = originalX + (direction > 0 ? 0x48 : -0x48);
                collisionY = originalY;
            }
        }

        // Update segment positions based on current angle
        updateSegmentPositions();
    }

    /**
     * Update the positions of all 8 log segments based on the current rotation angle.
     * Uses sine/cosine to calculate positions along the rotating bridge.
     */
    private void updateSegmentPositions() {
        // Convert angle to 0-255 range for sine table lookup
        // ROM angle is -0x40 to +0x40, we need to map to 0-255
        int sineAngle = (angle + 0x40) & 0xFF;

        int sin = calcSine(sineAngle);
        int cos = calcCosine(sineAngle);

        // Position each log segment
        // The segments are arranged along the bridge, starting from the pivot point
        int baseX = originalX;
        int baseY = originalY + INITIAL_Y_OFFSET;

        // Determine the direction offset based on flip state
        int yDirection = yFlipped ? -1 : 1;

        for (int i = 0; i < NUM_LOG_SEGMENTS; i++) {
            // Calculate offset for this segment
            // Each segment is LOG_SPACING pixels apart along the bridge length
            int distance = (i + 1) * LOG_SPACING * yDirection;

            // Apply rotation using sine/cosine
            // X offset = distance * cos(angle) / 256
            // Y offset = distance * sin(angle) / 256
            int offsetX = (cos * distance) >> 8;
            int offsetY = (sin * distance) >> 8;

            logX[i] = baseX + offsetX;
            logY[i] = baseY + offsetY;
        }
    }

    /**
     * Calculate sine value for angle (0-255 maps to 0-360 degrees).
     */
    private int calcSine(int angle) {
        return SINE_TABLE[angle & 0xFF];
    }

    /**
     * Calculate cosine value for angle (0-255 maps to 0-360 degrees).
     */
    private int calcCosine(int angle) {
        return SINE_TABLE[(angle + 0x40) & 0xFF];
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

        // Render each log segment using frame index 1 (frame 0 is empty)
        boolean hFlip = (spawn.renderFlags() & 0x01) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x02) != 0;

        for (int i = 0; i < NUM_LOG_SEGMENTS; i++) {
            renderer.drawFrameIndex(1, logX[i], logY[i], hFlip, vFlip);
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
        int topHeight = params.topHeight();
        int bottomHeight = params.bottomHeight();

        int left = collisionX - halfWidth;
        int right = collisionX + halfWidth;
        int top = collisionY - topHeight;
        int bottom = collisionY + bottomHeight;

        appendLine(commands, left, top, right, top, 0.0f, 1.0f, 0.0f);
        appendLine(commands, right, top, right, bottom, 0.0f, 0.7f, 0.0f);
        appendLine(commands, right, bottom, left, bottom, 0.0f, 0.7f, 0.0f);
        appendLine(commands, left, bottom, left, top, 0.0f, 0.7f, 0.0f);
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
