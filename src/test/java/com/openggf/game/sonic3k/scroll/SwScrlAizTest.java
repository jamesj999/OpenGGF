package com.openggf.game.sonic3k.scroll;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.level.objects.ObjectSpawn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static com.openggf.level.scroll.M68KMath.VISIBLE_LINES;
import static com.openggf.level.scroll.M68KMath.asrWord;
import static com.openggf.level.scroll.M68KMath.negWord;
import static com.openggf.level.scroll.M68KMath.packScrollWords;
import static com.openggf.level.scroll.M68KMath.unpackBG;

public class SwScrlAizTest {

    private static final int INTRO_DEFORM_BANDS = 0x25;
    private static final int INTRO_DEFORM_CAP = 0x580;
    private static final int DEFORM_ORIGIN_X = 0x1300;
    private static final short[] AIZ_FINE_HAZE_FG_DEFORM = {
            0, 0, 1, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0
    };

    private SwScrlAiz handler;

    @Before
    public void setUp() {
        handler = new SwScrlAiz();
        Camera.getInstance().setLevelStarted(true);
        Sonic3kLevelEventManager.getInstance().resetState();
        resetIntroScrollState();
    }

    @After
    public void tearDown() {
        Camera.getInstance().setLevelStarted(true);
        Sonic3kLevelEventManager.getInstance().resetState();
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
    public void normalModeUsesPerBandParallaxAndHalfVerticalCamera() {
        Camera.getInstance().setLevelStarted(true);
        int[] buffer = new int[VISIBLE_LINES];
        int cameraX = 0x200;
        int cameraY = 0x80;

        handler.update(buffer, cameraX, cameraY, 0, 0);

        // vscrollFactorBG = cameraY / 2
        assertEquals(asrWord(cameraY, 1), handler.getVscrollFactorBG());

        // Compute expected band values from AIZ1_Deform formula
        short fgScroll = negWord(cameraX);
        int relative = (short) (cameraX - DEFORM_ORIGIN_X);
        long base = ((long) relative << 16) >> 5;

        // Tree bands (wave=0 on first call): d0 = base/2, loop adds wave+base
        long d0 = base >> 1;
        short[] treeBands = new short[6];
        for (int i = 5; i >= 0; i--) {
            // d3 (wave) = 0 on first frame
            treeBands[i] = (short) (d0 >> 16);
            d0 += base;
        }

        // bgY=64: band 0 (208px) → 144 visible lines, band 1 (32px), band 2 (48px)
        short bg0 = negWord(treeBands[0]);
        short bg1 = negWord(treeBands[1]);
        short bg2 = negWord(treeBands[2]);

        int expected0 = packScrollWords(fgScroll, bg0);
        int expected1 = packScrollWords(fgScroll, bg1);
        int expected2 = packScrollWords(fgScroll, bg2);

        // Band 0: lines 0-143
        assertEquals("Band 0 start", expected0, buffer[0]);
        assertEquals("Band 0 end", expected0, buffer[143]);
        // Band 1: lines 144-175
        assertEquals("Band 1 start", expected1, buffer[144]);
        assertEquals("Band 1 end", expected1, buffer[175]);
        // Band 2: lines 176-223
        assertEquals("Band 2 start", expected2, buffer[176]);
        assertEquals("Band 2 end", expected2, buffer[223]);

        // Different bands have different scroll speeds
        assertNotEquals("Bands 0 and 1 differ", buffer[143], buffer[144]);
        assertNotEquals("Bands 1 and 2 differ", buffer[175], buffer[176]);
    }

    @Test
    public void mountainBandsUseCorrectMultipliers() {
        Camera.getInstance().setLevelStarted(true);
        int[] buffer = new int[VISIBLE_LINES];
        // cameraX=0x1400: relative=256, base=256*2048=524288 (base>>16=8)
        // cameraY=640: bgY=320, which skips bands 0-2 fully and part of band 3
        int cameraX = 0x1400;
        int cameraY = 640;

        handler.update(buffer, cameraX, cameraY, 0, 0);

        short fgScroll = negWord(cameraX);
        int relative = (short) (cameraX - DEFORM_ORIGIN_X);
        long base = ((long) relative << 16) >> 5;

        // --- Band 7: per-line gradient (lines 64-76) ---
        // 13 distinct values: base*9/8 through base*21/8
        long inc = base >> 3;
        long d0 = base;
        for (int line = 64; line <= 76; line++) {
            d0 += inc;
            short expectedBg = negWord((short) (d0 >> 16));
            short actualBg = unpackBG(buffer[line]);
            assertEquals("Band 7 per-line at scanline " + line, expectedBg, actualBg);
        }

        // Verify band 7 lines are distinct (per-line gradient, not repeated)
        assertNotEquals("Band 7 lines differ",
                unpackBG(buffer[64]), unpackBG(buffer[65]));

        // --- Band 8 (lines 77-91): base*14 ---
        long d1 = base + base;
        long mountain = (d1 << 3) - d1; // base*14
        short bg8 = negWord((short) (mountain >> 16));
        for (int line = 77; line <= 91; line++) {
            assertEquals("Band 8 at line " + line, bg8, unpackBG(buffer[line]));
        }

        // --- Band 9 (lines 92-97): base*16 ---
        mountain += d1;
        short bg9 = negWord((short) (mountain >> 16));
        assertEquals("Band 9", bg9, unpackBG(buffer[92]));

        // --- Band 10 (lines 98-111): base*18 ---
        mountain += d1;
        short bg10 = negWord((short) (mountain >> 16));
        assertEquals("Band 10", bg10, unpackBG(buffer[98]));

        // --- Band 11 (lines 112-191): base*20 ---
        mountain += d1;
        short bg11 = negWord((short) (mountain >> 16));
        assertEquals("Band 11 start", bg11, unpackBG(buffer[112]));
        assertEquals("Band 11 end", bg11, unpackBG(buffer[191]));

        // --- Band 12 (lines 192-223): base*18 (same as band 10) ---
        assertEquals("Band 12 = base*18", bg10, unpackBG(buffer[192]));
        assertEquals("Band 12 end", bg10, unpackBG(buffer[223]));

        // Mountain bands are much faster than tree bands
        // base*14 >> 16 = 112, which is 14x the base speed of 8
        assertEquals("Band 8 speed = base*14", (short) -112, bg8);
        assertEquals("Band 11 speed = base*20", (short) -160, bg11);
    }

    @Test
    public void fireTransitionExposesPerColumnVScrollWave() {
        Sonic3kLevelEventManager eventsManager = Sonic3kLevelEventManager.getInstance();
        eventsManager.initLevel(0, 0);
        Sonic3kAIZEvents events = eventsManager.getAizEvents();
        assertNotNull(events);

        events.setEventsFg5(true);
        events.update(0, 0);
        assertTrue(events.isFireTransitionActive());

        int[] buffer = new int[VISIBLE_LINES];
        handler.update(buffer, 0x2F10, 0x200, 8, 0);

        short[] perColumn = handler.getPerColumnVScrollBG();
        assertNotNull(perColumn);
        assertEquals(20, perColumn.length);

        boolean hasVariation = false;
        short first = perColumn[0];
        for (int i = 1; i < perColumn.length; i++) {
            if (perColumn[i] != first) {
                hasVariation = true;
                break;
            }
        }
        assertTrue("Expected non-flat per-column VScroll during AIZ fire transition", hasVariation);
    }

    @Test
    public void fireTransitionUsesPlainDeformationInsteadOfAiz1ParallaxBands() {
        Sonic3kLevelEventManager eventsManager = Sonic3kLevelEventManager.getInstance();
        eventsManager.initLevel(0, 0);
        Sonic3kAIZEvents events = eventsManager.getAizEvents();
        assertNotNull(events);

        events.setEventsFg5(true);
        events.update(0, 0);
        assertTrue(events.isFireTransitionActive());

        int cameraX = 0x2F10;
        int[] buffer = new int[VISIBLE_LINES];
        handler.update(buffer, cameraX, 0x200, 8, 0);

        int expected = packScrollWords(negWord(cameraX), negWord(events.getFireTransitionBgX()));
        assertEquals(expected, buffer[0]);
        assertEquals(expected, buffer[VISIBLE_LINES - 1]);
    }

    @Test
    public void resumedAct2FireContinuationStillUsesPlainFireScrollMode() {
        Sonic3kLevelEventManager eventsManager = Sonic3kLevelEventManager.getInstance();
        eventsManager.initLevel(0, 0);
        Sonic3kAIZEvents act1Events = eventsManager.getAizEvents();
        assertNotNull(act1Events);

        act1Events.setEventsFg5(true);
        for (int i = 0; i < 320 && !act1Events.isAct2TransitionRequested(); i++) {
            act1Events.update(0, i);
        }

        eventsManager.initLevel(0, 1);
        Sonic3kAIZEvents act2Events = eventsManager.getAizEvents();
        assertNotNull(act2Events);
        assertTrue(act2Events.isFireTransitionScrollActive());

        int cameraX = 0x0010;
        int[] buffer = new int[VISIBLE_LINES];
        handler.update(buffer, cameraX, 0x180, 0, 1);

        int expected = packScrollWords(negWord(cameraX), negWord(act2Events.getFireTransitionBgX()));
        assertEquals(expected, buffer[0]);
        assertEquals(expected, buffer[VISIBLE_LINES - 1]);
    }

    @Test
    public void postBurnFineHazeUsesAiz2ForegroundDeltaTable() {
        Camera.getInstance().setLevelStarted(true);
        int[] buffer = new int[VISIBLE_LINES];
        int cameraX = 0x2E20;
        int cameraY = 0x180;
        int frameCounter = 19;

        handler.update(buffer, cameraX, cameraY, frameCounter, 0);

        // Fine haze is FG-only in this phase; no fire-transition column VScroll should be active.
        assertNull(handler.getPerColumnVScrollBG());

        int phase = ((frameCounter + (cameraY << 1)) & 0x3E) >> 1;
        short baseFg = negWord(cameraX);
        for (int line = 0; line < VISIBLE_LINES; line++) {
            short actualFg = (short) (buffer[line] >>> 16);
            short expectedFg = (short) (baseFg + AIZ_FINE_HAZE_FG_DEFORM[(phase + line) & 0x1F]);
            assertEquals("FG haze mismatch at scanline " + line, expectedFg, actualFg);
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
        // First update: routine 0 → init (resets introScrollOffset to 0, advances to routine 2)
        intro.update(0, null);
        // Second update: scrollVelocity runs before routine 2, sets introScrollOffset < 0
        intro.update(1, null);
    }

    private void resetIntroScrollState() {
        AizPlaneIntroInstance.resetIntroPhaseState();
    }
}
