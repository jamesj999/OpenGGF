package com.openggf.level.objects;

import com.openggf.game.GameServices;

import com.openggf.game.LevelState;
import com.openggf.game.GameStateManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.PatternDesc;
import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;

public class HudRenderManager {

    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(HudRenderManager.class.getName());

    /**
     * Controls how the HUD warns the player when rings=0 or time>=9:00.
     * S1/S2 swap the label palette (red flash), S3K hides the label text entirely.
     */
    public enum HudFlashMode {
        /** S1/S2: label switches to icon palette (red flash) */
        PALETTE_SWAP,
        /** S3K: label text is hidden on blink frames (toggled every 8 frames) */
        TEXT_HIDE
    }

    private static final int DIGIT_ZERO = 0; // Relative to loaded HUD digit patterns
    private static final int DIGIT_COLON = 10; // Assuming colon is after 9

    // Pattern indices relative to the base HUD patterns loaded
    // We need to know where HUD patterns are loaded.
    // Usually they are at fixed VRAM, but our GraphicsManager uses pattern indices.
    // We might need to look up mapping frames or assume a sequence.

    // For now, assuming a simple draw capability.
    // However, given the engine structure, we likely need to use PatternDescs or
    // similar if we want to bypass sprite system,
    // OR create sprites for HUD elements.
    // Given the HUD overlay nature, direct pattern drawing via GraphicsManager
    // (like DebugOverlay) or a dedicated SpriteManager bucket is best.
    // We'll follow the pattern of DebugOverlayManager / DebugRenderer for now, or
    // similar to ring rendering helpers.

    // BUT, Sonic 2 HUD is effectively a set of sprites/patterns fixed to screen.
    // Let's assume we can draw direct patterns to screen coordinates.

    private final GraphicsManager graphicsManager;
    private final Camera camera = Camera.getInstance();
    private final GameStateManager gameState = GameStateManager.getInstance();
    private int digitPatternIndex;
    private int textPatternIndex;

    // Cached HUD values for performance - avoid String.valueOf() and parsing each frame
    private int cachedScore = -1;
    private int cachedRings = -1;
    private int cachedLives = -1;
    private String cachedTime = null;
    // Pre-computed digit arrays to avoid allocation each frame
    private final int[] scoreDigits = new int[6];  // Max 6 digits for score
    private final int[] ringDigits = new int[3];   // Max 3 digits for rings
    private final int[] livesDigits = new int[3];  // Max 3 digits for lives
    private int scoreDigitCount = 0;
    private int ringDigitCount = 0;
    private int livesDigitCount = 0;

    // PatternDesc for standard HUD rendering (Palette 0, no flip, priority high?)
    // Priority is handled by draw order usually, but PatternDesc needs a priority
    // bit.
    // Assuming priority 1 (high).
    // Icon/flash palette and HUD text palette — configurable per game
    private PatternDesc iconPatternDesc = new PatternDesc(0x8000); // default: Priority 1, Pal 0
    private PatternDesc hudPatternDesc = new PatternDesc(0xA000);  // default: Priority 1, Pal 1

    private HudFlashMode flashMode = HudFlashMode.PALETTE_SWAP;

    public HudRenderManager(GraphicsManager graphicsManager) {
        this.graphicsManager = graphicsManager;
    }

    /**
     * Sets the palette lines for HUD rendering.
     * Different games use different palette lines for the yellow text vs flash colors.
     *
     * @param textPalLine  palette line for HUD text labels (yellow)
     * @param flashPalLine palette line for icon and flash state (red)
     */
    public void setHudPalettes(int textPalLine, int flashPalLine) {
        this.hudPatternDesc = new PatternDesc(0x8000 | (textPalLine << 13));
        this.iconPatternDesc = new PatternDesc(0x8000 | (flashPalLine << 13));
    }

    public void setHudFlashMode(HudFlashMode mode) {
        this.flashMode = mode;
    }

    private int textPatternCount;
    private int livesPatternCount;

