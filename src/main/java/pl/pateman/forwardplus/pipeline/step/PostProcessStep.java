package pl.pateman.forwardplus.pipeline.step;

import pl.pateman.forwardplus.scene.Scene;
import pl.pateman.forwardplus.shader.ShaderProgram;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL30C.*;

public class PostProcessStep extends AbstractPipelineStep {

    private final ShaderProgram postProcessProgram;

    private int fullscreenTriangleVao;

    public PostProcessStep(ShaderProgram postProcessProgram) {
        this.postProcessProgram = postProcessProgram;
    }

    @Override
    public void init() {
        // Empty VAO is enough because the fullscreen triangle is generated from gl_VertexID.
        fullscreenTriangleVao = glGenVertexArrays();
    }

    @Override
    public void render(Scene scene) {
        Integer screenWidth = getInput("screenWidth", Integer.class);
        Integer screenHeight = getInput("screenHeight", Integer.class);

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, screenWidth, screenHeight);

        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);

        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        postProcessProgram.use();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, getInput("colorTexture", Integer.class));

        postProcessProgram.setInt("uSceneColor", 0);
        postProcessProgram.setInt("uVignetteEnabled", getInput("isVignetteEnabled", Boolean.class) ? 1 : 0);
        postProcessProgram.setInt("uScreenSizeX", screenWidth);
        postProcessProgram.setInt("uScreenSizeY", screenHeight);

        glBindVertexArray(fullscreenTriangleVao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);

        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }

    @Override
    public void destroy() {
        glDeleteVertexArrays(fullscreenTriangleVao);
    }
}
