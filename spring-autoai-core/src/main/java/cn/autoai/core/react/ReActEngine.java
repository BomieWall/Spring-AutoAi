package cn.autoai.core.react;

import cn.autoai.core.i18n.I18nService;
import cn.autoai.core.registry.FrontendToolInvoker;

import cn.autoai.core.llm.AutoAiModel;
import cn.autoai.core.llm.ModelRegistry;
import cn.autoai.core.llm.StreamCallback;
import cn.autoai.core.llm.TypedStreamCallback;
import cn.autoai.core.model.ToolDetail;
import cn.autoai.core.model.ToolParamSpec;
import cn.autoai.core.model.ToolSummary;
import cn.autoai.core.protocol.ChatCompletionChoice;
import cn.autoai.core.protocol.ChatCompletionRequest;
import cn.autoai.core.protocol.ChatCompletionResponse;
import cn.autoai.core.protocol.ChatMessage;
import cn.autoai.core.protocol.ToolCall;
import cn.autoai.core.protocol.ToolCallFunction;
import cn.autoai.core.protocol.ToolFunctionSpec;
import cn.autoai.core.protocol.ToolSpec;
import cn.autoai.core.registry.ToolDefinition;
import cn.autoai.core.registry.ToolInvoker;
import cn.autoai.core.registry.ToolParamBinding;
import cn.autoai.core.registry.ToolRegistry;
import cn.autoai.core.util.ExampleGenerator;
import cn.autoai.core.web.RequestContext;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct scheduling engine, implements thought/action/observation loop, referencing react-framework-demo.js.
 */
public class ReActEngine {
    public static final String TOOL_DETAIL_NAME = "autoai.tool_detail";

    static final String THINKING="THINK";
    static final String ANSWER="ANSWER";
    static final String ACTION="ACTION";
    static final String OBSERVATION="OBSERVE";
    static final String COMPLETION="DONE";
    static final String ASK="ASK";

    private static final List<String> COMPLETION_MARKERS = List.of(
        ANSWER+":",COMPLETION+":"
    );

    private static final List<String> INTERUPTION_MARKERS = List.of(
        ASK+":"
    );

    private static final Pattern THOUGHT_PATTERN = Pattern.compile(""+THINKING+"[:：]\\s*(.+?)(?=\\n"+ACTION+"|\\n"+ANSWER+"|\\n"+ASK+"|$)", Pattern.DOTALL);
    private static final Pattern ACTION_PATTERN = Pattern.compile(""+ACTION+"[:：]\\s*\\[?(.+?)\\]?(?=\\n"+THINKING+"|\\n"+OBSERVATION+"|\\n"+ANSWER+"|\\n"+ASK+"|$)", Pattern.DOTALL);
    private static final Pattern ASK_PATTERN = Pattern.compile(""+ASK+"[:：]\\s*(.+?)(?=\\n"+THINKING+"|\\n"+ACTION+"|\\n"+ANSWER+"|$)", Pattern.DOTALL);
    // Only match tool calls with ACTION marker prefix, such as "ACTION: ToolName(parameters)"
    private static final Pattern DIRECT_ACTION_PATTERN = Pattern.compile(""+ACTION+"[:：]\\s*([\\w.$]+\\([^)]*\\))", Pattern.DOTALL);
    private static final Pattern ACTION_CALL_PATTERN = Pattern.compile("([\\w.$\\-\\u4e00-\\u9fa5]+)\\((.*)\\)", Pattern.DOTALL);
    private static final Pattern ACTION_ALT_PATTERN = Pattern.compile("([\\w.$\\-\\u4e00-\\u9fa5]+)\\s*,\\s*(.+)", Pattern.DOTALL);

    private final ToolRegistry toolRegistry;
    private final ToolInvoker toolInvoker;
    private final ModelRegistry modelRegistry;
    private final ObjectMapper objectMapper;
    private final ReActSettings settings;
    private final FrontendToolInvoker frontendToolInvoker;
    private final I18nService i18nService;

    /**
     * Session wrapper class, containing message history and last access time
     */
    private static class SessionWrapper {
        private final List<ChatMessage> messages;
        private volatile long lastAccessTime;

        public SessionWrapper(List<ChatMessage> messages) {
            this.messages = messages;
            this.lastAccessTime = System.currentTimeMillis();
        }

        public List<ChatMessage> getMessages() {
            return messages;
        }

        public long getLastAccessTime() {
            return lastAccessTime;
        }

        public void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }

    // Session storage, used to save message history and access time for each session
    private final Map<String, SessionWrapper> sessionStore = new ConcurrentHashMap<>();

    // System prompt cache
    private volatile SystemPromptCache promptCache;

    // Scheduled executor for periodically cleaning up expired sessions
    private final ScheduledExecutorService cleanupScheduler;

