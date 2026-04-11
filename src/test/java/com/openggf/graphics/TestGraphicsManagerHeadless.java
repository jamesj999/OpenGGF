package com.openggf.graphics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for GraphicsManager headless mode.
 * Verifies that GL operations are properly skipped when running
 * without an OpenGL context.
 *
 * Note: These tests require LWJGL native libraries to be loadable.
 * On headless CI systems without xvfb, these tests will be skipped.
 */
public class TestGraphicsManagerHeadless {

    private GraphicsManager graphicsManager;
    private static boolean lwjglAvailable = false;

    @BeforeAll
    public static void checkLwjglAvailable() {
        // These tests use GraphicsManager in headless mode, which should NOT require
        // LWJGL natives. The headless mode avoids all GL calls and lazy-allocates
        // native buffers only when needed. We just check if the LWJGL classes are on
        // the classpath (without triggering native library loading).
        try {
            Class.forName("org.lwjgl.system.MemoryUtil");
            lwjglAvailable = true;
        } catch (ClassNotFoundException e) {
            System.err.println("LWJGL classes not on classpath, skipping headless tests: " + e.getMessage());
            lwjglAvailable = false;
        }
    }

    @BeforeEach
    public void setUp() {
        assumeTrue(lwjglAvailable, "LWJGL natives not available");
        // Destroy and recreate the singleton to get a truly fresh instance.
        // resetState() preserves headlessMode, which causes test ordering
        // failures when a prior test enables headless mode on the singleton.
        GraphicsManager.destroyForReinit();
        graphicsManager = GraphicsManager.getInstance();
    }

    @AfterEach
    public void tearDown() {
        GraphicsManager.destroyForReinit();
    }

    // ==================== Headless Mode Flag Tests ====================

    @Test
    public void testDefaultModeIsNotHeadless() {
        assertFalse(graphicsManager.isHeadlessMode(), "Default mode should not be headless");
    }

    @Test
    public void testSetHeadlessMode() {
        graphicsManager.setHeadlessMode(true);
        assertTrue(graphicsManager.isHeadlessMode(), "Headless mode should be enabled");

        graphicsManager.setHeadlessMode(false);
        assertFalse(graphicsManager.isHeadlessMode(), "Headless mode should be disabled");
    }

    @Test
    public void testInitHeadlessEnablesHeadlessMode() {
        graphicsManager.initHeadless();
        assertTrue(graphicsManager.isHeadlessMode(), "initHeadless should enable headless mode");
    }

    @Test
    public void testInitHeadlessSetsGlNotInitialized() {
        graphicsManager.initHeadless();
        assertFalse(graphicsManager.isGlInitialized(), "GL should not be initialized in headless mode");
    }

    // ==================== Pattern Caching Tests ====================

    @Test
    public void testCachePatternTextureInHeadlessMode() {
        graphicsManager.initHeadless();

        // Create a simple test pattern
        Pattern pattern = createTestPattern();

        // This should not throw even without a GL context
        graphicsManager.cachePatternTexture(pattern, 42);

        // Pattern should be tracked (with dummy ID -1)
        Integer textureId = graphicsManager.getPatternTextureId(42);
        assertNotNull(textureId, "Pattern should be tracked in headless mode");
        assertEquals(Integer.valueOf(-1), textureId, "Pattern texture ID should be -1 in headless mode");
    }

    @Test
    public void testCacheMultiplePatternsInHeadlessMode() {
        graphicsManager.initHeadless();
        Pattern pattern = createTestPattern();

        graphicsManager.cachePatternTexture(pattern, 0);
        graphicsManager.cachePatternTexture(pattern, 1);
        graphicsManager.cachePatternTexture(pattern, 100);

        assertNotNull(graphicsManager.getPatternTextureId(0), "Pattern 0 should be tracked");
        assertNotNull(graphicsManager.getPatternTextureId(1), "Pattern 1 should be tracked");
        assertNotNull(graphicsManager.getPatternTextureId(100), "Pattern 100 should be tracked");
    }

