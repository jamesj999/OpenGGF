package uk.co.jamesj999.sonic.game.sonic2.objects;

import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.debug.DebugOverlayManager;
import uk.co.jamesj999.sonic.debug.DebugOverlayToggle;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectManager;
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
 * Object 0x6A - MCZ Rotating Platforms / MTZ Moving Platforms.
 * <p>
 * In MCZ: Large wooden crates (64x64) that move when the player walks off of them.
 * Subtype 0x18 creates a 3-block formation. Platform cycles through movement phases
 * indefinitely once triggered.
 * <p>
 * In MTZ: Uses level art and different dimensions, moves when stepped off.
 * <p>
 * <b>Disassembly Reference:</b> s2.asm lines 53645-53850 (Obj6A code)
 * <p>
 * <b>Movement Tables:</b>
 * <ul>
 *   <li>byte_27CF4: Counter-clockwise (subtype without x_flip) - 5 phases</li>
 *   <li>byte_27D12: Clockwise (subtype with x_flip) - 5 phases</li>
 *   <li>byte_27CDC: MTZ variant - 4 phases</li>
 * </ul>
 * <p>
 * <b>Subtype 0x18:</b>
 * Creates 2 child platforms at (+64,+64) and (-64,+64) with subtypes 6 and C.
 * The parent doesn't render and only coordinates the children.
 * <p>
 * <b>Activation Logic:</b>
 * Platform waits for player to stand on it, then waits for player to walk off.
 * Once player leaves, platform begins cycling through movement phases.
 */
