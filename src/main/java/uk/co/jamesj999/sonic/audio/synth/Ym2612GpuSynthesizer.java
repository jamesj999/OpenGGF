package uk.co.jamesj999.sonic.audio.synth;

import com.jogamp.opengl.GL4;
import uk.co.jamesj999.sonic.graphics.compute.ComputeCapabilities;
import uk.co.jamesj999.sonic.graphics.compute.ComputeShaderProgram;
import uk.co.jamesj999.sonic.graphics.compute.GpuBuffer;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GPU-accelerated YM2612 FM synthesizer using OpenGL compute shaders.
 * <p>
 * This class offloads FM synthesis calculations to the GPU, processing
 * multiple samples in parallel. It maintains synchronization with the
 * CPU-based Ym2612Chip for register writes and state management.
 * <p>
 * When compute shaders are not available (OpenGL < 4.3), this class
 * gracefully falls back to returning null from initialize(), allowing
 * callers to use the CPU implementation instead.
 * <p>
 * The synthesizer processes audio in configurable batches (256, 512, or 1024
 * samples) to balance latency against GPU dispatch overhead.
 */
public class Ym2612GpuSynthesizer {
    private static final Logger LOGGER = Logger.getLogger(Ym2612GpuSynthesizer.class.getName());

    // Shader resource paths
    private static final String SYNTH_SHADER_PATH = "shaders/shader_ym2612_synth.glsl";
    private static final String ENVELOPE_SHADER_PATH = "shaders/shader_ym2612_envelope.glsl";

    // SSBO binding points (must match shader layout bindings)
    private static final int BINDING_CHANNEL_STATE = 0;
    private static final int BINDING_SIN_TABLE = 1;
    private static final int BINDING_TL_TABLE = 2;
    private static final int BINDING_EG_INC_TABLE = 3;
    private static final int BINDING_EG_RATE_TABLE = 4;
    private static final int BINDING_DT_TABLE = 5;
    private static final int BINDING_LFO_PM_TABLE = 6;
    private static final int BINDING_OUTPUT_SAMPLES = 7;
    private static final int BINDING_OPERATOR_ENV_STATES = 8;
    private static final int BINDING_ENVELOPE_TRAJECTORY = 9;
    private static final int BINDING_LFO_TRAJECTORY = 10;

    // Workgroup size (must match shader local_size_x)
    private static final int SYNTH_WORKGROUP_SIZE = 64;
    private static final int ENVELOPE_WORKGROUP_SIZE = 24;

    // Table sizes
    private static final int SIN_TAB_SIZE = 1024;
    private static final int TL_TAB_SIZE = 6656;
    private static final int EG_INC_SIZE = 152;
    private static final int EG_RATE_SIZE = 128;
    private static final int DT_TAB_SIZE = 256;
    private static final int LFO_PM_TAB_SIZE = 32768;

    // State
    private final int batchSize;
    private boolean initialized = false;
    private GL4 gl;

    // Shaders
    private ComputeShaderProgram synthShader;
    private ComputeShaderProgram envelopeShader;

    // Lookup table buffers (UBOs - uploaded once)
    private GpuBuffer sinTableBuffer;
    private GpuBuffer tlTableBuffer;
    private GpuBuffer egIncTableBuffer;
    private GpuBuffer egRateTableBuffer;
    private GpuBuffer dtTableBuffer;
    private GpuBuffer lfoPmTableBuffer;

    // State buffers (SSBOs - updated each batch)
    private GpuBuffer channelStateBuffer;
    private GpuBuffer operatorEnvStateBuffer;
    private GpuBuffer outputSamplesBuffer;

    // Trajectory buffers for per-sample envelope/LFO values
    private GpuBuffer envelopeTrajectoryBuffer;
    private GpuBuffer lfoTrajectoryBuffer;
    private int[] envelopeTrajectory;  // 24 operators × (batchSize/3 + 1) entries
    private int[] lfoTrajectory;       // batchSize × 2 (AM, PM interleaved)

    // Double-buffered output for async readback
    private int[] outputBufferA;
    private int[] outputBufferB;
    private boolean useBufferA = true;
    private int outputReadPos = 0;
    private int outputWritePos = 0;
    private int samplesAvailable = 0;