    public void setDigitPatternIndex(int digitPatternIndex) {
        this.digitPatternIndex = digitPatternIndex;
        LOGGER.fine("HudRenderManager Digit Index: " + digitPatternIndex);
    }

    public void setTextPatternIndex(int textPatternIndex, int count) {
        this.textPatternIndex = textPatternIndex;
        this.textPatternCount = count;
        LOGGER.fine("HudRenderManager Text Index: " + textPatternIndex + ", Count: " + count);
    }

    private int livesPatternIndex;
    private int livesNumbersPatternIndex;

    public void setLivesPatternIndex(int livesPatternIndex, int count) {
        this.livesPatternIndex = livesPatternIndex;
        this.livesPatternCount = count;
    }

    public void setLivesNumbersPatternIndex(int livesNumbersPatternIndex) {
        this.livesNumbersPatternIndex = livesNumbersPatternIndex;
    }

    /**
     * Invalidates the HUD cache, forcing a full recalculation on next frame.
     * Call this when loading a new level or when HUD state needs full refresh.
     */
    public void invalidateCache() {
        cachedScore = -1;
        cachedRings = -1;
        cachedLives = -1;
        cachedTime = null;
        scoreDigitCount = 0;
        ringDigitCount = 0;
        livesDigitCount = 0;
    }

    /**
     * Converts a number to digits and stores in the provided array.
     * Returns the number of digits written.
     * Avoids String allocation that would occur with String.valueOf().
     */
    private int numberToDigits(int value, int[] digits) {
        if (value == 0) {
            digits[0] = 0;
            return 1;
        }

        // Count digits
        int temp = value;
        int count = 0;
        while (temp > 0) {
            count++;
            temp /= 10;
        }

        // Extract digits in reverse order (right to left)
        int idx = count - 1;
        temp = value;
        while (temp > 0) {
            digits[idx--] = temp % 10;
            temp /= 10;
        }

        return count;
    }

    public void draw(LevelState levelGamestate) {
        draw(levelGamestate, null);
    }

    public void draw(LevelState levelGamestate, PlayableEntity player) {
        if (levelGamestate == null)
            return;

        // Check if debug mode is active
        boolean debugMode = player != null && player.isDebugMode();

        if (debugMode) {
            // Debug mode: Sonic 2 style coordinate display
            // "SCOR" text stays, "E" is replaced with smaller hex coordinates
            // Two rows of 8 hex digits each (using smaller 8x8 lives number font):
            // Top row: Player X (4 hex digits) + Player Y (4 hex digits)
            // Bottom row: Camera X (4 hex digits) + Camera Y (4 hex digits)

            // Draw "SCOR" (skip the E) - this uses the normal large HUD text
            drawHudString(16, 8, "SCOR", hudPatternDesc);

            // Calculate where the hex coordinates start (right after "SCOR")
            // "SCOR" is 4 characters * 8 pixels = 32 pixels, starting at x=16
            int hexStartX = 16 + 32; // = 48

            // Player coordinates (top row, at same Y as "SCOR")
            int playerX = player.getCentreX() & 0xFFFF;
            int playerY = player.getCentreY() & 0xFFFF;
            drawSmallHexCoordinates(hexStartX, 8, playerX, playerY);

            // Camera coordinates (second row, below the first)
            int camX = camera.getX() & 0xFFFF;
            int camY = camera.getY() & 0xFFFF;
            drawSmallHexCoordinates(hexStartX, 16, camX, camY);

            // Draw Time below the debug coordinates
            boolean flashTime = levelGamestate.shouldFlashTimer();
            drawTimeLabel(flashTime, levelGamestate.getFlashCycle());
            drawTime(56, 24, levelGamestate.getDisplayTime());
        } else {
            // Normal gameplay: Draw Score
            drawHudString(16, 8, "SCORE", hudPatternDesc);
            drawScore(gameState.getScore());

            // Draw Time
            boolean flashTime = levelGamestate.shouldFlashTimer();
            drawTimeLabel(flashTime, levelGamestate.getFlashCycle());
            drawTime(56, 24, levelGamestate.getDisplayTime());
        }

        drawCores(levelGamestate.getRings(), levelGamestate.getFlashCycle());
        drawLives(gameState.getLives());
    }

