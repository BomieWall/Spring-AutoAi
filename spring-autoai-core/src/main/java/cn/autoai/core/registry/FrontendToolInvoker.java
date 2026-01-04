package cn.autoai.core.registry;

import cn.autoai.core.llm.TypedStreamCallback;
import cn.autoai.core.protocol.ToolCall;
import cn.autoai.core.react.ContentType;
import cn.autoai.core.react.FrontendToolManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Frontend tool invoker, responsible for executing frontend tool calls.
 *
 * <p>Frontend tools are tools dynamically registered through the browser and need to be executed by asynchronously notifying the frontend via SSE.
 * Unlike {@link ToolInvoker} and {@link RestToolInvoker}, the execution of frontend tools is asynchronous:
 * <ul>
 *   <li>1. Register tool call and get callId</li>
 *   <li>2. Send tool call request to frontend via SSE</li>
 *   <li>3. Wait for frontend to return result (blocking wait, up to 30 seconds)</li>
 * </ul>
 *
 * <p>Reference the design patterns of {@link ToolInvoker} and {@link RestToolInvoker}.
 *
 * @see FrontendToolManager
 */
public class FrontendToolInvoker {
    private static final Logger logger = LoggerFactory.getLogger(FrontendToolInvoker.class);

    private final FrontendToolManager frontendToolManager;
    private final ObjectMapper objectMapper;

    public FrontendToolInvoker(FrontendToolManager frontendToolManager, ObjectMapper objectMapper) {
        this.frontendToolManager = frontendToolManager;
        this.objectMapper = objectMapper;
    }

    /**
     * Execute frontend tool call
     *
     * @param toolCall       Tool call object
     * @param sessionId      Session ID
     * @param typedCallback  Stream callback (used for sending SSE notifications)
     * @return Tool execution result
     * @throws InterruptedException If waiting is interrupted
     */
    public String invoke(ToolCall toolCall, String sessionId, TypedStreamCallback typedCallback)
            throws InterruptedException {
        try {
            logger.debug("Starting to execute frontend tool: tool={}, sessionId={}",
                    toolCall.getFunction().getName(), sessionId);

            // 1. Register tool call and get callId
            String callId = frontendToolManager.registerToolCall(sessionId, toolCall);
            logger.debug("Frontend tool call registered: callId={}", callId);

            // 2. Send tool call request to frontend via SSE
            if (typedCallback != null) {
                sendToolCallNotification(toolCall, callId, typedCallback);
                logger.debug("Frontend tool call notification sent: callId={}", callId);
            } else {
                logger.warn("typedCallback is null, unable to send frontend tool call notification");
            }

            // 3. Wait for frontend to return result (blocking wait, up to 30 seconds)
            String result = frontendToolManager.waitForResult(sessionId, callId);
            logger.debug("Frontend tool call completed: callId={}, resultLength={}",
                    callId, result != null ? result.length() : 0);

            return result;

        } catch (InterruptedException e) {
            logger.error("Frontend tool call interrupted: tool={}", toolCall.getFunction().getName());
            throw e;
        } catch (Exception e) {
            logger.error("Frontend tool call failed: tool={}, error={}",
                    toolCall.getFunction().getName(), e.getMessage(), e);
            throw new IllegalStateException("Frontend tool call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Send frontend tool call notification via SSE
     *
     * <p>Notification format: [Frontend Tool Call] {"type":"FRONTEND_TOOL_CALL","callId":"xxx","toolCall":{...}}
     *
     * @param toolCall       Tool call object
     * @param callId         Tool call ID
     * @param typedCallback  Stream callback
     */
    private void sendToolCallNotification(ToolCall toolCall, String callId, TypedStreamCallback typedCallback) {
        try {
            // Build notification message
            Map<String, Object> notification = new LinkedHashMap<>();
            notification.put("type", "FRONTEND_TOOL_CALL");
            notification.put("callId", callId);
            notification.put("toolCall", toolCall);

            // Convert to JSON string
            String jsonStr = objectMapper.writeValueAsString(notification);

            // Send via callback (using ACTION type so frontend can see it)
            // Format: [Frontend Tool Call] {json}
            typedCallback.onTypedChunk(ContentType.ACTION, "[FRONTEND_TOOL_CALL] " + jsonStr);

            logger.debug("Frontend tool call notification sent: callId={}, toolName={}",
                    callId, toolCall.getFunction().getName());

        } catch (Exception e) {
            // Send failure does not affect main flow
            logger.error("Failed to send frontend tool call notification: tool={}, error={}",
                    toolCall.getFunction().getName(), e.getMessage());
        }
    }

    /**
     * Clean up all pending tool calls for a session
     *
     * @param sessionId Session ID
     */
    public void cleanupSession(String sessionId) {
        frontendToolManager.cleanupSession(sessionId);
    }

    /**
     * Get the number of currently pending tool calls
     *
     * @return Pending count
     */
    public int getPendingCount() {
        return frontendToolManager.getPendingCount();
    }

    /**
     * Get the number of pending tool calls for a specific session
     *
     * @param sessionId Session ID
     * @return Pending count
     */
    public int getPendingCount(String sessionId) {
        return frontendToolManager.getPendingCount(sessionId);
    }
}
