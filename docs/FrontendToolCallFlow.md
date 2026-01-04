# Complete Frontend Tool Invocation Flow Analysis

## Overview

The AutoAi framework supports frontend tool invocation mechanism, allowing AI models to call browser-side JavaScript functions, implementing page interaction, data storage, user operations, and other functions.

### Core Features

- **Ready to Use**: 8 common built-in tools automatically available
- **Flexible Extension**: Support custom tools and override built-in tools
- **Unified Interface**: Completely consistent with backend tool usage
- **Asynchronous Communication**: Realize frontend-backend asynchronous communication via SSE
- **Type Safety**: Complete parameter types and validation

## I. Flow Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    Complete Frontend Tool Invocation Flow        │
└─────────────────────────────────────────────────────────────────┘

1️⃣ Frontend Tool Registration
   ↓
2️⃣ Send to Backend
   ↓
3️⃣ Backend Builds Tool List
   ↓
4️⃣ System Prompt Generation
   ↓
5️⃣ Pass to LLM (tools parameter)
   ↓
6️⃣ LLM Returns tool_calls
   ↓
7️⃣ Backend Identifies and Executes Frontend Tool
   ↓
8️⃣ SSE Notify Frontend
   ↓
9️⃣ Frontend Executes Tool
   ↓
🔟 Return Result to Backend
   ↓
