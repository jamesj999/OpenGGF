package com.openggf.graphics;

import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static org.lwjgl.opengl.GL11.*;

/**
 * Utility class for framebuffer capture and image comparison.
 * Used by visual regression testing to capture screenshots and compare them against reference images.
 */
public final class ScreenshotCapture {

    private ScreenshotCapture() {
        // Utility class
    }

    /**
     * Result of comparing two images.
     */
    public record ComparisonResult(
            boolean matched,
            int diffCount,
            int maxDiff,
            int firstDiffX,
            int firstDiffY
    ) {
        public static ComparisonResult success() {
            return new ComparisonResult(true, 0, 0, -1, -1);
        }

        public static ComparisonResult failure(int diffCount, int maxDiff, int firstDiffX, int firstDiffY) {
            return new ComparisonResult(false, diffCount, maxDiff, firstDiffX, firstDiffY);
        }
    }

    /**
     * Capture the current OpenGL framebuffer as a BufferedImage.
     * Reads from the back buffer and flips the Y-axis (OpenGL origin is bottom-left).
     *
     * @param width  Width of the capture area in pixels
     * @param height Height of the capture area in pixels
     * @return A BufferedImage containing the captured framebuffer contents
     */
    public static BufferedImage captureFramebuffer(int width, int height) {
        // Allocate buffer for RGBA data using LWJGL's MemoryUtil
        ByteBuffer buffer = MemoryUtil.memAlloc(width * height * 4);

        try {
            // Read pixels from back buffer
            glReadBuffer(GL_BACK);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

            // Create BufferedImage
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

            // Copy pixels from buffer to image, flipping Y coordinate
            // OpenGL origin is bottom-left, BufferedImage origin is top-left
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    // Read from flipped Y position in buffer
                    int srcY = height - 1 - y;
                    int srcIndex = (srcY * width + x) * 4;

                    int r = buffer.get(srcIndex) & 0xFF;
                    int g = buffer.get(srcIndex + 1) & 0xFF;
                    int b = buffer.get(srcIndex + 2) & 0xFF;
                    int a = buffer.get(srcIndex + 3) & 0xFF;

                    int argb = (a << 24) | (r << 16) | (g << 8) | b;
                    image.setRGB(x, y, argb);
                }
            }

            return image;
        } finally {
            // Free the native memory
            MemoryUtil.memFree(buffer);
        }
    }

    /**
     * Compare two images for equality within a per-channel tolerance.
     *
     * @param reference The reference (expected) image
     * @param current   The current (actual) image
     * @param tolerance Per-channel tolerance for pixel comparison (0 = exact match required)
     * @return ComparisonResult with match status and diff details
     */
    public static ComparisonResult imagesMatch(BufferedImage reference, BufferedImage current, int tolerance) {
        if (reference.getWidth() != current.getWidth() || reference.getHeight() != current.getHeight()) {
            return ComparisonResult.failure(
                    reference.getWidth() * reference.getHeight(),
                    255,
                    0, 0
            );
        }

        int diffCount = 0;
        int maxDiff = 0;
        int firstDiffX = -1;
        int firstDiffY = -1;

        for (int y = 0; y < reference.getHeight(); y++) {
            for (int x = 0; x < reference.getWidth(); x++) {
                int refPixel = reference.getRGB(x, y);
                int curPixel = current.getRGB(x, y);

                // Extract ARGB components
                int refA = (refPixel >> 24) & 0xFF;
                int refR = (refPixel >> 16) & 0xFF;
                int refG = (refPixel >> 8) & 0xFF;
                int refB = refPixel & 0xFF;

                int curA = (curPixel >> 24) & 0xFF;
                int curR = (curPixel >> 16) & 0xFF;
                int curG = (curPixel >> 8) & 0xFF;
                int curB = curPixel & 0xFF;

                // Calculate per-channel differences
                int diffA = Math.abs(refA - curA);
                int diffR = Math.abs(refR - curR);
                int diffG = Math.abs(refG - curG);
                int diffB = Math.abs(refB - curB);

                int pixelMaxDiff = Math.max(Math.max(diffA, diffR), Math.max(diffG, diffB));

                if (pixelMaxDiff > tolerance) {
                    diffCount++;
                    if (pixelMaxDiff > maxDiff) {
                        maxDiff = pixelMaxDiff;
                    }
                    if (firstDiffX < 0) {
                        firstDiffX = x;
                        firstDiffY = y;
                    }
                }
            }
        }

        if (diffCount > 0) {
            return ComparisonResult.failure(diffCount, maxDiff, firstDiffX, firstDiffY);
        }
        return ComparisonResult.success();
    }

    /**
     * Save a BufferedImage as a PNG file.
     *
     * @param image The image to save
     * @param path  The file path to save to
     * @throws IOException If the file cannot be written
     */
    public static void savePNG(BufferedImage image, Path path) throws IOException {
        ImageIO.write(image, "PNG", path.toFile());
    }

    /**
     * Load a PNG file as a BufferedImage.
     *
     * @param path The file path to load from
     * @return The loaded BufferedImage
     * @throws IOException If the file cannot be read
     */
    public static BufferedImage loadPNG(Path path) throws IOException {
        return ImageIO.read(path.toFile());
    }

    /**
     * Create a visual diff image showing the differences between two images.
     * Pixels that match are dimmed, pixels that differ are highlighted in red.
     *
     * @param reference The reference image
     * @param current   The current image
     * @param tolerance Per-channel tolerance
     * @return A new image highlighting the differences
     */
    public static BufferedImage createDiffImage(BufferedImage reference, BufferedImage current, int tolerance) {
        int width = Math.max(reference.getWidth(), current.getWidth());
        int height = Math.max(reference.getHeight(), current.getHeight());
        BufferedImage diff = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Handle size mismatch
                if (x >= reference.getWidth() || y >= reference.getHeight() ||
                        x >= current.getWidth() || y >= current.getHeight()) {
                    // Out of bounds in one image - mark as red
                    diff.setRGB(x, y, 0xFFFF0000);
                    continue;
                }

                int refPixel = reference.getRGB(x, y);
                int curPixel = current.getRGB(x, y);

                int refR = (refPixel >> 16) & 0xFF;
                int refG = (refPixel >> 8) & 0xFF;
                int refB = refPixel & 0xFF;

                int curR = (curPixel >> 16) & 0xFF;
                int curG = (curPixel >> 8) & 0xFF;
                int curB = curPixel & 0xFF;

                int diffR = Math.abs(refR - curR);
                int diffG = Math.abs(refG - curG);
                int diffB = Math.abs(refB - curB);

                int maxDiff = Math.max(Math.max(diffR, diffG), diffB);

                if (maxDiff > tolerance) {
                    // Highlight differences in red, intensity based on diff magnitude
                    int intensity = Math.min(255, 128 + maxDiff);
                    diff.setRGB(x, y, 0xFF000000 | (intensity << 16));
                } else {
                    // Match - show dimmed version of current image
                    int dimR = curR / 4;
                    int dimG = curG / 4;
                    int dimB = curB / 4;
                    diff.setRGB(x, y, 0xFF000000 | (dimR << 16) | (dimG << 8) | dimB);
                }
            }
        }

        return diff;
    }
}
