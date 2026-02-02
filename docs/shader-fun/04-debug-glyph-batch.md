# Debug Glyph Batch Rendering - GPU Optimization Plan

## Overview

Replace CPU-based `TextRenderer` (AWT) with GPU-accelerated instanced glyph rendering. Currently, debug text rendering uses 9 draw calls per outlined string, resulting in 500-1350 draw calls per frame in debug mode.

## Current State

**Files:**
- `DebugRenderer.java:39-42` (TextRenderer instances)
- `DebugRenderer.java:577-589` (drawOutlined method)
- `PerformancePanelRenderer.java:42, 75-77`

**Current implementation:**
```java
// 4 separate TextRenderer instances
private TextRenderer mainRenderer;
private TextRenderer objectRenderer;
private TextRenderer planeSwitcherRenderer;
private TextRenderer sensorRenderer;

// Outlined text = 9 draw calls per string
private void drawOutlined(TextRenderer renderer, String text, int x, int y, Color color) {
    renderer.beginRendering(width, height);
    // 8 black shadow draws at offsets
    for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
            if (dx != 0 || dy != 0) {
                renderer.draw(text, x + dx, y + dy);  // Black
            }
        }
    }
    renderer.setColor(color);
    renderer.draw(text, x, y);  // Colored center
    renderer.endRendering();
}
```

**Impact:**
- Object labels: 20-50 objects × 3 lines × 9 calls = **540-1350 draw calls**
- Sensor labels: 6 sensors × 9 calls = **54 draw calls**
- Plane switcher: 2-3 labels × 9 calls = **27 draw calls**
- Performance panel text: 10+ strings × 9 calls = **90+ draw calls**

## Proposed Solution

### 1. Glyph Atlas Texture

Pre-render font glyphs to a texture atlas at startup:

```java
public class GlyphAtlas {
    private int textureId;
    private Map<Character, GlyphInfo> glyphs;

    public static class GlyphInfo {
        float u0, v0, u1, v1;  // Texture coordinates
        int width, height;
        int xOffset, yOffset;
        int advance;
    }

    public GlyphAtlas(Font font, String charset) {
        // Render all glyphs to BufferedImage
        // Upload as texture
        // Store UV coordinates for each glyph
    }
}
```

### 2. Instanced Glyph Rendering

Each glyph instance carries:
- Screen position (x, y)
- Glyph index (for UV lookup)
- Color (RGBA)
- Outline flag

```glsl
// Vertex shader
layout(location = 0) in vec2 a_vertexPos;     // Quad corner (0-1)
layout(location = 1) in vec2 a_instancePos;   // Screen position
layout(location = 2) in vec4 a_glyphUV;       // u0, v0, u1, v1
layout(location = 3) in vec2 a_glyphSize;     // width, height
layout(location = 4) in vec4 a_color;         // RGBA
layout(location = 5) in float a_isOutline;    // 0 or 1

out vec2 v_texCoord;
out vec4 v_color;

void main() {
    vec2 pos = a_instancePos + a_vertexPos * a_glyphSize;

    // Outline offset (8 directions + center)
    if (a_isOutline > 0.5) {
        // Handled in fragment shader via SDF or multi-sample
    }

    gl_Position = u_projection * vec4(pos, 0.0, 1.0);
    v_texCoord = mix(a_glyphUV.xy, a_glyphUV.zw, a_vertexPos);
    v_color = a_color;
}
```

### 3. SDF-Based Outline (Fragment Shader)

Instead of 9 draws, use Signed Distance Field for outline in single pass:

```glsl
// Fragment shader with SDF outline
uniform sampler2D u_glyphAtlas;
uniform float u_outlineWidth;
uniform vec4 u_outlineColor;

in vec2 v_texCoord;
in vec4 v_color;

void main() {
    float dist = texture(u_glyphAtlas, v_texCoord).a;

    // Smoothstep for anti-aliased edges
    float alpha = smoothstep(0.5 - u_smoothing, 0.5 + u_smoothing, dist);

    // Outline
    float outlineAlpha = smoothstep(0.5 - u_outlineWidth - u_smoothing,
                                     0.5 - u_outlineWidth + u_smoothing, dist);

    vec4 color = mix(u_outlineColor, v_color, alpha);
    color.a *= outlineAlpha;

    gl_FragColor = color;
}
```

