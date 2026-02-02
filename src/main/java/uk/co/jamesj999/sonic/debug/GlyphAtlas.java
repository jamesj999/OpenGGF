package uk.co.jamesj999.sonic.debug;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.GLBuffers;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Pre-renders ASCII glyphs to a texture atlas for GPU-accelerated text rendering.
 * Uses a 512x512 GL_ALPHA texture with row-based packing.
 */
public class GlyphAtlas {
    private static final Logger LOGGER = Logger.getLogger(GlyphAtlas.class.getName());

    private static final int ATLAS_SIZE = 512;
    private static final int FIRST_CHAR = 32;  // Space
    private static final int LAST_CHAR = 126;  // Tilde
    private static final int GLYPH_PADDING = 2;

    private int textureId;
    private final Map<Character, GlyphInfo> glyphs = new HashMap<>();
    private int lineHeight;
    private int ascent;
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
     * Initializes the glyph atlas with the specified font.
     * Should be called once during startup with a valid GL context.
     */
    public void init(GL2 gl, Font font) {
        if (initialized || gl == null) {
            return;
        }

        // Create a temporary image for rendering glyphs
        BufferedImage tempImage = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D tempG2d = tempImage.createGraphics();
        tempG2d.setFont(font);
        FontMetrics metrics = tempG2d.getFontMetrics();
        FontRenderContext frc = tempG2d.getFontRenderContext();

        this.lineHeight = metrics.getHeight();
        this.ascent = metrics.getAscent();

        // Calculate required space for all glyphs
        int totalGlyphs = LAST_CHAR - FIRST_CHAR + 1;
        int[] widths = new int[totalGlyphs];
        int[] heights = new int[totalGlyphs];
        int maxHeight = 0;

        for (int c = FIRST_CHAR; c <= LAST_CHAR; c++) {
            char ch = (char) c;
            GlyphVector gv = font.createGlyphVector(frc, String.valueOf(ch));
            Rectangle2D bounds = gv.getVisualBounds();

            int w = (int) Math.ceil(bounds.getWidth()) + GLYPH_PADDING * 2;
            int h = (int) Math.ceil(bounds.getHeight()) + GLYPH_PADDING * 2;
            widths[c - FIRST_CHAR] = Math.max(w, metrics.charWidth(ch));
            heights[c - FIRST_CHAR] = Math.max(h, lineHeight);
            maxHeight = Math.max(maxHeight, heights[c - FIRST_CHAR]);
        }

        tempG2d.dispose();

        // Create the atlas image
        BufferedImage atlasImage = new BufferedImage(ATLAS_SIZE, ATLAS_SIZE, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = atlasImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g2d.setFont(font);
        g2d.setColor(Color.WHITE);

        // Pack glyphs row by row
        int cursorX = GLYPH_PADDING;
        int cursorY = GLYPH_PADDING;
        int rowHeight = maxHeight;

        for (int c = FIRST_CHAR; c <= LAST_CHAR; c++) {
            char ch = (char) c;
            int glyphWidth = widths[c - FIRST_CHAR];
            int glyphHeight = heights[c - FIRST_CHAR];

            // Check if we need to start a new row
            if (cursorX + glyphWidth + GLYPH_PADDING > ATLAS_SIZE) {
                cursorX = GLYPH_PADDING;
                cursorY += rowHeight + GLYPH_PADDING;
            }

            // Check if we've run out of space
            if (cursorY + glyphHeight > ATLAS_SIZE) {
                LOGGER.warning("Glyph atlas is full, some characters may be missing");
                break;
            }

            // Draw the glyph
            int drawX = cursorX;
            int drawY = cursorY + ascent;
            g2d.drawString(String.valueOf(ch), drawX, drawY);

            // Calculate UV coordinates (normalized)
            // Flip V coordinates for OpenGL (texture Y=0 at bottom, but BufferedImage Y=0 at top)
            float u0 = (float) cursorX / ATLAS_SIZE;
            float v0 = 1.0f - (float) cursorY / ATLAS_SIZE;
            float u1 = (float) (cursorX + glyphWidth) / ATLAS_SIZE;
            float v1 = 1.0f - (float) (cursorY + glyphHeight) / ATLAS_SIZE;

            // Store glyph info
            GlyphInfo info = new GlyphInfo(
                    u0, v0, u1, v1,
                    glyphWidth, glyphHeight,
                    0, 0,
                    metrics.charWidth(ch)
            );
            glyphs.put(ch, info);

            cursorX += glyphWidth + GLYPH_PADDING;
        }

        g2d.dispose();

        // Upload to OpenGL texture
        byte[] pixels = ((DataBufferByte) atlasImage.getRaster().getDataBuffer()).getData();
        ByteBuffer buffer = GLBuffers.newDirectByteBuffer(pixels.length);
        buffer.put(pixels);
        buffer.flip();

        int[] textures = new int[1];
        gl.glGenTextures(1, textures, 0);
        textureId = textures[0];

        gl.glBindTexture(GL2.GL_TEXTURE_2D, textureId);
        gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_NEAREST);
        gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_NEAREST);
        gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP_TO_EDGE);

        // Use GL_LUMINANCE for single-channel grayscale (GLSL 1.10 compatible)
        gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_LUMINANCE,
                ATLAS_SIZE, ATLAS_SIZE, 0,
                GL2.GL_LUMINANCE, GL2.GL_UNSIGNED_BYTE, buffer);

        gl.glBindTexture(GL2.GL_TEXTURE_2D, 0);

        initialized = true;
        LOGGER.info("Glyph atlas initialized: " + glyphs.size() + " glyphs, texture ID " + textureId);
    }

    /**
     * Gets the glyph info for a character.
     * Returns null for unsupported characters.
     */
    public GlyphInfo getGlyph(char c) {
        return glyphs.get(c);
    }

    /**
     * Measures the width of a string in pixels.
     */
    public int measureTextWidth(String text) {
        if (text == null || text.isEmpty()) {
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
     * Gets the line height for the font.
     */
    public int getLineHeight() {
        return lineHeight;
    }

    /**
     * Gets the ascent (distance from baseline to top of tallest glyph).
     */
    public int getAscent() {
        return ascent;
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
    public void cleanup(GL2 gl) {
        if (gl != null && textureId != 0) {
            gl.glDeleteTextures(1, new int[]{textureId}, 0);
        }
        textureId = 0;
        glyphs.clear();
        initialized = false;
    }
}
