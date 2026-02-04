#version 120

// Debug color fragment shader for rendering debug primitives

varying vec4 v_color;

void main()
{
    gl_FragColor = v_color;
}
