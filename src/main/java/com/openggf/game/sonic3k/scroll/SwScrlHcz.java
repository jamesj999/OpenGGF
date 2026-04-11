package com.openggf.game.sonic3k.scroll;

import com.openggf.level.scroll.AbstractZoneScrollHandler;

import java.util.Arrays;

import static com.openggf.level.scroll.M68KMath.VISIBLE_LINES;
import static com.openggf.level.scroll.M68KMath.asrWord;
import static com.openggf.level.scroll.M68KMath.negWord;
import static com.openggf.level.scroll.M68KMath.packScrollWords;

/**
 * Hydrocity Zone (HCZ) scroll handler for Sonic 3K.
 *
 * <p>Ports HCZ1_Deform and HCZ2_Deform from the S3K disassembly
 * (s3.asm lines ~71620 and ~71995).
 *
 * <h3>HCZ1 — Water Equilibrium System</h3>
 * <p>The background has a symmetrical cave structure with 13 parallax bands
 * at the top/bottom (mirrored at 7 speed levels), and a 192-scanline
 * per-line section in the middle. The cave bands use quarter-speed base
 * with 1/32 step between levels. When the camera is near the water
 * equilibrium point (Y=$610), binary waterline scroll data creates a wavy
 * water surface refraction effect around the midpoint (word 109).
 *
 * <p>The per-line section is split into three zones by the post-fill:
 * <ul>
 *   <li>Above water: flat at fastest cave speed (d3)</li>
 *   <li>Waterline zone: ripple-deformed gradient ({@code |d2|} words wide)</li>
 *   <li>Below water: flat at slowest cave speed (d4)</li>
 * </ul>
 *
 * <h3>HCZ2 — Scatter-Fill Parallax</h3>
 * <p>Uses a scatter-fill index pattern (like MGZ2) to distribute 7 speed
 * levels across 23 HScroll table words, then applies a 22-band deform array.
 * BG Y = cameraY / 4.
 */
public class SwScrlHcz extends AbstractZoneScrollHandler {

    private static final int PER_LINE_FLAG = 0x8000;

    // ==== HCZ1 Constants (s3.asm line ~71807) ====

    /**
     * HCZ1_BGDeformArray: 13 fixed bands + 192-line per-scanline band + terminator.
     * <pre>
     * Bands 0-5: upper cave ($40, 8, 8, 5, 5, 6)
     * Band 6:    main body ($F0 = 240px)
     * Bands 7-12: lower cave (mirror of 0-5: 6, 5, 5, 8, 8, $30)
     * Band 13:   per-line underwater ($80C0 = 192 lines, per-line flag set)
     * </pre>
     */
    private static final int[] HCZ1_DEFORM_HEIGHTS = {
            0x40, 8, 8, 5, 5, 6, 0xF0, 6, 5, 5, 8, 8, 0x30, 0x80C0, 0x7FFF
    };

    /** Y coordinate of the water equilibrium point (subi.w #$610,d0). */
    private static final int EQUILIBRIUM_Y = 0x610;

    /** Offset added to quarter-speed BG Y (addi.w #$190,d0). */
    private static final int BG_Y_OFFSET = 0x190;

    /** Waterline visibility threshold: deform applied when |d2| < $60. */
    private static final int WATERLINE_THRESHOLD = 0x60;

    /**
     * HCZ1 HScroll table size: 206 words (indices 0-205).
     * <ul>
     *   <li>Words 0-12: top/bottom cave bands (symmetrical, 7 speed levels)</li>
     *   <li>Words 13-204: per-scanline underwater section (192 words)</li>
     *   <li>Word 205: slowest speed value (same as word 6)</li>
     * </ul>
     */
    private static final int HCZ1_HSCROLL_SIZE = 206;

    // ==== HCZ2 Constants (s3.asm lines ~72038-72049) ====

