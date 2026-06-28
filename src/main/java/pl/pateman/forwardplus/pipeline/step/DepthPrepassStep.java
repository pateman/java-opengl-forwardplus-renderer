package pl.pateman.forwardplus.pipeline.step;

import org.joml.Matrix4f;
import pl.pateman.forwardplus.scene.Scene;
import pl.pateman.forwardplus.scene.entity.mesh.MeshInstance;
import pl.pateman.forwardplus.shader.ShaderProgram;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;

public class DepthPrepassStep extends AbstractPipelineStep {

    private final int mainFbo;
    private final ShaderProgram depthProgram;

    public DepthPrepassStep(int mainFbo, ShaderProgram depthProgram) {
        this.mainFbo = mainFbo;
        this.depthProgram = depthProgram;
    }

    @Override
    public void init() {
        // Do nothing.
    }

    @Override
    public void render(Scene scene) {
        glBindFramebuffer(GL_FRAMEBUFFER, mainFbo);
        glViewport(0, 0, getInput("screenWidth", Integer.class), getInput("screenHeight", Integer.class));

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        glDepthMask(true);

        // Depth-only pre-pass: no color writes.
        glColorMask(false, false, false, false);
        glClearDepth(1.0);
        glClear(GL_DEPTH_BUFFER_BIT);

        depthProgram.use();
        depthProgram.setMat4("uView", getInput("viewMatrix", Matrix4f.class));
        depthProgram.setMat4("uProjection", getInput("projectionMatrix", Matrix4f.class));

        for (int i = 0; i < scene.getMeshInstances().size(); i++) {
            MeshInstance instance = scene.getMeshInstances().get(i);
            depthProgram.setMat4("uModel", instance.model());
            instance.mesh().draw();
        }

        glColorMask(true, true, true, true);
    }

    @Override
    public void destroy() {
        // Do nothing.
    }
}
