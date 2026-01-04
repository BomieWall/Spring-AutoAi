# AutoAi2 Framework Architecture Documentation

## 1. Overview

AutoAi2 is a lightweight AI framework based on Java/Spring that implements a complete ReAct (Reasoning + Acting) reasoning pattern. It allows developers to convert existing methods into AI-callable tools through simple `@AutoAiTool` annotations. The framework automatically discovers, registers, and calls these tools through LLM interaction.

### 1.1 Core Philosophy

- **Declarative Tool Definition**: Declare tools through annotations without writing additional code
- **Automatic Discovery and Registration**: Utilize Spring's capabilities to automatically scan and register tools
- **ReAct Reasoning Pattern**: Implement the intelligent reasoning loop of think-act-observe
- **OpenAI Protocol Compatible**: Fully compatible with OpenAI's Chat Completion API
- **Streaming Response**: Support real-time streaming output and content type markers
- **Dual-Mode Tool Calling**: Support both method invocation and REST API invocation modes

### 1.2 Key Features

| Feature Category | Feature Name | Description |
|---------|---------|------|
| Core Functions | ReAct Reasoning Engine | Implements "Think-Act-Observe-Answer" loop |
| | Automatic Tool Discovery | Annotation-based automatic scanning and registration |
| | Multi-Model Support | Compatible with OpenAI, BigModel (Zhipu), MiniMax, etc. |
| Tool Calling | Method Invocation Mode | Direct method calls through reflection |
| | REST API Invocation Mode | Calls through HTTP with authentication support |
| | Frontend Tools | Support for JavaScript tool calls |
| Resource Management | Session Management | Session isolation based on sessionId |
| | Conversation History Compression | Automatically compress long conversations to save tokens |
| | Session Expiration Cleanup | Automatically cleanup expired sessions to prevent memory leaks |
| | SSE Disconnect Detection | Intelligently detect disconnections and stop invalid reasoning |
| Performance Optimization | Tool Scanning Optimization | Precise control of scanning scope with @AutoAiToolScan |
| | Smart Caching | Tool information and session state caching |
| Integration Capabilities | Spring Boot Integration | Out-of-the-box auto-configuration |
| | OpenAI Protocol Compatible | Standard RESTful API interface |
| | Streaming Response | Server-Sent Events (SSE) support |

## 2. Technology Stack

| Technology | Version | Purpose |
|------|------|------|
| Java | 17+ | Programming Language |
| Spring Boot | 3.2.5 | Application Framework |
| Spring Web | 3.2.5 | Web Support |
| Jackson | - | JSON Serialization/Deserialization |
| SLF4J | - | Logging Facade |
| Spring AOP | - | Aspect-Oriented Programming |
| Maven | - | Project Build Tool |

**Dependency Notes**:
- `autoai-core.jar` is an uber-jar (fat jar) containing all required dependencies
- No need to introduce Spring, Jackson and other dependencies separately
- Out-of-the-box, simplified integration

## 3. Module Structure

### 3.1 Project Structure

```
AutoAi2
├── autoai-core/              # Core framework module
│   ├── src/main/java/cn/autoai/core/
│   │   ├── annotation/       # Annotation definitions
│   │   │   ├── AutoAiTool.java
│   │   │   ├── AutoAiParam.java
│   │   │   ├── AutoAiField.java
│   │   │   ├── AutoAiReturn.java
│   │   │   ├── AutoAiToolScan.java
│   │   │   └── EnableAutoAi.java
│   │   ├── builtin/          # Built-in tools
│   │   ├── llm/              # Large model adapters
│   │   │   ├── AutoAiModel.java
│   │   │   ├── BigModelModel.java
│   │   │   ├── OpenAIModel.java
│   │   │   └── MiniMaxModel.java
│   │   ├── model/            # Data models
│   │   │   ├── ChatCompletionRequest.java
│   │   │   ├── ChatCompletionResponse.java
│   │   │   ├── ToolSpec.java
│   │   │   └── ToolCall.java
│   │   ├── protocol/         # OpenAI compatible protocol
│   │   ├── react/            # ReAct engine
│   │   │   ├── ReActEngine.java
│   │   │   ├── ReActSettings.java
│   │   │   ├── ChatTaskManager.java
│   │   │   └── FrontendToolManager.java
│   │   ├── registry/         # Tool registration mechanism
│   │   │   ├── ToolRegistry.java
│   │   │   ├── ToolDefinition.java
│   │   │   ├── ToolInvoker.java
│   │   │   └── FrontendToolInvoker.java
│   │   ├── spring/           # Spring configuration
│   │   │   ├── AutoAiSpringConfiguration.java
│   │   │   ├── AutoAiWebConfiguration.java
│   │   │   └── AutoAiToolScanner.java
│   │   ├── util/             # Utility classes
│   │   │   ├── ExampleGenerator.java
│   │   │   └── TokenCounter.java
│   │   └── web/              # Web interfaces
│   │       ├── AutoAiChatController.java
│   │       ├── AutoAiStreamController.java
│   │       └── AutoAiToolController.java
│   └── src/main/resources/   # Static resources
│       └── static/auto-ai/   # Frontend components
│           ├── autoai-chat.js
│           ├── index.html
│           └── sidebar-demo.html
├── autoai-demo-console/      # Console demo module
├── autoai-demo-web/          # Web demo module
└── docs/                     # Project documentation
    ├── README.md
    ├── Architecture.md
    ├── ReActReasoningMechanism.md
    ├── FrontendToolCallFlow.md
    └── ToolDiscoveryAndDynamicInfoArchitecture.md
```

