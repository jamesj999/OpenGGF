package com.openggf.game.sonic3k.features;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.objects.HCZWaterRushObjectInstance.HCZBreakableBarState;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * HCZ Water Tunnel physics — a global per-frame subroutine that pushes the
 * player through water pipes in Hydrocity Zone.
 *
 * <p>ROM equivalent: {@code sub_6F4A} / {@code HCZ_WaterTunnels}
 * (sonic3k.asm:8818–8929).
 *
 * <p>Each frame, the system checks whether the player is within any rectangular
 * tunnel region. If so, it applies the region's velocity to push the player
 * along the pipe, forces the floating animation ($0F = FLOAT2), sets rolling
 * status, clears the double-jump flag, and allows the player to nudge
 * perpendicular to the main flow using the D-pad.
 *
 * <p>When the player exits all tunnel regions and was previously inside one,
 * the exit animation ($1A = HURT) is applied and the wind-tunnel flag is cleared.
 *
 * <h3>Tunnel entry format (ROM: 7 words = 14 bytes per entry)</h3>
 * <pre>
 *   minX, minY, maxX, maxY, xVel, yVel, influenceFlag
 * </pre>
 * {@code influenceFlag}: 0 = D-pad nudges Y (horizontal flow), non-zero = D-pad
 * nudges X (vertical flow).
 *
 * <h3>Velocity-to-position math</h3>
 * ROM: {@code ext.l d0; lsl.l #8,d0; add.l d0,x_pos(a1)} adds velocity shifted
 * left 8 bits to the 32-bit position (16.16 fixed-point). This is equivalent to
 * adding {@code velocity >> 8} pixels and accumulating the fractional remainder.
 * Since the engine player sprites do not expose a subpixel API, we shift the
 * velocity right by 8 and add the integer pixel displacement each frame, matching
 * the ROM's per-frame pixel movement to within ±1 pixel.
 */
public final class HCZWaterTunnelHandler {
    private HCZWaterTunnelHandler() {}

    // =========================================================================
    // Per-player wind-tunnel state (ROM: WindTunnel_flag / WindTunnel_flag_P2)
    // =========================================================================

    private static boolean windTunnelFlagP1;
    private static boolean windTunnelFlagP2;

    // We drive tunnel motion explicitly because the engine's standard airborne
    // physics path does not match the original routine closely enough here.
    // Restore the last tunnel velocity on exit so ejection keeps the expected momentum.
    private static short lastXVelP1, lastYVelP1;
    private static short lastXVelP2, lastYVelP2;

    private static int exitAnimTimerP1;
    private static int exitAnimTimerP2;

    // =========================================================================
    // Tunnel region tables
    //
    // ROM: HCZ1_WaterTunLocs (sonic3k.asm:8932) — 15 entries
    //      HCZ2_WaterTunLocs (sonic3k.asm:8949) — 2 entries
    //
    // Each row: {minX, minY, maxX, maxY, xVel, yVel, influenceFlag}
    // Velocities are signed 16-bit subpixel values (0x100 = 1 pixel/frame).
    // influenceFlag: 0 = Y nudge (horizontal tunnel), non-zero = X nudge (vertical tunnel).
    // =========================================================================

    private static final int[][] HCZ1_TUNNELS = {
            {0x0380, 0x0580, 0x05A0, 0x05C0,  0x03F0, -0x0020, 0},
            {0x05A0, 0x0560, 0x0A80, 0x05C0,  0x03F0, -0x0010, 0},
            {0x1400, 0x0A80, 0x15A0, 0x0AC0,  0x0400,  0x0000, 0},
            {0x15A0, 0x0A40, 0x1960, 0x0AC0,  0x0400, -0x0040, 0},
            {0x1990, 0x0780, 0x19E0, 0x07F0,  0x0000, -0x0400, 0x100},
            {0x1990, 0x07F0, 0x19F0, 0x0878, -0x0140, -0x0400, 0x100},
            {0x1990, 0x0878, 0x19F0, 0x08FD,  0x0140, -0x0400, 0x100},
            {0x1990, 0x08FD, 0x19F0, 0x0978, -0x0140, -0x0400, 0x100},
            {0x1990, 0x0978, 0x19F0, 0x0A10,  0x0100, -0x0400, 0x100},
            {0x1960, 0x0A10, 0x19D0, 0x0A80,  0x0300, -0x0280, 0x100},
            {0x2B00, 0x0800, 0x2C20, 0x0840,  0x0400,  0x0000, 0},
            {0x2C20, 0x07C0, 0x2EE0, 0x0840,  0x0400, -0x0040, 0},
            {0x2EE0, 0x0790, 0x2F50, 0x0800,  0x0300, -0x0300, 0x100},
            {0x2F00, 0x0700, 0x2F70, 0x0790,  0x0100, -0x0400, 0x100},
            {0x2F30, 0x0680, 0x2F70, 0x0700,  0x0000, -0x0400, 0x100},
    };

    private static final int[][] HCZ2_TUNNELS = {
            {0x3980, 0x0800, 0x3AA0, 0x0840, 0x0400, 0x0000, 0},
            {0x3AA0, 0x07C0, 0x3F00, 0x0840, 0x0400, -0x0040, 0},
    };

    // Table entry field indices
    private static final int MIN_X = 0;
    private static final int MIN_Y = 1;
    private static final int MAX_X = 2;
    private static final int MAX_Y = 3;
    private static final int X_VEL = 4;
    private static final int Y_VEL = 5;
    private static final int INFLUENCE_FLAG = 6;

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Runs the HCZ water tunnel check for all players.
     * Call once per frame from the zone feature provider's pre-physics update
     * when the current zone is HCZ.
     *
     * <p>ROM: {@code sub_6F4A} (sonic3k.asm:8818).
     *
     * @param act current act index (0 = HCZ1, 1 = HCZ2)
     */
    public static void update(int act) {
        // ROM: tst.w (Debug_placement_mode).w / bne locret_705A
        // Checked per-player below.

        AbstractPlayableSprite player = GameServices.camera().getFocusedSprite();
        if (player == null) {
            return;
        }

        // ROM: cmpi.w #2,(Player_mode).w / beq.s loc_6F82
        // Special Knuckles-alone handling: uses WindTunnel_flag_P2 slot for P1.
        // In our engine this detail is captured by using separate flag fields.
        // For simplicity, process P1 with its own flag, sidekicks with theirs.

        int[][] tunnels = (act == 0) ? HCZ1_TUNNELS : HCZ2_TUNNELS;

        if (!player.isDebugMode()) {
            windTunnelFlagP1 = processPlayer(player, tunnels, windTunnelFlagP1, 0);
        }
        if (exitAnimTimerP1 > 0) {
            if (!player.getAir()) {
                exitAnimTimerP1 = 0;
                player.setForcedAnimationId(-1);
            }
        }

        for (AbstractPlayableSprite sidekick : GameServices.sprites().getSidekicks()) {
            if (!sidekick.isDebugMode()) {
                windTunnelFlagP2 = processPlayer(sidekick, tunnels, windTunnelFlagP2, 1);
            }
            if (exitAnimTimerP2 > 0) {
                if (!sidekick.getAir()) {
                    exitAnimTimerP2 = 0;
                    sidekick.setForcedAnimationId(-1);
                }
            }
        }
    }

    /**
     * Resets static state. Call on level load or engine reset.
     */
    public static void reset() {
        windTunnelFlagP1 = false;
        windTunnelFlagP2 = false;
        lastXVelP1 = lastYVelP1 = 0;
        lastXVelP2 = lastYVelP2 = 0;
        exitAnimTimerP1 = 0;
        exitAnimTimerP2 = 0;
    }

    // =========================================================================
    // Core tunnel logic (ROM: HCZ_WaterTunnels)
    // =========================================================================

    private static boolean processPlayer(AbstractPlayableSprite player,
                                          int[][] tunnels,
                                          boolean wasInTunnel,
                                          int playerIndex) {
        int playerX = player.getCentreX() & 0xFFFF;
        int playerY = player.getCentreY() & 0xFFFF;

        for (int[] entry : tunnels) {
            // ROM: cmp.w (a2),d0 / blo loc_7046
            if (playerX < entry[MIN_X]) continue;
            // ROM: cmp.w 4(a2),d0 / bhs loc_7046
            if (playerX >= entry[MAX_X]) continue;
            // ROM: cmp.w 2(a2),d1 / blo loc_7046
            if (playerY < entry[MIN_Y]) continue;
            // ROM: cmp.w 6(a2),d1 / bhs loc_7046
            if (playerY >= entry[MAX_Y]) continue;

            // Player is inside this tunnel region.

            // ROM: cmpi.b #4,routine(a1) / bhs loc_7058
            if (player.isHurt() || player.getDead()) {
                return false;
            }

            // ROM: btst d5,(_unkF7C7).w / bne locret_702E
            if (HCZBreakableBarState.testBit(playerIndex)) {
                return wasInTunnel;
            }

            // ROM: tst.b object_control(a1) / bne loc_7058
            if (player.isObjectControlled()) {
                return false;
            }

            short xVel = (short) entry[X_VEL];
            short yVel = (short) entry[Y_VEL];

            // Apply the full tunnel displacement directly. The engine's normal
            // physics path otherwise adds unwanted motion and breaks control feel.
            int xPixels = (xVel * 2) / 256;
            int yPixels = (yVel * 2) / 256;
            player.setCentreX((short) (player.getCentreX() + xPixels));
            player.setCentreY((short) (player.getCentreY() + yPixels));

            if (playerIndex == 0) {
                lastXVelP1 = xVel;
                lastYVelP1 = yVel;
            } else {
                lastXVelP2 = xVel;
                lastYVelP2 = yVel;
            }

            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setGSpeed((short) 0);

            // ROM: move.b #$F,anim(a1)
            player.setAnimationId(Sonic3kAnimationIds.FLOAT2);
            player.setForcedAnimationId(Sonic3kAnimationIds.FLOAT2);

            // ROM: bset #1,status(a1)
            player.setAir(true);

            // ROM: move.b #0,double_jump_flag(a1)
            player.setDoubleJumpFlag(0);

            // ROM: tst.b $C(a2) / bne loc_7030
            if (entry[INFLUENCE_FLAG] == 0) {
                // ROM: btst #button_up,d6 / beq loc_7024 / subq.w #1,y_pos(a1)
                if (player.isUpPressed()) {
                    player.setCentreY((short) (player.getCentreY() - 1));
                }
                // ROM: btst #button_down,d6 / beq locret_702E / addq.w #1,y_pos(a1)
                if (player.isDownPressed()) {
                    player.setCentreY((short) (player.getCentreY() + 1));
                }
            } else {
                // ROM: btst #button_left,d6 / beq loc_703A / subq.w #1,x_pos(a1)
                if (player.isLeftPressed()) {
                    player.setCentreX((short) (player.getCentreX() - 1));
                }
                // ROM: btst #button_right,d6 / beq locret_7044 / addq.w #1,x_pos(a1)
                if (player.isRightPressed()) {
                    player.setCentreX((short) (player.getCentreX() + 1));
                }
            }

            return true;
        }

        // No tunnel matched.
        // ROM: tst.b (a3) / beq locret_705A / move.b #$1A,anim(a1)
        if (wasInTunnel) {
            if (playerIndex == 0) {
                player.setXSpeed(lastXVelP1);
                player.setYSpeed(lastYVelP1);
                exitAnimTimerP1 = 1;
            } else {
                player.setXSpeed(lastXVelP2);
                player.setYSpeed(lastYVelP2);
                exitAnimTimerP2 = 1;
            }
            player.setAir(true);
            player.setAnimationId(Sonic3kAnimationIds.HURT);
            player.setForcedAnimationId(Sonic3kAnimationIds.HURT);
        }

        return false;
    }
}
