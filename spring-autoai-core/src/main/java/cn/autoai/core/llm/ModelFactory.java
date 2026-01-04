package cn.autoai.core.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Model factory, creates different model instances based on configuration.
 * Uses strategy pattern to support new model types through ModelAdapter without modifying factory code.
 */
public class ModelFactory {
    private static final Logger log = LoggerFactory.getLogger(ModelFactory.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, ModelAdapter> adapters;

    public ModelFactory(ObjectMapper objectMapper, List<ModelAdapter> adapters) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(30))
            .build();
        this.adapters = adapters.stream()
            .collect(Collectors.toMap(
                ModelAdapter::getAdapterType,
                adapter -> adapter,
                (existing, replacement) -> {
                    log.warn("Duplicate adapter type detected: {}, will use the latter one", existing.getAdapterType());
                    return replacement;
                }
            ));
        log.debug("Registered model adapters: {}", this.adapters.keySet());
    }

    /**
     * Create model instance based on configuration.
     */
    public AutoAiModel createModel(ModelProperties properties) {
        if (!properties.isValid()) {
            throw new IllegalArgumentException("Invalid model configuration, please check adapter, model and api-key settings");
        }

        String adapterType = properties.getAdapter().toLowerCase();
        ModelAdapter adapter = adapters.get(adapterType);

        if (adapter == null) {
            throw new IllegalArgumentException("Unsupported model adapter: " + properties.getAdapter()
                + ", supported types: " + adapters.keySet());
        }

        return adapter.createModel(properties, objectMapper, httpClient);
    }
}
