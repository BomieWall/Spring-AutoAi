package cn.autoai.core.llm;

import cn.autoai.core.protocol.ChatCompletionChoice;
import cn.autoai.core.protocol.ChatCompletionRequest;
import cn.autoai.core.protocol.ChatCompletionResponse;
import cn.autoai.core.protocol.ChatMessage;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-driven demo model, used for demonstration only.
 */
public class RuleBasedDemoModel implements AutoAiModel {
    private static final Pattern ADD_PATTERN = Pattern.compile("(\\d+)\\s*\\+\\s*(\\d+)");

    private final String name;

    public RuleBasedDemoModel(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ChatCompletionResponse chat(ChatCompletionRequest request) {
        ChatMessage last = lastMessage(request);
        if (last != null && last.getContent() != null) {
            String content = last.getContent();
            if (content.startsWith("Observation:")) {
                String observation = content.replaceFirst("Observation:\\s*", "").trim();
                return buildResponse(request, "Thought: Got observation result\nAnswer: " + observation, "stop");
            }

            Matcher matcher = ADD_PATTERN.matcher(content);
            if (matcher.find()) {
                String a = matcher.group(1);
                String b = matcher.group(2);
                String action = String.format("Thought: Need to call tool to complete calculation\\nAction: demo.add(%s, %s)", a, b);
                return buildResponse(request, action, "stop");
            }

            return buildResponse(request, "Thought: Received input\nAnswer: Received: " + content, "stop");
        }

        return buildResponse(request, "Thought: No input\nAnswer: None", "stop");
    }

    private ChatMessage lastMessage(ChatCompletionRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return null;
        }
        return request.getMessages().get(request.getMessages().size() - 1);
    }

    private ChatCompletionResponse buildResponse(ChatCompletionRequest request, String content, String finishReason) {
        ChatMessage assistant = ChatMessage.assistant(content);
        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setId("chatcmpl_" + UUID.randomUUID());
        response.setObject("chat.completion");
        response.setCreated(Instant.now().getEpochSecond());
        response.setModel(request.getModel() == null ? name : request.getModel());
        response.addChoice(new ChatCompletionChoice(0, assistant, finishReason));
        return response;
    }
}
