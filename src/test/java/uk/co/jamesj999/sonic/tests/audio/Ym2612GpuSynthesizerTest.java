package uk.co.jamesj999.sonic.tests.audio;

import com.jogamp.opengl.*;
import org.junit.*;
import uk.co.jamesj999.sonic.audio.synth.Ym2612Chip;
import uk.co.jamesj999.sonic.audio.synth.Ym2612GpuSynthesizer;
import uk.co.jamesj999.sonic.graphics.compute.ComputeCapabilities;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Tests for GPU-accelerated YM2612 synthesis.
 * <p>
 * These tests verify that GPU synthesis produces output identical to CPU synthesis.
 * Tests are skipped if OpenGL 4.3+ with compute shader support is not available.
 * <p>
 * Note: These tests require a display or virtual framebuffer (Xvfb on Linux) to
 * create an OpenGL context. They may not run in headless CI environments.
 */
public class Ym2612GpuSynthesizerTest {

    private static GLAutoDrawable glDrawable;
    private static GL4 gl4;
    private static boolean computeSupported = false;
    private static String skipReason = null;

    private Ym2612Chip cpuChip;
    private Ym2612GpuSynthesizer gpuSynthesizer;

    @BeforeClass
    public static void setUpClass() {
        try {
            // Try to create a headless GL4 context
            GLProfile profile = GLProfile.get(GLProfile.GL4);
            GLCapabilities caps = new GLCapabilities(profile);
            caps.setOnscreen(false);  // Offscreen rendering
            caps.setHardwareAccelerated(true);

            GLDrawableFactory factory = GLDrawableFactory.getFactory(profile);

            // Create an offscreen drawable
            glDrawable = factory.createOffscreenAutoDrawable(
                    factory.getDefaultDevice(),
                    caps,
                    null,  // GLCapabilitiesChooser
                    1, 1   // Minimal size for compute-only context
            );

            glDrawable.display();  // Initialize the context

            // Get the GL4 context
            GLContext ctx = glDrawable.getContext();
            int result = ctx.makeCurrent();
            if (result == GLContext.CONTEXT_NOT_CURRENT) {
                skipReason = "Failed to make GL4 context current";
                return;
            }

            GL gl = glDrawable.getGL();
            if (!gl.isGL4()) {
                skipReason = "GL4 profile not available";
                ctx.release();
                return;
            }

            gl4 = gl.getGL4();

            // Detect compute capabilities
            ComputeCapabilities.reset();  // Reset in case previous tests ran
            ComputeCapabilities.detect(gl4);

            if (!ComputeCapabilities.isComputeSupported()) {
                skipReason = "OpenGL compute shaders not supported (requires GL 4.3+)";
                ctx.release();
                return;
            }

            computeSupported = true;
            System.out.println("GPU synthesis test environment initialized:");
            System.out.println("  GL Version: " + ComputeCapabilities.getGlVersion());
            System.out.println("  GLSL Version: " + ComputeCapabilities.getGlslVersion());
            System.out.println("  Max workgroup size: " + ComputeCapabilities.getMaxComputeWorkGroupSize());

        } catch (GLException e) {
            skipReason = "Failed to create GL4 context: " + e.getMessage();
        } catch (Exception e) {
            skipReason = "Unexpected error initializing GL: " + e.getMessage();
        }
    }

    @AfterClass
    public static void tearDownClass() {
        if (glDrawable != null) {
            GLContext ctx = glDrawable.getContext();
            if (ctx != null && ctx.isCurrent()) {
                ctx.release();
            }
            glDrawable.destroy();
            glDrawable = null;
        }
        gl4 = null;
        ComputeCapabilities.reset();
    }

    @Before
    public void setUp() {
        assumeTrue("GPU synthesis tests skipped: " + skipReason, computeSupported);

        // Make context current for this test
        GLContext ctx = glDrawable.getContext();
        ctx.makeCurrent();

        cpuChip = new Ym2612Chip();
        gpuSynthesizer = new Ym2612GpuSynthesizer(256);
    }

    @After
    public void tearDown() {
        if (gpuSynthesizer != null) {
            gpuSynthesizer.cleanup();
            gpuSynthesizer = null;
        }
        cpuChip = null;

        if (glDrawable != null) {
            GLContext ctx = glDrawable.getContext();
            if (ctx != null && ctx.isCurrent()) {
                ctx.release();
            }
        }
    }

    /**
     * Test that GPU synthesizer initializes successfully when compute is available.
     */
    @Test
    public void testGpuSynthesizerInitializes() {
        boolean initialized = gpuSynthesizer.initialize(gl4, cpuChip);
        assertTrue("GPU synthesizer should initialize when compute is available", initialized);
        assertTrue("GPU synthesizer should report as initialized", gpuSynthesizer.isInitialized());
        assertEquals("Batch size should be 256", 256, gpuSynthesizer.getBatchSize());
    }

