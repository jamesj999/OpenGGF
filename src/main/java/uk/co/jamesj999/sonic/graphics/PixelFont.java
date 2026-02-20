package uk.co.jamesj999.sonic.graphics;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Sprite font renderer using a PNG grid sheet.
 * <p>
 * Grid spec: 26 columns x 5 rows, 16x16 cells (9px glyph + 7px padding wide, 10px glyph + 6px padding tall).
 * Padding includes outer edges, so first glyph starts at pixel (7, 5) in the image.
 */
public class PixelFont {

    private static final int COLS = 26;
    private static final int ROWS = 5;
    private static final int CELL_W = 16;
    private static final int CELL_H = 16;
    private static final int GLYPH_W = 9;
    private static final int GLYPH_H = 10;
    // Padding includes outer edges: first glyph starts at (7, 5) in the image
    private static final int GLYPH_OFFSET_X = 7;
    private static final int GLYPH_OFFSET_Y = 5;

    // Row contents (matching the pixel-font.png layout)
    private static final String ROW0 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String ROW1 = "abcdefghijklmnopqrstuvwxyz";
    private static final String ROW2 = "0123456789_-+\u00D7\u00F7=*/\\%@\u00A9\u00AE\u00B0#\u00A6";
    private static final String ROW3 = "?\u00BF!\u00A1()[]{}<>\u00AB\u00BB:;\u2018\u2019~`\u00AC,.|\u00A3\u20AC";
    private static final String ROW4 = "$\u201C\u201D^&\u263A\u00BD\u00BE&";

    private int textureId;
    private int textureWidth;
    private int textureHeight;
    private TexturedQuadRenderer renderer;

    // Fast ASCII lookup (0-127)
    private final int[] charToCol = new int[128];
    private final int[] charToRow = new int[128];
    private final boolean[] charKnown = new boolean[128];

    // Extended characters (beyond ASCII 127)
    private final Map<Character, int[]> extendedChars = new HashMap<>();

    public void init(String fontPngPath, TexturedQuadRenderer renderer) throws IOException {
        this.renderer = renderer;
        this.textureId = PngTextureLoader.loadTexture(fontPngPath);
        this.textureWidth = PngTextureLoader.getLastWidth();
        this.textureHeight = PngTextureLoader.getLastHeight();

        // Build lookup tables
        buildLookup(ROW0, 0);
        buildLookup(ROW1, 1);
        buildLookup(ROW2, 2);
        buildLookup(ROW3, 3);
        buildLookup(ROW4, 4);
    }

    private void buildLookup(String row, int rowIndex) {
        for (int col = 0; col < row.length() && col < COLS; col++) {
            char c = row.charAt(col);
            if (c == ' ') continue; // skip spaces in the grid definition
            if (c < 128) {
                charToCol[c] = col;
                charToRow[c] = rowIndex;
                charKnown[c] = true;
            } else {
                extendedChars.put(c, new int[]{rowIndex, col});
            }
        }
    }

    /**
     * Draws a string at the given screen position.
     * Coordinates are in game resolution (320x224), origin top-left.
     * The renderer uses OpenGL coords (y=0 at bottom), so we flip internally.
     */
    public void drawText(String text, int x, int y, float r, float g, float b, float a) {
        int cursorX = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                cursorX += GLYPH_W;
                continue;
            }

            int col = -1, row = -1;
            if (c < 128 && charKnown[c]) {
                col = charToCol[c];
                row = charToRow[c];
            } else {
                int[] ext = extendedChars.get(c);
                if (ext != null) {
                    row = ext[0];
                    col = ext[1];
                }
            }

            if (col >= 0) {
                // Glyph pixel position in the source image (before Y-flip)
                int srcX = GLYPH_OFFSET_X + col * CELL_W;
                int srcY = GLYPH_OFFSET_Y + row * CELL_H;

                float u0 = (float) srcX / textureWidth;
                float u1 = (float)(srcX + GLYPH_W) / textureWidth;

                // Convert top-left game coords to OpenGL bottom-left coords
                float glY = 224f - y - GLYPH_H;

                // Texture is Y-flipped by PngTextureLoader: source top = v=1, bottom = v=0
                float texTop = 1.0f - (float) srcY / textureHeight;
                float texBottom = 1.0f - (float)(srcY + GLYPH_H) / textureHeight;

                renderer.drawTextureRegion(textureId,
                    cursorX, glY, GLYPH_W, GLYPH_H,
                    u0, texBottom, u1, texTop,
                    r, g, b, a);
            }

            cursorX += GLYPH_W;
        }
    }

    /**
     * Draws a string centered horizontally on screen.
     */
    public void drawTextCentered(String text, int screenWidth, int y,
                                 float r, float g, float b, float a) {
        int textWidth = measureWidth(text);
        int x = (screenWidth - textWidth) / 2;
        drawText(text, x, y, r, g, b, a);
    }

    /**
     * Returns the pixel width of a string.
     */
    public int measureWidth(String text) {
        return text.length() * GLYPH_W;
    }

    public void cleanup() {
        PngTextureLoader.deleteTexture(textureId);
    }
}
