# Touch Response Overlap Batch - GPU Compute Shader Plan

## Overview

Parallelize touch response collision detection (player vs. collectibles, enemies, hazards) using a GPU compute shader. Currently iterates through 50+ objects sequentially to check overlap.

## Current State

**Files:**
- `ObjectManager.java` inner class `TouchResponses.update()` (lines 806-873)
- `ObjectManager.java` inner class `TouchResponses.isOverlapping()` (lines 875-898)

**Current CPU work:**
```java
// For each touchable object (50+):
for (ObjectInstance instance : activeObjects) {
    TouchResponseProvider provider = (TouchResponseProvider) instance;
    int flags = provider.getCollisionFlags();
    int sizeIndex = flags & 0x3F;

    // Table lookup for collision size
    int width = table.getWidthRadius(sizeIndex);
    int height = table.getHeightRadius(sizeIndex);

    // Overlap check with wrapping subtraction
    boolean overlap = isOverlapping(playerX, playerY, playerHeight,
                                    instance.getSpawn(), width, height);
    if (overlap) {
        overlapping.add(instance);
    }
}

// isOverlapping implementation:
int dx = spawn.x() - objectWidth - playerX;
if (dx < 0) {
    int sum = (dx & 0xFFFF) + ((objectWidth * 2) & 0xFFFF);
    if (sum <= 0xFFFF) return false;
}
// Similar for Y axis...
```

**Impact:** 50+ independent overlap tests per frame, each with table lookups and wrapping arithmetic.

## Proposed Solution

### GPU Compute Shader Approach

Test all touchable objects against player in parallel, output list of overlapping object indices.

### 1. Data Structures

**Collision Size Table (Uniform Buffer):**
```glsl
// Pre-loaded from Sonic2ObjectConstants.TOUCH_COLLISION_TABLE_ADDR
layout(std140, binding = 0) uniform CollisionSizeTable {
    vec2 sizes[64];  // [width, height] for each size index
};
```

**Object Buffer:**
```glsl
struct TouchObject {
    vec2 position;      // spawn.x(), spawn.y()
    uint sizeIndex;     // flags & 0x3F
    uint objectIndex;   // For result mapping
};

layout(std430, binding = 1) readonly buffer ObjectBuffer {
    TouchObject objects[];
};
```

**Result Buffer:**
```glsl
// Atomic counter + list of overlapping indices
layout(std430, binding = 2) buffer ResultBuffer {
    uint overlapCount;
    uint overlappingIndices[];  // Compact list
};
```

### 2. Compute Shader

```glsl
#version 430
layout(local_size_x = 64) in;

uniform vec2 u_playerPos;
uniform float u_playerHeight;
uniform uint u_objectCount;

void main() {
    uint idx = gl_GlobalInvocationID.x;
    if (idx >= u_objectCount) return;

    TouchObject obj = objects[idx];
    vec2 size = sizes[obj.sizeIndex];

    // Overlap test (matches original wrapping logic)
    float dx = obj.position.x - size.x - u_playerPos.x;
    bool xOverlap = true;
    if (dx < 0.0) {
        // Wrapping subtraction emulation
        float sum = mod(dx, 65536.0) + mod(size.x * 2.0, 65536.0);
        if (sum <= 65535.0) xOverlap = false;
    } else if (dx >= size.x * 2.0) {
        xOverlap = false;
    }

    if (!xOverlap) return;

    // Similar for Y...
    float dy = obj.position.y - size.y - u_playerPos.y;
    bool yOverlap = true;
    // ... same logic

    if (!yOverlap) return;

    // Collision detected - add to result list atomically
    uint slot = atomicAdd(overlapCount, 1u);
    if (slot < MAX_OVERLAPS) {
        overlappingIndices[slot] = obj.objectIndex;
    }
}
```

### 3. CPU Integration

