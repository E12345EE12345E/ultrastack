#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform vec2 u_resolution;
uniform int u_waveCount;
uniform vec2 u_centers[4];
uniform float u_progress[4];
uniform float u_maxRadius[4];
uniform float u_amplitudes[4];
uniform float u_crest[4];
uniform float u_thickness;

// Screen-space displacement for one expanding ring. Offset is in UV; the ring lives in
// aspect-corrected space so it stays circular on widescreen.
void addWave(inout vec2 offset, inout float highlight,
             vec2 uv, vec2 aspect, vec2 center, float progress, float maxRadius,
             float amplitude, float crest) {
    vec2 fromCenter = (uv - center) * aspect;
    float dist = length(fromCenter);
    if (dist < 1e-5) return;

    float radius = progress * maxRadius;
    float x = (dist - radius) / max(u_thickness, 1e-4);
    float envelope = exp(-x * x);
    // Derivative-of-Gaussian: pixels just inside the front pull out, just outside push away.
    float disp = envelope * x * amplitude * (1.0 - progress);
    vec2 dir = fromCenter / dist;
    vec2 o = dir * disp;
    o.x /= aspect.x;
    offset += o;
    highlight += envelope * 0.18 * crest;
}

void main() {
    vec2 uv = v_texCoords;
    float aspectX = u_resolution.x / max(u_resolution.y, 1.0);
    vec2 aspect = vec2(aspectX, 1.0);

    vec2 offset = vec2(0.0);
    float highlight = 0.0;
    if (u_waveCount > 0) addWave(offset, highlight, uv, aspect, u_centers[0], u_progress[0], u_maxRadius[0], u_amplitudes[0], u_crest[0]);
    if (u_waveCount > 1) addWave(offset, highlight, uv, aspect, u_centers[1], u_progress[1], u_maxRadius[1], u_amplitudes[1], u_crest[1]);
    if (u_waveCount > 2) addWave(offset, highlight, uv, aspect, u_centers[2], u_progress[2], u_maxRadius[2], u_amplitudes[2], u_crest[2]);
    if (u_waveCount > 3) addWave(offset, highlight, uv, aspect, u_centers[3], u_progress[3], u_maxRadius[3], u_amplitudes[3], u_crest[3]);

    vec4 color = texture2D(u_texture, uv + offset);
    color.rgb = mix(color.rgb, vec3(1.0), clamp(highlight, 0.0, 0.28));
    gl_FragColor = color * v_color;
}
