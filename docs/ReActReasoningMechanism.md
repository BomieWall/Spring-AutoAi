# ReAct Reasoning Mechanism Detailed Explanation

## Overview

AutoAi has a built-in complete ReAct (Reasoning + Acting) reasoning engine that implements the "Thinking-Acting-Observation-Answer" intelligent reasoning loop. Strictly follows ReAct pattern, supports multiple tool invocation formats, provides streaming output and session management capabilities.

## Core Design Philosophy

### 1. Combining Reasoning and Acting

The ReAct engine combines large language model reasoning capabilities with tool invocation capabilities to form a powerful AI Agent system:

- **Reasoning**: AI analyzes problems, plans solution steps
- **Acting**: Call tools to execute specific operations
- **Observation**: Obtain tool execution results
- **Iterative Loop**: Continue reasoning based on observation results until reaching final answer

### 2. Dual-Mode Tool Invocation

Engine supports two tool invocation formats:

#### Mode 1: Text Format (ReAct Standard Format)
```
🤔 Thinking Process
User requests order information, I should call getOrders tool

⚡ Execute Action
getOrders()

👁️ Observation Result
Tool returned: [{id: 1, item: "Product A", amount: 100}]

✨ Final Answer
Based on query results, you have 1 order...
```

#### Mode 2: tool_calls Format (OpenAI Compatible）
```json
{
  "tool_calls": [
    {
      "id": "call_abc123",
      "type": "function",
      "function": {
        "name": "getOrders",
        "arguments": "{}"
      }
    }
  ]
}
```

**Engine Auto-Recognition**: Automatically selects execution method based on LLM returned format.

## Runtime Flow

### Complete Reasoning Loop

```
┌─────────────────────────────────────────────────┐
│              1. User Input                          │
│       "Query all my orders"                         │
└───────────────────┬─────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────┐
│         2. Build Conversation Context                      │
│    - Add system prompt                              │
│    - Inject available tool list                            │
│    - Append conversation history                                │
└───────────────────┬─────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────┐
│         3. Call LLM to Generate Response                     │
└───────────────────┬─────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────┐
│      4. Parse Response Content (Determine Type）                 │
├─────────────────────────────────────────────────┤
│  Thinking  │ Record thinking process                        │
│  Action    │ Extract tool call information                    │
│  tool_calls│ Parse structured tool call                  │
│  Answer    │ Output final answer                        │
└───────────────────┬─────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────┐
│      5. Execute Tool Call (if any）                   │
│    - Parse tool name and parameters                          │
│    - Bind parameters and invoke method                          │
│    - Get execution result                                │
└───────────────────┬─────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────┐
│      6. Construct Observation Result Message                         │
│    - Format tool execution result                          │
│    - Add to conversation history                              │
└───────────────────┬─────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────┐
│      7. Determine Whether to Continue                             │
│    - Got answer → End                           │
│    - Need more steps → Return to step 3                   │
│    - Reached max steps → Terminate                         │
└─────────────────────────────────────────────────┘
```

## Core Components

### 1. ReActEngine

**Location**: `autoai-core/src/main/java/cn/autoai/core/react/ReActEngine.java`

**Core Methods**:
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
}
```

**Key Features**:
- Auto-detect tool invocation format (text vs tool_calls）
- Support synchronous and streaming two call modes
- Built-in session management and state maintenance
- Smart error handling and retry mechanism

### 2. ReActSettings

**Location**: `autoai-core/src/main/java/cn/autoai/core/react/ReActSettings.java`

**Configuration Parameters**:

#### Basic Configuration
```yaml
autoai:
  react:
    max-steps: 20                      # Maximum execution steps (default 20）
    show-tool-details: false           # Whether to show tool execution details (default true）
    temperature: 0.7                   # Temperature parameter
    max-tokens: 2000                   # Maximum token count
```

#### Conversation History Compression Configuration
```yaml
autoai:
  react:
    # Whether to enable conversation history compression (default true）
    enable-compression: true

    # Token count threshold to trigger compression
    compression-threshold-tokens: 64000

    # Recent context token count to keep during compression
    keep-recent-tokens: 12000

    # Maximum token count after compression (safety boundary）
    max-tokens-after-compression: 16000
```

**Working Principle**:
1. Before each reasoning, detect conversation history token count
2. When exceeding threshold, call LLM to generate conversation summary
3. Keep recent message context (ensure coherence）
4. Delete old intermediate conversation, only keep summary
5. Add summary as system message to conversation history

#### Session Management Configuration
```yaml
autoai:
  react:
    # Whether to enable session expiration cleanup (default true）
    enable-session-expiration: true

    # Session expiration time (minutes), default 60 minutes
    session-expire-minutes: 60

    # Session cleanup interval (minutes), default 10 minutes
    session-cleanup-interval-minutes: 10
```

**Working Principle**:
- Update last access time on each session access
- Background daemon thread periodically checks for expired sessions
- Automatically clean up sessions not accessed for specified time
- Output detailed logs for easy monitoring

### 3. ChatTaskManager

**Location**: `autoai-core/src/main/java/cn/autoai/core/react/ChatTaskManager.java`

**Responsibilities**:
- Manage ongoing streaming chat tasks
- Support SSE connection disconnect detection
- Provide task cancellation and status query capabilities

**Core API**:
```java
public class ChatTaskManager {
    // Create task
    public ChatTask createTask(String sessionId);

