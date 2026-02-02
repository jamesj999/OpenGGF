# Full Audio GPU Synthesis - Experimental Plan

## Overview

**EXPERIMENTAL:** Move the entire audio synthesis pipeline (YM2612 FM + PSG) to GPU compute shaders. This is a research-level endeavor with significant technical challenges around latency and real-time requirements.

## Why This Is Experimental

Audio synthesis has unique constraints that make GPU acceleration challenging:

1. **Strict latency requirements:** Audio must deliver samples every 10-20ms
2. **GPU-CPU synchronization:** Data transfer introduces latency
3. **Sequential dependencies:** Some audio state machines are inherently serial
4. **Small batch sizes:** Unlike graphics, audio processes small chunks

This plan explores whether GPU synthesis is viable for this engine.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     Audio Thread (CPU)                       │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │   SMPS      │───▶│  Register   │───▶│  GPU Sync   │     │
│  │  Sequencer  │    │   Queue     │    │   Point     │     │
│  └─────────────┘    └─────────────┘    └─────────────┘     │
│                                               │              │
└───────────────────────────────────────────────│──────────────┘
                                                │
                                                ▼
┌─────────────────────────────────────────────────────────────┐
│                     GPU Compute Pipeline                     │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │  YM2612     │───▶│    PSG      │───▶│   Mixer/    │     │
│  │  Synthesis  │    │  Synthesis  │    │  Resampler  │     │
│  └─────────────┘    └─────────────┘    └─────────────┘     │
│         │                  │                  │              │
│         └──────────────────┴──────────────────┘              │
│                           │                                  │
│                           ▼                                  │
│                   ┌─────────────┐                           │
│                   │   Output    │                           │
│                   │   Buffer    │                           │
│                   └─────────────┘                           │
└───────────────────────────────────────────────│──────────────┘
                                                │
                                                ▼
                                    ┌─────────────────────┐
                                    │   Audio Callback    │
                                    │   (Playback)        │
                                    └─────────────────────┘
```

## Component Breakdown

### 1. YM2612 FM Synthesis (Compute Shader)

**State per channel (6 channels):**
```glsl
struct YM2612Channel {
    // Operators (4 per channel)
    Operator ops[4];

    // Channel state
    uint algorithm;       // 0-7
    uint feedback;        // 0-7 (OP1 self-modulation)
    uint panLeft;
    uint panRight;
    int op1FeedbackHistory[2];

    // Frequency
    uint blockFnum;       // Block + F-number
    uint kcode;           // Key code for EG rates
};

struct Operator {
    // Phase generator
    uint phaseCnt;        // Current phase (32-bit)
    uint phaseInc;        // Phase increment per sample

    // Envelope generator
    int egVolume;         // Current volume (0-1023)
    uint egState;         // ATTACK/DECAY1/DECAY2/SUSTAIN/RELEASE
    uint egRates[4];      // AR, D1R, D2R, RR
    uint sustainLevel;
    uint totalLevel;

    // Modulation
    uint detune;
    uint multiple;
    uint ssgEg;
    bool keyOn;
};
```

**Compute shader organization:**
```glsl
// shader_ym2612_compute.glsl
#version 430

layout(local_size_x = 256) in;  // 256 samples per workgroup

// Channel state (persistent between dispatches)
layout(std430, binding = 0) buffer ChannelState {
    YM2612Channel channels[6];
};

// Lookup tables (read-only)
layout(std430, binding = 1) readonly buffer SinTable { int sinTab[1024]; };
layout(std430, binding = 2) readonly buffer TlTable { int tlTab[6656]; };
layout(std430, binding = 3) readonly buffer DtTable { int dtTab[32][32]; };
layout(std430, binding = 4) readonly buffer EgIncTable { uint egInc[19*8]; };

// Register write queue (changes from SMPS)
layout(std430, binding = 5) readonly buffer RegisterQueue {
    uint regWrites[];  // Packed: (sample_offset << 16) | (addr << 8) | data
};

// Output buffer
layout(std430, binding = 6) writeonly buffer OutputBuffer {
    vec2 samples[];   // Stereo output
};

uniform uint u_sampleCount;
uniform uint u_regWriteCount;
uniform uint u_globalEgCounter;
uniform uint u_lfoCounter;

