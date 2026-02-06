package uk.co.jamesj999.sonic.graphics;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/**
 * GPU renderer that draws a tilemap texture into the current framebuffer.
 */
public class TilemapGpuRenderer {
    private static final Logger LOGGER = Logger.getLogger(TilemapGpuRenderer.class.getName());

    public enum Layer {
        BACKGROUND,
        FOREGROUND
    }

    private TilemapShaderProgram shader;
    private final TilemapTexture backgroundTexture = new TilemapTexture();
    private final TilemapTexture foregroundTexture = new TilemapTexture();
    private final PatternLookupBuffer patternLookup = new PatternLookupBuffer();
    private final QuadRenderer quadRenderer = new QuadRenderer();

    // Dummy 1x1 texture used as fallback when no real texture is available.
    // This prevents macOS OpenGL driver warnings about unbound samplers.
    private int dummyTextureId = 0;

    private byte[] backgroundData;
    private int backgroundWidthTiles;
    private int backgroundHeightTiles;
    private boolean backgroundDirty = false;

    private byte[] foregroundData;
    private int foregroundWidthTiles;
    private int foregroundHeightTiles;
    private boolean foregroundDirty = false;

    private byte[] lookupData;
    private int lookupSize;
    private boolean lookupDirty = false;

    public void init(String shaderPath) throws IOException {
        if (shader == null) {
            shader = new TilemapShaderProgram(shaderPath);
            shader.cacheUniformLocations();

            // Create a dummy 1x1 texture to bind to unused sampler units.
            // This prevents macOS OpenGL driver warnings about unbound samplers
            // when the shader is first validated.
            dummyTextureId = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, dummyTextureId);
            ByteBuffer pixel = MemoryUtil.memAlloc(4);
            try {
                pixel.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0).flip();
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            } finally {
                MemoryUtil.memFree(pixel);
            }
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glBindTexture(GL_TEXTURE_2D, 0);

            LOGGER.info("Tilemap GPU renderer initialized.");
        }
        quadRenderer.init();
    }

    public void setTilemapData(Layer layer, byte[] data, int widthTiles, int heightTiles) {
        if (layer == Layer.FOREGROUND) {
            this.foregroundData = data;
            this.foregroundWidthTiles = widthTiles;
            this.foregroundHeightTiles = heightTiles;
            this.foregroundDirty = true;
        } else {
            this.backgroundData = data;
            this.backgroundWidthTiles = widthTiles;
            this.backgroundHeightTiles = heightTiles;
            this.backgroundDirty = true;
        }
    }

    public void setPatternLookupData(byte[] data, int size) {
        this.lookupData = data;
        this.lookupSize = size;
        this.lookupDirty = true;
    }

    public void render(
            Layer layer,
            int windowWidth,
            int windowHeight,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            float worldOffsetX,
            float worldOffsetY,
            int atlasWidth,
            int atlasHeight,
            int atlasTextureId,
            int paletteTextureId,
            int underwaterPaletteTextureId,
            int priorityPass,
            boolean wrapY,
            boolean maskOutput,
            boolean useUnderwaterPalette,
            float waterlineScreenY) {
        byte[] tilemapData = layer == Layer.FOREGROUND ? foregroundData : backgroundData;
        int tilemapWidthTiles = layer == Layer.FOREGROUND ? foregroundWidthTiles : backgroundWidthTiles;
        int tilemapHeightTiles = layer == Layer.FOREGROUND ? foregroundHeightTiles : backgroundHeightTiles;
        TilemapTexture tilemapTexture = layer == Layer.FOREGROUND ? foregroundTexture : backgroundTexture;

        if (shader == null || tilemapData == null || lookupData == null) {
            return;
        }

        if (layer == Layer.FOREGROUND) {
            if (foregroundDirty) {
                tilemapTexture.upload(tilemapData, tilemapWidthTiles, tilemapHeightTiles);
                foregroundDirty = false;
            }
        } else if (backgroundDirty) {
            tilemapTexture.upload(tilemapData, tilemapWidthTiles, tilemapHeightTiles);
            backgroundDirty = false;
        }
        if (lookupDirty) {
            patternLookup.upload(lookupData, lookupSize);
            lookupDirty = false;
        }

        shader.use();
        shader.cacheUniformLocations();

        shader.setTextureUnits(0, 1, 2, 3, 4);
        shader.setTilemapDimensions(tilemapWidthTiles, tilemapHeightTiles);
        shader.setAtlasDimensions(atlasWidth, atlasHeight);
        shader.setLookupSize(lookupSize);
        shader.setWindowDimensions(windowWidth, windowHeight);
        shader.setViewport(viewportX, viewportY, viewportWidth, viewportHeight);
        shader.setWorldOffset(worldOffsetX, worldOffsetY);
        shader.setWrapY(wrapY);
        shader.setPriorityPass(priorityPass);
        shader.setMaskOutput(maskOutput);
        shader.setWaterSplit(useUnderwaterPalette, waterlineScreenY);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, tilemapTexture.getTextureId());

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_1D, patternLookup.getTextureId());

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, atlasTextureId);

        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_2D, paletteTextureId);

        glActiveTexture(GL_TEXTURE4);
        // Use dummy texture when no underwater palette is available to avoid
        // macOS OpenGL driver warnings about unbound samplers.
        glBindTexture(GL_TEXTURE_2D, underwaterPaletteTextureId != 0 ? underwaterPaletteTextureId : dummyTextureId);

        quadRenderer.draw(0, 0, windowWidth, windowHeight);

        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE4);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_1D, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, 0);

        shader.stop();
    }

    public void cleanup() {
        if (shader != null) {
            shader.cleanup();
            shader = null;
        }
        if (dummyTextureId != 0) {
            glDeleteTextures(dummyTextureId);
            dummyTextureId = 0;
        }
        backgroundTexture.cleanup();
        foregroundTexture.cleanup();
        patternLookup.cleanup();
        quadRenderer.cleanup();
    }
}
