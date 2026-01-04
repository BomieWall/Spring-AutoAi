# AutoAi Core - Integrated Version

AutoAi Core is now a monolithic module integrating all features, including:

- **Core Features**: ReAct engine, tool registration, model management
- **Spring Integration**: Automatic scanning and registration of tools
- **Web Interface**: OpenAI-compatible REST API (optional)
- **Frontend Components**: Built-in chat interface (optional)

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>cn.autoai</groupId>
    <artifactId>autoai-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. Enable AutoAi

#### Web Application

```java
@SpringBootApplication
@EnableAutoAi          // Enable AutoAi (includes core features and Web interface)
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

#### Non-Web Application (Console/Background Service)

**Method 1: Using Service (Recommended)**

```java
@SpringBootApplication
@EnableAutoAi
public class ConsoleApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ConsoleApplication.class);
        ApplicationContext context = application.run(args);

        // Get Service from Spring container and start
        ConsoleService consoleService = context.getBean(ConsoleService.class);
        consoleService.start();
    }
}

@Service
public class ConsoleService {
    private final ReActEngine reActEngine;

    public ConsoleService(ReActEngine reActEngine) {
        this.reActEngine = reActEngine;
    }

    public void start() {
        // Use ReActEngine directly
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("GLM-4.7");
        request.setMessages(List.of(ChatMessage.user("Hello")));

        ChatCompletionResponse response = reActEngine.chat(request);
        System.out.println(response.getChoices().get(0).getMessage().getContent());
    }
}
```

**Method 2: Using CommandLineRunner**

```java
@SpringBootApplication
@EnableAutoAi
public class ConsoleApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConsoleApplication.class, args);
    }

    @Bean
    public CommandLineRunner runner(ReActEngine engine) {
        return args -> {
            // Use ReActEngine directly
            ChatCompletionRequest request = new ChatCompletionRequest();
            request.setModel("GLM-4.7");
            request.setMessages(List.of(ChatMessage.user("Hello")));

            ChatCompletionResponse response = engine.chat(request);
            System.out.println(response.getChoices().get(0).getMessage().getContent());
        };
    }
}
```

**Note**: Using Service + dependency injection is recommended, as it better aligns with Spring Boot best practices.

### 3. Configuration

#### Web Application Configuration

```yaml
autoai:
  web:
    enabled: true           # Enable Web service, default true
    base-path: /auto-ai     # Web service base path, default /auto-ai
  model:
    adapter: BigModel
    model: GLM-4.7
    api-key: your-api-key
```

After startup, visit:
- Chat interface: `http://localhost:8080/auto-ai/`
- API interface: `http://localhost:8080/auto-ai/v1/chat/completions`
- Tool list: `http://localhost:8080/auto-ai/v1/tools`

#### Non-Web Application Configuration

```yaml
autoai:
  web:
    enabled: false          # Disable Web interface
  model:
    adapter: BigModel
    model: GLM-4.7
    api-key: your-api-key
```

**Note**:
- Web interface is enabled by default, non-Web applications need to set `enabled: false`
- Web functionality is optional, core ReAct engine does not depend on Web environment

## Use Cases

### Web Application

Provides HTTP interface and built-in chat interface, suitable for:
- Online customer service systems
- Chatbots
- AI applications requiring user interaction

### Non-Web Application

Directly call by injecting `ReActEngine`, suitable for:
- **Scheduled Tasks**: Generate daily report summaries, data analysis
- **Message Queue Consumers**: Process messages and perform AI analysis
- **Background Batch Processing**: Batch data cleaning, content generation
- **Microservices**: Provide internal AI capability nodes

**Example: Scheduled Task Service**

```java
@Service
public class ReportScheduler {

    @Autowired
    private ReActEngine reActEngine;

    @Scheduled(cron = "0 0 8 * * ?")  // Execute every day at 8 AM
    public void generateDailyReport() {
        List<ChatMessage> messages = List.of(
            ChatMessage.user("Please generate today's sales data summary")
        );

        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("GLM-4.7");
        request.setMessages(messages);

        ChatCompletionResponse response = reActEngine.chat(request);
        String report = response.getChoices().get(0).getMessage().getContent();

        // Send email or save to database
        emailService.sendDailyReport(report);
    }
}
```

