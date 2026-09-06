#ifdef GL_ES
precision highp float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform vec2 u_resolution;
uniform float u_time;
uniform float u_intensity;
uniform float u_starDensity;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 m = mat2(1.6, 1.2, -1.2, 1.6);
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = m * p;
        a *= 0.5;
    }
    return v;
}

void main() {
    vec2 uv = v_texCoords;
    float aspect = u_resolution.x / max(u_resolution.y, 1.0);
    vec2 p = vec2(uv.x * aspect, uv.y);
    float t = u_time;

    vec2 q = p * 1.15;
    q.x += t * 0.03;
    float n1 = fbm(q + vec2(t * 0.04, t * 0.02));
    vec2 warp = vec2(n1, fbm(q + 3.1));
    float n2 = fbm(q * 1.25 + warp * 1.55 + vec2(-t * 0.03, t * 0.045));
    float n3 = fbm(q * 0.70 + warp * 1.05 + vec2(t * 0.022, -t * 0.035));

    // Two wide, independently drifting curtains. max() keeps coverage up when
    // one layer dips; a little product adds brighter overlap without requiring it.
    float c1 = smoothstep(0.12, 0.48, n2);
    float c2 = smoothstep(0.10, 0.46, n3);
    float curtain = max(c1, c2 * 0.9) + c1 * c2 * 0.35;
    // Never extinguish the field — pulse between dim and bright, not on/off.
    float breathe = 0.70 + 0.30 * sin(t * 0.26 + n1 * 4.0);

    vec3 pink = vec3(0.95, 0.32, 0.68);
    vec3 green = vec3(0.22, 0.88, 0.52);
    vec3 tint = mix(pink, green, smoothstep(0.25, 0.70, n3));
    // curtain max is 1.35; haze max is 0.18. Lift the floor to the midpoint
    // of the old [min, max] so dim sky sits halfway to the old peak.
    float raw = (curtain * breathe * 0.72 + (0.10 + 0.08 * n1)) * 0.45;
    const float rawMax = (1.35 * 0.72 + 0.18) * 0.45;
    raw = 0.5 * (raw + rawMax);
    vec3 aurora = tint * raw * u_intensity;

    float gridScale = max(u_starDensity, 0.15) * 90.0;
    vec2 grid = vec2(uv.x * aspect, uv.y) * gridScale;
    vec2 cell = floor(grid);
    vec2 f = fract(grid);
    float h = hash(cell);
    float star = 0.0;
    // Bright curtains keep the original ~3.8% gate; dim sky opens more cells.
    float auroraAmt = max(aurora.r, max(aurora.g, aurora.b));
    float darkSky = 1.0 - smoothstep(0.18, 0.34, auroraAmt);
    float starGate = mix(0.962, 0.66, darkSky);
    if (h > starGate) {
        vec2 c = vec2(hash(cell + 1.37), hash(cell + 4.19));
        float d = length(f - c);
        // Per-cell clocks so neighbors never share a beat.
        float hRate = hash(cell + 9.13);
        float hRate2 = hash(cell + 5.51);
        float hPhase = hash(cell + 17.77) * 6.28318;
        float hPhase2 = hash(cell + 41.3) * 6.28318;
        float brightHz = 0.7 + hRate * 2.6;
        float dimHz = 2.8 + hRate * 11.0 + hRate2 * 9.0;
        float hz = mix(brightHz, dimHz, darkSky);
        float hz2 = hz * (0.55 + hash(cell + 3.31) * 0.9);
        float twinkle = 0.30 + 0.40 * sin(t * hz + hPhase) + 0.30 * sin(t * hz2 + hPhase2);
        twinkle = clamp(twinkle, 0.0, 1.0);
        float rarity = clamp((h - 0.962) / 0.038, 0.0, 1.0);
        float extra = darkSky * (0.50 + 0.50 * hash(cell + 2.4));
        star = smoothstep(0.038, 0.0, d) * twinkle * max(rarity, extra);
    }

    vec3 col = aurora + vec3(star);
    float alpha = max(max(aurora.r, max(aurora.g, aurora.b)), star);
    gl_FragColor = vec4(col, clamp(alpha, 0.0, 1.0)) * v_color;
}