### 3.2 Core Module Descriptions

#### autoai-core (Core Module)

**Responsibility**: Provides the framework's core functionality, including tool registration, ReAct engine, LLM integration, web interfaces, etc.

**Main Components**:
- **Annotation System**: `@AutoAiTool`, `@AutoAiParam`, `@AutoAiField`, `@AutoAiReturn`, `@AutoAiToolScan`, `@EnableAutoAi`
- **ReAct Engine**: `ReActEngine` - Core reasoning engine
- **Tool Registry**: `ToolRegistry` - Tool discovery and registration
- **LLM Abstraction**: `AutoAiModel` - Multi-model support
- **Web Controllers**: `AutoAiChatController`, `AutoAiStreamController`, `AutoAiToolController`
- **Spring Integration**: Auto-configuration and conditional assembly

#### autoai-demo-console (Console Demo)

**Responsibility**: Demonstrates usage in console applications.

**Features**:
- Non-web application
- Direct use of `ReActEngine` API
- Suitable for background tasks, scheduled tasks, etc.

#### autoai-demo-web (Web Demo)

**Responsibility**: Demonstrates usage in web applications.

**Features**:
- Provides RESTful API interfaces
- Built-in chat interface
- Streaming response demonstration
- Frontend tool integration examples

## 4. Core Architecture Design

### 4.1 Layered Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Web Layer (Controllers)                   │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────┐ │
│  │ AutoAiChatCtrl   │  │AutoAiStreamCtrl  │  │AutoAiTool  │ │
│  │ (Standard Chat)  │  │ (Stream Response)│  │ Controller │ │
│  └──────────────────┘  └──────────────────┘  └────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    ReAct Engine Layer                        │
│  ┌────────────────────────────────────────────────────────┐ │
│  │                   ReActEngine                          │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │ │
│  │  │ Session Mgmt │  │Tool Parsing  │  │  Streaming   │ │ │
│  │  │(Session Mgmt)│  │(Tool Parsing)│  │(Stream Suppt)│ │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘ │ │
│  │  ┌──────────────┐  ┌──────────────┐                   │ │
│  │  │   History    │  │  Compression │                   │ │
│  │  │ (History Mgmt)│  │(History Comp)│                   │ │
│  │  └──────────────┘  └──────────────┘                   │ │
│  └────────────────────────────────────────────────────────┘ │
│  ┌──────────────────┐  ┌──────────────────┐               │
│  │ChatTaskManager   │  │FrontendToolMgr   │               │
│  │ (Task Mgmt)      │  │(Frontend Tool)   │               │
│  └──────────────────┘  └──────────────────┘               │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                  Tool Registry Layer                         │
│  ┌────────────────────────────────────────────────────────┐ │
│  │                  ToolRegistry                           │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │ │
│  │  │   Discovery  │  │ Registration │  │   Metadata   │ │ │
│  │  │(Tool Discvry)│  │(Tool Reg)    │  │(Metadata)    │ │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘ │ │
│  └────────────────────────────────────────────────────────┘ │
│  ┌──────────────────┐  ┌──────────────────┐               │
│  │  ToolInvoker     │  │FrontendToolInvoker│               │
│  │ (Backend Tool)   │  │ (Frontend Tool)   │               │
│  └──────────────────┘  └──────────────────┘               │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   LLM Integration Layer                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ BigModelModel│  │ OpenAIModel  │  │ MiniMaxModel │     │
│  │ (Zhipu GLM)   │  │ (OpenAI)      │  │ (MiniMax)    │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 Tool Invocation Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   Tool Invocation Entry                      │
│               (LLM returns tool call request)                │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │ Tool Type Check │
                    │ (Backend vs Frontend)│
                    └────────┬────────┘
                             │
            ┌────────────────┴────────────────┐
            │                                 │
            ▼                                 ▼