    /** HCZ2_BGDeformArray: 22 bands + terminator. */
    private static final int[] HCZ2_DEFORM_HEIGHTS = {
            8, 8, 0x90, 0x10, 8, 0x30, 0x18, 8, 8, 0xA8, 0x30, 0x18,
            8, 8, 0xA8, 0x30, 0x18, 8, 8, 0xB0, 0x10, 8, 0x7FFF
    };

    /**
     * HCZ2_BGDeformIndex: scatter-fill index table.
     * Format: {count-1, byteOff, byteOff, ..., 0xFF}.
     * 7 speed groups distribute values into 23 HScroll table words.
     */
    private static final int[] HCZ2_DEFORM_INDEX = {
            /* Group 0 (fastest): words 5, 10, 15, 22 */
            4 - 1, 0x0A, 0x14, 0x1E, 0x2C,
            /* Group 1: words 6, 11, 16 */
            3 - 1, 0x0C, 0x16, 0x20,
            /* Group 2: words 0, 4, 7, 12, 17, 21 */
            6 - 1, 0x00, 0x08, 0x0E, 0x18, 0x22, 0x2A,
            /* Group 3: words 1, 8, 13, 18 */
            4 - 1, 0x02, 0x10, 0x1A, 0x24,
            /* Group 4: words 9, 14 */
            2 - 1, 0x12, 0x1C,
            /* Group 5: words 3, 20 */
            2 - 1, 0x06, 0x28,
            /* Group 6 (slowest): words 2, 19 */
            2 - 1, 0x04, 0x26,
            /* Terminator */
            0xFF
    };

    private static final int HCZ2_HSCROLL_SIZE = 24;

    // ==== HCZ2 Wall-Chase Constants (sonic3k.asm line ~106129) ====

    /** BG Y offset during wall-chase: camY - $500. */
    private static final int WALL_CHASE_BG_Y_OFFSET = 0x500;

    /** BG X offset during wall-chase: camX - $200 + wallOffset. */
    private static final int WALL_CHASE_BG_X_OFFSET = 0x200;

    // ==== HCZ2 Background Phase ====

    /**
     * Background scroll phase for HCZ Act 2.
     *
     * <p>The ROM's HCZ2_BackgroundEvent has 5 states (0/$4/$8/$C/$10). This
     * enum simplifies them into two meaningful modes for the scroll handler:
     * <ul>
     *   <li>{@link #WALL_CHASE} — states 0-4: flat scroll (PlainDeformation)
     *       during the collapsing-wall chase sequence</li>
     *   <li>{@link #NORMAL} — state $10: full parallax with HCZ2_Deform +
     *       ApplyDeformation. Also used for states $8/$C (transition) once
     *       the BG tile refresh is underway.</li>
     * </ul>
     */
    public enum Hcz2BgPhase {
        /** Wall-chase mode: flat/plain deformation, screen shake. */
        WALL_CHASE,
        /** Normal parallax mode: 7 speed levels via scatter-fill. */
        NORMAL
    }

    // ==== Mutable state ====

    private final short[] hcz1HScroll = new short[HCZ1_HSCROLL_SIZE];
    private final short[] hcz2HScroll = new short[HCZ2_HSCROLL_SIZE];

    /** Current HCZ2 background scroll mode. Default is NORMAL. */
    private Hcz2BgPhase hcz2Phase = Hcz2BgPhase.NORMAL;

    /** Screen shake Y offset (from ShakeScreen_Setup). */
    private int screenShakeOffset;

    /**
     * Wall movement X offset (Events_bg+$00 in ROM). Added to BG X during
     * wall-chase mode. Negative values mean the wall is advancing leftward.
     */
    private int wallChaseOffsetX;

    /** FG vertical scroll (non-zero only during screen shake). */
    private short vscrollFactorFG;

    /**
     * Optional HCZ waterline scroll data (9312 bytes from ROM).
     * Each of 97 positions has a 96-byte lookup table of byte indices
     * into the per-line gradient around word 109.
     */
    private final byte[] waterlineData;

    /**
     * Create an HCZ scroll handler with waterline scroll data for the full
     * water surface refraction effect.
     *
     * @param waterlineData 9312-byte waterline lookup table, or null to skip
     */
    public SwScrlHcz(byte[] waterlineData) {
        this.waterlineData = waterlineData;
    }

