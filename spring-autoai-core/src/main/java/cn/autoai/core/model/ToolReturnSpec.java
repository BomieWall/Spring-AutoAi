package cn.autoai.core.model;

/**
 * Tool return value description information.
 */
public class ToolReturnSpec {
    private String type;
    private String description;
    private String example;

    public ToolReturnSpec() {
    }

    public ToolReturnSpec(String type, String description, String example) {
        this.type = type;
        this.description = description;
        this.example = example;
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

    public String getExample() {
        return example;
    }

    public void setExample(String example) {
        this.example = example;
    }
}
