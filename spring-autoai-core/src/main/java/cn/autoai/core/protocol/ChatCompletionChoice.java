package cn.autoai.core.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenAI compatible chat completion choice.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionChoice {
    private Integer index;
    private ChatMessage message;

    @JsonProperty("finish_reason")
    private String finishReason;

    public ChatCompletionChoice() {
    }

    public ChatCompletionChoice(Integer index, ChatMessage message, String finishReason) {
        this.index = index;
        this.message = message;
        this.finishReason = finishReason;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public ChatMessage getMessage() {
        return message;
    }

    public void setMessage(ChatMessage message) {
        this.message = message;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }
}
