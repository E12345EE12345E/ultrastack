#ifdef GL_ES
precision mediump float;
#endif

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform vec2 u_resolution;
uniform vec2 u_center;
uniform float u_tileSize;
uniform float u_radius;
uniform float u_widthMult;
uniform float u_heightMult;
uniform float u_thickness;
uniform float u_rippleIntensity;
uniform float u_time;

uniform vec4 u_colors[16];
uniform int u_colorCount;
uniform float u_minOpacity;
uniform float u_maxOpacity;
uniform int u_colorMode;        // 0 = ANGULAR, 1 = RADIAL, 2 = NOISE
uniform float u_colorShiftSpeed;

// --- Simplex Noise 2D ---
vec3 permute(vec3 x) { return mod(((x*34.0)+1.0)*x, 289.0); }

float snoise(vec2 v){
  const vec4 C = vec4(0.211324865405187, 0.366025403784439,
                     -0.577350269189626, 0.024390243902439);
  vec2 i  = floor(v + dot(v, C.yy) );
  vec2 x0 = v -   i + dot(i, C.xx);
  vec2 i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
  vec4 x12 = x0.xyxy + C.xxzz;
  x12.xy -= i1;
  i = mod(i, 289.0);
  vec3 p = permute( permute( i.y + vec3(0.0, i1.y, 1.0 ))
  + i.x + vec3(0.0, i1.x, 1.0 ));
  vec3 m = max(0.5 - vec3(dot(x0,x0), dot(x12.xy,x12.xy), dot(x12.zw,x12.zw)), 0.0);
  m = m*m;
  m = m*m;
  vec3 x = 2.0 * fract(p * C.www) - 1.0;
  vec3 h = abs(x) - 0.5;
  vec3 ox = floor(x + 0.5);
  vec3 a0 = x - ox;
  m *= 1.79284291400159 - 0.85373472095314 * ( a0*a0 + h*h );
  vec3 g;
  g.x  = a0.x  * x0.x  + h.x  * x0.y;
  g.yz = a0.yz * x12.xz + h.yz * x12.yw;
  return 130.0 * dot(m, g);
}

vec4 getColor(int index) {
    for (int i = 0; i < 16; i++) {
        if (i == index) return u_colors[i];
    }
    return vec4(1.0);
}

void main() {
    vec2 pixelPos = gl_FragCoord.xy;
    vec2 pixelDiff = pixelPos - u_center;

    // Difference in board tile units
    vec2 posTiles = pixelDiff / u_tileSize;

    float w = max(u_widthMult, 0.001);
    float h = max(u_heightMult, 0.001);

    // Angle around center in board space
    float angle = atan(posTiles.y, posTiles.x);
    float cosA = cos(angle);
    float sinA = sin(angle);

    // Unrippled ellipse base radius along direction 'angle' in board tiles
    float denom = sqrt((cosA * cosA) / (w * w) + (sinA * sinA) / (h * h));
    float r_ellipse = u_radius / denom;

    // Normalized multi-frequency waves (sum of coefficients = 1.0)
    float waveInner = 0.55 * sin(4.0 * angle + u_time * 2.3)
                    + 0.30 * cos(7.0 * angle - u_time * 1.7)
                    + 0.15 * sin(3.0 * angle + u_time * 3.1);

    float waveOuter = 0.55 * cos(5.0 * angle - u_time * 2.7)
                    + 0.30 * sin(8.0 * angle + u_time * 1.9)
                    + 0.15 * cos(3.0 * angle - u_time * 3.5);

    float rippleInner = u_rippleIntensity * waveInner;
    float rippleOuter = u_rippleIntensity * waveOuter;

    // Average thickness and boundary radii
    float halfThickness = u_thickness * 0.5;
    float r_inner = r_ellipse - halfThickness + rippleInner;
    float r_outer = r_ellipse + halfThickness + rippleOuter;

    // Radial distance of point from center in board tiles
    float r_point = length(posTiles);
    float d_inner = r_point - r_inner;
    float d_outer = r_outer - r_point;

    // Perpendicular correction factor for elliptical distortion
    vec2 normalVec = normalize(vec2(cosA / (w * w), sinA / (h * h)));
    float cosNormal = abs(dot(normalVec, vec2(cosA, sinA)));
    float p_inner = d_inner * cosNormal;
    float p_outer = d_outer * cosNormal;

    // Smooth anti-aliased edges (0.2 tiles feathering)
    float edgeFeather = 0.2;
    float alphaInner = smoothstep(-edgeFeather, edgeFeather, p_inner);
    float alphaOuter = smoothstep(-edgeFeather, edgeFeather, p_outer);
    float intensity = alphaInner * alphaOuter;

    if (intensity <= 0.0) {
        discard;
    }

    // Shader's own multiplying pulse factor (oscillates between u_minOpacity and u_maxOpacity)
    float wavePulse = 0.5 + 0.5 * sin(u_time * 3.5 + angle * 2.0);
    float pulse = mix(u_minOpacity, u_maxOpacity, wavePulse);
    float multiplier = intensity * pulse;

    // Color interpolation across the color array
    vec4 baseColor = vec4(1.0);
    int count = u_colorCount;
    if (count <= 1) {
        baseColor = u_colors[0];
    } else {
        float shift = u_time * u_colorShiftSpeed;
        float u = 0.0;

        if (u_colorMode == 0) {
            // ANGULAR (Perimeter angle)
            float normAngle = (angle + 3.141592653589793) / 6.283185307179586;
            u = fract(normAngle + shift);
        } else if (u_colorMode == 1) {
            // RADIAL (Across outline thickness from inner to outer edge)
            float totalP = p_inner + p_outer;
            float normRadial = (totalP > 0.0001) ? (p_inner / totalP) : 0.5;
            u = fract(normRadial + shift);
        } else if (u_colorMode == 2) {
            // NOISE (Smooth Simplex noise function morphing over time - 20x larger scale)
            vec2 noisePos = posTiles * 0.03 + vec2(shift * 0.7, shift * 0.5);
            float n = snoise(noisePos);
            u = clamp(0.5 + 0.5 * n, 0.0, 1.0);
        }

        float idxFloat = u * float(count);
        int i0 = int(floor(idxFloat));
        int i1 = i0 + 1;
        float frac = fract(idxFloat);

        i0 = i0 - (i0 / count) * count; // i0 % count
        i1 = i1 - (i1 / count) * count; // i1 % count
        if (i0 < 0) i0 += count;
        if (i1 < 0) i1 += count;

        vec4 c0 = getColor(i0);
        vec4 c1 = getColor(i1);
        baseColor = mix(c0, c1, frac);
    }

    // Multiply baseColor (including its alpha) by shader's multiplying values and vertex alpha
    gl_FragColor = vec4(baseColor.rgb, baseColor.a * multiplier * v_color.a);
}
