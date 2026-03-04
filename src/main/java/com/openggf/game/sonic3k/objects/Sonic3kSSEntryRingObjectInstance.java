package com.openggf.game.sonic3k.objects;

import com.openggf.audio.AudioManager;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Sonic 3&K Special Stage Entry Ring (Obj_SSEntryRing, object ID 0x85).
 * <p>
 * The giant gold ring that warps the player into a Special Stage when touched.
 * Each ring has a subtype (0-31) used as a bit index into the 32-bit
 * {@code Collected_special_ring_array} bitfield. If the ring's bit is already
 * set, it is deleted immediately on spawn.
 * <p>
 * The ring plays a formation animation (sparkle effect) for the first 8
 * animation frames (~48 game frames at 6 frames per animation frame).
 * Collision is only enabled after the formation completes.
 * <p>
 * Collision box: +/-24 X, +/-40 Y from center.
 * ROM: {@code SSEntry_Range: dc.w -$18, $30, -$28, $50}
 * (x_offset=-24, x_range=48, y_offset=-40, y_range=80)
 * <p>
 * On touch:
 * <ul>
 *   <li>Play sfx_BigRing (0xB3)</li>
 *   <li>Mark the ring's bit as collected</li>
 *   <li>If all emeralds collected: award 50 rings</li>
 *   <li>Otherwise: would transition to special stage (deferred for now; awards 50 rings)</li>
 * </ul>
 * <p>
 * Reference: docs/skdisasm/sonic3k.asm Obj_SSEntryRing
 */
public class Sonic3kSSEntryRingObjectInstance extends AbstractObjectInstance {
    private static final Logger LOGGER = Logger.getLogger(Sonic3kSSEntryRingObjectInstance.class.getName());

    // Collision extents from center (ROM: SSEntry_Range)
    private static final int COLLISION_HALF_WIDTH = 24;   // 0x18
    private static final int COLLISION_HALF_HEIGHT = 40;  // 0x28

    // ROM: render_flags = 4 (on-screen check), priority = $280 (bucket 5)
    private static final int RENDER_PRIORITY = 5;

    // Formation animation: collision disabled until anim frame >= 8
    // At 6 game frames per animation frame, that's 48 game frames
    private static final int FORMATION_ANIM_FRAMES = 8;
    private static final int FRAMES_PER_ANIM_FRAME = 6;
    private static final int FORMATION_DURATION = FORMATION_ANIM_FRAMES * FRAMES_PER_ANIM_FRAME;

    // Ring award when all emeralds already collected
    private static final int RING_REWARD = 50;

    /** Subtype is the bit index (0-31) into Collected_special_ring_array. */
    private final int bitIndex;

    /** Frame counter for formation animation. */
    private int formationTimer;

    /** Whether the formation animation is complete and collision is active. */
    private boolean formationComplete;

    /** Whether this ring has been collected during this session. */
    private boolean collected;

    /** Visual sparkle animation phase for rendering. */
    private int sparklePhase;

    public Sonic3kSSEntryRingObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSEntryRing");
        this.bitIndex = spawn.subtype();

        // ROM pre-check: if already collected, delete immediately
        GameStateManager gameState = GameServices.gameState();
        if (gameState.isSpecialRingCollected(bitIndex)) {
            setDestroyed(true);
            this.collected = true;
            return;
        }

        this.formationTimer = 0;
        this.formationComplete = false;
        this.collected = false;
        this.sparklePhase = 0;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (collected || isDestroyed()) {
            return;
        }

        // Advance formation animation
        if (!formationComplete) {
            formationTimer++;
            if (formationTimer >= FORMATION_DURATION) {
                formationComplete = true;
            }
            // Sparkle phase for visual effect
            sparklePhase = formationTimer / FRAMES_PER_ANIM_FRAME;
            return;
        }

        // Increment sparkle for idle animation
        sparklePhase++;

