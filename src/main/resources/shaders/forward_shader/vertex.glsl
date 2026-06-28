#version 430 core

invariant gl_Position;

layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;

out vec3 vViewPos;
out vec3 vViewNormal;
out vec2 vUv;
out vec4 vShadowCoord;

uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProjection;
uniform mat4 uLightSpaceMatrix;
uniform mat3 uNormalMatrix;

void main() {
    vec4 worldPos = uModel * vec4(aPosition, 1.0);
    vec4 viewPos = uView * worldPos;

    vViewPos = viewPos.xyz;
    vViewNormal = normalize(uNormalMatrix * aNormal);
    vUv = aUv;
    vShadowCoord = uLightSpaceMatrix * worldPos;

    gl_Position = uProjection * uView * uModel * vec4(aPosition, 1.0);
}