package com.openggf.game.sonic2.objects;
import com.openggf.level.objects.BoxObjectInstance;

import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Seesaw (Object 0x14) from Hill Top Zone.
 * <p>
 * A tilting platform that responds to player and ball position.
 * When the player lands on one end, the seesaw tilts and launches the ball.
 * When the ball lands, it launches any standing player.
 * <p>
 * Based on Sonic 2 disassembly s2.asm lines 46966-47313.
 * <p>
 * Subtype 0xFF: Seesaw without ball (ball not spawned).
 */
public class SeesawObjectInstance extends BoxObjectInstance
        implements SolidObjectProvider, SolidObjectListener, SlopedSolidProvider {

    /**
     * Slope data for tilted state (frame 0 or 2).
     * From s2.asm byte_21C8E (49 bytes).
     */
    private static final byte[] SLOPE_TILTED = {
            20, 20, 22, 24, 26, 28, 26, 24, 22, 20, 19, 18, 17, 16, 15, 14,
            13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, -1, -2,
            -3, -4, -5, -6, -7, -8, -9, -10, -11, -12, -13, -14, -14, -14, -14, -14,
            -14
    };

    /**
     * Slope data for flat state (frame 1).
     * From s2.asm byte_21CBF (48 bytes, all value 5).
     */
    private static final byte[] SLOPE_FLAT = {
            5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
            5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
            5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5
    };

    // Collision dimensions from ROM (Obj14_Init)
    // width_pixels = $30 (48)
    private static final int COLLISION_HALF_WIDTH = 0x30;
    private static final int COLLISION_HEIGHT = 8;

    // Priority from ROM: move.b #4,priority(a0)
    private static final int PRIORITY = 4;

    // Reference to spawned ball child
    private SeesawBallObjectInstance ball;
    private boolean ballSpawned = false;

    // Current angle state (objoff_3A in ROM)
    // 0 = tilted right (left side up), 1 = flat, 2 = tilted left (right side up)
    private int currentAngle;

    // Stored player Y velocity for ball launch power calculation (objoff_38 in ROM)
    private int storedPlayerYVel;

    // Current mapping frame
    private int mappingFrame;

    // Standing player tracking (p1_standing_bit / p2_standing_bit in ROM status)
    // ROM uses object-specific status bits to track which player is standing on THIS seesaw
    private AbstractPlayableSprite standingPlayer1;
    private AbstractPlayableSprite standingPlayer2;

    public SeesawObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name, COLLISION_HALF_WIDTH, COLLISION_HEIGHT, 1.0f, 0.85f, 0.1f, false);

        // Initialize angle based on flip status
        // ROM: btst #status.npc.x_flip,status(a0) / move.b #2,mapping_frame(a0)
        if (isFlippedHorizontal()) {
            currentAngle = 2;
            mappingFrame = 2;
        } else {
            currentAngle = 0;
            mappingFrame = 0;
        }
    }

    /**
     * Spawn ball on first update if not already spawned.
     * This deferred spawning ensures ObjectManager is available.
     */
    private void ensureBallSpawned() {
        if (ballSpawned) {
            return;
        }
        ballSpawned = true;

        // Spawn ball child only when subtype is 0 (no ball for ANY non-zero subtype)
        // ROM: tst.b subtype(a0) / bne.s loc_219A4 (if NOT zero, skip ball spawning)
        if (spawn.subtype() == 0) {
            spawnBall();
        }
    }

    private void spawnBall() {
        // ROM: addi.w #$28,x_pos(a0) / addi.w #$10,y_pos(a0)
        // Ball is spawned at seesaw position + offset
        int ballX = spawn.x() + 0x28;
        int ballY = spawn.y() + 0x10;

        // If flipped, ball starts on the other side
        // ROM: subi.w #$50,x_pos(a0) / move.b #2,objoff_3A(a0)
        if (isFlippedHorizontal()) {
            ballX = spawn.x() - 0x28;
        }

        ball = new SeesawBallObjectInstance(
                spawn.x(), // Seesaw center X for reference
                spawn.y() + 0x10, // Bottom of seesaw Y
                ballX,
                ballY,
                this,
                isFlippedHorizontal()
        );

        // Register the ball with ObjectManager
        ObjectManager objectManager = services().objectManager();
        if (objectManager != null) {
            objectManager.addDynamicObject(ball);
        }
    }

    /**
     * Called when player has solid contact with the seesaw.
     */
    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (player == null) {
            return;
        }

        // Determine if this is player 1 or player 2
        boolean isPlayer1 = isMainCharacter(player);

        if (!contact.standing()) {
            // Player left the seesaw - clear standing tracking
            // ROM: bclr #p1_standing_bit,status(a0) / bclr #p2_standing_bit,status(a0)
            if (isPlayer1) {
                standingPlayer1 = null;
            } else {
                standingPlayer2 = null;
            }
            return;
        }

        // Track this player as standing on the seesaw
        // ROM: bset #p1_standing_bit,status(a0) / bset #p2_standing_bit,status(a0)
        if (isPlayer1) {
            standingPlayer1 = player;
        } else {
            standingPlayer2 = player;
        }

        // Note: Y velocity storage is handled in update() when NEITHER player is standing,
        // capturing the approaching velocity BEFORE landing (ROM: loc_21A38).
        // We do NOT store Y velocity on contact since ROM only stores when neither is standing.

        // Calculate angles for standing players and average if both standing
        // ROM: loc_21A28 through loc_21A4C
        int targetAngle = calculateCombinedTargetAngle();

        // Update current angle toward target (gradual transition)
        updateAngle(targetAngle);
    }

    /**
     * Determines if the given player is the main character (player 1).
     */
    private boolean isMainCharacter(AbstractPlayableSprite player) {
        // If player is not any sidekick, they're the main character
        return !SpriteManager.getInstance().getSidekicks().contains(player);
    }

    /**
     * Calculates combined target angle when both players are standing.
     * ROM: loc_21A28 through loc_21A4C
     */
    private int calculateCombinedTargetAngle() {
        if (standingPlayer1 != null && standingPlayer2 != null) {
            // Both players standing - average their angles
            // ROM: move.b d2,d1 / add.b d0,d1 (combine angles)
            // ROM: cmpi.b #3,d1 / blo.s + / addq.b #1,d1 (if sum == 3, make it 4 before dividing)
            // ROM: lsr.b #1,d1 (divide by 2)
            int angle1 = calculateTargetAngle(standingPlayer1);
            int angle2 = calculateTargetAngle(standingPlayer2);
            int sum = angle1 + angle2;
            if (sum == 3) {
                sum = 4; // Special case: 1+2 or 2+1 = 3 -> 4 before division = 2
            }
            return sum / 2;
        } else if (standingPlayer1 != null) {
            return calculateTargetAngle(standingPlayer1);
        } else if (standingPlayer2 != null) {
            return calculateTargetAngle(standingPlayer2);
        }
        return 1; // Neither standing - return balanced
    }

    /**
     * Calculates target angle based on player position relative to seesaw center.
     * ROM: loc_21A12 through loc_21A72
     *
     * Bug fix #2 & #3: ROM calculates angle purely from (seesaw_x - player_x) with no
     * flip-dependent branching. The comparison uses >= 8 on the absolute difference,
     * creating a 15-pixel dead zone [-7, +7] where seesaw stays flat.
     */
    private int calculateTargetAngle(AbstractPlayableSprite player) {
        // ROM: move.w x_pos(a1),d0 / sub.w x_pos(a2),d0
        // d0 = seesaw_x - player_x
        int delta = spawn.x() - player.getCentreX();

        // ROM: cmpi.w #-8,d0 / bge.s + (if delta >= -8, check other side)
        // ROM: cmpi.w #8,d0 / blt.s + (if delta < 8, go flat)
        // This creates a dead zone when |delta| < 8
        if (Math.abs(delta) < 8) {
            return 1;  // Flat when within 8 pixels of center
        }

        // ROM: tst.w d0 / bmi.s + / moveq #2,d1 / rts (if delta > 0, return 2)
        // delta > 0 means seesaw_x > player_x, so player is LEFT of seesaw center
        return (delta >= 0) ? 2 : 0;
    }

    /**
     * Updates angle toward target with gradual transition.
     * ROM: Obj14_SetMapping
     *
     * CRITICAL: The ROM stores the TARGET angle in objoff_3A immediately,
     * even while mapping_frame is still transitioning. This allows the ball
     * to detect the angle change and launch as soon as the player lands,
     * not after the visual tilt completes.
     */
    private void updateAngle(int targetAngle) {
        // ROM: move.b d1,objoff_3A(a0) - store TARGET angle immediately
        // This is stored BEFORE the mapping_frame transition logic
        currentAngle = targetAngle;

        if (mappingFrame != targetAngle) {
            // ROM: Obj14_SetMapping - gradual visual transition
            // addq.b #2,d0 / subq.b #1,d0 or subq.b #1,d0
            if (mappingFrame < targetAngle) {
                mappingFrame += 2;
            }
            mappingFrame--;

            // Clamp to valid range 0-2
            if (mappingFrame < 0) mappingFrame = 0;
            if (mappingFrame > 2) mappingFrame = 2;
        }
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // Spawn ball on first update
        ensureBallSpawned();

        // ROM: SlopedPlatform_SingleCharacter runs every frame to validate standing players.
        // Checks in_air status and X bounds, clearing standing bit if either fails.
        // (s2.asm:35526-35545)
        validateStandingPlayers();

        // ROM: loc_21A38 - Store max Y velocity when NEITHER player is standing
        // This prepares for heavy landing detection before the player actually lands
        if (standingPlayer1 == null && standingPlayer2 == null) {
            // player parameter is always the main character
            int p1Vel = (player != null) ? player.getYSpeed() : 0;
            int p2Vel = 0;
            for (AbstractPlayableSprite sidekick : SpriteManager.getInstance().getSidekicks()) {
                p2Vel = Math.max(p2Vel, sidekick.getYSpeed());
            }

            // Bug fix #4: ROM always overwrites with max of both players.
            // Java incorrectly only updated if new value was higher.
            // ROM: cmp.w d0,d2 / blt.s + / move.w d2,d0 then move.w d0,objoff_38(a1)
            storedPlayerYVel = Math.max(p1Vel, p2Vel);
        }

        // ROM: Obj14_SetMapping is called every frame to animate visual transition
        // Bug fix: Without this, seesaw doesn't tilt after ball lands because
        // setCurrentAngle() only updates currentAngle, not mappingFrame
        updateAngle(currentAngle);
    }

    /**
     * Gets the current angle state for the ball to query.
     */
    public int getCurrentAngle() {
        return currentAngle;
    }

    /**
     * Gets the stored player Y velocity for ball launch power.
     */
    public int getStoredPlayerYVel() {
        return storedPlayerYVel;
    }

    /**
     * Gets the current mapping frame (visual state) for the ball to query.
     * ROM: mapping_frame(a1) is used for ball Y offset and player launch comparison.
     */
    public int getMappingFrame() {
        return mappingFrame;
    }

    /**
     * Sets only the current angle (target), not mapping frame.
     * ROM: move.b d1,objoff_3A(a0) only updates objoff_3A, not mapping_frame.
     * Called by ball when it lands - the visual transition happens via updateAngle().
     */
    public void setCurrentAngle(int angle) {
        this.currentAngle = angle;
    }

    /**
     * Sets both angle and mapping frame (for initialization only).
     */
    public void setAngle(int angle) {
        this.currentAngle = angle;
        this.mappingFrame = angle;
    }

    /**
     * Resets the stored player Y velocity (called after ball uses it).
     */
    public void resetStoredPlayerYVel() {
        this.storedPlayerYVel = 0;
    }

    /**
     * Gets player 1 if standing on this seesaw.
     * ROM: Uses p1_standing_bit in object status.
     */
    public AbstractPlayableSprite getStandingPlayer1() {
        return standingPlayer1;
    }

    /**
     * Gets player 2 if standing on this seesaw.
     * ROM: Uses p2_standing_bit in object status.
     */
    public AbstractPlayableSprite getStandingPlayer2() {
        return standingPlayer2;
    }

    /**
     * Clears player 1 standing tracking (called after player is launched).
     * ROM: bclr #p1_standing_bit,status(a1)
     */
    public void clearStandingPlayer1() {
        this.standingPlayer1 = null;
    }

    /**
     * Clears player 2 standing tracking (called after player is launched).
     * ROM: bclr #p2_standing_bit,status(a1)
     */
    public void clearStandingPlayer2() {
        this.standingPlayer2 = null;
    }

    private boolean isFlippedHorizontal() {
        return (spawn.renderFlags() & 0x1) != 0;
    }

    /**
     * ROM: SlopedPlatform_SingleCharacter checks each standing player every frame.
     * If player is in air or outside X bounds, the standing bit is cleared.
     * This is necessary because SolidContacts.update() only calls onSolidContact()
     * when there's an active collision — once the player moves away, the callback
     * never fires and standingPlayer refs become stale.
     */
    private void validateStandingPlayers() {
        if (standingPlayer1 != null) {
            if (standingPlayer1.getAir() || !isPlayerInXRange(standingPlayer1)) {
                standingPlayer1 = null;
            }
        }
        if (standingPlayer2 != null) {
            if (standingPlayer2.getAir() || !isPlayerInXRange(standingPlayer2)) {
                standingPlayer2 = null;
            }
        }
    }

    /**
     * ROM: SlopedPlatform checks if player X is within platform width.
     * sub.w x_pos(a0),d0 / add.w d1,d0 / bmi.s clear / cmp.w d2,d0 / blo.s stay
     */
    private boolean isPlayerInXRange(AbstractPlayableSprite player) {
        int relX = player.getCentreX() - spawn.x() + COLLISION_HALF_WIDTH;
        return relX >= 0 && relX < COLLISION_HALF_WIDTH * 2;
    }

    @Override
    public boolean isTopSolidOnly() {
        return true; // ROM's SlopedPlatform is top-solid-only (s2.asm:35747)
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(COLLISION_HALF_WIDTH, COLLISION_HEIGHT, COLLISION_HEIGHT);
    }

    @Override
    public byte[] getSlopeData() {
        // ROM: Obj14_UpdateMappingAndCollision selects slope data based on mapping_frame
        // Frame 0 or 2 (tilted) = SLOPE_TILTED, Frame 1 (flat) = SLOPE_FLAT
        return (mappingFrame == 1) ? SLOPE_FLAT : SLOPE_TILTED;
    }

    @Override
    public int getSlopeBaseline() {
        // ROM's SlopedPlatform overwrites d3 (height param) with the slope sample,
        // so the surface is at object_y - slopeSample. But the Java framework bakes
        // halfHeight into the landing snap via maxTop, so slopeBase must equal
        // halfHeight to compensate: baseY - halfHeight = anchorY - slopeSample.
        return COLLISION_HEIGHT;
    }

    @Override
    public boolean isSlopeFlipped() {
        // Slope is flipped when seesaw is tilted left (frame 2)
        // ROM: SlopedPlatform uses current render_flags.x_flip which is set from mapping_frame
        // Bug fix: Spawn flip only determines initial angle (frame 0 or 2), but after
        // initialization both render AND collision should be based purely on mappingFrame == 2
        return (mappingFrame == 2);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic2ObjectArtKeys.SEESAW);
        if (renderer == null) return;

        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;

        // ROM mapping frames: 0 = tilted right, 1 = flat, 2 = tilted left (uses frame 0 with x-flip)
        // Frames 0 and 2 use the same sprite data but frame 2 is x-flipped
        int frameToRender = (mappingFrame == 2) ? 0 : mappingFrame;

        // Bug fix #5: ROM always clears render x_flip then sets based solely on mapping_frame bit 1.
        // ROM: andi.b #~(1<<render_flags.x_flip),render_flags(a0) ; clear x_flip
        //      btst #1,mapping_frame(a0) / beq.s + ; check if frame is 2
        //      ori.b #1<<render_flags.x_flip,render_flags(a0) ; set x_flip for frame 2
        // Java incorrectly XORed with spawn x_flip.
        boolean useHFlip = (mappingFrame == 2);

        renderer.drawFrameIndex(frameToRender, spawn.x(), spawn.y(), useHFlip, vFlip);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }
}
