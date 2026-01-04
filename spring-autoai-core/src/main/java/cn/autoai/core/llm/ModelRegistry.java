package cn.autoai.core.llm;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model registry, manages available model instances.
 */
public class ModelRegistry {
    private final Map<String, AutoAiModel> models = new ConcurrentHashMap<>();

    public void register(AutoAiModel model) {
        models.put(model.getName(), model);
    }

    public Optional<AutoAiModel> get(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(models.get(name));
    }

    public Collection<AutoAiModel> list() {
        return models.values();
    }
}
