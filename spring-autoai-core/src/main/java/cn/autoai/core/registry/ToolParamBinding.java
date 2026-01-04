package cn.autoai.core.registry;

/**
 * Binding between method parameters and tool parameter names.
 */
public class ToolParamBinding {
    private final String name;
    private final Class<?> type;
    private final int index;
    private final boolean required;
    private final ParamSource paramSource;

    /**
     * Parameter source type.
     */
    public enum ParamSource {
        /**
         * Request body parameter (@RequestBody)
         */
        REQUEST_BODY,
        /**
         * Path parameter (@PathVariable)
         */
        PATH_VARIABLE,
        /**
         * Query parameter (@RequestParam)
         */
        REQUEST_PARAM,
        /**
         * Other type parameter
         */
        OTHER
    }

    public ToolParamBinding(String name, Class<?> type, int index, boolean required) {
        this(name, type, index, required, ParamSource.OTHER);
    }

    public ToolParamBinding(String name, Class<?> type, int index, boolean required, ParamSource paramSource) {
        this.name = name;
        this.type = type;
        this.index = index;
        this.required = required;
        this.paramSource = paramSource;
    }

    public String getName() {
        return name;
    }

    public Class<?> getType() {
        return type;
    }

    public int getIndex() {
        return index;
    }

    public boolean isRequired() {
        return required;
    }

    public ParamSource getParamSource() {
        return paramSource;
    }
}
