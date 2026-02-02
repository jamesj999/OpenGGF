# Object Collision AABB Batch - GPU Compute Shader Plan

## Overview

Parallelize bounding-box collision tests between the player and all active solid objects using a GPU compute shader. Currently, collision detection iterates through 20-50 objects sequentially on the CPU.

## Current State

**Files:**
- `ObjectManager.java` inner class `SolidContacts.resolveContact()` (lines 1416-1450)
- `ObjectManager.java` inner class `SolidContacts.update()` (collision loop)

**Current CPU work:**
```java
// For each of 20-50 active objects:
for (ObjectInstance instance : activeObjects) {
    // AABB test
    int relX = playerCenterX - anchorX + halfWidth;
    if (relX < 0 || relX > halfWidth * 2) continue;  // X cull

    int playerYRadius = player.getYRadius();
    int maxTop = halfHeight + playerYRadius;
    int verticalOffset = monitorSolidity ? 0 : 4;
    int relY = playerCenterY - anchorY + verticalOffset + maxTop;

    if (relY < minRelY || relY >= maxTop * 2) continue;  // Y cull

    // ... further resolution logic
}
```

**Impact:** 20-50 independent AABB tests per frame, each with ~15-20 arithmetic ops and 4-6 conditional branches.

## Proposed Solution

### GPU Compute Shader Approach

Use a compute shader to test all objects in parallel, outputting a bitmask or list of colliding objects for CPU follow-up.

### 1. Data Structures

**Input Buffer (Object Data):**
```glsl
struct ObjectData {
    vec2 position;      // anchorX, anchorY
    vec2 halfSize;      // halfWidth, halfHeight
    uint flags;         // monitorSolidity, isRiding, etc.
    uint objectIndex;   // Original index for result mapping
};

layout(std430, binding = 0) readonly buffer ObjectBuffer {
    ObjectData objects[];
};
```

**Input Uniform (Player Data):**
```glsl
uniform vec2 u_playerCenter;    // playerCenterX, playerCenterY
uniform vec2 u_playerRadius;    // xRadius, yRadius
```

**Output Buffer (Collision Results):**
```glsl
layout(std430, binding = 1) writeonly buffer ResultBuffer {
    uint collisionMask[];  // Bit per object, or...
    // OR: list of colliding indices
};
```

### 2. Compute Shader

```glsl
#version 430
layout(local_size_x = 64) in;

void main() {
    uint idx = gl_GlobalInvocationID.x;
    if (idx >= u_objectCount) return;

    ObjectData obj = objects[idx];

    // AABB test (same logic as CPU)
    float relX = u_playerCenter.x - obj.position.x + obj.halfSize.x;
    if (relX < 0.0 || relX > obj.halfSize.x * 2.0) {
        results[idx] = 0u;
        return;
    }

    float verticalOffset = ((obj.flags & MONITOR_SOLIDITY_BIT) != 0u) ? 0.0 : 4.0;
    float maxTop = obj.halfSize.y + u_playerRadius.y;
    float relY = u_playerCenter.y - obj.position.y + verticalOffset + maxTop;

    bool isRiding = (obj.flags & RIDING_BIT) != 0u;
    float minRelY = isRiding ? -16.0 : 0.0;

    if (relY < minRelY || relY >= maxTop * 2.0) {
        results[idx] = 0u;
        return;
    }

    // Collision detected - encode result
    results[idx] = packContactInfo(relX, relY, obj.halfSize);
}
```

### 3. CPU Integration

```java
public class SolidContactsGpu {
    private int objectBufferSSBO;
    private int resultBufferSSBO;
    private ShaderProgram collisionShader;

    public void update(AbstractPlayableSprite player, List<ObjectInstance> objects) {
        // 1. Upload object data to GPU
        uploadObjectData(objects);

        // 2. Set player uniforms
        collisionShader.setUniform2f(gl, "u_playerCenter",
            player.getCentreX(), player.getCentreY());
        collisionShader.setUniform2f(gl, "u_playerRadius",
            player.getXRadius(), player.getYRadius());

        // 3. Dispatch compute shader
        int workGroups = (objects.size() + 63) / 64;
        gl.glDispatchCompute(workGroups, 1, 1);
        gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        // 4. Read back collision results
        int[] results = readResults(objects.size());

        // 5. Process only colliding objects on CPU
        for (int i = 0; i < results.length; i++) {
            if (results[i] != 0) {
                resolveCollision(player, objects.get(i), results[i]);
            }
        }
    }
}
```

## Implementation Steps

1. **Create compute shader file**
   - `src/main/resources/shaders/shader_collision_aabb.glsl`

2. **Create GPU collision manager class**
   - `uk.co.jamesj999.sonic.physics.SolidContactsGpu`
   - Handle buffer allocation, upload, dispatch, readback

3. **Modify `ObjectManager.SolidContacts`**
   - Add GPU path with CPU fallback
   - Use GPU for broad phase, CPU for narrow phase resolution

4. **Buffer management**
   - Persistent mapped buffers for object data (update each frame)
   - Double-buffered results to avoid stalls

## Data Flow

```
Frame N:
├── CPU: Collect active objects → objectData[]
├── GPU: Upload objectData to SSBO
├── GPU: Set player uniforms
├── GPU: Dispatch compute (1 thread per object)
├── GPU: Write collision flags to result SSBO
├── CPU: Read back results
└── CPU: Process only colliding objects (narrow phase)
```

## Expected Benefits

| Metric | Before (CPU) | After (GPU) |
|--------|--------------|-------------|
| AABB tests | Sequential (50 × 20 ops) | Parallel (50 threads × 20 ops) |
| Time complexity | O(n) | O(1) with n threads |
| Branches | 4-6 per object (branch predictor stress) | GPU handles divergence |
| Typical objects | 20-50 | 20-50 |

**Estimated speedup:** 5-10x for collision broad phase (though absolute time is small).

**Real benefit:** Frees CPU for other work; scales better with more objects.

## Testing

1. **Correctness:**
   - Compare GPU results against CPU implementation
   - Test edge cases: objects at screen boundaries, player partially overlapping

2. **Performance:**
   - Profile with 50, 100, 200 objects
   - Measure GPU dispatch + readback latency

3. **Regression:**
   - Solid object standing/pushing behavior unchanged
   - Monitor solidity special case works

## Risks

- **Medium:** GPU readback latency may negate benefits for small object counts
- **Mitigation:** Use persistent mapped buffers, or async readback with 1-frame delay
- **Fallback:** Keep CPU path for systems without compute shader support

## Multi-Piece Objects

For objects with multiple collision pieces (platforms, chains):

```glsl
// Extend object data to include piece offset
struct ObjectPiece {
    vec2 basePosition;
    vec2 pieceOffset;
    vec2 halfSize;
    uint flags;
    uint parentIndex;  // For result aggregation
};
```

Dispatch one thread per piece, aggregate results by parentIndex on CPU.

## Files to Create/Modify

**Create:**
- `src/main/resources/shaders/shader_collision_aabb.glsl`
- `src/main/java/uk/co/jamesj999/sonic/physics/SolidContactsGpu.java`

**Modify:**
- `src/main/java/uk/co/jamesj999/sonic/level/objects/ObjectManager.java`
- `src/main/java/uk/co/jamesj999/sonic/graphics/GraphicsManager.java` (buffer management)
