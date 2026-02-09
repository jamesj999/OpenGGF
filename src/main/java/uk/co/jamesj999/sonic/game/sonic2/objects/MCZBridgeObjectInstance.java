package uk.co.jamesj999.sonic.game.sonic2.objects;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.debug.DebugOverlayManager;
import uk.co.jamesj999.sonic.debug.DebugOverlayToggle;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.ButtonVineTriggerManager;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.audio.Sonic2Sfx;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.SolidContact;
import uk.co.jamesj999.sonic.level.objects.SolidObjectListener;
import uk.co.jamesj999.sonic.level.objects.SolidObjectParams;
import uk.co.jamesj999.sonic.level.objects.SolidObjectProvider;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0x77 - MCZ Bridge (Mystic Cave Zone horizontal gate).
 * <p>
 * A horizontal gate composed of 8 log segments. When triggered by a ButtonVine switch,
 * the gate toggles between open and closed states via a 5-frame animation.
 * Unlike the MCZ Drawbridge (0x81) which uses sine/cosine rotation, this object
 * uses pre-baked sprite mapping frames for its animation.
 * <p>
 * <b>Disassembly Reference:</b> s2.asm Obj77 code, mappings/sprite/obj77.asm
 * <p>
 * <b>Subtype encoding:</b>
 * <ul>
 *   <li>Full byte: Switch ID (0-15) - which ButtonVine_Trigger to monitor</li>
 * </ul>
 * <p>
 * <b>Collision:</b>
 * <ul>
 *   <li>Solid only when fully closed (mapping frame 0)</li>
 *   <li>Half-width: 0x4B (75), air half-height: 8, ground half-height: 9</li>
 *   <li>When not on frame 0, players standing on it are dropped</li>
 * </ul>
 */
public class MCZBridgeObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final Logger LOGGER = Logger.getLogger(MCZBridgeObjectInstance.class.getName());

    // Animation frame sequences
    // Close anim: from open to closed
    private static final int[] CLOSE_FRAMES = {4, 3, 2, 1, 0};
    // Open anim: from closed to open
    private static final int[] OPEN_FRAMES = {0, 1, 2, 3, 4};

    // Animation speed: advance every (ANIM_SPEED + 1) frames
    private static final int ANIM_SPEED = 3;

    // Collision parameters (only used when fully closed, frame 0)
    private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(0x4B, 8, 9);

    // Width for on-screen culling (128 pixels = 0x80)
    private static final int WIDTH_PIXELS = 0x80;

    // Debug state
    private static final boolean DEBUG_VIEW_ENABLED = SonicConfigurationService.getInstance()
            .getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED);
    private static final DebugOverlayManager OVERLAY_MANAGER = GameServices.debugOverlay();

    // State variables
    private final int switchId;         // ButtonVine trigger ID
    private int mappingFrame;           // Current display frame (0-4)
    private int animId;                 // 0 = close anim, 1 = open anim
    private int frameIndex;             // Index into current frame array
    private int animTimer;              // Counts up to ANIM_SPEED before advancing
    private boolean animating;          // True when animation is in progress
    private boolean triggerLatch;       // One-shot latch to detect trigger edge

    public MCZBridgeObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);

        // Extract switch ID from full subtype byte
        this.switchId = spawn.subtype() & 0xFF;

        // Initial state: closed, not animating
        this.mappingFrame = 0;
        this.animId = 0;       // Close anim
        this.frameIndex = 0;
        this.animTimer = 0;
        this.animating = false;
        this.triggerLatch = false;

        LOGGER.fine(() -> String.format(
                "MCZBridge init: pos=(%d,%d), switchId=%d",
                spawn.x(), spawn.y(), switchId));
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        // Check ButtonVine trigger (one-shot: only react on rising edge)
        boolean triggered = ButtonVineTriggerManager.getTrigger(switchId);
        if (triggered && !triggerLatch) {
            triggerLatch = true;

            // Toggle animation direction
            animId ^= 1;
            frameIndex = 0;
            animTimer = 0;
            animating = true;

            // Play door slam sound if on screen
            if (isOnScreen(WIDTH_PIXELS)) {
                AudioManager.getInstance().playSfx(Sonic2Sfx.DOOR_SLAM.id);
            }
        } else if (!triggered) {
            triggerLatch = false;
        }

        // Step animation
        if (animating) {
            animTimer++;
            if (animTimer > ANIM_SPEED) {
                animTimer = 0;
                frameIndex++;

                int[] currentFrames = (animId == 0) ? CLOSE_FRAMES : OPEN_FRAMES;
                if (frameIndex >= currentFrames.length) {
                    // Animation complete - loop back to start (ROM: $FE, 1 restart command)
                    frameIndex = 0;
                    animating = false;
                }
                mappingFrame = currentFrames[frameIndex];
            }
        }
    }

    // SolidObjectProvider implementation

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        // Only solid when fully closed (frame 0)
        // When not frame 0, returning false causes SolidContacts to auto-drop standing players
        return !isDestroyed() && mappingFrame == 0;
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

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.MCZ_BRIDGE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Render the current frame at object position
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    private void appendDebug(List<GLCommand> commands) {
        int x = getX();
        int y = getY();

        // Draw object center (yellow cross)
        appendLine(commands, x - 4, y, x + 4, y, 1.0f, 1.0f, 0.0f);
        appendLine(commands, x, y - 4, x, y + 4, 1.0f, 1.0f, 0.0f);

        // Draw solid collision bounds (green box) only when solid (frame 0)
        if (mappingFrame == 0) {
            int halfWidth = SOLID_PARAMS.halfWidth();
            int airHalfHeight = SOLID_PARAMS.airHalfHeight();
            int groundHalfHeight = SOLID_PARAMS.groundHalfHeight();

            int left = x - halfWidth;
            int right = x + halfWidth;
            int top = y - airHalfHeight;
            int bottom = y + groundHalfHeight;

            appendLine(commands, left, top, right, top, 0.0f, 1.0f, 0.0f);
            appendLine(commands, right, top, right, bottom, 0.0f, 0.7f, 0.0f);
            appendLine(commands, right, bottom, left, bottom, 0.0f, 0.7f, 0.0f);
            appendLine(commands, left, bottom, left, top, 0.0f, 0.7f, 0.0f);
        }
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
