package cn.autoai.core.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * OpenAI compatible tool call structure.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCall {
    private String id;
    private String type;
    private ToolCallFunction function;

    public ToolCall() {
    }

    public ToolCall(String id, String type, ToolCallFunction function) {
        this.id = id;
        this.type = type;
        this.function = function;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public ToolCallFunction getFunction() {
        return function;
    }

    public void setFunction(ToolCallFunction function) {
        this.function = function;
    }
}