    private void drawTimeLabel(boolean flashTime, boolean flashCycle) {
        if (flashMode == HudFlashMode.TEXT_HIDE) {
            // S3K: hide TIME label entirely on blink frames
            if (!(flashTime && flashCycle)) {
                drawHudString(16, 24, "TIME", hudPatternDesc);
            }
        } else {
            // S1/S2: swap palette to red on flash
            drawHudString(16, 24, "TIME", flashTime ? iconPatternDesc : hudPatternDesc);
        }
    }

    private void drawCores(int rings, boolean flashCycle) {
        if (flashMode == HudFlashMode.TEXT_HIDE) {
            // S3K: hide RINGS label entirely on blink frames when rings=0
            if (!(rings == 0 && flashCycle)) {
                drawHudString(16, 40, "RINGS", hudPatternDesc);
            }
        } else {
            // S1/S2: swap palette to red on flash
            boolean flash = (rings == 0);
            PatternDesc desc = (flash && flashCycle) ? iconPatternDesc : hudPatternDesc;
            drawHudString(16, 40, "RINGS", desc);
        }
        drawNumberRightAligned(64, 40, rings, 3);
    }

    private void drawScore(int score) {
        // "SCORE" already drawn in main draw loop to group label logic?
        // No, invalidating previous lines logic if I just change draw().
        // Wait, original code had `drawHudString` inside `drawScore`.
        // I removed it in my replacement block above?
        // Let's look at the original code structure:
        // draw() called drawHudString("SCORE"), then drawScore().
        // default drawScore() had: drawHudString("SCORE") inside it!
        // That's redundant double drawing in the original code?
        // Line 83: drawHudString(.., "SCORE")
        // Line 104 (inside drawScore): drawHudString(.., "SCORE")
        // Yes, the original code double-drew "SCORE".
        // I will fix this cleanliness issue while I am here.

        drawNumberRightAligned(64, 8, score, 6);
    }

    /**
     * Draws hex coordinates using small 8x8 font (like original Sonic 2 debug
     * mode).
     * Format: XXXXYYYY (8 hex digits total, no gap between X and Y)
     * Uses the smaller lives number font for digits 0-9.
     * 
     * @param x      Base X position on screen
     * @param y      Base Y position on screen
     * @param xCoord X coordinate to display (will be masked to 16-bit)
     * @param yCoord Y coordinate to display (will be masked to 16-bit)
     */
    private void drawSmallHexCoordinates(int x, int y, int xCoord, int yCoord) {
        int camX = camera.getX();
        int camY = camera.getY();

        // Draw X coordinate (4 hex digits)
        for (int i = 0; i < 4; i++) {
            int nibble = (xCoord >> (12 - i * 4)) & 0xF;
            drawSmallHexDigit(x + camX + (i * 8), y + camY, nibble);
        }

        // Draw Y coordinate immediately after X (4 hex digits, no gap)
        int yStartX = x + 32; // 4 digits * 8 pixels = 32 pixels

        for (int i = 0; i < 4; i++) {
            int nibble = (yCoord >> (12 - i * 4)) & 0xF;
            drawSmallHexDigit(yStartX + camX + (i * 8), y + camY, nibble);
        }
    }

    /**
     * Draws a single hex digit (0-F) using the small 8x8 lives number font.
     * Unlike the large HUD digits which are 16px tall (2 tiles), these are single
     * 8x8 tiles.
     * 
     * @param x     Screen X position
     * @param y     Screen Y position
     * @param digit Hex digit value (0-15)
     */
    private void drawSmallHexDigit(int x, int y, int digit) {
        // Fallback: use lives numbers for 0-9, approximations for A-F
        if (livesNumbersPatternIndex <= 0) {
            return;
        }

        if (digit < 10) {
            renderSafe(livesNumbersPatternIndex + digit, iconPatternDesc, x, y);
        } else {
            // Fallback for A-F when debug font not available
            int fallbackDigit = digit - 9;
            if (fallbackDigit >= 0 && fallbackDigit <= 9) {
                renderSafe(livesNumbersPatternIndex + fallbackDigit, iconPatternDesc, x, y);
            }
        }
    }

