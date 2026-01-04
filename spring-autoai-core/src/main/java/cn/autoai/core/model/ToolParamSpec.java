package cn.autoai.core.model;

import cn.autoai.core.registry.ToolParamBinding;

/**
 * Tool parameter description information.
 */
public class ToolParamSpec {
    private String name;
    private String type;
    private String description;
    private boolean required;
    private String example;
    private ToolParamBinding.ParamSource paramSource;

    public ToolParamSpec() {
    }

    public ToolParamSpec(String name, String type, String description, boolean required, String example) {
        this(name, type, description, required, example, ToolParamBinding.ParamSource.OTHER);
    }

    public ToolParamSpec(String name, String type, String description, boolean required, String example,
                         ToolParamBinding.ParamSource paramSource) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.required = required;
        this.example = example;
        this.paramSource = paramSource;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getExample() {
        return example;
    }

    public void setExample(String example) {
        this.example = example;
    }

    public ToolParamBinding.ParamSource getParamSource() {
        return paramSource;
    }

    public void setParamSource(ToolParamBinding.ParamSource paramSource) {
        this.paramSource = paramSource;
    }
}