┌───────────────────────┐       ┌───────────────────────┐
│    Backend Tool Call   │       │    Frontend Tool Call  │
├───────────────────────┤       ├───────────────────────┤
│ 1. Check call mode     │       │ 1. Generate callId    │
│    - Method mode       │       │ 2. Notify frontend via │
│    - REST API mode     │       │    SSE                │
│                       │       │ 3. Wait for result     │
│ [Method Mode]          │       │    (max 30 sec)       │
│ 2. Parameter binding   │       │ 4. Return result to   │
│ 3. Reflection call     │       │    LLM                │
│ 4. Return result       │       │                       │
│                       │       │                       │
│ [REST API Mode]        │       │                       │
│ 2. Build HTTP request  │       │                       │
│ 3. Pass Header/Cookie  │       │                       │
│ 4. Send request        │       │                       │
│ 5. Return result       │       │                       │
└───────────────────────┘       └───────────────────────┘
            │                                 │
            └────────────────┬────────────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │ Construct       │
                    │ Observation     │
                    │ Return to ReAct │
                    └─────────────────┘
```

### 4.3 Session Management Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Session Lifecycle                         │
└─────────────────────────────────────────────────────────────┘

   User Request    Create Session    Reasoning Loop    Session Cleanup
      │               │                │                 │
      ▼               ▼                ▼                 ▼
┌─────────┐    ┌─────────┐     ┌──────────┐     ┌──────────┐
│Generate/│    │Session- │     │ Continuous│     │Expiration│
│Use      │    │Wrapper  │     │ Update    │     │Cleanup   │
│sessionId│    │         │     │ Messages  │     │Auto Delete│
└─────────┘    └─────────┘     └──────────┘     └──────────┘
                    │                │                 │
                    ▼                ▼                 ▼
              ┌─────────┐     ┌──────────┐     ┌──────────┐
              │Storage  │     │lastAccess│     │Daemon    │
              │Structure│     │Time      │     │Thread    │
              │messages │     │          │     │Periodic  │
              │lastAccess│     │          │     │Check     │
              └─────────┘     └──────────┘     └──────────┘

┌─────────────────────────────────────────────────────────────┐
│                   Resource Management                        │
├─────────────────────────────────────────────────────────────┤
│  Conversation History│Session Expiration│SSE Disconnect  │
│       Compression      │       Cleanup    │    Detection   │
│  - Token counting     │  - Periodic check│  - Listen for   │
│  - Threshold compress │  - Auto delete   │    connection  │
│  - Keep recent context│  - Prevent memory │    events       │
│                        │    leaks         │  - Mark task    │
│                        │                  │    termination  │
│                        │                  │  - Stop         │
│                        │                  │    reasoning     │
└─────────────────────────────────────────────────────────────┘
```

## 5. Core Component Details

### 5.1 ReActEngine

**Location**: `autoai-core/src/main/java/cn/autoai/core/react/ReActEngine.java`

**Core Responsibilities**:
- Implement ReAct reasoning loop
- Manage session state
- Parse and execute tool calls
- Support synchronous and streaming responses
- Conversation history compression
- Session expiration cleanup

**Key Methods**:
```java
public class ReActEngine {
    // Synchronous chat
    public ChatCompletionResponse chat(ChatCompletionRequest request);

    // Streaming chat
    public void chat(ChatCompletionRequest request, StreamCallback callback);

    // Session management
    public void clearSession(String sessionId);
    public void clearAllSessions();
    public int getSessionCount();
    public void cleanupExpiredSessions();
}
```