    /** Create an HCZ scroll handler without waterline data. */
    public SwScrlHcz() {
        this(null);
    }

    // ---- HCZ2 phase and screen shake API ----

    /**
     * Set the HCZ2 background scroll phase. The event handler calls this to
     * switch between wall-chase (flat scroll) and normal (parallax) modes.
     *
     * @param phase the new scroll phase
     */
    public void setHcz2BgPhase(Hcz2BgPhase phase) {
        this.hcz2Phase = phase;
    }

    /** @return the current HCZ2 background scroll phase */
    public Hcz2BgPhase getHcz2BgPhase() {
        return hcz2Phase;
    }

    /**
     * Set the screen shake Y offset. The HCZ2 event handler (or
     * ShakeScreen_Setup) calls this each frame. Affects both BG Y and FG
     * vertical scroll in Act 2.
     *
     * @param offset shake pixel offset (positive = shift down)
     */
    public void setScreenShakeOffset(int offset) {
        this.screenShakeOffset = offset;
    }

    /**
     * Set the wall movement X offset for wall-chase mode. In the ROM, this is
     * {@code Events_bg+$00}, updated by {@code HCZ2_WallMove}. Negative values
     * mean the wall has advanced leftward.
     *
     * @param offset wall chase BG X offset (added to base BG X)
     */
    public void setWallChaseOffsetX(int offset) {
        this.wallChaseOffsetX = offset;
    }

    @Override
    public short getVscrollFactorFG() {
        return vscrollFactorFG;
    }

    @Override
    public int getShakeOffsetY() {
        return screenShakeOffset;
    }

    @Override
    public void update(int[] horizScrollBuf,
                       int cameraX,
                       int cameraY,
                       int frameCounter,
                       int actId) {
        resetScrollTracking();
        vscrollFactorFG = 0;

        if (actId == 0) {
            updateHcz1(horizScrollBuf, cameraX, cameraY);
        } else {
            updateHcz2(horizScrollBuf, cameraX, cameraY);
            // HCZ2_ScreenEvent (sonic3k.asm line 105989): adds Screen_shake_offset
            // to Camera_Y_pos_copy. In the engine, shake is applied uniformly to both
            // tiles and sprites via camera.setShakeOffsets() + getShakeOffsetY() in
            // the rendering pipeline, so we don't modify vscrollFactorFG here.
        }

        if (minScrollOffset == Integer.MAX_VALUE) {
            minScrollOffset = 0;
            maxScrollOffset = 0;
        }
    }

    // ====================================================================
    // HCZ1_Deform (s3.asm line ~71620)
    // ====================================================================

