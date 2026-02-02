#version 430

// YM2612 Envelope Generator Compute Shader
// Advances envelope state for all 24 operators (6 channels × 4 operators).
// Called every 3 internal samples to match hardware timing.

layout(local_size_x = 24) in;  // One thread per operator

// ============================================================================
// Constants
// ============================================================================

// Envelope states
const int ENV_ATTACK = 0;
const int ENV_DECAY1 = 1;
const int ENV_DECAY2 = 2;
const int ENV_RELEASE = 3;
const int ENV_IDLE = 4;

// Max attenuation index (silent)
const int MAX_ATT_INDEX = 1023;

// SSG-EG threshold
const int SSG_THRESHOLD = 0x200;

// ============================================================================
// Envelope Rate Tables (UBOs)
// ============================================================================

// EG_INC: 152 entries - envelope increment values
layout(std140, binding = 3) uniform EgIncTable {
    int egInc[152];
};

// EG_RATE_SELECT and EG_RATE_SHIFT: 128 entries each
layout(std140, binding = 4) uniform EgRateTable {
    ivec2 egRate[128];  // x = select, y = shift
};

// ============================================================================
// Operator State (SSBO)
// ============================================================================

struct OperatorEnvState {
    int volume;         // Current envelope value (0 = max, 1023 = silent)
    int volOut;         // Cached volume + tll
    int tll;            // Total level
    int curEnv;         // Current envelope state (ENV_ATTACK, etc.)
    int slReg;          // Sustain level register (0-15)

    // EG rate cache (precomputed shift/select for each phase)
    int egShAr;         // Attack rate shift
    int egSelAr;        // Attack rate select
    int egShD1r;        // Decay1 rate shift
    int egSelD1r;       // Decay1 rate select
    int egShD2r;        // Decay2 rate shift
    int egSelD2r;       // Decay2 rate select
    int egShRr;         // Release rate shift
    int egSelRr;        // Release rate select

    // SSG-EG state
    int ssgEg;          // SSG-EG mode
    int ssgn;           // SSG-EG negation flag
    int ssgEnabled;     // SSG-EG enabled

    int key;            // Key on/off state
    int _pad;           // Padding for alignment
};

layout(std430, binding = 8) buffer OperatorEnvStates {
    OperatorEnvState opStates[24];  // 6 channels × 4 operators
};

// ============================================================================
// Uniforms
// ============================================================================

uniform uint egCnt;     // EG counter (1-4095, cycles every 3 samples)

// Sustain level table (precomputed on CPU, uploaded as uniform)
// SL_TAB[i] = sustain level threshold for decay->sustain transition
uniform int slTab[16];

// ============================================================================
// Helper: Get envelope increment from rate tables
// ============================================================================

int getEgIncrement(int egSel, int egSh) {
    // Calculate table index based on egCnt and rate
    int idx = egSel + ((int(egCnt) >> egSh) & 7);
    return egInc[idx];
}

// ============================================================================
// Main Envelope Advance Logic
// Port of Ym2612Chip.advanceEgOperator()
// ============================================================================

void advanceEnvelope(uint opIdx) {
    OperatorEnvState op = opStates[opIdx];

    // Skip if in IDLE state
    if (op.curEnv == ENV_IDLE) return;

    int vol = op.volume;
    int state = op.curEnv;

    switch (state) {
        case ENV_ATTACK: {
            // Attack phase: volume decreases towards 0
            int inc = getEgIncrement(op.egSelAr, op.egShAr);
            if (inc > 0) {
                // GPGX attack formula: vol += ~vol * inc >> 4
                vol += ((~vol) * inc) >> 4;

                if (vol <= 0) {
                    vol = 0;
                    state = ENV_DECAY1;
                }
            }
            break;
        }

        case ENV_DECAY1: {
            // Decay1 phase: volume increases towards sustain level
            // Handle SSG-EG if enabled
            if (op.ssgEnabled != 0) {
                if (vol < SSG_THRESHOLD) {
                    int inc = getEgIncrement(op.egSelD1r, op.egShD1r);
                    vol += inc;
                    // Check for sustain level
                    if (vol >= slTab[op.slReg]) {
                        state = ENV_DECAY2;
                    }
                } else {
                    // SSG-EG special handling
                    int mode = op.ssgEg;
                    if ((mode & 0x01) != 0) {
                        // Hold mode
                        if ((mode & 0x02) != 0) {
                            // Attack bit set - invert
                            opStates[opIdx].ssgn = opStates[opIdx].ssgn ^ 0x3FF;
                        }
                        if ((mode & 0x04) == 0) {
                            state = ENV_RELEASE;
                            vol = MAX_ATT_INDEX;
                        }
                    } else {
                        // Repeat mode
                        vol = 0;
                        if ((mode & 0x02) != 0) {
                            opStates[opIdx].ssgn = opStates[opIdx].ssgn ^ 0x3FF;
                        }
                    }
                }
            } else {
                int inc = getEgIncrement(op.egSelD1r, op.egShD1r);
                vol += inc;
                if (vol >= slTab[op.slReg]) {
                    state = ENV_DECAY2;
                }
            }
            break;
        }

        case ENV_DECAY2: {
            // Decay2/Sustain phase: volume slowly increases
            if (op.ssgEnabled != 0) {
                if (vol < SSG_THRESHOLD) {
                    int inc = getEgIncrement(op.egSelD2r, op.egShD2r);
                    vol += inc;
                } else {
                    int mode = op.ssgEg;
                    if ((mode & 0x01) != 0) {
                        if ((mode & 0x02) != 0) {
                            opStates[opIdx].ssgn = opStates[opIdx].ssgn ^ 0x3FF;
                        }
                        if ((mode & 0x04) == 0) {
                            state = ENV_RELEASE;
                            vol = MAX_ATT_INDEX;
                        }
                    } else {
                        vol = 0;
                        if ((mode & 0x02) != 0) {
                            opStates[opIdx].ssgn = opStates[opIdx].ssgn ^ 0x3FF;
                        }
                        state = ENV_DECAY1;
                    }
                }
            } else {
                int inc = getEgIncrement(op.egSelD2r, op.egShD2r);
                vol += inc;
            }
            break;
        }

        case ENV_RELEASE: {
            // Release phase: volume increases towards max attenuation
            int inc = getEgIncrement(op.egSelRr, op.egShRr);
            vol += inc;
            if (vol >= MAX_ATT_INDEX) {
                vol = MAX_ATT_INDEX;
                state = ENV_IDLE;
            }
            break;
        }
    }

    // Clamp volume
    if (vol > MAX_ATT_INDEX) vol = MAX_ATT_INDEX;
    if (vol < 0) vol = 0;

    // Write back updated state
    opStates[opIdx].volume = vol;
    opStates[opIdx].curEnv = state;

    // Update cached volOut
    int finalVol = vol;
    if (op.ssgEnabled != 0) {
        finalVol = finalVol ^ op.ssgn;
    }
    opStates[opIdx].volOut = finalVol + op.tll;
}

// ============================================================================
// Main Entry Point
// ============================================================================

void main() {
    uint opIdx = gl_GlobalInvocationID.x;

    // Bounds check (24 operators total)
    if (opIdx >= 24) return;

    advanceEnvelope(opIdx);
}
