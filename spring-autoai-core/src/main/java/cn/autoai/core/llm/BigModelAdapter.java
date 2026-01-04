package cn.autoai.core.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;

/**
 * BigModel (Zhipu) model adapter.
 */
@Component
public class BigModelAdapter implements ModelAdapter {
    @Override
    public String getAdapterType() {
        return "bigmodel";
    }

    @Override
    public AutoAiModel createModel(ModelProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        if (properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()) {
            return new BigModelModel(properties.getModel(), properties.getBaseUrl(),
                properties.getApiKey(), objectMapper, httpClient);
        }
        return new BigModelModel(properties.getModel(), properties.getApiKey(), objectMapper);
    }
}