**Example: Message Queue Consumer**

```java
@Service
public class MessageProcessor {

    @Autowired
    private ReActEngine reActEngine;

    @KafkaListener(topics = "user-feedback")
    public void processFeedback(String feedback) {
        List<ChatMessage> messages = List.of(
            ChatMessage.user("Please analyze the sentiment and keywords of the following user feedback: " + feedback)
        );

        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("GLM-4.7");
        request.setMessages(messages);

        ChatCompletionResponse response = reActEngine.chat(request);
        String analysis = response.getChoices().get(0).getMessage().getContent();

        // Save analysis results
        saveAnalysis(feedback, analysis);
    }
}
```

## Features

### Auto Configuration
- Automatic configuration via Spring Boot, no manual Bean configuration needed
- Optional dependencies, only enable related features in Spring environment

### Web Interface (Optional)
- OpenAI-compatible chat interface
- Tool query interface
- Static resource service (frontend interface)
- Controlled by `autoai.web.enabled` configuration
- **SSE Disconnect Detection**: Automatically stops reasoning when client disconnects

### Frontend Components (Optional)
- Built-in chat interface supporting real-time conversation
- Adaptive UI design
- Visualization support for tool calls

### Core Engine
- ReAct Engine: Thought-Action-Observation loop
- Tool Registry: Automatic scanning and registration of tools
- Model Management: Support for multiple LLM adapters
- **Session Management**:
  - Automatic session expiration cleanup
  - Conversation history compression
  - Prevent memory leaks

### Configuration Options

| Configuration Item | Default Value | Description |
|-------------------|---------------|-------------|
| `autoai.web.enabled` | `true` | Whether to enable Web service |
| `autoai.web.base-path` | `/auto-ai` | Base path of Web service |
| `autoai.react.max-steps` | `20` | ReAct maximum execution steps |
| `autoai.react.enable-compression` | `true` | Whether to enable conversation history compression |
| `autoai.react.compression-threshold-tokens` | `64000` | Token threshold to trigger compression |
| `autoai.react.enable-session-expiration` | `true` | Whether to enable session expiration cleanup |
| `autoai.react.session-expire-minutes` | `60` | Session expiration time (minutes) |
| `autoai.react.session-cleanup-interval-minutes` | `10` | Session cleanup interval (minutes) |
| `autoai.model.adapter` | - | Model adapter (BigModel/OpenAI/MiniMax) |
| `autoai.model.model` | - | Model name |
| `autoai.model.api-key` | - | API key |

## Migration Guide

If you previously used separated modules, now you only need to:

1. Remove old dependencies:
   ```xml
   <!-- Remove these dependencies -->
   <dependency>
       <groupId>cn.autoai</groupId>
       <artifactId>autoai-spring</artifactId>
   </dependency>
   <dependency>
       <groupId>cn.autoai</groupId>
       <artifactId>autoai-spring-web</artifactId>
   </dependency>
   ```

2. Add new dependency:
   ```xml
   <dependency>
       <groupId>cn.autoai</groupId>
       <artifactId>autoai-core</artifactId>
       <version>0.1.0-SNAPSHOT</version>
   </dependency>
   ```

3. Update import statements:
   ```java
   // Old import
   import cn.autoai.spring.EnableAutoAi;
   import cn.autoai.spring.web.EnableAutoAiWeb;

   // New import
   import cn.autoai.core.spring.EnableAutoAi;
   ```

4. Configure Web functionality (if needed):
   ```yaml
   autoai:
     web:
       enabled: true  # Set to true for Web applications (default)
       enabled: false # Set to false for console applications
   ```

## Example Projects

- **autoai-demo-console**: Console application example, showing how to use in non-Web projects
- **autoai-demo-web**: Web application example, showing how to use HTTP interface and chat interface
