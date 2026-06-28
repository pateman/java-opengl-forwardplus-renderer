package pl.pateman.forwardplus.shader;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11C.GL_FALSE;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL43C.GL_COMPUTE_SHADER;


abstract class AbstractShaderProgram {

    protected int program;

    protected int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);

        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String kind = switch (type) {
                case GL_VERTEX_SHADER -> "vertex";
                case GL_FRAGMENT_SHADER -> "fragment";
                case GL_COMPUTE_SHADER -> "compute";
                default -> "unknown";
            };
            String log = glGetShaderInfoLog(shader);
            throw new IllegalStateException("Failed to compile " + kind + " shader:\n" + log + "\nSource:\n" + source);
        }

        return shader;
    }

    public void use() {
        glUseProgram(program);
    }

    public void destroy() {
        glDeleteProgram(program);
    }

    public void setInt(String name, int value) {
        int location = glGetUniformLocation(program, name);
        if (location >= 0) {
            glUniform1i(location, value);
        }
    }

    public void setVec3(String name, Vector3f value) {
        int location = glGetUniformLocation(program, name);
        if (location >= 0) {
            glUniform3f(location, value.x, value.y, value.z);
        }
    }

    public void setMat4(String name, Matrix4f value) {
        int location = glGetUniformLocation(program, name);
        if (location >= 0) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer buffer = stack.mallocFloat(16);
                value.get(buffer);
                glUniformMatrix4fv(location, false, buffer);
            }
        }
    }

    public void setMat3(String name, Matrix3f value) {
        int location = glGetUniformLocation(program, name);
        if (location >= 0) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer buffer = stack.mallocFloat(9);
                value.get(buffer);
                glUniformMatrix3fv(location, false, buffer);
            }
        }
    }

}