**Reasoning Flow**:
```
1. Receive user request
   ↓
2. Build conversation context (system prompt + tool list + history)
   ↓
3. Call LLM to generate response
   ↓
4. Parse response type (Thinking / Action / tool_calls / Answer)
   ↓
5. If it's a tool call:
   - Determine tool type (backend/frontend)
   - Execute tool
   - Construct observation result
   - Add to conversation history
   - Return to step 3
   ↓
6. If it's final answer:
   - Return result
   - End loop
   ↓
7. Check termination conditions (max steps/task cancellation)
```

### 5.2 ToolRegistry

**Location**: `autoai-core/src/main/java/cn/autoai/core/registry/ToolRegistry.java`

**Core Responsibilities**:
- Tool registration and storage
- Tool query and retrieval
- Tool metadata management

**Data Structure**:
```java
public class InMemoryToolRegistry implements ToolRegistry {
    // Fast lookup: tool name -> ToolSummary
    private final Map<String, ToolSummary> toolSummaries;

    // Complete metadata: tool name -> ToolDetail
    private final Map<String, ToolDetail> toolDetails;
}
```

**Tool Definition Structure**:
```java
public class ToolDefinition {
    private Object bean;              // Spring Bean instance
    private Method method;            // Method reference
    private String name;              // Tool name
    private String description;       // Tool description
    private Map<String, ParameterBinding> paramBindings;  // Parameter binding
    private boolean isRestApiCall;    // Whether it's REST API call mode
    private String httpMethod;        // HTTP method (GET/POST, etc.)
    private String url;               // REST API URL
    private String requestExample;    // Request example
    private String responseExample;   // Response example
}
```

### 5.3 AutoAiToolScanner

**Location**: `autoai-core/src/main/java/cn/autoai/core/spring/AutoAiToolScanner.java`

**Core Responsibilities**:
- Scan Spring Beans
- Discover `@AutoAiTool` annotations
- Build tool definitions
- Register to ToolRegistry

**Scanning Strategy**:

**Default Scanning** (for small projects):
```java
@SpringBootApplication
@EnableAutoAi
public class MyApp {
    // Scan all Spring Beans
}
```

**Precise Scanning** (for large projects):
```java
@SpringBootApplication
@EnableAutoAi
@AutoAiToolScan(classes = {DemoTools.class})  // Only scan specified classes
public class MyApp {
    // ...
}
```

**Package-based Scanning**:
```java
@AutoAiToolScan({"com.example.myapp.tools", "com.example.myapp.services"})
```

**Performance Comparison**:

| Project Size | Bean Count | Scanning Strategy | Startup Time |
|---------|----------|---------|---------|
| Small | < 100 | Default scanning | < 100ms |
| Medium | 100-500 | Package scanning | < 200ms |
| Large | > 500 | Specified classes | < 100ms |

### 5.4 FrontendToolManager

**Location**: `autoai-core/src/main/java/cn/autoai/core/react/FrontendToolManager.java`

**Core Responsibilities**:
- Manage frontend tool call lifecycle
- Implement frontend-backend asynchronous communication
- Provide tool call waiting mechanism

**Workflow**:
```
1. Backend identifies frontend tool
   ↓
2. registerToolCall() - Generate callId, create CompletableFuture
   ↓
3. Notify frontend via SSE
   ↓
4. waitForResult() - Block and wait (max 30 seconds)
   ↓
5. Frontend executes tool and POST result to /v1/chat/tool-result
   ↓
6. completeToolCall() - Complete CompletableFuture
   ↓
7. waitForResult() returns result
```

**Key Data Structure**:
```java
public class FrontendToolManager {
    // Pending tool calls: sessionId:callId -> CompletableFuture
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingToolCalls;
}
```

### 5.5 ChatTaskManager

**Location**: `autoai-core/src/main/java/cn/autoai/core/react/ChatTaskManager.java`

**Core Responsibilities**:
- Manage streaming chat tasks
- Support SSE disconnect detection
- Provide task cancellation capability

**SSE Disconnect Detection Mechanism**:
```java
public ChatTask createTask(String sessionId, SseEmitter emitter) {
    ChatTask task = new ChatTask(sessionId, taskId);

    // Listen to connection events
    emitter.onCompletion(() -> {
        task.complete();  // Connection completed normally
    });

    emitter.onError((ex) -> {
        task.cancel();    // Connection error, cancel task
    });

    emitter.onTimeout(() -> {
        task.cancel();    // Connection timeout, cancel task
    });

    return task;
}
```