    // Channel state upload buffer (6 channels × 44 ints each)
    private static final int CHANNEL_STATE_INTS = Ym2612Chip.CHANNEL_STATE_INTS;
    private final int[] channelUploadBuffer = new int[6 * CHANNEL_STATE_INTS];

    // Reference to CPU chip for state synchronization
    private Ym2612Chip cpuChip;

    // Lookup tables uploaded once at initialization
    private boolean tablesUploaded = false;

    // LFO state (synchronized from CPU)
    private int currentLfoAm = 126;
    private int currentLfoPm = 0;

    // EG counter (synchronized from CPU)
    private int egCnt = 1;
    private int egTimer = 0;

    /**
     * Create a GPU synthesizer with the specified batch size.
     *
     * @param batchSize samples per GPU dispatch (256, 512, or 1024)
     */
    public Ym2612GpuSynthesizer(int batchSize) {
        this.batchSize = validateBatchSize(batchSize);
        this.outputBufferA = new int[this.batchSize * 2];
        this.outputBufferB = new int[this.batchSize * 2];
    }

    /**
     * Validate and clamp batch size to allowed values.
     */
    private int validateBatchSize(int size) {
        if (size <= 256) return 256;
        if (size <= 512) return 512;
        return 1024;
    }

    /**
     * Get the configured batch size.
     *
     * @return batch size in samples
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Check if the GPU synthesizer is initialized and ready.
     *
     * @return true if GPU synthesis is available
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Initialize the GPU synthesizer.
     * <p>
     * This must be called from the OpenGL thread with a valid GL4 context.
     * If compute shaders are not supported, this method returns false and
     * the caller should fall back to CPU synthesis.
     *
     * @param gl      the OpenGL 4 context
     * @param cpuChip the CPU chip to synchronize state with
     * @return true if initialization succeeded
     */
    public boolean initialize(GL4 gl, Ym2612Chip cpuChip) {
        if (initialized) {
            return true;
        }

        // Check compute shader support
        if (!ComputeCapabilities.isDetected()) {
            ComputeCapabilities.detect(gl);
        }

        if (!ComputeCapabilities.isComputeSupported()) {
            LOGGER.info("Compute shaders not supported - GPU synthesis disabled");
            return false;
        }

        this.gl = gl;
        this.cpuChip = cpuChip;

        try {
            // Load shaders
            synthShader = new ComputeShaderProgram(gl, SYNTH_SHADER_PATH);
            envelopeShader = new ComputeShaderProgram(gl, ENVELOPE_SHADER_PATH);

            // Create lookup table buffers
            createLookupTableBuffers(gl);

            // Create state buffers
            createStateBuffers(gl);

            // Upload lookup tables (done once)
            uploadLookupTables(gl);

            initialized = true;
            LOGGER.info("GPU YM2612 synthesizer initialized with batch size " + batchSize);
            return true;

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to initialize GPU synthesizer", e);
            cleanup();
            return false;
        }
    }

    /**
     * Create GPU buffers for lookup tables.
     */
    private void createLookupTableBuffers(GL4 gl) {
        // UBOs for lookup tables (read-only, fast access)
        sinTableBuffer = new GpuBuffer(gl, GpuBuffer.Type.UBO,
                SIN_TAB_SIZE * Integer.BYTES, GpuBuffer.Usage.STATIC);
        tlTableBuffer = new GpuBuffer(gl, GpuBuffer.Type.UBO,
                TL_TAB_SIZE * Integer.BYTES, GpuBuffer.Usage.STATIC);
        egIncTableBuffer = new GpuBuffer(gl, GpuBuffer.Type.UBO,
                EG_INC_SIZE * Integer.BYTES, GpuBuffer.Usage.STATIC);
        egRateTableBuffer = new GpuBuffer(gl, GpuBuffer.Type.UBO,
                EG_RATE_SIZE * 2 * Integer.BYTES, GpuBuffer.Usage.STATIC);
        dtTableBuffer = new GpuBuffer(gl, GpuBuffer.Type.UBO,
                DT_TAB_SIZE * Integer.BYTES, GpuBuffer.Usage.STATIC);
        lfoPmTableBuffer = new GpuBuffer(gl, GpuBuffer.Type.UBO,
                LFO_PM_TAB_SIZE * Integer.BYTES, GpuBuffer.Usage.STATIC);
    }

