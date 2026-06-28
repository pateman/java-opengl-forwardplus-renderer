package pl.pateman.forwardplus.pipeline;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import pl.pateman.forwardplus.pipeline.step.*;
import pl.pateman.forwardplus.scene.Scene;
import pl.pateman.forwardplus.scene.entity.camera.Camera;
import pl.pateman.forwardplus.scene.entity.light.PointLight;
import pl.pateman.forwardplus.shader.ComputeShaderProgram;
import pl.pateman.forwardplus.shader.ShaderProgram;
import pl.pateman.forwardplus.util.TempVars;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL14C.GL_TEXTURE_COMPARE_MODE;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL42C.*;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static pl.pateman.forwardplus.util.IOUtils.readResourceAsString;

public final class RenderPipeline {

    // Keep in sync with TILE_SIZE in the light culling compute shader.
    private static final int TILE_SIZE = 16;

    private final int screenWidth;
    private final int screenHeight;
    private final int tilesX;
    private final int tilesY;

    private int mainFbo;
    private int colorTexture;
    private int depthTexture;

    private ShaderProgram depthProgram;
    private ShaderProgram forwardProgram;
    private ShaderProgram postProcessProgram;
    private ComputeShaderProgram lightCullingProgram;

    private ShadowMappingStep shadowMappingStep;
    private DepthPrepassStep depthPrepassStep;
    private LightCullingStep lightCullingStep;
    private ForwardPassStep forwardPassStep;
    private PostProcessStep postProcessStep;

    public RenderPipeline(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;

        tilesX = (screenWidth + TILE_SIZE - 1) / TILE_SIZE;
        tilesY = (screenHeight + TILE_SIZE - 1) / TILE_SIZE;
    }

    public void init() {
        createFramebuffer();
        createShaderPrograms();
        createPipelineSteps();

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }

    public void render(Camera camera, Scene scene, double deltaTime, boolean vignetteEnabled, boolean debugTiles) {
        animateLights(camera, scene, deltaTime);
        shadowMappingStep.render(scene);

        depthPrepassStep.setInput("viewMatrix", camera.getView());
        depthPrepassStep.setInput("projectionMatrix", camera.getProjection());
        depthPrepassStep.render(scene);

        // Make the depth texture writes visible to the compute shader texture fetch.
        glMemoryBarrier(GL_FRAMEBUFFER_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);

        lightCullingStep.setInput("invProjectionMatrix", camera.getInvProjection());
        lightCullingStep.render(scene);

        // Make compute SSBO writes visible to the fragment shader.
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        forwardPassStep.setInput("viewMatrix", camera.getView());
        forwardPassStep.setInput("projectionMatrix", camera.getProjection());
        forwardPassStep.setInput("lightSpaceMatrix", shadowMappingStep.getOutput("lightSpaceMatrix", Matrix4f.class));
        forwardPassStep.setInput("sunDirection", shadowMappingStep.getOutput("sunDirectionWorld", Vector3f.class));
        forwardPassStep.setInput("isDebug", debugTiles);
        forwardPassStep.render(scene);

        postProcessStep.setInput("isVignetteEnabled", vignetteEnabled);
        postProcessStep.render(scene);
    }

    private void animateLights(Camera camera, Scene scene, double deltaTime) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(scene.getLights().size() * 8);
            TempVars tempVars = TempVars.get();

            for (int i = 0; i < scene.getLights().size(); i++) {
                PointLight light = scene.getLights().get(i);

                Vector3f animatedWorldPos = tempVars.vect3d1.set(light.position());
                animatedWorldPos.x += (float) Math.sin(deltaTime * 0.7 + light.phase()) * 0.75f;
                animatedWorldPos.z += (float) Math.cos(deltaTime * 0.6 + light.phase()) * 0.75f;

                Vector4f viewPos = tempVars.vect4d1.set(animatedWorldPos.x, animatedWorldPos.y, animatedWorldPos.z, 1.0f);
                camera.getView().transform(viewPos);

                buffer.put(viewPos.x);
                buffer.put(viewPos.y);
                buffer.put(viewPos.z);
                buffer.put(light.radius());

                buffer.put(light.color().x);
                buffer.put(light.color().y);
                buffer.put(light.color().z);
                buffer.put(light.intensity());
            }

