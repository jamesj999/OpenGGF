package com.openggf.graphics;

import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Utility for loading PNG images from the classpath into OpenGL textures.
 * Uses RGBA format with GL_NEAREST filtering (pixel-art friendly).
 */
public class PngTextureLoader {

    private static int lastWidth;
    private static int lastHeight;

    /**
     * Loads a PNG from the classpath and creates an OpenGL texture.
     *
     * @param resourcePath classpath path (e.g. "titlescreen/background.png")
     * @return OpenGL texture ID
     * @throws IOException if the resource cannot be found or read
     */
    public static int loadTexture(String resourcePath) throws IOException {
        BufferedImage image;
        try (InputStream is = PngTextureLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("PNG resource not found: " + resourcePath);
            }
            image = ImageIO.read(is);
        }

        int width = image.getWidth();
        int height = image.getHeight();
        lastWidth = width;
        lastHeight = height;

        // Convert to RGBA ByteBuffer, Y-flipped for OpenGL (bottom-left origin)
        ByteBuffer buffer = MemoryUtil.memAlloc(width * height * 4);
        for (int y = 0; y < height; y++) {
            int srcY = height - 1 - y; // flip Y
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, srcY);
                buffer.put((byte) ((argb >> 16) & 0xFF)); // R
                buffer.put((byte) ((argb >> 8) & 0xFF));  // G
                buffer.put((byte) (argb & 0xFF));          // B
                buffer.put((byte) ((argb >> 24) & 0xFF)); // A
            }
        }
        buffer.flip();

        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);

        MemoryUtil.memFree(buffer);

        return textureId;
    }

    public static int getLastWidth() {
        return lastWidth;
    }

    public static int getLastHeight() {
        return lastHeight;
    }

    public static void deleteTexture(int textureId) {
        if (textureId != 0) {
            glDeleteTextures(textureId);
        }
    }
}
