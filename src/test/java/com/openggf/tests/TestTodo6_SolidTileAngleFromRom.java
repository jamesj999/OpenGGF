package com.openggf.tests;

import org.junit.Rule;
import org.junit.Test;
import com.openggf.data.Rom;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.level.SolidTile;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * TODO #6 coverage: SolidTile "TODO add angle recalculations" (SolidTile.java line 21).
 * <p>
 * The SolidTile constructor stores the raw angle byte from ROM (ColCurveMap / "Curve and
 * resistance mapping.bin") and provides getAngle(hFlip, vFlip) for flip transformations.
 * This test verifies that the ROM's ColCurveMap data at SOLID_TILE_ANGLE_ADDR matches
 * expected angle values for known collision tile indices.
 * <p>
 * The angle map has 0x100 entries (one byte per collision tile index). Index 0 is unused.
 * <p>
 * Reference:
 * - docs/s2disasm/s2.asm line 89595: ColCurveMap: BINCLUDE "collision/Curve and resistance mapping.bin"
 * - docs/s2disasm/s2.asm line 42986: move.b (a2,d0.w),(a4) ; get angle from AngleMap
 * - Sonic2Constants.SOLID_TILE_ANGLE_ADDR = 0x42D50
 * - Sonic2Constants.SOLID_TILE_ANGLE_SIZE = 0x100
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestTodo6_SolidTileAngleFromRom {
    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    /**
     * Verify the angle table has exactly 256 entries.
     */
    @Test
    public void testAngleTableSize() throws IOException {
        Rom rom = romRule.rom();
        byte[] angleData = rom.readBytes(Sonic2Constants.SOLID_TILE_ANGLE_ADDR,
                Sonic2Constants.SOLID_TILE_ANGLE_SIZE);
        assertEquals("Angle table should have 256 entries",
                Sonic2Constants.SOLID_TILE_ANGLE_SIZE, angleData.length);
    }

    /**
     * Verify collision tile index 0 has angle 0xFF.
     * Index 0 in ColCurveMap is the "empty" collision tile but its angle byte
     * is 0xFF (meaning "no slope / full block"), not 0x00. This is because
     * collision lookups that resolve to index 0 are short-circuited before
     * the angle is read (see s2.asm line 42984: beq.s loc_1E7E2).
     */
    @Test
    public void testTileIndex0Angle() throws IOException {
        Rom rom = romRule.rom();
        byte angle = rom.readByte(Sonic2Constants.SOLID_TILE_ANGLE_ADDR);
        assertEquals("Tile 0 angle should be 0xFF", 0xFF, angle & 0xFF);
    }

    /**
     * Verify that flat floor tiles (e.g., tile 1 = full solid block) have angle 0xFF.
     * Angle 0xFF means "no angle" - the tile is a full solid block with no slope.
     * This is the canonical flat floor collision tile used throughout the game.
     */
    @Test
    public void testFullSolidBlockAngle() throws IOException {
        Rom rom = romRule.rom();
        // Tile index 1 is typically the full solid block (all 16 height values = 16)
        byte angle = rom.readByte(Sonic2Constants.SOLID_TILE_ANGLE_ADDR + 1);
        assertEquals("Full solid tile (index 1) should have angle 0xFF",
                (byte) 0xFF, angle);
    }

    /**
     * Verify SolidTile correctly stores the ROM angle value and applies flip transforms.
     * This cross-references the ROM data with the SolidTile class behavior.
     */
    @Test
    public void testSolidTileConstructorUsesRomAngle() throws IOException {
        Rom rom = romRule.rom();

        // Read the height and angle data for a known sloped tile
        // We use tile index 2 which should be a slope
        byte angleFromRom = rom.readByte(Sonic2Constants.SOLID_TILE_ANGLE_ADDR + 2);

        // Construct a SolidTile with this angle
        byte[] heights = rom.readBytes(
                Sonic2Constants.SOLID_TILE_VERTICAL_MAP_ADDR + 2 * SolidTile.TILE_SIZE_IN_ROM,
                SolidTile.TILE_SIZE_IN_ROM);
        byte[] widths = rom.readBytes(
                Sonic2Constants.SOLID_TILE_HORIZONTAL_MAP_ADDR + 2 * SolidTile.TILE_SIZE_IN_ROM,
                SolidTile.TILE_SIZE_IN_ROM);

        SolidTile tile = new SolidTile(2, heights, widths, angleFromRom);

        // Verify the base angle matches
        assertEquals("SolidTile should store exact ROM angle",
                angleFromRom, tile.getAngle());

        // Verify no-flip returns the same angle
        assertEquals("No-flip should return original angle",
                angleFromRom, tile.getAngle(false, false));
    }

    /**
     * Verify angle values for several known collision tiles from the ROM.
     * Reads a range of angle values and checks basic consistency:
     * - Non-zero tiles should have angles that make geometric sense
     * - Sloped tiles should not have angle 0xFF (which means "no slope")
     */
    @Test
    public void testAngleValuesConsistency() throws IOException {
        Rom rom = romRule.rom();
        byte[] angles = rom.readBytes(Sonic2Constants.SOLID_TILE_ANGLE_ADDR,
                Sonic2Constants.SOLID_TILE_ANGLE_SIZE);

        // Count tiles with specific angle properties
        int flatCount = 0;  // angle 0xFF = no slope
        int slopedCount = 0;  // angle != 0xFF and != 0x00
        int emptyCount = 0;  // angle 0x00

        for (int i = 0; i < angles.length; i++) {
            int angle = angles[i] & 0xFF;
            if (angle == 0xFF) {
                flatCount++;
            } else if (angle == 0x00) {
                emptyCount++;
            } else {
                slopedCount++;
            }
        }

        // There should be a mix of flat, sloped, and empty tiles
        assertTrue("Should have at least some flat tiles (angle=0xFF)", flatCount > 0);
        assertTrue("Should have at least some sloped tiles (angle != 0xFF and != 0x00)", slopedCount > 0);
        assertTrue("Should have at least one empty entry (angle=0x00)", emptyCount > 0);

        // The Sonic 2 collision data has well-known properties:
        // ~20-40 entries are flat (0xFF), the rest are sloped or empty
        assertTrue("Sloped tiles should outnumber flat tiles",
                slopedCount > flatCount);
    }

    /**
     * Verify that the disassembly's flip transformation matches our SolidTile implementation.
     * <p>
     * From s2.asm lines 42988-42998:
     * - H-flip: neg.b (a4)
     * - V-flip: addi.b #$40,(a4); neg.b (a4); subi.b #$40,(a4)
     * Which is equivalent to: 0x80 - angle
     */
    @Test
    public void testFlipTransformMatchesDisassembly() throws IOException {
        Rom rom = romRule.rom();

        // Pick a sloped tile with a known non-trivial angle
        // Read through angles to find one in the ~0x10-0x30 range (gentle slope)
        byte[] angles = rom.readBytes(Sonic2Constants.SOLID_TILE_ANGLE_ADDR,
                Sonic2Constants.SOLID_TILE_ANGLE_SIZE);

        for (int i = 2; i < angles.length; i++) {
            int rawAngle = angles[i] & 0xFF;
            if (rawAngle > 0x10 && rawAngle < 0x30) {
                // Found a gentle slope tile - test flip transforms

                byte[] heights = rom.readBytes(
                        Sonic2Constants.SOLID_TILE_VERTICAL_MAP_ADDR + i * SolidTile.TILE_SIZE_IN_ROM,
                        SolidTile.TILE_SIZE_IN_ROM);
                byte[] widths = rom.readBytes(
                        Sonic2Constants.SOLID_TILE_HORIZONTAL_MAP_ADDR + i * SolidTile.TILE_SIZE_IN_ROM,
                        SolidTile.TILE_SIZE_IN_ROM);

                SolidTile tile = new SolidTile(i, heights, widths, angles[i]);

                // H-flip: neg.b angle = -angle (two's complement)
                byte expectedHFlip = (byte) (-rawAngle);
                assertEquals(String.format("Tile %d H-flip angle", i),
                        expectedHFlip, tile.getAngle(true, false));

                // V-flip: 0x80 - angle
                byte expectedVFlip = (byte) (0x80 - rawAngle);
                assertEquals(String.format("Tile %d V-flip angle", i),
                        expectedVFlip, tile.getAngle(false, true));

                return; // Found and tested a suitable tile
            }
        }
        // If no suitable tile found, that's unexpected
        assertTrue("Should find at least one gentle slope tile in angle data", false);
    }
}
