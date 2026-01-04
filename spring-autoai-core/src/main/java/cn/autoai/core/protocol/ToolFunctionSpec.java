package cn.autoai.core.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * OpenAI compatible function tool description.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolFunctionSpec {
    private String name;
    private String description;
    private Object parameters;

    public ToolFunctionSpec() {
    }

    public ToolFunctionSpec(String name, String description, Object parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Object getParameters() {
        return parameters;
    }

    public void setParameters(Object parameters) {
        this.parameters = parameters;
    }
}
