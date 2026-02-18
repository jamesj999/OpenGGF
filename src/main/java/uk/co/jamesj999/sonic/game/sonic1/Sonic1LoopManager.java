package uk.co.jamesj999.sonic.game.sonic1;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.audio.GameSound;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.logging.Logger;

/**
 * Implements Sonic 1's loop/plane switching mechanism (Sonic_Loops).
 *
 * <p>ROM reference: {@code _incObj/01 Sonic.asm} lines 1536–1611.
 *
 * <p>In Sonic 1, there are no dedicated plane switcher objects. Instead, the FG layout
 * marks certain 256×256 blocks with a loop flag (bit 7). Each frame the engine checks
 * Sonic's position within the block + ground angle to toggle between "high plane" and
 * "low plane." On the low plane, collision uses the next block in sequence (block ID + 1)
 * which has alternate collision data.
 *
 * <p>Plane switching conditions within the 256×256 block:
 * <ul>
 *   <li>X &lt; 0x2C (44px): force high plane</li>
 *   <li>X &gt;= 0xE0 (224px): force low plane</li>
 *   <li>Otherwise: angle-based — angle 1–0x80 → low plane; angle &gt; 0x80 → high plane</li>
 * </ul>
 *
 * <p>Roll tunnel tiles force the player into rolling mode when grounded.
 */
public class Sonic1LoopManager {
    private static final Logger LOG = Logger.getLogger(Sonic1LoopManager.class.getName());

    /**
     * LoopTileNums table: per-zone { loop1, loop2, roll1, roll2 }.
     * Indexed by gameplay progression order (matching zone registry):
     * 0=GHZ, 1=MZ, 2=SYZ, 3=LZ, 4=SLZ, 5=SBZ, 6=FZ, 7=Ending.
     * 0x7F = disabled (no loop/roll in that slot).
     */
    private static final int[][] LOOP_TILE_NUMS = {
        // GHZ (gameplay 0)
        { Sonic1Constants.GHZ_LOOP1, Sonic1Constants.GHZ_LOOP2,
          Sonic1Constants.GHZ_ROLL1, Sonic1Constants.GHZ_ROLL2 },
        // MZ (gameplay 1)
        { Sonic1Constants.LOOP_DISABLED, Sonic1Constants.LOOP_DISABLED,
          Sonic1Constants.LOOP_DISABLED, Sonic1Constants.LOOP_DISABLED },
        // SYZ (gameplay 2)
        { Sonic1Constants.LOOP_DISABLED, Sonic1Constants.LOOP_DISABLED,
          Sonic1Constants.LOOP_DISABLED, Sonic1Constants.LOOP_DISABLED },
        // LZ (gameplay 3)
        { Sonic1Constants.LOOP_DISABLED, Sonic1Constants.LOOP_DISABLED,
          Sonic1Constants.LOOP_DISABLED, Sonic1Constants.LOOP_DISABLED },
        // SLZ (gameplay 4)
        { Sonic1Constants.SLZ_LOOP1, Sonic1Constants.SLZ_LOOP2,
          Sonic1Constants.SLZ_ROLL1, Sonic1Constants.SLZ_ROLL2 },
        // SBZ (gameplay 5)
        { Sonic1Constants.LOOP_DISABLED, Sonic1Constants.LOOP_DISABLED,
          Sonic1Constants.LOOP_DISABLED, Sonic1Constants.LOOP_DISABLED },
        // FZ (gameplay 6)
        { Sonic1Constants.LOOP_DISABLED, Sonic1Constants.LOOP_DISABLED,
          Sonic1Constants.LOOP_DISABLED, Sonic1Constants.LOOP_DISABLED },
        // Ending (gameplay 7)
        { Sonic1Constants.LOOP_DISABLED, Sonic1Constants.LOOP_DISABLED,
          Sonic1Constants.LOOP_DISABLED, Sonic1Constants.LOOP_DISABLED },
    };

    private int currentZone = -1;
    private int[] zoneLoopTiles;
    private boolean zoneHasLoops;

    /**
     * Initializes the loop manager for a zone/act.
     */
    public void initLevel(int zone, int act) {
        this.currentZone = zone;
        if (zone >= 0 && zone < LOOP_TILE_NUMS.length) {
            this.zoneLoopTiles = LOOP_TILE_NUMS[zone];
            // Zone has loops if any of the 4 loop/roll tile IDs are not disabled
            this.zoneHasLoops = zoneLoopTiles[0] != Sonic1Constants.LOOP_DISABLED
                             || zoneLoopTiles[1] != Sonic1Constants.LOOP_DISABLED
                             || zoneLoopTiles[2] != Sonic1Constants.LOOP_DISABLED
                             || zoneLoopTiles[3] != Sonic1Constants.LOOP_DISABLED;
        } else {
            this.zoneLoopTiles = null;
            this.zoneHasLoops = false;
        }
    }