        // Check collision with player
        if (player != null) {
            checkCollision(player);
        }
    }

    /**
     * Checks collision between the player and this ring.
     * Uses center-to-center distance matching the ROM's SSEntry_Range box.
     * ROM: x_offset=-$18, x_range=$30, y_offset=-$28, y_range=$50
     */
    private void checkCollision(AbstractPlayableSprite player) {
        int playerCX = player.getCentreX();
        int playerCY = player.getCentreY();
        int ringX = spawn.x();
        int ringY = spawn.y();

        int dx = playerCX - ringX;
        int dy = playerCY - ringY;

        if (dx >= -COLLISION_HALF_WIDTH && dx <= COLLISION_HALF_WIDTH
                && dy >= -COLLISION_HALF_HEIGHT && dy <= COLLISION_HALF_HEIGHT) {
            onCollected(player);
        }
    }

    /**
     * Handle ring collection.
     * ROM: plays sfx_BigRing, marks bit in Collected_special_ring_array,
     * checks emerald count to decide between SS transition and ring award.
     */
    private void onCollected(AbstractPlayableSprite player) {
        collected = true;

        // Mark bit in collected array
        // ROM: bset d0,d1 / move.l d1,(Collected_special_ring_array).w
        GameStateManager gameState = GameServices.gameState();
        gameState.markSpecialRingCollected(bitIndex);

        // Play sfx_BigRing ($B3)
        AudioManager.getInstance().playSfx(Sonic3kSfx.BIG_RING.id);

        // ROM: if all emeralds collected, award 50 rings instead of SS transition
        // For now, always award 50 rings (SS transition not yet implemented)
        if (gameState.hasAllEmeralds()) {
            LOGGER.fine("SSEntryRing #" + bitIndex + " collected — all emeralds owned, awarding 50 rings");
        } else {
            LOGGER.fine("SSEntryRing #" + bitIndex + " collected — would enter Special Stage (not yet implemented), awarding 50 rings");
        }
        player.addRings(RING_REWARD);

        // Mark as destroyed so ObjectManager removes it
        setDestroyed(true);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (collected || isDestroyed()) {
            return;
        }

        int cx = spawn.x();
        int cy = spawn.y();

        // During formation: draw with increasing opacity/size
        if (!formationComplete) {
            float progress = (float) formationTimer / FORMATION_DURATION;
            appendFormingRing(commands, cx, cy, progress);
            return;
        }

        // Full ring: draw as a wireframe diamond/ring shape
        appendFullRing(commands, cx, cy);
    }

    /**
     * Draw the ring during its formation phase with increasing scale.
     */
    private void appendFormingRing(List<GLCommand> commands, int cx, int cy, float progress) {
        // Scale up from 0 to full size
        int halfW = (int) (COLLISION_HALF_WIDTH * progress);
        int halfH = (int) (COLLISION_HALF_HEIGHT * progress);
        if (halfW < 2) halfW = 2;
        if (halfH < 2) halfH = 2;

        // Gold color with increasing brightness
        float r = 1.0f;
        float g = 0.85f * progress;
        float b = 0.2f * progress;

        appendDiamondWire(commands, cx, cy, halfW, halfH, r, g, b);
    }

    /**
     * Draw the fully formed ring as a large wireframe diamond shape.
     * The sparkle phase creates a subtle pulsing effect.
     */
    private void appendFullRing(List<GLCommand> commands, int cx, int cy) {
        // Pulse effect via sparkle phase
        float pulse = 0.9f + 0.1f * (float) Math.sin(sparklePhase * 0.15);
        float r = 1.0f;
        float g = 0.85f * pulse;
        float b = 0.2f;

        // Outer diamond
        appendDiamondWire(commands, cx, cy, COLLISION_HALF_WIDTH, COLLISION_HALF_HEIGHT, r, g, b);
        // Inner diamond (slightly smaller for ring appearance)
        int innerW = COLLISION_HALF_WIDTH - 4;
        int innerH = COLLISION_HALF_HEIGHT - 6;
        appendDiamondWire(commands, cx, cy, innerW, innerH, r * 0.8f, g * 0.8f, b * 0.6f);
    }

    /**
     * Draws a diamond-shaped wireframe (4 line segments forming a diamond).
     */
    private void appendDiamondWire(List<GLCommand> commands, int cx, int cy,
            int halfW, int halfH, float r, float g, float b) {
        // Top to Right
        addLine(commands, cx, cy - halfH, cx + halfW, cy, r, g, b);
        // Right to Bottom
        addLine(commands, cx + halfW, cy, cx, cy + halfH, r, g, b);
        // Bottom to Left
        addLine(commands, cx, cy + halfH, cx - halfW, cy, r, g, b);
        // Left to Top
        addLine(commands, cx - halfW, cy, cx, cy - halfH, r, g, b);
    }

    private void addLine(List<GLCommand> commands, int x1, int y1, int x2, int y2,
            float r, float g, float b) {
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, x1, y1, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, x2, y2, 0, 0));
    }

    @Override
    public int getPriorityBucket() {
        // ROM: priority = $280, which maps to bucket 5
        return RenderPriority.clamp(RENDER_PRIORITY);
    }
}
