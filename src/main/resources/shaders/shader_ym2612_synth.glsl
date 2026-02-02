#version 430

// YM2612 FM Synthesis Compute Shader
// Generates audio samples using GPU-accelerated FM synthesis.
// Each work item processes one output sample, with all 6 channels rendered in parallel.

layout(local_size_x = 64) in;  // 64 samples per workgroup

// Include shared lookup tables
// Note: In actual GLSL, we can't use #include, so tables are defined inline
// The Java code will concatenate shader_ym2612_tables.glsl with this file

// ============================================================================
// Constants (duplicated from tables for standalone compilation)
// ============================================================================

const int SIN_LEN = 1024;
const int SIN_BITS = 10;
const int SIN_MASK = SIN_LEN - 1;
const int TL_TAB_LEN = 6656;
const int ENV_QUIET = TL_TAB_LEN >> 3;
const int LIMIT_CH_OUT_POS = 8191;
const int LIMIT_CH_OUT_NEG = -8192;
const int OUT_SHIFT = 0;
const int DT_MASK = 0x1FFFF;  // 17-bit mask

// ============================================================================
// Lookup Tables (UBOs)
// ============================================================================

layout(std140, binding = 1) uniform SinTable {
    int sinTab[1024];
};

layout(std140, binding = 2) uniform TlTable {
    int tlTab[6656];
};

layout(std140, binding = 5) uniform DtTable {
    int dtTab[256];  // 8 × 32
};

layout(std140, binding = 6) uniform LfoPmTable {
    int lfoPmTab[32768];
};

// ============================================================================
// Operator State (SSBO - per-operator, updated each batch)
// ============================================================================

struct Operator {
    uint fCnt;      // Phase counter (32-bit)
    int fInc;       // Phase increment
    int volume;     // GPGX-style volume (0 = max, 1023 = silent)
    int tll;        // Total level + key scaling
    int dt1;        // Detune index (0-7)
    int mul;        // Frequency multiplier
    int amMask;     // AM enable mask
    int _pad;       // Padding for alignment
};

// ============================================================================
// Channel State (SSBO - per-channel)
// ============================================================================

struct Channel {
    Operator ops[4];    // 4 operators per channel
    int feedback;       // Feedback shift (31 = disabled)
    int algo;           // Algorithm (0-7)
    int ams;            // AM sensitivity shift
    int pms;            // PM sensitivity
    int kCode;          // Key code for LFO PM
    int blockFnum;      // Block + F-number for LFO PM
    int leftMask;       // Left channel enable
    int rightMask;      // Right channel enable
    int opOut0;         // Feedback buffer slot 0
    int opOut1;         // Feedback buffer slot 1
    int memValue;       // Memory value for algorithms 0-3, 5
    int muted;          // Channel mute flag
};

layout(std430, binding = 0) buffer ChannelState {
    Channel channels[6];
};

// ============================================================================
// Output Buffer (SSBO - stereo samples)
// ============================================================================

layout(std430, binding = 7) writeonly buffer OutputSamples {
    ivec2 samples[];  // x = left, y = right
};

// ============================================================================
// Trajectory Buffers (SSBOs - pre-computed on CPU)
// ============================================================================

// Envelope trajectory: [egTick * 24 + opIdx] = envelope volume
// EG advances every 3 samples; GPU indexes by (sampleIdx + startEgTimer) / 3
layout(std430, binding = 9) readonly buffer EnvelopeTrajectory {
    int envTrajectory[];
};

// LFO trajectory: [sampleIdx * 2] = AM, [sampleIdx * 2 + 1] = PM
layout(std430, binding = 10) readonly buffer LfoTrajectory {
    int lfoTrajectory[];
};

// ============================================================================
// Uniforms
// ============================================================================

uniform uint batchOffset;   // Starting sample index in this batch
uniform uint batchSize;     // Total samples in this batch
uniform int lfoAm;          // Current LFO AM value (0-126) - legacy, unused
uniform int lfoPm;          // Current LFO PM value (0-31) - legacy, unused
uniform int startEgTimer;   // EG timer value at batch start (0, 1, or 2)

