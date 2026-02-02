# Animated Pattern LUT Texture - GPU Optimization Plan

## Overview

Replace CPU-based animated tile pattern copying with a GPU lookup table (LUT) texture. Currently, animated tiles require copying pattern data and updating GPU textures every frame.

## Current State

**Files:**
- `Sonic2PatternAnimator.java:110-341` (animation logic)
- `Sonic2PatternAnimator.java:323-340` (applyFrame - CPU copy)
- `GraphicsManager.updatePatternTexture()` (GPU upload)

**Current CPU work:**
```java
// Per animated tile per frame:
private void applyFrame(Level level, int frameIndex) {
    for (int i = 0; i < tilesPerFrame; i++) {
        int srcIndex = tileId + i;
        int destIndex = destTileIndex + i;

        // CPU copy of pattern data
        Pattern dest = level.getPattern(destIndex);
        dest.copyFrom(artPatterns[srcIndex]);

        // GPU texture update
        if (canUpdateTextures) {
            graphicsManager.updatePatternTexture(dest, destIndex);
        }
    }
}
```

**Impact:**
- EHZ: 1-2 animated tiles
- MTZ: 3-4 animated tiles
- CNZ: 2-3 animated tiles
- CPZ: 2-3 animated tiles
- Total: ~20-40 pattern copies + GPU uploads per frame

## Proposed Solution

### LUT-Based Animation

Instead of copying pattern data, use a lookup table that maps (tile index, frame) → actual pattern index.

```
Tilemap references tile index 0x50 (animated waterfall)
    → Shader looks up LUT[0x50][currentFrame]
    → LUT returns actual pattern index 0x120
    → Shader samples pattern 0x120 from atlas
```

### 1. Animation LUT Texture

**2D Texture format:**
- Width: Number of animatable tile slots (e.g., 32)
- Height: Max animation frames (e.g., 16)
- Format: R16UI (16-bit unsigned int = pattern index)

```glsl
uniform usampler2D u_animationLUT;  // (tileSlot, frame) → patternIndex
uniform uint u_frameCounters[32];    // Current frame per animation slot
```

### 2. Animation Slot Mapping

**CPU maintains:**
```java
public class AnimationLUT {
    // Map animated tile indices to LUT slots
    private Map<Integer, Integer> tileToSlot = new HashMap<>();

    // LUT data: [slot][frame] = patternIndex
    private int[][] lutData = new int[MAX_SLOTS][MAX_FRAMES];

    public void registerAnimation(int destTileIndex, int[] framePatterns) {
        int slot = tileToSlot.size();
        tileToSlot.put(destTileIndex, slot);

        for (int f = 0; f < framePatterns.length; f++) {
            lutData[slot][f] = framePatterns[f];
        }
    }

    public void uploadToGpu(GL2 gl) {
        // Upload lutData as 2D texture
    }
}
```

### 3. Modified Tilemap Shader

```glsl
#version 330

uniform usampler2D u_tilemap;        // Chunk descriptors
uniform sampler2D u_patternAtlas;    // All patterns
uniform usampler2D u_animationLUT;   // Animation lookup table
uniform uint u_animSlotMap[256];     // Tile index → LUT slot (0 = not animated)
uniform uint u_frameCounters[32];    // Current frame per slot

in vec2 v_worldPos;
out vec4 fragColor;

uint resolvePatternIndex(uint rawIndex) {
    // Check if this tile is animated
    uint slot = u_animSlotMap[rawIndex & 0xFFu];
    if (slot == 0u) {
        return rawIndex;  // Not animated
    }

    // Look up current frame in LUT
    uint frame = u_frameCounters[slot - 1u];
    uint animatedIndex = texelFetch(u_animationLUT, ivec2(slot - 1, frame), 0).r;

    return animatedIndex;
}

void main() {
    // Get chunk descriptor at world position
    ivec2 chunkPos = ivec2(v_worldPos) / 16;
    uint chunkDesc = texelFetch(u_tilemap, chunkPos, 0).r;

    uint patternIndex = chunkDesc & 0x7FFu;

    // Resolve animation
    patternIndex = resolvePatternIndex(patternIndex);

    // Sample pattern atlas
    // ... rest of tilemap shader
}
```

