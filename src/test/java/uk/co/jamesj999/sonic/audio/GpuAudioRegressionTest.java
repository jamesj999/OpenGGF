package uk.co.jamesj999.sonic.audio;

import com.jogamp.opengl.*;
import org.junit.*;
import uk.co.jamesj999.sonic.audio.driver.SmpsDriver;
import uk.co.jamesj999.sonic.audio.smps.AbstractSmpsData;
import uk.co.jamesj999.sonic.audio.smps.DacData;
import uk.co.jamesj999.sonic.audio.smps.SmpsSequencer;
import uk.co.jamesj999.sonic.audio.synth.VirtualSynthesizer;
import uk.co.jamesj999.sonic.audio.synth.Ym2612Chip;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.sonic2.audio.Sonic2SmpsSequencerConfig;
import uk.co.jamesj999.sonic.game.sonic2.audio.smps.Sonic2SmpsLoader;
import uk.co.jamesj999.sonic.graphics.compute.ComputeCapabilities;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static uk.co.jamesj999.sonic.tests.RomTestUtils.ensureRomAvailable;

/**
 * GPU audio regression tests that compare GPU-synthesized output against CPU reference files.
 * <p>
 * These tests verify that GPU synthesis produces output identical (or near-identical) to CPU
 * synthesis. Tests are skipped if:
 * - OpenGL 4.3+ with compute shader support is not available
 * - ROM file is not available
 * - Reference audio files have not been generated
 * <p>
 * Note: GPU synthesis may have minor floating point differences from CPU synthesis.
 * A small tolerance is allowed for these tests.
 */
public class GpuAudioRegressionTest {

    private static final String REFERENCE_DIR = "audio-reference";
    private static final double SAMPLE_RATE = Ym2612Chip.getDefaultOutputRate();
    private static final int BUFFER_SIZE = 1024;

    // Tolerance for GPU vs CPU comparison
    // Allow small differences due to floating point variations between GPU and CPU
    private static final int GPU_SAMPLE_TOLERANCE = 2;

    // Music IDs
    private static final int MUSIC_EHZ = 0x81;

    // SFX IDs
    private static final int SFX_RING = 0xB5;

    private static GLAutoDrawable glDrawable;
    private static GL4 gl4;
    private static boolean gpuAvailable = false;
    private static String gpuSkipReason = null;

    private static Rom rom;
    private static Sonic2SmpsLoader loader;
    private static DacData dacData;
    private static boolean referenceFilesExist;