    private void drawLives(int lives) {
        int camX = camera.getX();
        int camY = camera.getY();

        // Base position for Lives HUD (Bottom Left)
        int baseX = 16;
        int baseY = 200;

        // Draw Icon (Sonic) - Palette 0 (iconPatternDesc)
        // 16x16 icon composed of 4 tiles in column-major order (0,1,2,3)
        // Top-Left (0)
        renderSafe(livesPatternIndex + 0, iconPatternDesc, baseX + camX, baseY + camY);
        // Bottom-Left (1)
        renderSafe(livesPatternIndex + 1, iconPatternDesc, baseX + camX, baseY + camY + 8);
        // Top-Right (2)
        renderSafe(livesPatternIndex + 2, iconPatternDesc, baseX + camX + 8, baseY + camY);
        // Bottom-Right (3)
        renderSafe(livesPatternIndex + 3, iconPatternDesc, baseX + camX + 8, baseY + camY + 8);

        // Draw Name "SONIC"
        // S: 4
        // O: 5 (Left), 7 (Right)
        // N: 8 (Left), 10 (Right)
        // I: 11 (Left), 12 (Right)
        // C: 13

        int drawX = baseX + 16;

        // S (8px)
        renderSafe(livesPatternIndex + 4, hudPatternDesc, drawX + camX, baseY + camY);
        drawX += 8;

        // O (16px)
        renderSafe(livesPatternIndex + 6, hudPatternDesc, drawX + camX, baseY + camY);
        renderSafe(livesPatternIndex + 9, hudPatternDesc, drawX + camX + 8, baseY + camY);
        drawX += 8;

        // N I-l(16px)
        renderSafe(livesPatternIndex + 8, hudPatternDesc, drawX + camX, baseY + camY);
        renderSafe(livesPatternIndex + 10, hudPatternDesc, drawX + camX + 8, baseY + camY);
        drawX += 16;

        // I-r C(8px)
        renderSafe(livesPatternIndex + 11, hudPatternDesc, drawX + camX, baseY + camY);

        // NEW LINE for X and Numbers
        int line2Y = baseY + 8; // Next line
        // Indent to align with text above? Or align with Icon?
        // User said: "next to the currently-misplaced X".
        // Usually X is roughly under the start of "SONIC".
        int xDrawX = baseX + 16;

        // Draw "X"
        // X: 5 (Left), 7 (Right) (Swapped with O)
        renderSafe(livesPatternIndex + 5, iconPatternDesc, xDrawX + camX, line2Y + camY); // X Left
        renderSafe(livesPatternIndex + 7, iconPatternDesc, xDrawX + camX + 8, line2Y + camY); // X Right

        // Gap after X
        int numDrawX = xDrawX + 16 + 8; // 16 for X + 8 gap

        // Numbers use livesNumbersPatternIndex and iconPatternDesc
        // livesNumbersPatternIndex corresponds to '0'
        // Use cached digit array to avoid String allocation each frame

        if (livesNumbersPatternIndex > 0) {
            if (lives != cachedLives) {
                cachedLives = lives;
                livesDigitCount = numberToDigits(lives, livesDigits);
            }
            for (int i = 0; i < livesDigitCount; i++) {
                int digit = livesDigits[i];
                renderSafe(livesNumbersPatternIndex + digit, iconPatternDesc, numDrawX + camX + (i * 8), line2Y + camY);
            }
        }
    }