### 4. Alternative: Multi-Sample Outline (Simpler)

If SDF generation is too complex, sample 8 neighboring texels:

```glsl
void main() {
    float center = texture(u_glyphAtlas, v_texCoord).a;

    // Sample 8 neighbors for outline
    float outline = 0.0;
    vec2 texelSize = 1.0 / textureSize(u_glyphAtlas, 0);
    for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
            if (dx != 0 || dy != 0) {
                outline = max(outline, texture(u_glyphAtlas,
                    v_texCoord + vec2(dx, dy) * texelSize).a);
            }
        }
    }

    // Combine outline and fill
    vec4 outlineColor = vec4(0.0, 0.0, 0.0, outline);
    vec4 fillColor = vec4(v_color.rgb, center);
    gl_FragColor = mix(outlineColor, fillColor, center);
}
```

## Implementation Steps

1. **Create GlyphAtlas class**
   - `uk.co.jamesj999.sonic.debug.GlyphAtlas`
   - Generate atlas from Java Font at startup
   - Support ASCII + common debug characters

2. **Create debug text shader**
   - `src/main/resources/shaders/shader_debug_text.glsl`
   - Vertex shader for instanced quads
   - Fragment shader with outline effect

3. **Create GlyphBatchRenderer**
   - `uk.co.jamesj999.sonic.debug.GlyphBatchRenderer`
   - Accumulate glyph instances during frame
   - Single draw call at end of debug rendering

4. **Modify DebugRenderer**
   - Replace TextRenderer calls with GlyphBatchRenderer
   - Remove `drawOutlined()` method
   - Add color to batch submission

5. **Modify PerformancePanelRenderer**
   - Use same GlyphBatchRenderer for panel text

## API Design

```java
public class GlyphBatchRenderer {
    private GlyphAtlas atlas;
    private FloatBuffer instanceData;
    private int instanceCount;

    public void begin() {
        instanceCount = 0;
    }

    public void drawText(String text, int x, int y, Color color) {
        for (char c : text.toCharArray()) {
            GlyphInfo glyph = atlas.getGlyph(c);
            addInstance(x, y, glyph, color);
            x += glyph.advance;
        }
    }

    public void drawTextOutlined(String text, int x, int y,
                                  Color fillColor, Color outlineColor) {
        // Same as above, shader handles outline
    }

    public void end(GL2 gl) {
        // Upload instance data
        // Single instanced draw call
        gl.bindVertexArray(vao);
        gl.drawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, instanceCount);
    }
}
```

## Expected Benefits

| Metric | Before | After |
|--------|--------|-------|
| Draw calls (object labels) | 540-1350 | **1** |
| Draw calls (all debug text) | 700-1500 | **1** |
| CPU text rasterization | Per-frame | Once at startup |
| Context switches | Per-string | None |

**Total reduction:** ~99% fewer draw calls in debug mode.

## Testing

1. **Visual:**
   - Text legibility at various sizes
   - Outline quality vs. original
   - Color accuracy

2. **Performance:**
   - Measure frame time with debug overlay enabled
   - Compare draw call count (RenderDoc/apitrace)

3. **Coverage:**
   - All debug text types render correctly
   - Unicode/special characters (if needed)
   - Different screen resolutions

## Risks

- **Low:** Glyph atlas generation complexity
- **Medium:** SDF generation for arbitrary fonts
- **Mitigation:** Use simple multi-sample outline instead of SDF
- **Fallback:** Keep TextRenderer for rare edge cases

## Files to Create/Modify

**Create:**
- `src/main/java/uk/co/jamesj999/sonic/debug/GlyphAtlas.java`
- `src/main/java/uk/co/jamesj999/sonic/debug/GlyphBatchRenderer.java`
- `src/main/resources/shaders/shader_debug_text.glsl`

**Modify:**
- `src/main/java/uk/co/jamesj999/sonic/debug/DebugRenderer.java`
- `src/main/java/uk/co/jamesj999/sonic/debug/PerformancePanelRenderer.java`