public class MCZRotPformsObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final Logger LOGGER = Logger.getLogger(MCZRotPformsObjectInstance.class.getName());

    // Debug state
    private static final boolean DEBUG_VIEW_ENABLED = SonicConfigurationService.getInstance()
            .getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED);
    private static final DebugOverlayManager OVERLAY_MANAGER = GameServices.debugOverlay();

    // MCZ movement tables from disassembly (lines 53834-53846)
    // Format: {x_vel, y_vel, duration} per phase
    // Values are 16-bit signed velocities in 16.8 fixed-point format
    // We maintain xFixed/yFixed in 16.8 format matching ROM behavior

    // byte_27CF4: Counter-clockwise (no x_flip) - 4 active phases (5th never reached due to wrap at 24)
    // ROM wraps at cmpi.b #6*4,objoff_38 (line 53821), so only phases 0-3 execute
    private static final int[][] MOVE_TABLE_CCW = {
            {0x0000, 0x0100, 0x40},      // Phase 0: Down for 64 frames
            {-0x0100, 0x0000, 0x80},     // Phase 1: Left for 128 frames
            {0x0000, -0x0100, 0x40},     // Phase 2: Up for 64 frames
            {0x0100, 0x0000, 0x80},      // Phase 3: Right for 128 frames
    };

    // byte_27D12: Clockwise (x_flip set) - 4 active phases
    private static final int[][] MOVE_TABLE_CW = {
            {0x0000, 0x0100, 0x40},      // Phase 0: Down for 64 frames
            {0x0100, 0x0000, 0x80},      // Phase 1: Right for 128 frames
            {0x0000, -0x0100, 0x40},     // Phase 2: Up for 64 frames
            {-0x0100, 0x0000, 0x80},     // Phase 3: Left for 128 frames
    };

    // MCZ collision parameters from disassembly (lines 53676-53677)
    // width_pixels = 0x20 (32 pixels half-width), y_radius = 0x20 (32 pixels)
    // Collision half-width = width_pixels + 0x0B = 0x20 + 0x0B = 0x2B (43 pixels)
    private static final int MCZ_WIDTH_PIXELS = 0x20;   // 32 pixels
    private static final int MCZ_Y_RADIUS = 0x20;       // 32 pixels
    private static final int MCZ_HALF_WIDTH = MCZ_WIDTH_PIXELS + 0x0B;  // 43 pixels

    // Phase wrap threshold from disassembly line 53821: cmpi.b #6*4,objoff_38
    // Each phase entry is 6 bytes (3 words), so 4 phases before reset = 24
    private static final int PHASE_WRAP_THRESHOLD = 24;  // 6*4 as per disassembly

    // Position state (16.8 fixed point for subpixel accuracy)
    private int x;                  // Current X position (integer)
    private int y;                  // Current Y position (integer)
    private int xFixed;             // X position in 16.8 fixed point
    private int yFixed;             // Y position in 16.8 fixed point
    private int xVel;               // X velocity (signed 16-bit)
    private int yVel;               // Y velocity (signed 16-bit)

    // Original spawn position (objoff_32, objoff_30 in disassembly)
    private int baseX;
    private int baseY;

    // Movement state
    private int phaseIndex;         // objoff_38: Current index into movement table
    private int phaseDuration;      // objoff_34: Frame counter for current phase
    private boolean activated;      // objoff_36: Movement started (0=idle, 1=moving)
    private int prevStandingFlags;  // objoff_3C: Previous frame's player standing status

    // Configuration
    private final int[][] moveTable;
    private final boolean xFlip;
    private final boolean yFlip;
    private final boolean isParent;  // Subtype 0x18 parent doesn't render

    // Dynamic spawn for moving position
    private ObjectSpawn dynamicSpawn;

    public MCZRotPformsObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);

        // Store flags
        this.xFlip = (spawn.renderFlags() & 0x01) != 0;
        this.yFlip = (spawn.renderFlags() & 0x02) != 0;

        // MCZ uses different movement tables based on x_flip (lines 53678-53681)
        // x_flip set = clockwise, x_flip clear = counter-clockwise
        this.moveTable = xFlip ? MOVE_TABLE_CW : MOVE_TABLE_CCW;

        // Check for parent multi-block (subtype 0x18)
        this.isParent = (spawn.subtype() == 0x18);

        // Initialize positions
        this.baseX = spawn.x();
        this.baseY = spawn.y();
        this.x = baseX;
        this.y = baseY;
        this.xFixed = x << 8;
        this.yFixed = y << 8;

        // Initialize movement state
        this.phaseIndex = spawn.subtype() & 0x0F;  // objoff_38 = subtype & 0x0F (0, 6, or C)
        this.phaseDuration = 0;
        this.activated = false;
        this.prevStandingFlags = 0;
        this.xVel = 0;
        this.yVel = 0;

        // Load initial phase parameters
        loadPhaseParameters();

        // Spawn child platforms for subtype 0x18
        if (isParent) {
            spawnChildren();
        }

        refreshDynamicSpawn();

        LOGGER.fine(() -> String.format(
                "MCZRotPforms init: pos=(%d,%d), subtype=0x%02X, xFlip=%b, isParent=%b",
                baseX, baseY, spawn.subtype(), xFlip, isParent));
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
    public SolidObjectParams getSolidParams() {
        // From disassembly lines 53771-53777:
        // d1 = width_pixels + 0x0B = half-width for collision
        // d2 = y_radius, d3 = y_radius + 1
        return new SolidObjectParams(MCZ_HALF_WIDTH, MCZ_Y_RADIUS, MCZ_Y_RADIUS + 1);
    }

    @Override
    public boolean isTopSolidOnly() {
        // MCZ crates use regular SolidObject (jsrto JmpTo13_SolidObject)
        // which is fully solid from all sides
        return false;
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        // Parent platforms (subtype 0x18) don't provide collision - children do
        return !isDestroyed() && !isParent;
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // Track standing for activation detection
        // This is handled in update() via status byte polling
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        // Parent platforms don't need movement updates - children handle themselves
        if (isParent) {
            return;
        }

        // Get current standing status from player
        // In the disassembly, this checks status bits p1_standing_bit (bit 0) and p2_standing_bit (bit 2)
        // We simplify by checking if this object is the player's standing object
        boolean playerStanding = isPlayerStandingOnUs(player);

        // Movement state machine (from disassembly lines 53729-53765)
        // The ROM uses objoff_36 as a routine selector at entry, but once moving,
        // the platform continues based on phase duration, not the activation flag.
        // We use phaseDuration > 0 to indicate "currently moving" state.
        if (phaseDuration > 0) {
            // Platform is currently executing a movement phase
            applyMovement();
        } else if (!activated) {
            // Idle state - waiting for player to step off
            // Track standing transitions: if player WAS standing but isn't now, activate
            if (!playerStanding && (prevStandingFlags != 0)) {
                // Player just walked off - activate movement
                activated = true;
                prevStandingFlags = 0;
                loadPhaseParameters();  // Start first movement phase
                LOGGER.fine(() -> String.format("MCZRotPforms activated at (%d,%d)", x, y));
            } else if (playerStanding) {
                // Player is standing - remember this
                prevStandingFlags = 1;
            }
        }

        prevStandingFlags = playerStanding ? 1 : 0;

        refreshDynamicSpawn();
    }

    /**
     * Apply movement based on current phase.
     * Disassembly lines 53761-53765, 53785-53807, 53810-53826
     */
    private void applyMovement() {
        // Apply velocity (16.8 fixed point)
        // Disassembly: jsr (ObjectMove).l - adds x_vel to x_pos, y_vel to y_pos
        xFixed += xVel;
        yFixed += yVel;
        x = xFixed >> 8;
        y = yFixed >> 8;

        // Decrement phase duration
        phaseDuration--;

        if (phaseDuration <= 0) {
            // Advance to next phase
            advancePhase();
        }
    }

    /**
     * Load parameters for current movement phase.
     * Disassembly lines 53810-53826 (loc_27CA2)
     * <p>
     * Note: The disassembly clears objoff_36 here (line 53819), but this doesn't
     * stop movement - the platform continues based on phase duration. The flag
     * is only checked at routine entry (line 53731) as a routine selector.
     */
    private void loadPhaseParameters() {
        // Convert phase index (byte offset) to array index
        // Phase index increments by 6 each phase (3 words * 2 bytes)
        int effectiveIndex = phaseIndex / 6;

        if (effectiveIndex >= 0 && effectiveIndex < moveTable.length) {
            int[] phase = moveTable[effectiveIndex];
            xVel = phase[0];
            yVel = phase[1];
            phaseDuration = phase[2];
        } else {
            xVel = 0;
            yVel = 0;
            phaseDuration = 0x40;  // Default
        }

        // Disassembly line 53819: move.b #0,objoff_36(a0)
        // This clears the activation flag, but movement continues because
        // phaseDuration is non-zero. The flag controls routine path selection,
        // not per-frame movement.
        activated = false;
    }

    /**
     * Advance to next movement phase.
     * Disassembly lines 53820-53824
     */
    private void advancePhase() {
        // Advance phase index by 6 (each table entry is 3 words = 6 bytes)
        // Disassembly line 53820: addq.b #6,objoff_38(a0)
        phaseIndex += 6;

        // Check for wrap: cmpi.b #6*4,objoff_38 (check if >= 24)
        // Disassembly line 53821: cmpi.b #6*4,objoff_38(a0)
        if (phaseIndex >= PHASE_WRAP_THRESHOLD) {
            phaseIndex = 0;
        }

        loadPhaseParameters();
    }

    /**
     * Check if player is standing on this platform.
     */
    private boolean isPlayerStandingOnUs(AbstractPlayableSprite player) {
        if (player == null) {
            return false;
        }

        // Check if player is riding this object
        // In the original, this is done via status bit flags
        // We check if we're the player's riding object
        ObjectManager manager = LevelManager.getInstance().getObjectManager();
        if (manager != null) {
            return manager.isAnyPlayerRiding(this);
        }

        return false;
    }

    /**
     * Spawn child platforms for subtype 0x18 multi-block formation.
     * Disassembly lines 53685-53705
     */
    private void spawnChildren() {
        ObjectManager manager = LevelManager.getInstance().getObjectManager();
        if (manager == null) {
            return;
        }

        // Child 1: +64, +64 from parent with subtype 6 (or C if parent x_flip)
        int child1Subtype = xFlip ? 0x0C : 0x06;
        ObjectSpawn child1Spawn = new ObjectSpawn(
                baseX + 0x40,
                baseY + 0x40,
                spawn.objectId(),
                child1Subtype,
                spawn.renderFlags(),
                spawn.respawnTracked(),
                spawn.rawYWord());
        MCZRotPformsObjectInstance child1 = new MCZRotPformsObjectInstance(child1Spawn, "MCZRotPforms");
        manager.addDynamicObject(child1);

        // Child 2: -64, +64 from parent with subtype C (or 6 if parent x_flip)
        int child2Subtype = xFlip ? 0x06 : 0x0C;
        ObjectSpawn child2Spawn = new ObjectSpawn(
                baseX - 0x40,
                baseY + 0x40,
                spawn.objectId(),
                child2Subtype,
                spawn.renderFlags(),
                spawn.respawnTracked(),
                spawn.rawYWord());
        MCZRotPformsObjectInstance child2 = new MCZRotPformsObjectInstance(child2Spawn, "MCZRotPforms");
        manager.addDynamicObject(child2);

        LOGGER.fine(() -> String.format(
                "MCZRotPforms spawned children at (%d,%d) and (%d,%d)",
                baseX + 0x40, baseY + 0x40, baseX - 0x40, baseY + 0x40));
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Parent platforms (subtype 0x18) don't render - children do
        if (isParent) {
            return;
        }

        // Debug overlay
        if (isDebugViewEnabled()) {
            appendDebug(commands);
        }

        // Try to render using loaded art
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        PatternSpriteRenderer renderer = null;

        if (renderManager != null) {
            renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.MCZ_CRATE);
        }

        if (renderer != null && renderer.isReady()) {
            // Frame 0 is the crate
            renderer.drawFrameIndex(0, x, y, xFlip, yFlip);
        }
    }

    private void refreshDynamicSpawn() {
        if (dynamicSpawn == null || dynamicSpawn.x() != x || dynamicSpawn.y() != y) {
            dynamicSpawn = new ObjectSpawn(
                    x,
                    y,
                    spawn.objectId(),
                    spawn.subtype(),
                    spawn.renderFlags(),
                    spawn.respawnTracked(),
                    spawn.rawYWord());
        }
    }

    private void appendDebug(List<GLCommand> commands) {
        // Draw collision box
        int left = x - MCZ_HALF_WIDTH;
        int right = x + MCZ_HALF_WIDTH;
        int top = y - MCZ_Y_RADIUS;
        int bottom = y + MCZ_Y_RADIUS + 1;

        // Green for solid platforms
        float r = 0.2f, g = 0.8f, b = 0.2f;
        if (activated) {
            // Yellow when moving
            r = 0.8f;
            g = 0.8f;
            b = 0.2f;
        }

        appendLine(commands, left, top, right, top, r, g, b);
        appendLine(commands, right, top, right, bottom, r, g, b);
        appendLine(commands, right, bottom, left, bottom, r, g, b);
        appendLine(commands, left, bottom, left, top, r, g, b);

        // Draw center cross (cyan)
        appendLine(commands, x - 4, y, x + 4, y, 0.0f, 1.0f, 1.0f);
        appendLine(commands, x, y - 4, x, y + 4, 0.0f, 1.0f, 1.0f);
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