// FM operator calculation
int opCalc(uint phase, int env, int pm) {
    uint p = uint((env << 3) + sinTab[(phase >> 10) & 0x3FF]);
    if (p >= 6656u) return 0;
    return tlTab[p];
}

// Process single sample for all channels
vec2 synthesizeSample(uint sampleIdx) {
    // Apply any register writes scheduled for this sample
    applyRegisterWrites(sampleIdx);

    // Advance envelopes (every 3 samples)
    if (((u_globalEgCounter + sampleIdx) % 3u) == 0u) {
        advanceEnvelopes();
    }

    // Advance LFO
    uint lfo = advanceLfo(sampleIdx);

    // Render all channels
    int left = 0, right = 0;
    for (int c = 0; c < 6; c++) {
        int out = renderChannel(channels[c], lfo);
        if (channels[c].panLeft != 0u) left += out;
        if (channels[c].panRight != 0u) right += out;
    }

    return vec2(float(left), float(right)) / 32768.0;
}

void main() {
    uint sampleIdx = gl_GlobalInvocationID.x;
    if (sampleIdx >= u_sampleCount) return;

    samples[sampleIdx] = synthesizeSample(sampleIdx);
}
```

### 2. PSG Synthesis (Compute Shader)

```glsl
// shader_psg_compute.glsl
#version 430

layout(local_size_x = 256) in;

struct PSGState {
    // Tone channels (3)
    uint toneCounters[3];
    uint tonePeriods[3];
    int toneOutputs[3];

    // Noise channel
    uint noiseCounter;
    uint noisePeriod;
    uint lfsr;           // 16-bit shift register
    bool noiseWhite;     // White vs periodic noise

    // Volumes
    uint volumes[4];     // 0-15, 15 = silent
};

layout(std430, binding = 0) buffer State { PSGState psg; };
layout(std430, binding = 1) readonly buffer VolumeTable { float volTab[16]; };
layout(std430, binding = 2) writeonly buffer Output { vec2 samples[]; };

// LFSR feedback (white noise: XOR bits 0 and 3)
uint lfsrStep(uint lfsr, bool white) {
    uint feedback = white ? ((lfsr ^ (lfsr >> 3)) & 1u) : (lfsr & 1u);
    return (lfsr >> 1) | (feedback << 15);
}

vec2 synthesizeSample(uint sampleIdx) {
    float output = 0.0;

    // Tone channels
    for (int i = 0; i < 3; i++) {
        psg.toneCounters[i]--;
        if (psg.toneCounters[i] == 0u) {
            psg.toneCounters[i] = psg.tonePeriods[i];
            psg.toneOutputs[i] = -psg.toneOutputs[i];
        }
        output += float(psg.toneOutputs[i]) * volTab[psg.volumes[i]];
    }

    // Noise channel
    psg.noiseCounter--;
    if (psg.noiseCounter == 0u) {
        psg.noiseCounter = psg.noisePeriod;
        psg.lfsr = lfsrStep(psg.lfsr, psg.noiseWhite);
    }
    float noiseOut = ((psg.lfsr & 1u) != 0u) ? 1.0 : -1.0;
    output += noiseOut * volTab[psg.volumes[3]];

    return vec2(output * 0.25);  // Mix to stereo
}

void main() {
    uint sampleIdx = gl_GlobalInvocationID.x;
    samples[sampleIdx] = synthesizeSample(sampleIdx);
}
```

### 3. Mixer/Resampler (Compute Shader)

```glsl
// shader_audio_mixer.glsl
#version 430

layout(local_size_x = 64) in;

layout(std430, binding = 0) readonly buffer YM2612Output { vec2 ym2612[]; };
layout(std430, binding = 1) readonly buffer PSGOutput { vec2 psg[]; };
layout(std430, binding = 2) readonly buffer ResampleKernel { float kernel[32*16]; };
layout(std430, binding = 3) writeonly buffer FinalOutput { vec2 output[]; };

uniform float u_ym2612Volume;
uniform float u_psgVolume;
uniform float u_resampleRatio;  // 53267 / 44100 ≈ 1.208

