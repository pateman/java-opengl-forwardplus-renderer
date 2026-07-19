# Forward+ Rendering Demo — Java / LWJGL / OpenGL 4.3

This repository contains a Java demo implementing a small Forward+ renderer using LWJGL and modern OpenGL. It was built as an educational implementation inspired by Harada, McKee, and Yang's [*Forward+: Bringing Deferred Lighting to the Next Level*](https://takahiroharada.wordpress.com/wp-content/uploads/2015/04/forward_plus.pdf).

The goal is not to be a full engine, but to show the complete rendering pipeline in one place: depth pre-pass, GPU tiled light culling, forward shading with many point lights, shadow mapping, FPS reporting, and a simple post-processing pass. Plus, it's been ages since I've last written anything outside my regular e-commerce projects, so it a nice change.

## Features

- GLFW window and OpenGL 4.3 core context.
- Simple cube-based scene.
- Hundreds of animated point lights.
- Randomized point light colors.
- Depth-only pre-pass.
- GPU tiled light culling using a compute shader.
- Per-tile light index lists stored in shader storage buffer objects.
- Final forward shading that only evaluates lights affecting the current screen tile.
- Directional light with shadow mapping.
- 3x3 PCF shadow filtering.
- FPS and frame-time display in the window title.
- Fullscreen post-processing pass.
- Toggleable vignette effect.
- Debug heatmap showing how many lights affect each screen tile.

## Requirements

- Java 17 or newer
- LWJGL 3
- JOML
- GPU and driver supporting OpenGL 4.3+

## Controls

```text
ESC  Exit
F    Toggle tile-light-count debug heatmap
V    Toggle vignette post-processing
```

## Renderer architecture

### 1. Shadow map pass

The demo renders the scene from the directional light's point of view into a depth texture. This shadow map is later sampled during the forward shading pass.

The implementation uses:

- fixed-size shadow map
- orthographic light projection
- directional light shadows
- front-face culling during the shadow pass to reduce shadow acne
- receiver-side depth bias
- 3x3 PCF filtering

Only the directional light casts shadows. The Forward+ point lights do not cast shadows in this demo.

Supporting shadows for all point lights would require a more expensive setup, such as cubemap shadow maps per selected point light or a shadow atlas for local lights.

### 2. Depth pre-pass

Before the Forward+ light culling pass, the scene is rendered with a depth-only shader.

This pass fills the camera depth buffer without writing color. The compute shader then reads this depth texture to estimate the visible depth range for each screen tile.

The depth pre-pass is important because Forward+ shading can be expensive if overdraw is high. Rendering only visible fragments in the final pass avoids wasting lighting work on hidden pixels.

### 3. Tiled light culling

The screen is divided into fixed-size tiles:

```java
static final int TILE_SIZE = 16;
static final int MAX_LIGHTS_PER_TILE = 256;
```

For each tile, a compute shader:

1. Reads depth values for pixels in the tile.
2. Computes minimum and maximum depth.
3. Reconstructs approximate view-space tile bounds.
4. Tests each point light against the tile bounds.
5. Stores matching light indices in a tile-local list.
6. Writes the tile list offset and light count into a tile header buffer.

The implementation uses a simple fixed-size allocation scheme:

```text
tile offset = tileIndex * MAX_LIGHTS_PER_TILE
```

This wastes some memory, but it keeps the demo simple and easy to debug.

The important GPU buffers are:

```text
SSBO 0: Light buffer
SSBO 1: Tile header buffer
SSBO 2: Light index buffer
```

Conceptually:

```glsl
struct Light {
    vec4 positionRadius; // xyz = view-space position, w = radius
    vec4 colorIntensity; // rgb = color, w = intensity
};

uvec2 tileHeaders[]; // x = offset, y = count
uint lightIndices[];
```

### 4. Forward+ shading pass

The final scene pass is still a forward render.

For each fragment, the shader computes the tile coordinate:

```glsl
ivec2 tile = ivec2(gl_FragCoord.xy) / TILE_SIZE;
```

Then it fetches the tile header, loops over that tile's light indices, and evaluates only those lights.

The forward shader combines:

- ambient term
- shadowed directional light
- Forward+ point lights
- Blinn-Phong-style specular
- distance attenuation
- simple tonemapping and gamma correction

This keeps the material/shading path forward while avoiding a full scene-wide light loop for every pixel.

### 5. Debug heatmap

A debug mode visualizes how many lights are assigned to each tile.

Instead of rendering normal material shading, the fragment shader maps the tile's light count to a heatmap color. This makes it easier to verify that the compute culling pass is working.

### 6. Post-processing and vignette

The final version renders the Forward+ scene into an offscreen color texture. Instead of blitting that texture directly to the default framebuffer, it runs a fullscreen post-processing pass.

The post pass uses a fullscreen triangle generated from `gl_VertexID`, so it does not need an extra vertex buffer.

The vignette darkens the image toward the corners.

## Possible future improvements

Good next steps would be:

- Add WASD/mouse camera controls
- Add real mesh loading
- Add texture maps and per-material parameters
- Replace Blinn-Phong lighting with PBR
- Implement exact tile frustum plane tests
- Add clustered shading instead of 2D tiled Forward+
- Add cascaded shadow maps for the directional light
- Add selected point-light shadow maps
- Implement weighted blended order-independent transparency
- Add bloom, color grading, FXAA, or better tonemapping