1️⃣1️⃣ Backend Returns to LLM
```

---

## II. Detailed Flow Analysis

### Step 1️⃣: Frontend Tool Registration (index.html)

**Location**: `autoai-demo-web/src/main/resources/static/index.html:364-629`

```javascript
frontendTools: [
    {
        name: 'getLocalStorage',
        description: 'Get data from localStorage',
        parameters: {
            type: 'object',
            properties: {
                key: { type: 'string', description: 'Stored key name' },
                parseJson: { type: 'boolean', description: 'Whether to parse as JSON' }
            },
            required: ['key']
        },
        fn: async (args) => {
            // Actual execution logic
            const value = localStorage.getItem(args.key);
            return value;
        }
    },
    // ... more tools
]
```

**Key Points**:
- Frontend defines tool name, description, parameters
- Provides fn function for actual execution
- Follows OpenAI Function Calling specification

---

### Step 2️⃣: Send to Backend (autoai-chat.js)

**Location**: `autoai-core/src/main/resources/static/auto-ai/autoai-chat.js:55-62`

```javascript
async chatStream(messages, signal, onChunk, onComplete, onError, frontendTools) {
    const payload = {
        messages,
        stream: true,
        sessionId: this.sessionId,
        environment_context: this.getEnvironmentContext(),
        frontend_tools: frontendTools || []  // 👈 Frontend tool definitions
    };

    const response = await fetch(`${this.baseUrl}/v1/chat/stream`, {
        method: "POST",
        headers: this.getRequestHeaders(),
        body: JSON.stringify(payload),
        signal: signal
    });
    // ...
}
```

**Key Points**:
- `frontend_tools` field passes frontend tool definitions
- Format: `ToolSpec[]`

---

### Step 3️⃣: Backend Receives and Builds (ReActEngine)

**Location**: `ReActEngine.java:141-142`

```java
List<ToolSpec> toolSpecs = buildToolSpecs(request);  // Build tool list
applySystemPrompt(session, toolSpecs, request.getEnvironmentContext(), request);
```

**Location**: `ReActEngine.java:1695-1711` (buildToolSpecs method)

```java
private List<ToolSpec> buildToolSpecs(ChatCompletionRequest request) {
    List<ToolSpec> specs = new ArrayList<>();

    // 1. Add backend tool details
    specs.add(buildToolDetailSpec());

    // 2. Add all backend registered tools
    for (ToolSummary summary : toolRegistry.listSummaries()) {
        ToolFunctionSpec function = new ToolFunctionSpec();
        function.setName(summary.getName());
        function.setDescription(summary.getDescription());

        Optional<ToolDetail> detail = toolRegistry.getDetail(summary.getName());
        if (detail.isPresent()) {
            Map<String, Object> basicSchema = buildBasicSchema(detail.get());
            function.setParameters(basicSchema);
        }

        specs.add(new ToolSpec(function));
    }

    // 3. Add frontend tool definitions 👈 Key step
    if (request.getFrontendTools() != null && !request.getFrontendTools().isEmpty()) {
        specs.addAll(request.getFrontendTools());  // Directly add frontend tools
    }

    return specs;
}
```

**Key Points**:
- Frontend tools are directly added to `toolSpecs` list
- Include complete parameter schema definitions

---

### Step 4️⃣: System Prompt Generation

**Location**: `ReActEngine.java:1468-1504`

```java
private String buildSystemPrompt(boolean detailed, List<ToolSpec> toolSpecs, ChatCompletionRequest request) {
    StringBuilder toolDescriptions = new StringBuilder();

    for (ToolSpec spec : toolSpecs) {
        if (spec.getFunction() == null) {
            continue;
        }
        ToolFunctionSpec function = spec.getFunction();
        String name = function.getName();

        // 👇 Key: Filter out frontend tools
        if (request.getFrontendTools() != null) {
            boolean isFrontendTool = request.getFrontendTools().stream()
                    .anyMatch(ft -> name.equals(ft.getFunction().getName()));
            if (isFrontendTool) {
                continue;  // Skip frontend tools, not listed in system prompt
            }
        }

        String desc = function.getDescription();
        toolDescriptions.append("- ").append(name).append(": ").append(desc);
        // ... Add examples, etc.
    }

    return buildChineseSystemPrompt(toolDescriptions);
}
```

**Key Points**:
- ⚠️ **Frontend tools are filtered out, not in system prompt's "Available Tools" list**
- This causes AI to not know about frontend tools in text format

---

### Step 5️⃣: Pass to LLM

**Location**: `ReActEngine.java:1656-1668` (copyRequest method)

```java
private ChatCompletionRequest copyRequest(ChatCompletionRequest original, List<ChatMessage> messages,
                                          List<ToolSpec> toolSpecs, String modelName) {
    ChatCompletionRequest request = new ChatCompletionRequest();
    request.setModel(modelName);
    request.setMessages(messages);
    request.setTools(toolSpecs);  // 👈 Includes frontend tool complete definitions
    request.setToolChoice(toolChoice == null ? "auto" : toolChoice);
    request.setTemperature(original.getTemperature());
    request.setMaxTokens(original.getMaxTokens());
    request.setStream(original.getStream());
    return request;
}
```

**Key Points**:
- ✅ `request.setTools(toolSpecs)` - Frontend tool definitions are passed to LLM via this parameter
- Follows OpenAI Function Calling specification

---

### Step 6️⃣: LLM Returns tool_calls

**Expected Format**:
```json
{
  "content": null,
  "tool_calls": [
    {
      "id": "call_abc123",
      "type": "function",
      "function": {
        "name": "getAllCookies",
        "arguments": "{\"key\":\"value\"}"
      }
    }
  ]
}
```

**Key Points**:
- LLM should call frontend tools via `tool_calls` format
- Not text format `ACTION: getAllCookies()`

---

### Step 7️⃣: Backend Identifies and Executes Frontend Tool

**Location**: `ReActEngine.java:626-666` (executeToolCall method)

```java
private String executeToolCall(ToolCall toolCall, RequestContext requestContext,
                               ChatCompletionRequest request, TypedStreamCallback typedCallback,
                               String sessionId) {
    ToolCallFunction function = toolCall.getFunction();
    String name = function.getName();

    // 1. Check if it's frontend tool
    boolean isFrontendTool = request.getFrontendTools() != null &&
            request.getFrontendTools().stream()
                    .anyMatch(spec -> name.equals(spec.getFunction().getName()));

    if (isFrontendTool) {
        // 2. Execute frontend tool
        try {
            return frontendToolInvoker.invoke(toolCall, sessionId, typedCallback);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "❌ Tool call failed: Call interrupted";
        } catch (Exception e) {
            return "❌ Tool call failed: " + e.getMessage();
        }
    }

    // 3. Otherwise execute backend tool
    // ...
}
```

**Key Points**:
- Determine if it's frontend tool via `request.getFrontendTools()`
- Call `FrontendToolInvoker.invoke()` to execute

---

### Step 8️⃣: SSE Notify Frontend

**Location**: `FrontendToolInvoker.java:93-116`

```java
private void sendToolCallNotification(ToolCall toolCall, String callId, TypedStreamCallback typedCallback) {
    try {
        // Build notification message
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("type", "FRONTEND_TOOL_CALL");
        notification.put("callId", callId);
        notification.put("toolCall", toolCall);

        // Convert to JSON string
        String jsonStr = objectMapper.writeValueAsString(notification);

        // Send via SSE
        typedCallback.onTypedChunk(ContentType.ACTION, "[Frontend Tool Call] " + jsonStr);
    } catch (Exception e) {
        logger.error("Failed to send frontend tool call notification: {}", e.getMessage());
    }
}
```

**SSE Message Format**:
```
data: {"type":"action", "content": "[Frontend Tool Call] {\"type\":\"FRONTEND_TOOL_CALL\",\"callId\":\"xxx\",\"toolCall\":{...}}", "taskId": null}

