package cn.autoai.core.react;

/**
 * ReAct content type enum, used to identify the type of output content
 */
public enum ContentType {
    /**
     * Thinking process
     */
    THINKING("Thinking"),

    /**
     * Reasoning content
     */
    REASONING("Reasoning"),

    /**
     * Execute action
     */
    ACTION("Action"),

    /**
     * Action start - Notification when tool starts execution
     */
    ACTION_START("Action Start"),
    /**
     * Action end - Notification when tool starts execution
     */
    ACTION_END("Action End"),

    /**
     * Observation result
     */
    OBSERVATION("Observation"),

    /**
     * Final answer
     */
    ANSWER("Answer"),

    /**
     * Ask user
     */
    ASK("Ask"),

    /**
     * Regular content
     */
    CONTENT("Content"),

    /**
     * Error message
     */
    ERROR("Error");
    
    private final String displayName;
    
    ContentType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get identifier format for frontend recognition
     */
    public String getMarker() {
        return "[" + displayName + "]";
    }

    /**
     * Parse content type from identifier
     */
    public static ContentType fromMarker(String marker) {
        if (marker == null) {
            return CONTENT;
        }
        
        for (ContentType type : values()) {
            if (type.getMarker().equals(marker)) {
                return type;
            }
        }
        
        return CONTENT;
    }

    /**
     * Parse content type from display name
     */
    public static ContentType fromDisplayName(String displayName) {
        if (displayName == null) {
            return CONTENT;
        }
        
        for (ContentType type : values()) {
            if (type.displayName.equals(displayName)) {
                return type;
            }
        }
        
        return CONTENT;
    }
}