    /**
     * Create GPU buffers for runtime state.
     */
    private void createStateBuffers(GL4 gl) {
        // Channel state SSBO (6 channels × channel struct size)
        // Channel struct: 4 operators × 8 ints + 12 channel-level ints = 44 ints
        int channelStructSize = CHANNEL_STATE_INTS * Integer.BYTES;
        channelStateBuffer = new GpuBuffer(gl, GpuBuffer.Type.SSBO,
                6 * channelStructSize, GpuBuffer.Usage.DYNAMIC);

        // Operator envelope state SSBO (24 operators × env struct size)
        // EnvState struct: ~18 ints - kept for future envelope GPU processing
        int envStructSize = 18 * Integer.BYTES;
        operatorEnvStateBuffer = new GpuBuffer(gl, GpuBuffer.Type.SSBO,
                24 * envStructSize, GpuBuffer.Usage.DYNAMIC);

        // Output samples SSBO (batch size × 2 ints for stereo)
        outputSamplesBuffer = new GpuBuffer(gl, GpuBuffer.Type.SSBO,
                batchSize * 2 * Integer.BYTES, GpuBuffer.Usage.STREAM);

        // Envelope trajectory: 24 operators × max EG ticks per batch
        // EG advances every 3 samples, so max ticks = (batchSize / 3) + 1
        int maxEgTicks = (batchSize / 3) + 1;
        envelopeTrajectoryBuffer = new GpuBuffer(gl, GpuBuffer.Type.SSBO,
                24 * maxEgTicks * Integer.BYTES, GpuBuffer.Usage.DYNAMIC);
        envelopeTrajectory = new int[24 * maxEgTicks];

        // LFO trajectory: batchSize × 2 (AM, PM interleaved)
        lfoTrajectoryBuffer = new GpuBuffer(gl, GpuBuffer.Type.SSBO,
                batchSize * 2 * Integer.BYTES, GpuBuffer.Usage.DYNAMIC);
        lfoTrajectory = new int[batchSize * 2];
    }

    /**
     * Upload lookup tables to GPU. Called once during initialization.
     */
    private void uploadLookupTables(GL4 gl) {
        if (tablesUploaded) return;

        // Upload sine table (1024 entries)
        int[] sinTab = Ym2612Chip.getSinTable();
        sinTableBuffer.upload(gl, sinTab, 0);

        // Upload total level table (6656 entries)
        int[] tlTab = Ym2612Chip.getTlTable();
        tlTableBuffer.upload(gl, tlTab, 0);

        // Upload envelope increment table (152 entries)
        int[] egInc = Ym2612Chip.getEgIncTable();
        egIncTableBuffer.upload(gl, egInc, 0);

        // Upload EG rate tables (128 entries each, interleaved as 256 total)
        int[] egRateSelect = Ym2612Chip.getEgRateSelectTable();
        int[] egRateShift = Ym2612Chip.getEgRateShiftTable();
        int[] egRateCombined = new int[256];
        System.arraycopy(egRateSelect, 0, egRateCombined, 0, 128);
        System.arraycopy(egRateShift, 0, egRateCombined, 128, 128);
        egRateTableBuffer.upload(gl, egRateCombined, 0);

        // Upload detune table (8 × 32 = 256 entries, flattened)
        int[] dtTab = Ym2612Chip.getDtTableFlat();
        dtTableBuffer.upload(gl, dtTab, 0);

        // Upload LFO PM table (32768 entries)
        int[] lfoPmTab = Ym2612Chip.getLfoPmTable();
        lfoPmTableBuffer.upload(gl, lfoPmTab, 0);

        tablesUploaded = true;
        LOGGER.fine("Lookup tables uploaded to GPU");
    }