void main() {
    uint outIdx = gl_GlobalInvocationID.x;

    // Calculate source position with fractional part
    float srcPos = float(outIdx) * u_resampleRatio;
    uint srcIdx = uint(srcPos);
    float frac = fract(srcPos);

    // Polyphase resampling (16-tap)
    uint phase = uint(frac * 32.0);
    vec2 sum = vec2(0.0);
    for (int i = 0; i < 16; i++) {
        uint idx = srcIdx + uint(i) - 8u;
        vec2 sample = ym2612[idx] * u_ym2612Volume + psg[idx] * u_psgVolume;
        sum += sample * kernel[phase * 16u + uint(i)];
    }

    output[outIdx] = clamp(sum, vec2(-1.0), vec2(1.0));
}
```

### 4. CPU Coordinator

```java
public class GpuAudioSynthesizer {
    // Double-buffered output
    private static final int BATCH_SIZE = 512;  // ~11.6ms at 44.1kHz
    private float[][] outputBuffers = new float[2][BATCH_SIZE * 2];
    private int currentBuffer = 0;
    private int readPosition = 0;

    // GPU resources
    private int ym2612StateSSBO;
    private int psgStateSSBO;
    private int registerQueueSSBO;
    private int outputSSBO;

    private ShaderProgram ym2612Shader;
    private ShaderProgram psgShader;
    private ShaderProgram mixerShader;

    // Register write queue (from SMPS sequencer)
    private Queue<RegisterWrite> pendingWrites = new ConcurrentLinkedQueue<>();

    public void queueRegisterWrite(int addr, int data, int sampleOffset) {
        pendingWrites.add(new RegisterWrite(addr, data, sampleOffset));
    }

    public void renderBatch() {
        // 1. Upload register writes
        uploadRegisterQueue();

        // 2. Dispatch YM2612 synthesis
        ym2612Shader.use(gl);
        ym2612Shader.setUniform1ui(gl, "u_sampleCount", BATCH_SIZE);
        gl.glDispatchCompute((BATCH_SIZE + 255) / 256, 1, 1);
        gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        // 3. Dispatch PSG synthesis
        psgShader.use(gl);
        gl.glDispatchCompute((BATCH_SIZE + 255) / 256, 1, 1);
        gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        // 4. Dispatch mixer/resampler
        int outputSamples = (int)(BATCH_SIZE / RESAMPLE_RATIO);
        mixerShader.use(gl);
        gl.glDispatchCompute((outputSamples + 63) / 64, 1, 1);
        gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        // 5. Read back to CPU buffer
        int nextBuffer = 1 - currentBuffer;
        gl.glBindBuffer(GL_SHADER_STORAGE_BUFFER, outputSSBO);
        gl.glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0,
            outputBuffers[nextBuffer].length * 4,
            FloatBuffer.wrap(outputBuffers[nextBuffer]));

        // Swap buffers
        currentBuffer = nextBuffer;
        readPosition = 0;
    }

    // Called from audio callback
    public int getSamples(float[] dest, int offset, int count) {
        int available = BATCH_SIZE - readPosition;
        if (available <= 0) {
            // Need more samples - this shouldn't happen in steady state
            return 0;
        }

        int toCopy = Math.min(available, count);
        System.arraycopy(outputBuffers[currentBuffer], readPosition * 2,
                        dest, offset, toCopy * 2);
        readPosition += toCopy;
        return toCopy;
    }
}
```

## Latency Analysis

### Current CPU Path
```
SMPS tick → YM2612 render → PSG render → Mix → Audio callback
           └────────────────────────────────────────────────┘
                              ~1-2ms CPU time
```

### Proposed GPU Path
```
SMPS tick → Queue writes → GPU dispatch → GPU render → Readback → Audio callback
           └──────────────────────────────────────────────────────────────────┘
                                    ~5-15ms total latency
```

**Latency breakdown:**
- Register queue upload: ~0.1ms
- GPU dispatch overhead: ~0.5ms
- Compute execution: ~0.5ms (for 512 samples)
- Memory barrier: ~0.1ms
- Readback: ~0.5-2ms (main bottleneck)
- **Total added latency: ~2-5ms**

### Mitigation: Async Readback

Use persistent mapped buffers with fence sync:

```java
// Create persistent mapped buffer
int flags = GL_MAP_READ_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT;
gl.glBufferStorage(GL_SHADER_STORAGE_BUFFER, size, null, flags);
FloatBuffer mappedBuffer = gl.glMapBufferRange(GL_SHADER_STORAGE_BUFFER,
    0, size, flags);

