package cn.autoai.core.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Model configuration properties, reads model definitions from configuration files.
 */
@ConfigurationProperties(prefix = "autoai.model")
public class ModelProperties {
    /**
     * Model adapter type: BigModel, MiniMax, OpenAI
     */
    private String adapter;

    /**
     * Model name, for example: GLM-4.7, gpt-4, etc.
     */
    private String model;

    /**
     * API key
     */
    private String apiKey;

    /**
     * API base URL (optional)
     */
    private String baseUrl;

    public String getAdapter() {
        return adapter;
    }

    public void setAdapter(String adapter) {
        this.adapter = adapter;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isValid() {
        return adapter != null && !adapter.isBlank()
            && model != null && !model.isBlank()
            && apiKey != null && !apiKey.isBlank();
    }
}
