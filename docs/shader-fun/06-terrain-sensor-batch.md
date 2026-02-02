# Terrain Sensor Batch - GPU Compute Shader Plan

## Overview

Parallelize terrain sensor probing by running all 4-8 sensors simultaneously on GPU. Currently, sensors scan sequentially, each performing tile lookups and metric calculations.

## Current State

**Files:**
- `TerrainCollisionManager.java:16-22` (sensor loop)
- `GroundSensor.java:63-307` (doScan, metrics)
- `CollisionSystem.java` (collision orchestration)

**Current CPU work:**
```java
// Sequential sensor scanning
for (int i = 0; i < sensors.length; i++) {
    results[i] = sensors[i].scan();
}

// Each scan does:
// 1. Get chunk descriptor at position
ChunkDesc desc = levelManager.getChunkDescAt(layer, x, y);

// 2. Get solid tile from chunk
SolidTile tile = getSolidTile(desc, solidityBit);

// 3. Calculate height/wall metric
int index = x & 0x0F;
if (desc.getHFlip()) index = 15 - index;
byte metric = tile.getHeightAt((byte) index);

// 4. Apply flip logic
if (metric != 0 && metric != FULL_TILE) {
    boolean invert = desc.getVFlip() ^ (direction == UP);
    if (invert) metric = (byte) (16 - metric);
}

// 5. Extended scanning for edge cases
// ... state machine (FOUND/EXTEND/REGRESS)
```

**Sensor types:**
- 2 ground sensors (left/right foot)
- 2 ceiling sensors (head left/right)
- 2 wall sensors (push left/right)
- Optional: additional sensors for slopes

## Proposed Solution

### GPU Compute Approach

Upload level collision data to GPU textures, dispatch one thread per sensor.

### 1. Level Data Textures

**Chunk Map (2D Texture):**
```glsl
// Level dimensions: typically 256×16 blocks = 4096×256 chunks
uniform usampler2D u_chunkMap;  // Chunk descriptors (pattern index + flags)
```

**Collision Tiles (1D Texture Array):**
```glsl
// 256 solid tiles × 16 height values
uniform sampler2D u_heightMaps;  // Height at each X position
uniform sampler2D u_widthMaps;   // Width at each Y position (for walls)
```

**Angle Table:**
```glsl
uniform sampler1D u_angleTable;  // Per-tile angle values
```

### 2. Data Structures

**Sensor Input:**
```glsl
struct SensorInput {
    vec2 position;      // World position (x, y)
    int direction;      // UP, DOWN, LEFT, RIGHT
    uint solidityBit;   // Which solidity layer (0 or 1)
    uint layer;         // Collision layer
};

layout(std430, binding = 0) readonly buffer SensorInputs {
    SensorInput sensors[];
};
```

**Sensor Output:**
```glsl
struct SensorResult {
    int distance;       // Pixels to surface
    uint angle;         // Surface angle (hex)
    uint tileId;        // Tile that was hit
    uint flags;         // Found, airborne, etc.
};

layout(std430, binding = 1) writeonly buffer SensorResults {
    SensorResult results[];
};
```

### 3. Compute Shader

```glsl
#version 430
layout(local_size_x = 8) in;  // Up to 8 sensors

uniform uvec2 u_levelSize;    // Level dimensions in chunks

uint getChunkDesc(ivec2 chunkPos) {
    if (chunkPos.x < 0 || chunkPos.x >= int(u_levelSize.x) ||
        chunkPos.y < 0 || chunkPos.y >= int(u_levelSize.y)) {
        return 0u;  // Out of bounds = empty
    }
    return texelFetch(u_chunkMap, chunkPos, 0).r;
}

int getHeightMetric(uint chunkDesc, int localX, int direction) {
    uint tileIndex = chunkDesc & 0x3FFu;
    bool hFlip = (chunkDesc & 0x800u) != 0u;
    bool vFlip = (chunkDesc & 0x1000u) != 0u;

    int index = localX;
    if (hFlip) index = 15 - index;

    int metric = int(texelFetch(u_heightMaps, ivec2(index, tileIndex), 0).r);

    if (metric != 0 && metric != 16) {
        bool invert = vFlip ^^ (direction == DIR_UP);
        if (invert) metric = 16 - metric;
    }

    return metric;
}

void scanVertical(SensorInput sensor, out SensorResult result) {
    int x = int(sensor.position.x);
    int y = int(sensor.position.y);

    // Convert to chunk coordinates
    ivec2 chunkPos = ivec2(x / 16, y / 16);
    int localX = x & 0xF;
    int localY = y & 0xF;

    // Get chunk at sensor position
    uint chunkDesc = getChunkDesc(chunkPos);

    // Check solidity
    uint solidBits = (chunkDesc >> 14) & 0x3u;
    if ((solidBits & (1u << sensor.solidityBit)) == 0u) {
        // Not solid - search downward/upward
        result.distance = searchForSurface(sensor, chunkPos, localX, localY);
        return;
    }

    // Get height metric
    int metric = getHeightMetric(chunkDesc, localX, sensor.direction);

    // Calculate distance to surface
    if (sensor.direction == DIR_DOWN) {
        result.distance = (16 - localY) - metric;
    } else {
        result.distance = localY - (16 - metric);
    }

    // Get angle
    uint tileIndex = chunkDesc & 0x3FFu;
    result.angle = uint(texelFetch(u_angleTable, int(tileIndex), 0).r);

    result.tileId = tileIndex;
    result.flags = FLAG_FOUND;
}

void main() {
    uint sensorIdx = gl_GlobalInvocationID.x;
    if (sensorIdx >= u_sensorCount) return;

    SensorInput sensor = sensors[sensorIdx];
    SensorResult result;
    result.flags = 0u;

    if (sensor.direction == DIR_DOWN || sensor.direction == DIR_UP) {
        scanVertical(sensor, result);
    } else {
        scanHorizontal(sensor, result);
    }

    results[sensorIdx] = result;
}
```