```java
public class TouchResponsesGpu {
    private int sizeTableUBO;
    private int objectBufferSSBO;
    private int resultBufferSSBO;
    private ShaderProgram touchShader;

    public Set<ObjectInstance> findOverlapping(
            AbstractPlayableSprite player,
            List<ObjectInstance> touchables) {

        // 1. Upload object positions and size indices
        uploadTouchObjects(touchables);

        // 2. Reset overlap counter
        gl.glClearBufferData(GL_SHADER_STORAGE_BUFFER, ...);

        // 3. Set player uniforms
        touchShader.setUniform2f(gl, "u_playerPos",
            player.getCentreX(), player.getCentreY());
        touchShader.setUniform1f(gl, "u_playerHeight",
            player.getYRadius() * 2);

        // 4. Dispatch
        int workGroups = (touchables.size() + 63) / 64;
        gl.glDispatchCompute(workGroups, 1, 1);
        gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        // 5. Read back compact result list
        int count = readOverlapCount();
        int[] indices = readOverlapIndices(count);

        // 6. Build result set
        Set<ObjectInstance> result = new HashSet<>();
        for (int index : indices) {
            result.add(touchables.get(index));
        }
        return result;
    }
}
```

## Implementation Steps

1. **Create compute shader**
   - `src/main/resources/shaders/shader_touch_overlap.glsl`

2. **Create GPU touch response class**
   - `uk.co.jamesj999.sonic.physics.TouchResponsesGpu`

3. **Pre-load collision size table**
   - Read from ROM at `Sonic2ObjectConstants.TOUCH_COLLISION_TABLE_ADDR`
   - Upload to UBO once at level load

4. **Modify `ObjectManager.TouchResponses`**
   - Add GPU path with CPU fallback
   - GPU returns overlapping indices, CPU handles response logic

## Data Flow

```
Frame N:
├── CPU: Filter touchable objects → touchObjects[]
├── GPU: Upload positions + size indices
├── GPU: Dispatch compute (1 thread per object)
├── GPU: Atomic append overlapping indices
├── CPU: Read back overlap count + indices
└── CPU: Process touch responses for overlapping objects
```

## Expected Benefits

| Metric | Before (CPU) | After (GPU) |
|--------|--------------|-------------|
| Overlap tests | Sequential (50+ × 10 ops) | Parallel (50+ threads) |
| Branch divergence | High (CPU prediction misses) | GPU handles naturally |
| Result collection | N/A | Atomic append (compact) |

**Estimated speedup:** 8-15x for overlap detection phase.

## Testing

1. **Correctness:**
   - Compare overlapping set against CPU implementation
   - Test with rings, monitors, badniks, hazards

2. **Edge cases:**
   - Objects at world boundaries (wrapping arithmetic)
   - Very large/small collision sizes
   - Maximum object count stress test

3. **Regression:**
   - Ring collection timing unchanged
   - Enemy damage detection accurate
   - Checkpoint activation correct

## Risks

- **Medium:** Atomic operations can bottleneck if many overlaps
- **Mitigation:** Limit MAX_OVERLAPS (16-32 typically sufficient)
- **Low:** Wrapping arithmetic emulation may differ slightly
- **Mitigation:** Validate against reference ROM behavior

## Optimization: Combined with AABB Shader

Consider combining touch response and solid contact detection into single dispatch:

```glsl
// Single shader with object type flag
if (obj.isSolid) {
    // AABB solid contact test
} else if (obj.isTouchable) {
    // Touch overlap test
}
```

Reduces dispatch overhead, shares player uniform setup.

## Files to Create/Modify

**Create:**
- `src/main/resources/shaders/shader_touch_overlap.glsl`
- `src/main/java/uk/co/jamesj999/sonic/physics/TouchResponsesGpu.java`

**Modify:**
- `src/main/java/uk/co/jamesj999/sonic/level/objects/ObjectManager.java`
- `src/main/java/uk/co/jamesj999/sonic/graphics/GraphicsManager.java` (buffer management)
