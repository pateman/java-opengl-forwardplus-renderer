package pl.pateman.forwardplus.scene.entity.mesh;

import org.joml.Matrix4f;
import org.joml.Vector3f;


public record MeshInstance(Mesh mesh, Matrix4f model, Vector3f albedo) {
}
