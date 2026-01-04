package cn.autoai.demo.console;

import cn.autoai.core.llm.TypedStreamCallback;
import cn.autoai.core.react.ContentType;

public class ColoredStreamCallback implements TypedStreamCallback {

    private ContentType currentType = null;
    private boolean hasContentInCurrentBlock = false;
    private boolean headerPrintedForCurrentType = false;

    @Override
    public void onTypedChunk(ContentType contentType, String content) {
        if (content == null || content.isEmpty()) {
            return;
        }

        String filteredContent = filterInternalTags(content);

        if (contentType != currentType) {
            if (hasContentInCurrentBlock && currentType != null) {
                System.out.println();
            }
            currentType = contentType;
            hasContentInCurrentBlock = false;
            headerPrintedForCurrentType = false;
        }

        // Check if it's pure whitespace content (filters pure newline chunks, but keeps chunks containing text and newlines)
        if (filteredContent == null || filteredContent.trim().isEmpty()) {
            return;
        }

        // Only print block header when there is actual content
        if (!headerPrintedForCurrentType) {
            printBlockHeader(contentType);
            headerPrintedForCurrentType = true;
        }

        System.out.print(filteredContent);
        hasContentInCurrentBlock = true;
    }

    private void printBlockHeader(ContentType type) {
        String icon = getIconForType(type);
        String title = type.getDisplayName();
        String color = getColorForType(type);

        System.out.print(ConsoleColors.RESET);
        System.out.println();
        System.out.print(color);
        System.out.print(ConsoleColors.BOLD);
        System.out.print(icon + " " + title + ": ");
        System.out.print(ConsoleColors.RESET);
    }

    private String getIconForType(ContentType type) {
        switch (type) {
            case THINKING: return "🤔";
            case REASONING: return "🧠";
            case ACTION: return "⚡";
            case OBSERVATION: return "👁️";
            case ANSWER: return "✅";
            case ASK: return "❓";
            case ERROR: return "❌";
            case CONTENT:
            default: return "💬";
        }
    }

    private String getColorForType(ContentType type) {
        switch (type) {
            case THINKING: return ConsoleColors.BRIGHT_CYAN;
            case REASONING: return ConsoleColors.BRIGHT_WHITE;
            case ACTION: return ConsoleColors.BRIGHT_YELLOW;
            case OBSERVATION: return ConsoleColors.BRIGHT_PURPLE;
            case ANSWER: return ConsoleColors.BRIGHT_GREEN;
            case ASK: return ConsoleColors.BRIGHT_BLUE;
            case ERROR: return ConsoleColors.BRIGHT_RED;
            case CONTENT:
            default: return ConsoleColors.RESET;
        }
    }

    private String filterInternalTags(String content) {
        if (content == null) {
            return "";
        }

        // Use regular expressions to filter internal tags
        String result = content;
        result = result.replaceAll("</?(?:think|reasoning|arg_value)>", "");
        // Filter zero-width characters and other special symbols
        result = result.replaceAll("[\\u200B-\\u200D\\u2060\\uFEFF]", "");
        result = result.replaceAll("[♫🎵♪♬]:", "");
        // Filter bracket tags: [Action], [Thinking], [Observation], [Answer], [Ask], etc.
        result = result.replaceAll("\\[(?:Action|Thinking|Observation|Answer|Ask|Content|Reasoning|Error)\\]", "");
        // Filter prefixes like "Execution result:"
        result = result.replaceAll("Execution result:", "");
        // Filter markdown code block markers
        result = result.replaceAll("```", "");

        // Only remove leading/trailing spaces and tabs, preserve newlines
        return result.replaceAll("^[ \\t]+|[ \\t]+$", "");
    }
}
