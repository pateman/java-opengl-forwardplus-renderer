package pl.pateman.forwardplus.scene;

import pl.pateman.forwardplus.scene.entity.light.PointLight;
import pl.pateman.forwardplus.scene.entity.mesh.MeshInstance;

import java.util.ArrayList;
import java.util.List;

public final class Scene {

    private final List<MeshInstance> meshInstances = new ArrayList<>();
    private final List<PointLight> lights = new ArrayList<>();

    public List<MeshInstance> getMeshInstances() {
        return meshInstances;
    }

    public List<PointLight> getLights() {
        return lights;
    }
}
