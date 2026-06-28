#version 430 core

#define TILE_SIZE 16

struct Light {
    vec4 positionRadius;// xyz = view-space position, w = radius
    vec4 colorIntensity;// rgb = color, w = intensity
};

layout(std430, binding = 0) readonly buffer LightBuffer {
    Light lights[];
};

layout(std430, binding = 1) readonly buffer TileHeaderBuffer {
    uvec2 tileHeaders[];// x = offset, y = count
};

layout(std430, binding = 2) readonly buffer LightIndexBuffer {
    uint lightIndices[];
};

in vec3 vViewPos;
in vec3 vViewNormal;
in vec2 vUv;
in vec4 vShadowCoord;

out vec4 fragColor;

uniform int uScreenSizeX;
uniform int uScreenSizeY;
uniform int uTileCountX;
uniform int uTileCountY;
uniform int uDebugTiles;

uniform vec3 uAlbedo;

uniform sampler2D uShadowMap;
uniform vec3 uSunDirectionView;
uniform vec3 uSunColor;

float calculateShadow(vec4 shadowCoord, vec3 normal, vec3 lightDir) {
    vec3 proj = shadowCoord.xyz / shadowCoord.w;
    proj = proj * 0.5 + 0.5;

    if (proj.x < 0.0 || proj.x > 1.0 || proj.y < 0.0 || proj.y > 1.0 || proj.z > 1.0) {
        return 0.0;
    }

    float ndotl = max(dot(normal, lightDir), 0.0);
    float bias = max(0.004 * (1.0 - ndotl), 0.0008);

    vec2 texelSize = 1.0 / vec2(textureSize(uShadowMap, 0));
    float shadow = 0.0;

    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            float closestDepth = texture(uShadowMap, proj.xy + vec2(x, y) * texelSize).r;
            shadow += (proj.z - bias) > closestDepth ? 1.0 : 0.0;
        }
    }

    return shadow / 9.0;
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    ivec2 tile = pixel / TILE_SIZE;

    tile.x = clamp(tile.x, 0, uTileCountX - 1);
    tile.y = clamp(tile.y, 0, uTileCountY - 1);

    uint tileIndex = uint(tile.y * uTileCountX + tile.x);
    uvec2 header = tileHeaders[tileIndex];

    uint offset = header.x;
    uint count = header.y;

    if (uDebugTiles != 0) {
        float heat = clamp(float(count) / 64.0, 0.0, 1.0);
        vec3 cold = vec3(0.05, 0.15, 0.85);
        vec3 mid  = vec3(0.05, 0.85, 0.25);
        vec3 hot  = vec3(1.00, 0.10, 0.03);

        vec3 color = heat < 0.5
        ? mix(cold, mid, heat * 2.0)
        : mix(mid, hot, (heat - 0.5) * 2.0);

        fragColor = vec4(color, 1.0);
        return;
    }

    vec3 normal = normalize(vViewNormal);
    vec3 viewDir = normalize(-vViewPos);

    vec3 result = uAlbedo * 0.035;

    // One shadowed directional/sun light, separate from the Forward+ point-light list.
    vec3 sunDir = normalize(uSunDirectionView);
    float sunNdotL = max(dot(normal, sunDir), 0.0);
    float shadow = calculateShadow(vShadowCoord, normal, sunDir);
    vec3 sunDiffuse = uAlbedo * uSunColor * sunNdotL;
    result += sunDiffuse * (1.0 - shadow) * 0.85;

    for (uint i = 0; i < count; i++) {
        uint lightIndex = lightIndices[offset + i];
        Light light = lights[lightIndex];

        vec3 lightPos = light.positionRadius.xyz;
        float radius = light.positionRadius.w;

        vec3 toLight = lightPos - vViewPos;
        float distSq = dot(toLight, toLight);
        float radiusSq = radius * radius;

        if (distSq > radiusSq) {
            continue;
        }

        float dist = sqrt(distSq);
        vec3 lightDir = toLight / max(dist, 0.0001);

        float linear = 1.0 - clamp(dist / radius, 0.0, 1.0);
        float attenuation = linear * linear;

        vec3 lightColor = light.colorIntensity.rgb * light.colorIntensity.w;

        float ndotl = max(dot(normal, lightDir), 0.0);

        vec3 halfDir = normalize(lightDir + viewDir);
        float specularTerm = pow(max(dot(normal, halfDir), 0.0), 48.0);

        vec3 diffuse = uAlbedo * lightColor * ndotl;
        vec3 specular = lightColor * specularTerm * 0.20;

        result += (diffuse + specular) * attenuation;
    }

    // Simple tonemap + gamma-ish output.
    result = result / (result + vec3(1.0));
    result = pow(result, vec3(1.0 / 2.2));

    fragColor = vec4(result, 1.0);
}