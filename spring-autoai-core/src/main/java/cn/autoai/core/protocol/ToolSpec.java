package cn.autoai.core.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * OpenAI compatible tool description structure.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolSpec {
    private String type;
    private ToolFunctionSpec function;

    public ToolSpec() {
    }

    public ToolSpec(String type, ToolFunctionSpec function) {
        this.type = type;
        this.function = function;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public ToolFunctionSpec getFunction() {
        return function;
    }

    public void setFunction(ToolFunctionSpec function) {
        this.function = function;
    }
}