    private void updateHcz1(int[] horizScrollBuf, int cameraX, int cameraY) {
        Arrays.fill(hcz1HScroll, (short) 0);

        // ---- Step 1: BG Y calculation (s3.asm lines 71621-71629) ----
        // d0 = cameraY - $610; d1 = d0; d0 >>= 2; d2 = d0 - d1; bgY = d0 + $190
        short delta = (short) (cameraY - EQUILIBRIUM_Y);
        short quarterDelta = (short) (delta >> 2);            // asr.w #2
        short d2 = (short) (quarterDelta - delta);            // sub.w d1,d2
        vscrollFactorBG = (short) (quarterDelta + BG_Y_OFFSET);

        // ---- Step 2: Fill middle per-line section (s3.asm lines 71630-71698) ----
        // d0 = Camera_X << 16 (16.16 fixed-point)
        int d0 = ((short) cameraX) << 16;

        if (d2 != 0) {
            int d1 = d0;                                      // move.l d0,d1
            int step = d0 >> 7;                               // asr.l #7,d3

            if (d2 <= -WATERLINE_THRESHOLD) {
                // Water well above: forward fill words 13-108 (s3.asm loc_3C442)
                fillMiddleForward(d1, step);
            } else {
                // Water near/below: backward fill words 204-109 (s3.asm loc_3C45E)
                fillMiddleBackward(d1, step);

                // Apply waterline deform if surface is visible (s3.asm lines 71671-71723)
                if (d2 > -WATERLINE_THRESHOLD && d2 < WATERLINE_THRESHOLD) {
                    applyWaterlineDeform(d2);
                }
            }
        }

        // ---- Step 3: Fill cave bands (s3.asm loc_3C4E2, lines 71725-71763) ----
        fillCaveBands(d0);
        short d3 = hcz1HScroll[0];   // fastest (word[0])
        short d4 = hcz1HScroll[6];   // slowest (word[6])

        // ---- Step 4: Post-fill overwrite (s3.asm lines 71765-71800) ----
        if (d2 < 0) {
            // Water above equilibrium (s3.asm loc_3C556):
            // Fill words 109-204 with d4 (slowest)
            fillRange(hcz1HScroll, 109, 205, d4);
            // Fill (96+d2) words from word 13 with d3 (fastest)
            int count = WATERLINE_THRESHOLD - 1 + d2;         // moveq #$60-1,d0; add.w d2,d0
            if (count >= 0) {
                fillRange(hcz1HScroll, 13, 13 + count + 1, d3);
            }
        } else {
            // Water at/below equilibrium (s3.asm loc_3C570):
            // Fill words 13-108 with d3 (fastest)
            fillRange(hcz1HScroll, 13, 109, d3);
            // Fill (96-d2) words backward from word 204 with d4 (slowest)
            int count = WATERLINE_THRESHOLD - 1 - d2;         // moveq #$60-1,d0; sub.w d2,d0
            if (count >= 0) {
                int writeIdx = 205;                            // lea (HScroll_table+$19A).w,a1
                for (int i = 0; i <= count; i++) {
                    hcz1HScroll[--writeIdx] = d4;             // move.w d4,-(a1)
                }
            }
        }

        // ---- Step 5: Apply deformation to 224 scanlines ----
        short fgScroll = negWord(cameraX);
        applyDeformation(horizScrollBuf, fgScroll, vscrollFactorBG,
                HCZ1_DEFORM_HEIGHTS, hcz1HScroll);
    }

    /**
     * Forward fill middle section: words 13-108 with gradient.
     * ROM: loc_3C442 (HScroll_table+$01A, 48 iterations x 2 words).
     *
     * <p>Gradient runs from cameraX at word 13 to ~cameraX/4 at word 108,
     * simulating depth perspective for the above-water portion.
     */
    private void fillMiddleForward(int d1, int step) {
        int idx = 13;
        for (int i = 0; i < 48; i++) {
            hcz1HScroll[idx++] = (short) (d1 >> 16);          // swap d1; move.w d1,(a1)+
            d1 -= step;                                        // sub.l d3,d1
            hcz1HScroll[idx++] = (short) (d1 >> 16);
            d1 -= step;
        }
    }

    /**
     * Backward fill middle section: words 204 down to 109 with gradient.
     * ROM: loc_3C45E (HScroll_table+$19A, 48 iterations x 2 words, pre-decrement).
     *
     * <p>Gradient runs from cameraX at word 204 to ~cameraX/4 at word 109,
     * creating the underwater depth perspective. The waterline deform then
     * re-orders values around word 109 to produce the wavy surface effect.
     */
    private void fillMiddleBackward(int d1, int step) {
        int idx = 205;                                         // lea (HScroll_table+$19A).w,a1
        for (int i = 0; i < 48; i++) {
            hcz1HScroll[--idx] = (short) (d1 >> 16);          // swap d1; move.w d1,-(a1)
            d1 -= step;
            hcz1HScroll[--idx] = (short) (d1 >> 16);
            d1 -= step;
        }
    }

