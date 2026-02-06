# Sprite Geometry Shader - GPU Optimization Plan

## Overview

Use a geometry shader to expand sprite pieces into tile quads on the GPU, eliminating nested CPU loops for multi-tile sprite assembly. Currently, each sprite piece requires iterating through its tiles in a nested loop.

## Current State

**Files:**
- `SpritePieceRenderer.java:19-74` (renderPieces)
- `PlayerSpriteRenderer.java:30-68` (drawFrame)
- `PatternSpriteRenderer.java:141-165` (drawFramePieces)

**Current CPU work:**
```java
// For each sprite piece:
for (SpriteFramePiece piece : pieces) {
    int widthTiles = piece.widthTiles();
    int heightTiles = piece.heightTiles();

    // Nested loop over tiles (column-major)
    for (int ty = 0; ty < heightTiles; ty++) {
        for (int tx = 0; tx < widthTiles; tx++) {
            int srcX = pieceHFlip ? (widthTiles - 1 - tx) : tx;
            int srcY = pieceVFlip ? (heightTiles - 1 - ty) : ty;
            int tileOffset = (tx * heightTiles) + ty;  // Column-major
            int patternIndex = basePatternIndex + piece.tileIndex() + tileOffset;

            int drawX = origin.x() + pieceXOffset + tx * 8;
            int drawY = origin.y() + pieceYOffset + ty * 8;

            consumer.render(patternIndex, drawX, drawY, hFlip, vFlip, palette);
        }
    }
}
```

**Impact:**
- Sonic sprite: 3-5 pieces × 2-4 tiles = 6-20 tiles per frame
- 60 FPS = 360-1200 tile calculations per second per sprite
- Additional objects multiply this significantly

## Proposed Solution

### Geometry Shader Approach

Input: One vertex per sprite piece (position, size, flags)
Output: N quads (one per tile in the piece)

```
Input: Sprite piece descriptor
    → Geometry shader expands to WxH tile quads
    → Fragment shader samples pattern atlas
```

### 1. Vertex Shader (Pass-Through)

```glsl
#version 330
layout(location = 0) in vec2 a_pieceOrigin;    // Base position
layout(location = 1) in vec2 a_pieceOffset;    // Offset from origin
layout(location = 2) in vec2 a_pieceSize;      // Width/height in tiles
layout(location = 3) in uint a_basePattern;    // Starting pattern index
layout(location = 4) in uint a_flags;          // hFlip, vFlip, paletteIndex

out VS_OUT {
    vec2 origin;
    vec2 offset;
    ivec2 size;
    uint basePattern;
    uint flags;
} vs_out;

void main() {
    vs_out.origin = a_pieceOrigin;
    vs_out.offset = a_pieceOffset;
    vs_out.size = ivec2(a_pieceSize);
    vs_out.basePattern = a_basePattern;
    vs_out.flags = a_flags;
}
```

### 2. Geometry Shader (Tile Expansion)

```glsl
#version 330
layout(points) in;
layout(triangle_strip, max_vertices = 64) out;  // 4 verts × 16 tiles max

in VS_OUT {
    vec2 origin;
    vec2 offset;
    ivec2 size;
    uint basePattern;
    uint flags;
} gs_in[];

out vec2 v_texCoord;
out float v_paletteIndex;

uniform mat4 u_projection;
uniform vec2 u_atlasSize;      // Pattern atlas dimensions
uniform float u_patternSize;   // 8.0

void emitTileQuad(vec2 pos, uint patternIndex, bool hFlip, bool vFlip, float palette) {
    // Calculate UV coordinates in atlas
    uint atlasX = patternIndex % uint(u_atlasSize.x / u_patternSize);
    uint atlasY = patternIndex / uint(u_atlasSize.x / u_patternSize);
    vec2 uvBase = vec2(atlasX, atlasY) * u_patternSize / u_atlasSize;
    vec2 uvSize = vec2(u_patternSize) / u_atlasSize;

    // Apply flip to UVs
    vec2 uv0 = uvBase;
    vec2 uv1 = uvBase + uvSize;
    if (hFlip) { float tmp = uv0.x; uv0.x = uv1.x; uv1.x = tmp; }
    if (vFlip) { float tmp = uv0.y; uv0.y = uv1.y; uv1.y = tmp; }

    // Emit 4 vertices for quad
    v_paletteIndex = palette;

    gl_Position = u_projection * vec4(pos, 0.0, 1.0);
    v_texCoord = uv0;
    EmitVertex();

    gl_Position = u_projection * vec4(pos + vec2(u_patternSize, 0.0), 0.0, 1.0);
    v_texCoord = vec2(uv1.x, uv0.y);
    EmitVertex();

    gl_Position = u_projection * vec4(pos + vec2(0.0, u_patternSize), 0.0, 1.0);
    v_texCoord = vec2(uv0.x, uv1.y);
    EmitVertex();

    gl_Position = u_projection * vec4(pos + vec2(u_patternSize), 0.0, 1.0);
    v_texCoord = uv1;
    EmitVertex();

    EndPrimitive();
}

void main() {
    vec2 origin = gs_in[0].origin;
    vec2 offset = gs_in[0].offset;
    ivec2 size = gs_in[0].size;
    uint basePattern = gs_in[0].basePattern;
    uint flags = gs_in[0].flags;

    bool pieceHFlip = (flags & 1u) != 0u;
    bool pieceVFlip = (flags & 2u) != 0u;
    float paletteIndex = float((flags >> 2) & 3u);

    // Adjust offset for flip
    vec2 adjustedOffset = offset;
    if (pieceHFlip) adjustedOffset.x = -offset.x - float(size.x) * u_patternSize;
    if (pieceVFlip) adjustedOffset.y = -offset.y - float(size.y) * u_patternSize;

    // Emit tiles (column-major order)
    for (int tx = 0; tx < size.x; tx++) {
        for (int ty = 0; ty < size.y; ty++) {
            // Source tile with flip
            int srcX = pieceHFlip ? (size.x - 1 - tx) : tx;
            int srcY = pieceVFlip ? (size.y - 1 - ty) : ty;

            // Column-major tile offset
            uint tileOffset = uint(tx * size.y + ty);
            uint patternIndex = basePattern + tileOffset;

            // Screen position
            vec2 tilePos = origin + adjustedOffset + vec2(tx, ty) * u_patternSize;

            emitTileQuad(tilePos, patternIndex, pieceHFlip, pieceVFlip, paletteIndex);
        }
    }
}
```

