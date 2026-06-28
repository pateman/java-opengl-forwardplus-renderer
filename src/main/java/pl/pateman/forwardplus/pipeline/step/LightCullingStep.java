package pl.pateman.forwardplus.pipeline.step;

import org.joml.Matrix4f;
import pl.pateman.forwardplus.scene.Scene;
import pl.pateman.forwardplus.shader.ComputeShaderProgram;

import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;

public class LightCullingStep extends AbstractPipelineStep {

    private static final int MAX_LIGHTS = 1024;

    // Keep in sync with MAX_LIGHTS_PER_TILE in the light culling compute shader.
    private static final int MAX_LIGHTS_PER_TILE = 256;

    private final ComputeShaderProgram lightCullingProgram;

    private int lightSsbo;
    private int tileHeaderSsbo;
    private int lightIndexSsbo;

    public LightCullingStep(ComputeShaderProgram lightCullingProgram) {
        this.lightCullingProgram = lightCullingProgram;
    }

    @Override
    public void init() {
        Integer tileCount = getInput("tileCount", Integer.class);

        lightSsbo = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, lightSsbo);
        glBufferData(GL_SHADER_STORAGE_BUFFER, (long) MAX_LIGHTS * 8L * Float.BYTES, GL_DYNAMIC_DRAW);

        tileHeaderSsbo = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, tileHeaderSsbo);
        glBufferData(GL_SHADER_STORAGE_BUFFER, (long) tileCount * 2L * Integer.BYTES, GL_DYNAMIC_DRAW);

        lightIndexSsbo = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, lightIndexSsbo);
        glBufferData(
                GL_SHADER_STORAGE_BUFFER,
                (long) tileCount * MAX_LIGHTS_PER_TILE * Integer.BYTES,
                GL_DYNAMIC_DRAW
        );

        setOutput("lightSsbo", lightSsbo);
        setOutput("tileHeaderSsbo", tileHeaderSsbo);
        setOutput("lightIndexSsbo", lightIndexSsbo);
    }

    @Override
    public void render(Scene scene) {
        Integer tilesX = getInput("tilesX", Integer.class);
        Integer tilesY = getInput("tilesY", Integer.class);

        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        lightCullingProgram.use();

        lightCullingProgram.setInt("uScreenSizeX", getInput("screenWidth", Integer.class));
        lightCullingProgram.setInt("uScreenSizeY", getInput("screenHeight", Integer.class));
        lightCullingProgram.setInt("uTileCountX", tilesX);
        lightCullingProgram.setInt("uTileCountY", tilesY);
        lightCullingProgram.setInt("uNumLights", scene.getLights().size());
        lightCullingProgram.setMat4("uInvProjection", getInput("invProjectionMatrix", Matrix4f.class));

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, getInput("depthTexture", Integer.class));
        lightCullingProgram.setInt("uDepthTexture", 0);

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, lightSsbo);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, tileHeaderSsbo);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, lightIndexSsbo);

        glDispatchCompute(tilesX, tilesY, 1);
    }

    @Override
    public void destroy() {
        glDeleteBuffers(lightSsbo);
        glDeleteBuffers(tileHeaderSsbo);
        glDeleteBuffers(lightIndexSsbo);
    }
}
