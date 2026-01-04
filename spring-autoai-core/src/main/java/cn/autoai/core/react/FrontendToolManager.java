package cn.autoai.core.react;

import cn.autoai.core.i18n.I18nService;
import cn.autoai.core.protocol.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Frontend tool call manager, managing the lifecycle of frontend tool calls.
 * Responsible for waiting for the frontend to return tool execution results.
 */
public class FrontendToolManager {
    private static final Logger logger = LoggerFactory.getLogger(FrontendToolManager.class);
    private static final long TOOL_CALL_TIMEOUT_SECONDS = 30;

    private final ObjectMapper objectMapper;
    private final I18nService i18nService;

    // Store pending frontend tool calls for each session
    // key: sessionId:callId, value: CompletableFuture<String>
    private final Map<String, CompletableFuture<String>> pendingToolCalls = new ConcurrentHashMap<>();

    public FrontendToolManager(ObjectMapper objectMapper, I18nService i18nService) {
        this.objectMapper = objectMapper;
        this.i18nService = i18nService;
    }

    /**
     * Register tool call, return Future for waiting for result
     *
     * @param sessionId Session ID
     * @param toolCall  Tool call information
     * @return Tool call ID
     */
    public String registerToolCall(String sessionId, ToolCall toolCall) {
        String callId = UUID.randomUUID().toString();
        String key = sessionId + ":" + callId;
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingToolCalls.put(key, future);
        logger.debug("Registering frontend tool call: sessionId={}, callId={}, tool={}", sessionId, callId, toolCall.getFunction().getName());
        return callId;
    }

    /**
     * Wait for tool call result
     *
     * @param sessionId Session ID
     * @param callId    Tool call ID
     * @return Tool execution result
     * @throws Exception If waiting times out or is interrupted
     */
    public String waitForResult(String sessionId, String callId) throws Exception {
        String key = sessionId + ":" + callId;
        CompletableFuture<String> future = pendingToolCalls.get(key);

        if (future == null) {
            logger.warn("No pending tool call found: sessionId={}, callId={}", sessionId, callId);
            return "❌ " + i18nService.get("react.tool_call_failed") + ": No pending tool call found";
        }

        try {
            // Wait for frontend to return result, set timeout
            String result = future.get(TOOL_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            logger.debug("Tool call completed: sessionId={}, callId={}", sessionId, callId);
            return result;
        } catch (java.util.concurrent.TimeoutException e) {
            pendingToolCalls.remove(key);
            logger.error("Tool call timeout: sessionId={}, callId={}", sessionId, callId);
            return "❌ Tool call timeout: Frontend did not return result within " + TOOL_CALL_TIMEOUT_SECONDS + " seconds";
        } catch (Exception e) {
            pendingToolCalls.remove(key);
            logger.error("Tool call exception: sessionId={}, callId={}, error={}", sessionId, callId, e.getMessage());
            throw e;
        }
    }

    /**
     * Complete tool call, set result
     *
     * @param sessionId Session ID
     * @param callId    Tool call ID
     * @param result    Tool execution result
     * @param error     Error message
     * @param isError   Whether it is an error
     * @return Whether completed successfully
     */
    public boolean completeToolCall(String sessionId, String callId, Object result, String error, boolean isError) {
        String key = sessionId + ":" + callId;
        CompletableFuture<String> future = pendingToolCalls.remove(key);

        if (future == null) {
            logger.warn("Received result for unknown tool call: sessionId={}, callId={}", sessionId, callId);
            return false;
        }

        try {
            String resultStr;
            if (isError) {
                resultStr = "❌ " + i18nService.get("react.tool_call_failed") + ": " + (error != null ? error : "Unknown error");
            } else {
                if (result == null) {
                    resultStr = "✅ " + i18nService.get("react.tool_call_success") + ": null";
                } else if (result instanceof String) {
                    resultStr = "✅ " + i18nService.get("react.tool_call_success") + ": " + result;
                } else {
                    resultStr = "✅ " + i18nService.get("react.tool_call_success") + ": " + objectMapper.writeValueAsString(result);
                }
            }

            future.complete(resultStr);
            logger.debug("Tool call result set: sessionId={}, callId={}, success={}", sessionId, callId, !isError);
            return true;
        } catch (Exception e) {
            logger.error("Failed to set tool call result: sessionId={}, callId={}, error={}", sessionId, callId, e.getMessage());
            future.completeExceptionally(e);
            return false;
        }
    }

    /**
     * Clean up all pending tool calls for a session
     *
     * @param sessionId Session ID
     */
    public void cleanupSession(String sessionId) {
        pendingToolCalls.keySet().removeIf(key -> key.startsWith(sessionId + ":"));
        logger.debug("Cleaning up pending tool calls for session: sessionId={}", sessionId);
    }

    /**
     * Get the number of currently pending tool calls
     *
     * @return Pending count
     */
    public int getPendingCount() {
        return pendingToolCalls.size();
    }

    /**
     * Get the number of pending tool calls for a specific session
     *
     * @param sessionId Session ID
     * @return Pending count
     */
    public int getPendingCount(String sessionId) {
        String prefix = sessionId + ":";
        return (int) pendingToolCalls.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .count();
    }
}