```

**Key Points**:
- Format: `[Frontend Tool Call] {json}`
- Frontend needs to parse this message

---

### Step 9️⃣: Frontend Executes Tool

**Location**: `autoai-chat.js:1095-1108`

```javascript
// In onChunk callback
if (content && content.includes('[Frontend Tool Call]')) {
    try {
        const jsonStr = content.replace('[Frontend Tool Call]', '').trim();
        const notification = JSON.parse(jsonStr);
        if (notification.type === 'FRONTEND_TOOL_CALL') {
            // Handle frontend tool call
            this.handleFrontendToolCall(notification.toolCall, notification.callId);
            return; // Don't show tool call notification, only execute tool
        }
    } catch (e) {
        console.error('Failed to parse frontend tool call notification:', e);
    }
}
```

**Location**: `autoai-chat.js:236-252` (handleFrontendToolCall)

```javascript
async handleFrontendToolCall(toolCall, callId) {
    const toolName = toolCall.function?.name;
    const args = toolCall.function?.arguments;

    // Find tool from registered tools
    const tool = this.frontendTools.get(toolName);
    if (!tool) {
        console.error(`Frontend tool not found: ${toolName}`);
        return this.sendToolResult(callId, null, "Tool not found", true);
    }

    try {
        // Parse parameters and execute
        const parsedArgs = typeof args === 'string' ? JSON.parse(args) : args;
        const result = await tool.fn(parsedArgs);
        this.sendToolResult(callId, result, null, false);
    } catch (error) {
        this.sendToolResult(callId, null, error.message, true);
    }
}
```

**Key Points**:
- Frontend finds tool from `this.frontendTools` Map
- Call tool's `fn` function to execute
- Return result via `sendToolResult`

---

### Step 🔟: Return Result to Backend

**Location**: `autoai-chat.js:257-278`

```javascript
async sendToolResult(callId, result, error, isError) {
    const response = await fetch(`${this.baseUrl}/v1/chat/tool-result`, {
        method: 'POST',
        headers: this.client.getRequestHeaders(),
        body: JSON.stringify({
            sessionId: this.client.sessionId,
            toolCall: {
                callId,
                result,
                error,
                isError
            }
        })
    });

    if (!response.ok) {
        console.error('Failed to send tool result:', response.status);
    }
}
```

**Request Format**:
```json
POST /v1/chat/tool-result
{
    "sessionId": "xxx",
    "toolCall": {
        "callId": "call_abc123",
        "result": {...},
        "error": null,
        "isError": false
    }
}
```

---

### Step 1️⃣1️⃣: Backend Receives Result and Returns to LLM

**Location**: `AutoAiStreamController.java:128-159`

```java
@PostMapping("/chat/tool-result")
public ResponseEntity<Map<String, Object>> receiveToolResult(@RequestBody Map<String, Object> payload) {
    String sessionId = (String) payload.get("sessionId");
    Map<String, Object> toolCallData = (Map<String, Object>) payload.get("toolCall");

    String callId = (String) toolCallData.get("callId");
    Object result = toolCallData.get("result");
    String error = (String) toolCallData.get("error");
    boolean isError = (Boolean) toolCallData.get("isError");

    // Complete tool call, set result
    boolean success = frontendToolManager.completeToolCall(sessionId, callId, result, error, isError);

    Map<String, Object> response = new HashMap<>();
    response.put("success", success);
    if (success) {
        response.put("message", "Tool result received");
    }
    return ResponseEntity.ok(response);
}
```

**Location**: `FrontendToolManager.java:91-122` (completeToolCall)

```java
public boolean completeToolCall(String sessionId, String callId, Object result, String error, boolean isError) {
    String key = sessionId + ":" + callId;
    CompletableFuture<String> future = pendingToolCalls.remove(key);

    if (future == null) {
        logger.warn("Received result for unknown tool call: sessionId={}, callId={}", sessionId, callId);
        return false;
    }

    try {
        String resultStr;
        if (isError) {
            resultStr = "❌ Tool call failed: " + (error != null ? error : "Unknown error");
        } else {
            if (result == null) {
                resultStr = "✅ Tool call succeeded: null";
            } else if (result instanceof String) {
                resultStr = "✅ Tool call succeeded: " + result;
            } else {
                resultStr = "✅ Tool call succeeded: " + objectMapper.writeValueAsString(result);
            }
        }

        future.complete(resultStr);  // 👈 Unblock, return result
        return true;
    } catch (Exception e) {
        logger.error("Failed to set tool call result: {}", e.getMessage());
        future.completeExceptionally(e);
        return false;
    }
}
```

**Location**: `FrontendToolInvoker.java:55-72` (invoke method)

```java
public String invoke(ToolCall toolCall, String sessionId, TypedStreamCallback typedCallback)
        throws InterruptedException {
    // 1. Register tool call, get callId
    String callId = frontendToolManager.registerToolCall(sessionId, toolCall);

    // 2. Send tool call request to frontend via SSE
    if (typedCallback != null) {
        sendToolCallNotification(toolCall, callId, typedCallback);
    }

    // 3. Wait for frontend to return result (block wait, max 30 seconds）👈 Blocking point
    String result = frontendToolManager.waitForResult(sessionId, callId);

    return result;
}
```

**Key Points**:
- `CompletableFuture.get()` blocks waiting for frontend return
- Max wait 30 seconds
- After frontend returns, `future.complete(resultStr)` unblocks
- Result returned to ReActEngine, then returned to LLM

---

## III. Key Data Structures

```java
// Frontend tool definition
ToolSpec {
    function: ToolFunctionSpec {
        name: "getAllCookies",
        description: "Get all page cookies",
        parameters: {
            type: "object",
            properties: {...},
            required: [...]
        }
    }
}