    @Test
    public void testUpdatePatternTextureInHeadlessMode() {
        graphicsManager.initHeadless();
        Pattern pattern = createTestPattern();

        // Update should work without error and track the pattern
        graphicsManager.updatePatternTexture(pattern, 5);

        assertNotNull(graphicsManager.getPatternTextureId(5), "Pattern should be tracked after update in headless mode");
    }

    // ==================== Palette Caching Tests ====================

    @Test
    public void testCachePaletteTextureInHeadlessMode() {
        graphicsManager.initHeadless();
        Palette palette = createTestPalette();

        // This should not throw even without a GL context
        graphicsManager.cachePaletteTexture(palette, 0);

        // Combined palette texture ID should be null in headless mode
        // (we use dummy tracking via paletteTextureMap)
        assertNull(graphicsManager.getCombinedPaletteTextureId(), "Combined palette texture should be null in headless mode");
    }

    // ==================== Flush Tests ====================

    @Test
    public void testFlushInHeadlessModeDoesNotThrow() {
        graphicsManager.initHeadless();

        // Register some commands
        graphicsManager.registerCommand((cX, cY, cW, cH) -> {
            // This would crash without a GL context if flush actually executed it
            throw new RuntimeException("Command should not execute in headless mode");
        });

        // Flush should clear commands without executing them
        graphicsManager.flush();
    }

    @Test
    public void testFlushClearsCommandsInHeadlessMode() {
        graphicsManager.initHeadless();

        // Register a command
        graphicsManager.registerCommand((cX, cY, cW, cH) -> {});

        // Flush should clear
        graphicsManager.flush();

        // Flush again - should not throw (commands already cleared)
        graphicsManager.flush();
    }

    // ==================== Batching Tests ====================

    @Test
    public void testBeginPatternBatchInHeadlessModeDoesNotThrow() {
        graphicsManager.initHeadless();

        // Should not throw or crash
        graphicsManager.beginPatternBatch();
    }

    @Test
    public void testFlushPatternBatchInHeadlessModeDoesNotThrow() {
        graphicsManager.initHeadless();

        // Should not throw or crash
        graphicsManager.flushPatternBatch();
    }

    @Test
    public void testBatchingOperationsInHeadlessMode() {
        graphicsManager.initHeadless();

        // Full batching cycle should work
        graphicsManager.beginPatternBatch();
        graphicsManager.flushPatternBatch();
        graphicsManager.beginPatternBatch();
        graphicsManager.flushPatternBatch();
    }

    // ==================== Cleanup Tests ====================

    @Test
    public void testCleanupInHeadlessModeDoesNotThrow() {
        graphicsManager.initHeadless();

        // Cache some patterns
        graphicsManager.cachePatternTexture(createTestPattern(), 0);
        graphicsManager.cachePatternTexture(createTestPattern(), 1);

        // Cleanup should work without GL context
        graphicsManager.cleanup();

        // Patterns should be cleared
        assertNull(graphicsManager.getPatternTextureId(0), "Patterns should be cleared after cleanup");
    }

    // ==================== Singleton Reset Tests ====================

    @Test
    public void testDestroyForReinitCreatesNewInstance() {
        GraphicsManager first = GraphicsManager.getInstance();
        first.setHeadlessMode(true);

        GraphicsManager.destroyForReinit();

        GraphicsManager second = GraphicsManager.getInstance();
        assertFalse(second.isHeadlessMode(), "New instance should have default headless mode");
    }

    // ==================== Batching Enable/Disable Tests ====================

    @Test
    public void testBatchingEnabledByDefault() {
        assertTrue(graphicsManager.isBatchingEnabled(), "Batching should be enabled by default");
    }

    @Test
    public void testSetBatchingEnabled() {
        graphicsManager.setBatchingEnabled(false);
        assertFalse(graphicsManager.isBatchingEnabled(), "Batching should be disabled");

        graphicsManager.setBatchingEnabled(true);
        assertTrue(graphicsManager.isBatchingEnabled(), "Batching should be re-enabled");
    }

    // ==================== Helper Methods ====================

    private Pattern createTestPattern() {
        // Create a simple 8x8 pattern for testing using default constructor
        return new Pattern();
    }

    private Palette createTestPalette() {
        // Create a simple 16-color palette for testing using default constructor
        return new Palette();
    }
}


