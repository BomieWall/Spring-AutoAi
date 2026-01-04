package cn.autoai.core.llm;

import java.net.http.HttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Model adapter interface, used to decouple model creation logic.
 * To add a new model type, simply implement this interface and register it as a Spring Bean, no need to modify factory code.
 */
public interface ModelAdapter {
    /**
     * Get adapter type identifier (lowercase).
     * Example: bigmodel, minimax, openai
     */
    String getAdapterType();

    /**
     * Create model instance based on configuration.
     */
    AutoAiModel createModel(ModelProperties properties, ObjectMapper objectMapper, HttpClient httpClient);
}