// Tool call
ToolCall {
    id: "call_abc123",
    type: "function",
    function: {
        name: "getAllCookies",
        arguments: "{\"key\":\"value\"}"
    }
}
```

---

## IV. Sequence Diagram

```
User          FrontendJS          BackendReActEngine          LLM          FrontendToolManager
 │              │                    │                  │                    │
 │  Register Tool     │                    │                  │                    │
 │──────────────>│                    │                  │                    │
 │              │                    │                  │                    │
 │  Send Message     │                    │                  │                    │
 │──────────────>│ frontend_tools     │                  │                    │
 │              │────────────────────>│                  │                    │
 │              │                    │ Build toolSpecs    │                    │
 │              │                    │ Generate system prompt│                    │
 │              │                    │ setTools(specs)  │                    │
 │              │                    │─────────────────>│                    │
 │              │                    │                  │                    │
 │              │                    │  Return tool_calls   │                    │
 │              │                    │<─────────────────│                    │
 │              │                    │                  │                    │
 │              │                    │  Identify frontend tool│                    │
 │              │                    │ registerToolCall │                    │
 │              │                    │───────────────────>│                    │
 │              │                    │                  │ Save Future         │
 │              │                    │<───────────────────│                    │
 │              │                    │                  │                    │
 │  SSE Notify     │                    │                  │                    │
 │<─────────────│ [Frontend Tool Call]     │                  │                    │
 │              │                    │                  │                    │
 │  Execute Tool     │                    │                  │                    │
 │  getAllCookies│                   │                  │                    │
 │  Return Result    │                    │                  │                    │
 │──────────────>│ POST /tool-result │                  │                    │
 │              │────────────────────>│                  │                    │
 │              │                    │ completeToolCall │                    │
 │              │                    │───────────────────>│                  │
 │              │                    │                  │ future.complete()  │
 │              │                    │<───────────────────│                    │
 │              │                    │  Return result      │                    │
 │              │                    │                  │                    │
```

---

## V. Summary

### Complete Flow Chain

```
1. Frontend registers tools
   └─> frontendTools array

2. Send to backend
   └─> frontend_tools field

3. Backend builds toolSpecs
   └─> Include backend tools + frontend tools

4. Generate system prompt
   └─> Current: Filter out frontend tools
   └─> Issue: AI doesn't know about frontend tools

5. Pass to LLM
   └─> setTools(toolSpecs) ✅ Frontend tools in tools parameter

6. LLM returns
   └─> tool_calls format ✅
   └─> Or text format ❌

7. Backend executes
   └─> Identify frontend tool
   └─> Call FrontendToolInvoker

8. SSE notify frontend
   └─> [Frontend Tool Call] {json}

9. Frontend executes
   └─> Find tool and execute fn

10. Return result
    └─> POST /v1/chat/tool-result

11. Backend receives
    └─> completeToolCall
    └─> future.complete()
    └─> Unblock

12. Return to LLM
    └─> Continue conversation
```
---
