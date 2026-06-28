package pl.pateman.forwardplus.shader;

import static org.lwjgl.opengl.GL11C.GL_FALSE;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL43C.GL_COMPUTE_SHADER;

public final class ComputeShaderProgram extends AbstractShaderProgram {

    public ComputeShaderProgram(String computeSource) {
        int computeShader = compileShader(GL_COMPUTE_SHADER, computeSource);

        program = glCreateProgram();
        glAttachShader(program, computeShader);
        glLinkProgram(program);

        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            throw new IllegalStateException("Compute program link failed:\n" + log);
        }

        glDetachShader(program, computeShader);
        glDeleteShader(computeShader);
    }

}
