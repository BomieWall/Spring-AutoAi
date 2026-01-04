# Tool Discovery and Dynamic Information Acquisition Architecture

## Problem Background

In AI tool invocation scenarios, the following challenges exist:

1. **Token Efficiency Issues**: Complete structures and examples of complex object parameters consume a large number of tokens
2. **Missing Information Acquisition Mechanism**: AI has no way to dynamically obtain detailed parameter information for tools
3. **User Experience Issues**: AI may fail to correctly invoke tools due to lack of detailed information
4. **Performance Issues**: Scanning all Beans during startup in large projects consumes time

## Solution Architecture

### Core Design Philosophy

**Layered Information Acquisition**: Divide tool information into two layers
- **Base Layer**: Tool name and brief description (always visible, minimal token consumption)
- **Detail Layer**: Complete parameter structures and examples (obtained on demand to save tokens)

**Performance Optimization**: Precisely control tool scanning scope
- Small projects: Default scan all Beans
- Large projects: Use `@AutoAiToolScan` to precisely control scanning scope

### Architecture Components

```
┌─────────────────────────────────────────────────────────────┐
│                        AI Agent                             │
├─────────────────────────────────────────────────────────────┤
│  1. Get Tool List (getAvailableTools)                         │
│  2. Get Tool Details on Demand (getToolDetail)                       │
│  3. Construct Parameters and Invoke Tool                              │
└─────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────┐
│                   ToolRegistry                              │
├─────────────────────────────────────────────────────────────┤
│  • listSummaries(): Returns tool brief list                        │
│  • getDetail(name): Returns tool detailed information                       │
│    - Parameter structure                                                │
│    - Parameter examples                                                │
│    - Return type                                                │
│    - Usage examples                                                │
└─────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────┐
│                  AutoAiToolScanner                          │
├─────────────────────────────────────────────────────────────┤
│  • Scan Spring Beans (according to @AutoAiToolScan configuration)            │
│  • Discover @AutoAiTool annotations                                     │
│  • Build ToolDefinition                                      │
│  • Register to ToolRegistry                                      │
└─────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────┐
│                  ExampleGenerator                           │
├─────────────────────────────────────────────────────────────┤
│  • Parse class structure via reflection                                        │
│  • Generate meaningful example values                                        │
│  • Support complex objects and nested structures                                    │
└─────────────────────────────────────────────────────────────┘
```

## Tool Discovery Mechanism

### 1. Auto Scan (Default)

**Applicable Scenarios**: Small projects (< 100 Beans)

```java
@SpringBootApplication
@EnableAutoAi
public class MyApp {
    // Scan all Spring Beans
}
```

**Workflow**:
```
Spring Boot Startup
    │
    ▼
AutoAiToolScanner Starts (SmartLifecycle）
    │
    ▼
Iterate All Spring Beans
    │
    ▼
Check Methods for @AutoAiTool Annotation Using Reflection
    │
    ▼
ToolDefinitionBuilder Builds Tool Metadata
    │
    ├─► Extract method signature
    ├─► Build parameter metadata
    ├─► Generate JSON Schema
    ├─► Generate example values
    └─► Determine call mode (method/REST API)
    │
    ▼
Register to InMemoryToolRegistry
```

### 2. Precise Scan (Performance Optimization)

**Applicable Scenarios**: Large projects (> 500 Beans)

```java
@SpringBootApplication
@EnableAutoAi
@AutoAiToolScan(classes = {DemoTools.class})  // Only scan specified classes
public class MyApp {
    // ...
}
```

**Usage Methods**:

**Method 1: Scan by Class**
```java
@AutoAiToolScan(classes = {DemoTools.class, UserService.class})
```

**Method 2: Scan by Package**
```java
@AutoAiToolScan({"com.example.myapp.tools", "com.example.myapp.services"})
```

**Method 3: Mixed Usage**
```java
@AutoAiToolScan(value = {"com.example.myapp.tools"}, classes = {SpecialTool.class})
```

**Performance Comparison**:

| Project Scale | Bean Count | Scan Strategy | Startup Time |
|---------------|-----------|---------------|---------------|
| Small | < 100 | Default scan | < 100ms |
| Medium | 100-500 | By package scan | < 200ms |
| Large | > 500 | Specify classes | < 100ms |

## Workflow

### 1. Initialization Stage