            buffer.flip();
            tempVars.release();

            glBindBuffer(GL_SHADER_STORAGE_BUFFER, lightCullingStep.getOutput("lightSsbo", Integer.class));
            glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0L, buffer);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        }
    }

    private void createPipelineSteps() {
        shadowMappingStep = new ShadowMappingStep(depthProgram);
        shadowMappingStep.init();

        depthPrepassStep = new DepthPrepassStep(mainFbo, depthProgram);
        depthPrepassStep.setInput("screenWidth", screenWidth);
        depthPrepassStep.setInput("screenHeight", screenHeight);

        lightCullingStep = new LightCullingStep(lightCullingProgram);
        lightCullingStep.setInput("tilesX", tilesX);
        lightCullingStep.setInput("tilesY", tilesY);
        lightCullingStep.setInput("tileCount", tilesX * tilesY);
        lightCullingStep.setInput("screenWidth", screenWidth);
        lightCullingStep.setInput("screenHeight", screenHeight);
        lightCullingStep.setInput("depthTexture", depthTexture);
        lightCullingStep.init();

        forwardPassStep = new ForwardPassStep(mainFbo, forwardProgram);
        forwardPassStep.setInput("screenWidth", screenWidth);
        forwardPassStep.setInput("screenHeight", screenHeight);
        forwardPassStep.setInput("tilesX", tilesX);
        forwardPassStep.setInput("tilesY", tilesY);
        forwardPassStep.setInput("lightSsbo", lightCullingStep.getOutput("lightSsbo", Integer.class));
        forwardPassStep.setInput("tileHeaderSsbo", lightCullingStep.getOutput("tileHeaderSsbo", Integer.class));
        forwardPassStep.setInput("lightIndexSsbo", lightCullingStep.getOutput("lightIndexSsbo", Integer.class));
        forwardPassStep.setInput("shadowDepthTexture", shadowMappingStep.getOutput("shadowDepthTexture", Integer.class));

        postProcessStep = new PostProcessStep(postProcessProgram);
        postProcessStep.setInput("screenWidth", screenWidth);
        postProcessStep.setInput("screenHeight", screenHeight);
        postProcessStep.setInput("colorTexture", colorTexture);
        postProcessStep.init();
    }

    private void createShaderPrograms() {
        depthProgram = new ShaderProgram(
                readResourceAsString("shaders/depth_shader/vertex.glsl"),
                readResourceAsString("shaders/depth_shader/fragment.glsl"));
        forwardProgram = new ShaderProgram(
                readResourceAsString("shaders/forward_shader/vertex.glsl"),
                readResourceAsString("shaders/forward_shader/fragment.glsl"));
        postProcessProgram = new ShaderProgram(
                readResourceAsString("shaders/post_shader/vertex.glsl"),
                readResourceAsString("shaders/post_shader/fragment.glsl"));
        lightCullingProgram = new ComputeShaderProgram(
                readResourceAsString("shaders/light_culling/compute.glsl"));
    }

    private void createFramebuffer() {
        mainFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, mainFbo);

        colorTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, colorTexture);
        glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_RGBA16F,
                screenWidth,
                screenHeight,
                0,
                GL_RGBA,
                GL_FLOAT,
                0L
        );
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTexture, 0);

        depthTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, depthTexture);
        glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_DEPTH_COMPONENT32F,
                screenWidth,
                screenHeight,
                0,
                GL_DEPTH_COMPONENT,
                GL_FLOAT,
                0L
        );
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_NONE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTexture, 0);

        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glReadBuffer(GL_COLOR_ATTACHMENT0);

        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Framebuffer incomplete. Status = 0x" + Integer.toHexString(status));
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void destroy() {
        depthProgram.destroy();
        forwardProgram.destroy();
        postProcessProgram.destroy();
        lightCullingProgram.destroy();

        postProcessStep.destroy();
        lightCullingStep.destroy();
        glDeleteTextures(colorTexture);
        glDeleteTextures(depthTexture);
        glDeleteFramebuffers(mainFbo);
        shadowMappingStep.destroy();
    }

    public int getTilesX() {
        return tilesX;
    }

    public int getTilesY() {
        return tilesY;
    }
}
