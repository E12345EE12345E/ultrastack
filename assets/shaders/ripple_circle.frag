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

    // Subtle brightness pulse for extra fluid aesthetic
    float pulse = 0.88 + 0.12 * sin(u_time * 3.5 + angle * 2.0);
    float alpha = intensity * pulse * v_color.a;

    gl_FragColor = vec4(1.0, 1.0, 1.0, alpha);
}
