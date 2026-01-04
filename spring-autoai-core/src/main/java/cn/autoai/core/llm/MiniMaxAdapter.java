package cn.autoai.core.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;

/**
 * MiniMax model adapter.
 */
@Component
public class MiniMaxAdapter implements ModelAdapter {
    @Override
    public String getAdapterType() {
        return "minimax";
    }

    @Override
    public AutoAiModel createModel(ModelProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        if (properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()) {
            return new MiniMaxModel(properties.getModel(), properties.getBaseUrl(),
                properties.getApiKey(), objectMapper, httpClient);
        }
        return new MiniMaxModel(properties.getModel(), properties.getApiKey(), objectMapper);
    }
}
