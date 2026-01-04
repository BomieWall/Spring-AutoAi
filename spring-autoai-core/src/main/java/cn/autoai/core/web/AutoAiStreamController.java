package cn.autoai.core.web;

import cn.autoai.core.i18n.I18nService;
import cn.autoai.core.llm.TypedStreamCallback;
import cn.autoai.core.protocol.ChatCompletionRequest;
import cn.autoai.core.protocol.ChatCompletionResponse;
import cn.autoai.core.react.ChatTaskManager;
import cn.autoai.core.react.ContentType;
import cn.autoai.core.react.FrontendToolManager;
import cn.autoai.core.react.ReActEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * AutoAi stream chat controller, supports OpenAI compatible streaming output.
 */
@RestController
@RequestMapping("${autoai.web.base-path:/auto-ai}/v1")
public class AutoAiStreamController {

    private final ReActEngine reActEngine;
    private final ObjectMapper objectMapper;
    private final ChatTaskManager taskManager;
    private final FrontendToolManager frontendToolManager;
    private final I18nService i18nService;

    public AutoAiStreamController(ReActEngine reActEngine, ObjectMapper objectMapper, ChatTaskManager taskManager, FrontendToolManager frontendToolManager, I18nService i18nService) {
        this.reActEngine = reActEngine;
        this.objectMapper = objectMapper;
        this.taskManager = taskManager;
        this.frontendToolManager = frontendToolManager;
        this.i18nService = i18nService;
    }

