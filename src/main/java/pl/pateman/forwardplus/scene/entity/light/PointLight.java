package pl.pateman.forwardplus.scene.entity.light;

import org.joml.Vector3f;

public record PointLight(Vector3f position, Vector3f color, float radius, float intensity, float phase) {
}