**Reasoning Loop Check**:
```java
// In ReActEngine's reasoning loop
if (chatTaskManager.isTaskCancelled(taskId)) {
    logger.info("Task cancelled, stop reasoning: {}", taskId);
    break;  // Immediately stop reasoning loop
}
```

## 6. Data Flow

### 6.1 Initialization Flow

```
Spring Boot Startup
    │
    ▼
@EnableAutoAi takes effect
    │
    ▼
AutoAiSpringConfiguration auto-configuration
    │
    ├─► Create ToolRegistry
    ├─► Create AutoAiModel (BigModel/OpenAI/MiniMax)
    ├─► Create ReActSettings
    ├─► Create ReActEngine
    ├─► Create ChatTaskManager
    └─► Create FrontendToolManager
    │
    ▼
AutoAiToolScanner starts (SmartLifecycle)
    │
    ▼
Scan Spring Beans (based on @AutoAiToolScan configuration)
    │
    ▼
Discover methods with @AutoAiTool annotation
    │
    ▼
ToolDefinitionBuilder builds tool definitions
    │
    ├─► Extract method signature
    ├─► Build parameter metadata
    ├─► Generate JSON Schema
    ├─► Generate example values
    ├─► Determine call mode (method/REST API)
    └─► Create ToolDefinition
    │
    ▼
Register to ToolRegistry
    │
    ▼
System Ready
```

### 6.2 Tool Call Flow (Backend - Method Invocation Mode)

```
LLM returns tool call request
    │
    ▼
ReActEngine parses tool call
    │
    ▼
Lookup tool from ToolRegistry
    │
    ▼
ToolInvoker handles
    │
    ├─► Parse JSON parameters
    ├─► Type conversion
    ├─► Parameter binding
    └─► Reflection method call
    │
    ▼
Serialize return result
    │
    ▼
Construct Observation message
    │
    ▼
Return to ReActEngine
    │
    ▼
Add to conversation history
    │
    ▼
Continue next reasoning round
```

### 6.3 Tool Call Flow (Backend - REST API Invocation Mode)

```
LLM returns tool call request
    │
    ▼
ReActEngine parses tool call
    │
    ▼
Identify as REST API tool
    │
    ▼
FrontendToolInvoker handles
    │
    ├─► Build HTTP request
    │   - URL: Tool's REST API address
    │   - Method: GET/POST/PUT/DELETE
    │   - Headers: Original request headers (Authorization, etc.)
    │   - Cookies: Original request cookies
    │   - Body: Tool parameter JSON
    │
    ├─► Send request via RestTemplate
    │
    ├─► Wait for response
    │
    └─► Parse response result
    │
    ▼
Construct Observation message
    │
    ▼
Return to ReActEngine
```

### 6.4 Tool Call Flow (Frontend Tools)

```
LLM returns tool_calls format
    │
    ▼
ReActEngine identifies as frontend tool
    │
    ▼
FrontendToolManager.registerToolCall()
    │
    ├─► Generate callId
    └─► Create CompletableFuture
    │
    ▼
Send notification to frontend via SSE
    │
    Format: [Frontend Tool Call] {"type":"FRONTEND_TOOL_CALL","callId":"xxx","toolCall":{...}}
    │
    ▼
FrontendToolManager.waitForResult() - Block and wait (max 30 seconds)
    │
    ▼
Frontend receives SSE message
    │
    ▼
Frontend executes tool function
    │
    ▼
Frontend POSTs result to /v1/chat/tool-result
    │
    ▼
AutoAiStreamController.receiveToolResult()
    │
    ▼
FrontendToolManager.completeToolCall()
    │
    └─► future.complete(resultStr) - Release block
    │
    ▼
waitForResult() returns result
    │
    ▼
Return to ReActEngine
    │
    ▼
Add to conversation history
    │
    ▼
Continue next reasoning round
```

### 6.5 Complete Conversation Flow

