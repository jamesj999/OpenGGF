# FM Synthesis Compute Shader - GPU Optimization Plan

## Overview

Accelerate YM2612 FM synthesis by running all 24 operators (6 channels × 4 operators) in parallel on GPU compute shader. Currently, FM synthesis is the most CPU-intensive part of audio rendering.

## Current State

**Files:**
- `Ym2612Chip.java` (2121 lines)
- Key methods:
  - `renderChannel()` (lines 1479-1564)
  - `opCalc()` (lines 1590-1636)
  - `advanceEgOperator()` (lines 1238-1326)
  - `doAlgo()` (lines 1638-1751)

**Current CPU work per sample:**
```java
// For each of 6 channels:
for (Channel ch : channels) {
    // Phase accumulation for 4 operators
    for (Operator op : ch.ops) {
        op.fCnt += op.fInc;  // Phase advance
    }

    // LFO modulation (shared)
    int pm = LFO_PM_TABLE[...];

    // Envelope generation for 4 operators
    for (Operator op : ch.ops) {
        advanceEgOperator(op);  // State machine
    }

    // FM algorithm output (operator interconnection)
    int output = doAlgo(ch);  // 8 different algorithms
}
```

**Key computations:**
1. **Phase accumulation:** `fCnt += fInc` (24 adds/sample)
2. **Sine lookup:** `SIN_TAB[(phase >> SIN_BITS) & SIN_MASK]` (24 lookups)
3. **Envelope:** State machine with exponential/linear curves (24 state updates)
4. **FM modulation:** Operators feed into each other per algorithm topology

## Proposed Solution

### Architecture: Batch Processing

Process N samples (e.g., 256-1024) on GPU, stream back to audio buffer.

```
Audio callback requests 512 samples
    → GPU computes 512 × 6 channels × 4 operators
    → GPU outputs 512 stereo samples
    → CPU receives and queues for playback
```

### 1. Data Structures

**Channel/Operator State (SSBO):**
```glsl
struct Operator {
    uint fCnt;          // Phase counter
    uint fInc;          // Phase increment
    int volume;         // Envelope volume (0-1023)
    uint curEnv;        // Envelope state (ATTACK/DECAY1/DECAY2/RELEASE)
    uint egShAr;        // Attack shift
    uint egSelAr;       // Attack select
    // ... other EG parameters
    uint dt1;           // Detune
    uint mul;           // Multiplier
    uint tl;            // Total level
    uint ssgEg;         // SSG-EG flags
};

struct Channel {
    Operator ops[4];
    uint algorithm;     // 0-7
    uint feedback;      // 0-7
    uint panLeft;       // 0 or 1
    uint panRight;      // 0 or 1
    int op1Feedback[2]; // Feedback history
};

layout(std430, binding = 0) buffer ChannelBuffer {
    Channel channels[6];
};
```

**Lookup Tables (Uniform Buffers):**
```glsl
layout(std140, binding = 1) uniform SinTable {
    int SIN_TAB[1024];      // Sine wave
};

layout(std140, binding = 2) uniform TlTable {
    int TL_TAB[6656];       // Total level
};

layout(std140, binding = 3) uniform EgIncTable {
    uint EG_INC[19 * 8];    // Envelope increments
};

layout(std140, binding = 4) uniform LfoTables {
    int LFO_PM_TABLE[128 * 32];  // PM depth
    uint LFO_AM_TABLE[128];       // AM depth
};
```

**Output Buffer:**
```glsl
layout(std430, binding = 5) writeonly buffer OutputBuffer {
    vec2 samples[];  // Stereo pairs
};
```

### 2. Compute Shader

