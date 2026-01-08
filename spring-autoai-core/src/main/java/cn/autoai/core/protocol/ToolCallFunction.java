package cn.autoai.core.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Function field definition in tool call.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCallFunction {
    private String name;
    private String arguments;
    private String description;

    public ToolCallFunction() {
    }

    public ToolCallFunction(String name, String arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