```
AI Agent Starts
    ↓
Call listSummaries()
    ↓
Get Tool Brief List (save tokens）
    ↓
Cache Tool List
```

**Return Format**:
```json
{
  "tools": [
    {
      "name": "createEmployee",
      "description": "Create new employee, requires providing employee basic information"
    },
    {
      "name": "batchUpdateSalary",
      "description": "Batch update employee salary, supports increasing, decreasing, or setting salary"
    }
  ]
}
```

### 2. Tool Invocation Stage

```
User Request
    ↓
AI Identifies Required Tool
    ↓
Call getToolDetail(toolName)
    ↓
Get Detailed Parameter Structure and Examples
    ↓
Construct Parameters Based on User Input
    ↓
Call Actual Tool Method
    ↓
Return Result to User
```

**Return Format**:
```json
{
  "toolInfo": {
    "name": "createEmployee",
    "description": "Create new employee, requires providing employee basic information",
    "parameters": [
      {
        "name": "request",
        "type": "EmployeeRequest",
        "fullTypeName": "cn.autoai.demo.console.DemoTools$EmployeeRequest",
        "example": "{\"name\":\"Zhang San\",\"department\":\"Technology Department\",\"salary\":50000.0,\"position\":\"Engineer\",\"joinDate\":\"2024-01-01\"}",
        "isComplexObject": true
      }
    ],
    "returnType": "EmployeeResult",
    "returnExample": "{\"success\":true,\"message\":\"Created successfully\",\"employee\":{...}}"
  }
}
```

## Core API Design

### ToolRegistry API

#### 1. listSummaries()

**Functionality**: Get brief information of all available tools

**Returns**: `List<ToolSummary>`

**ToolSummary Structure**:
```java
public class ToolSummary {
    private String name;              // Tool name
    private String description;       // Tool description
    private boolean isRestApiCall;    // Whether it's REST API invocation mode
}
```

#### 2. getDetail(name)

**Functionality**: Get detailed information of specified tool

**Parameter**: `name` - Tool name

**Returns**: `Optional<ToolDetail>`

**ToolDetail Structure**:
```java
public class ToolDetail {
    private String name;              // Tool name
    private String description;       // Tool description
    private List<ParameterInfo> parameters;  // Parameter list
    private String returnType;        // Return type
    private String returnExample;     // Return value example
    private String requestExample;    // Request example
    private String responseExample;   // Response example
}
```

## Implementation Features

### 1. Smart Example Generation

**Field Semantic Recognition**: Generate meaningful example values based on field names

| Field Name | Example Value | Type |
|-----------|--------------|------|
| `name` | "Zhang San" | String |
| `department` | "Technology Department" | String |
| `salary` | 50000.0 | Double |
| `operation` | "INCREASE" | Enum |
| `age` | 30 | Integer |
| `email` | "zhangsan@example.com" | String |
| `enabled` | true | Boolean |
| `id` | "12345" | String |

**Nested Object Support**: Automatically handle complex nested structures

```json
{
  "updates": [
    {
      "employeeName": "Zhang San",
      "operation": "INCREASE",
      "amount": 5000.0
    }
  ],
  "reason": "Excellent performance"
}
```

### 2. Reflection Mechanism

**Field Parsing**:
- Get class public fields via reflection
- Infer private fields via setter methods
- Support Java Bean specification

**Type Handling**:
- Basic types: int, long, double, boolean, etc.
- Collection types: List, Set, Map
- Custom objects: Recursively parse field structure
- Enum types: Extract enum values

### 3. Tool Invocation Mode Recognition

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

**Recognition Characteristics**:
- Method defined in regular Bean
- Uses `@Component`, `@Service`, etc. annotations

**REST API Invocation Mode**:
```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    @GetMapping
    @AutoAiTool(description = "Get list of all orders")
    public List<Order> getAllOrders(
        @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        return orderService.findAll();
    }
}
```

**Recognition Characteristics**:
- Method defined in `@RestController`
- Uses `@RequestMapping`, `@GetMapping`, `@PostMapping`, etc. annotations
- Tool definition contains HTTP method and URL

### 4. Error Handling

- **Tool Not Found**: Return `Optional.empty()` or friendly error message
- **Parameter Error**: Provide detailed error description
- **Reflection Exception**: Gracefully fallback to default examples
- **Type Conversion Failure**: Log error and return null value or default value

## Built-in Tools

### autoai.tool_detail