```glsl
#version 430
layout(local_size_x = 64) in;  // 64 samples per work group

uniform uint u_sampleCount;
uniform uint u_egCnt;        // Global envelope counter
uniform uint u_lfoCnt;       // Global LFO counter

// Operator calculation (matches CPU opCalc)
int opCalc(uint phase, uint env, int pm) {
    uint p = ((env << 3) + SIN_TAB[(phase >> SIN_BITS) & SIN_MASK]);
    if (p >= TL_TAB_LEN) return 0;
    return TL_TAB[p];
}

// Single channel output for one sample
int renderChannel(inout Channel ch, uint sampleIdx) {
    // Advance phases
    for (int i = 0; i < 4; i++) {
        ch.ops[i].fCnt += ch.ops[i].fInc;
    }

    // Get envelope volumes
    int env[4];
    for (int i = 0; i < 4; i++) {
        env[i] = ch.ops[i].volume + ch.ops[i].tl;
    }

    // Algorithm-specific operator routing
    int out = 0;
    switch (ch.algorithm) {
        case 0:
            // [OP1] → [OP2] → [OP3] → [OP4] → out
            int op1 = opCalc(ch.ops[0].fCnt, env[0], ...);
            int op2 = opCalc(ch.ops[1].fCnt + op1, env[1], ...);
            int op3 = opCalc(ch.ops[2].fCnt + op2, env[2], ...);
            out = opCalc(ch.ops[3].fCnt + op3, env[3], ...);
            break;
        case 1:
            // [OP1 + OP2] → [OP3] → [OP4] → out
            // ...
        // ... cases 2-7
    }

    return out;
}

void main() {
    uint sampleIdx = gl_GlobalInvocationID.x;
    if (sampleIdx >= u_sampleCount) return;

    // Render all 6 channels
    int left = 0, right = 0;
    for (int c = 0; c < 6; c++) {
        int out = renderChannel(channels[c], sampleIdx);
        if (channels[c].panLeft != 0) left += out;
        if (channels[c].panRight != 0) right += out;
    }

    // Normalize and write output
    samples[sampleIdx] = vec2(
        float(left) / 32768.0,
        float(right) / 32768.0
    );
}
```

### 3. Envelope Processing (Separate Pass)

Envelope state machines have dependencies between samples, so process in separate dispatch:

```glsl
// envelope_compute.glsl
layout(local_size_x = 24) in;  // 6 channels × 4 operators

void main() {
    uint opIdx = gl_GlobalInvocationID.x;
    uint chIdx = opIdx / 4;
    uint slotIdx = opIdx % 4;

    Operator op = channels[chIdx].ops[slotIdx];

    // Advance envelope (state machine)
    if ((u_egCnt & ((1u << op.egShAr) - 1u)) == 0u) {
        uint inc = EG_INC[op.egSelAr + ((u_egCnt >> op.egShAr) & 7u)];

        switch (op.curEnv) {
            case ENV_ATTACK:
                op.volume += ((~op.volume) * int(inc)) >> 4;
                if (op.volume <= 0) {
                    op.volume = 0;
                    op.curEnv = ENV_DECAY1;
                }
                break;
            case ENV_DECAY1:
                op.volume += int(inc);
                if (op.volume >= op.sustainLevel) {
                    op.curEnv = ENV_DECAY2;
                }
                break;
            // ... other states
        }
    }

    channels[chIdx].ops[slotIdx] = op;
}
```

### 4. CPU Integration