    /**
     * Apply waterline scroll data around the midpoint (word 109).
     * ROM: s3.asm lines 71671-71723.
     *
     * <p>Reads byte indices from the waterline data file and uses them to
     * remap gradient values around word 109, creating a wavy water surface
     * refraction effect. Each byte index references a word offset from word 109
     * in the already-filled gradient.
     *
     * <p>For d2 &gt; 0 (water below equilibrium): writes forward from word 109,
     * data offset = (0x60 - d2) * 96. For d2 &lt; 0 (water above): writes
     * backward from word 108, data offset = (d2 + 0x60) * 96.
     *
     * @param d2 waterline offset (signed, non-zero, |d2| &lt; 0x60)
     */
    private void applyWaterlineDeform(short d2) {
        if (waterlineData == null) {
            return;
        }

        if (d2 > 0) {
            // Water below equilibrium: write forward from word 109 (s3.asm loc_3C4A0)
            int dataOffset = (WATERLINE_THRESHOLD - d2) * 96;
            for (int i = 0; i < d2; i++) {
                if (dataOffset + i >= waterlineData.length) break;
                int byteIdx = waterlineData[dataOffset + i] & 0xFF;
                int readWord = 109 + byteIdx;
                if (readWord < hcz1HScroll.length) {
                    hcz1HScroll[109 + i] = hcz1HScroll[readWord];
                }
            }
        } else {
            // Water above equilibrium: write backward from word 108 (s3.asm loc_3C4CE)
            int dataOffset = (d2 + WATERLINE_THRESHOLD) * 96;
            int count = -d2;
            int writeIdx = 109;                                // -(a1) from HScroll_table+$0DA
            for (int i = 0; i < count; i++) {
                if (dataOffset + i >= waterlineData.length) break;
                int byteIdx = waterlineData[dataOffset + i] & 0xFF;
                int readWord = 109 + byteIdx;
                writeIdx--;
                if (readWord < hcz1HScroll.length && writeIdx >= 0) {
                    hcz1HScroll[writeIdx] = hcz1HScroll[readWord];
                }
            }
        }
    }

    /**
     * Fill top/bottom cave bands: words 0-12 (mirrored) plus word 205.
     * ROM: loc_3C4E2 (s3.asm lines 71725-71763).
     *
     * <p>7 speed levels using quarter-speed base ({@code d0 = cameraX << 14})
     * and 1/32 step ({@code d1 = cameraX << 11}):
     * <pre>
     * word[0] = word[12] = base             (fastest of these bands)
     * word[1] = word[11] = base - step
     * word[2] = word[10] = base - 2*step
     * word[3] = word[9]  = base - 3*step
     * word[4] = word[8]  = base - 4*step
     * word[5] = word[7]  = base - 5*step
     * word[6] = word[205] = base - 6*step   (slowest)
     * </pre>
     *
     * @param d0orig Camera_X &lt;&lt; 16 (16.16 fixed-point, before any shifts)
     */
    private void fillCaveBands(int d0orig) {
        int d0 = d0orig >> 2;                                  // asr.l #2 → quarter speed
        int d1 = d0orig >> 5;                                  // asr.l #5 → step

        for (int level = 0; level < 7; level++) {
            short value = (short) (d0 >> 16);                  // swap d0; move.w d0,...
            hcz1HScroll[level] = value;
            hcz1HScroll[12 - level] = value;
            d0 -= d1;                                          // sub.l d1,d0
        }
        hcz1HScroll[205] = hcz1HScroll[6];                    // move.w d0,$19A(a1)
    }

    // ====================================================================
    // HCZ2_Deform (s3.asm line ~71995)
    // ====================================================================