    @BeforeClass
    public static void setUpClass() {
        // First, try to set up GL context
        try {
            GLProfile profile = GLProfile.get(GLProfile.GL4);
            GLCapabilities caps = new GLCapabilities(profile);
            caps.setOnscreen(false);
            caps.setHardwareAccelerated(true);

            GLDrawableFactory factory = GLDrawableFactory.getFactory(profile);
            glDrawable = factory.createOffscreenAutoDrawable(
                    factory.getDefaultDevice(),
                    caps,
                    null,
                    1, 1
            );

            glDrawable.display();
            GLContext ctx = glDrawable.getContext();
            int result = ctx.makeCurrent();
            if (result == GLContext.CONTEXT_NOT_CURRENT) {
                gpuSkipReason = "Failed to make GL4 context current";
            } else {
                GL gl = glDrawable.getGL();
                if (!gl.isGL4()) {
                    gpuSkipReason = "GL4 profile not available";
                    ctx.release();
                } else {
                    gl4 = gl.getGL4();
                    ComputeCapabilities.reset();
                    ComputeCapabilities.detect(gl4);

                    if (!ComputeCapabilities.isComputeSupported()) {
                        gpuSkipReason = "OpenGL compute shaders not supported (requires GL 4.3+)";
                        ctx.release();
                    } else {
                        gpuAvailable = true;
                        ctx.release();
                    }
                }
            }
        } catch (GLException e) {
            gpuSkipReason = "Failed to create GL4 context: " + e.getMessage();
        } catch (Exception e) {
            gpuSkipReason = "Unexpected error initializing GL: " + e.getMessage();
        }

        // Then, try to load ROM
        try {
            File romFile = ensureRomAvailable();
            rom = new Rom();
            assertTrue("Failed to open ROM", rom.open(romFile.getAbsolutePath()));
            loader = new Sonic2SmpsLoader(rom);
            dacData = loader.loadDacData();
            referenceFilesExist = referenceFileExists("music_ehz.wav");
        } catch (Exception e) {
            referenceFilesExist = false;
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
        assumeTrue("GPU not available: " + gpuSkipReason, gpuAvailable);
        assumeTrue("Reference files not generated yet. Run AudioReferenceGenerator first.", referenceFilesExist);
    }

    /**
     * Test that GPU-rendered EHZ music matches the CPU reference.
     * <p>
     * Note: This test is currently marked as ignored because the GPU state
     * synchronization is not yet fully implemented. Once the GPU synthesizer
     * can properly sync state from the SMPS driver, this test should pass.
     */
    @Test
    @Ignore("GPU state synchronization with SmpsDriver not yet implemented")
    public void testGpuMusicEhzMatchesCpuReference() throws Exception {
        // This test would compare GPU-rendered audio against CPU reference
        // Currently ignored until GPU-SMPS integration is complete

        short[] reference = loadReferenceWav("music_ehz.wav");
        assertNotNull("Reference file should load", reference);

        // Make GL context current
        GLContext ctx = glDrawable.getContext();
        ctx.makeCurrent();

        try {
            // TODO: Create GPU-enabled SmpsDriver or VirtualSynthesizer
            // For now, this serves as documentation of the intended test

            // Generate current output using GPU
            // AbstractSmpsData musicData = loader.loadMusic(MUSIC_EHZ);
            // SmpsDriver driver = createGpuEnabledDriver();
            // ...

            // short[] current = renderAudioWithGpu(driver, reference.length);
            // assertGpuAudioMatchesCpu("music_ehz.wav (GPU)", reference, current);

            fail("GPU-SMPS integration not yet implemented");
        } finally {
            ctx.release();
        }
    }

    /**
     * Test that GPU-rendered SFX matches the CPU reference.
     */
    @Test
    @Ignore("GPU state synchronization with SmpsDriver not yet implemented")
    public void testGpuSfxRingMatchesCpuReference() throws Exception {
        short[] reference = loadReferenceWav("sfx_ring.wav");
        assertNotNull("Reference file should load", reference);

        GLContext ctx = glDrawable.getContext();
        ctx.makeCurrent();

        try {
            // TODO: Implement GPU SFX rendering
            fail("GPU-SMPS integration not yet implemented");
        } finally {
            ctx.release();
        }
    }

    /**
     * Verify that GPU synthesis can be initialized through VirtualSynthesizer.
     */
    @Test
    public void testVirtualSynthesizerGpuInitialization() {
        GLContext ctx = glDrawable.getContext();
        ctx.makeCurrent();

        try {
            VirtualSynthesizer synth = new VirtualSynthesizer(SAMPLE_RATE);

            // Try to initialize GPU synthesis
            boolean gpuInitialized = synth.initializeGpuSynthesis(gl4, 256);

            // Should succeed since we verified compute is available
            assertTrue("GPU synthesis should initialize when compute is available", gpuInitialized);
            assertTrue("GPU synthesis should be active", synth.isGpuSynthesisActive());

            // Should be able to toggle
            synth.setGpuSynthesisEnabled(false);
            assertFalse("GPU synthesis should be disabled", synth.isGpuSynthesisActive());

            synth.setGpuSynthesisEnabled(true);
            assertTrue("GPU synthesis should be re-enabled", synth.isGpuSynthesisActive());

            synth.cleanup();
            assertFalse("GPU synthesis should be inactive after cleanup", synth.isGpuSynthesisActive());
        } finally {
            ctx.release();
        }
    }

    /**
     * Performance comparison: GPU vs CPU rendering speed.
     * <p>
     * This test measures the time to render audio using both CPU and GPU
     * (once GPU is fully integrated). For now, it only benchmarks CPU.
     */
    @Test
    public void benchmarkGpuVsCpuRendering() {
        assumeTrue("ROM not available", loader != null);

        AbstractSmpsData musicData = loader.loadMusic(MUSIC_EHZ);
        assertNotNull("Music data should load", musicData);

        SmpsDriver driver = new SmpsDriver(SAMPLE_RATE);
        driver.setRegion(SmpsSequencer.Region.NTSC);

        SmpsSequencer seq = new SmpsSequencer(musicData, dacData, driver, Sonic2SmpsSequencerConfig.CONFIG);
        seq.setSampleRate(SAMPLE_RATE);
        driver.addSequencer(seq, false);

        short[] buffer = new short[BUFFER_SIZE * 2];

        // Warm up
        for (int i = 0; i < 100; i++) {
            driver.read(buffer);
        }

        // Benchmark CPU
        driver = new SmpsDriver(SAMPLE_RATE);
        driver.setRegion(SmpsSequencer.Region.NTSC);
        seq = new SmpsSequencer(musicData, dacData, driver, Sonic2SmpsSequencerConfig.CONFIG);
        seq.setSampleRate(SAMPLE_RATE);
        driver.addSequencer(seq, false);

        int iterations = (int) (SAMPLE_RATE / BUFFER_SIZE);
        long cpuStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            driver.read(buffer);
        }
        long cpuElapsed = System.nanoTime() - cpuStart;
        double cpuMs = cpuElapsed / 1_000_000.0;

        System.out.println("=== Audio Rendering Benchmark ===");
        System.out.println("CPU render time: " + String.format("%.2f", cpuMs) + " ms per second of audio");
        System.out.println("CPU real-time factor: " + String.format("%.1f", 1000.0 / cpuMs) + "x");

        // TODO: Add GPU benchmark once integration is complete
        // GLContext ctx = glDrawable.getContext();
        // ctx.makeCurrent();
        // ... benchmark GPU rendering ...
        // ctx.release();

        System.out.println("GPU benchmark: Not yet implemented (pending SMPS integration)");
    }

