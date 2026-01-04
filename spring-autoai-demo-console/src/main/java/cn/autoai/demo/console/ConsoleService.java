package cn.autoai.demo.console;

import cn.autoai.core.protocol.ChatCompletionRequest;
import cn.autoai.core.protocol.ChatMessage;
import cn.autoai.core.react.ReActEngine;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Console interaction service.
 * Demonstrates how to use ReActEngine in non-Web projects
 */
@Service
public class ConsoleService {

    private final ReActEngine reActEngine;

    public ConsoleService(ReActEngine reActEngine) {
        this.reActEngine = reActEngine;
    }

    /**
     * Start the console interaction interface
     */
    public void start() throws Exception {
        Terminal terminal = TerminalBuilder.builder()
            .system(true)
            .encoding(java.nio.charset.StandardCharsets.UTF_8)
            .build();

        LineReader reader = LineReaderBuilder.builder()
            .terminal(terminal)
            .build();

        // Generate a fixed session ID to maintain conversation context
        String sessionId = "console_session_" + UUID.randomUUID().toString();

        // Startup message
        System.out.println(ConsoleColors.system("=".repeat(60)));
        System.out.println(ConsoleColors.system(ConsoleColors.bold("🤖 AutoAi Console Demo Started")));
        System.out.println(ConsoleColors.system("Type 'exit' to exit the program, 'clear' to clear conversation history"));
        System.out.println(ConsoleColors.system("Block type description:"));
        System.out.println(ConsoleColors.colorize("  🤔 Thinking Process (THINKING) - Cyan", ConsoleColors.BRIGHT_CYAN));
        System.out.println(ConsoleColors.colorize("  🧠 Reasoning Process (REASONING) - Gray/White", ConsoleColors.BRIGHT_WHITE));
        System.out.println(ConsoleColors.colorize("  ⚡ Executing Action (ACTION) - Yellow", ConsoleColors.BRIGHT_YELLOW));
        System.out.println(ConsoleColors.colorize("  👁️ Observation Result (OBSERVATION) - Purple", ConsoleColors.BRIGHT_PURPLE));
        System.out.println(ConsoleColors.colorize("  ✅ Final Answer (ANSWER) - Green", ConsoleColors.BRIGHT_GREEN));
        System.out.println(ConsoleColors.colorize("  ❌ Error Message (ERROR) - Red", ConsoleColors.BRIGHT_RED));
        System.out.println(ConsoleColors.colorize("  💬 Regular Content (CONTENT) - Default", ConsoleColors.RESET));
        System.out.println(ConsoleColors.system("=".repeat(60)));
        System.out.println();

        while (true) {
            // User input prompt - use JLine's prompt
            String prompt = ConsoleColors.user("You: ");
            String input = reader.readLine(prompt);

            if (input == null) {
                break;
            }

            input = input.trim();
            if ("exit".equalsIgnoreCase(input)) {
                System.out.println(ConsoleColors.system("👋 Goodbye!"));
                break;
            }

            if ("clear".equalsIgnoreCase(input)) {
                reActEngine.clearSession(sessionId);
                System.out.println(ConsoleColors.system("✓ Conversation history cleared"));
                System.out.println();
                continue;
            }

            if (input.isEmpty()) {
                continue;
            }

            // Call AI for conversation
            chat(sessionId, input);

            System.out.println(); // Extra newline to increase conversation spacing
        }

        terminal.close();
    }

    /**
     * Execute AI conversation
     * Demonstrates how to use ReActEngine for AI reasoning
     */
    private void chat(String sessionId, String userInput) {
        // Create request
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user(userInput));

        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("GLM-4.7");
        request.setMessages(messages);
        request.setSessionId(sessionId);  // Set session ID to maintain context
        request.setStream(true);

        // AI response prompt
        System.out.println(ConsoleColors.ai("AI:"));

        // Use colored streaming callback
        ColoredStreamCallback coloredCallback = new ColoredStreamCallback();
        reActEngine.chat(request, coloredCallback);

        System.out.println(); // Newline
    }
}