    /**
     * HCZ2 update: switches between wall-chase (PlainDeformation) and normal
     * (HCZ2_Deform + ApplyDeformation) depending on the current background phase.
     *
     * <p><b>Wall-chase mode</b> (sonic3k.asm HCZ2_WallMove, line ~106129):
     * Flat scroll with BG Y = camY - $500, BG X = camX - $200 + wallOffset.
     * Active during the collapsing-wall chase sequence (BackgroundEvent states 0-4).
     *
     * <p><b>Normal mode</b> (sonic3k.asm HCZ2_Deform, line ~106179):
     * 7-speed scatter-fill parallax. BG Y = (camY - shake)/4 + shake.
     * Active after the wall chase ends (BackgroundEvent state $10).
     */
    private void updateHcz2(int[] horizScrollBuf, int cameraX, int cameraY) {
        short fgScroll = negWord(cameraX);

        if (hcz2Phase == Hcz2BgPhase.WALL_CHASE) {
            // PlainDeformation (sonic3k.asm loc_5F086): uniform FG+BG scroll.
            // HCZ2_WallMove: BG Y = camY - $500, BG X = camX - $200 + wallOffset.
            vscrollFactorBG = (short) (cameraY - WALL_CHASE_BG_Y_OFFSET);
            short bgScroll = negWord(cameraX - WALL_CHASE_BG_X_OFFSET + wallChaseOffsetX);
            writePlainDeformation(horizScrollBuf, fgScroll, bgScroll);
            return;
        }

        // Normal HCZ2_Deform (sonic3k.asm line ~106179)
        Arrays.fill(hcz2HScroll, (short) 0);

        // BG Y = (cameraY - shake) / 4 + shake  (asr.w #2 with shake compensation)
        short shakeY = (short) screenShakeOffset;
        vscrollFactorBG = (short) (asrWord(cameraY - shakeY, 2) + shakeY);

        // Build HScroll table using scatter-fill
        buildHcz2HScroll(cameraX);

        // Apply deformation to 224 scanlines
        applyDeformation(horizScrollBuf, fgScroll, vscrollFactorBG,
                HCZ2_DEFORM_HEIGHTS, hcz2HScroll);
    }

    /**
     * PlainDeformation: fill all 224 scanlines with a uniform FG/BG scroll pair.
     * ROM: sonic3k.asm line ~103593.
     */
    private void writePlainDeformation(int[] horizScrollBuf, short fgScroll, short bgScroll) {
        int packed = packScrollWords(fgScroll, bgScroll);
        trackOffset(fgScroll, bgScroll);
        Arrays.fill(horizScrollBuf, 0, VISIBLE_LINES, packed);
    }

    /**
     * Build HCZ2 HScroll table using scatter-fill index pattern.
     * ROM: HCZ2_Deform (s3.asm line ~71995).
     *
     * <p>Starts at half camera speed ({@code cameraX << 15}), decrements by
     * 1/8 ({@code cameraX << 12}) for each of 7 speed groups. The index table
     * maps each group to specific word offsets in the HScroll table.
     */
    private void buildHcz2HScroll(int cameraX) {
        int d0 = ((short) cameraX) << 16;
        d0 >>= 1;                                             // asr.l #1 → half speed
        int d1 = d0 >> 3;                                     // asr.l #3 → step

        int pos = 0;
        while (pos < HCZ2_DEFORM_INDEX.length) {
            int count = HCZ2_DEFORM_INDEX[pos++];
            if ((count & 0x80) != 0) break;                   // 0xFF terminator (bmi.s)
            short value = (short) (d0 >> 16);                  // swap d0
            for (int i = 0; i <= count; i++) {
                int byteOffset = HCZ2_DEFORM_INDEX[pos++];
                int wordIndex = byteOffset >> 1;
                if (wordIndex < hcz2HScroll.length) {
                    hcz2HScroll[wordIndex] = value;            // move.w d0,(a1,d2.w)
                }
            }
            d0 -= d1;                                          // sub.l d1,d0
        }
    }

    // ====================================================================
    // Shared deformation application (ports ApplyDeformation, sonic3k.asm ~103659)
    // ====================================================================