    public ReActEngine(ToolRegistry toolRegistry, ToolInvoker toolInvoker, ModelRegistry modelRegistry,
                       ObjectMapper objectMapper, ReActSettings settings, FrontendToolManager frontendToolManager, I18nService i18nService) {
        this.toolRegistry = toolRegistry;
        this.toolInvoker = toolInvoker;
        this.modelRegistry = modelRegistry;
        this.objectMapper = objectMapper;
        this.settings = settings;
        this.frontendToolInvoker = new FrontendToolInvoker(frontendToolManager, objectMapper);
        this.i18nService = i18nService;

        // Start periodic cleanup task
        if (settings.isEnableSessionExpiration()) {
            this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "session-cleanup-thread");
                thread.setDaemon(true);
                return thread;
            });
            long intervalMinutes = settings.getSessionCleanupIntervalMinutes();
            this.cleanupScheduler.scheduleAtFixedRate(
                this::cleanupExpiredSessions,
                intervalMinutes, // initial delay
                intervalMinutes, // execution interval
                TimeUnit.MINUTES
            );
            System.out.println("Session expiration cleanup enabled: cleanup interval=" + intervalMinutes + " minutes, expiration time=" + settings.getSessionExpireMinutes() + " minutes");
        } else {
            this.cleanupScheduler = null;
            System.out.println("Session expiration cleanup not enabled");
        }
    }

    /**
     * Execute one ReAct conversation flow until final response is output or maximum steps are reached.
     */
    public ChatCompletionResponse chat(ChatCompletionRequest request) {
        return chat(request, null, null);
    }

    /**
     * Execute one ReAct conversation flow, supporting stream callback.
     */
    public ChatCompletionResponse chat(ChatCompletionRequest request, StreamCallback streamCallback) {
        return chat(request, streamCallback, null);
    }

    /**
     * Execute one ReAct conversation flow, supporting stream callback and request context.
     */
    public ChatCompletionResponse chat(ChatCompletionRequest request, StreamCallback streamCallback, RequestContext requestContext) {
        return chat(request, streamCallback, requestContext, null);
    }

    /**
     * Execute one ReAct conversation flow, supporting stream callback, request context, and task termination check.
     */
    public ChatCompletionResponse chat(ChatCompletionRequest request, StreamCallback streamCallback, RequestContext requestContext, ChatTaskManager.ChatTask task) {
        // Get or create session
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = "default_" + UUID.randomUUID().toString();
        }

        List<ChatMessage> session = getOrCreateSession(sessionId);

        // Add new user message to session
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            // Only add new messages (usually the last user message)
            ChatMessage lastMessage = request.getMessages().get(request.getMessages().size() - 1);
            if ("user".equals(lastMessage.getRole())) {
                session.add(lastMessage);
            }
        }

        // Check if conversation history compression is needed
        if (settings.isEnableCompression()) {
            checkAndCompressSession(session, sessionId);
        }

        List<ToolSpec> toolSpecs = buildToolSpecs(request);
        applySystemPrompt(session, toolSpecs, request.getEnvironmentContext(), request);

        AutoAiModel model = resolveModel(request.getModel());

        // Create typed stream callback wrapper
        TypedStreamCallback typedCallback = createTypedCallback(streamCallback);

        // Used to track consecutive failed same actions to avoid infinite loops
        String lastFailedAction = null;
        int sameActionFailureCount = 0;

        for (int step = 0; step < settings.getMaxSteps(); step++) {
            // Check if task is terminated
            if (task != null && task.isAborted()) {
                ChatCompletionResponse aborted = new ChatCompletionResponse();
                aborted.setId("chatcmpl_" + UUID.randomUUID());
                aborted.setObject("chat.completion");
                aborted.setCreated(Instant.now().getEpochSecond());
                aborted.setModel(model.getName());
                aborted.addChoice(new ChatCompletionChoice(0,
                    ChatMessage.assistant(i18nService.get("react.aborted")), "stop"));
                return aborted;
            }

            // Build progress hint message
            String progressHint = buildProgressHint(step, settings.getMaxSteps());

            ChatCompletionRequest modelRequest = copyRequest(request, session, toolSpecs, model.getName());
            ChatCompletionResponse response;
            if (Boolean.TRUE.equals(modelRequest.getStream())) {
                response = model.chatStream(modelRequest, createModelCallback(typedCallback));
            } else {
                response = model.chat(modelRequest);
            }
            ChatMessage assistant = firstAssistant(response);
            if (assistant == null) {
                saveSession(sessionId, session);
                return response;
            }

            session.add(assistant);

            String content = assistant.getContent();
            if (content == null) {
                // Compatible with tool_calls return format
                if (assistant.getToolCalls() != null && !assistant.getToolCalls().isEmpty()) {
                    boolean hasFailure = false;
                    StringBuilder allObservations = new StringBuilder();

                    for (ToolCall toolCall : assistant.getToolCalls()) {
                        String observation = executeToolCall(toolCall, requestContext, request, typedCallback, sessionId);
                        allObservations.append(observation).append("\n");

                        if (typedCallback != null) {
                            observationChunk(typedCallback, observation);
                        }

                        session.add(ChatMessage.user(OBSERVATION + ": " + observation));

                        // Check if failed
                        if (observation.startsWith("❌")) {
                            hasFailure = true;
                        }
                    }

                    // If all tool calls failed, add intelligent recovery hint
                    if (hasFailure) {
                        String recoveryHint = buildRecoveryHint(allObservations.toString(), step, settings.getMaxSteps());
                        session.add(ChatMessage.user(recoveryHint));
                        lastFailedAction = "tool_calls";
                        sameActionFailureCount++;
                    } else {
                        // Call successful, reset failure count
                        sameActionFailureCount = 0;
                    }
                    continue;
                }
                saveSession(sessionId, session);
                return response;
            }

            if (isFinalAnswer(content)) {
                saveSession(sessionId, session);
                return response;
            }

            // Check if user inquiry is needed, interrupt flow if so
            if (isInterruptionByAsk(content)) {
                saveSession(sessionId, session);
                return response;
            }

            ThoughtAction parsed = parseThoughtActionObservation(content);

            // Check if it is an inquiry marker
            if (parsed.getThought() != null && parsed.getThought().startsWith("ASK:")) {
                // This is an inquiry, return response directly and interrupt flow
                saveSession(sessionId, session);
                return response;
            }

            // Handle unformatted output
            if (parsed.getThought() == null && parsed.getAction() == null) {
                session.add(ChatMessage.user(progressHint + "Please output in the correct format:\n" +
                    THINKING + ": [Analyze problem]\n" + ACTION + ": [Tool call] or " + ANSWER + ": [Final answer] or " + COMPLETION + ": [Complete]"));
                continue;
            }

            // Handle case with only thinking but no action
            if (parsed.getAction() == null) {
                String hint;
                if (sameActionFailureCount >= 2) {
                    hint = "Previous attempts failed to solve the problem, please try a different method or check input parameters.";
                } else {
                    hint = progressHint;
                }
                session.add(ChatMessage.user(hint + "Please output " + ACTION + ": [Tool call] to continue, or " + ANSWER + ": [Complete] or " + COMPLETION + ": [Complete]"));
                continue;
            }

            // Execute action
            String fullObservation = executeActionWithContext(parsed.getAction(), requestContext, request, typedCallback);

            // Send observation result
            if (typedCallback != null) {
                observationChunk(typedCallback, fullObservation);
            }

            // Add observation result to session
            session.add(ChatMessage.user(OBSERVATION + ": " + fullObservation));

            // Failure detection and intelligent recovery
            if (fullObservation.startsWith("❌")) {
                String currentAction = normalizeActionKey(parsed.getAction());

                if (currentAction.equals(lastFailedAction)) {
                    sameActionFailureCount++;
                } else {
                    lastFailedAction = currentAction;
                    sameActionFailureCount = 1;
                }

                // If failed consecutively 2 or more times, provide recovery suggestion
                if (sameActionFailureCount >= 2) {
                    String recoveryHint = buildRecoveryHint(fullObservation, step, settings.getMaxSteps());
                    session.add(ChatMessage.user(recoveryHint));
                }
            } else {
                // Successfully executed, reset failure count
                sameActionFailureCount = 0;
            }
        }

        // Timeout handling
        saveSession(sessionId, session);

        ChatCompletionResponse fallback = new ChatCompletionResponse();
        fallback.setId("chatcmpl_" + UUID.randomUUID());
        fallback.setObject("chat.completion");
        fallback.setCreated(Instant.now().getEpochSecond());
        fallback.setModel(model.getName());
        fallback.addChoice(new ChatCompletionChoice(0,
            ChatMessage.assistant(i18nService.get("react.max_steps_reached", settings.getMaxSteps()) + "\n\n" +
                i18nService.get("react.max_steps_reached_hint") + "\n" +
                i18nService.get("react.max_steps_reached_hint_1") + "\n" +
                i18nService.get("react.max_steps_reached_hint_2") + "\n" +
                i18nService.get("react.max_steps_reached_hint_3")), "length"));
        return fallback;
    }

    /**
     * Build progress hint message
     */
    private String buildProgressHint(int currentStep, int maxSteps) {
        int remaining = maxSteps - currentStep;
        float progress = (float) currentStep / maxSteps;

        StringBuilder hint = new StringBuilder();
        hint.append("[Progress: ").append(currentStep).append("/").append(maxSteps).append(" steps");

        if (progress < 0.3) {
            hint.append(", Just started]");
        } else if (progress < 0.7) {
            hint.append(", In progress]");
        } else {
            hint.append(", Almost complete]");
        }

        hint.append(" ").append(remaining).append(" steps remaining");
        return hint.toString();
    }

    /**
     * Build recovery hint message
     */
    private String buildRecoveryHint(String observation, int currentStep, int maxSteps) {
        StringBuilder hint = new StringBuilder();

        // Extract key content from error message
        String errorMsg = extractErrorMessage(observation);

        hint.append("⚠️ Previous execution failed: ").append(errorMsg).append("\n\n");

        int remaining = maxSteps - currentStep;
        hint.append("Please try one of the following methods:\n");
        hint.append("1. Check if parameters are correct and retry after adjustment\n");
        hint.append("2. Use autoai.tool_detail(\"ToolName\") to view tool details\n");

        if (remaining <= 2) {
            hint.append("3. Simplify the task and handle it in steps\n");
            hint.append("4. If unable to complete, output simplified intermediate results\n");
        }

        return hint.toString();
    }

    /**
     * Extract error message from observation result
     */
    private String extractErrorMessage(String observation) {
        if (observation == null) {
            return "Unknown error";
        }

        // Remove success/failure markers
        String cleaned = observation.replaceAll("^[✅❌]\\s*", "").trim();

        // Extract main content after colon
        int colonIndex = cleaned.indexOf(':');
        if (colonIndex >= 0 && colonIndex < cleaned.length() - 1) {
            return cleaned.substring(colonIndex + 1).trim();
        }

        return cleaned.length() > 100 ? cleaned.substring(0, 100) + "..." : cleaned;
    }

    /**
     * Normalize action key for comparison
     */
    private String normalizeActionKey(String action) {
        if (action == null) {
            return "";
        }
        // Extract tool name as key
        Matcher matcher = ACTION_CALL_PATTERN.matcher(action);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return action.split("\\(")[0].trim();
    }

    /**
     * Get or create session
     */
    private List<ChatMessage> getOrCreateSession(String sessionId) {
        SessionWrapper wrapper = sessionStore.computeIfAbsent(sessionId,
            k -> new SessionWrapper(new ArrayList<>()));
        wrapper.updateAccessTime(); // Update last access time
        return wrapper.getMessages();
    }

    /**
     * Save session (update access time)
     */
    private void saveSession(String sessionId, List<ChatMessage> messages) {
        SessionWrapper wrapper = new SessionWrapper(new ArrayList<>(messages));
        sessionStore.put(sessionId, wrapper);
    }

    /**
     * Clear session
     */
    public void clearSession(String sessionId) {
        if (sessionId != null) {
            sessionStore.remove(sessionId);
        }
    }

    /**
     * Clear all sessions
     */
    public void clearAllSessions() {
        sessionStore.clear();
    }

    /**
     * Clean up expired sessions
     */
    public void cleanupExpiredSessions() {
        if (!settings.isEnableSessionExpiration()) {
            return;
        }

        long expireMillis = settings.getSessionExpireMinutes() * 60 * 1000;
        long now = System.currentTimeMillis();
        int beforeCount = sessionStore.size();

        sessionStore.entrySet().removeIf(entry -> {
            SessionWrapper wrapper = entry.getValue();
            long lastAccess = wrapper.getLastAccessTime();
            boolean expired = (now - lastAccess) > expireMillis;
            if (expired) {
                System.out.println("Cleaning expired session: sessionId=" + entry.getKey() +
                    ", last access=" + (now - lastAccess) / 1000 + " seconds ago");
            }
            return expired;
        });

        int afterCount = sessionStore.size();
        int cleanedCount = beforeCount - afterCount;
        if (cleanedCount > 0) {
            System.out.println("Session cleanup completed: cleaned " + cleanedCount + " expired sessions, remaining " + afterCount + " active sessions");
        }
    }

    /**
     * Get current session count
     */
    public int getSessionCount() {
        return sessionStore.size();
    }

    /**
     * Create typed stream callback wrapper
     */
    private TypedStreamCallback createTypedCallback(StreamCallback originalCallback) {
        if (originalCallback == null) {
            return null;
        }
        
        if (originalCallback instanceof TypedStreamCallback) {
            return (TypedStreamCallback) originalCallback;
        }

        // Create adapter to wrap original callback as typed callback
        return new TypedStreamCallback() {
            @Override
            public void onTypedChunk(ContentType contentType, String content) {
                originalCallback.onChunk(content);
            }
            
            @Override
            public void onChunk(String content) {
                originalCallback.onChunk(content);
            }
        };
    }

    private void observationChunk(TypedStreamCallback typedCallback,String fullObservation) {
        if (!settings.isShowToolDetails()) {
            // String messageString = "";
            // // Simplified display: only show success or failure
            // if (fullObservation.startsWith("✅")) {
            //     messageString = "Execution result: ✅";
            // } else if (fullObservation.startsWith("❌")) {
            //     messageString = "Execution result: ❌";
            // }
            // typedCallback.onTypeMarker(ContentType.ACTION);
            // typedCallback.onTypedChunk(ContentType.ACTION, messageString);
        } else {
            typedCallback.onTypeMarker(ContentType.OBSERVATION);
            typedCallback.onTypedChunk(ContentType.OBSERVATION, fullObservation);
        }
    }
    
    /**
     * Create callback for processing model output, parse and identify content type - based on line buffer
     */
    private StreamCallback createModelCallback(TypedStreamCallback typedCallback) {
        if (typedCallback == null) {
            return null;
        }

        // Minimum complete marker length: "THINK:" = 6
        // final int MIN_MARKER_LENGTH = 6;

        return new TypedStreamCallback() {
            private ContentType currentType = ContentType.REASONING;
            private boolean isFirstLine = true;
            private StringBuilder lineBuffer = new StringBuilder();
            private Boolean isOutputting = false;

            @Override
            public void onChunk(String content) {
                if (content == null || content.isEmpty()) {
                    return;
                }

                if(isFirstLine&&content.isBlank()){
                    isFirstLine=false;
                    return;
                }
                isFirstLine=false;

                int start = 0;
                // int length=content.trim().length();
               
                while (start < content.length()) {
                    int end = content.indexOf('\n', start);
                    if (end == -1) {
                        end = content.length();
                    }
                    
                    String line = content.substring(start, end);
                    
                    if(end + 1<= content.length()){
                        handerLineContent(line+"\n",true);
                        isOutputting=false;
                    }
                    else
                        handerLineContent(line,false);
                    // if(isOutputting){
                    //     if (!settings.isShowToolDetails()&&currentType==ContentType.ACTION) {
                    //     }
                    //     else
                    //         typedCallback.onTypedChunk(currentType, lineBuffer.toString());
                    //     lineBuffer.setLength(0); // Clear buffer
                    // }

                    start = end + 1;

                }

                // // Split content by newlines for processing
                // // int pos = 0;
                // // while (pos < content.length()) {
                //     int newlinePos = content.lastIndexOf('\n');

                //     if (newlinePos == -1) {
                //         lineBuffer.append(content);
                //         int pos = lineBuffer.length();

                //         if(pos>ANSWER.length() && lineBuffer.toString().startsWith(ANSWER+":")){
                //             currentType = ContentType.ANSWER;
                //             isOutputting = true;
                //             lineBuffer.delete(0, ANSWER.length() + 1); // Delete "ANSWER:" prefix
                //         }else if(pos>THINKING.length() && lineBuffer.toString().startsWith(THINKING+":")){
                //             currentType = ContentType.THINKING;
                //             isOutputting = true;
                //             lineBuffer.delete(0, THINKING.length() + 1); // Delete "THINKING:" prefix
                //         }else if(pos>ACTION.length() && lineBuffer.toString().startsWith(ACTION+":")){
                //             currentType = ContentType.ACTION;
                //             isOutputting = true;
                //             lineBuffer.delete(0, ACTION.length() + 1); // Delete "ACTION:" prefix
                //         }
                //         else if(pos>6){
                //             isOutputting = true;
                //         }

                //     } else {
                //         // Found newline character
                //         isOutputting = false;
                //         String beforeNewline = content.substring(0, newlinePos);
                //         typedCallback.onTypedChunk(currentType, beforeNewline+"\n");

                //         lineBuffer.setLength(0); // Clear buffer
                //                 lineBuffer.append(content.substring(newlinePos + 1));
                //     }

                //     if(isOutputting){
                //          if (!settings.isShowToolDetails()&&currentType==ContentType.ACTION) {
                //          }
                //          else
                //             typedCallback.onTypedChunk(currentType, lineBuffer.toString());
                //         lineBuffer.setLength(0); // Clear buffer
                //     }
                // }
            }

            private void handerLineContent(String content, boolean needOutput) {
                lineBuffer.append(content);
                if (isOutputting) {
                     if (!settings.isShowToolDetails() && currentType == ContentType.ACTION) {
                    } else
                        typedCallback.onTypedChunk(currentType, lineBuffer.toString());
                    lineBuffer.setLength(0); // Clear buffer
                    return;
                }

                int pos = lineBuffer.length();

                if (pos > ASK.length() && lineBuffer.toString().startsWith(ASK + ":")) {
                    currentType = ContentType.ASK;
                    isOutputting = true;
                    lineBuffer.delete(0, ASK.length() + 1); // Delete "ASK:" prefix
                } else if (pos > ANSWER.length() && lineBuffer.toString().startsWith(ANSWER + ":")) {
                    currentType = ContentType.ANSWER;
                    isOutputting = true;
                    lineBuffer.delete(0, ANSWER.length() + 1); // Delete "ANSWER:" prefix
                } else if (pos > THINKING.length() && lineBuffer.toString().startsWith(THINKING + ":")) {
                    currentType = ContentType.THINKING;
                    isOutputting = true;
                    lineBuffer.delete(0, THINKING.length() + 1); // Delete "THINKING:" prefix
                } else if (pos > ACTION.length() && lineBuffer.toString().startsWith(ACTION + ":")) {
                    currentType = ContentType.ACTION;
                    isOutputting = true;
                    lineBuffer.delete(0, ACTION.length() + 1); // Delete "ACTION:" prefix
                } else if (pos > 6) {
                    isOutputting = true;
                }

                if (isOutputting || needOutput) {
                    if (!settings.isShowToolDetails() && currentType == ContentType.ACTION) {
                    } else
                        typedCallback.onTypedChunk(currentType, lineBuffer.toString());
                    lineBuffer.setLength(0); // Clear buffer
                }
            }

           

            @Override
            public void onTypedChunk(ContentType contentType, String content) {
                // Send processed content
                typedCallback.onTypedChunk(contentType, content);
            }
        };
    }
    
    // /**
    //  * Get identifier for content type
    //  */
    // private String getTypeMarker(ContentType type) {
    //     switch (type) {
    //         case THINKING:
    //             return "THINK:";
    //         case ACTION:
    //             return "ACTION:";
    //         case OBSERVATION:
    //             return "OBSERVE:";
    //         case ANSWER:
    //             return "ANSWER:";
    //         default:
    //             return "";
    //     }
    // }
    

    /**
     * Execute structured tool_calls, return observation content, supports passing request context.
     * Unified processing: backend tools → ToolInvoker, frontend tools → FrontendToolInvoker
     */
    private String executeToolCall(ToolCall toolCall, RequestContext requestContext, ChatCompletionRequest request,
                                  TypedStreamCallback typedCallback, String sessionId) {
        ToolCallFunction function = toolCall.getFunction();
        if (function == null) {
            return "❌ " + i18nService.get("react.tool_call_failed") + ": Missing tool call definition";
        }

        String name = function.getName();

        // Send tool execution start notification (for frontend timer display)
        if (typedCallback != null) {
            typedCallback.onTypedChunk(ContentType.ACTION_START, i18nService.get("react.action_start"));
        }

        // Special handling: tool detail query
        if (TOOL_DETAIL_NAME.equals(name)) {
            String result = fetchToolDetail(function.getArguments(), request);

            // Send tool execution success notification (for frontend timer display)
            if (typedCallback != null) {
                typedCallback.onTypedChunk(ContentType.ACTION_END, "success");
            }

            return "✅ " + i18nService.get("react.tool_call_success") + ": " + result;
        }

        // Determine tool type and route to corresponding executor
        boolean isFrontendTool = request.getFrontendTools() != null &&
                request.getFrontendTools().stream()
                        .anyMatch(spec -> name.equals(spec.getFunction().getName()));

        if (isFrontendTool) {
            // Frontend tool → FrontendToolInvoker
            try {
                Object result = frontendToolInvoker.invoke(toolCall, sessionId, typedCallback);

                // Send tool execution success notification (for frontend timer display)
                if (typedCallback != null) {
                    typedCallback.onTypedChunk(ContentType.ACTION_END, "success");
                }

                // Always return complete information for AI use
                return "✅ " + i18nService.get("react.tool_call_success") + ": " + result;
            } catch (InterruptedException e) {
                // Send tool execution failure notification (for frontend timer display)
                if (typedCallback != null) {
                    typedCallback.onTypedChunk(ContentType.ACTION_END, "error");
                }
                Thread.currentThread().interrupt();
                return "❌ " + i18nService.get("react.tool_call_failed") + ": Call interrupted";
            } catch (Exception e) {
                return "❌ " + i18nService.get("react.tool_call_failed") + ": " + e.getMessage();
            }
        } else {
            // Backend tool → ToolInvoker
            Optional<ToolDefinition> definition = toolRegistry.getDefinition(name);
            if (definition.isEmpty()) {
                // Send tool execution failure notification (for frontend timer display)
                if (typedCallback != null) {
                    typedCallback.onTypedChunk(ContentType.ACTION_END, "error");
                }
                return "❌ " + i18nService.get("react.tool_call_failed") + ": Unknown tool: " + name;
            }
            try {
                Object result = toolInvoker.invoke(definition.get(), function.getArguments(), requestContext);
                String resultStr = result instanceof String ? (String) result : objectMapper.writeValueAsString(result);

                // Send tool execution success notification (for frontend timer display)
                if (typedCallback != null) {
                    typedCallback.onTypedChunk(ContentType.ACTION_END, "success");
                }

                // Always return complete information for AI use
                return "✅ " + i18nService.get("react.tool_call_success") + ": " + resultStr;
            } catch (Exception ex) {
                // Send tool execution failure notification (for frontend timer display)
                if (typedCallback != null) {
                    typedCallback.onTypedChunk(ContentType.ACTION_END, "error");
                }
                return "❌ " + i18nService.get("react.tool_call_failed") + ": Tool execution failed: " + ex.getMessage();
            }
        }
    }


    /**
     * Execute tool call text in thinking/action (test visible).
     */
    /**
     * Execute tool call text in thinking/action (test visible).
     */
    public String executeAction(String actionText) {
        return executeActionWithContext(actionText, null, null, null);
    }

    /**
     * Execute tool call text in thinking/action, supports passing request context.
     * Unified processing: backend tools → ToolInvoker, frontend tools → FrontendToolInvoker
     */
    public String executeActionWithContext(String actionText, RequestContext requestContext,
                                          ChatCompletionRequest request, TypedStreamCallback typedCallback) {
        ActionCall actionCall = parseActionCall(actionText);
        if (actionCall == null) {
            return "❌ " + i18nService.get("react.tool_call_failed") + ": Unable to parse action: " + actionText + ". Correct format: ToolName(\"param1\", \"param2\")";
        }

        String toolName = actionCall.getToolName();

        // Send tool execution start notification (for frontend timer display)
        if (typedCallback != null) {
            typedCallback.onTypedChunk(ContentType.ACTION_START, i18nService.get("react.action_start"));
        }

        // Special handling: tool detail query
        if (TOOL_DETAIL_NAME.equals(toolName)) {
            String result = fetchToolDetailFromArgs(actionCall.getArgs());
            if (typedCallback != null) {
                typedCallback.onTypedChunk(ContentType.ACTION_END, "success");
            }
            return "✅ " + i18nService.get("react.tool_call_success") + ": " + result;
        }

        // Determine tool type and route to corresponding executor
        boolean isFrontendTool = request != null && request.getFrontendTools() != null &&
                request.getFrontendTools().stream()
                        .anyMatch(spec -> toolName.equals(spec.getFunction().getName()));

        if (isFrontendTool) {
            // Frontend tool → FrontendToolInvoker
            return executeFrontendToolAction(actionCall, request, requestContext, typedCallback);
        } else {
            // Backend tool → ToolInvoker
            return executeBackendToolAction(actionCall, requestContext, typedCallback);
        }
    }

    /**
     * Execute frontend tool (ACTION format)
     * Construct ToolCall object and call FrontendToolInvoker
     */
    private String executeFrontendToolAction(ActionCall actionCall, ChatCompletionRequest request,
                                            RequestContext requestContext, TypedStreamCallback typedCallback) {
        try {
            // 1. Construct ToolCall object
            ToolCall toolCall = new ToolCall();
            toolCall.setId("call_" + UUID.randomUUID().toString());
            toolCall.setType("function");

            ToolCallFunction function = new ToolCallFunction();
            function.setName(actionCall.getToolName());

            // 2. Convert parameter format: ActionCall's List<Object> → JSON string
            Map<String, Object> paramsMap = convertActionArgsToMap(actionCall, request);
            function.setArguments(objectMapper.writeValueAsString(paramsMap));

            toolCall.setFunction(function);

            // 3. Get sessionId
            String sessionId = request.getSessionId();
            if (sessionId == null || sessionId.trim().isEmpty()) {
                sessionId = "default_" + UUID.randomUUID().toString();
            }

            // 4. Call FrontendToolInvoker (using the actual typedCallback)
            String result = frontendToolInvoker.invoke(toolCall, sessionId, typedCallback);

            // 6. Send tool execution completion notification (for frontend timer display)
            if (typedCallback != null) {
                typedCallback.onTypedChunk(ContentType.ACTION_END, "success");
            }

            // 5. Return result
            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (typedCallback != null) {
                typedCallback.onTypedChunk(ContentType.ACTION_END, "success");
            }
            return "❌ " + i18nService.get("react.tool_call_failed") + ": Call interrupted";
        } catch (Exception e) {
            if (typedCallback != null) {
                typedCallback.onTypedChunk(ContentType.ACTION_END, "error");
            }
            return "❌ " + i18nService.get("react.tool_call_failed") + ": " + e.getMessage();
        }
    }

    /**
     * Convert ActionCall's parameter list to parameter Map
     * For frontend tools, intelligently match based on tool's parameter schema
     */
    private Map<String, Object> convertActionArgsToMap(ActionCall actionCall, ChatCompletionRequest request) {
        // If no parameters, return empty Map
        if (actionCall.getArgs() == null || actionCall.getArgs().isEmpty()) {
            return Map.of();
        }

        List<Object> args = actionCall.getArgs();

        // Case 1: If only one parameter and it's a Map, return directly (standard format)
        if (args.size() == 1 && args.get(0) instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) args.get(0);
            return map;
        }

        // Case 2: If only one parameter and it's not a Map, wrap into single-element Map
        if (args.size() == 1) {
            // Need to find tool definition to get parameter name
            String toolName = actionCall.getToolName();
            if (request != null && request.getFrontendTools() != null) {
                Optional<ToolSpec> toolSpec = request.getFrontendTools().stream()
                        .filter(spec -> toolName.equals(spec.getFunction().getName()))
                        .findFirst();

                if (toolSpec.isPresent() && toolSpec.get().getFunction() != null) {
                    Object parametersObj = toolSpec.get().getFunction().getParameters();
                    if (parametersObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> parameters = (Map<String, Object>) parametersObj;
                        Object paramPropsObj = parameters.get("properties");
                        if (paramPropsObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> paramProps = (Map<String, Object>) paramPropsObj;
                            if (!paramProps.isEmpty()) {
                                // Get first parameter name
                                String firstParamName = paramProps.keySet().iterator().next();
                                return Map.of(firstParamName, args.get(0));
                            }
                        }
                    }
                }
            }

            // If parameter definition not found, use generic handling
            return Map.of("arg0", args.get(0));
        }

        // Case 3: Multiple parameters, match parameter names based on tool schema
        String toolName = actionCall.getToolName();
        if (request != null && request.getFrontendTools() != null) {
            Optional<ToolSpec> toolSpec = request.getFrontendTools().stream()
                    .filter(spec -> toolName.equals(spec.getFunction().getName()))
                    .findFirst();

            if (toolSpec.isPresent() && toolSpec.get().getFunction() != null) {
                Object parametersObj = toolSpec.get().getFunction().getParameters();
                if (parametersObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parameters = (Map<String, Object>) parametersObj;
                    Object paramPropsObj = parameters.get("properties");
                    if (paramPropsObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> paramProps = (Map<String, Object>) paramPropsObj;
                        if (!paramProps.isEmpty()) {
                            // Match parameters in order
                            Map<String, Object> result = new LinkedHashMap<>();
                            int index = 0;
                            for (String paramName : paramProps.keySet()) {
                                if (index < args.size()) {
                                    result.put(paramName, args.get(index));
                                    index++;
                                }
                            }
                            return result;
                        }
                    }
                }
            }
        }

        // Fallback: return empty Map
        return Map.of();
    }

    /**
     * Execute backend tool (ACTION format)
     * Use ToolInvoker for reflection-based invocation
     */
    private String executeBackendToolAction(ActionCall actionCall, RequestContext requestContext, TypedStreamCallback typedCallback) {
        Optional<ToolDefinition> definition = toolRegistry.getDefinition(actionCall.getToolName());
        if (definition.isEmpty()) {
            if (typedCallback != null) {
                typedCallback.onTypedChunk(ContentType.ACTION_END, "error");
            }
            return "❌ " + i18nService.get("react.tool_call_failed") + ": Tool not found: " + actionCall.getToolName() +
                   ". Available tools: " + String.join(", ", listToolNames());
        }

        try {
            // Intelligently handle parameter structure: if parameter is an object containing parameter names, extract its value
            List<Object> processedArgs = processActionArgs(definition.get(), actionCall.getArgs());
            Object result = toolInvoker.invokeWithArgs(definition.get(), processedArgs, requestContext);
            String resultStr = "";
            if (result != null) {
                resultStr = result instanceof String ? (String) result : objectMapper.writeValueAsString(result);
            }

            // Send tool execution success notification (for frontend timer display)
            if (typedCallback != null) {
                typedCallback.onTypedChunk(ContentType.ACTION_END, "success");
            }

            // Always return complete information for AI use
            return "✅ " + i18nService.get("react.tool_call_success") + ": " + resultStr;
        } catch (Exception ex) {
            if (typedCallback != null) {
                typedCallback.onTypedChunk(ContentType.ACTION_END, "error");
            }
            return "❌ " + i18nService.get("react.tool_call_failed") + ": Execution error: " + ex.getMessage();
        }
    }

    /**
     * Intelligently handle action parameters, resolve parameter nesting issues
     */
    private List<Object> processActionArgs(ToolDefinition definition, List<Object> rawArgs) {
        if (rawArgs == null || rawArgs.isEmpty()) {
            return rawArgs;
        }

        List<ToolParamBinding> bindings = definition.getParamBindings();

        // Special case: if only one parameter, and a Map is passed, may need to unwrap
        if (bindings.size() == 1 && rawArgs.size() == 1) {
            Object arg = rawArgs.get(0);
            if (arg instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> argMap = (Map<String, Object>) arg;
                ToolParamBinding binding = bindings.get(0);
                String paramName = binding.getName();

                // If parameter object has only one key, and this key matches method parameter name, extract its value
                if (argMap.size() == 1 && argMap.containsKey(paramName)) {
                    Object extractedValue = argMap.get(paramName);
                    return List.of(extractedValue);
                }
            }
        }

        // Multi-parameter case: if only one Map parameter, try to extract each parameter value from Map
        if (rawArgs.size() == 1 && rawArgs.get(0) instanceof Map && bindings.size() > 1) {
            @SuppressWarnings("unchecked")
            Map<String, Object> argMap = (Map<String, Object>) rawArgs.get(0);
            List<Object> extractedArgs = new ArrayList<>();

            // Extract values in parameter binding order
            for (ToolParamBinding binding : bindings) {
                String paramName = binding.getName();
                if (argMap.containsKey(paramName)) {
                    extractedArgs.add(argMap.get(paramName));
                } else {
                    // If parameter not found, use null
                    extractedArgs.add(null);
                }
            }
            return extractedArgs;
        }

        // Other cases, return original parameters directly
        return rawArgs;
    }

    /**
     * Check if business logic is successful (test visible)
     */
    public boolean checkBusinessSuccess(Object result, String resultStr) {
        if (result == null) {
            return false;
        }

        // If result is BatchOperationResult type, check overallSuccess field
        if (result.getClass().getSimpleName().contains("BatchOperationResult")) {
            try {
                // Use reflection to get overallSuccess field
                java.lang.reflect.Field successField = result.getClass().getField("overallSuccess");
                Object successValue = successField.get(result);
                return Boolean.TRUE.equals(successValue);
            } catch (Exception e) {
                // Reflection failed, use string check
                return resultStr.contains("\"overallSuccess\":true") &&
                       !resultStr.contains("failed") &&
                       !resultStr.contains("error") &&
                       !resultStr.contains("not found");
            }
        }

        // For other types, use string check
        return !resultStr.contains("failed") &&
               !resultStr.contains("error") &&
               !resultStr.contains("not found") &&
               !resultStr.contains("invalid") &&
               !resultStr.contains("\"found\":false");
    }

    /**
     * Get tool details based on action parameters.
     */
    private String fetchToolDetailFromArgs(List<Object> args) {
        if (args == null || args.isEmpty()) {
            return "Tool name is empty";
        }
        Object first = args.get(0);
        String toolName = first == null ? null : first.toString();
        if (toolName == null || toolName.isBlank()) {
            return "Tool name is empty";
        }
        Optional<ToolDetail> detail = toolRegistry.getDetail(toolName);
        if (detail.isEmpty()) {
            return "Tool details not found: " + toolName;
        }
        try {
            // Build enhanced tool detail information
            Map<String, Object> enhancedDetail = buildEnhancedToolDetail(detail.get());
            return objectMapper.writeValueAsString(enhancedDetail);
        } catch (Exception ex) {
            return "Tool detail serialization failed: " + ex.getMessage();
        }
    }

    /**
     * Build enhanced tool detail information, including complete parameter structure and examples
     */
    private Map<String, Object> buildEnhancedToolDetail(ToolDetail detail) {
        Map<String, Object> enhanced = new LinkedHashMap<>();

        // Basic information
        enhanced.put("name", detail.getName());
        enhanced.put("description", detail.getDescription());
        enhanced.put("methodSignature", detail.getMethodSignature());

        // Complete parameter schema
        Map<String, Object> parameters = buildJsonSchema(detail);
        enhanced.put("parameters", parameters);

        // Special handling: if only one parameter and it's a complex object, "unwrap" it to simplify AI usage
        Map<String, Object> props = (Map<String, Object>) parameters.get("properties");
        if (props != null && props.size() == 1) {
            // Only one parameter
            Map.Entry<String, Object> onlyParam = props.entrySet().iterator().next();
            @SuppressWarnings("unchecked")
            Map<String, Object> onlyParamDef = (Map<String, Object>) onlyParam.getValue();

            // If this parameter is object type and has properties, it can be "unwrapped"
            if ("object".equals(onlyParamDef.get("type")) && onlyParamDef.containsKey("properties")) {
                // Create an "unwrapped" parameter structure
                Map<String, Object> unwrappedParams = new LinkedHashMap<>();
                unwrappedParams.put("type", "object");
                unwrappedParams.put("properties", onlyParamDef.get("properties"));

                // Retain required information (if any)
                if (parameters.containsKey("required")) {
                    unwrappedParams.put("required", true);
                }

                // Use "unwrapped" parameter to replace original parameter
                enhanced.put("parameters", unwrappedParams);

                // Regenerate requestExample (without parameter name wrapping)
                @SuppressWarnings("unchecked")
                Map<String, Object> paramProps = (Map<String, Object>) onlyParamDef.get("properties");
                if (paramProps != null && !paramProps.isEmpty()) {
                    Object example = generateExampleFromProperties(paramProps);
                    if (example != null) {
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            String exampleJson = mapper.writeValueAsString(example);
                            enhanced.put("requestExample", exampleJson);
                        } catch (Exception e) {
                            // Ignore serialization error
                        }
                    }
                }
            } else {
                // Cannot "unwrap", use original logic
                regenerateRequestExample(enhanced, detail);
            }
        } else {
            // Multiple parameters, use original logic
            regenerateRequestExample(enhanced, detail);
        }

        // Return information
        if (detail.getReturns() != null) {
            Map<String, Object> returnInfo = new LinkedHashMap<>();
            returnInfo.put("type", detail.getReturns().getType());
            returnInfo.put("description", detail.getReturns().getDescription());
            if (detail.getResponseExample() != null) {
                returnInfo.put("example", detail.getResponseExample());
            }
            enhanced.put("returns", returnInfo);
        }

        // Usage tips
        String usageTip = "When using this tool, please construct parameters according to the structure in parameters. For complex object parameters, refer to the format in requestExample.";

        // Check if there are REST API parameters, add special note
        boolean hasRestParams = detail.getParams().stream()
                .anyMatch(p -> p.getParamSource() != null &&
                        p.getParamSource() != cn.autoai.core.registry.ToolParamBinding.ParamSource.OTHER);

        if (hasRestParams) {
            usageTip += "\n\nNote: This tool includes REST API parameters. Path parameters (paramSource=path_variable) and query parameters (paramSource=request_param) must be simple values (numbers, strings), do not use objects.";
        }

        enhanced.put("usage", usageTip);

        return enhanced;
    }

    /**
     * Regenerate requestExample (for cases where unwrapping is not possible)
     */
    private void regenerateRequestExample(Map<String, Object> enhanced, ToolDetail detail) {
        Map<String, Object> parameters = (Map<String, Object>) enhanced.get("parameters");
        Map<String, Object> props = (Map<String, Object>) parameters.get("properties");
        if (props != null && !props.isEmpty()) {
            // Generate example from properties
            Map<String, Object> requestExample = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> propDef = (Map<String, Object>) entry.getValue();
                if (propDef.containsKey("example")) {
                    requestExample.put(entry.getKey(), propDef.get("example"));
                }
            }
            if (!requestExample.isEmpty()) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    String exampleJson = mapper.writeValueAsString(requestExample);
                    enhanced.put("requestExample", exampleJson);
                } catch (Exception e) {
                    // Use original example when serialization fails
                    if (detail.getRequestExample() != null) {
                        enhanced.put("requestExample", detail.getRequestExample());
                    }
                }
            }
        }

        // Request example (if not regenerated)
        if (!enhanced.containsKey("requestExample") && detail.getRequestExample() != null) {
            enhanced.put("requestExample", detail.getRequestExample());
        }
    }

    /**
     * Compatible JSON parameter format tool detail retrieval.
     * Supports frontend tool and backend tool detail queries.
     */
    public String fetchToolDetail(String arguments, ChatCompletionRequest request) {
        if (arguments == null || arguments.isBlank()) {
            return "Tool name is empty";
        }
        try {
            Map<String, Object> map = objectMapper.readValue(arguments, new TypeReference<Map<String, Object>>() {
            });
            Object value = map.get("name");
            if (value == null) {
                return "Tool name is empty";
            }
            String toolName = value.toString();

            // Prioritize finding frontend tools
            if (request != null && request.getFrontendTools() != null) {
                for (ToolSpec frontendTool : request.getFrontendTools()) {
                    if (toolName.equals(frontendTool.getFunction().getName())) {
                        // Found frontend tool, return its complete details
                        return buildFrontendToolDetail(frontendTool);
                    }
                }
            }

            // Find backend tools
            Optional<ToolDetail> detail = toolRegistry.getDetail(toolName);
            if (detail.isEmpty()) {
                return "Tool details not found: " + toolName;
            }
            // Build enhanced tool detail information
            Map<String, Object> enhancedDetail = buildEnhancedToolDetail(detail.get());
            return objectMapper.writeValueAsString(enhancedDetail);
        } catch (Exception ex) {
            return "Tool detail parameter parsing failed: " + ex.getMessage();
        }
    }

    /**
     * Parse thought and action segments in model output.
     * Supports multiple formats to improve parsing success rate.
     */
    private ThoughtAction parseThoughtActionObservation(String text) {
        if (text == null || text.isBlank()) {
            return new ThoughtAction(null, null);
        }

        String normalizedText = text.replaceAll("\\r\\n", "\n").trim();

        // First check if ASK marker is present
        Matcher askMatch = ASK_PATTERN.matcher(normalizedText);
        if (askMatch.find()) {
            String askContent = askMatch.group(1).trim();
            // Return a special thought content, marked as inquiry
            return new ThoughtAction("ASK:" + askContent, null);
        }

        // Try to match complete thought-action format
        Matcher thoughtMatch = THOUGHT_PATTERN.matcher(normalizedText);
        Matcher actionMatch = ACTION_PATTERN.matcher(normalizedText);

        String thought = null;
        String action = null;

        if (thoughtMatch.find()) {
            thought = thoughtMatch.group(1).trim();
        }

        if (actionMatch.find()) {
            action = actionMatch.group(1).trim().replaceAll("^\\[|\\]$", "");
        }

        // Both markers exist, return result
        if (thought != null && action != null) {
            return new ThoughtAction(thought, action);
        }

        // Only thought, no action
        if (thought != null && action == null) {
            // Try to find action after thought
            int thoughtEnd = thoughtMatch.end();
            if (thoughtEnd < normalizedText.length()) {
                String afterThought = normalizedText.substring(thoughtEnd).trim();
                // Check if there's ACTION marker
                if (afterThought.startsWith(ACTION) || afterThought.startsWith(ACTION + ":")) {
                    int colonIndex = afterThought.indexOf(':');
                    if (colonIndex >= 0) {
                        action = afterThought.substring(colonIndex + 1).trim()
                            .replaceAll("^\\[|\\]$", "");
                    }
                }
            }
            // If still no action, return result with only thought
            return new ThoughtAction(thought, action);
        }

        // Only action, no thought
        if (thought == null && action != null) {
            // Try to find thought before action
            int actionStart = actionMatch.start();
            if (actionStart > 0) {
                String beforeAction = normalizedText.substring(0, actionStart).trim();
                // Check if there's THINK marker
                if (beforeAction.endsWith(THINKING) || beforeAction.endsWith(THINKING + ":")) {
                    int lastNewline = beforeAction.lastIndexOf('\n');
                    if (lastNewline >= 0) {
                        thought = beforeAction.substring(lastNewline + 1).trim();
                    } else {
                        thought = beforeAction;
                    }
                    int colonIndex = thought.indexOf(':');
                    if (colonIndex >= 0) {
                        thought = thought.substring(colonIndex + 1).trim();
                    }
                }
            }
            if (thought == null) {
                thought = "Execute operation";
            }
            return new ThoughtAction(thought, action);
        }

        // Fallback: directly match tool call format (must have ACTION: marker prefix)
        Matcher directMatch = DIRECT_ACTION_PATTERN.matcher(normalizedText);
        if (directMatch.find()) {
            // group(0) is complete match "ACTION: ToolName(Parameters)", group(1) is tool call part
            String fullMatch = directMatch.group(0);
            String toolCall = directMatch.group(1);
            thought = extractThoughtFromContext(normalizedText, fullMatch);
            action = toolCall;
            return new ThoughtAction(thought, action);
        }

        // Final fallback: entire text as thought
        thought = normalizedText;
        return new ThoughtAction(thought, null);
    }

    /**
     * Extract thought content from tool call context
     */
    private String extractThoughtFromContext(String fullText, String actionText) {
        int actionIndex = fullText.indexOf(actionText);
        if (actionIndex <= 0) {
            return "Execute operation";
        }

        String before = fullText.substring(0, actionIndex).trim();

        // Find last non-empty line as thought
        String[] lines = before.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                // Remove marker prefix (support new English labels and old symbols)
                line = line.replaceAll("^(THINK|ACTION|ANSWER|OBSERVE|DONE)[:：]?\\s*", "");
                line = line.replaceAll("^[♩♫♪♬]+[:：]?\\s*", "");
                if (!line.isEmpty()) {
                    return line;
                }
            }
        }

        return "Execute operation";
    }

    /**
     * Parse tool name and parameter string in action.
     */
    private ActionCall parseActionCall(String actionText) {
        Matcher match = ACTION_CALL_PATTERN.matcher(actionText);
        if (!match.find()) {
            Matcher alt = ACTION_ALT_PATTERN.matcher(actionText);
            if (alt.find()) {
                String toolName = alt.group(1);
                String args = alt.group(2);
                return new ActionCall(toolName, args, parseActionArgs(args));
            }
            return null;
        }
        String toolName = match.group(1);
        String args = match.group(2);
        return new ActionCall(toolName, args, parseActionArgs(args));
    }

    /**
     * Parse action parameters, prioritize JSON array parsing, fallback to relaxed split rules.
     */
    private List<Object> parseActionArgs(String argsStr) {
        if (argsStr == null || argsStr.isBlank()) {
            return List.of();
        }
        String trimmedArgs = argsStr.trim();
        try {
            return objectMapper.readValue("[" + trimmedArgs + "]", new TypeReference<List<Object>>() {
            });
        } catch (Exception ignore) {
        }

        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;
        int depth = 0;

        for (int i = 0; i < trimmedArgs.length(); i++) {
            char ch = trimmedArgs.charAt(i);
            if ((ch == '\"' || ch == '\'') && (i == 0 || trimmedArgs.charAt(i - 1) != '\\')) {
                if (!inQuotes) {
                    inQuotes = true;
                    quoteChar = ch;
                } else if (ch == quoteChar) {
                    inQuotes = false;
                    quoteChar = 0;
                }
                current.append(ch);
            } else if (ch == '(' || ch == '[' || ch == '{') {
                depth++;
                current.append(ch);
            } else if (ch == ')' || ch == ']' || ch == '}') {
                depth--;
                current.append(ch);
            } else if (ch == ',' && !inQuotes && depth == 0) {
                String piece = current.toString().trim();
                if (!piece.isEmpty()) {
                    parts.add(piece);
                }
                current = new StringBuilder();
            } else {
                current.append(ch);
            }
        }

        String piece = current.toString().trim();
        if (!piece.isEmpty()) {
            parts.add(piece);
        }

        List<Object> parsed = new ArrayList<>();
        for (String part : parts) {
            parsed.add(parsePrimitive(part));
        }
        return parsed;
    }

    /**
     * Parse basic type parameters, supports string/number/boolean/null.
     */
    private Object parsePrimitive(String part) {
        if (part == null) {
            return null;
        }
        String value = part.trim();
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if ("true".equals(lower)) {
            return true;
        }
        if ("false".equals(lower)) {
            return false;
        }
        if ("null".equals(lower)) {
            return null;
        }
        if (value.matches("-?\\d+")) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ex) {
                return value;
            }
        }
        if (value.matches("-?\\d+\\.\\d+")) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException ex) {
                return value;
            }
        }
        return value;
    }

    /**
     * Check if output contains final answer marker.
     * Improvement: only check markers at line start to avoid false positives in normal text
     */
    private boolean isFinalAnswer(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        // Check by line, only match markers at line start (allowing spaces at line start)
        String[] lines = content.split("\\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            for (String marker : COMPLETION_MARKERS) {
                // Check if starts with marker (after removing leading/trailing spaces)
                if (trimmedLine.startsWith(marker)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if output contains marker to ask user.
     * When user input is needed, the process will be interrupted and wait for user response
     */
    private boolean isInterruptionByAsk(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        // Check by line, only match markers at line start (allowing spaces at line start)
        String[] lines = content.split("\\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            for (String marker : INTERUPTION_MARKERS) {
                // Check if starts with marker (after removing leading/trailing spaces)
                if (trimmedLine.startsWith(marker)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Write or update system prompt (using cache optimization).
     */
    private void applySystemPrompt(List<ChatMessage> session, List<ToolSpec> toolSpecs, List<String> environmentContext, ChatCompletionRequest request) {
        boolean detailed = session.size() < 10;

        // Calculate current cache version
        int currentVersion = computeCacheVersion(toolSpecs);

        // Check if cache is valid
        if (promptCache != null && promptCache.getCacheVersion() == currentVersion) {
            // Cache valid, use directly
            String prompt = promptCache.getPrompt(detailed);
            if (settings.getSystemPrompt() != null && !settings.getSystemPrompt().isBlank()) {
                prompt = settings.getSystemPrompt() + "\n" + prompt;
            }

            if (session.isEmpty() || !"system".equals(session.get(0).getRole())) {
                session.add(0, ChatMessage.system(prompt));
            } else {
                session.set(0, ChatMessage.system(prompt));
            }

            // Inject environment context as independent message (after system prompt)
            injectEnvironmentContextMessage(session, environmentContext);
            return;
        }

        // Cache invalid, rebuild system prompt
        String detailedPrompt = buildSystemPrompt(true, toolSpecs, request, settings.getLanguage());
        String simplePrompt = buildSystemPrompt(false, toolSpecs, request, settings.getLanguage());

        // Update cache
        promptCache = new SystemPromptCache(detailedPrompt, simplePrompt, currentVersion);

        // Use newly built prompt
        String prompt = detailed ? detailedPrompt : simplePrompt;
        if (settings.getSystemPrompt() != null && !settings.getSystemPrompt().isBlank()) {
            prompt = settings.getSystemPrompt() + "\n" + prompt;
        }

        if (session.isEmpty() || !"system".equals(session.get(0).getRole())) {
            session.add(0, ChatMessage.system(prompt));
        } else {
            session.set(0, ChatMessage.system(prompt));
        }

        // Inject environment context as independent message (after system prompt)
        injectEnvironmentContextMessage(session, environmentContext);
    }
    
    /**
     * Environment context marker, used to identify and update environment context messages
     */
    private static final String ENV_CONTEXT_MARKER = "[ENV_CONTEXT]";

    /**
     * Inject environment context as independent system message to help AI recognize latest environment.
     * Each call will update or replace previous environment context message.
     */
    private void injectEnvironmentContextMessage(List<ChatMessage> session, List<String> environmentContext) {
        if (environmentContext == null || environmentContext.isEmpty()) {
            // If no environment information, remove previous environment message
            removeEnvironmentContextMessage(session);
            return;
        }

        // Build environment context message
        StringBuilder envInfo = new StringBuilder();
        envInfo.append(ENV_CONTEXT_MARKER).append("\n");
        envInfo.append("[Current Environment Information - Take this as authoritative]\n");
        for (String info : environmentContext) {
            if (info != null && !info.isBlank()) {
                envInfo.append("• ").append(info.trim()).append("\n");
            }
        }
        envInfo.append("Note: The above is the latest environment information. If there is a conflict with environment information mentioned in previous conversations, please take this as authoritative.");

        String envMessage = envInfo.toString();

        // Find and update or insert environment context message (place after system prompt, position 1)
        int envIndex = findEnvironmentContextMessageIndex(session);
        if (envIndex >= 0) {
            // Update existing environment context message
            session.set(envIndex, ChatMessage.system(envMessage));
        } else {
            // Insert new environment context message after system prompt
            if (session.size() > 0) {
                session.add(1, ChatMessage.system(envMessage));
            } else {
                session.add(ChatMessage.system(envMessage));
            }
        }
    }

    /**
     * Find index of environment context message
     */
    private int findEnvironmentContextMessageIndex(List<ChatMessage> session) {
        for (int i = 0; i < session.size(); i++) {
            ChatMessage msg = session.get(i);
            if ("system".equals(msg.getRole()) && msg.getContent() != null
                && msg.getContent().startsWith(ENV_CONTEXT_MARKER)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Remove environment context message
     */
    private void removeEnvironmentContextMessage(List<ChatMessage> session) {
        int index = findEnvironmentContextMessageIndex(session);
        if (index >= 0) {
            session.remove(index);
        }
    }

    /**
     * Inject environment context into system prompt (deprecated, kept for compatibility).
     */
    @SuppressWarnings("unused")
    private String injectEnvironmentContext(String prompt, List<String> environmentContext) {
        if (environmentContext == null || environmentContext.isEmpty()) {
            return prompt;
        }

        StringBuilder envInfo = new StringBuilder();
        envInfo.append("\n\n**Current Environment Information**:\n");
        for (String info : environmentContext) {
            if (info != null && !info.isBlank()) {
                envInfo.append("- ").append(info.trim()).append("\n");
            }
        }
        envInfo.append("\nPlease consider the above environment information when answering.");

        return prompt + envInfo.toString();
    }

    /**
     * Build system prompt, including tool list and format specifications.
     * List all tools (backend tools + frontend tools).
     */
    private String buildSystemPrompt(boolean detailed, List<ToolSpec> toolSpecs, ChatCompletionRequest request, String language) {
        StringBuilder toolDescriptions = new StringBuilder();

        // Categorize and list tools
        StringBuilder backendTools = new StringBuilder();
        StringBuilder frontendTools = new StringBuilder();

        for (ToolSpec spec : toolSpecs) {
            if (spec.getFunction() == null) {
                continue;
            }
            ToolFunctionSpec function = spec.getFunction();
            String name = function.getName();
            String desc = function.getDescription();

            // Determine if it's a frontend tool
            boolean isFrontend = request.getFrontendTools() != null &&
                    request.getFrontendTools().stream()
                            .anyMatch(ft -> name.equals(ft.getFunction().getName()));

            StringBuilder target = isFrontend ? frontendTools : backendTools;
            target.append("- ").append(name).append(": ").append(desc == null ? "" : desc);

            if (detailed && !isFrontend) {
                // Only add examples for backend tools (frontend tools are not registered in ToolRegistry)
                Optional<ToolDetail> detail = toolRegistry.getDetail(name);
                if (detail.isPresent() && detail.get().getRequestExample() != null) {
                    target.append("\n  Example: ").append(detail.get().getRequestExample());
                }
            }
            target.append("\n");
        }

        // Combine tool list
        if (backendTools.length() > 0) {
            toolDescriptions.append("### Backend Tools\n");
            toolDescriptions.append(backendTools);
        }

        if (frontendTools.length() > 0) {
            if (backendTools.length() > 0) {
                toolDescriptions.append("\n");
            }
            toolDescriptions.append("### Frontend Tools (Browser API Calls)\n");
            toolDescriptions.append(frontendTools);
        }

        return buildEnglishSystemPrompt(toolDescriptions, language);
    }

    /**
     * Build English system prompt
     */
    private String buildEnglishSystemPrompt(StringBuilder toolDescriptions, String language) {
        // Get language-specific instruction
        String languageInstruction = getLanguageInstruction(language);

        return """
            You are an intelligent assistant that solves problems through reasoning and tool invocation.

            ## Language Preference
            """ + languageInstruction + """

            ## Available Tools
            """ + toolDescriptions + """

            ## Workflow

            ### Step 1: Determine Question Type
            - **Simple questions** (greetings, casual chat, general knowledge, calculations, etc.): Answer directly without tools, output `ANSWER: [direct answer]`
            - **Complex questions** (requiring data lookup, operations, external information, etc.): Use tools
            - **Insufficient information** (need user to provide more details): Ask the user

            ### Step 2: Tool Invocation Format
            If tools are needed, output in the following format, each part must be on a separate line:

            ```
            THINK: [Analyze the problem, determine which tool to use]
            ACTION: ToolFullName("param1", "param2", ...)
            ```

            ### Step 3: After Getting Results
            - If the problem is solved: Output `ANSWER: [final answer]`
            - If more information is needed: Continue calling tools
            - If user input is required: Output `ASK: [your question]`

            ## Tool Invocation Rules
            1. Use complete tool names (including class prefix)
            2. String parameters in double quotes: `"text"`
            3. Numbers written directly: `123`, `45.67`
            4. Boolean values: `true`, `false`
            5. Complex objects: Pass object content directly, no wrapping
               - Correct: `DemoTools.batchUpdateSalary({"updates":[...],"reason":"..."})`
               - Incorrect: `DemoTools.batchUpdateSalary({"batchRequest":{...}})`
            6. Call only one tool at a time

            ## User Interaction Rules
            Use the `ASK:` marker to ask the user when:
            1. The user's question is unclear or missing necessary parameters
            2. User needs to make a choice (e.g., multiple matching options)
            3. **Mandatory confirmation for sensitive operations**:
               - Deleting data (files, records, directories, etc.)
               - Modifying important data (configurations, critical data, etc.)
               - Executing irreversible operations (clearing data, overwriting files, etc.)
               **Confirmation format**: First describe the operation, then ask "Confirm to execute?"
               Example: `ASK: You are about to delete file /path/to/file. This operation cannot be undone. Confirm to execute?`
            4. Tool execution failed and requires additional user information

            Format: `ASK: [your question]`

            ## Common Issue Handling
            - **Tool call failed**: Check error message, adjust parameters and retry
            - **Parameter structure uncertain**: Call `autoai.tool_detail("ToolName")` to view details
            - **Result not as expected**: Adjust strategy based on observation

            ## ⚠️ Format Enforcement
            - Must strictly follow the above format, do not use other formats
            - Parameters must use function call format, do not use XML or other formats (e.g., `<argkey>` or `<parameter>` tags)
            - ACTION line must be complete, tool name and parameters on the same line

            ## Notes
            - Keep thinking concise, focus on next action
            - Do not repeat failed attempts
            - Must output `ANSWER:` marker on the last line when task is completed
            - Use `ASK:` marker when user input is needed, the process will interrupt and wait for user response
            - **Avoid translation of names or department names**: Output them as they are, without translation, to avoid confusion.


            Now, please handle the user's question.
            """;
    }

    /**
     * Get language instruction based on language setting.
     */
    private String getLanguageInstruction(String language) {
        return i18nService.get("ai.language_instruction");
    }

    /**
     * Resolve model instance based on request or default configuration.
     */
    private AutoAiModel resolveModel(String modelName) {
        if (modelName != null) {
            Optional<AutoAiModel> byName = modelRegistry.get(modelName);
            if (byName.isPresent()) {
                return byName.get();
            }
        }
        if (settings.getDefaultModel() != null) {
            Optional<AutoAiModel> byDefault = modelRegistry.get(settings.getDefaultModel());
            if (byDefault.isPresent()) {
                return byDefault.get();
            }
        }
        return modelRegistry.list().stream().findFirst()
            .orElseThrow(() -> new IllegalStateException("No available model configured"));
    }

    /**
     * Copy request and inject current session state and tool collection.
     */
    private ChatCompletionRequest copyRequest(ChatCompletionRequest original, List<ChatMessage> messages,
                                              List<ToolSpec> toolSpecs, String modelName) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(modelName);
        request.setMessages(messages);
        request.setTools(toolSpecs);
        Object toolChoice = original.getToolChoice();
        request.setToolChoice(toolChoice == null ? "auto" : toolChoice);
        request.setTemperature(original.getTemperature());
        request.setMaxTokens(original.getMaxTokens());
        request.setStream(original.getStream());
        return request;
    }

    /**
     * Get the first message from model response.
     */
    private ChatMessage firstAssistant(ChatCompletionResponse response) {
        if (response.getChoices() == null || response.getChoices().isEmpty()) {
            return null;
        }
        return response.getChoices().get(0).getMessage();
    }

    /**
     * Build OpenAI compatible tool description list.
     */
    private List<ToolSpec> buildToolSpecs(ChatCompletionRequest request) {
        List<ToolSpec> specs = new ArrayList<>();
        specs.add(buildToolDetailSpec());
        for (ToolSummary summary : toolRegistry.listSummaries()) {
            // Only pass basic information: name and description, not complex parameter structure
            ToolFunctionSpec function = new ToolFunctionSpec();
            function.setName(summary.getName());
            function.setDescription(summary.getDescription());

            // For complex tools, only provide minimal schema hints
            Optional<ToolDetail> detail = toolRegistry.getDetail(summary.getName());
            if (detail.isPresent()) {
                Map<String, Object> basicSchema = buildBasicSchema(detail.get());
                function.setParameters(basicSchema);
            } else {
                // Default empty parameter schema
                Map<String, Object> emptySchema = new LinkedHashMap<>();
                emptySchema.put("type", "object");
                emptySchema.put("properties", new LinkedHashMap<>());
                function.setParameters(emptySchema);
            }

            specs.add(new ToolSpec("function", function));
        }

        // Add frontend tool definitions (apply simplification logic)
        if (request.getFrontendTools() != null && !request.getFrontendTools().isEmpty()) {
            for (ToolSpec frontendTool : request.getFrontendTools()) {
                // Apply simplification logic to frontend tools, unify parameter format
                ToolSpec simplifiedTool = simplifyFrontendToolSchema(frontendTool);
                specs.add(simplifiedTool);
            }
        }

        return specs;
    }

    /**
     * Build description for built-in tool detail query.
     */
    private ToolSpec buildToolDetailSpec() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", Map.of("type", "string", "description", "Tool name"));
        schema.put("properties", properties);
        schema.put("required", List.of("name"));
        ToolFunctionSpec function = new ToolFunctionSpec(TOOL_DETAIL_NAME, "Get tool details (supports frontend tools and backend tools)", schema);
        return new ToolSpec("function", function);
    }

    /**
     * Simplify frontend tool schema, maintain consistent format with backend tools.
     * Add hint information for complex parameters, guide to use tool_detail for queries.
     */
    private ToolSpec simplifyFrontendToolSchema(ToolSpec frontendTool) {
        ToolSpec simplified = new ToolSpec();
        simplified.setType(frontendTool.getType());

        ToolFunctionSpec simplifiedFunction = new ToolFunctionSpec();
        simplifiedFunction.setName(frontendTool.getFunction().getName());
        simplifiedFunction.setDescription(frontendTool.getFunction().getDescription());

        // Get original parameter schema
        Object originalParams = frontendTool.getFunction().getParameters();
        if (originalParams instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> originalSchema = (Map<String, Object>) originalParams;

            // Build simplified schema
            Map<String, Object> simplifiedSchema = new LinkedHashMap<>();
            simplifiedSchema.put("type", originalSchema.get("type"));

            Map<String, Object> simplifiedProperties = new LinkedHashMap<>();
            if (originalSchema.containsKey("properties")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> originalProperties = (Map<String, Object>) originalSchema.get("properties");

                for (Map.Entry<String, Object> entry : originalProperties.entrySet()) {
                    String paramName = entry.getKey();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> paramDef = (Map<String, Object>) entry.getValue();

                    Map<String, Object> simplifiedParam = new LinkedHashMap<>();
                    String paramType = (String) paramDef.get("type");
                    simplifiedParam.put("type", paramType);

                    // Retain description
                    if (paramDef.containsKey("description")) {
                        String description = (String) paramDef.get("description");

                        // Check if it's a complex object type
                        if (isComplexFrontendParam(paramDef)) {
                            // Add hint information
                            String hint = description + " (complex object, use autoai.tool_detail to get detailed structure)";
                            simplifiedParam.put("description", hint.trim());
                        } else {
                            simplifiedParam.put("description", description);
                            // Simple types can retain examples
                            if (paramDef.containsKey("example")) {
                                simplifiedParam.put("example", paramDef.get("example"));
                            }
                        }
                    }

                    simplifiedProperties.put(paramName, simplifiedParam);
                }
            }

            simplifiedSchema.put("properties", simplifiedProperties);

            // Retain required field
            if (originalSchema.containsKey("required")) {
                simplifiedSchema.put("required", originalSchema.get("required"));
            }

            simplifiedFunction.setParameters(simplifiedSchema);
        }

        simplified.setFunction(simplifiedFunction);
        return simplified;
    }

    /**
     * Determine if frontend tool parameter is a complex object type.
     */
    private boolean isComplexFrontendParam(Map<String, Object> paramDef) {
        String type = (String) paramDef.get("type");
        if (!"object".equals(type)) {
            return false;
        }

        // If has properties field, it's a complex object
        if (paramDef.containsKey("properties")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) paramDef.get("properties");
            // If has nested properties, or has multiple properties, consider it a complex object
            return properties != null && !properties.isEmpty();
        }

        return false;
    }

    /**
     * Build complete details for frontend tool, including complete parameter structure and examples.
     */
    private String buildFrontendToolDetail(ToolSpec frontendTool) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("name", frontendTool.getFunction().getName());
            detail.put("description", frontendTool.getFunction().getDescription());
            detail.put("type", "frontend");

            // Complete parameter schema
            detail.put("parameters", frontendTool.getFunction().getParameters());

            // Generate example
            Map<String, Object> example = generateFrontendToolExample(frontendTool);
            if (example != null) {
                detail.put("example", example);
            }

            return objectMapper.writeValueAsString(detail);
        } catch (Exception ex) {
            return "Frontend tool detail construction failed: " + ex.getMessage();
        }
    }

    /**
     * Generate invocation example for frontend tool.
     */
    private Map<String, Object> generateFrontendToolExample(ToolSpec frontendTool) {
        Object params = frontendTool.getFunction().getParameters();
        if (params instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> schema = (Map<String, Object>) params;

            if (schema.containsKey("properties")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

                Map<String, Object> example = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    String paramName = entry.getKey();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> paramDef = (Map<String, Object>) entry.getValue();

                    // If there is an example, use it; otherwise generate default value
                    if (paramDef.containsKey("example")) {
                        example.put(paramName, paramDef.get("example"));
                    } else {
                        String type = (String) paramDef.get("type");
                        example.put(paramName, generateDefaultValueForType(type));
                    }
                }
                return example;
            }
        }
        return null;
    }

    /**
     * Generate default value based on type.
     */
    private Object generateDefaultValueForType(String type) {
        if ("string".equals(type)) {
            return "Example value";
        } else if ("integer".equals(type) || "number".equals(type)) {
            return 0;
        } else if ("boolean".equals(type)) {
            return true;
        } else if ("array".equals(type)) {
            return new ArrayList<>();
        } else if ("object".equals(type)) {
            return new LinkedHashMap<>();
        }
        return null;
    }

    /**
     * Build basic parameter schema, including only parameter names and types, without complex structures
     */
    private Map<String, Object> buildBasicSchema(ToolDetail detail) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (ToolParamSpec param : detail.getParams()) {
            Map<String, Object> prop = new LinkedHashMap<>();
            String jsonType = mapJsonType(param.getType());
            prop.put("type", jsonType);

            // Provide only basic description, without complex structures and examples
            String description = param.getDescription();
            if (description != null) {
                prop.put("description", description);
            }

            // For complex objects, add hint information but do not expand structure
            if ("object".equals(jsonType) && isComplexObjectType(param.getType())) {
                String hint = (description != null ? description : "") +
                    " (Complex object, use autoai.tool_detail to get detailed structure)";
                prop.put("description", hint.trim());
            } else {
                // For simple types, provide examples
                if (param.getExample() != null) {
                    prop.put("example", param.getExample());
                }
            }

            properties.put(param.getName(), prop);
            if (param.isRequired()) {
                required.add(param.getName());
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }
    
    /**
     * Determine if it is a complex object type
     */
    private boolean isComplexObjectType(String typeName) {
        if (typeName == null) return false;

        // Basic types and common types are not complex objects
        String lowerType = typeName.toLowerCase();
        if (lowerType.contains("string") || lowerType.contains("int") ||
            lowerType.contains("double") || lowerType.contains("boolean") ||
            lowerType.contains("long") || lowerType.contains("float")) {
            return false;
        }

        // Map and List are not complex objects (though they may contain complex content)
        if (lowerType.contains("map") || lowerType.contains("list") ||
            lowerType.contains("collection") || lowerType.contains("set")) {
            return false;
        }

        // Custom classes are usually complex objects
        return typeName.contains(".") && !typeName.startsWith("java.lang");
    }
    /**
     * Map tool parameters to complete JSON Schema structure (for tool detail retrieval).
     */
    private Map<String, Object> buildJsonSchema(ToolDetail detail) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (ToolParamSpec param : detail.getParams()) {
            Map<String, Object> prop = buildPropertySchema(param);
            properties.put(param.getName(), prop);
            if (param.isRequired()) {
                required.add(param.getName());
            }
        }
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    /**
     * Generate example object from expanded properties
     */
    private Object generateExampleFromProperties(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return null;
        }

        Map<String, Object> example = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> propValue = (Map<String, Object>) entry.getValue();

            // If the property itself has example, use it
            if (propValue.containsKey("example")) {
                example.put(key, propValue.get("example"));
            } else {
                // Otherwise generate default example based on type
                String type = (String) propValue.get("type");
                if ("string".equals(type)) {
                    example.put(key, "Example text");
                } else if ("integer".equals(type) || "number".equals(type)) {
                    example.put(key, 1);
                } else if ("boolean".equals(type)) {
                    example.put(key, true);
                } else if ("array".equals(type)) {
                    // For arrays, try to get element example from items
                    if (propValue.containsKey("items")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> itemsDef = (Map<String, Object>) propValue.get("items");
                        if (itemsDef.containsKey("example")) {
                            example.put(key, List.of(itemsDef.get("example")));
                        } else {
                            example.put(key, List.of("Example element"));
                        }
                    } else {
                        example.put(key, List.of("Example element"));
                    }
                } else if ("object".equals(type)) {
                    // For nested objects, recursively generate example
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nestedProps = (Map<String, Object>) propValue.get("properties");
                    Object nestedExample = generateExampleFromProperties(nestedProps);
                    example.put(key, nestedExample != null ? nestedExample : "Complex object example");
                } else {
                    example.put(key, "Example value");
                }
            }
        }

        return example;
    }
    
    /**
     * Build JSON Schema property for a single parameter
     */
    private Map<String, Object> buildPropertySchema(ToolParamSpec param) {
        Map<String, Object> prop = new LinkedHashMap<>();
        String jsonType = mapJsonType(param.getType());

        System.err.println("DEBUG buildPropertySchema START: paramType=" + param.getType() + ", jsonType=" + jsonType);

        prop.put("type", jsonType);

        // Add parameter source information (for REST API tools)
        if (param.getParamSource() != null) {
            String paramSourceStr = param.getParamSource().name().toLowerCase();
            prop.put("paramSource", paramSourceStr);

            // Append parameter source description to the description
            String originalDesc = param.getDescription();
            String enhancedDesc = originalDesc;
            switch (param.getParamSource()) {
                case PATH_VARIABLE:
                    enhancedDesc = (originalDesc != null ? originalDesc + " " : "") + "[URL path parameter]";
                    break;
                case REQUEST_PARAM:
                    enhancedDesc = (originalDesc != null ? originalDesc + " " : "") + "[URL query parameter]";
                    break;
                case REQUEST_BODY:
                    enhancedDesc = (originalDesc != null ? originalDesc + " " : "") + "[Request body parameter]";
                    break;
                case OTHER:
                    // Do not add marker
                    break;
            }
            prop.put("description", enhancedDesc);
        } else if (param.getDescription() != null) {
            prop.put("description", param.getDescription());
        }

        // If it is a complex object type, try to expand its field structure
        boolean isObject = "object".equals(jsonType);
        boolean hasMap = param.getType().contains("Map");
        boolean hasList = param.getType().contains("List");
        boolean shouldExpand = isObject && !hasMap && !hasList;

        System.err.println("DEBUG buildPropertySchema: isObject=" + isObject + ", hasMap=" + hasMap + ", hasList=" + hasList + ", shouldExpand=" + shouldExpand);

        if (shouldExpand) {
            try {
                // Try to load class and parse its fields
                Class<?> paramClass = loadClassFromTypeName(param.getType());

                System.out.println("DEBUG buildPropertySchema: paramType=" + param.getType() + ", jsonType=" + jsonType + ", loadedClass=" + (paramClass != null ? paramClass.getName() : "null"));

                if (paramClass != null) {
                    Map<String, Object> objectProperties = buildObjectProperties(paramClass);
                    System.out.println("DEBUG buildPropertySchema: objectProperties.size=" + objectProperties.size() + ", keys=" + objectProperties.keySet());

                    if (!objectProperties.isEmpty()) {
                        prop.put("properties", objectProperties);
                        // Generate example from expanded properties (instead of using ExampleGenerator)
                        Object example = generateExampleFromProperties(objectProperties);
                        if (example != null) {
                            prop.put("example", example);
                        }
                    }
                } else {
                    System.out.println("DEBUG buildPropertySchema: Failed to load class for type: " + param.getType());
                }
            } catch (Exception e) {
                System.out.println("DEBUG buildPropertySchema: Exception - " + e.getMessage());
                e.printStackTrace();
                // If parsing fails, use the original example
                if (param.getExample() != null) {
                    prop.put("example", param.getExample());
                }
            }
        } else {
            // Non-complex object, need to parse example string to actual value
            if (param.getExample() != null) {
                try {
                    // Try to parse example string to actual object
                    // For simple types (numbers, boolean, null), this returns correct type
                    // For string types, returns string itself
                    Object parsedExample = objectMapper.readValue(param.getExample(), Object.class);
                    prop.put("example", parsedExample);
                } catch (Exception e) {
                    // If parsing fails, use original string
                    prop.put("example", param.getExample());
                }
            }
        }

        return prop;
    }
    
    /**
     * Build object property structure, supports reading field descriptions from @AutoAiField annotation, recursively handles nested objects
     */
    private Map<String, Object> buildObjectProperties(Class<?> objectClass) {
        return buildObjectPropertiesRecursive(objectClass, 0, 3); // Default maximum recursion depth of 3
    }

    /**
     * Recursively build object property structure
     * @param objectClass The class to parse
     * @param currentDepth Current recursion depth
     * @param maxDepth Maximum recursion depth
     */
    private Map<String, Object> buildObjectPropertiesRecursive(Class<?> objectClass, int currentDepth, int maxDepth) {
        Map<String, Object> properties = new LinkedHashMap<>();

        // Prevent infinite recursion
        if (currentDepth >= maxDepth) {
            properties.put("_note", "Nesting level too deep, expansion stopped");
            return properties;
        }

        try {
            // First try to infer fields from getter methods (for private fields)
            Map<String, FieldInfo> fieldInfoMap = new LinkedHashMap<>();
            java.lang.reflect.Method[] methods = objectClass.getMethods();
            for (java.lang.reflect.Method method : methods) {
                String methodName = method.getName();

                // Only process getXxx() methods, exclude getClass() and other Object class methods
                if (methodName.startsWith("get") && methodName.length() > 3 &&
                    method.getParameterCount() == 0 &&
                    !methodName.equals("getClass")) {

                    String fieldName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);

                    // Try to find field
                    java.lang.reflect.Field field = findField(objectClass, fieldName);

                    // Add field info: even if field is not found, add if there's a getter method
                    // Condition: return type is not Class (exclude getClass())
                    if (!method.getReturnType().equals(Class.class)) {
                        fieldInfoMap.put(fieldName, new FieldInfo(field, method));
                    }
                }
            }

            // If no getter found, try to get fields directly (including private fields)
            if (fieldInfoMap.isEmpty()) {
                try {
                    java.lang.reflect.Field[] declaredFields = objectClass.getDeclaredFields();
                    for (java.lang.reflect.Field field : declaredFields) {
                        if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                            continue;
                        }
                        fieldInfoMap.put(field.getName(), new FieldInfo(field, null));
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }

            // If still no fields, try public fields
            if (fieldInfoMap.isEmpty()) {
                java.lang.reflect.Field[] fields = objectClass.getFields();
                for (java.lang.reflect.Field field : fields) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    fieldInfoMap.put(field.getName(), new FieldInfo(field, null));
                }
            }

            // Build property definition
            for (Map.Entry<String, FieldInfo> entry : fieldInfoMap.entrySet()) {
                String fieldName = entry.getKey();
                FieldInfo fieldInfo = entry.getValue();
                java.lang.reflect.Field field = fieldInfo.field;
                java.lang.reflect.Method getter = fieldInfo.getter;

                // Get field type
                Class<?> fieldType = field != null ? field.getType() :
                    (getter != null ? getter.getReturnType() : Object.class);

                String fieldTypeSimpleName = fieldType.getSimpleName();
                String jsonType = mapJsonType(fieldType.getName());

                Map<String, Object> fieldProp = new LinkedHashMap<>();
                fieldProp.put("type", jsonType);

                // Try to get description information from @AutoAiField annotation
                cn.autoai.core.annotation.AutoAiField fieldAnnotation = null;
                if (field != null) {
                    fieldAnnotation = field.getAnnotation(cn.autoai.core.annotation.AutoAiField.class);
                }

                if (fieldAnnotation != null) {
                    // Only add description when annotation has it (avoid redundancy)
                    String description = fieldAnnotation.description();
                    if (description != null && !description.trim().isEmpty()) {
                        fieldProp.put("description", description);
                    }
                    // If no description, do not add description field, as field name and type already provide enough information

                    // Add example value
                    String example = fieldAnnotation.example();
                    if (example != null && !example.trim().isEmpty()) {
                        fieldProp.put("example", example);
                    }

                    // Add required marker
                    if (fieldAnnotation.required()) {
                        fieldProp.put("required", true);
                    }
                }
                // Do not add description when no annotation, avoid redundant information

                // If it is a complex object type, recursively expand its properties
                if ("object".equals(jsonType) && isComplexObjectType(fieldType.getName())) {
                    try {
                        Map<String, Object> nestedProperties = buildObjectPropertiesRecursive(fieldType, currentDepth + 1, maxDepth);
                        if (!nestedProperties.isEmpty()) {
                            fieldProp.put("properties", nestedProperties);
                            // If no example obtained from annotation, try to generate nested object example
                            if (fieldAnnotation == null || fieldAnnotation.example() == null || fieldAnnotation.example().trim().isEmpty()) {
                                Object nestedExample = ExampleGenerator.exampleValue(fieldType, fieldType.getName());
                                if (nestedExample != null) {
                                    fieldProp.put("example", nestedExample);
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Recursive expansion failed, keep basic properties
                    }
                } else if (jsonType.equals("array") && java.util.Collection.class.isAssignableFrom(fieldType)) {
                    // Handle List/Collection types, try to get element type from generic
                    // Note: Must check Collection first, then check primitive array, because Collection also satisfies !fieldType.isPrimitive()
                    try {
                        java.lang.reflect.Type genericType = null;
                        if (field != null) {
                            genericType = field.getGenericType();
                        } else if (getter != null) {
                            genericType = getter.getGenericReturnType();
                        }

                        if (genericType instanceof java.lang.reflect.ParameterizedType) {
                            java.lang.reflect.ParameterizedType pType = (java.lang.reflect.ParameterizedType) genericType;
                            java.lang.reflect.Type[] actualTypeArgs = pType.getActualTypeArguments();
                            if (actualTypeArgs.length > 0) {
                                java.lang.reflect.Type elementType = actualTypeArgs[0];

                                // Try to load element type class (handle inner classes etc.)
                                Class<?> elementClass = null;
                                if (elementType instanceof Class) {
                                    elementClass = (Class<?>) elementType;
                                } else {
                                    // If not Class, try to load from type name
                                    String elementTypeName = elementType.getTypeName();
                                    elementClass = loadClassFromTypeName(elementTypeName);
                                }

                                if (elementClass != null) {
                                    String elementJsonType = mapJsonType(elementClass.getName());
                                    Map<String, Object> itemsDef = new LinkedHashMap<>();
                                    itemsDef.put("type", elementJsonType);

                                    // If element is a complex object, recursively expand its properties
                                    if ("object".equals(elementJsonType) && isComplexObjectType(elementClass.getName())) {
                                        Map<String, Object> itemProperties = buildObjectPropertiesRecursive(elementClass, currentDepth + 1, maxDepth);
                                        if (!itemProperties.isEmpty()) {
                                            itemsDef.put("properties", itemProperties);
                                        }
                                    }

                                    // Generate element example
                                    Object elementExample = ExampleGenerator.exampleValue(elementClass, elementClass.getName());
                                    if (elementExample != null) {
                                        itemsDef.put("example", elementExample);
                                    }

                                    fieldProp.put("items", itemsDef);

                                    // Generate example for entire list
                                    if (fieldAnnotation == null || fieldAnnotation.example() == null || fieldAnnotation.example().trim().isEmpty()) {
                                        if (elementExample != null) {
                                            fieldProp.put("example", List.of(elementExample));
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Generic parsing failed, keep basic properties
                    }
                } else if (jsonType.equals("array") && !fieldType.isPrimitive()) {
                    // Handle primitive array types, try to get element type and expand
                    try {
                        Class<?> componentType = fieldType.getComponentType();
                        if (componentType != null && "object".equals(mapJsonType(componentType.getName())) &&
                            isComplexObjectType(componentType.getName())) {
                            Map<String, Object> itemProperties = buildObjectPropertiesRecursive(componentType, currentDepth + 1, maxDepth);
                            if (!itemProperties.isEmpty()) {
                                fieldProp.put("items", Map.of("type", "object", "properties", itemProperties));
                            }
                        }
                    } catch (Exception e) {
                        // Array element type parsing failed, keep basic properties
                    }
                }

                properties.put(fieldName, fieldProp);
            }
        } catch (Exception e) {
            // Return empty on parsing failure
        }

        return properties;
    }

    /**
     * Find field in class (including parent class)
     */
    private java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null) {
                return findField(superClass, fieldName);
            }
            return null;
        }
    }

    /**
     * Field information wrapper class
     */
    private static class FieldInfo {
        final java.lang.reflect.Field field;
        final java.lang.reflect.Method getter;

        FieldInfo(java.lang.reflect.Field field, java.lang.reflect.Method getter) {
            this.field = field;
            this.getter = getter;
        }
    }
    
    /**
     * Load Class object from type name
     */
    private Class<?> loadClassFromTypeName(String typeName) {
        try {
            // Handle generic type names
            String className = typeName;
            if (className.contains("<")) {
                className = className.substring(0, className.indexOf("<"));
            }

            try {
                return Class.forName(className);
            } catch (ClassNotFoundException e) {
                // If it's an inner class (contains $), try to find in current class loader
                if (className.contains("$")) {
                    try {
                        // Try to load directly
                        return Class.forName(className, false, Thread.currentThread().getContextClassLoader());
                    } catch (ClassNotFoundException e2) {
                        // Try using . separator
                        String dottedName = className.replace('$', '.');
                        return Class.forName(dottedName);
                    }
                }
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Map Java types to JSON Schema types.
     */
    private String mapJsonType(String javaType) {
        if (javaType == null) {
            return "string";
        }
        String normalized = javaType.toLowerCase(Locale.ROOT);

        // Handle collection types
        if (normalized.contains("list") || normalized.contains("collection") ||
            normalized.contains("set") || normalized.contains("array") || normalized.contains("[]")) {
            return "array";
        }

        if (normalized.contains("int") || normalized.contains("long") || normalized.contains("short")) {
            return "integer";
        }
        if (normalized.contains("double") || normalized.contains("float") || normalized.contains("bigdecimal")) {
            return "number";
        }
        if (normalized.contains("boolean")) {
            return "boolean";
        }
        if (normalized.contains("string") || normalized.contains("char")) {
            return "string";
        }
        return "object";
    }

    /**
     * Output list of currently available tool names.
     */
    private List<String> listToolNames() {
        List<String> names = new ArrayList<>();
        for (ToolSummary summary : toolRegistry.listSummaries()) {
            names.add(summary.getName());
        }
        names.add(TOOL_DETAIL_NAME);
        return names;
    }

    /**
     * Thought/action parsing result.
     */
    private static class ThoughtAction {
        private final String thought;
        private final String action;

        private ThoughtAction(String thought, String action) {
            this.thought = thought;
            this.action = action;
        }

        public String getThought() {
            return thought;
        }

        public String getAction() {
            return action;
        }
    }

    /**
     * Tool call parsing result.
     */
    private static class ActionCall {
        private final String toolName;
        private final String rawArgs;
        private final List<Object> args;

        private ActionCall(String toolName, String rawArgs, List<Object> args) {
            this.toolName = toolName;
            this.rawArgs = rawArgs;
            this.args = args;
        }

        public String getToolName() {
            return toolName;
        }

        public String getRawArgs() {
            return rawArgs;
        }

        public List<Object> getArgs() {
            return args;
        }
    }

    /**
     * System prompt cache, avoid rebuilding for each conversation.
     */
    private static class SystemPromptCache {
        private final String detailedPrompt;  // System prompt in detailed mode
        private final String simplePrompt;    // System prompt in simple mode
        private final int cacheVersion;       // Cache version number

        SystemPromptCache(String detailedPrompt, String simplePrompt, int cacheVersion) {
            this.detailedPrompt = detailedPrompt;
            this.simplePrompt = simplePrompt;
            this.cacheVersion = cacheVersion;
        }

        String getPrompt(boolean detailed) {
            return detailed ? detailedPrompt : simplePrompt;
        }

        int getCacheVersion() {
            return cacheVersion;
        }
    }

    /**
     * Calculate version number for tool list and settings, used for cache invalidation judgment.
     */
    private int computeCacheVersion(List<ToolSpec> toolSpecs) {
        // Calculate simple hash using tool count and tool name list
        StringBuilder sb = new StringBuilder();
        for (ToolSpec spec : toolSpecs) {
            if (spec.getFunction() != null) {
                sb.append(spec.getFunction().getName());
            }
        }
        sb.append(settings.getSystemPrompt());
        sb.append(settings.getLanguage());

        return sb.toString().hashCode();
    }

    /**
     * Clear system prompt cache (called when tool list changes).
     */
    public void clearPromptCache() {
        promptCache = null;
    }

    /**
     * Check and compress session history (based on token count)
     */
    private void checkAndCompressSession(List<ChatMessage> session, String sessionId) {
        // Calculate total tokens for current session (excluding system messages)
        int totalTokens = calculateSessionTokens(session);

        // If token count exceeds threshold, execute compression
        if (totalTokens >= settings.getCompressionThresholdTokens()) {
            System.out.println("Session token count (" + totalTokens + ") exceeds threshold (" +
                settings.getCompressionThresholdTokens() + "), starting compression...");
            compressSessionHistory(session, sessionId);
        }
    }

    /**
     * Compress session history (based on token count)
     * Strategy: Keep recent N token messages, compress earlier messages into summary
     */
    private void compressSessionHistory(List<ChatMessage> session, String sessionId) {
        if (session == null || session.isEmpty()) {
            return;
        }

        try {
            // 1. Separate system messages and dialog messages
            List<ChatMessage> systemMessages = new ArrayList<>();
            List<ChatMessage> dialogMessages = new ArrayList<>();

            for (ChatMessage msg : session) {
                if ("system".equals(msg.getRole())) {
                    systemMessages.add(msg);
                } else {
                    dialogMessages.add(msg);
                }
            }

            // 2. Calculate total tokens for dialog messages
            int dialogTokens = calculateMessagesTokens(dialogMessages);

            // 3. If token count is not enough, no need to compress
            if (dialogTokens <= settings.getKeepRecentTokens() * 1.5) {
                return;
            }

            // 4. Keep messages from back to front until reaching keepRecentTokens threshold
            List<ChatMessage> recentMessages = new ArrayList<>();
            List<ChatMessage> oldMessages = new ArrayList<>();
            int accumulatedTokens = 0;

            // Traverse from latest message forward
            for (int i = dialogMessages.size() - 1; i >= 0; i--) {
                ChatMessage msg = dialogMessages.get(i);
                int msgTokens = estimateTokens(msg.getContent());

                if (accumulatedTokens + msgTokens <= settings.getKeepRecentTokens()) {
                    recentMessages.add(0, msg); // Add to beginning of list
                    accumulatedTokens += msgTokens;
                } else {
                    // Remaining messages need compression
                    oldMessages = dialogMessages.subList(0, i);
                    break;
                }
            }

            // 5. Call AI model to generate compression summary
            String summary = generateCompressionSummary(oldMessages);

            if (summary != null && !summary.isBlank()) {
                // 6. Rebuild session: system messages + compressed summary + recent messages
                List<ChatMessage> compressedSession = new ArrayList<>();

                // Add system messages
                compressedSession.addAll(systemMessages);

                // Add compressed summary as assistant message
                ChatMessage summaryMessage = ChatMessage.assistant(
                    "[Dialog History Summary]\n" + summary + "\n[End of Summary]"
                );
                compressedSession.add(summaryMessage);

                // Add recent messages
                compressedSession.addAll(recentMessages);

                // 7. Calculate token count after compression
                int compressedTokens = calculateSessionTokens(compressedSession);

                // 8. Update session storage
                saveSession(sessionId, compressedSession);
                session.clear();
                session.addAll(compressedSession);

                System.out.println("Session history compressed: sessionId=" + sessionId +
                    ", original token count=" + dialogTokens +
                    ", compressed token count=" + compressedTokens +
                    ", compression ratio=" + String.format("%.1f%%", (1 - (double)compressedTokens / dialogTokens) * 100));
            }
        } catch (Exception e) {
            // Compression failure does not affect normal flow
            System.err.println("Dialog history compression failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Calculate total token count for session (excluding system messages)
     */
    private int calculateSessionTokens(List<ChatMessage> session) {
        int totalTokens = 0;
        for (ChatMessage msg : session) {
            if (!"system".equals(msg.getRole())) {
                totalTokens += estimateTokens(msg.getContent());
            }
        }
        return totalTokens;
    }

    /**
     * Calculate total token count for message list
     */
    private int calculateMessagesTokens(List<ChatMessage> messages) {
        int totalTokens = 0;
        for (ChatMessage msg : messages) {
            totalTokens += estimateTokens(msg.getContent());
        }
        return totalTokens;
    }

    /**
     * Estimate token count of text
     * Strategy:
     * - Chinese: approximately 1.5 characters = 1 token
     * - English: approximately 4 characters = 1 token
     * - Mixed text: dynamic calculation
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int chineseChars = 0;
        int englishChars = 0;
        int otherChars = 0;

        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) { // Chinese character range
                chineseChars++;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                englishChars++;
            } else if (c == ' ') {
                englishChars++; // Spaces count as English
            } else {
                otherChars++;
            }
        }

        // Chinese: 1.5 characters = 1 token, English: 4 characters = 1 token, others calculated as Chinese
        int chineseTokens = (int) Math.ceil((chineseChars + otherChars) / 1.5);
        int englishTokens = (int) Math.ceil(englishChars / 4.0);

        return chineseTokens + englishTokens;
    }

    /**
     * Use AI model to generate compressed summary of dialog history
     */
    private String generateCompressionSummary(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        try {
            // Build compression prompt
            String compressionPrompt = buildCompressionPrompt();

            // Build dialog text to be compressed
            StringBuilder dialogText = new StringBuilder();
            for (ChatMessage msg : messages) {
                String role = msg.getRole();
                String content = msg.getContent();

                if ("user".equals(role)) {
                    dialogText.append("User: ").append(content).append("\n");
                } else if ("assistant".equals(role)) {
                    // Simplify assistant output, keep only key information
                    String simplified = simplifyAssistantMessage(content);
                    dialogText.append("Assistant: ").append(simplified).append("\n");
                }
            }

            // Build complete compression request
            ChatCompletionRequest compressionRequest = new ChatCompletionRequest();
            compressionRequest.setModel(resolveModel(null).getName());

            List<ChatMessage> compressionMessages = new ArrayList<>();
            compressionMessages.add(ChatMessage.system(compressionPrompt));
            compressionMessages.add(ChatMessage.user(
                "Please compress the following dialog history and extract key information:\n\n" + dialogText.toString()
            ));
            compressionRequest.setMessages(compressionMessages);
            compressionRequest.setTemperature(0.3); // Use lower temperature for stability
            compressionRequest.setMaxTokens(1000); // Limit summary length

            // Call model to generate summary
            AutoAiModel model = resolveModel(null);
            ChatCompletionResponse response = model.chat(compressionRequest);

            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                ChatMessage result = response.getChoices().get(0).getMessage();
                return result != null ? result.getContent() : null;
            }
        } catch (Exception e) {
            System.err.println("Failed to generate compression summary: " + e.getMessage());
        }

        return null;
    }

    /**
     * Build compression prompt
     */
    private String buildCompressionPrompt() {
        
            return """
                You are a conversation summarization assistant, responsible for compressing conversation history while preserving key information.

                Requirements:
                1. Extract the user's main questions and requirements
                2. Summarize the assistant's important responses and actions taken
                3. Retain key data and decision points
                4. Remove redundant conversational content
                5. Output a concise summary in paragraph format
                6. Do not include procedural details or tool calls

                Format: Provide a summary that helps continue the conversation seamlessly.
                """;
        
    }

    /**
     * Simplify assistant message, keep only key information
     */
    private String simplifyAssistantMessage(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        StringBuilder simplified = new StringBuilder();
        String[] lines = content.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();

            // Skip process markers
            if (trimmed.startsWith("THINK:") || trimmed.startsWith("ACTION:") ||
                trimmed.startsWith("OBSERVE:") || trimmed.startsWith("Thinking") ||
                trimmed.startsWith("Action") || trimmed.startsWith("Observation")) {
                continue;
            }

            // Keep only key results
            if (trimmed.startsWith("ANSWER:") || trimmed.startsWith("✅") ||
                trimmed.startsWith("Final Answer") || trimmed.startsWith("Success")) {
                simplified.append(trimmed).append("\n");
            } else if (!trimmed.startsWith("THINK") && !trimmed.startsWith("ACTION") &&
                       !trimmed.startsWith("OBSERVE")) {
                // Keep other important content
                if (trimmed.length() > 0) {
                    simplified.append(trimmed).append("\n");
                }
            }
        }

        return simplified.toString().trim();
    }
}