```java
public class Ym2612GpuSynthesizer {
    private static final int BATCH_SIZE = 512;

    private int channelSSBO;
    private int sinTableUBO;
    private int outputSSBO;
    private ShaderProgram synthShader;
    private ShaderProgram envelopeShader;

    private float[] outputBuffer = new float[BATCH_SIZE * 2];
    private int outputReadPos = 0;
    private int outputWritePos = 0;

    public void renderSamples(float[] dest, int count) {
        while (count > 0) {
            // Refill GPU buffer if needed
            if (outputReadPos >= outputWritePos) {
                dispatchGpuBatch();
            }

            // Copy from GPU output to destination
            int available = outputWritePos - outputReadPos;
            int toCopy = Math.min(available, count);
            System.arraycopy(outputBuffer, outputReadPos * 2,
                           dest, 0, toCopy * 2);
            outputReadPos += toCopy;
            count -= toCopy;
        }
    }

    private void dispatchGpuBatch() {
        // 1. Sync channel state to GPU
        uploadChannelState();

        // 2. Dispatch envelope update (24 operators)
        envelopeShader.use(gl);
        gl.glDispatchCompute(1, 1, 1);  // 24 threads
        gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        // 3. Dispatch synthesis (BATCH_SIZE samples)
        synthShader.use(gl);
        synthShader.setUniform1ui(gl, "u_sampleCount", BATCH_SIZE);
        gl.glDispatchCompute((BATCH_SIZE + 63) / 64, 1, 1);
        gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        // 4. Read back output
        gl.glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0,
            outputBuffer.length * 4, FloatBuffer.wrap(outputBuffer));

        outputReadPos = 0;
        outputWritePos = BATCH_SIZE;
    }
}
```

## Implementation Steps

1. **Create lookup table UBOs**
   - Export SIN_TAB, TL_TAB, EG_INC from Java to GPU-friendly format
   - Upload once at initialization

2. **Create channel state SSBO**
   - Mirror `Channel` and `Operator` Java classes in GLSL structs
   - Sync state when registers change

3. **Create synthesis compute shader**
   - `shader_ym2612_synth.glsl`
   - Implement all 8 FM algorithms
   - Handle feedback correctly

4. **Create envelope compute shader**
   - `shader_ym2612_envelope.glsl`
   - State machine for all 24 operators

5. **Create GPU synthesizer class**
   - `Ym2612GpuSynthesizer.java`
   - Batch rendering with double-buffering

6. **Integrate with audio callback**
   - Replace CPU synthesis path
   - Maintain fallback for compatibility

## Expected Benefits

| Metric | CPU | GPU |
|--------|-----|-----|
| Operators per sample | 24 sequential | 24 parallel |
| Samples per dispatch | 1 | 512-1024 |
| Sine lookups | 24 cache misses | 24 texture fetches (cached) |
| Throughput | ~50k samples/sec | ~5M samples/sec |

**Note:** Actual benefit depends on GPU-CPU data transfer overhead.

## Latency Considerations

**Critical:** Audio requires low latency (~10-20ms).

**Mitigation strategies:**
1. **Small batch sizes:** 256-512 samples (5-10ms at 44.1kHz)
2. **Double buffering:** Render batch N+1 while playing batch N
3. **Persistent mapped buffers:** Reduce upload/download overhead
4. **Async compute:** Overlap GPU work with CPU audio mixing

```
Frame N:
├── GPU renders batch N+1
├── CPU plays batch N
└── Audio callback requests batch N+2 (queued)
```

## Testing

1. **Accuracy:**
   - Byte-for-byte comparison with CPU synthesis
   - Test all 8 FM algorithms
   - Verify envelope shapes

2. **Latency:**
   - Measure end-to-end audio latency
   - Compare with CPU path

3. **Stability:**
   - Long-running audio playback
   - Rapid instrument changes
   - Edge cases (key on/off timing)

## Risks

- **High:** GPU-CPU sync latency may negate benefits
- **Medium:** Algorithm complexity (8 different topologies)
- **Medium:** Envelope state machine correctness
- **Mitigation:** Keep CPU path as fallback, extensive testing

## Files to Create/Modify

**Create:**
- `src/main/resources/shaders/shader_ym2612_synth.glsl`
- `src/main/resources/shaders/shader_ym2612_envelope.glsl`
- `src/main/java/uk/co/jamesj999/sonic/audio/synth/Ym2612GpuSynthesizer.java`

**Modify:**
- `src/main/java/uk/co/jamesj999/sonic/audio/synth/Ym2612Chip.java`
- `src/main/java/uk/co/jamesj999/sonic/audio/AudioManager.java`