```
User sends message
    │
    ▼
AutoAiStreamController.chatStream()
    │
    ▼
Create SseEmitter and ChatTask
    │
    ▼
ReActEngine.chat(request, callback)
    │
    ▼
┌─────────────────────────────────────────┐
│          ReAct Reasoning Loop            │
│  ┌───────────────────────────────────┐  │
│  │ 1. Build conversation context      │  │
│  │    - System prompt                │  │
│  │    - Tool list                     │  │
│  │    - Conversation history          │  │
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │ 2. Call LLM                       │  │
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │ 3. Parse response                 │  │
│  │    - Thinking → Stream output     │  │
│  │    - Action/tool_calls → Execute │  │
│  │      tools                        │  │
│  │    - Answer → Stream output and   │  │
│  │      end                          │  │
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │ 4. If tool call                   │  │
│  │    - Execute tool                 │  │
│  │    - Construct Observation        │  │
│  │    - Add to history               │  │
│  │    - Continue loop                │  │
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │ 5. Check termination conditions    │  │
│  │    - Got answer                   │  │
│  │    - Reached max steps            │  │
│  │    - Task cancelled               │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
    │
    ▼
ChatTask.complete()
    │
    ▼
SseEmitter.complete()
    │
    ▼
End
```

## 7. Design Patterns

### 7.1 Strategy Pattern

**Application Scenarios**:
- LLM model implementations (`BigModelModel`, `OpenAIModel`, `MiniMaxModel`)
- Tool registry implementations (`InMemoryToolRegistry`, can extend to other implementations)

**Advantages**:
- Easy to switch algorithms
- Follows Open/Closed Principle
- Supports multiple LLM providers

### 7.2 Registry Pattern

**Application Scenarios**:
- `ToolRegistry` manages tool registration and lookup

**Advantages**:
- Centralized management
- Fast lookup
- O(1) complexity

### 7.3 Factory Pattern

**Application Scenarios**:
- `ToolDefinitionBuilder` builds tool definitions
- `ExampleGenerator` generates example values

**Advantages**:
- Encapsulate creation logic
- Unified creation interface

### 7.4 Observer Pattern

**Application Scenarios**:
- `StreamCallback` streaming callback
- `TypedStreamCallback` type-aware streaming callback
- SSE connection event listening

**Advantages**:
- Decouple producer and consumer
- Support multiple observers
- Real-time notification

### 7.5 Template Method Pattern

**Application Scenarios**:
- `ReActEngine` defines reasoning/action loop template

**Advantages**:
- Define algorithm skeleton
- Subclasses can extend specific steps

### 7.6 Builder Pattern

**Application Scenarios**:
- `ChatCompletionRequest.Builder`
- `ToolSpec.Builder`

**Advantages**:
- Step-by-step construction of complex objects
- Improve readability

## 8. Key Features

### 8.1 Dual-Mode Tool Calling

**Method Invocation Mode**:
```java
@Component
public class CalculatorTools {
    @AutoAiTool(description = "Calculate the sum of two integers")
    public int add(int a, int b) {
        return a + b;
    }
}
```

**REST API Invocation Mode**:
```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    @GetMapping
    @AutoAiTool(description = "Get all orders list")
    public List<Order> getAllOrders(
        @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        // When AI calls, it will initiate request via HTTP GET /api/orders
        return orderService.findAll();
    }
}
```

**Comparison**:

| Feature | Method Invocation Mode | REST API Invocation Mode |
|------|-------------|------------------|
| Call Method | Direct reflection call | HTTP request |
| Performance | High | Medium |
| Request Passing | None | Automatically passes Header, Cookie |
| Security Authentication | Bypass Spring Security | Goes through Spring Security auth |
| Multi-tenant Support | Needs manual handling | Automatically passed through Header |
| Applicable Scenarios | Internal tools, no auth needed | External APIs, auth needed |

### 8.2 Conversation History Compression

**Trigger Conditions**:
```yaml
autoai:
  react:
    enable-compression: true            # Enable compression
    compression-threshold-tokens: 64000 # Trigger compression when token count exceeds this value
```

**Compression Strategy**:
1. Keep system prompt
2. Keep most recent N messages (controlled by `keepRecentTokens`)
3. Call LLM to generate summary of intermediate conversation
4. Insert summary as system message
5. Delete intermediate original conversation

**Effect**:
- Significantly reduce token consumption
- Maintain conversation coherence
- AI can still understand previous conversation content

### 8.3 Session Expiration Management

**Configuration**:
```yaml
autoai:
  react:
    enable-session-expiration: true     # Enable session expiration cleanup
    session-expire-minutes: 60          # Clean up after 60 minutes of no access
    session-cleanup-interval-minutes: 10 # Check every 10 minutes
```

**Working Principle**:
- Update last access time on each session access
- Background daemon thread periodically checks for expired sessions
- Automatically cleanup sessions not accessed for specified time
- Output detailed logs for easy monitoring

