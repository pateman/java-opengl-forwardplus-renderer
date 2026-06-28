#version 430 core

#define TILE_SIZE 16
#define MAX_LIGHTS_PER_TILE 256

layout(local_size_x = TILE_SIZE, local_size_y = TILE_SIZE, local_size_z = 1) in;

struct Light {
    vec4 positionRadius;// xyz = view-space position, w = radius
    vec4 colorIntensity;
};

layout(std430, binding = 0) readonly buffer LightBuffer {
    Light lights[];
};

layout(std430, binding = 1) writeonly buffer TileHeaderBuffer {
    uvec2 tileHeaders[];// x = offset, y = count
};

layout(std430, binding = 2) writeonly buffer LightIndexBuffer {
    uint lightIndices[];
};

uniform sampler2D uDepthTexture;

uniform int uScreenSizeX;
uniform int uScreenSizeY;
uniform int uTileCountX;
uniform int uTileCountY;
uniform int uNumLights;

uniform mat4 uInvProjection;

shared uint sMinDepthBits;
shared uint sMaxDepthBits;
shared uint sLightCount;
shared uint sLightIndices[MAX_LIGHTS_PER_TILE];

vec3 screenToView(vec2 screenPos, float depth) {
    vec2 screenSize = vec2(float(uScreenSizeX), float(uScreenSizeY));
    vec2 uv = screenPos / screenSize;

    vec4 clip = vec4(
    uv * 2.0 - 1.0,
    depth * 2.0 - 1.0,
    1.0
    );

    vec4 view = uInvProjection * clip;
    return view.xyz / view.w;
}

bool sphereIntersectsAabb(vec3 center, float radius, vec3 minB, vec3 maxB) {
    vec3 closest = clamp(center, minB, maxB);
    vec3 delta = center - closest;
    return dot(delta, delta) <= radius * radius;
}

void main() {
    uvec2 localId = gl_LocalInvocationID.xy;
    uvec2 tileId = gl_WorkGroupID.xy;

    ivec2 pixel = ivec2(tileId * TILE_SIZE + localId);

    if (gl_LocalInvocationIndex == 0u) {
        sMinDepthBits = floatBitsToUint(1.0);
        sMaxDepthBits = floatBitsToUint(0.0);
        sLightCount = 0u;
    }

    barrier();

    if (pixel.x < uScreenSizeX && pixel.y < uScreenSizeY) {
        float depth = texelFetch(uDepthTexture, pixel, 0).r;

        // Ignore untouched far-plane depth. It massively reduces false positives
        // in tiles that contain background pixels.
        if (depth < 1.0) {
            atomicMin(sMinDepthBits, floatBitsToUint(depth));
            atomicMax(sMaxDepthBits, floatBitsToUint(depth));
        }
    }

    barrier();

    float minDepth = uintBitsToFloat(sMinDepthBits);
    float maxDepth = uintBitsToFloat(sMaxDepthBits);

    // If the tile has no geometry, no visible fragment will use this list anyway.
    bool tileHasGeometry = maxDepth > 0.0 && minDepth < 1.0;

    vec2 tileMin = vec2(tileId * TILE_SIZE);
    vec2 tileMax = min(tileMin + vec2(TILE_SIZE), vec2(float(uScreenSizeX), float(uScreenSizeY)));

    vec3 minBounds = vec3(1e20);
    vec3 maxBounds = vec3(-1e20);

    if (tileHasGeometry) {
        vec3 corners[8];

        corners[0] = screenToView(vec2(tileMin.x, tileMin.y), minDepth);
        corners[1] = screenToView(vec2(tileMax.x, tileMin.y), minDepth);
        corners[2] = screenToView(vec2(tileMin.x, tileMax.y), minDepth);
        corners[3] = screenToView(vec2(tileMax.x, tileMax.y), minDepth);

        corners[4] = screenToView(vec2(tileMin.x, tileMin.y), maxDepth);
        corners[5] = screenToView(vec2(tileMax.x, tileMin.y), maxDepth);
        corners[6] = screenToView(vec2(tileMin.x, tileMax.y), maxDepth);
        corners[7] = screenToView(vec2(tileMax.x, tileMax.y), maxDepth);

        for (int i = 0; i < 8; i++) {
            minBounds = min(minBounds, corners[i]);
            maxBounds = max(maxBounds, corners[i]);
        }
    }

    uint threadIndex = gl_LocalInvocationIndex;
    uint threadCount = gl_WorkGroupSize.x * gl_WorkGroupSize.y;

    if (tileHasGeometry) {
        for (uint lightIndex = threadIndex; lightIndex < uint(uNumLights); lightIndex += threadCount) {
            Light light = lights[lightIndex];

            vec3 lightPos = light.positionRadius.xyz;
            float radius = light.positionRadius.w;

            if (sphereIntersectsAabb(lightPos, radius, minBounds, maxBounds)) {
                uint dst = atomicAdd(sLightCount, 1u);

                if (dst < MAX_LIGHTS_PER_TILE) {
                    sLightIndices[dst] = lightIndex;
                }
            }
        }
    }

    barrier();

    if (gl_LocalInvocationIndex == 0u) {
        uint tileIndex = uint(tileId.y * uint(uTileCountX) + tileId.x);
        uint offset = tileIndex * uint(MAX_LIGHTS_PER_TILE);
        uint count = min(sLightCount, uint(MAX_LIGHTS_PER_TILE));

        tileHeaders[tileIndex] = uvec2(offset, count);

        for (uint i = 0u; i < count; i++) {
            lightIndices[offset + i] = sLightIndices[i];
        }
    }
}