Framework provides `autoai.tool_detail` built-in tool, allowing LLM to dynamically query tool details.

**Tool Definition**:
```java
@AutoAiTool(
    name = "autoai.tool_detail",
    description = "Get detailed parameter structure and usage examples for tools"
)
public ToolDetail getToolDetail(@AutoAiParam("tool_name") String toolName)
```

**AI Usage Example**:
```
User: How to use createEmployee tool?

AI:
🤔 Thinking Process
User wants to understand how to use createEmployee tool, I should call autoai.tool_detail to get detailed information

⚡ Execute Action
autoai.tool_detail("createEmployee")

👁️ Observation Result
Tool details: {
  "parameters": [
    {
      "name": "request",
      "type": "EmployeeRequest",
      "example": "{\"name\":\"Zhang San\",\"department\":\"Technology Department\"}"
    }
  ]
}

✨ Final Answer
createEmployee tool is used to create new employees...
```

## Usage Examples

### AI Workflow Example

```java
// Step 1: Get tool list
List<ToolSummary> toolList = toolRegistry.listSummaries();

// Step 2: Get specific tool details
Optional<ToolDetail> detail = toolRegistry.getDetail("createEmployee");

if (detail.isPresent()) {
    // Step 3: Construct parameters based on details
    String params = """
    {
      "request": {
        "name": "Liu Ba",
        "department": "Marketing Department",
        "salary": 60000.0,
        "position": "Marketing Specialist",
        "joinDate": "2024-12-23"
      }
    }
    """;

    // Step 4: Invoke tool
    Object result = toolInvoker.invoke(toolDefinition, params);
}
```

## Best Practices

### 1. Tool Naming

- Use dot notation: `user.profile.get`
- Descriptive names: `calculate.sum` is better than `calc`
- Avoid abbreviations: `createEmployee` is better than `createEmp`

### 2. Tool Description

- Describe functionality: What it does
- Describe parameters: What it needs
- Describe return value: What it returns
- Provide examples: How to use

**Good Example**:
```
Create new employee, requires providing employee basic information, including name, department, position, salary and join date
```

**Bad Example**:
```
Create employee
```

### 3. Parameter Design

- **Use Complex Types**: Organize related parameters into objects
- **Add Field Examples**: Use `@AutoAiField` to help LLM understand
- **Type Safety**: Prioritize strong types
- **Parameter Validation**: Validate parameters in tool methods

**Example**:
```java
public static class EmployeeRequest {
    @AutoAiField(description = "Employee name", required = true, example = "Zhang San")
    public String name;

    @AutoAiField(description = "Department", example = "Technology Department")
    public String department;

    @AutoAiField(description = "Salary", example = "50000.0")
    public Double salary;
}

@AutoAiTool(description = "Create new employee")
public EmployeeResult createEmployee(EmployeeRequest request) {
    // ...
}
```

### 4. Performance Optimization

For large projects, use `@AutoAiToolScan` to precisely control scanning scope:

```java
@SpringBootApplication
@EnableAutoAi
@AutoAiToolScan(classes = {
    UserService.class,
    OrderService.class,
    PaymentService.class
})
public class MyApp {
    // Only scan specified classes, significantly improve startup performance
}
```

## Advantages Summary

### 1. Token Efficiency

- **At Initialization**: Only transmit tool names and brief descriptions
- **On Demand**: Only get detailed information when needed
- **Reduce Redundancy**: Avoid transmitting unnecessary complex structures

**Comparison**:

| Method | Token Consumption | Accuracy |
|--------|-----------------|-----------|
| One-time transmission of all information | High (10,000+ tokens) | High |
| Layered information acquisition | Low (1,000 tokens) | High |

### 2. Flexibility

- **Dynamic Discovery**: Runtime discovery of available tools
- **Structure Aware**: Automatically understand parameter structure
- **Example Generation**: Automatically generate meaningful examples
- **Mode Recognition**: Automatically recognize tool invocation mode

### 3. Maintainability

- **Automation**: No need to manually maintain tool documentation
- **Consistency**: Unified information acquisition interface
- **Extensibility**: Easy to add new tools
- **Type Safety**: Compile-time checks

### 4. User Experience

- **Accurate Invocation**: AI can correctly construct complex parameters
- **Error Friendly**: Provide clear error messages
- **Fast Response**: Efficient information acquisition mechanism
- **Smart Hints**: Built-in tools help AI understand how to use other tools
