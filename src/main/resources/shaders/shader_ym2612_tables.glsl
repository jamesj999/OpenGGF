// YM2612 FM Synthesis - Shared Lookup Table Definitions
// These tables are ported from the CPU Ym2612Chip.java implementation
// and are used by both the synthesis and envelope compute shaders.

#ifndef YM2612_TABLES_GLSL
#define YM2612_TABLES_GLSL

// Constants matching Ym2612Chip.java
const int SIN_LEN = 1024;
const int SIN_BITS = 10;
const int SIN_MASK = SIN_LEN - 1;

const int ENV_LEN = 1024;
const int ENV_BITS = 10;

const int TL_RES_LEN = 256;
const int TL_TAB_LEN = 13 * 2 * TL_RES_LEN;  // 6656 entries

// Envelope states
const int ENV_ATTACK = 0;
const int ENV_DECAY1 = 1;
const int ENV_DECAY2 = 2;
const int ENV_RELEASE = 3;
const int ENV_IDLE = 4;

// Output clipping (GPGX-style asymmetric)
const int LIMIT_CH_OUT_POS = 8191;
const int LIMIT_CH_OUT_NEG = -8192;

// ENV_QUIET threshold - when envelope exceeds this, output is 0
const int ENV_QUIET = TL_TAB_LEN >> 3;  // 832

// GPGX EG rate table sentinel for zero-rate
const int EG_RATE_ZERO = 18 * 8;  // 144

// Lookup tables as UBOs (read-only, fast access)
// These are uploaded once at initialization

// SIN_TAB: 1024 entries - indices into TL_TAB (includes sign bit)
// Generated from: SIN_TAB[i] = n * 2 + (m >= 0.0 ? 0 : 1)
// where n is the logarithmic sine amplitude
layout(std140, binding = 1) uniform SinTable {
    int sinTab[1024];  // Padded to 16-byte alignment
};

// TL_TAB: 6656 entries - signed 14-bit output values
// Power-to-linear conversion table with sign
// TL_TAB[x*2] = positive value, TL_TAB[x*2+1] = negative value
layout(std140, binding = 2) uniform TlTable {
    int tlTab[6656];
};

// EG_INC: 152 entries (19 rows × 8 values) - envelope increment table
// Indexed by eg_rate_select[rate] + ((egCnt >> eg_rate_shift[rate]) & 7)
layout(std140, binding = 3) uniform EgIncTable {
    int egInc[152];
};

// EG_RATE_SELECT and EG_RATE_SHIFT combined: 128 entries each
// Stored as ivec2 for efficient access
layout(std140, binding = 4) uniform EgRateTable {
    ivec2 egRate[128];  // x = select, y = shift
};

// DT_TAB: 8×32 = 256 entries - detune table
// DT_TAB[i][k] where i is detune setting (0-7), k is key code (0-31)
layout(std140, binding = 5) uniform DtTable {
    int dtTab[256];
};

// LFO_PM_TABLE: Large precomputed table for pitch modulation
// Size: 128 * 8 * 32 = 32768 entries
// This may need to be split or computed on-the-fly if UBO size is limited
layout(std140, binding = 6) uniform LfoPmTable {
    int lfoPmTab[32768];
};

// Helper function: op_calc - calculate operator output with phase modulation
// Port of Ym2612Chip.opCalc()
int opCalc(int phase, int env, int pm) {
    // GPGX op_calc(): (phase >> SIN_BITS) + (pm >> 1)
    int idx = ((phase >> SIN_BITS) + (pm >> 1)) & SIN_MASK;
    int p = (env << 3) + sinTab[idx];
    if (p >= TL_TAB_LEN) return 0;
    return tlTab[p];
}

// Helper function: op_calc1 - calculate operator output for feedback (no pm shift)
// Port of Ym2612Chip.opCalc1()
int opCalc1(int phase, int env, int pm) {
    // GPGX op_calc1(): (phase >> SIN_BITS) + pm (no >> 1)
    int idx = ((phase >> SIN_BITS) + pm) & SIN_MASK;
    int p = (env << 3) + sinTab[idx];
    if (p >= TL_TAB_LEN) return 0;
    return tlTab[p];
}

// Helper function: op_calc without modulation
int opCalcNoMod(int phase, int env) {
    int p = (env << 3) + sinTab[(phase >> SIN_BITS) & SIN_MASK];
    if (p >= TL_TAB_LEN) return 0;
    return tlTab[p];
}

// Helper function: masked operator calculation
int opCalcMasked(int phase, int env, int pm, int mask) {
    return opCalc(phase, env, pm) & mask;
}

int opCalc1Masked(int phase, int env, int pm, int mask) {
    return opCalc1(phase, env, pm) & mask;
}

int opCalcNoModMasked(int phase, int env, int mask) {
    return opCalcNoMod(phase, env) & mask;
}

#endif // YM2612_TABLES_GLSL