    // Complete task
    public void completeTask(String taskId);

    // Cancel task
    public void cancelTask(String taskId);

    // Check if task is cancelled
    public boolean isTaskCancelled(String taskId);
}
```

### 4. FrontendToolManager

**Location**: `autoai-core/src/main/java/cn/autoai/core/react/FrontendToolManager.java`

**Responsibilities**:
- Manage frontend tool call lifecycle
- Implement frontend-backend asynchronous communication
- Provide tool call waiting mechanism

**Core API**:
```java
public class FrontendToolManager {
    // Register tool call
    public String registerToolCall(String sessionId, ToolCall toolCall);

    // Wait for tool result
    public String waitForResult(String sessionId, String callId) throws InterruptedException;

    // Complete tool call
    public boolean completeToolCall(String sessionId, String callId, Object result, String error, boolean isError);
}
```

## Tool Invocation Detailed

### Backend Tool Invocation

**Support Two Modes**:

#### Mode 1: Method Invocation Mode
```java
@Component
public class CalculatorTools {

    @AutoAiTool(description = "Calculate")
    public int add(int a, int b) {
        return a + b;
    }
}
```

**Features**:
- Directly invoke method via reflection
- High performance, low latency
- Suitable for internal business logic

#### Mode 2: REST API Invocation Mode
```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping
    @AutoAiTool(description = "Get list of all orders")
    public List<Order> getAllOrders(
        @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        // When AI calls this tool, it will initiate HTTP GET /api/orders request
        // auth parameter will be automatically passed from original request
        return orderService.findAll();
    }
}
```

**Features**:
- Initiate HTTP request via RestTemplate
- Automatically pass request headers, cookies, and other authentication information
- Pass through Spring Security authentication
- Support multi-tenancy (pass tenant ID via Header）
- Suitable for external APIs requiring authentication

**Mode Comparison**:

| Feature | Method Invocation Mode | REST API Invocation Mode |
|---------|------------------|-------------------------|
| Call Method | Reflection direct call | HTTP request |
| Request Passing | None | Automatically pass Header, Cookie |
| Security Authentication | Bypasses Spring Security | Passes through Spring Security authentication |
| Multi-Tenancy Support | Manual handling required | Automatically pass via Header |
| Use Cases | Internal tools | External APIs, operations requiring authentication |

### Frontend Tool Invocation

**Core Flow**:

```
1. Frontend defines tools
   ↓
2. Pass to backend via frontend_tools parameter
   ↓
3. Backend adds to toolSpecs list
   ↓
4. LLM returns tool_calls format
   ↓
5. Backend identifies as frontend tool
   ↓
6. Notify frontend via SSE
   ↓
7. Frontend executes tool
   ↓
8. Frontend POSTs result to /v1/chat/tool-result
   ↓
9. Backend receives and returns to LLM
```

**Built-in Tool List**:

| Tool Name | Function | Parameters |
|-----------|---------|-----------|
| `getLocalStorage` | Get localStorage data | `key` (required), `parseJson` (optional) |
| `setLocalStorage` | Store localStorage data | `key` (required), `value` (required) |
| `removeLocalStorage` | Delete localStorage data | `key` (required) |
| `listLocalStorage` | List all localStorage data | None |
| `getCookie` | Read specified cookie | `name` (required) |
| `getAllCookies` | Read all cookies | None |
| `getPageInfo` | Get basic current page information | None |
| `showNotification` | Show notification prompt | `message` (required), `type` (optional) |

**Custom Frontend Tools**:
```javascript
const widget = new AutoAiChatWidget({
  baseUrl: '/auto-ai',
  frontendTools: [
    {
      name: 'getUsername',
      description: 'Get current logged-in user username',
      parameters: {
        type: 'object',
        properties: {
          format: {
            type: 'string',
            description: 'Return format: plain-plain text, json-JSON object',
            enum: ['plain', 'json']
          }
        },
        required: []
      },
      fn: async (args) => {
        const username = localStorage.getItem('username');
        if (args.format === 'json') {
          return { username, timestamp: Date.now() };
        }
        return username;
      }
    }
  ]
});
```

## Resource Management

### SSE Disconnect Detection

**Working Principle**:
- Listen for `onCompletion`, `onError`, `onTimeout` events of `SseEmitter`
- When client disconnects, automatically mark task as terminated
- ReAct engine's reasoning loop will check task status and immediately stop reasoning
- Save server resources, avoid invalid computation

**Applicable Scenarios**:
- User closes browser or refreshes page
- Network interruption causes disconnect
- Frontend actively cancels request

**No configuration needed, automatically takes effect.**

### Session Management API

```java
@Autowired
private ReActEngine reActEngine;

// Clean single session
reActEngine.clearSession("session-id");

// Clean all sessions
reActEngine.clearAllSessions();

// Manually trigger expiration cleanup
reActEngine.cleanupExpiredSessions();

// Check current session count
int count = reActEngine.getSessionCount();
```

### Conversation History Compression

**Auto Trigger**:
```java
// Automatically execute in ReActEngine
if (enableCompression && getTokenCount(messages) > compressionThresholdTokens) {
    // Compress conversation history
    compressChatHistory(session, messages);
}
```

**Compression Strategy**:
1. Keep system prompt
2. Keep most recent N messages (controlled by `keepRecentTokens`）
3. Call LLM to generate summary of intermediate conversation
4. Insert summary as system message
5. Delete intermediate original conversation
