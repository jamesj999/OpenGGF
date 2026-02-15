package uk.co.jamesj999.sonic.game.sonic3k.scroll;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.sonic3k.objects.AizPlaneIntroInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static uk.co.jamesj999.sonic.level.scroll.M68KMath.VISIBLE_LINES;
import static uk.co.jamesj999.sonic.level.scroll.M68KMath.asrWord;
import static uk.co.jamesj999.sonic.level.scroll.M68KMath.negWord;
import static uk.co.jamesj999.sonic.level.scroll.M68KMath.packScrollWords;

public class SwScrlAizTest {

    private static final int INTRO_DEFORM_BANDS = 0x25;
    private static final int INTRO_DEFORM_CAP = 0x580;

    private SwScrlAiz handler;

    @Before
    public void setUp() {
        handler = new SwScrlAiz();
        Camera.getInstance().setLevelStarted(true);
        resetIntroScrollState();
    }

    @After
    public void tearDown() {
        Camera.getInstance().setLevelStarted(true);
        resetIntroScrollState();
    }

    @Test
    public void introModeMatchesApplyDeformationForCameraSource() {
        Camera.getInstance().setLevelStarted(false);
        int[] buffer = new int[VISIBLE_LINES];
        int cameraX = 0x400;
        int cameraY = 0x3C0;

        handler.update(buffer, cameraX, cameraY, 0, 0);

        int[] expected = buildExpectedIntroBuffer(cameraX, cameraY, cameraX);
        assertEquals((short) cameraY, handler.getVscrollFactorBG());
        assertPackedEquals(expected, buffer);
    }

    @Test
    public void introModeUsesEventsFg1WhileNegative() {
        activateIntroScrollState();
        int[] buffer = new int[VISIBLE_LINES];
        int cameraX = 0x200;
        int cameraY = 0x3C0;
        int source = AizPlaneIntroInstance.getIntroScrollOffset();
        assertTrue(source < 0);

        handler.update(buffer, cameraX, cameraY, 0, 0);

        int[] expected = buildExpectedIntroBuffer(cameraX, cameraY, source);
        assertEquals((short) cameraY, handler.getVscrollFactorBG());
        assertPackedEquals(expected, buffer);
    }

    @Test
    public void normalModeUsesHalfSpeedParallaxAndHalfVerticalCamera() {
        Camera.getInstance().setLevelStarted(true);
        int[] buffer = new int[VISIBLE_LINES];
        int cameraX = 0x200;
        int cameraY = 0x80;

        handler.update(buffer, cameraX, cameraY, 0, 0);

        short fgScroll = negWord(cameraX);
        short bgScroll = asrWord(fgScroll, 1);
        int expectedPacked = packScrollWords(fgScroll, bgScroll);
        int expectedOffset = bgScroll - fgScroll;

        assertEquals(asrWord(cameraY, 1), handler.getVscrollFactorBG());
        assertEquals(expectedOffset, handler.getMinScrollOffset());
        assertEquals(expectedOffset, handler.getMaxScrollOffset());
        for (int line = 0; line < VISIBLE_LINES; line++) {
            assertEquals(expectedPacked, buffer[line]);
        }
    }

    private int[] buildExpectedIntroBuffer(int cameraX, int cameraY, int source) {
        return buildExpectedIntroBufferDeform(cameraX, cameraY, source);
    }

    private int[] buildExpectedIntroBufferDeform(int cameraX, int cameraY, int source) {
        short fgScroll = negWord(cameraX);
        short[] bands = buildIntroBandValues(source);
        int[] buffer = new int[VISIBLE_LINES];

        int[] segments = new int[INTRO_DEFORM_BANDS];
        segments[0] = 0x3E0;
        for (int i = 1; i < segments.length; i++) {
            segments[i] = 4;
        }

        int remainingY = (short) cameraY;
        int line = 0;
        int segmentIndex = 0;
        int valueIndex = 0;

        while (segmentIndex < segments.length) {
            int next = remainingY - segments[segmentIndex];
            if (next >= 0) {
                remainingY = next;
                segmentIndex++;
                if (valueIndex < INTRO_DEFORM_BANDS - 1) {
                    valueIndex++;
                }
                continue;
            }

            line = fillSegment(buffer, line, -next, fgScroll, negWord(bands[valueIndex]));
            segmentIndex++;
            if (valueIndex < INTRO_DEFORM_BANDS - 1) {
                valueIndex++;
            }

            while (line < VISIBLE_LINES && segmentIndex < segments.length) {
                int count = Math.min(segments[segmentIndex], VISIBLE_LINES - line);
                line = fillSegment(buffer, line, count, fgScroll, negWord(bands[valueIndex]));
                segmentIndex++;
                if (valueIndex < INTRO_DEFORM_BANDS - 1) {
                    valueIndex++;
                }
            }
            break;
        }

        short fallback = negWord(bands[Math.min(valueIndex, INTRO_DEFORM_BANDS - 1)]);
        while (line < VISIBLE_LINES) {
            buffer[line++] = packScrollWords(fgScroll, fallback);
        }
        return buffer;
    }

    private int fillSegment(int[] buffer, int start, int count, short fgScroll, short bgScroll) {
        int end = Math.min(VISIBLE_LINES, start + Math.max(0, count));
        int packed = packScrollWords(fgScroll, bgScroll);
        for (int i = start; i < end; i++) {
            buffer[i] = packed;
        }
        return end;
    }

    private short[] buildIntroBandValues(int source) {
        short[] bands = new short[INTRO_DEFORM_BANDS];
        int d0 = (short) source;
        d0 >>= 1;

        if (d0 >= INTRO_DEFORM_CAP) {
            short value = (short) d0;
            for (int i = 0; i < INTRO_DEFORM_BANDS; i++) {
                bands[i] = value;
            }
            return bands;
        }

        bands[0] = (short) d0;
        int accum = (d0 - INTRO_DEFORM_CAP) << 16;
        int step = accum >> 5;
        for (int i = 1; i < INTRO_DEFORM_BANDS; i++) {
            accum += step;
            bands[i] = (short) ((accum >> 16) + INTRO_DEFORM_CAP);
        }
        return bands;
    }

    private void assertPackedEquals(int[] expected, int[] actual) {
        for (int i = 0; i < VISIBLE_LINES; i++) {
            assertEquals("Mismatch at scanline " + i, expected[i], actual[i]);
        }
    }

    private void activateIntroScrollState() {
        AizPlaneIntroInstance intro = new AizPlaneIntroInstance(
                new ObjectSpawn(0x60, 0x30, 0, 0, 0, false, 0));
        intro.update(0, null);
    }

    private void resetIntroScrollState() {
        AizPlaneIntroInstance intro = new AizPlaneIntroInstance(
                new ObjectSpawn(0x60, 0x30, 0, 0, 0, false, 0));
        intro.onUnload();
    }
}
