package pl.pateman.forwardplus.scene.entity.camera;

import org.joml.Matrix4f;

public final class Camera {
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f view = new Matrix4f();
    private final Matrix4f invProjection = new Matrix4f();

    private final int screenWidth;
    private final int screenHeight;

    public Camera(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    public void update(double deltaTime) {
        float aspect = screenWidth / (float) screenHeight;

        projection.identity()
                .perspective((float) Math.toRadians(60.0), aspect, 0.1f, 100.0f);

        float orbitRadius = 18.0f;
        float camX = (float) Math.sin(deltaTime * 0.18) * orbitRadius;
        float camZ = (float) Math.cos(deltaTime * 0.18) * orbitRadius;

        view.identity()
                .lookAt(
                        camX, 8.5f, camZ,
                        0.0f, 0.8f, 0.0f,
                        0.0f, 1.0f, 0.0f
                );

        projection.invert(invProjection);
    }

    public Matrix4f getProjection() {
        return projection;
    }

    public Matrix4f getView() {
        return view;
    }

    public Matrix4f getInvProjection() {
        return invProjection;
    }
}
