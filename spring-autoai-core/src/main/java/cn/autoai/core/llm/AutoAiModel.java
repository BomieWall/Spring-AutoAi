package cn.autoai.core.llm;

import cn.autoai.core.protocol.ChatCompletionRequest;
import cn.autoai.core.protocol.ChatCompletionResponse;
import cn.autoai.core.protocol.ChatMessage;

/**
 * Unified model interface.
 */
public interface AutoAiModel {
    /**
     * Return model name, used for external exposure and selection.
     */
    String getName();

    /**
     * Execute a conversation request, return OpenAI compatible response structure.
     */
    ChatCompletionResponse chat(ChatCompletionRequest request);

    /**
     * Execute conversation request in streaming mode, defaults to non-streaming call.
     */
    default ChatCompletionResponse chatStream(ChatCompletionRequest request, StreamCallback callback) {
        if (request != null) {
            request.setStream(true);
        }
        ChatCompletionResponse response = chat(request);
        if (callback != null && response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
            ChatMessage message = response.getChoices().get(0).getMessage();
            if (message != null && message.getContent() != null) {
                callback.onChunk(message.getContent());
            }
        }
        return response;
    }
}