    /**
     * Stream chat interface, using OpenAI compatible streaming output format.
     * Extract request context (Cookie and Header) and pass to ReActEngine, used for authentication during REST API tool calls.
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @RequestBody ChatCompletionRequest request,
            HttpServletRequest httpRequest
    ) {
        SseEmitter emitter = new SseEmitter(600000L); // 10 minute timeout, adapting to long model output time

        // Use array container to share task reference between multiple lambdas
        final ChatTaskManager.ChatTask[] taskHolder = new ChatTaskManager.ChatTask[1];

        // Add timeout callback - send error message to frontend when request times out
        emitter.onTimeout(() -> {
            try {
                if (taskHolder[0] != null) {
                    taskHolder[0].markConnectionClosed();
                }
                String errorData = createErrorChunk(i18nService.get("stream.request_timeout"));
                emitter.send(SseEmitter.event().data(errorData));
                emitter.complete();
            } catch (IOException e) {
                // Ignore send error, complete directly
                emitter.completeWithError(e);
            }
        });

        // Add completion callback - triggered when connection completes normally or disconnects
        emitter.onCompletion(() -> {
            if (taskHolder[0] != null) {
                taskHolder[0].markConnectionClosed();
            }
        });

        // Add error callback - triggered when connection error occurs
        emitter.onError((ex) -> {
            if (taskHolder[0] != null) {
                taskHolder[0].markConnectionClosed();
            }
            try {
                String errorData = createErrorChunk(i18nService.get("stream.connection_error", ex.getMessage()));
                emitter.send(SseEmitter.event().data(errorData));
            } catch (IOException e) {
                // Ignore
            }
        });

        // Asynchronously process chat request
        CompletableFuture.runAsync(() -> {
            ChatTaskManager.ChatTask task = null;
            try {
                // Extract request context
                RequestContext context = RequestContext.from(httpRequest);

                // Create task and get taskId
                String sessionId = request.getSessionId();
                task = taskManager.createTask(sessionId);
                taskHolder[0] = task; // Save to holder for callback access

                // Send taskId to frontend
                Map<String, String> taskInfo = new HashMap<>();
                taskInfo.put("taskId", task.getTaskId());
                taskInfo.put("type", "TASK_INFO");
                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(taskInfo)));

                // Create stream callback
                OpenAIStreamCallback callback = new OpenAIStreamCallback(emitter, objectMapper, request.getModel(), task, i18nService);

                // Set to stream mode
                request.setStream(true);

                // Execute chat
                ChatCompletionResponse response = reActEngine.chat(request, callback, context, task);

                // Send final completion message
                callback.sendFinalMessage();

                // Complete streaming transfer
                emitter.complete();

            } catch (Exception e) {
                try {
                    String errorData = createErrorChunk(e.getMessage());
                    if (errorData != null) {
                        emitter.send(SseEmitter.event().data(errorData));
                    }
                    emitter.complete();
                } catch (IOException ioException) {
                    // Ignore send error
                    emitter.completeWithError(e);
                }
            } finally {
                // Cleanup task
                if (task != null) {
                    taskManager.removeTask(task.getTaskId());
                }
            }
        });

        return emitter;
    }

    /**
     * Abort ongoing inference task
     */
    @DeleteMapping("/chat/stream/{taskId}")
    public ResponseEntity<Map<String, Object>> abortChat(@PathVariable String taskId) {
        boolean aborted = taskManager.abortTask(taskId);

        Map<String, Object> response = new HashMap<>();
        if (aborted) {
            response.put("success", true);
            response.put("message", i18nService.get("task.abort_success"));
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", i18nService.get("task.abort_not_found"));
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Receive frontend tool execution result
     */
    @PostMapping("/chat/tool-result")
    public ResponseEntity<Map<String, Object>> receiveToolResult(@RequestBody Map<String, Object> payload) {
        String sessionId = (String) payload.get("sessionId");
        @SuppressWarnings("unchecked")
        Map<String, Object> toolCallData = (Map<String, Object>) payload.get("toolCall");

        if (sessionId == null || toolCallData == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", i18nService.get("task.missing_parameters"));
            return ResponseEntity.badRequest().body(errorResponse);
        }

        String toolName = (String) toolCallData.get("toolName");
        String callId = (String) toolCallData.get("callId");
        Object result = toolCallData.get("result");
        String error = (String) toolCallData.get("error");
        Boolean isErrorObj = (Boolean) toolCallData.get("isError");
        boolean isError = isErrorObj != null ? isErrorObj : false;

        // Complete tool call and set result
        boolean success = frontendToolManager.completeToolCall(sessionId, callId, result, error, isError);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        if (success) {
            response.put("message", i18nService.get("task.tool_result_received"));
        } else {
            response.put("message", i18nService.get("task.no_matching_tool"));
        }
        return ResponseEntity.ok(response);
    }

    private String createErrorChunk(String error) {
        try {
            SimpleStreamMessage message = new SimpleStreamMessage();
            message.content = "Error: " + error;
            message.type = ContentType.ERROR.name();

            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            return "{\"content\":\"" + error + "\",\"type\":\"ERROR\"}";
        }
    }
    
    /**
     * OpenAI compatible stream callback handler, supports content type identification - using simplified message format
     */
    private static class OpenAIStreamCallback implements TypedStreamCallback {
        private final SseEmitter emitter;
        private final ObjectMapper objectMapper;
        private final ChatTaskManager.ChatTask task;
        private final I18nService i18nService;

        // Regular expression for filtering internal tags
        private static final Pattern INTERNAL_TAG_PATTERN = Pattern.compile(
            "</?(?:think|reasoning|arg_value)>|♫:|🎵:|♪:|♬:|♪", Pattern.CASE_INSENSITIVE
        );

        public OpenAIStreamCallback(SseEmitter emitter, ObjectMapper objectMapper, String model, ChatTaskManager.ChatTask task, I18nService i18nService) {
            this.emitter = emitter;
            this.objectMapper = objectMapper;
            this.task = task;
            this.i18nService = i18nService;
        }

        /**
         * Check if task is terminated
         */
        private void checkAborted() throws IOException {
            if (task != null && task.isAborted()) {
                // Send termination message
                SimpleStreamMessage message = new SimpleStreamMessage();
                message.content = i18nService.get("stream.task_aborted");
                message.type = ContentType.ERROR.name();
                String jsonData = objectMapper.writeValueAsString(message);
                emitter.send(SseEmitter.event().data(jsonData));

                // Complete stream
                emitter.complete();
                throw new RuntimeException("Task aborted by user");
            }
        }
        
        @Override
        public void onTypedChunk(ContentType contentType, String content) {
            if (content == null || content.isEmpty()) {
                return;
            }

            try {
                // Check if task is terminated
                checkAborted();

                // Filter internal tags and process content
                String processedContent = processContent(content);
                if (processedContent.isEmpty()) {
                    return;
                }
                
                // Create simplified message format
                SimpleStreamMessage message = new SimpleStreamMessage();
                message.content = processedContent;
                message.type = contentType.name(); // Use enum name

                // Send message
                String jsonData = objectMapper.writeValueAsString(message);
                emitter.send(SseEmitter.event().data(jsonData));
                
            } catch (Exception e) {
                // Send error message but do not interrupt stream
                try {
                    SimpleStreamMessage errorMessage = new SimpleStreamMessage();
                    errorMessage.content = i18nService.get("stream.content_process_error");
                    errorMessage.type = ContentType.ERROR.name();

                    String jsonData = objectMapper.writeValueAsString(errorMessage);
                    emitter.send(SseEmitter.event().data(jsonData));
                } catch (Exception ignored) {
                    // Ignore secondary error
                }
            }
        }

        @Override
        public void onTypeMarker(ContentType contentType) {
            // try {
            //     // Send type switch marker
            //     SimpleStreamMessage message = new SimpleStreamMessage();
            //     message.content = "";
            //     message.type = contentType.name();

            //     String jsonData = objectMapper.writeValueAsString(message);
            //     emitter.send(SseEmitter.event().data(jsonData));
            // } catch (Exception e) {
            //     // Ignore identifier send error
            // }
        }
        
        @Override
        public void onChunk(String content) {
            // Default handling as content type
            onTypedChunk(ContentType.CONTENT, content);
        }
        
        public void sendFinalMessage() throws IOException {
            // Send end marker
            emitter.send(SseEmitter.event().data("[DONE]"));
        }

        /**
         * Process content: filter tags, preserve line breaks and spaces
         */
        private String processContent(String content) {
            if (content == null) {
                return "";
            }

            // Remove internal tags - extended filtering rules
            String filtered = INTERNAL_TAG_PATTERN.matcher(content).replaceAll("");

            // Remove other possible internal tags and symbols (only remove the marker itself, do not remove surrounding content)
            filtered = filtered.replaceAll("</think>", "")
                              .replaceAll("<think>", "")
                              .replaceAll("♫:", "")
                              .replaceAll("🎵:", "")
                              .replaceAll("♪:", "")
                              .replaceAll("♬:", "");

            // Only remove whitespace characters at the beginning and end (newline + space), preserve format in the middle
            // return filtered.replaceAll("^[\\n\\r\\s]+|[\\n\\r\\s]+$", "");
            return filtered;
        }
        // }

    }

    /**
     * Simplified stream message format
     */
    public static class SimpleStreamMessage {
        public String content;
        public String type;
    }
}