    /**
     * Per-frame loop check. Must be called each frame for the player sprite.
     *
     * <p>ROM equivalent: Sonic_Loops subroutine.
     */
    public void update(AbstractPlayableSprite player) {
        if (!zoneHasLoops || zoneLoopTiles == null || player == null) {
            return;
        }

        Level level = LevelManager.getInstance().getCurrentLevel();
        if (!(level instanceof Sonic1Level s1Level)) {
            return;
        }

        // Get player centre position in pixels
        int px = player.getCentreX() & 0xFFFF; // unsigned
        int py = player.getCentreY() & 0xFFFF;

        // Compute map cell coordinates (256px blocks)
        int blockPixelSize = Sonic1Constants.BLOCK_WIDTH_PX;
        int mapX = px / blockPixelSize;
        int mapY = py / blockPixelSize;

        // Reconstruct the raw FG layout value (block ID with loop flag)
        int rawValue = s1Level.getRawFgValue(mapX, mapY);

        // Check roll tunnel tiles first (ROM checks these before loop tiles)
        boolean isRoll1 = (rawValue == zoneLoopTiles[2]);
        boolean isRoll2 = (rawValue == zoneLoopTiles[3]);
        if (isRoll1 || isRoll2) {
            applyRollTunnel(player);
            return;
        }

        // Not on a tunnel tile — clear tunnel mode so wall checks resume next frame
        player.setTunnelMode(false);

        // Check loop tile IDs
        boolean isLoop1 = (rawValue == zoneLoopTiles[0]);
        boolean isLoop2 = (rawValue == zoneLoopTiles[1]);

        if (isLoop1) {
            // Loop1: standard position/angle plane switching
            applyLoopPlaneSwitching(player, px, blockPixelSize);
        } else if (isLoop2) {
            // Loop2: if airborne, force high plane and return
            // ROM: .chkifinair — btst #1,obStatus(a0); beq .chkifleft
            if (player.getAir()) {
                player.setLoopLowPlane(false);
            } else {
                applyLoopPlaneSwitching(player, px, blockPixelSize);
            }
        } else {
            // NOT on any loop/roll tile in a loop zone:
            // ROM: bclr #6,obRender(a0) — force high plane every frame
            player.setLoopLowPlane(false);
        }
    }

    /**
     * Applies position/angle-based plane switching within a loop tile.
     *
     * <p>ROM: Sonic_Loops loc_12DF4 – loc_12E3E
     */
    private void applyLoopPlaneSwitching(AbstractPlayableSprite player, int px, int blockPixelSize) {
        // Position within 256x256 block
        int xInBlock = px % blockPixelSize;

        if (xInBlock < Sonic1Constants.LOOP_HIGH_PLANE_X_MAX) {
            // Left edge of block: force high plane
            player.setLoopLowPlane(false);
        } else if (xInBlock >= Sonic1Constants.LOOP_LOW_PLANE_X_MIN) {
            // Right edge of block: force low plane
            player.setLoopLowPlane(true);
        } else {
            // Middle of block: angle-based switching
            // ROM: angle 1-0x80 → low plane (going up/right side of loop)
            //      angle > 0x80 → high plane (going down/left side)
            int angle = player.getAngle() & 0xFF;
            if (angle != 0) {
                if (angle <= 0x80) {
                    // Angle 1-128: low plane (behind the loop)
                    player.setLoopLowPlane(true);
                } else {
                    // Angle 129-255: high plane (in front of the loop)
                    player.setLoopLowPlane(false);
                }
            }
            // angle == 0: no change (flat ground, keep current plane)
        }
    }

    /**
     * Forces rolling mode when on a roll tunnel tile.
     *
     * <p>ROM: Sonic_Loops branches to Sonic_ChkRoll (01 Sonic.asm ~line 910).
     * Sets roll flag, reduces hitbox (19→14 y-radius), adjusts Y to keep feet
     * planted, plays roll SFX, and gives minimum speed ($200) if stationary.
     */
    private void applyRollTunnel(AbstractPlayableSprite player) {
        // Only force rolling when grounded and not already rolling
        // ROM: btst #2,obStatus(a0); beq .roll; rts
        if (!player.getAir() && !player.getRolling()) {
            player.setRolling(true);
            // ROM: addq.w #5,obY(a0) — adjust Y for smaller hitbox (5px in center coords).
            // Engine uses top-left coords so we use the full height adjustment.
            player.setY((short) (player.getY() + player.getRollHeightAdjustment()));
            AudioManager.getInstance().playSfx(GameSound.ROLLING);
            // ROM: tst.w obInertia(a0); bne .ismoving; move.w #$200,obInertia(a0)
            if (player.getGSpeed() == 0) {
                player.setGSpeed((short) 0x200);
            }
        }

        // Suppress the S2-derived ground wall check while on tunnel tiles.
        // S1's ROM has no Obj01_CheckWallsOnGround equivalent during ground
        // movement — the push sensors falsely detect the narrow tunnel walls.
        player.setTunnelMode(true);
    }
}