### 4. Frame Counter Updates

**CPU only updates frame counters (not pattern data):**

```java
public class Sonic2PatternAnimatorGpu {
    private int[] frameCounters = new int[MAX_SLOTS];
    private int frameCounterUBO;

    public void tick() {
        for (ScriptState script : activeScripts) {
            if (script.timer > 0) {
                script.timer--;
            } else {
                script.timer = script.timerReset;
                script.frame = (script.frame + 1) % script.frameCount;

                // Update frame counter (no pattern copy!)
                int slot = tileToSlot.get(script.destTileIndex);
                frameCounters[slot] = script.frame;
            }
        }

        // Upload frame counters (32 × 4 bytes = 128 bytes)
        uploadFrameCounters();
    }

    private void uploadFrameCounters() {
        gl.glBindBuffer(GL_UNIFORM_BUFFER, frameCounterUBO);
        gl.glBufferSubData(GL_UNIFORM_BUFFER, 0, frameCounters);
    }
}
```

## Implementation Steps

1. **Create AnimationLUT class**
   - `uk.co.jamesj999.sonic.graphics.AnimationLUT`
   - Parse animation scripts at level load
   - Build LUT texture with all frame patterns

2. **Modify tilemap shader**
   - Add animation LUT uniform
   - Add slot mapping array
   - Add frame counter array
   - Resolve pattern index before atlas sample

3. **Modify Sonic2PatternAnimator**
   - Remove pattern copy logic
   - Update frame counters only
   - Upload counters as uniform buffer

4. **Level loading**
   - Pre-load all animation frame patterns to atlas
   - Build LUT at level load time
   - Register animations with LUT

## Data Layout

**Animation LUT Texture (u_animationLUT):**
```
         Frame 0   Frame 1   Frame 2   ...
Slot 0   [0x120]   [0x121]   [0x122]   ...   (e.g., waterfall)
Slot 1   [0x150]   [0x151]   [0x150]   ...   (e.g., flowers)
Slot 2   [0x180]   [0x181]   [0x182]   ...   (e.g., conveyor)
...
```

**Slot Map Array (u_animSlotMap):**
```
Index 0x50 → Slot 1 (waterfall uses slot 1)
Index 0x60 → Slot 2 (flowers uses slot 2)
Other indices → 0 (not animated)
```

## Expected Benefits

| Metric | Before | After |
|--------|--------|-------|
| Pattern copies/frame | 20-40 | 0 |
| GPU texture updates/frame | 20-40 | 0 |
| Data uploaded/frame | ~2-4 KB | 128 bytes |
| Shader complexity | None | +1 texture lookup |

**Estimated improvement:** Eliminates CPU pattern copying and GPU texture uploads for animations.

## Testing

1. **Visual:**
   - All animated tiles animate correctly
   - Timing matches original
   - All zones work (EHZ, MTZ, CNZ, etc.)

2. **Performance:**
   - Measure `updatePatternTexture` call count
   - Measure CPU time in animation tick

3. **Correctness:**
   - Frame timing identical to CPU version
   - No visual glitches at frame transitions

## Risks

- **Low:** Additional texture lookup in shader (negligible)
- **Low:** Shader uniform array limits
- **Mitigation:** Group animations by slot count, ensure under 32 slots

## Alternative: Texture Array Animation

Instead of LUT, use texture array with one layer per animation frame:

```glsl
uniform sampler2DArray u_animatedPatterns;
// Sample: texture(u_animatedPatterns, vec3(uv, frame))
```

Simpler shader code but requires separate atlas per animated tile set.

## Files to Create/Modify

**Create:**
- `src/main/java/uk/co/jamesj999/sonic/graphics/AnimationLUT.java`

**Modify:**
- `src/main/java/uk/co/jamesj999/sonic/game/sonic2/Sonic2PatternAnimator.java`
- `src/main/resources/shaders/shader_tilemap.glsl`
- `src/main/java/uk/co/jamesj999/sonic/level/LevelManager.java` (LUT setup)
