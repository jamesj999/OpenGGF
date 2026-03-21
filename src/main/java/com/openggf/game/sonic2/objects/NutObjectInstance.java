package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * MTZ Nut Object (Obj69) - Screw nut from Metropolis Zone.
 * <p>
 * A nut sits on a threaded column. When the player stands on it and walks
 * to one side, the nut rotates and moves vertically (like turning a screw).
 * Walking left screws the nut downward, walking right screws it upward
 * (or vice versa depending on the player's side).
 * <p>
 * The nut has a maximum travel distance determined by the subtype. If bit 7
 * of the subtype is set, the nut falls off the thread when it reaches the end.
 * <p>
 * <b>Subtype encoding:</b>
 * <ul>
 *   <li>Bits 0-6: Travel range factor. Range = (subtype &amp; 0x7F) * 8 pixels</li>
 *   <li>Bit 7: If set, nut falls off when reaching max travel (routine 4)</li>
 * </ul>
 * <p>
 * <b>Per-player state machine (3 modes):</b>
 * <ul>
 *   <li>Mode 0: Idle - waiting for player to stand on nut</li>
 *   <li>Mode 2: Aligning - centering player on nut (within 16px)</li>
 *   <li>Mode 4: Screwing - player pushes nut, accumulating displacement</li>
 * </ul>
 * <p>
 * <b>Disassembly Reference:</b> s2.asm Obj69, lines 53487-53656
 */
public class NutObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // Solid object collision dimensions (from disassembly lines 53532-53534)
    // d1 = $2B (half-width = 43), d2 = $C (y_radius = 12), d3 = $D (ground height = 13)
    private static final int HALF_WIDTH = 0x2B;
    private static final int Y_RADIUS = 0x0C;
    private static final int GROUND_HALF_HEIGHT = 0x0D;

    // Width in pixels (from line 53510: move.b #$20,width_pixels(a0))
    private static final int WIDTH_PIXELS = 0x20;

    // Alignment threshold (from line 53578: cmpi.w #$10,d0)
    private static final int ALIGN_THRESHOLD = 0x10;

    // Falling gravity (from line 53642: addi.w #$38,y_vel(a0))
    private static final int FALL_GRAVITY = 0x38;

    // Routine states
    private static final int ROUTINE_MAIN = 2;
    private static final int ROUTINE_FALLING = 4;
    private static final int ROUTINE_LANDED = 6;

    // Per-player action modes (from Obj69_Modes offset table)
    private static final int MODE_IDLE = 0;
    private static final int MODE_ALIGNING = 2;
    private static final int MODE_SCREWING = 4;

    // Position tracking
    private int x;                  // Current X (constant)
    private int y;                  // Current Y position
    private int baseY;              // objoff_32 - Original Y position
    private int accumulator;        // objoff_34 - Accumulated screw displacement
    private int maxTravel;          // objoff_36 - Max travel distance in pixels
    private int mappingFrame;       // Current animation frame (0-3)

    // Routine state
    private int routine;

    // Falling state (routine 4)
    private int yVel;               // Y velocity (word, in 1/256 pixel units)
    private int ySub;               // Sub-pixel Y accumulator (low 16 bits of 32-bit position)

    // Per-player action state (objoff_38 for P1, objoff_3C for P2)
    // Each stores: byte 0 = mode, byte 1 = direction (0=right push, 1=left push)
    private int p1Mode;
    private int p1Direction;

    // Player standing detection
    private int lastContactFrame = -2;
    private boolean playerStanding;

    // Subtype flags
    private boolean fallsOff;       // Bit 7 of subtype: nut falls off at max travel

    public NutObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        init();
    }

    private void init() {
        x = spawn.x();
        baseY = spawn.y();
        y = baseY;

        // subtype & 0x7F, then << 3 (from lines 53514-53517)
        int subtypeVal = spawn.subtype() & 0xFF;
        fallsOff = (subtypeVal & 0x80) != 0;
        maxTravel = (subtypeVal & 0x7F) << 3;

        accumulator = 0;
        mappingFrame = 0;
        routine = ROUTINE_MAIN;
        yVel = 0;
        ySub = 0;

        p1Mode = MODE_IDLE;
        p1Direction = 0;

        updateDynamicSpawn(x, y);
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
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(HALF_WIDTH, Y_RADIUS, GROUND_HALF_HEIGHT);
    }

    @Override
    public boolean isTopSolidOnly() {
        // Uses JmpTo12_SolidObject (full solid from all sides)
        return false;
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return !isDestroyed();
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (contact.standing() || contact.touchTop()) {
            lastContactFrame = frameCounter;
        }
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        playerStanding = (frameCounter - lastContactFrame) <= 1;

        switch (routine) {
            case ROUTINE_MAIN -> updateMain(player);
            case ROUTINE_FALLING -> updateFalling();
            case ROUTINE_LANDED -> {
                /* Stationary on ground - SolidObject collision handled by SolidObjectProvider interface */
            }
        }

        // Mask Y to 11 bits (from line 53531: andi.w #$7FF,y_pos(a0))
        y &= 0x7FF;

        updateDynamicSpawn(x, y);
    }

    /**
     * Routine 2: Main behavior - process player action.
     * ROM: Obj69_Main (lines 53519-53537)
     */
    private void updateMain(AbstractPlayableSprite player) {
        if (player != null) {
            processPlayerAction(player);
        }
    }

    /**
     * Process the per-player action state machine.
     * ROM: Obj69_Action (lines 53540-53637)
     * <p>
     * The ROM tracks standing via status bits (p1_standing_bit / p2_standing_bit).
     * If player is NOT standing, the mode resets to 0.
     */
    private void processPlayerAction(AbstractPlayableSprite player) {
        // ROM: btst d6,status(a0) / bne.s + / clr.b (a4)
        // If player is not standing on nut, reset mode to idle
        if (!playerStanding) {
            p1Mode = MODE_IDLE;
        }

        switch (p1Mode) {
            case MODE_IDLE -> actionIdle(player);
            case MODE_ALIGNING -> actionAligning(player);
            case MODE_SCREWING -> actionScrewing(player);
        }
    }

    /**
     * Mode 0: Wait for player to stand on the nut.
     * When standing detected, determine push direction and fall through to aligning mode.
     * ROM: loc_2792C (lines 53557-53568) - falls through to loc_2794C
     */
    private void actionIdle(AbstractPlayableSprite player) {
        if (!playerStanding) {
            return;
        }

        // Advance to aligning mode
        p1Mode = MODE_ALIGNING;

        // Determine direction: 0 = obj is right of player, 1 = obj is left of player
        // ROM: move.w x_pos(a0),d0 / sub.w x_pos(a1),d0
        // d0 = obj_x - player_x
        // bcc.s = if d0 >= 0 (obj right of player), direction stays 0
        // Otherwise (obj left of player), direction = 1
        int dx = x - player.getCentreX();
        p1Direction = (dx < 0) ? 1 : 0;

        // ROM falls through directly to aligning code (loc_2794C) - process immediately
        actionAligning(player);
    }

    /**
     * Mode 2: Align player to nut center.
     * Checks if player is within ALIGN_THRESHOLD of nut center. If not, snaps player.
     * ROM: loc_2794C (lines 53570-53583)
     */
    private void actionAligning(AbstractPlayableSprite player) {
        // ROM: move.w x_pos(a1),d0 / sub.w x_pos(a0),d0
        int dx = player.getCentreX() - x;

        // If direction == 1 (player to the left), add 0x0F to dx
        // ROM: tst.b 1(a4) / beq.s + / addi.w #$F,d0
        if (p1Direction != 0) {
            dx += 0x0F;
        }

        // Check if within threshold (unsigned comparison)
        // ROM: cmpi.w #$10,d0 / bhs.s + (rts) - if d0 >= 16 (unsigned), skip
        // Only snap and advance when player is within 16px of center
        if ((dx & 0xFFFF) < ALIGN_THRESHOLD) {
            // Aligned - snap player X to nut X and advance to screwing
            // ROM: move.w x_pos(a0),x_pos(a1) / addq.b #2,(a4)
            player.setCentreX((short) x);
            p1Mode = MODE_SCREWING;
        }
    }

    /**
     * Mode 4: Screw movement. Player displacement drives nut rotation and vertical movement.
     * ROM: loc_2796E (lines 53586-53637) and loc_279D4 (lines 53625-53637)
     * <p>
     * dx = player_x - nut_x. If player moves left (dx < 0), nut screws in one direction.
     * If right (dx >= 0), the other direction.
     * The delta is accumulated in objoff_34, which drives both Y position and rotation frame.
     */
    private void actionScrewing(AbstractPlayableSprite player) {
        int dx = player.getCentreX() - x;

        if (dx < 0) {
            // Player pushed left (ROM: loc_2796E, lines 53586-53622)
            // Accumulate negative displacement
            accumulator += dx;

            // Snap player X to nut center
            player.setCentreX((short) x);

            // Calculate vertical position from accumulator
            // ROM: asr.w #3,d0 / move.w d0,d1
            int shifted = accumulator >> 3;
            int d1 = shifted;

            // Animation frame: (shifted >> 1) & 3
            // ROM: asr.w #1,d0 / andi.w #3,d0
            mappingFrame = (shifted >> 1) & 3;

            // Y position: baseY - shifted (negated: neg.w d1 / add.w objoff_32,d1)
            y = baseY + (-d1);

            // Check if reached max travel
            // ROM: sub.w objoff_32(a0),d1 / ... / cmp.w d0,d1
            int travel = y - baseY;
            if (travel >= maxTravel && maxTravel > 0) {
                // Clamp to max travel
                y = baseY + maxTravel;

                // Reset accumulator: lsl.w #3,d0 / neg.w d0 / move.w d0,objoff_34
                accumulator = -(maxTravel << 3);
                mappingFrame = 0;

                // Check if nut should fall off (bit 7 of subtype)
                if (fallsOff) {
                    routine = ROUTINE_FALLING;
                } else {
                    // Reset player mode to idle
                    p1Mode = MODE_IDLE;
                }
            }
        } else {
            // Player pushed right (ROM: loc_279D4, lines 53625-53637)
            // Same accumulation but positive direction - no max travel check on this path
            accumulator += dx;

            // Snap player X to nut center
            player.setCentreX((short) x);

            // Calculate vertical position from accumulator
            int shifted = accumulator >> 3;

            // Animation frame
            mappingFrame = (shifted >> 1) & 3;

            // Y position: baseY - shifted
            y = baseY + (-shifted);
        }
    }

    /**
     * Routine 4: Falling after detaching from thread.
     * ROM: loc_279FC (lines 53640-53651)
     * <p>
     * Object falls with gravity, checks for floor collision.
     * When it lands, advances to routine 6 (landed/stationary).
     */
    private void updateFalling() {
        // ObjectMove: ROM applies velocity to 32-bit position (y_pos.w:y_sub.w)
        // vel is sign-extended to long, shifted left 8, then added to 32-bit pos
        // This is equivalent to: (y << 16 | ySub) += (yVel << 8)
        int pos32 = (y << 16) | (ySub & 0xFFFF);
        pos32 += (yVel << 8);
        y = (short) (pos32 >> 16);
        ySub = pos32 & 0xFFFF;

        // Add gravity: addi.w #$38,y_vel(a0)
        yVel += FALL_GRAVITY;

        // Check floor collision: jsrto JmpTo_ObjCheckFloorDist
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(x, y, 0x0B);

        // ROM: tst.w d1 / bpl.w + (skip if positive/zero = no floor)
        if (floor.distance() < 0) {
            // Landed: add.w d1,y_pos(a0)
            y += floor.distance();
            y &= 0x7FF;
            yVel = 0;
            ySub = 0;
            // Advance to landed routine (routine 4 + 2 = 6)
            routine = ROUTINE_LANDED;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic2ObjectArtKeys.MTZ_NUT);
        if (renderer != null) {
            // Clamp frame to 0-3
            int frame = mappingFrame & 3;
            renderer.drawFrameIndex(frame, x, y, false, false);
            return;
        }
        appendDebugRender(commands);
    }

    private void appendDebugRender(List<GLCommand> commands) {
        int left = x - HALF_WIDTH;
        int right = x + HALF_WIDTH;
        int top = y - Y_RADIUS;
        int bottom = y + GROUND_HALF_HEIGHT;

        float r = 0.8f, g = 0.6f, b = 0.2f;

        // Collision box
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, left, top, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, right, top, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, right, top, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, right, bottom, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, right, bottom, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, left, bottom, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, left, bottom, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, left, top, 0, 0));

        // Center cross
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, x - 4, y, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, x + 4, y, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, x, y - 4, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, x, y + 4, 0, 0));

        // Show max travel range
        if (maxTravel > 0) {
            int travelEnd = baseY + maxTravel;
            commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                    0.5f, 0.5f, 0.2f, x - 8, travelEnd, 0, 0));
            commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                    0.5f, 0.5f, 0.2f, x + 8, travelEnd, 0, 0));
        }
    }

    @Override
    public int getPriorityBucket() {
        // From disassembly: move.b #4,priority(a0)
        return RenderPriority.clamp(4);
    }
}
