#version 430 core

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uSceneColor;
uniform int uVignetteEnabled;
uniform int uScreenSizeX;
uniform int uScreenSizeY;

void main() {
    vec3 color = texture(uSceneColor, vUv).rgb;

    if (uVignetteEnabled != 0) {
        float aspect = float(uScreenSizeX) / float(uScreenSizeY);

        // Centered coordinates. Aspect correction keeps the vignette circular
        // instead of stretched on widescreen displays.
        vec2 p = vUv * 2.0 - 1.0;
        p.x *= aspect;

        float dist = length(p);

        // Inner radius: scene is untouched near the center.
        // Outer radius: screen edges become darker.
        float edge = smoothstep(0.55, 1.35, dist);

        // Strength controls how dark the corners get.
        float strength = 0.55;
        float vignette = mix(1.0, 1.0 - strength, edge);

        color *= vignette;
    }

    fragColor = vec4(color, 1.0);
}