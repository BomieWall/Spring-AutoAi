package cn.autoai.core.llm;

import cn.autoai.core.protocol.ChatCompletionChoice;
import cn.autoai.core.protocol.ChatCompletionRequest;
import cn.autoai.core.protocol.ChatCompletionResponse;
import cn.autoai.core.protocol.ChatMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * BigModel (Zhipu) adapter, supports streaming and non-streaming output.
 */
public class BigModelModel implements AutoAiModel {
    // private static final String DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/coding/paas/v4";
     private static final String DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/paas/v4";
    // private static final String DEFAULT_BASE_URL = "https://api.minimaxi.com/v1";
    
    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final double DEFAULT_TOP_P = 0.8;

    private final String name;
    private final URI chatCompletionsUrl;
    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public BigModelModel(String name, String apiKey, ObjectMapper objectMapper) {
        this(name, DEFAULT_BASE_URL, apiKey, objectMapper,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build());
    }

    public BigModelModel(String name, String baseUrl, String apiKey, ObjectMapper objectMapper, HttpClient httpClient) {
        this.name = name;
        this.chatCompletionsUrl = URI.create(normalizeBaseUrl(baseUrl) + "/chat/completions");
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Execute non-streaming call, automatically switches to streaming if request contains stream=true.
     */
    @Override
    public ChatCompletionResponse chat(ChatCompletionRequest request) {
        if (request != null && Boolean.TRUE.equals(request.getStream())) {
            return chatStream(request, null);
        }
        try {
            String body = buildPayload(request, false);
            HttpRequest httpRequest = buildRequest(body);
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("BigModel API error: " + response.statusCode() + " " + response.body());
            }
            return objectMapper.readValue(response.body(), ChatCompletionResponse.class);
        } catch (Exception ex) {
            throw new IllegalStateException("BigModel call failed", ex);
        }
    }

    /**
     * Execute streaming call and output incremental content through callback.
     */
    @Override
    public ChatCompletionResponse chatStream(ChatCompletionRequest request, StreamCallback callback) {
        try {
            String body = buildPayload(request, true);
            HttpRequest httpRequest = buildRequest(body);
            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorBody = readBody(response.body());
                throw new IllegalStateException("BigModel API error: " + response.statusCode() + " " + errorBody);
            }
            String modelName = request == null || request.getModel() == null || request.getModel().isBlank()
                ? name
                : request.getModel();
            return readStreamResponse(response.body(), modelName, callback);
        } catch (Exception ex) {
            throw new IllegalStateException("BigModel stream call failed", ex);
        }
    }

    /**
     * Build BigModel request body.
     */
    private String buildPayload(ChatCompletionRequest request, boolean stream) throws Exception {
        Map<String, Object> payload = objectMapper.convertValue(request, new TypeReference<Map<String, Object>>() {
        });
        String modelName = request == null || request.getModel() == null || request.getModel().isBlank()
            ? name
            : request.getModel();
        payload.put("model", modelName);
        payload.putIfAbsent("temperature", DEFAULT_TEMPERATURE);
        payload.putIfAbsent("top_p", DEFAULT_TOP_P);
        payload.put("thinking", Map.of("type", "disabled"));
        payload.put("stream", stream);
        payload.putIfAbsent("tool_choice", "auto");
        return objectMapper.writeValueAsString(payload);
    }

    /**
     * Build HTTP request.
     */
    private HttpRequest buildRequest(String body) {
        return HttpRequest.newBuilder(chatCompletionsUrl)
            .timeout(Duration.ofSeconds(600)) // 10-minute timeout to accommodate long output from large models
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
    }

    /**
     * Parse streaming output, concatenate complete content and construct standard response.
     */
    private ChatCompletionResponse readStreamResponse(InputStream inputStream, String modelName, StreamCallback callback) {
        StringBuilder content = new StringBuilder();
        StringBuilder reasoningContent = new StringBuilder();
        String id = null;
        Long created = null;
        String finishReason = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.startsWith("[DONE]")) {
                    break;
                }
                JsonNode root = objectMapper.readTree(data);
                if (id == null && root.has("id")) {
                    id = root.get("id").asText();
                }
                if (created == null && root.has("created")) {
                    created = root.get("created").asLong();
                }
                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode choice = choices.get(0);
                    JsonNode delta = choice.path("delta");
                    JsonNode deltaContent = delta.get("content");
                    if (deltaContent != null && !deltaContent.isNull()) {
                        String chunk = deltaContent.asText();
                        content.append(chunk);
                        if (callback != null) {
                            callback.onChunk(chunk);
                        }
                    }
                    
                    // Handle reasoning content
                    JsonNode reasoning_content = delta.get("reasoning_content");
                    if (reasoning_content != null && !reasoning_content.isNull()) {
                        String chunk = reasoning_content.asText();
                        reasoningContent.append(chunk);
                        if (callback != null) {
                            // If TypedStreamCallback, send reasoning type content
                            if (callback instanceof cn.autoai.core.llm.TypedStreamCallback) {
                                ((cn.autoai.core.llm.TypedStreamCallback) callback).onTypedChunk(
                                    cn.autoai.core.react.ContentType.REASONING, chunk);
                            } else {
                                // Otherwise send as regular content
                                callback.onChunk(chunk);
                            }
                        }
                    }
                    
                    JsonNode finishNode = choice.get("finish_reason");
                    if (finishNode != null && !finishNode.isNull()) {
                        finishReason = finishNode.asText();
                    }
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("BigModel stream parse failed", ex);
        }

        if (id == null) {
            id = "chatcmpl_" + UUID.randomUUID();
        }
        if (created == null) {
            created = Instant.now().getEpochSecond();
        }
        ChatMessage message = ChatMessage.assistant(content.toString());
        ChatCompletionChoice choice = new ChatCompletionChoice(0, message, finishReason == null ? "stop" : finishReason);

        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setId(id);
        response.setObject("chat.completion");
        response.setCreated(created);
        response.setModel(modelName == null ? name : modelName);
        response.addChoice(choice);
        return response;
    }

    /**
     * Read error response content.
     */
    private String readBody(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    /**
     * Normalize base URL format, avoid duplicate slashes.
     */
    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
