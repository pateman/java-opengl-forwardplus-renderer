package pl.pateman.forwardplus.pipeline.step;

import pl.pateman.forwardplus.scene.Scene;

import java.util.HashMap;
import java.util.Map;

abstract class AbstractPipelineStep {

    private final Map<String, Object> stepInputs = new HashMap<>();
    private final Map<String, Object> stepOutputs = new HashMap<>();

    public abstract void init();

    public abstract void render(Scene scene);

    public abstract void destroy();

    public final void setInput(String name, Object value) {
        stepInputs.put(name, value);
    }

    public final <T> T getInput(String name, Class<T> cls) {
        return cls.cast(stepInputs.get(name));
    }

    protected final void setOutput(String name, Object value) {
        stepOutputs.put(name, value);
    }

    public final <T> T getOutput(String name, Class<T> cls) {
        return cls.cast(stepOutputs.get(name));
    }

}
