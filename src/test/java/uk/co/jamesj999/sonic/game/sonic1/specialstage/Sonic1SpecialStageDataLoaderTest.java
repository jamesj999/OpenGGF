package uk.co.jamesj999.sonic.game.sonic1.specialstage;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.tests.rules.RequiresRom;
import uk.co.jamesj999.sonic.tests.rules.RequiresRomRule;
import uk.co.jamesj999.sonic.tests.rules.SonicGame;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants.SS_BLOCKBUFFER_OFFSET;
import static uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants.SS_BLOCKBUFFER_ROWS;
import static uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants.SS_LAYOUT_COLS;
import static uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants.SS_LAYOUT_STRIDE;
import static uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants.SS_STAGE_COUNT;

@RequiresRom(SonicGame.SONIC_1)
public class Sonic1SpecialStageDataLoaderTest {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private Sonic1SpecialStageDataLoader loader;

    @Before
    public void setUp() {
        Rom rom = romRule.rom();
        loader = new Sonic1SpecialStageDataLoader(rom);
    }

    @Test
    public void testStageLayoutsHaveValidBlockContent() throws IOException {
        for (int stageIndex = 0; stageIndex < SS_STAGE_COUNT; stageIndex++) {
            byte[] layout = loader.getStageLayout(stageIndex);
            assertNotNull("Layout should not be null for stage " + (stageIndex + 1), layout);
            assertTrue("Layout should not be empty for stage " + (stageIndex + 1), layout.length > 0);
            assertTrue("Layout should be stride-aligned for stage " + (stageIndex + 1),
                    layout.length % SS_LAYOUT_STRIDE == 0);

            int totalCells = SS_BLOCKBUFFER_ROWS * SS_LAYOUT_COLS;
            int validCount = 0;
            int nonZeroCount = 0;
            int wallCount = 0;
            int interactiveCount = 0;

            for (int row = 0; row < SS_BLOCKBUFFER_ROWS; row++) {
                int rowOff = SS_BLOCKBUFFER_OFFSET + row * SS_LAYOUT_STRIDE;
                for (int col = 0; col < SS_LAYOUT_COLS; col++) {
                    int blockId = layout[rowOff + col] & 0xFF;
                    if (blockId <= Sonic1SpecialStageBlockType.MAX_BLOCK_ID) {
                        validCount++;
                    }
                    if (blockId != 0) {
                        nonZeroCount++;
                    }
                    if (blockId >= 0x01 && blockId <= 0x24) {
                        wallCount++;
                    }
                    if (blockId >= 0x25 && blockId <= Sonic1SpecialStageBlockType.MAX_BLOCK_ID) {
                        interactiveCount++;
                    }
                }
            }

            double validRatio = validCount / (double) totalCells;
            double nonZeroRatio = nonZeroCount / (double) totalCells;

            assertTrue("Stage " + (stageIndex + 1) + " should have mostly valid block IDs, ratio=" + validRatio,
                    validRatio >= 0.75);
            assertTrue("Stage " + (stageIndex + 1) + " should have non-zero block content, ratio=" + nonZeroRatio,
                    nonZeroRatio >= 0.05);
            assertTrue("Stage " + (stageIndex + 1) + " should contain at least one wall block", wallCount > 0);
            assertTrue("Stage " + (stageIndex + 1) + " should contain interactive blocks", interactiveCount > 0);
        }
    }

    @Test
    public void testStageStartPositionsAreNearNonEmptyCells() throws IOException {
        for (int stageIndex = 0; stageIndex < SS_STAGE_COUNT; stageIndex++) {
            byte[] layout = loader.getStageLayout(stageIndex);
            int[] start = loader.getStartPosition(stageIndex);

            int gridCol = (start[0] + 0x20) / 0x18;
            int gridRow = (start[1] + 0x50) / 0x18;

            int nonZeroNearby = 0;
            int samples = 0;
            for (int dr = -3; dr <= 3; dr++) {
                for (int dc = -3; dc <= 3; dc++) {
                    int row = gridRow + dr;
                    int col = gridCol + dc;
                    if (row < 0 || col < 0 || col >= SS_LAYOUT_STRIDE) {
                        continue;
                    }
                    int idx = row * SS_LAYOUT_STRIDE + col;
                    if (idx < 0 || idx >= layout.length) {
                        continue;
                    }
                    samples++;
                    if ((layout[idx] & 0xFF) != 0) {
                        nonZeroNearby++;
                    }
                }
            }

            assertTrue("Stage " + (stageIndex + 1) + " start should be near non-empty layout cells",
                    samples > 0 && nonZeroNearby > 0);
        }
    }

    @Test
    public void testSpecialStageBackgroundPlanesAreBuiltFromSsBgLoadLogic() throws IOException {
        byte[] bgPlane5 = loader.getBgPlane5Tilemap();
        byte[] bgPlane6 = loader.getBgPlane6Tilemap();
        assertNotNull(bgPlane5);
        assertNotNull(bgPlane6);
        assertEquals("BG plane 5 should be 64x64 words", 64 * 64 * 2, bgPlane5.length);
        assertEquals("BG plane 6 should be 64x64 words", 64 * 64 * 2, bgPlane6.length);
        assertTrue("BG plane 5 should contain non-zero mappings", countNonZeroWords(bgPlane5) > 0);
        assertTrue("BG plane 6 should contain non-zero mappings", countNonZeroWords(bgPlane6) > 0);
        assertTrue("BG planes 5 and 6 should differ", !Arrays.equals(bgPlane5, bgPlane6));

        for (int plane = 1; plane <= 4; plane++) {
            byte[] fgPlane = loader.getFgPlaneTilemap(plane);
            assertNotNull("FG plane should load for plane " + plane, fgPlane);
            assertEquals("FG plane should be 64x64 words for plane " + plane, 64 * 64 * 2, fgPlane.length);
            assertTrue("FG plane should contain non-zero mappings for plane " + plane,
                    countNonZeroWords(fgPlane) > 0);
        }
    }

    private int countNonZeroWords(byte[] tilemap) {
        int nonZero = 0;
        for (int i = 0; i + 1 < tilemap.length; i += 2) {
            int word = ((tilemap[i] & 0xFF) << 8) | (tilemap[i + 1] & 0xFF);
            if (word != 0) {
                nonZero++;
            }
        }
        return nonZero;
    }
}
