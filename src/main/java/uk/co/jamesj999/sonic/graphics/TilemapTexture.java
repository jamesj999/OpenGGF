package uk.co.jamesj999.sonic.graphics;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * 2D texture storing tile descriptors for GPU tilemap rendering.
 */
public class TilemapTexture {
    private int textureId = 0;
    private int widthTiles = 0;
    private int heightTiles = 0;

    public void init(int widthTiles, int heightTiles) {
        if (widthTiles <= 0 || heightTiles <= 0) {
            return;
        }
        if (textureId == 0) {
            textureId = glGenTextures();
        }
        this.widthTiles = widthTiles;
        this.heightTiles = heightTiles;

        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, widthTiles, heightTiles, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void upload(byte[] data, int widthTiles, int heightTiles) {
        if (data == null || widthTiles <= 0 || heightTiles <= 0) {
            return;
        }
        if (textureId == 0 || this.widthTiles != widthTiles || this.heightTiles != heightTiles) {
            init(widthTiles, heightTiles);
        }
        ByteBuffer buffer = MemoryUtil.memAlloc(data.length);
        try {
            buffer.put(data);
            buffer.flip();
            glBindTexture(GL_TEXTURE_2D, textureId);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, widthTiles, heightTiles,
                    GL_RGBA, GL_UNSIGNED_BYTE, buffer);
            glBindTexture(GL_TEXTURE_2D, 0);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    public int getTextureId() {
        return textureId;
    }

    public int getWidthTiles() {
        return widthTiles;
    }

    public int getHeightTiles() {
        return heightTiles;
    }

    public void cleanup() {
        if (textureId != 0) {
            glDeleteTextures(textureId);
        }
        textureId = 0;
        widthTiles = 0;
        heightTiles = 0;
    }
}