    /**
     * Map HScroll table values to 224 scanlines using a deform height array.
     * Implements the same logic as the ROM's ApplyDeformation routine.
     *
     * <p>For non-per-line bands: one HScroll_table word is repeated for all
     * scanlines in the band. For per-line bands ({@code PER_LINE_FLAG} set):
     * each scanline gets its own sequential word from the table.
     *
     * @param horizScrollBuf output buffer (224 entries)
     * @param fgScroll       negated camera X (foreground scroll value)
     * @param bgY            BG camera Y (determines which bands are visible)
     * @param deformHeights  height array with optional PER_LINE_FLAG
     * @param hScrollTable   source scroll values
     */
    private void applyDeformation(int[] horizScrollBuf,
                                   short fgScroll,
                                   short bgY,
                                   int[] deformHeights,
                                   short[] hScrollTable) {
        int bandIdx = 0;
        int valueIdx = 0;
        int remainingY = (short) bgY;
        int lineIndex = 0;

        // Skip bands above the visible area
        while (bandIdx < deformHeights.length) {
            int raw = deformHeights[bandIdx];
            if ((raw & 0x7FFF) == 0x7FFF) break;              // terminator
            boolean perLine = (raw & PER_LINE_FLAG) != 0;
            int height = raw & 0x7FFF;

            int next = remainingY - height;
            if (next < 0) {
                // Top of screen is within this band
                int invisibleCount = remainingY;
                int visibleLines = -next;

                if (perLine) {
                    // Skip invisible scanlines' values
                    valueIdx += invisibleCount;
                    int count = Math.min(visibleLines, VISIBLE_LINES);
                    for (int i = 0; i < count && lineIndex < VISIBLE_LINES; i++) {
                        short bg = negWord(safeRead(hScrollTable, valueIdx++));
                        horizScrollBuf[lineIndex++] = packScrollWords(fgScroll, bg);
                        trackOffset(fgScroll, bg);
                    }
                } else {
                    short bg = negWord(safeRead(hScrollTable, valueIdx++));
                    lineIndex = writeBand(horizScrollBuf, lineIndex, visibleLines,
                            fgScroll, bg);
                }
                bandIdx++;
                break;
            }

            // Whole band above screen — skip its values
            if (perLine) {
                valueIdx += height;
            } else {
                valueIdx++;
            }
            remainingY = next;
            bandIdx++;
        }

        // Write remaining fully visible bands
        while (lineIndex < VISIBLE_LINES && bandIdx < deformHeights.length) {
            int raw = deformHeights[bandIdx];
            if ((raw & 0x7FFF) == 0x7FFF) break;
            boolean perLine = (raw & PER_LINE_FLAG) != 0;
            int height = raw & 0x7FFF;
            int count = Math.min(height, VISIBLE_LINES - lineIndex);

            if (perLine) {
                for (int i = 0; i < count; i++) {
                    short bg = negWord(safeRead(hScrollTable, valueIdx++));
                    horizScrollBuf[lineIndex++] = packScrollWords(fgScroll, bg);
                    trackOffset(fgScroll, bg);
                }
            } else {
                short bg = negWord(safeRead(hScrollTable, valueIdx++));
                lineIndex = writeBand(horizScrollBuf, lineIndex, count, fgScroll, bg);
            }
            bandIdx++;
        }

        // Pad remaining lines with the last value used
        short lastBg = valueIdx > 0
                ? negWord(safeRead(hScrollTable, valueIdx - 1))
                : negWord(safeRead(hScrollTable, 0));
        while (lineIndex < VISIBLE_LINES) {
            horizScrollBuf[lineIndex] = packScrollWords(fgScroll, lastBg);
            trackOffset(fgScroll, lastBg);
            lineIndex++;
        }
    }

    private int writeBand(int[] buf, int start, int count,
                          short fgScroll, short bgScroll) {
        int packed = packScrollWords(fgScroll, bgScroll);
        trackOffset(fgScroll, bgScroll);
        int end = Math.min(VISIBLE_LINES, start + count);
        for (int i = start; i < end; i++) {
            buf[i] = packed;
        }
        return end;
    }

    private static short safeRead(short[] table, int index) {
        if (index < 0) return 0;
        if (index >= table.length) return table[table.length - 1];
        return table[index];
    }

    private static void fillRange(short[] table, int from, int toExclusive, short value) {
        int end = Math.min(toExclusive, table.length);
        for (int i = Math.max(0, from); i < end; i++) {
            table[i] = value;
        }
    }
}
