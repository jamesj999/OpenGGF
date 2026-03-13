package com.openggf.debug;

import org.lwjgl.system.MemoryUtil;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.GL_R8;

/**
 * Pre-renders ASCII glyphs to a texture atlas for GPU-accelerated text rendering.
 * Supports multiple font sizes (SMALL, MEDIUM, LARGE) packed into a single 1024x1024 texture.
 * Uses GL_RED for single-channel grayscale with antialiasing for smooth outlines.
 */
public class GlyphAtlas {
    private static final Logger LOGGER = Logger.getLogger(GlyphAtlas.class.getName());

    private static final int ATLAS_SIZE = 1024;
    private static final int FIRST_CHAR = 32;  // Space
    private static final int LAST_CHAR = 126;  // Tilde
    private static final int GLYPH_PADDING = 2;

    private int textureId;
    private final Map<FontSize, Map<Character, GlyphInfo>> glyphsBySize = new EnumMap<>(FontSize.class);
    private final Map<FontSize, Integer> lineHeightBySize = new EnumMap<>(FontSize.class);
    private final Map<FontSize, Integer> ascentBySize = new EnumMap<>(FontSize.class);
    private boolean initialized;

    /**
     * Information about a single glyph in the atlas.
     */
    public static class GlyphInfo {
        public final float u0, v0, u1, v1;  // Normalized UV coords
        public final int width, height;
        public final int xOffset, yOffset;
        public final int advance;

        GlyphInfo(float u0, float v0, float u1, float v1,
                  int width, int height, int xOffset, int yOffset, int advance) {
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.width = width;
            this.height = height;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
            this.advance = advance;
        }
    }

    /**
     * Initializes the glyph atlas with multiple font sizes.
     * Should be called once during startup with a valid GL context.
     *
     * @param baseFont The base font (size will be overridden for each FontSize)
     * @param scaleFactor Scale factor for DPI scaling
     */
    public void init(Font baseFont, float scaleFactor) {
        if (initialized) {
            return;
        }

        // Create the atlas image
        BufferedImage atlasImage = new BufferedImage(ATLAS_SIZE, ATLAS_SIZE, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = atlasImage.createGraphics();

        // Enable antialiasing for smooth outline rendering
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setColor(Color.WHITE);

        // Track cursor position for packing
        int cursorX = GLYPH_PADDING;
        int cursorY = GLYPH_PADDING;
        int rowHeight = 0;

        // Pack all font sizes into the atlas
        for (FontSize fontSize : FontSize.values()) {
            Map<Character, GlyphInfo> glyphs = new HashMap<>();
            glyphsBySize.put(fontSize, glyphs);

            // Scale the font size (cap at 32pt max to avoid atlas overflow at high DPI)
            int scaledSize = Math.max(8, Math.min(32, Math.round(fontSize.getPoints() * scaleFactor)));
            // Use bold for SMALL and MEDIUM to improve readability at small sizes
            int style = (fontSize == FontSize.SMALL || fontSize == FontSize.MEDIUM) ? Font.BOLD : Font.PLAIN;
            Font scaledFont = baseFont.deriveFont(style, (float) scaledSize);
            g2d.setFont(scaledFont);

            // Get font metrics
            FontMetrics metrics = g2d.getFontMetrics();
            FontRenderContext frc = g2d.getFontRenderContext();

            int lineHeight = metrics.getHeight();
            int ascent = metrics.getAscent();
            lineHeightBySize.put(fontSize, lineHeight);
            ascentBySize.put(fontSize, ascent);

            // Calculate glyph dimensions
            int[] widths = new int[LAST_CHAR - FIRST_CHAR + 1];
            int[] heights = new int[LAST_CHAR - FIRST_CHAR + 1];
            int maxGlyphHeight = 0;

            for (int c = FIRST_CHAR; c <= LAST_CHAR; c++) {
                char ch = (char) c;
                GlyphVector gv = scaledFont.createGlyphVector(frc, String.valueOf(ch));
                Rectangle2D bounds = gv.getVisualBounds();

                int w = (int) Math.ceil(bounds.getWidth()) + GLYPH_PADDING * 2;
                int h = (int) Math.ceil(bounds.getHeight()) + GLYPH_PADDING * 2;
                widths[c - FIRST_CHAR] = Math.max(w, metrics.charWidth(ch));
                heights[c - FIRST_CHAR] = Math.max(h, lineHeight);
                maxGlyphHeight = Math.max(maxGlyphHeight, heights[c - FIRST_CHAR]);
            }

            // Pack glyphs
            for (int c = FIRST_CHAR; c <= LAST_CHAR; c++) {
                char ch = (char) c;
                int glyphWidth = widths[c - FIRST_CHAR];
                int glyphHeight = heights[c - FIRST_CHAR];

                // Check if we need to start a new row
                if (cursorX + glyphWidth + GLYPH_PADDING > ATLAS_SIZE) {
                    cursorX = GLYPH_PADDING;
                    cursorY += rowHeight + GLYPH_PADDING;
                    rowHeight = 0;
                }

                // Check if we've run out of space
                if (cursorY + glyphHeight > ATLAS_SIZE) {
                    LOGGER.warning("Glyph atlas is full at font size " + fontSize +
                                   ", some characters may be missing");
                    break;
                }

                // Draw the glyph
                int drawX = cursorX;
                int drawY = cursorY + ascent;
                g2d.drawString(String.valueOf(ch), drawX, drawY);

                // Calculate UV coordinates (normalized)
                // Swap v0/v1 so glyph renders right-side up
                float u0 = (float) cursorX / ATLAS_SIZE;
                float v0 = (float) (cursorY + glyphHeight) / ATLAS_SIZE;
                float u1 = (float) (cursorX + glyphWidth) / ATLAS_SIZE;
                float v1 = (float) cursorY / ATLAS_SIZE;

                // Store glyph info
                GlyphInfo info = new GlyphInfo(
                        u0, v0, u1, v1,
                        glyphWidth, glyphHeight,
                        0, 0,
                        metrics.charWidth(ch)
                );
                glyphs.put(ch, info);

                cursorX += glyphWidth + GLYPH_PADDING;
                rowHeight = Math.max(rowHeight, glyphHeight);
            }
        }

        g2d.dispose();

        // Upload to OpenGL texture
        byte[] pixels = ((DataBufferByte) atlasImage.getRaster().getDataBuffer()).getData();
        ByteBuffer buffer = MemoryUtil.memAlloc(pixels.length);
        buffer.put(pixels);
        buffer.flip();

        textureId = glGenTextures();

        glBindTexture(GL_TEXTURE_2D, textureId);
        // Use LINEAR filtering for smooth antialiased text
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        // Use GL_RED for single-channel grayscale (GL_LUMINANCE removed in core profile)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R8,
                ATLAS_SIZE, ATLAS_SIZE, 0,
                GL_RED, GL_UNSIGNED_BYTE, buffer);

        glBindTexture(GL_TEXTURE_2D, 0);

        MemoryUtil.memFree(buffer);

        int totalGlyphs = glyphsBySize.values().stream().mapToInt(Map::size).sum();
        initialized = true;
        LOGGER.info("Glyph atlas initialized: " + totalGlyphs + " glyphs across " +
                    FontSize.values().length + " sizes, texture ID " + textureId);
    }