    private void drawHudString(int x, int y, String text, PatternDesc patternDesc) {
        int camX = camera.getX();
        int camY = camera.getY();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int patternId = getPatternIndexForChar(c);
            if (patternId != -1) {
                // Interleaved layout: Top tile is even (2*k), Bottom tile is odd (2*k + 1)
                // Assuming "Even items were bottoms" meant "2nd, 4th..." (1-based count) ->
                // Indices 1, 3, 5...
                // If indices 0, 2, 4 were bottoms, text would be upside down in a linear strip.
                // Standard Nemesis/Interleaved is Top then Bottom.
                renderSafe(textPatternIndex + (patternId * 2), patternDesc, x + camX + (i * 8), y + camY);
                renderSafe(textPatternIndex + (patternId * 2) + 1, patternDesc, x + camX + (i * 8), y + camY + 8);
            }
        }
    }

    // Mapping derived from debug strip: "SCORRINGTIME"
    // Sequence in ROM: S, C, O, R, R, I, N, G, T, I, M, E
    // Indices: 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11
    private int getPatternIndexForChar(char c) {
        return switch (c) {
            case 'S' -> 0;
            case 'C' -> 1;
            case 'O' -> 2;
            case 'R' -> 3; // Using first R (index 3). Second R is 4.
            case 'I' -> 5; // Using first I (index 5). Second I is 9.
            case 'N' -> 6;
            case 'G' -> 7;
            case 'T' -> 8;
            case 'M' -> 10;
            case 'E' -> 11;
            case ':' -> 12;
            default -> -1;
        };
    }

    private void drawNumberRightAligned(int startX, int y, int value, int maxDigits) {
        int camX = camera.getX();
        int camY = camera.getY();

        // Use cached digit arrays to avoid String allocation each frame
        int[] digitArray;
        int digitCount;

        // Determine which cache to use based on maxDigits and value range
        if (maxDigits == 6) {
            // Score
            if (value != cachedScore) {
                cachedScore = value;
                scoreDigitCount = numberToDigits(value, scoreDigits);
            }
            digitArray = scoreDigits;
            digitCount = scoreDigitCount;
        } else if (maxDigits == 3 && value <= 999) {
            // Could be rings or lives - use rings cache for this call
            // (lives uses a separate method)
            if (value != cachedRings) {
                cachedRings = value;
                ringDigitCount = numberToDigits(value, ringDigits);
            }
            digitArray = ringDigits;
            digitCount = ringDigitCount;
        } else {
            // Fallback: compute directly (rare case)
            digitArray = new int[maxDigits];
            digitCount = numberToDigits(value, digitArray);
        }

        // Calculate offset for right alignment
        int padding = maxDigits - digitCount;
        if (padding < 0) padding = 0;

        for (int i = 0; i < digitCount; i++) {
            int digit = digitArray[i];
            int xPos = startX + (padding + i) * 8;

            // Draw top
            renderSafe(digitPatternIndex + (digit * 2), hudPatternDesc, xPos + camX, y + camY);
            // Draw bottom
            renderSafe(digitPatternIndex + (digit * 2) + 1, hudPatternDesc, xPos + camX, y + camY + 8);
        }
    }

    private void drawTime(int x, int y, String timeStr) {
        int camX = camera.getX();
        int camY = camera.getY();
        for (int i = 0; i < timeStr.length(); i++) {
            char c = timeStr.charAt(i);
            int patternIdx;
            if (c == ':') {
                patternIdx = digitPatternIndex + 20; // Reverted to use digit patterns for colon
            } else {
                patternIdx = digitPatternIndex + ((c - '0') * 2);
            }
            // Draw top
            renderSafe(patternIdx, hudPatternDesc, x + camX + (i * 8), y + camY);
            // Draw bottom
            renderSafe(patternIdx + 1, hudPatternDesc, x + camX + (i * 8), y + camY + 8);
        }
    }

    private void renderSafe(int patternId, PatternDesc desc, int x, int y) {
        // Simple bounds check if we knew the max, but for now just catch GL errors
        // effectively?
        // Actually, just delegate. The error comes from map lookup failure.
        // We will add debug strip here.
        graphicsManager.renderPatternWithId(patternId, desc, x, y);
    }

    public void drawDebugStrip() {
        int camX = camera.getX();
        int camY = camera.getY();
        int chunks = Math.min(40, textPatternCount); // Clamp limit
        for (int i = 0; i < chunks; i++) {
            graphicsManager.renderPatternWithId(textPatternIndex + i, hudPatternDesc, camX + 10 + (i * 8), camY + 100);
        }
    }
}

