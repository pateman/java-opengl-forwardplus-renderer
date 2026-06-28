package pl.pateman.forwardplus.pipeline.step;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import pl.pateman.forwardplus.scene.Scene;
import pl.pateman.forwardplus.scene.entity.mesh.MeshInstance;
import pl.pateman.forwardplus.shader.ShaderProgram;
import pl.pateman.forwardplus.util.TempVars;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL13C.GL_CLAMP_TO_BORDER;
import static org.lwjgl.opengl.GL14C.GL_TEXTURE_COMPARE_MODE;
import static org.lwjgl.opengl.GL30C.*;

public class ShadowMappingStep extends AbstractPipelineStep {

    private static final int SHADOW_MAP_SIZE = 2048;

    // Direction from the scene toward the sun, in world space.
    private static final Vector3f SUN_DIRECTION_WORLD = new Vector3f(-0.45f, 0.85f, 0.35f).normalize();

    private final Matrix4f lightProjection = new Matrix4f();
    private final Matrix4f lightView = new Matrix4f();
    private final Matrix4f lightSpaceMatrix = new Matrix4f();
    private final ShaderProgram depthProgram;

    private int shadowFbo;
    private int shadowDepthTexture;

    public ShadowMappingStep(ShaderProgram depthProgram) {
        this.depthProgram = depthProgram;

        setOutput("lightSpaceMatrix", lightSpaceMatrix);
        setOutput("sunDirectionWorld", SUN_DIRECTION_WORLD);
    }

    @Override
    public void init() {
        shadowFbo = glGenFramebuffers();
        shadowDepthTexture = glGenTextures();
        setOutput("shadowDepthTexture", shadowDepthTexture);

        glBindTexture(GL_TEXTURE_2D, shadowDepthTexture);
        glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_DEPTH_COMPONENT32F,
                SHADOW_MAP_SIZE,
                SHADOW_MAP_SIZE,
                0,
                GL_DEPTH_COMPONENT,
                GL_FLOAT,
                0L
        );
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_NONE);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer borderColor = stack.mallocFloat(4);
            borderColor.put(1.0f).put(1.0f).put(1.0f).put(1.0f).flip();
            glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, borderColor);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, shadowFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, shadowDepthTexture, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);

        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Shadow framebuffer incomplete. Status = 0x" + Integer.toHexString(status));
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    @Override
    public void render(Scene scene) {
        updateShadowMatrices();

        glBindFramebuffer(GL_FRAMEBUFFER, shadowFbo);
        glViewport(0, 0, SHADOW_MAP_SIZE, SHADOW_MAP_SIZE);

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        glDepthMask(true);
        glColorMask(false, false, false, false);

        glClearDepth(1.0);
        glClear(GL_DEPTH_BUFFER_BIT);

        // Front-face culling reduces classic shadow acne for closed meshes.
        // The shader also applies a small receiver-side bias.
        glCullFace(GL_FRONT);

        depthProgram.use();
        depthProgram.setMat4("uView", lightView);
        depthProgram.setMat4("uProjection", lightProjection);

        for (int i = 0; i < scene.getMeshInstances().size(); i++) {
            MeshInstance instance = scene.getMeshInstances().get(i);
            depthProgram.setMat4("uModel", instance.model());
            instance.mesh().draw();
        }

        glCullFace(GL_BACK);
        glColorMask(true, true, true, true);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

    }

    private void updateShadowMatrices() {
        TempVars tempVars = TempVars.get();
        Vector3f target = tempVars.vect3d1.set(0.0f, 0.0f, 0.0f);
        Vector3f eye = tempVars.vect3d2.set(SUN_DIRECTION_WORLD).mul(22.0f).add(target);
        Vector3f up = Math.abs(SUN_DIRECTION_WORLD.dot(0.0f, 1.0f, 0.0f)) > 0.95f
                ? tempVars.vect3d3.set(0.0f, 0.0f, 1.0f)
                : tempVars.vect3d3.set(0.0f, 1.0f, 0.0f);

        lightProjection.identity().ortho(-18.0f, 18.0f, -18.0f, 18.0f, 1.0f, 55.0f);
        lightView.identity().lookAt(eye, target, up);
        lightSpaceMatrix.set(lightProjection).mul(lightView);

        tempVars.release();
    }

    @Override
    public void destroy() {
        glDeleteFramebuffers(shadowFbo);
        glDeleteTextures(shadowDepthTexture);
    }

}
