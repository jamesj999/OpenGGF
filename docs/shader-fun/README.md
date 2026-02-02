# GPU Shader Optimization Plans

This directory contains detailed implementation plans for moving CPU-bound computations to GPU shaders. Each plan was generated after a comprehensive analysis of the engine's subsystems.

## Plan Index

### Tier 1: High Impact, Low Complexity

| # | Plan | Subsystem | Estimated Benefit |
|---|------|-----------|-------------------|
| 01 | [Pattern Vertex Transforms](01-pattern-vertex-transforms.md) | Graphics | 10-15% pattern rendering speedup |
| 02 | [Collision AABB Batch](02-collision-aabb-batch.md) | Physics | 20-50 parallel collision tests |
| 03 | [Touch Response Batch](03-touch-response-batch.md) | Physics | 50+ parallel overlap tests |
| 04 | [Debug Glyph Batch](04-debug-glyph-batch.md) | Debug/UI | 99% draw call reduction in debug mode |

### Tier 2: High Impact, Medium Complexity

| # | Plan | Subsystem | Estimated Benefit |
|---|------|-----------|-------------------|
| 05 | [FM Synthesis Compute](05-fm-synthesis-compute.md) | Audio | 24 parallel FM operators |
| 06 | [Terrain Sensor Batch](06-terrain-sensor-batch.md) | Physics | 4-8 parallel sensor probes |
| 07 | [Sprite Geometry Shader](07-sprite-geometry-shader.md) | Graphics | 50-70% sprite rendering speedup |
| 08 | [Animated Pattern LUT](08-animated-pattern-lut.md) | Level | Eliminate CPU pattern copies |

### Tier 3: Experimental

| # | Plan | Subsystem | Notes |
|---|------|-----------|-------|
| 09 | [Full Audio GPU](09-full-audio-gpu-experimental.md) | Audio | Research-level, latency concerns |

## Implementation Priority

**Recommended order:**

1. **01-pattern-vertex-transforms** - Simplest change, affects all rendering
2. **04-debug-glyph-batch** - Huge debug mode improvement, isolated change
3. **02-collision-aabb-batch** + **03-touch-response-batch** - Can share infrastructure
4. **08-animated-pattern-lut** - Medium complexity, good shader practice
5. **06-terrain-sensor-batch** - Requires collision data upload
6. **07-sprite-geometry-shader** - More complex shader, needs careful testing
7. **05-fm-synthesis-compute** - Significant but needs latency management
8. **09-full-audio-gpu** - Only if FM compute works well

## Already GPU-Optimized

The analysis found these systems are already well-optimized:

- Parallax scrolling (shader-based)
- Tilemap rendering (GPU texture sampling)
- Water distortion (fragment shader)
- Screen fades (fragment shader)
- HUD rendering (pattern batching)

## Common Infrastructure

Several plans share infrastructure needs:

### Compute Shader Support
- Plans 02, 03, 05, 06 all need compute shader setup
- Recommend creating `ComputeShaderManager` utility class

### SSBO Management
- Plans 02, 03, 05, 06 need shader storage buffer objects
- Recommend creating `GpuBufferPool` for allocation

### Level Data Upload
- Plans 02, 03, 06 need level collision data on GPU
- Upload once at level load, share between shaders

## Testing Strategy

Each plan includes specific testing recommendations. General approach:

1. **Visual regression** - Screenshot comparison
2. **Performance profiling** - Measure before/after
3. **Accuracy verification** - Compare GPU vs CPU results
4. **Edge case testing** - Boundary conditions, special cases

## Risk Assessment

| Plan | Risk Level | Main Concern |
|------|------------|--------------|
| 01 | Low | Simple math, easy to verify |
| 02 | Medium | GPU readback latency |
| 03 | Medium | Atomic operation bottleneck |
| 04 | Low | Self-contained debug system |
| 05 | High | Audio latency requirements |
| 06 | Medium | Collision accuracy critical |
| 07 | Medium | Geometry shader perf varies |
| 08 | Low | Additional texture lookup |
| 09 | Very High | Real-time audio constraints |

## Hardware Requirements

- **Minimum:** OpenGL 4.3 (for compute shaders)
- **Recommended:** OpenGL 4.5 (for persistent mapped buffers)
- **Geometry shaders:** OpenGL 3.2+

All plans include CPU fallback recommendations for compatibility.
