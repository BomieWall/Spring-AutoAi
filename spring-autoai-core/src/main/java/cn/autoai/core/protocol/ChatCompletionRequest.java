package cn.autoai.core.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI compatible chat request.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionRequest {
    private String model;
    private List<ChatMessage> messages = new ArrayList<>();
    private List<ToolSpec> tools;

    @JsonProperty("tool_choice")
    private Object toolChoice;

    private Double temperature;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private Boolean stream;

    // Session ID, used to distinguish different conversation sessions
    private String sessionId;

    // Environment context, used to provide current environment context to AI
    @JsonProperty("environment_context")
    private List<String> environmentContext;

    // Frontend tool definition list
    @JsonProperty("frontend_tools")
    private List<ToolSpec> frontendTools;

    public ChatCompletionRequest() {
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public List<ToolSpec> getTools() {
        return tools;
    }

    public void setTools(List<ToolSpec> tools) {
        this.tools = tools;
    }

    public Object getToolChoice() {
        return toolChoice;
    }

    public void setToolChoice(Object toolChoice) {
        this.toolChoice = toolChoice;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<String> getEnvironmentContext() {
        return environmentContext;
    }

    public void setEnvironmentContext(List<String> environmentContext) {
        this.environmentContext = environmentContext;
    }

    public List<ToolSpec> getFrontendTools() {
        return frontendTools;
    }

    public void setFrontendTools(List<ToolSpec> frontendTools) {
        this.frontendTools = frontendTools;
    }
}