    /**
     * Synchronize channel and operator state from CPU to GPU.
     * Called before dispatching a batch.
     */
    private void syncStateToGpu() {
        if (cpuChip == null || !initialized) return;

        // Export all 6 channel states from CPU chip
        for (int ch = 0; ch < 6; ch++) {
            cpuChip.exportChannelState(ch, channelUploadBuffer, ch * CHANNEL_STATE_INTS);
        }

        // Upload to GPU
        channelStateBuffer.upload(gl, channelUploadBuffer, 0);

        // Update LFO state uniforms
        currentLfoAm = cpuChip.getLfoAm();
        currentLfoPm = cpuChip.getLfoPm();

        // Update EG counter state
        egCnt = cpuChip.getEgCnt();
        egTimer = cpuChip.getEgTimer();
    }

    /**
     * Synchronize evolving state from GPU back to CPU.
     * Called after GPU batch completes.
     * <p>
     * Only imports fields that change during synthesis:
     * - Phase counters (fCnt)
     * - Feedback buffers (opOut)
     * - Memory value for algorithms
     */
    private void syncStateFromGpu() {
        if (cpuChip == null || !initialized) return;

        // Download channel state from GPU
        channelStateBuffer.download(gl, channelUploadBuffer, 0);

        // Import evolving state back to CPU chip
        for (int ch = 0; ch < 6; ch++) {
            cpuChip.importEvolvingState(ch, channelUploadBuffer, ch * CHANNEL_STATE_INTS);
        }
    }

    /**
     * Compute envelope values at each EG tick for all 24 operators.
     * This runs the envelope state machine on CPU to generate a lookup table
     * that the GPU can index by sample number.
     * <p>
     * EG advances every 3 samples, so for a batch of N samples we need
     * approximately N/3 + 1 trajectory entries per operator.
     *
     * @param sampleCount number of samples in this batch
     */
    private void computeEnvelopeTrajectory(int sampleCount) {
        // Save current state so we can restore after trajectory computation
        int savedEgCnt = cpuChip.getEgCnt();
        int savedEgTimer = cpuChip.getEgTimer();
        int[][] savedOpState = cpuChip.saveOperatorEnvelopeState();

        // Calculate how many EG ticks we need to compute
        // EG advances every 3 samples; startEgTimer tells us our position within the tick
        int startTimer = savedEgTimer;
        int egTicks = (sampleCount + startTimer + 2) / 3;

        // Record envelope values at each EG tick
        for (int tick = 0; tick < egTicks; tick++) {
            // Record current envelope values for all 24 operators
            for (int ch = 0; ch < 6; ch++) {
                for (int op = 0; op < 4; op++) {
                    int opIdx = ch * 4 + op;
                    envelopeTrajectory[tick * 24 + opIdx] = cpuChip.getOperatorVolume(ch, op);
                }
            }
            // Advance envelopes for next tick
            cpuChip.advanceEnvelopesOnce();
        }

        // Restore original state (actual rendering will re-advance properly)
        cpuChip.restoreOperatorEnvelopeState(savedOpState);
        cpuChip.setEgState(savedEgCnt, savedEgTimer);
    }

    /**
     * Compute LFO AM/PM values for each sample in the batch.
     * The GPU will index into this array by sample number.
     *
     * @param sampleCount number of samples in this batch
     */
    private void computeLfoTrajectory(int sampleCount) {
        // Save current state
        int savedLfoCnt = cpuChip.getLfoCnt();
        int savedLfoTimer = cpuChip.getLfoTimer();

        // Record LFO values for each sample
        for (int i = 0; i < sampleCount; i++) {
            lfoTrajectory[i * 2] = cpuChip.getLfoAm();
            lfoTrajectory[i * 2 + 1] = cpuChip.getLfoPm();
            cpuChip.advanceLfoOnce();
        }

        // Restore original state
        cpuChip.setLfoState(savedLfoCnt, savedLfoTimer);
    }