// ============================================================================
// Helper Functions
// ============================================================================

int opCalc(int phase, int env, int pm) {
    int idx = ((phase >> SIN_BITS) + (pm >> 1)) & SIN_MASK;
    int p = (env << 3) + sinTab[idx];
    if (p >= TL_TAB_LEN) return 0;
    return tlTab[p];
}

int opCalc1(int phase, int env, int pm) {
    int idx = ((phase >> SIN_BITS) + pm) & SIN_MASK;
    int p = (env << 3) + sinTab[idx];
    if (p >= TL_TAB_LEN) return 0;
    return tlTab[p];
}

int opCalcNoMod(int phase, int env) {
    int p = (env << 3) + sinTab[(phase >> SIN_BITS) & SIN_MASK];
    if (p >= TL_TAB_LEN) return 0;
    return tlTab[p];
}

// Get current envelope value for an operator using trajectory lookup
int getCurrentEnv(int opIdx, int chIdx, uint sampleIdx) {
    Operator op = channels[chIdx].ops[opIdx];

    // Calculate which EG tick this sample falls in
    // EG advances every 3 samples; startEgTimer tells us offset within tick
    uint egTick = (sampleIdx + uint(startEgTimer)) / 3u;

    // Flatten operator index (channel * 4 + operator)
    int flatOpIdx = chIdx * 4 + opIdx;

    // Look up pre-computed envelope volume from trajectory
    int env = envTrajectory[egTick * 24u + uint(flatOpIdx)] + op.tll;

    // Apply LFO AM from trajectory
    int lfoAm = lfoTrajectory[sampleIdx * 2u];
    int am = lfoAm >> channels[chIdx].ams;
    env += (am & op.amMask);

    return env;
}

// ============================================================================
// Algorithm Implementations
// ============================================================================

int doAlgo(int chIdx, int in0, int in1, int in2, int in3,
           int env0, int env1, int env2, int env3) {
    Channel ch = channels[chIdx];

    // Check if M1 is quiet
    bool s0Quiet = env0 >= ENV_QUIET;

    // Calculate feedback for M1
    int fb = (ch.feedback < SIN_BITS) ? (ch.opOut0 + ch.opOut1) >> ch.feedback : 0;
    int s0_out = s0Quiet ? 0 : opCalc1(in0, env0, fb);

    // Update feedback buffer (will be written back)
    channels[chIdx].opOut1 = ch.opOut0;
    channels[chIdx].opOut0 = s0_out;

    int result = 0;

    switch (ch.algo) {
        case 0: {
            // M1 -> C1 -> MEM -> M2 -> C2
            int m2 = ch.memValue;
            int mem = opCalc(in1, env1, s0_out);
            int m2_out = opCalc(in2, env2, m2);
            result = opCalc(in3, env3, m2_out) >> OUT_SHIFT;
            channels[chIdx].memValue = mem;
            break;
        }
        case 1: {
            // (M1 + C1) -> MEM -> M2 -> C2
            int m2 = ch.memValue;
            int c1_out = opCalcNoMod(in1, env1);
            int mem = s0_out + c1_out;
            int m2_out = opCalc(in2, env2, m2);
            result = opCalc(in3, env3, m2_out) >> OUT_SHIFT;
            channels[chIdx].memValue = mem;
            break;
        }
        case 2: {
            // M1 + (C1 -> MEM -> M2) -> C2
            int m2 = ch.memValue;
            int mem = opCalcNoMod(in1, env1);
            int m2_out = opCalc(in2, env2, m2);
            result = opCalc(in3, env3, s0_out + m2_out) >> OUT_SHIFT;
            channels[chIdx].memValue = mem;
            break;
        }
        case 3: {
            // M1 -> C1 -> MEM + M2 -> C2
            int c2 = ch.memValue;
            int mem = opCalc(in1, env1, s0_out);
            int m2_out = opCalcNoMod(in2, env2);
            c2 += m2_out;
            result = opCalc(in3, env3, c2) >> OUT_SHIFT;
            channels[chIdx].memValue = mem;
            break;
        }
        case 4: {
            // M1 -> C1 + M2 -> C2
            int c1_out = opCalc(in1, env1, s0_out);
            int m2_out = opCalcNoMod(in2, env2);
            int c2_out = opCalc(in3, env3, m2_out);
            result = (c1_out + c2_out) >> OUT_SHIFT;
            break;
        }
        case 5: {
            // M1 -> (C1 + M2 + C2) all use M1 modulation
            int m2 = ch.memValue;
            int c1_out = opCalc(in1, env1, s0_out);
            int m2_out = opCalc(in2, env2, m2);
            int c2_out = opCalc(in3, env3, s0_out);
            result = (c1_out + m2_out + c2_out) >> OUT_SHIFT;
            channels[chIdx].memValue = s0_out;
            break;
        }
        case 6: {
            // M1 -> C1 + M2 + C2 (carriers)
            int c1_out = opCalc(in1, env1, s0_out);
            int m2_out = opCalcNoMod(in2, env2);
            int c2_out = opCalcNoMod(in3, env3);
            result = (c1_out + m2_out + c2_out) >> OUT_SHIFT;
            break;
        }
        case 7: {
            // All carriers, no modulation
            int c1_out = opCalcNoMod(in1, env1);
            int m2_out = opCalcNoMod(in2, env2);
            int c2_out = opCalcNoMod(in3, env3);
            result = (s0_out + c1_out + m2_out + c2_out) >> OUT_SHIFT;
            break;
        }
    }

    // Clamp output
    if (result > LIMIT_CH_OUT_POS) result = LIMIT_CH_OUT_POS;
    else if (result < LIMIT_CH_OUT_NEG) result = LIMIT_CH_OUT_NEG;

    return result;
}

