#version 410 core

// Debug color fragment shader for rendering debug primitives

in vec4 v_color;

out vec4 FragColor;

void main()
{
    FragColor = v_color;
}
