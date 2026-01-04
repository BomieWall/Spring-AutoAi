package cn.autoai.core.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;

/**
 * OpenAI compatible model adapter.
 */
@Component
public class OpenAIAdapter implements ModelAdapter {
    @Override
    public String getAdapterType() {
        return "openai";
    }

    @Override
    public AutoAiModel createModel(ModelProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        String baseUrl = (properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank())
            ? properties.getBaseUrl()
            : "https://api.openai.com";
        return new OpenAiCompatibleModel(properties.getModel(), baseUrl,
            properties.getApiKey(), objectMapper, httpClient);
    }
}
