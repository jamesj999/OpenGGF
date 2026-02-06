# Pattern Vertex Transforms - GPU Optimization Plan

## Overview

Move per-pattern coordinate transformations from CPU to GPU vertex shader. Currently, every pattern rendered performs Y-axis coordinate conversion and texture coordinate flipping on the CPU before upload.

## Current State

**Files:**
- `BatchedPatternRenderer.java:102-129`
- `PatternRenderCommand.java:100-101, 189-202`
- `InstancedPatternRenderer.java:115-129`

**Current CPU work per pattern:**
```java
// Y-coordinate transformation (every pattern)
int screenY = screenHeight - y - 8;

// H-flip texture coordinate swap
if (desc.getHFlip()) {
    float tmp = u0;
    u0 = u1;
    u1 = tmp;
}

// V-flip texture coordinate swap
if (!desc.getVFlip()) {
    float tmp = v0;
    v0 = v1;
    v1 = tmp;
}
```

**Impact:** Thousands of patterns per frame, each performing 3 conditional operations + arithmetic.

## Proposed Solution

### 1. Modify Vertex Attributes

Instead of pre-computing flipped coordinates on CPU, pass flip flags as vertex attributes:

```glsl
// New vertex attributes
layout(location = 3) in float a_flipFlags;  // Packed: bit 0 = hFlip, bit 1 = vFlip

// In vertex shader
bool hFlip = (int(a_flipFlags) & 1) != 0;
bool vFlip = (int(a_flipFlags) & 2) != 0;

// Apply Y-coordinate transform
float screenY = u_screenHeight - a_position.y - 8.0;

// Apply flip to texture coordinates
vec2 texCoord = a_texCoord;
if (hFlip) texCoord.x = 1.0 - texCoord.x;
if (vFlip) texCoord.y = 1.0 - texCoord.y;
```

### 2. Simplify CPU-Side Data Preparation

```java
// Before: CPU computes final coordinates
int screenY = screenHeight - y - 8;
float u0 = desc.getHFlip() ? texU1 : texU0;
// ... more swapping logic

// After: CPU just packs flags
int flipFlags = (desc.getHFlip() ? 1 : 0) | (desc.getVFlip() ? 2 : 0);
vertexData[offset + FLIP_OFFSET] = flipFlags;
// Y and UV uploaded as-is
```

### 3. Use Uniform for Screen Height

```java
// Set once per frame, not per pattern
shaderProgram.setUniform1f(gl, "u_screenHeight", 224.0f);
```

## Implementation Steps

1. **Modify `shader_pattern.glsl`** (or create new shader)
   - Add `a_flipFlags` attribute
   - Add `u_screenHeight` uniform
   - Implement Y-transform and UV flip in vertex shader

2. **Update `BatchedPatternRenderer.java`**
   - Remove Y-coordinate calculation from `addPattern()`
   - Remove UV swap logic
   - Add flip flags to vertex attribute buffer
   - Set screen height uniform once per frame

3. **Update `InstancedPatternRenderer.java`**
   - Same changes as BatchedPatternRenderer
   - Flip flags become per-instance attribute

4. **Update `PatternRenderCommand.java`**
   - Remove transformation code from `getX()`/`getY()` methods
   - Store raw coordinates + flip flags

## Vertex Buffer Layout Change

**Before:**
```
[x, y, u, v, paletteIndex] × 4 vertices per pattern
```

**After:**
```
[x, y, u, v, paletteIndex, flipFlags] × 4 vertices per pattern
```

Or with instancing:
```
Per-instance: [x, y, patternIndex, flipFlags, paletteIndex]
Per-vertex: [localX, localY, baseU, baseV]
```

## Expected Benefits

| Metric | Before | After |
|--------|--------|-------|
| CPU ops per pattern | ~15 | ~3 |
| Conditional branches (CPU) | 2-4 | 0 |
| Data transfer | Same | Same |
| GPU ops per vertex | 0 | ~5 |

**Net effect:** 10-15% reduction in pattern rendering CPU overhead. GPU arithmetic is essentially free at this scale.

## Testing

1. Visual regression: Ensure all sprites render identically
2. Performance profiling: Measure CPU time in `addPattern()` before/after
3. Edge cases:
   - Special stage strip rendering (uses custom V calculations)
   - Water shader interaction
   - High-priority sprite rendering

## Risks

- **Low:** Simple arithmetic moved to GPU
- **Shader compatibility:** Ensure GLSL version supports bitwise ops (GLSL 130+)
- **Special stage:** May need separate code path for strip V-coordinate logic

## Files to Modify

- `src/main/java/uk/co/jamesj999/sonic/graphics/BatchedPatternRenderer.java`
- `src/main/java/uk/co/jamesj999/sonic/graphics/InstancedPatternRenderer.java`
- `src/main/java/uk/co/jamesj999/sonic/graphics/PatternRenderCommand.java`
- `src/main/resources/shaders/shader_pattern.glsl` (or new shader)