    private void assertGpuAudioMatchesCpu(String context, short[] reference, short[] current) {
        assertEquals("Sample count mismatch for " + context, reference.length, current.length);

        int maxDeviation = 0;
        long sumSquaredError = 0;
        int firstMismatchIndex = -1;

        for (int i = 0; i < reference.length; i++) {
            int diff = Math.abs(reference[i] - current[i]);
            if (diff > maxDeviation) {
                maxDeviation = diff;
            }
            sumSquaredError += (long) diff * diff;

            if (diff > GPU_SAMPLE_TOLERANCE && firstMismatchIndex < 0) {
                firstMismatchIndex = i;
            }
        }

        double rmsError = Math.sqrt((double) sumSquaredError / reference.length);

        if (maxDeviation > GPU_SAMPLE_TOLERANCE) {
            int sampleIndex = firstMismatchIndex / 2;
            double timeSeconds = sampleIndex / SAMPLE_RATE;
            fail(String.format(
                    "GPU/CPU audio mismatch for %s: max deviation=%d (tolerance=%d), RMS error=%.2f, " +
                    "first mismatch at sample %d (%.3f seconds), ref=%d, current=%d",
                    context, maxDeviation, GPU_SAMPLE_TOLERANCE, rmsError,
                    sampleIndex, timeSeconds, reference[firstMismatchIndex], current[firstMismatchIndex]
            ));
        }

        System.out.println(String.format("GPU/CPU match verified for %s: max deviation=%d, RMS error=%.2f",
                context, maxDeviation, rmsError));
    }

    private static boolean referenceFileExists(String filename) {
        try (InputStream is = GpuAudioRegressionTest.class.getClassLoader()
                .getResourceAsStream(REFERENCE_DIR + "/" + filename)) {
            return is != null;
        } catch (IOException e) {
            return false;
        }
    }

    private short[] loadReferenceWav(String filename) throws IOException, UnsupportedAudioFileException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(REFERENCE_DIR + "/" + filename)) {
            if (is == null) {
                Path filePath = Paths.get("src/test/resources", REFERENCE_DIR, filename);
                if (!Files.exists(filePath)) {
                    return null;
                }
                return loadWavFromFile(filePath.toFile());
            }

            File tempFile = File.createTempFile("audio_ref_", ".wav");
            tempFile.deleteOnExit();
            Files.copy(is, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return loadWavFromFile(tempFile);
        }
    }

    private short[] loadWavFromFile(File file) throws IOException, UnsupportedAudioFileException {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(file)) {
            AudioFormat format = ais.getFormat();
            assertEquals("Sample rate mismatch", (float) SAMPLE_RATE, format.getSampleRate(), 0.1f);
            assertEquals("Channels mismatch", 2, format.getChannels());
            assertEquals("Sample size mismatch", 16, format.getSampleSizeInBits());

            byte[] bytes = ais.readAllBytes();
            short[] samples = new short[bytes.length / 2];

            for (int i = 0; i < samples.length; i++) {
                int lo = bytes[i * 2] & 0xFF;
                int hi = bytes[i * 2 + 1];
                samples[i] = (short) ((hi << 8) | lo);
            }

            return samples;
        }
    }
}