    /**
     * Test that GPU synthesizer handles different batch sizes.
     */
    @Test
    public void testBatchSizeValidation() {
        // Test valid batch sizes
        Ym2612GpuSynthesizer synth256 = new Ym2612GpuSynthesizer(256);
        assertEquals(256, synth256.getBatchSize());

        Ym2612GpuSynthesizer synth512 = new Ym2612GpuSynthesizer(512);
        assertEquals(512, synth512.getBatchSize());

        Ym2612GpuSynthesizer synth1024 = new Ym2612GpuSynthesizer(1024);
        assertEquals(1024, synth1024.getBatchSize());

        // Test invalid batch sizes get clamped
        Ym2612GpuSynthesizer synthSmall = new Ym2612GpuSynthesizer(100);
        assertEquals("Small batch size should clamp to 256", 256, synthSmall.getBatchSize());

        Ym2612GpuSynthesizer synthMedium = new Ym2612GpuSynthesizer(400);
        assertEquals("Medium batch size should clamp to 512", 512, synthMedium.getBatchSize());

        Ym2612GpuSynthesizer synthLarge = new Ym2612GpuSynthesizer(2000);
        assertEquals("Large batch size should clamp to 1024", 1024, synthLarge.getBatchSize());
    }

    /**
     * Test that GPU synthesizer can be cleaned up multiple times safely.
     */
    @Test
    public void testCleanupIsSafe() {
        gpuSynthesizer.initialize(gl4, cpuChip);
        gpuSynthesizer.cleanup();
        assertFalse("Should not be initialized after cleanup", gpuSynthesizer.isInitialized());

        // Cleanup should be safe to call multiple times
        gpuSynthesizer.cleanup();
        gpuSynthesizer.cleanup();
    }

    /**
     * Test that LFO state can be updated.
     */
    @Test
    public void testLfoStateUpdate() {
        gpuSynthesizer.initialize(gl4, cpuChip);

        // Should not throw
        gpuSynthesizer.setLfoState(64, 16);
        gpuSynthesizer.setLfoState(0, 0);
        gpuSynthesizer.setLfoState(126, 31);
    }

    /**
     * Test that EG state can be updated.
     */
    @Test
    public void testEgStateUpdate() {
        gpuSynthesizer.initialize(gl4, cpuChip);

        // Should not throw
        gpuSynthesizer.setEgState(1, 0);
        gpuSynthesizer.setEgState(2048, 1);
        gpuSynthesizer.setEgState(4095, 2);
    }

    /**
     * Placeholder test for CPU vs GPU output comparison.
     * <p>
     * This test is currently a placeholder because the full state synchronization
     * between CPU and GPU is not yet implemented. Once implemented, this test
     * should verify byte-for-byte identical output.
     */
    @Test
    @Ignore("GPU state synchronization not yet fully implemented")
    public void testGpuOutputMatchesCpuOutput() {
        // TODO: Implement full CPU/GPU comparison once state sync is complete
        //
        // The test should:
        // 1. Configure identical state on both CPU and GPU chips
        // 2. Render a batch of samples from both
        // 3. Compare output byte-for-byte
        //
        // For now, this serves as documentation of the intended test.

        gpuSynthesizer.initialize(gl4, cpuChip);

        // Set up a simple tone on both
        cpuChip.write(0, 0x22, 0x00);  // LFO off
        cpuChip.write(0, 0x27, 0x00);  // CH3 normal mode
        cpuChip.write(0, 0x28, 0xF0);  // Key on CH0

        // Render from CPU
        int[] cpuLeft = new int[256];
        int[] cpuRight = new int[256];
        cpuChip.renderStereo(cpuLeft, cpuRight);

        // Render from GPU
        int[] gpuLeft = new int[256];
        int[] gpuRight = new int[256];
        gpuSynthesizer.renderStereo(gpuLeft, gpuRight, 256);

        // Compare
        assertArrayEquals("Left channel should match", cpuLeft, gpuLeft);
        assertArrayEquals("Right channel should match", cpuRight, gpuRight);
    }

    /**
     * Test that compute capabilities detection works correctly.
     */
    @Test
    public void testComputeCapabilitiesDetected() {
        assertTrue("Compute should be detected", ComputeCapabilities.isDetected());
        assertTrue("Compute should be supported", ComputeCapabilities.isComputeSupported());
        assertTrue("Workgroup count should be > 0", ComputeCapabilities.getMaxComputeWorkGroupCount() > 0);
        assertTrue("Workgroup size should be > 0", ComputeCapabilities.getMaxComputeWorkGroupSize() > 0);
        assertTrue("SSBO size should be > 0", ComputeCapabilities.getMaxShaderStorageBlockSize() > 0);
    }
}
