#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform float u_direction;
uniform float u_magnitude;

void main() {
    vec2 offset = vec2(cos(u_direction), sin(u_direction)) * u_magnitude;

    vec4 colR = texture2D(u_texture, v_texCoords + offset);
    vec4 colG = texture2D(u_texture, v_texCoords);
    vec4 colB = texture2D(u_texture, v_texCoords - offset);

    float alpha = max(colR.a, max(colG.a, colB.a));
    gl_FragColor = vec4(colR.r, colG.g, colB.b, alpha) * v_color;
}
