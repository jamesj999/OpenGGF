package com.openggf.level.scroll.compose;

import com.openggf.level.scroll.M68KMath;

import java.util.Arrays;

/**
 * Frame-local writer for packed scroll output and optional VScroll overlays.
 */
public final class ScrollEffectComposer {

    public static final int VISIBLE_LINES = M68KMath.VISIBLE_LINES;
    public static final int PER_COLUMN_VSCROLL_COLUMNS = 20;

    private final int[] packedScrollWords;
    private int minScrollOffset;
    private int maxScrollOffset;
    private short vscrollFactorBG;
    private short vscrollFactorFG;
    private int shakeOffsetX;
    private int shakeOffsetY;
    private short[] perLineVScrollBG;
    private short[] perColumnVScrollBG;
    private short[] perColumnVScrollFG;
    private boolean hasPerColumnVScrollBG;

    public ScrollEffectComposer() {
        this(VISIBLE_LINES);
    }

    public ScrollEffectComposer(int visibleLines) {
        if (visibleLines < 0) {
            throw new IllegalArgumentException("visibleLines must be non-negative");
        }
        this.packedScrollWords = new int[visibleLines];
        reset();
    }

    public void reset() {
        Arrays.fill(packedScrollWords, 0);
        minScrollOffset = Integer.MAX_VALUE;
        maxScrollOffset = Integer.MIN_VALUE;
        vscrollFactorBG = 0;
        vscrollFactorFG = 0;
        shakeOffsetX = 0;
        shakeOffsetY = 0;
        perLineVScrollBG = null;
        perColumnVScrollFG = null;
        hasPerColumnVScrollBG = false;
    }

    public int visibleLineCount() {
        return packedScrollWords.length;
    }

    public int[] packedScrollWords() {
        return Arrays.copyOf(packedScrollWords, packedScrollWords.length);
    }

    public void copyPackedScrollWordsTo(int[] target) {
        if (target.length < packedScrollWords.length) {
            throw new IllegalArgumentException("target length must be at least " + packedScrollWords.length);
        }
        System.arraycopy(packedScrollWords, 0, target, 0, packedScrollWords.length);
    }

    public int packedScrollWordAt(int line) {
        checkLine(line);
        return packedScrollWords[line];
    }

    public void writePackedScrollWord(int line, short fgScroll, short bgScroll) {
        checkLine(line);
        packedScrollWords[line] = M68KMath.packScrollWords(fgScroll, bgScroll);
        trackOffset(fgScroll, bgScroll);
    }

    public void fillPackedScrollWords(int startLine, int lineCount, short fgScroll, short bgScroll) {
        if (lineCount <= 0) {
            return;
        }
        int end = Math.min(packedScrollWords.length, startLine + lineCount);
        for (int line = Math.max(0, startLine); line < end; line++) {
            writePackedScrollWord(line, fgScroll, bgScroll);
        }
    }

    public int getMinScrollOffset() {
        return minScrollOffset;
    }

    public int getMaxScrollOffset() {
        return maxScrollOffset;
    }

    public void recalculateTrackedOffsets() {
        minScrollOffset = Integer.MAX_VALUE;
        maxScrollOffset = Integer.MIN_VALUE;
        for (int packedScrollWord : packedScrollWords) {
            short fgScroll = (short) (packedScrollWord >> 16);
            short bgScroll = (short) packedScrollWord;
            trackOffset(fgScroll, bgScroll);
        }
    }

    public short getVscrollFactorBG() {
        return vscrollFactorBG;
    }

    public void setVscrollFactorBG(short vscrollFactorBG) {
        this.vscrollFactorBG = vscrollFactorBG;
    }

    public short getVscrollFactorFG() {
        return vscrollFactorFG;
    }

    public void setVscrollFactorFG(short vscrollFactorFG) {
        this.vscrollFactorFG = vscrollFactorFG;
    }

    public int getShakeOffsetX() {
        return shakeOffsetX;
    }

    public void setShakeOffsetX(int shakeOffsetX) {
        this.shakeOffsetX = shakeOffsetX;
    }

    public int getShakeOffsetY() {
        return shakeOffsetY;
    }

    public void setShakeOffsetY(int shakeOffsetY) {
        this.shakeOffsetY = shakeOffsetY;
    }

    public short[] getPerLineVScrollBG() {
        return copyOrNull(perLineVScrollBG);
    }

    public void setPerLineVScrollBG(short[] perLineVScrollBG) {
        this.perLineVScrollBG = copyOrNull(perLineVScrollBG);
    }

    public short[] getPerColumnVScrollBG() {
        return hasPerColumnVScrollBG ? perColumnVScrollBG : null;
    }

    public void setPerColumnVScrollBG(short[] perColumnVScrollBG) {
        if (perColumnVScrollBG == null) {
            clearPerColumnVScrollBG();
            return;
        }
        if (this.perColumnVScrollBG == null || this.perColumnVScrollBG.length != perColumnVScrollBG.length) {
            this.perColumnVScrollBG = Arrays.copyOf(perColumnVScrollBG, perColumnVScrollBG.length);
        } else {
            System.arraycopy(perColumnVScrollBG, 0, this.perColumnVScrollBG, 0, perColumnVScrollBG.length);
        }
        hasPerColumnVScrollBG = true;
    }

    public short[] writablePerColumnVScrollBG(int columnCount) {
        if (columnCount < 0) {
            throw new IllegalArgumentException("columnCount must be non-negative");
        }
        if (perColumnVScrollBG == null || perColumnVScrollBG.length != columnCount) {
            perColumnVScrollBG = new short[columnCount];
        }
        hasPerColumnVScrollBG = true;
        return perColumnVScrollBG;
    }

    public void clearPerColumnVScrollBG() {
        hasPerColumnVScrollBG = false;
    }

    public short[] getPerColumnVScrollFG() {
        return copyOrNull(perColumnVScrollFG);
    }

    public void setPerColumnVScrollFG(short[] perColumnVScrollFG) {
        this.perColumnVScrollFG = copyOrNull(perColumnVScrollFG);
    }

    private void trackOffset(short fgScroll, short bgScroll) {
        int offset = bgScroll - fgScroll;
        if (offset < minScrollOffset) {
            minScrollOffset = offset;
        }
        if (offset > maxScrollOffset) {
            maxScrollOffset = offset;
        }
    }

    private void checkLine(int line) {
        if (line < 0 || line >= packedScrollWords.length) {
            throw new IndexOutOfBoundsException("line " + line + " outside 0.." + (packedScrollWords.length - 1));
        }
    }

    private short[] copyOrNull(short[] values) {
        return values == null ? null : Arrays.copyOf(values, values.length);
    }
}
