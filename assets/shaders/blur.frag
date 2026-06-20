#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;

// Pass (blurRadius/screenWidth, 0) for horizontal, (0, blurRadius/screenHeight) for vertical.
uniform vec2 u_blurDirection;

void main() {
    vec4 sum = vec4(0.0);
    sum += texture2D(u_texture, v_texCoords - u_blurDirection * 3.2307692308) * 0.0702702703;
    sum += texture2D(u_texture, v_texCoords - u_blurDirection * 1.3846153846) * 0.3162162162;
    sum += texture2D(u_texture, v_texCoords                                 ) * 0.2270270270;
    sum += texture2D(u_texture, v_texCoords + u_blurDirection * 1.3846153846) * 0.3162162162;
    sum += texture2D(u_texture, v_texCoords + u_blurDirection * 3.2307692308) * 0.0702702703;
    gl_FragColor = v_color * sum;
}
