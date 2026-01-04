package cn.autoai.core.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI compatible chat response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionResponse {
    private String id;
    private String object;
    private Long created;
    private String model;
    private List<ChatCompletionChoice> choices = new ArrayList<>();
    private ChatUsage usage;

    public ChatCompletionResponse() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public Long getCreated() {
        return created;
    }

    public void setCreated(Long created) {
        this.created = created;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<ChatCompletionChoice> getChoices() {
        return choices;
    }

    public void setChoices(List<ChatCompletionChoice> choices) {
        this.choices = choices;
    }

    public ChatUsage getUsage() {
        return usage;
    }

    public void setUsage(ChatUsage usage) {
        this.usage = usage;
    }

    public void addChoice(ChatCompletionChoice choice) {
        this.choices.add(choice);
    }
}
