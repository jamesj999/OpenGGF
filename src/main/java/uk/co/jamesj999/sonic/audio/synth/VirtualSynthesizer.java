package uk.co.jamesj999.sonic.audio.synth;

import com.jogamp.opengl.GL4;
import uk.co.jamesj999.sonic.audio.smps.DacData;
import uk.co.jamesj999.sonic.graphics.compute.ComputeCapabilities;

import java.util.Arrays;
import java.util.logging.Logger;

public class VirtualSynthesizer implements Synthesizer {
    private static final Logger LOGGER = Logger.getLogger(VirtualSynthesizer.class.getName());

    private final PsgChip psg;
    private final Ym2612Chip ym;
    private double outputSampleRate = Ym2612Chip.getDefaultOutputRate();

    // Output headroom: reduce overall level so 6 FM channels + PSG don't clip 16-bit output.
    private static final int MASTER_GAIN_SHIFT = 1; // -6 dB

    // Scratch buffers for render() to avoid per-call allocations
    private int[] scratchLeft = new int[0];
    private int[] scratchRight = new int[0];
    private int[] scratchLeftPsg = new int[0];
    private int[] scratchRightPsg = new int[0];

    // GPU synthesis support
    private Ym2612GpuSynthesizer gpuSynthesizer;
    private boolean useGpuSynthesis = false;
    private boolean gpuInitAttempted = false;

    // Minimum batch size for GPU rendering (below this, CPU is faster)
    private static final int GPU_MIN_BATCH_SIZE = 64;

    public VirtualSynthesizer() {
        this(Ym2612Chip.getDefaultOutputRate());
    }

    public VirtualSynthesizer(double outputSampleRate) {
        this.psg = new PsgChip(outputSampleRate);
        this.ym = new Ym2612Chip();
        setOutputSampleRate(outputSampleRate);
        // Match typical driver init: silence chips on startup to avoid power-on noise.
        silenceAll();
    }

    /**
     * Initialize GPU synthesis if available.
     * <p>
     * This must be called from the OpenGL thread with a valid GL4 context.
     * If initialization fails or compute shaders are not supported, the
     * synthesizer will continue using CPU rendering.
     *
     * @param gl        the OpenGL 4 context
     * @param batchSize samples per GPU dispatch (256, 512, or 1024)
     * @return true if GPU synthesis is now active
     */
    public boolean initializeGpuSynthesis(GL4 gl, int batchSize) {
        if (gpuInitAttempted) {
            return useGpuSynthesis;
        }
        gpuInitAttempted = true;

        if (!ComputeCapabilities.isComputeSupported()) {
            LOGGER.info("GPU synthesis unavailable - using CPU rendering");
            return false;
        }

        gpuSynthesizer = new Ym2612GpuSynthesizer(batchSize);
        if (gpuSynthesizer.initialize(gl, ym)) {
            useGpuSynthesis = true;
            LOGGER.info("GPU synthesis enabled with batch size " + gpuSynthesizer.getBatchSize());
            return true;
        } else {
            gpuSynthesizer = null;
            LOGGER.info("GPU synthesis initialization failed - using CPU rendering");
            return false;
        }
    }

    /**
     * Check if GPU synthesis is currently active.
     *
     * @return true if GPU synthesis is being used
     */
    public boolean isGpuSynthesisActive() {
        return useGpuSynthesis && gpuSynthesizer != null && gpuSynthesizer.isInitialized();
    }

    /**
     * Enable or disable GPU synthesis at runtime.
     * <p>
     * GPU synthesis must have been successfully initialized first.
     *
     * @param enabled true to use GPU, false to use CPU
     */
    public void setGpuSynthesisEnabled(boolean enabled) {
        if (enabled && gpuSynthesizer != null && gpuSynthesizer.isInitialized()) {
            useGpuSynthesis = true;
        } else {
            useGpuSynthesis = false;
        }
    }

    public void setOutputSampleRate(double outputSampleRate) {
        if (outputSampleRate <= 0.0) {
            return;
        }
        this.outputSampleRate = outputSampleRate;
        ym.setOutputSampleRate(outputSampleRate);
        psg.setSampleRate(outputSampleRate);
    }

    public double getOutputSampleRate() {
        return outputSampleRate;
    }

    public void setDacData(DacData data) {
        ym.setDacData(data);
    }

    @Override
    public void playDac(Object source, int note) {
        ym.playDac(note);
    }

    @Override
    public void stopDac(Object source) {
        ym.stopDac();
    }

    public void render(short[] buffer) {
        // Assume buffer is Stereo Interleaved (L, R, L, R...)
        int frames = buffer.length / 2;

        // Reuse scratch buffers, resize only when needed
        if (scratchLeft.length < frames) {
            scratchLeft = new int[frames];
            scratchRight = new int[frames];
            scratchLeftPsg = new int[frames];
            scratchRightPsg = new int[frames];
        }

        // Clear reused buffers (chips accumulate into them)
        Arrays.fill(scratchLeft, 0, frames, 0);
        Arrays.fill(scratchRight, 0, frames, 0);
        Arrays.fill(scratchLeftPsg, 0, frames, 0);
        Arrays.fill(scratchRightPsg, 0, frames, 0);

        ym.renderStereo(scratchLeft, scratchRight);

        // GPGX-style: FM output is clipped to ±8191 internally.
        // No output gain applied here - volume issues are in the EG/feedback implementation.

        psg.renderStereo(scratchLeftPsg, scratchRightPsg);

        // Mix PSG at ~50% level relative to FM
        for (int i = 0; i < frames; i++) {
            scratchLeft[i] += scratchLeftPsg[i] >> 1;
            scratchRight[i] += scratchRightPsg[i] >> 1;
        }

        for (int i = 0; i < frames; i++) {
            // Master gain: apply fixed headroom scaling before 16-bit clamp.
            int l = scratchLeft[i];
            int r = scratchRight[i];

            if (MASTER_GAIN_SHIFT > 0) {
                l >>= MASTER_GAIN_SHIFT;
                r >>= MASTER_GAIN_SHIFT;
            }

            if (l > 32767) l = 32767; else if (l < -32768) l = -32768;
            if (r > 32767) r = 32767; else if (r < -32768) r = -32768;

            buffer[i * 2] = (short) l;
            buffer[i * 2 + 1] = (short) r;
        }
    }