// ============================================================================
// Render one channel
// ============================================================================

int renderChannel(int chIdx, uint sampleIdx) {
    Channel ch = channels[chIdx];

    if (ch.muted != 0) return 0;

    // Capture phase counters BEFORE increment (GET_CURRENT_PHASE)
    // Slot order: S0=op0, S1=op2, S2=op1, S3=op3
    int in0 = int(ch.ops[0].fCnt);
    int in1 = int(ch.ops[2].fCnt);
    int in2 = int(ch.ops[1].fCnt);
    int in3 = int(ch.ops[3].fCnt);

    // UPDATE_PHASE - increment phase counters
    for (int op = 0; op < 4; op++) {
        channels[chIdx].ops[op].fCnt += uint(ch.ops[op].fInc);
    }

    // GET_CURRENT_ENV using trajectory lookup
    // Note: env values are in ops[] order, need to reorder for algo
    int env0 = getCurrentEnv(0, chIdx, sampleIdx);  // S0
    int env2 = getCurrentEnv(1, chIdx, sampleIdx);  // S2 (ops[1])
    int env1 = getCurrentEnv(2, chIdx, sampleIdx);  // S1 (ops[2])
    int env3 = getCurrentEnv(3, chIdx, sampleIdx);  // S3

    return doAlgo(chIdx, in0, in1, in2, in3, env0, env1, env2, env3);
}

// ============================================================================
// Main Entry Point
// ============================================================================

void main() {
    uint sampleIdx = gl_GlobalInvocationID.x;

    // Bounds check
    if (sampleIdx >= batchSize) return;

    // Render all 6 channels and accumulate
    // Envelope and LFO values come from trajectory buffers indexed by sampleIdx
    int leftSum = 0;
    int rightSum = 0;

    for (int ch = 0; ch < 6; ch++) {
        int chOut = renderChannel(ch, sampleIdx);

        if (channels[ch].leftMask != 0) leftSum += chOut;
        if (channels[ch].rightMask != 0) rightSum += chOut;
    }

    // Write stereo output
    samples[sampleIdx] = ivec2(leftSum, rightSum);
}
