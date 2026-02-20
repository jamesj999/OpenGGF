#version 410 core

uniform sampler2D Texture;
uniform vec4 Tint;

in vec2 v_texCoord;
out vec4 FragColor;

void main()
{
    FragColor = texture(Texture, v_texCoord) * Tint;
    if (FragColor.a < 0.01) discard;
}