    @Override
    public void writeFm(Object source, int port, int reg, int val) {
        ym.write(port, reg, val);
    }

    @Override
    public void writePsg(Object source, int val) {
        psg.write(val);
    }

    @Override
    public void setInstrument(Object source, int channelId, byte[] voice) {
        ym.setInstrument(channelId, voice);
    }

    @Override
    public void setFmMute(int channel, boolean mute) {
        ym.setMute(channel, mute);
    }

    @Override
    public void setPsgMute(int channel, boolean mute) {
        psg.setMute(channel, mute);
    }

    @Override
    public void setDacInterpolate(boolean interpolate) {
        ym.setDacInterpolate(interpolate);
    }

    @Override
    public void silenceAll() {
        ym.silenceAll();
        psg.silenceAll();
    }

    /**
     * Force-silence an FM channel by directly resetting envelope state.
     * Used when SFX steals a channel to prevent chirp artifacts.
     */
    public void forceSilenceChannel(int channelId) {
        ym.forceSilenceChannel(channelId);
    }

    /**
     * Render a batch of samples using GPU acceleration when available.
     * <p>
     * This method renders FM synthesis on the GPU (when enabled and batch is large enough),
     * then mixes in PSG output (always CPU-rendered since it's cheap).
     * <p>
     * For small batches or when GPU is disabled, falls back to per-sample CPU rendering.
     *
     * @param leftBuf  left channel output buffer
     * @param rightBuf right channel output buffer
     * @param offset   starting offset in output buffers
     * @param count    number of samples to render
     */
    public void renderBatch(int[] leftBuf, int[] rightBuf, int offset, int count) {
        if (count <= 0) return;

        // Use exactly-sized temp arrays (the chips use array length to determine count)
        int[] tempLeftFm = new int[count];
        int[] tempRightFm = new int[count];
        int[] tempLeftPsg = new int[count];
        int[] tempRightPsg = new int[count];

        if (useGpuSynthesis && count >= GPU_MIN_BATCH_SIZE && gpuSynthesizer != null) {
            // GPU path for FM synthesis
            gpuSynthesizer.renderStereoBatch(tempLeftFm, tempRightFm, 0, count);

            // PSG still on CPU (it's cheap and doesn't benefit from GPU batching)
            psg.renderStereo(tempLeftPsg, tempRightPsg);

            // Mix PSG into FM output (same ratio as render())
            for (int i = 0; i < count; i++) {
                tempLeftFm[i] += tempLeftPsg[i] >> 1;
                tempRightFm[i] += tempRightPsg[i] >> 1;
            }
        } else {
            // Per-sample CPU fallback using the chip's renderStereo
            // Pass exactly-sized arrays so the chips render the correct count
            ym.renderStereo(tempLeftFm, tempRightFm);
            psg.renderStereo(tempLeftPsg, tempRightPsg);

            // Mix PSG at ~50% level relative to FM
            for (int i = 0; i < count; i++) {
                tempLeftFm[i] += tempLeftPsg[i] >> 1;
                tempRightFm[i] += tempRightPsg[i] >> 1;
            }
        }

        // Copy to output buffers with master gain
        for (int i = 0; i < count; i++) {
            int l = tempLeftFm[i];
            int r = tempRightFm[i];

            if (MASTER_GAIN_SHIFT > 0) {
                l >>= MASTER_GAIN_SHIFT;
                r >>= MASTER_GAIN_SHIFT;
            }

            // Clamp to 32-bit range (will be clamped to 16-bit later)
            if (l > 32767) l = 32767; else if (l < -32768) l = -32768;
            if (r > 32767) r = 32767; else if (r < -32768) r = -32768;

            leftBuf[offset + i] = l;
            rightBuf[offset + i] = r;
        }
    }

    /**
     * Get the YM2612 chip for direct access (used by SmpsDriver for batch rendering).
     *
     * @return the YM2612 chip instance
     */
    public Ym2612Chip getYm2612() {
        return ym;
    }

    /**
     * Get the PSG chip for direct access (used by SmpsDriver for batch rendering).
     *
     * @return the PSG chip instance
     */
    public PsgChip getPsg() {
        return psg;
    }

    /**
     * Check if batch rendering should use GPU for a given batch size.
     *
     * @param batchSize proposed batch size
     * @return true if GPU should be used
     */
    public boolean shouldUseGpuForBatch(int batchSize) {
        return useGpuSynthesis && batchSize >= GPU_MIN_BATCH_SIZE && gpuSynthesizer != null;
    }

    /**
     * Clean up GPU synthesis resources.
     * Call this when shutting down the audio system.
     */
    public void cleanup() {
        if (gpuSynthesizer != null) {
            gpuSynthesizer.cleanup();
            gpuSynthesizer = null;
        }
        useGpuSynthesis = false;
    }
}
