package pl.pateman.forwardplus.scene.entity.mesh;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL20C.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20C.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30C.*;


public record Mesh(int vao, int vbo, int ebo, int indexCount) {

    public void draw() {
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L);
        glBindVertexArray(0);
    }

    public void destroy() {
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
    }

    public static Mesh createCube() {
        // 24 unique vertices: 4 per face, so each face has a correct flat normal.
        float[] vertices = {
                // position              // normal             // uv

                // front +Z
                -1, -1, 1, 0, 0, 1, 0, 0,
                1, -1, 1, 0, 0, 1, 1, 0,
                1, 1, 1, 0, 0, 1, 1, 1,
                -1, 1, 1, 0, 0, 1, 0, 1,

                // back -Z
                1, -1, -1, 0, 0, -1, 0, 0,
                -1, -1, -1, 0, 0, -1, 1, 0,
                -1, 1, -1, 0, 0, -1, 1, 1,
                1, 1, -1, 0, 0, -1, 0, 1,

                // left -X
                -1, -1, -1, -1, 0, 0, 0, 0,
                -1, -1, 1, -1, 0, 0, 1, 0,
                -1, 1, 1, -1, 0, 0, 1, 1,
                -1, 1, -1, -1, 0, 0, 0, 1,

                // right +X
                1, -1, 1, 1, 0, 0, 0, 0,
                1, -1, -1, 1, 0, 0, 1, 0,
                1, 1, -1, 1, 0, 0, 1, 1,
                1, 1, 1, 1, 0, 0, 0, 1,

                // top +Y
                -1, 1, 1, 0, 1, 0, 0, 0,
                1, 1, 1, 0, 1, 0, 1, 0,
                1, 1, -1, 0, 1, 0, 1, 1,
                -1, 1, -1, 0, 1, 0, 0, 1,

                // bottom -Y
                -1, -1, -1, 0, -1, 0, 0, 0,
                1, -1, -1, 0, -1, 0, 1, 0,
                1, -1, 1, 0, -1, 0, 1, 1,
                -1, -1, 1, 0, -1, 0, 0, 1
        };

        int[] indices = {
                0, 1, 2, 2, 3, 0,
                4, 5, 6, 6, 7, 4,
                8, 9, 10, 10, 11, 8,
                12, 13, 14, 14, 15, 12,
                16, 17, 18, 18, 19, 16,
                20, 21, 22, 22, 23, 20
        };

        int vao = glGenVertexArrays();
        int vbo = glGenBuffers();
        int ebo = glGenBuffers();

        glBindVertexArray(vao);

        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(vertices.length);
        vertexBuffer.put(vertices).flip();

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);

        IntBuffer indexBuffer = BufferUtils.createIntBuffer(indices.length);
        indexBuffer.put(indices).flip();

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL_STATIC_DRAW);

        int stride = 8 * Float.BYTES;

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);

        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);

        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 6L * Float.BYTES);

        glBindVertexArray(0);

        return new Mesh(vao, vbo, ebo, indices.length);
    }
}