### 3. Fragment Shader

```glsl
#version 330
in vec2 v_texCoord;
in float v_paletteIndex;

uniform sampler2D u_patternAtlas;
uniform sampler1D u_palette;

out vec4 fragColor;

void main() {
    // Sample pattern (indexed color)
    float colorIndex = texture(u_patternAtlas, v_texCoord).r * 255.0;

    // Palette lookup
    float paletteOffset = v_paletteIndex * 16.0;
    vec4 color = texture(u_palette, (paletteOffset + colorIndex + 0.5) / 64.0);

    // Transparent color 0
    if (colorIndex < 0.5) discard;

    fragColor = color;
}
```

### 4. CPU Integration

```java
public class GeometrySpriteRenderer {
    private int pieceVAO;
    private int pieceVBO;
    private ShaderProgram spriteShader;

    // Piece data buffer
    private FloatBuffer pieceData;
    private int pieceCount;

    public void begin() {
        pieceCount = 0;
        pieceData.clear();
    }

    public void addPiece(int originX, int originY, SpriteFramePiece piece,
                         boolean hFlip, boolean vFlip, int paletteIndex) {
        // Pack piece data
        pieceData.put(originX);
        pieceData.put(originY);
        pieceData.put(piece.xOffset());
        pieceData.put(piece.yOffset());
        pieceData.put(piece.widthTiles());
        pieceData.put(piece.heightTiles());
        pieceData.put(piece.tileIndex());

        int flags = (hFlip ? 1 : 0) | (vFlip ? 2 : 0) | (paletteIndex << 2);
        pieceData.put(Float.intBitsToFloat(flags));

        pieceCount++;
    }

    public void end(GL2 gl) {
        if (pieceCount == 0) return;

        // Upload piece data
        pieceData.flip();
        gl.bindBuffer(GL_ARRAY_BUFFER, pieceVBO);
        gl.bufferSubData(GL_ARRAY_BUFFER, 0, pieceData);

        // Draw as points (geometry shader expands)
        spriteShader.use(gl);
        gl.bindVertexArray(pieceVAO);
        gl.drawArrays(GL_POINTS, 0, pieceCount);
    }
}
```

## Implementation Steps

1. **Create geometry shader**
   - `shader_sprite_geometry.glsl`
   - Handle column-major ordering
   - Handle flip flags correctly

2. **Create GeometrySpriteRenderer**
   - `uk.co.jamesj999.sonic.graphics.GeometrySpriteRenderer`
   - Batch piece submissions
   - Single draw call per frame

3. **Modify SpritePieceRenderer**
   - Use GeometrySpriteRenderer instead of nested loops
   - Fall back to CPU path if geometry shaders unavailable

4. **Update PlayerSpriteRenderer**
   - Adapt DPLC handling for batch submission

## Geometry Shader Limits

- `max_vertices = 64` (4 vertices × 16 tiles max)
- Typical sprite piece: 2×2 to 4×4 tiles = 16-64 vertices
- May need multiple pieces for very large sprites

For sprites exceeding limits:
```glsl
// Split into multiple geometry shader invocations
// Or use tessellation + geometry for unlimited expansion
```

## Alternative: Compute Shader

If geometry shader performance is poor, use compute shader:

```glsl
// Compute shader writes to vertex buffer
// CPU then draws pre-expanded quads
layout(local_size_x = 64) in;

void main() {
    uint pieceIdx = gl_GlobalInvocationID.x;
    // Expand piece to quads in output buffer
}
```

## Expected Benefits

| Metric | Before (CPU) | After (GPU) |
|--------|--------------|-------------|
| Nested loops | Per piece × tiles | None |
| Data transfer | Per tile | Per piece |
| Flip calculations | Per tile | Once per piece |
| Draw calls | Per tile (batched) | Per sprite (geometry) |

**Estimated improvement:** 50-70% reduction in sprite rendering CPU time.

## Testing

1. **Visual:**
   - All sprites render identically
   - Flip flags work correctly
   - Palette indices correct

2. **Performance:**
   - Measure CPU time in sprite rendering
   - Compare draw call count

3. **Edge cases:**
   - Large sprites (>16 tiles)
   - Mixed sprite sizes
   - Rapid animation changes

## Risks

- **Medium:** Geometry shader performance varies by GPU
- **Medium:** max_vertices limit for large sprites
- **Mitigation:** Benchmark, keep CPU fallback, split large sprites

## Files to Create/Modify

**Create:**
- `src/main/resources/shaders/shader_sprite_geometry.glsl`
- `src/main/java/uk/co/jamesj999/sonic/graphics/GeometrySpriteRenderer.java`

**Modify:**
- `src/main/java/uk/co/jamesj999/sonic/graphics/SpritePieceRenderer.java`
- `src/main/java/uk/co/jamesj999/sonic/sprites/PlayerSpriteRenderer.java`
