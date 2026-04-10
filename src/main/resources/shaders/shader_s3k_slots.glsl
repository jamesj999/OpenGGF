#version 410 core

uniform sampler2D SlotFaceTexture;
uniform sampler2D Palette;
uniform float TotalPaletteLines;

uniform int SlotFace0;
uniform int SlotFace1;
uniform int SlotFace2;
uniform int SlotNextFace0;
uniform int SlotNextFace1;
uniform int SlotNextFace2;
uniform float SlotOffset0;
uniform float SlotOffset1;
uniform float SlotOffset2;

uniform float ScreenX;
uniform float ScreenY;
uniform float ScreenWidth;
uniform float ScreenHeight;
uniform float ViewportWidth;
uniform float ViewportHeight;
uniform float PaletteLine;

const float SLOT_WIDTH = 32.0;
const float SLOT_HEIGHT = 32.0;
const float SLOT_SPACING = 0.0;
const float FACE_WIDTH = 32.0;
const float FACE_HEIGHT = 32.0;
const float TEXTURE_HEIGHT = 256.0;

out vec4 FragColor;

void main() {
    float scaleX = ScreenWidth / ViewportWidth;
    float scaleY = ScreenHeight / ViewportHeight;

    float pixelX = gl_FragCoord.x * scaleX;
    float pixelY = ScreenHeight - (gl_FragCoord.y * scaleY);

    float totalWidth = SLOT_WIDTH * 3.0 + SLOT_SPACING * 2.0;
    float localX = pixelX - ScreenX;
    float localY = pixelY - ScreenY;

    if (localX < 0.0 || localX >= totalWidth || localY < 0.0 || localY >= SLOT_HEIGHT) {
        discard;
    }

    int slotIndex = int(floor(localX / (SLOT_WIDTH + SLOT_SPACING)));
    if (slotIndex > 2) {
        slotIndex = 2;
    }

    int slotFace;
    int slotNextFace;
    float slotOffset;
    if (slotIndex == 0) {
        slotFace = SlotFace0;
        slotNextFace = SlotNextFace0;
        slotOffset = SlotOffset0;
    } else if (slotIndex == 1) {
        slotFace = SlotFace1;
        slotNextFace = SlotNextFace1;
        slotOffset = SlotOffset1;
    } else {
        slotFace = SlotFace2;
        slotNextFace = SlotNextFace2;
        slotOffset = SlotOffset2;
    }

    float slotLocalX = localX - float(slotIndex) * (SLOT_WIDTH + SLOT_SPACING);
    float slotLocalY = localY;

    float u = (slotLocalX + 0.5) / FACE_WIDTH;
    float scrolledY = slotLocalY + slotOffset * SLOT_HEIGHT;
    float faceIndex = float(slotFace);
    if (scrolledY >= FACE_HEIGHT) {
        scrolledY -= FACE_HEIGHT;
        faceIndex = float(slotNextFace);
    }

    float textureV = (faceIndex * FACE_HEIGHT + scrolledY + 0.5) / TEXTURE_HEIGHT;
    float colorIndex = texture(SlotFaceTexture, vec2(u, textureV)).r * 255.0;
    if (colorIndex < 0.5) {
        discard;
    }

    float paletteX = (colorIndex + 0.5) / 16.0;
    float paletteY = (PaletteLine + 0.5) / TotalPaletteLines;
    FragColor = texture(Palette, vec2(paletteX, paletteY));
}