// After dispatch
gl.glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);

// In audio callback
int status = gl.glClientWaitSync(fence, GL_SYNC_FLUSH_COMMANDS_BIT, 0);
if (status == GL_ALREADY_SIGNALED || status == GL_CONDITION_SATISFIED) {
    // Data ready - copy from mapped buffer
}
```

## Challenges & Solutions

### Challenge 1: Sample-Accurate Register Writes

**Problem:** SMPS can write registers mid-batch.

**Solution:** Include sample offset in register queue, apply in shader:

```glsl
void applyRegisterWrites(uint currentSample) {
    for (uint i = 0; i < u_regWriteCount; i++) {
        uint write = regWrites[i];
        uint sampleOffset = write >> 16;
        if (sampleOffset == currentSample) {
            uint addr = (write >> 8) & 0xFF;
            uint data = write & 0xFF;
            applyRegister(addr, data);
        }
    }
}
```

### Challenge 2: Envelope State Machine

**Problem:** EG state transitions depend on previous state.

**Solution:** Process envelopes in separate sequential pass, or accept 3-sample granularity (EG updates every 3 samples anyway).

### Challenge 3: DAC Channel

**Problem:** YM2612 channel 6 can be used for 8-bit PCM playback.

**Solution:** Handle DAC samples in register queue with special flag:

```glsl
if (addr == DAC_DATA_ADDR && dacEnabled) {
    dacSample = float(data - 128) / 128.0;
}
```

### Challenge 4: Thread Divergence

**Problem:** 8 FM algorithms have different operator routing.

**Solution:** Use switch statement (GPU handles divergence well for small switches) or separate kernel per algorithm type.

## Performance Expectations

| Metric | CPU | GPU (Estimated) |
|--------|-----|-----------------|
| Samples/sec capability | ~100k | ~10M |
| Batch size | 1 | 256-1024 |
| Latency | ~1ms | ~5-10ms |
| CPU usage (audio) | 5-10% | <1% |

**Verdict:** GPU has massive throughput advantage but latency overhead. Best for:
- Offline rendering
- Systems where 10ms latency is acceptable
- Future: Multi-channel audio (dozens of voices)

## Testing Plan

1. **Accuracy Testing:**
   - Render test sequences on both CPU and GPU
   - Byte-compare output samples
   - Verify all 8 FM algorithms
   - Test envelope shapes and timing

2. **Latency Measurement:**
   - Instrument audio callback timing
   - Measure GPU dispatch to readback time
   - Test with various batch sizes

3. **Stability:**
   - Long-running audio playback
   - Stress test with rapid note changes
   - Verify no buffer underruns

4. **A/B Listening:**
   - Blind comparison with CPU synthesis
   - Check for audio artifacts

## Files to Create

**Shaders:**
- `src/main/resources/shaders/shader_ym2612_compute.glsl`
- `src/main/resources/shaders/shader_psg_compute.glsl`
- `src/main/resources/shaders/shader_audio_mixer.glsl`

**Java Classes:**
- `src/main/java/uk/co/jamesj999/sonic/audio/gpu/GpuAudioSynthesizer.java`
- `src/main/java/uk/co/jamesj999/sonic/audio/gpu/RegisterWriteQueue.java`
- `src/main/java/uk/co/jamesj999/sonic/audio/gpu/GpuYm2612State.java`
- `src/main/java/uk/co/jamesj999/sonic/audio/gpu/GpuPsgState.java`

## Conclusion

Full GPU audio synthesis is **technically feasible** but introduces latency that may be unacceptable for real-time gameplay. Recommended approach:

1. **Phase 1:** Implement and benchmark with configurable batch sizes
2. **Phase 2:** If latency acceptable (~10ms), use as default
3. **Phase 3:** If latency problematic, keep as option for:
   - Recording/video capture
   - Systems with fast GPU-CPU interconnect
   - Future multi-voice audio features

The simpler **FM Operator Compute Shader** (plan 05) is recommended as the safer first step before attempting full GPU audio.