### 8.4 SSE Disconnect Detection

**Working Principle**:
- Listen to `SseEmitter`'s `onCompletion`, `onError`, `onTimeout` events
- When client disconnects, automatically mark task as terminated
- ReAct engine's reasoning loop checks task status and immediately stops reasoning
- Save server resources, avoid invalid computation

**No configuration needed, automatically effective.**

### 8.5 Tool Scanning Optimization

**Problem**: Scanning all Beans during startup in large projects (>500 Beans) takes time

**Solution**: Use `@AutoAiToolScan` to precisely control scanning scope

```java
// Method 1: Scan by class
@AutoAiToolScan(classes = {DemoTools.class})

// Method 2: Scan by package
@AutoAiToolScan({"com.example.myapp.tools"})

// Method 3: Mixed usage
@AutoAiToolScan(value = {"com.example.myapp.tools"}, classes = {SpecialTool.class})
```

**Performance Improvement**:
- Large project startup time reduced from 2-3 seconds to < 100ms
- Reduce unnecessary reflection calls

## 9. Security Considerations

### 9.1 Parameter Validation

- Type checking and conversion
- Null value handling
- Boundary condition validation
- JSON Schema validation

### 9.2 Error Handling

- Exception catching and logging
- Don't leak sensitive information
- Graceful degradation
- Friendly error messages

### 9.3 Security Authentication

**REST API Invocation Mode**:
- Automatically pass request headers (Authorization, X-User-Id, etc.)
- Automatically pass cookies
- Maintain Spring Security's authentication mechanism
- Support multi-tenant scenarios (pass tenant ID through Header)

**Spring Security Configuration Example**:
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // AutoAi chat interface public
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auto-ai/**").permitAll()  // Must be public
                // Business API configured as needed
                .requestMatchers(HttpMethod.GET, "/api/orders").hasRole("USER")
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
```

### 9.4 Command Execution Security

```java
@AutoAiTool(description = "Execute system command (restricted)")
public String executeCommand(@AutoAiParam("command") String cmd) {
    // Security check: limit executable commands
    if (!isCommandAllowed(cmd)) {
        throw new SecurityException("Command not allowed to execute");
    }
    // Execute command...
}
```

## 10. Performance Optimization

### 10.1 Memory Optimization

- **InMemoryToolRegistry**: Fast lookup, O(1) complexity
- **Message History Management**: Efficient storage and retrieval
- **Session Expiration Cleanup**: Automatically release memory

### 10.2 Response Optimization

- **Streaming Response**: Reduce time to first byte
- **Concurrent Processing**: Support multi-session concurrency
- **SSE Disconnect Detection**: Avoid invalid computation

### 10.3 Startup Optimization

- **Tool Scanning Optimization**: `@AutoAiToolScan` precisely controls scanning scope
- **Lazy Initialization**: Don't load Web components in non-Web environment

### 10.4 Token Optimization

- **Conversation History Compression**: Automatically compress long conversations
- **Layered Tool Information**: Get tool details on demand
- **Smart Caching**: Tool definition caching

## 11. Extension Guide

### 11.1 Add New LLM Provider

```java
@Component
@ConditionalOnProperty(name = "autoai.model.adapter", havingValue = "custom")
public class CustomModel implements AutoAiModel {

    private final ObjectMapper objectMapper;

    @Override
    public ChatCompletionResponse chat(ChatCompletionRequest request) {
        // Implement synchronous call
    }

    @Override
    public void streamChat(ChatCompletionRequest request, StreamCallback callback) {
        // Implement streaming call
    }
}
```

### 11.2 Custom Tool Registry

```java
@Component
public class DatabaseToolRegistry implements ToolRegistry {

    @Override
    public void register(ToolDefinition definition) {
        // Save to database
    }

    @Override
    public Optional<ToolDetail> getDetail(String name) {
        // Query from database
    }
}
```

### 11.3 Custom System Prompt

```yaml
autoai:
  react:
    system-prompt: |
      You are a professional AI assistant, skilled at:
      1. Data analysis and processing
      2. Code writing and debugging
      3. Problem solving and advice
```

Or through code:
```java
@Bean
public ReActSettings reActSettings() {
    ReActSettings settings = new ReActSettings();
    settings.setSystemPrompt("Custom system prompt...");
    return settings;
}
```
