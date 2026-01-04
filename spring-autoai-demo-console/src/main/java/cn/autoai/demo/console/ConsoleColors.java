package cn.autoai.demo.console;

/**
 * Console color utility class
 */
public class ConsoleColors {
    // ANSI color codes
    public static final String RESET = "\033[0m";  // Reset color

    // Foreground colors
    public static final String BLACK = "\033[30m";
    public static final String RED = "\033[31m";
    public static final String GREEN = "\033[32m";
    public static final String YELLOW = "\033[33m";
    public static final String BLUE = "\033[34m";
    public static final String PURPLE = "\033[35m";
    public static final String CYAN = "\033[36m";
    public static final String WHITE = "\033[37m";

    // Bright colors
    public static final String BRIGHT_BLACK = "\033[90m";
    public static final String BRIGHT_RED = "\033[91m";
    public static final String BRIGHT_GREEN = "\033[92m";
    public static final String BRIGHT_YELLOW = "\033[93m";
    public static final String BRIGHT_BLUE = "\033[94m";
    public static final String BRIGHT_PURPLE = "\033[95m";
    public static final String BRIGHT_CYAN = "\033[96m";
    public static final String BRIGHT_WHITE = "\033[97m";

    // Background colors
    public static final String BG_BLACK = "\033[40m";
    public static final String BG_RED = "\033[41m";
    public static final String BG_GREEN = "\033[42m";
    public static final String BG_YELLOW = "\033[43m";
    public static final String BG_BLUE = "\033[44m";
    public static final String BG_PURPLE = "\033[45m";
    public static final String BG_CYAN = "\033[46m";
    public static final String BG_WHITE = "\033[47m";

    // Styles
    public static final String BOLD = "\033[1m";
    public static final String DIM = "\033[2m";
    public static final String ITALIC = "\033[3m";
    public static final String UNDERLINE = "\033[4m";

    /**
     * Check if color output is supported
     */
    public static boolean isColorSupported() {
        String term = System.getenv("TERM");
        String colorTerm = System.getenv("COLORTERM");
        return (term != null && !term.equals("dumb")) ||
               (colorTerm != null && !colorTerm.isEmpty());
    }

    /**
     * Wrap text with specified color
     */
    public static String colorize(String text, String color) {
        if (!isColorSupported()) {
            return text;
        }
        return color + text + RESET;
    }

    /**
     * User input color (blue)
     */
    public static String user(String text) {
        return colorize(text, BRIGHT_BLUE);
    }

    /**
     * AI response color (green)
     */
    public static String ai(String text) {
        return colorize(text, BRIGHT_GREEN);
    }

    /**
     * System information color (yellow)
     */
    public static String system(String text) {
        return colorize(text, BRIGHT_YELLOW);
    }

    /**
     * Error message color (red)
     */
    public static String error(String text) {
        return colorize(text, BRIGHT_RED);
    }

    /**
     * Debug information color (gray)
     */
    public static String debug(String text) {
        return colorize(text, BRIGHT_BLACK);
    }

    /**
     * Emphasize text (bold)
     */
    public static String bold(String text) {
        if (!isColorSupported()) {
            return text;
        }
        return BOLD + text + RESET;
    }
}