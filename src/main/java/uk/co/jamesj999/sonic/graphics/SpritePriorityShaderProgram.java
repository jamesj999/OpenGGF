package uk.co.jamesj999.sonic.graphics;

import com.jogamp.opengl.GL2;
import java.io.IOException;

/**
 * Shader program for sprite-to-tile priority compositing.
 *
 * This shader extends the standard hedgehog pattern shader to support
 * ROM-accurate sprite-to-tile layering. Low-priority sprites (isHighPriority=false)
 * are hidden behind high-priority foreground tiles, while high-priority sprites
 * always render on top of all tiles.
 *
 * This allows sprite-to-sprite ordering (via buckets 0-7) to work independently
 * of sprite-to-tile layering (via isHighPriority flag).
 */
public class SpritePriorityShaderProgram extends ShaderProgram {

    // Uniform locations for priority compositing
    private int tilePriorityTextureLocation = -1;
    private int spriteHighPriorityLocation = -1;
    private int screenSizeLocation = -1;
    private int viewportOffsetLocation = -1;

    public SpritePriorityShaderProgram(GL2 gl, String fragmentShaderPath) throws IOException {
        super(gl, fragmentShaderPath);
    }

    @Override
    public void cacheUniformLocations(GL2 gl) {
        // Cache base uniforms (Palette, IndexedColorTexture, PaletteLine)
        super.cacheUniformLocations(gl);

        int programId = getProgramId();

        // Cache priority-specific uniforms
        tilePriorityTextureLocation = gl.glGetUniformLocation(programId, "TilePriorityTexture");
        spriteHighPriorityLocation = gl.glGetUniformLocation(programId, "SpriteHighPriority");
        screenSizeLocation = gl.glGetUniformLocation(programId, "ScreenSize");
        viewportOffsetLocation = gl.glGetUniformLocation(programId, "ViewportOffset");
    }

    /**
     * Get the uniform location for the tile priority texture sampler.
     */
    public int getTilePriorityTextureLocation() {
        return tilePriorityTextureLocation;
    }

    /**
     * Set the tile priority texture sampler unit.
     */
    public void setTilePriorityTexture(GL2 gl, int textureUnit) {
        if (tilePriorityTextureLocation != -1) {
            gl.glUniform1i(tilePriorityTextureLocation, textureUnit);
        }
    }

    /**
     * Set whether the current sprite has high priority (appears above all tiles).
     *
     * @param gl           OpenGL context
     * @param highPriority true if sprite should appear above all tiles, false if it
     *                     should appear behind high-priority tiles
     */
    public void setSpriteHighPriority(GL2 gl, boolean highPriority) {
        if (spriteHighPriorityLocation != -1) {
            gl.glUniform1i(spriteHighPriorityLocation, highPriority ? 1 : 0);
        }
    }

    /**
     * Set the screen dimensions for coordinate lookup in the priority texture.
     */
    public void setScreenSize(GL2 gl, float width, float height) {
        if (screenSizeLocation != -1) {
            gl.glUniform2f(screenSizeLocation, width, height);
        }
    }

    /**
     * Set the viewport offset in window coordinates.
     * This is needed because gl_FragCoord is in window coordinates, not viewport-local.
     * When the viewport is letterboxed/centered, the offset is non-zero.
     */
    public void setViewportOffset(GL2 gl, float x, float y) {
        if (viewportOffsetLocation != -1) {
            gl.glUniform2f(viewportOffsetLocation, x, y);
        }
    }
}