    /**
     * Dispatch the GPU to render a batch of samples.
     *
     * @param count number of samples to render (must be <= batchSize)
     */
    private void dispatchBatch(int count) {
        if (!initialized || count <= 0) return;

        // Clamp count to batch size
        int actualCount = Math.min(count, batchSize);

        // Step 1: Compute trajectories on CPU (these simulate envelope/LFO advancement
        // without modifying the actual CPU chip state)
        int startEgTimer = cpuChip.getEgTimer();
        computeEnvelopeTrajectory(actualCount);
        computeLfoTrajectory(actualCount);

        // Step 2: Upload trajectories to GPU
        envelopeTrajectoryBuffer.upload(gl, envelopeTrajectory, 0);
        lfoTrajectoryBuffer.upload(gl, lfoTrajectory, 0);

        // Step 3: Sync channel state (for phase counters, feedback, algorithm, etc.)
        syncStateToGpu();

        // Step 4: Bind all buffers
        channelStateBuffer.bindBase(gl, BINDING_CHANNEL_STATE);
        sinTableBuffer.bindBase(gl, BINDING_SIN_TABLE);
        tlTableBuffer.bindBase(gl, BINDING_TL_TABLE);
        egIncTableBuffer.bindBase(gl, BINDING_EG_INC_TABLE);
        egRateTableBuffer.bindBase(gl, BINDING_EG_RATE_TABLE);
        dtTableBuffer.bindBase(gl, BINDING_DT_TABLE);
        lfoPmTableBuffer.bindBase(gl, BINDING_LFO_PM_TABLE);
        outputSamplesBuffer.bindBase(gl, BINDING_OUTPUT_SAMPLES);
        operatorEnvStateBuffer.bindBase(gl, BINDING_OPERATOR_ENV_STATES);
        envelopeTrajectoryBuffer.bindBase(gl, BINDING_ENVELOPE_TRAJECTORY);
        lfoTrajectoryBuffer.bindBase(gl, BINDING_LFO_TRAJECTORY);

        // Step 5: Calculate work groups for synthesis
        int synthWorkGroups = (actualCount + SYNTH_WORKGROUP_SIZE - 1) / SYNTH_WORKGROUP_SIZE;

        // Step 6: Dispatch synthesis shader
        synthShader.use(gl);
        synthShader.setUniform(gl, "batchOffset", 0);
        synthShader.setUniformUint(gl, "batchSize", actualCount);
        synthShader.setUniform(gl, "lfoAm", currentLfoAm);  // Legacy, kept for compatibility
        synthShader.setUniform(gl, "lfoPm", currentLfoPm);  // Legacy, kept for compatibility
        synthShader.setUniform(gl, "startEgTimer", startEgTimer);
        synthShader.dispatch(gl, synthWorkGroups, 1, 1);

        // Step 7: Memory barrier before reading results
        synthShader.memoryBarrier(gl);
        synthShader.stop(gl);

        // Step 8: Read back results
        int[] outputBuffer = useBufferA ? outputBufferA : outputBufferB;
        outputSamplesBuffer.download(gl, outputBuffer, 0);

        // Step 9: Sync evolving state back to CPU (phase counters, feedback)
        syncStateFromGpu();

        // Step 10: Advance CPU chip state for the samples we rendered
        // The trajectories were computed without permanently modifying CPU state,
        // so now we need to actually advance the envelope/LFO state.
        for (int i = 0; i < actualCount; i++) {
            cpuChip.advanceLfoOnce();
            cpuChip.advanceEgTimerOnce();
        }

        samplesAvailable = actualCount;
        outputReadPos = 0;
        useBufferA = !useBufferA;
    }

    /**
     * Dispatch the GPU to render a full batch.
     */
    private void dispatchBatch() {
        dispatchBatch(batchSize);
    }

    /**
     * Render samples to the output buffers.
     * <p>
     * This method should be called from the audio thread. It pulls samples
     * from the GPU output buffer, dispatching new batches as needed.
     *
     * @param leftBuf  left channel output buffer
     * @param rightBuf right channel output buffer
     * @param count    number of samples to render
     */
    public void renderStereo(int[] leftBuf, int[] rightBuf, int count) {
        if (!initialized) return;

        int[] currentBuffer = useBufferA ? outputBufferB : outputBufferA;

        for (int i = 0; i < count; i++) {
            // Check if we need more samples
            if (samplesAvailable <= 0) {
                // Note: In a real implementation, this would need to be
                // coordinated with the GL thread since GL calls must happen
                // on the GL thread. This is a simplified version.
                dispatchBatch();
                currentBuffer = useBufferA ? outputBufferB : outputBufferA;
            }

            // Read stereo sample
            leftBuf[i] = currentBuffer[outputReadPos * 2];
            rightBuf[i] = currentBuffer[outputReadPos * 2 + 1];

            outputReadPos++;
            samplesAvailable--;
        }
    }