### 4. Extended Scanning

For EXTEND/REGRESS state machine (searching beyond current tile):

```glsl
int searchForSurface(SensorInput sensor, ivec2 startChunk,
                     int localX, int localY) {
    int maxSearch = 32;  // Pixels to search
    int searchDir = (sensor.direction == DIR_DOWN) ? 1 : -1;

    for (int offset = 1; offset <= maxSearch; offset++) {
        int newY = int(sensor.position.y) + offset * searchDir;
        ivec2 chunkPos = ivec2(int(sensor.position.x) / 16, newY / 16);

        uint chunkDesc = getChunkDesc(chunkPos);
        if ((chunkDesc >> 14 & (1u << sensor.solidityBit)) != 0u) {
            // Found solid tile
            int metric = getHeightMetric(chunkDesc, localX, sensor.direction);
            if (metric > 0) {
                return offset - metric + 16;  // Distance to surface
            }
        }
    }

    return maxSearch;  // Nothing found
}
```

### 5. CPU Integration

```java
public class TerrainCollisionGpu {
    private int chunkMapTexture;
    private int heightMapsTexture;
    private int angleTableTexture;
    private int sensorInputSSBO;
    private int sensorResultSSBO;
    private ShaderProgram sensorShader;

    public void uploadLevelData(Level level) {
        // Upload chunk map as 2D texture
        // Upload collision tiles as texture array
        // Upload angle table
    }

    public SensorResult[] scanSensors(Sensor[] sensors) {
        // 1. Pack sensor inputs
        FloatBuffer inputData = packSensorInputs(sensors);
        gl.glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, inputData);

        // 2. Dispatch compute
        sensorShader.use(gl);
        sensorShader.setUniform1ui(gl, "u_sensorCount", sensors.length);
        gl.glDispatchCompute(1, 1, 1);  // 8 threads for 8 sensors
        gl.glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        // 3. Read back results
        return readSensorResults(sensors.length);
    }
}
```

## Implementation Steps

1. **Upload level collision data to GPU**
   - Convert chunk array to 2D texture
   - Convert solid tiles to height/width texture arrays
   - Upload angle table

2. **Create sensor compute shader**
   - `shader_terrain_sensor.glsl`
   - Implement vertical and horizontal scanning
   - Handle flip flags correctly

3. **Create GPU collision class**
   - `TerrainCollisionGpu.java`
   - Manage texture uploads
   - Pack/unpack sensor data

4. **Integrate with CollisionSystem**
   - Replace `TerrainCollisionManager.getSensorResult()` calls
   - GPU path for ground/ceiling/wall sensors

## Data Upload Strategy

**Option A: Full level upload (simple)**
- Upload entire level collision data at level load
- 4096×256 chunks = 1MB texture (acceptable)
- No per-frame updates

**Option B: Streaming window (for large levels)**
- Upload visible region + margin
- Update when camera moves significantly
- More complex, unnecessary for Sonic 2 level sizes

## Expected Benefits

| Metric | CPU | GPU |
|--------|-----|-----|
| Sensors per dispatch | 4-8 sequential | 4-8 parallel |
| Tile lookups | Cache-unfriendly | Texture cache optimized |
| Extended search | Loop per sensor | Parallel loops |

**Estimated speedup:** 3-5x for sensor phase.

## Testing

1. **Accuracy:**
   - Compare GPU sensor results with CPU
   - Test all surface angles
   - Test edge cases (tile boundaries, level edges)

2. **Regression:**
   - Ground collision feels identical
   - Wall pushing works correctly
   - Slopes and loops work

3. **Performance:**
   - Measure sensor phase time before/after
   - Profile with different level sizes

## Risks

- **Medium:** Texture lookup precision (integer coordinates)
- **Medium:** Extended search complexity
- **Mitigation:** Validate thoroughly, keep CPU fallback

## Files to Create/Modify

**Create:**
- `src/main/resources/shaders/shader_terrain_sensor.glsl`
- `src/main/java/uk/co/jamesj999/sonic/physics/TerrainCollisionGpu.java`

**Modify:**
- `src/main/java/uk/co/jamesj999/sonic/physics/CollisionSystem.java`
- `src/main/java/uk/co/jamesj999/sonic/level/LevelManager.java` (data upload)