    /**
     * Initializes the glyph atlas with the specified font (single size, for backwards compatibility).
     * Should be called once during startup with a valid GL context.
     */
    public void init(Font font) {
        init(font, 1.0f);
    }

    /**
     * Gets the glyph info for a character at a specific font size.
     * Returns null for unsupported characters.
     */
    public GlyphInfo getGlyph(char c, FontSize fontSize) {
        Map<Character, GlyphInfo> glyphs = glyphsBySize.get(fontSize);
        if (glyphs == null) {
            return null;
        }
        return glyphs.get(c);
    }

    /**
     * Gets the glyph info for a character using the default (MEDIUM) font size.
     * Returns null for unsupported characters.
     */
    public GlyphInfo getGlyph(char c) {
        return getGlyph(c, FontSize.MEDIUM);
    }

    /**
     * Measures the width of a string in pixels at a specific font size.
     */
    public int measureTextWidth(String text, FontSize fontSize) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        Map<Character, GlyphInfo> glyphs = glyphsBySize.get(fontSize);
        if (glyphs == null) {
            return 0;
        }
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            GlyphInfo info = glyphs.get(text.charAt(i));
            if (info != null) {
                width += info.advance;
            }
        }
        return width;
    }

    /**
     * Measures the width of a string in pixels using the default (MEDIUM) font size.
     */
    public int measureTextWidth(String text) {
        return measureTextWidth(text, FontSize.MEDIUM);
    }

    /**
     * Gets the line height for a specific font size.
     */
    public int getLineHeight(FontSize fontSize) {
        Integer height = lineHeightBySize.get(fontSize);
        return height != null ? height : 12;
    }

    /**
     * Gets the line height for the default (MEDIUM) font size.
     */
    public int getLineHeight() {
        return getLineHeight(FontSize.MEDIUM);
    }

    /**
     * Gets the ascent for a specific font size.
     */
    public int getAscent(FontSize fontSize) {
        Integer ascent = ascentBySize.get(fontSize);
        return ascent != null ? ascent : 10;
    }

    /**
     * Gets the ascent for the default (MEDIUM) font size.
     */
    public int getAscent() {
        return getAscent(FontSize.MEDIUM);
    }

    /**
     * Gets the OpenGL texture ID.
     */
    public int getTextureId() {
        return textureId;
    }

    /**
     * Gets the atlas texture size.
     */
    public int getAtlasSize() {
        return ATLAS_SIZE;
    }

    /**
     * Checks if the atlas has been initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Cleans up OpenGL resources.
     */
    public void cleanup() {
        if (textureId != 0) {
            glDeleteTextures(textureId);
        }
        textureId = 0;
        glyphsBySize.clear();
        lineHeightBySize.clear();
        ascentBySize.clear();
        initialized = false;
    }
}