    /**
     * Render a batch of samples directly to the output buffers.
     * <p>
     * This is the primary interface for batch rendering. The GPU dispatches
     * compute shader invocations to generate all samples, potentially in
     * multiple batches if count exceeds batchSize.
     * <p>
     * Call this from the OpenGL thread since it makes GL calls.
     *
     * @param leftBuf  left channel output buffer
     * @param rightBuf right channel output buffer
     * @param offset   starting offset in output buffers
     * @param count    number of samples to render
     */
    public void renderStereoBatch(int[] leftBuf, int[] rightBuf, int offset, int count) {
        if (!initialized || cpuChip == null || count <= 0) return;

        int remaining = count;
        int currentOffset = offset;

        while (remaining > 0) {
            // Render up to batchSize samples at a time
            int toRender = Math.min(remaining, batchSize);

            // Dispatch GPU to render this batch
            dispatchBatch(toRender);

            // Copy results to output buffers
            int[] currentBuffer = useBufferA ? outputBufferB : outputBufferA;
            for (int i = 0; i < toRender; i++) {
                leftBuf[currentOffset + i] = currentBuffer[i * 2];
                rightBuf[currentOffset + i] = currentBuffer[i * 2 + 1];
            }

            currentOffset += toRender;
            remaining -= toRender;
        }

        // Reset state for next batch
        samplesAvailable = 0;
        outputReadPos = 0;
    }

    /**
     * Update LFO state from CPU.
     *
     * @param lfoAm current LFO AM value
     * @param lfoPm current LFO PM value
     */
    public void setLfoState(int lfoAm, int lfoPm) {
        this.currentLfoAm = lfoAm;
        this.currentLfoPm = lfoPm;
    }

    /**
     * Update EG counter from CPU.
     *
     * @param egCnt   current EG counter value
     * @param egTimer current EG timer value
     */
    public void setEgState(int egCnt, int egTimer) {
        this.egCnt = egCnt;
        this.egTimer = egTimer;
    }

    /**
     * Clean up GPU resources.
     */
    public void cleanup() {
        if (gl == null) return;

        if (synthShader != null) {
            synthShader.cleanup(gl);
            synthShader = null;
        }
        if (envelopeShader != null) {
            envelopeShader.cleanup(gl);
            envelopeShader = null;
        }

        if (sinTableBuffer != null) {
            sinTableBuffer.cleanup(gl);
            sinTableBuffer = null;
        }
        if (tlTableBuffer != null) {
            tlTableBuffer.cleanup(gl);
            tlTableBuffer = null;
        }
        if (egIncTableBuffer != null) {
            egIncTableBuffer.cleanup(gl);
            egIncTableBuffer = null;
        }
        if (egRateTableBuffer != null) {
            egRateTableBuffer.cleanup(gl);
            egRateTableBuffer = null;
        }
        if (dtTableBuffer != null) {
            dtTableBuffer.cleanup(gl);
            dtTableBuffer = null;
        }
        if (lfoPmTableBuffer != null) {
            lfoPmTableBuffer.cleanup(gl);
            lfoPmTableBuffer = null;
        }
        if (channelStateBuffer != null) {
            channelStateBuffer.cleanup(gl);
            channelStateBuffer = null;
        }
        if (operatorEnvStateBuffer != null) {
            operatorEnvStateBuffer.cleanup(gl);
            operatorEnvStateBuffer = null;
        }
        if (outputSamplesBuffer != null) {
            outputSamplesBuffer.cleanup(gl);
            outputSamplesBuffer = null;
        }
        if (envelopeTrajectoryBuffer != null) {
            envelopeTrajectoryBuffer.cleanup(gl);
            envelopeTrajectoryBuffer = null;
        }
        if (lfoTrajectoryBuffer != null) {
            lfoTrajectoryBuffer.cleanup(gl);
            lfoTrajectoryBuffer = null;
        }

        initialized = false;
        gl = null;
        cpuChip = null;
        LOGGER.info("GPU YM2612 synthesizer cleaned up");
    }
}
