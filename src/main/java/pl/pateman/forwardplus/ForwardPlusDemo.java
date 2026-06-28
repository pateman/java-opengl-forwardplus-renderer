package pl.pateman.forwardplus;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import pl.pateman.forwardplus.pipeline.RenderPipeline;
import pl.pateman.forwardplus.scene.Scene;
import pl.pateman.forwardplus.scene.entity.camera.Camera;
import pl.pateman.forwardplus.scene.entity.light.PointLight;
import pl.pateman.forwardplus.scene.entity.mesh.Mesh;
import pl.pateman.forwardplus.scene.entity.mesh.MeshInstance;

import java.util.Locale;
import java.util.Random;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43C.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class ForwardPlusDemo {

    private static final int SCREEN_WIDTH = 1920;
    private static final int SCREEN_HEIGHT = 1080;
    private static final int NUM_LIGHTS = 512;
    private static final String WINDOW_TITLE_BASE =
            "Forward+ Demo + Shadows + Vignette - Java / LWJGL / OpenGL 4.3";

    private final Scene scene = new Scene();
    private final Camera camera = new Camera(SCREEN_WIDTH, SCREEN_HEIGHT);
    private final RenderPipeline renderPipeline = new RenderPipeline(SCREEN_WIDTH, SCREEN_HEIGHT);

    private long window;

    private Mesh cubeMesh;

    private boolean debugTiles = false;
    private boolean vignetteEnabled = true;

    private int fpsFrameCounter = 0;
    private double fpsLastUpdateTime = 0.0;
    private double currentFps = 0.0;
    private double currentFrameMs = 0.0;

    public static void main(String[] args) {
        try {
            new ForwardPlusDemo().run();
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    private void run() {
        initWindow();
        initPipeline();
        initScene();

        fpsLastUpdateTime = glfwGetTime();
        updateWindowTitle();

        while (!glfwWindowShouldClose(window)) {
            double time = glfwGetTime();

            camera.update(time);
            renderPipeline.render(camera, scene, time, vignetteEnabled, debugTiles);

            glfwSwapBuffers(window);
            updateFpsCounter(glfwGetTime());

            glfwPollEvents();
        }

        cleanup();
    }

    private void updateFpsCounter(double now) {
        fpsFrameCounter++;

        double elapsed = now - fpsLastUpdateTime;
        if (elapsed >= 0.5) {
            currentFps = fpsFrameCounter / elapsed;
            currentFrameMs = 1000.0 / Math.max(currentFps, 0.0001);

            fpsFrameCounter = 0;
            fpsLastUpdateTime = now;

            updateWindowTitle();
        }
    }

    private void updateWindowTitle() {
        String title = String.format(
                Locale.US,
                "%s | FPS: %.1f | %.2f ms | Lights: %d | Tiles: %dx%d | Debug: %s | Vignette: %s",
                WINDOW_TITLE_BASE,
                currentFps,
                currentFrameMs,
                scene.getLights().size(),
                renderPipeline.getTilesX(),
                renderPipeline.getTilesY(),
                debugTiles ? "ON" : "OFF",
                vignetteEnabled ? "ON" : "OFF"
        );

        glfwSetWindowTitle(window, title);
    }

    private void initWindow() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Failed to initialize GLFW.");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        // Required on macOS for core profile contexts.
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        window = glfwCreateWindow(SCREEN_WIDTH, SCREEN_HEIGHT, WINDOW_TITLE_BASE, NULL, NULL);
        if (window == NULL) {
            throw new IllegalStateException("Failed to create GLFW window.");
        }

        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS) {
                if (key == GLFW_KEY_ESCAPE) {
                    glfwSetWindowShouldClose(w, true);
                }
                if (key == GLFW_KEY_F) {
                    debugTiles = !debugTiles;
                    updateWindowTitle();
                }
                if (key == GLFW_KEY_V) {
                    vignetteEnabled = !vignetteEnabled;
                    updateWindowTitle();
                }
            }
        });

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);

        glfwShowWindow(window);

        GLCapabilities caps = GL.createCapabilities();
        if (!caps.OpenGL43) {
            throw new IllegalStateException("This demo requires OpenGL 4.3 or newer because it uses compute shaders.");
        }

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
    }

    private void initPipeline() {
        renderPipeline.init();
        cubeMesh = Mesh.createCube();
    }

    private void initScene() {
        // Large floor.
        scene.getMeshInstances().add(new MeshInstance(
                cubeMesh,
                new Matrix4f().translation(0.0f, -1.1f, 0.0f).scale(24.0f, 0.2f, 24.0f),
                new Vector3f(0.45f, 0.45f, 0.45f)
        ));

        // Grid of cubes.
        for (int z = -4; z <= 4; z += 2) {
            for (int x = -4; x <= 4; x += 2) {
                float h = 0.8f + ((x + z + 20) % 5) * 0.35f;
                Matrix4f model = new Matrix4f()
                        .translation(x * 1.5f, -1.0f + h * 0.5f, z * 1.5f)
                        .scale(0.7f, h, 0.7f);

                Vector3f albedo = new Vector3f(
                        0.35f + (x + 4) * 0.04f,
                        0.35f + (z + 4) * 0.04f,
                        0.55f
                );

                scene.getMeshInstances().add(new MeshInstance(cubeMesh, model, albedo));
            }
        }

        // Random lights.
        Random rnd = new Random();
        for (int i = 0; i < NUM_LIGHTS; i++) {
            float x = -11.0f + rnd.nextFloat() * 22.0f;
            float y = 0.0f + rnd.nextFloat() * 6.0f;
            float z = -11.0f + rnd.nextFloat() * 22.0f;

            float radius = 2.5f + rnd.nextFloat() * 5.0f;
            float intensity = 1.0f + rnd.nextFloat() * 2.0f;

            Vector3f color = new Vector3f(
                    0.25f + rnd.nextFloat() * 0.25f,
                    0.35f + rnd.nextFloat() * 0.25f,
                    0.55f + rnd.nextFloat() * 0.25f
            );

            scene.getLights().add(new PointLight(
                    new Vector3f(x, y, z),
                    color,
                    radius,
                    intensity,
                    rnd.nextFloat() * 1000.0f
            ));
        }
    }

    private void cleanup() {
        renderPipeline.destroy();

        if (cubeMesh != null) {
            cubeMesh.destroy();
        }

        glfwDestroyWindow(window);
        glfwTerminate();

        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
    }

}
