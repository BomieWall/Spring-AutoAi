package cn.autoai.demo.web.controller;

import cn.autoai.core.annotation.AutoAiField;
import cn.autoai.core.annotation.AutoAiParam;
import cn.autoai.core.annotation.AutoAiTool;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User management Controller example
 * Demonstrates using @AutoAiTool annotation on Controller methods and calling via RestTemplate
 *
 * This Controller demonstrates:
 * 1. Annotating REST APIs with @AutoAiTool
 * 2. Spring Security authentication (retrieved from request)
 * 3. RESTful API design
 */
@RestController
@RequestMapping("/api/users")
public class UserManagementController {

    // Simulated database
    private static final Map<Long, User> userDatabase = new ConcurrentHashMap<>();
    private static Long idGenerator = 1L;

    static {
        // Initialize some test data
        User user1 = new User(null, "Zhang San", "zhangsan@example.com", "Technology Department", 50000.0);
        user1.setId(1L);
        userDatabase.put(1L, user1);

        User user2 = new User(null, "Li Si", "lisi@example.com", "Sales Department", 45000.0);
        user2.setId(2L);
        userDatabase.put(2L, user2);

        User user3 = new User(null, "Wang Wu", "wangwu@example.com", "Finance Department", 55000.0);
        user3.setId(3L);
        userDatabase.put(3L, user3);

        idGenerator = 4L;
    }

    /**
     * Get list of all users
     * AutoAi will recognize this as a REST API tool, called via HTTP GET
     */
    @GetMapping
    @AutoAiTool(description = "Get list of all users in the system")
    public List<User> getAllUsers() {
        return new ArrayList<>(userDatabase.values());
    }

    /**
     * Query user by ID
     * Path parameters will be automatically recognized and processed
     */
    @GetMapping("/{id}")
    @AutoAiTool(description = "Query detailed information for a single user by user ID")
    public User getUserById(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        User user = userDatabase.get(id);
        if (user == null) {
            throw new RuntimeException("User does not exist: " + id);
        }

        // Log access (simulated authentication check)
        System.out.println("User " + userId + " queried user information: " + id);

        return user;
    }

    /**
     * Query users by department
     * Query parameters will be automatically recognized and processed
     */
    @GetMapping("/search/by-department")
    @AutoAiTool(description = "Query all users in a department by department name")
    public List<User> getUsersByDepartment(
            @RequestParam String department,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        System.out.println("Received Authorization Header: " + authorization);

        return userDatabase.values().stream()
                .filter(user -> department.equals(user.getDepartment()))
                .toList();
    }

    /**
     * Create a new user
     * POST request, uses @RequestBody to receive JSON data
     */
    @PostMapping
    @AutoAiTool(description = "Create a new user, but the name must be in English before creation")
    public UserResponse createUser(@RequestBody CreateUserRequest request,
                                   @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        System.out.println("Tenant ID: " + tenantId);

        // Validate if email already exists
        boolean emailExists = userDatabase.values().stream()
                .anyMatch(user -> user.getEmail().equals(request.getEmail()));
        if (emailExists) {
            throw new RuntimeException("Email already in use: " + request.getEmail());
        }

        User newUser = createUser(new User(
                null,
                request.getName(),
                request.getEmail(),
                request.getDepartment(),
                request.getSalary()
        ));

        return new UserResponse(true, "User created successfully", newUser);
    }

    /**
     * Update user information
     */
    @PutMapping("/{id}")
    @AutoAiTool(description = "Update user information for specified ID")
    public UserResponse updateUser(
            @PathVariable Long id,
            @RequestBody UpdateUserRequest request,
            @CookieValue(value = "sessionId", required = false) String sessionId
    ) {
        System.out.println("Session ID: " + sessionId);

        User existingUser = userDatabase.get(id);
        if (existingUser == null) {
            throw new RuntimeException("User does not exist: " + id);
        }

        // Update fields
        if (request.getName() != null) {
            existingUser.setName(request.getName());
        }
        if (request.getEmail() != null) {
            existingUser.setEmail(request.getEmail());
        }
        if (request.getDepartment() != null) {
            existingUser.setDepartment(request.getDepartment());
        }
        if (request.getSalary() != null) {
            existingUser.setSalary(request.getSalary());
        }

        userDatabase.put(id, existingUser);

        return new UserResponse(true, "User updated successfully", existingUser);
    }

    /**
     * Delete user
     */
    @DeleteMapping("/{id}")
    @AutoAiTool(description = "Delete user with specified ID")
    public Map<String, Object> deleteUser(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        System.out.println("Operating user: " + userId);

        User removed = userDatabase.remove(id);
        if (removed == null) {
            throw new RuntimeException("User does not exist: " + id);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "User deleted successfully");
        result.put("deletedUser", removed);
        return result;
    }

    /**
     * Adjust user salary
     */
    @PostMapping("/{id}/salary")
    @AutoAiTool(description = "Adjust salary for specified user")
    public User adjustSalary(
            @PathVariable Long id,
            @RequestBody SalaryAdjustRequest request,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        System.out.println("Authorization: " + auth);

        User user = userDatabase.get(id);
        if (user == null) {
            throw new RuntimeException("User does not exist: " + id);
        }

        double currentSalary = user.getSalary();
        double newSalary;

        if ("INCREASE".equalsIgnoreCase(request.getOperation())) {
            newSalary = currentSalary + request.getAmount();
        } else if ("DECREASE".equalsIgnoreCase(request.getOperation())) {
            newSalary = Math.max(0, currentSalary - request.getAmount());
        } else if ("SET".equalsIgnoreCase(request.getOperation())) {
            newSalary = request.getAmount();
        } else {
            throw new RuntimeException("Invalid operation: " + request.getOperation());
        }

        user.setSalary(newSalary);
        userDatabase.put(id, user);

        return user;
    }

    // =============== Helper Methods ===============

    private User createUser(User user) {
        Long id = idGenerator++;
        user.setId(id);
        userDatabase.put(id, user);
        return user;
    }

    // =============== Entity Class Definitions ===============

    /**
     * User entity
     */
    public static class User {
        private Long id;
        private String name;
        private String email;
        private String department;
        private Double salary;

        public User() {}

        public User(Long id, String name, String email, String department, Double salary) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.department = department;
            this.salary = salary;
        }

        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }

        public Double getSalary() { return salary; }
        public void setSalary(Double salary) { this.salary = salary; }

        @Override
        public String toString() {
            return "User{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", email='" + email + '\'' +
                    ", department='" + department + '\'' +
                    ", salary=" + salary +
                    '}';
        }
    }

    /**
     * Create user request
     */
    public static class CreateUserRequest {
        @AutoAiField(description = "User name", required = true, example = "Zhang San")
        private String name;

        @AutoAiField(description = "User email", required = true, example = "zhangsan@example.com")
        private String email;

        @AutoAiField(description = "Department", example = "Technology Department")
        private String department;

        @AutoAiField(description = "Initial salary", example = "50000.0")
        private Double salary;

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }

        public Double getSalary() { return salary; }
        public void setSalary(Double salary) { this.salary = salary; }
    }

    /**
     * Update user request
     */
    public static class UpdateUserRequest {
        @AutoAiField(description = "New user name", example = "Li Si")
        private String name;

        @AutoAiField(description = "New user email", example = "lisi@example.com")
        private String email;

        @AutoAiField(description = "New department", example = "Sales Department")
        private String department;

        @AutoAiField(description = "New salary", example = "60000.0")
        private Double salary;

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }

        public Double getSalary() { return salary; }
        public void setSalary(Double salary) { this.salary = salary; }
    }

    /**
     * Salary adjustment request
     */
    public static class SalaryAdjustRequest {
        @AutoAiField(description = "Operation type: INCREASE (increase), DECREASE (decrease), SET (set)", required = true, example = "INCREASE")
        private String operation;

        @AutoAiField(description = "Adjustment amount", required = true, example = "5000.0")
        private Double amount;

        // Getters and Setters
        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }

        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
    }

    /**
     * User response
     */
    public static class UserResponse {
        private boolean success;
        private String message;
        private User user;

        public UserResponse() {}

        public UserResponse(boolean success, String message, User user) {
            this.success = success;
            this.message = message;
            this.user = user;
        }

        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public User getUser() { return user; }
        public void setUser(User user) { this.user = user; }
    }

    /**
     * 批量用户操作（复杂参数示例）
     * 支持批量创建、更新、删除用户，包含复杂的嵌套对象、列表、Map、枚举等数据类型
     */
    @PostMapping("/batch")
    @AutoAiTool(description = "批量用户操作，支持批量创建、更新、删除用户，包含复杂的嵌套对象、列表、Map、枚举等数据类型")
    public BatchOperationResult batchUserOperations(@RequestBody BatchUserOperationsRequest request,
                                                @RequestHeader(value = "X-Operator-Id", required = false) String operatorId) {
        System.out.println("操作人员: " + operatorId);

        if (request == null) {
            return new BatchOperationResult(false, "批量操作请求不能为空", null);
        }

        if (request.operations == null || request.operations.isEmpty()) {
            return new BatchOperationResult(false, "操作列表不能为空", null);
        }

        BatchOperationSummary summary = new BatchOperationSummary();
        summary.requestId = request.requestId;
        summary.operationType = request.operationType;
        summary.totalCount = request.operations.size();
        summary.successCount = 0;
        summary.failureCount = 0;
        summary.details = new ArrayList<>();
        summary.timestamp = System.currentTimeMillis();

        for (UserOperationItem item : request.operations) {
            try {
                UserOperationResult result = processUserOperation(item);
                summary.details.add(result);
                if (result.success) {
                    summary.successCount++;
                } else {
                    summary.failureCount++;
                }
            } catch (Exception e) {
                UserOperationResult errorResult = new UserOperationResult(
                    false, item.userId, "ERROR", "处理失败: " + e.getMessage());
                summary.details.add(errorResult);
                summary.failureCount++;
            }
        }

        return new BatchOperationResult(true, "批量操作完成", summary);
    }

    /**
     * 处理单个用户操作
     */
    private UserOperationResult processUserOperation(UserOperationItem item) {
        switch (item.operationType) {
            case CREATE:
                return handleCreateUser(item);
            case UPDATE:
                return handleUpdateUser(item);
            case DELETE:
                return handleDeleteUser(item);
            default:
                return new UserOperationResult(false, item.userId, "UNKNOWN_OPERATION",
                    "未知的操作类型: " + item.operationType);
        }
    }

    /**
     * 处理创建用户
     */
    private UserOperationResult handleCreateUser(UserOperationItem item) {
        if (item.userData == null) {
            return new UserOperationResult(false, item.userId, "VALIDATION_ERROR", "用户数据不能为空");
        }

        String email = item.userData.email;
        if (email == null || email.trim().isEmpty()) {
            return new UserOperationResult(false, item.userId, "VALIDATION_ERROR", "邮箱不能为空");
        }

        // 检查邮箱是否已存在
        boolean emailExists = userDatabase.values().stream()
            .anyMatch(user -> email.equals(user.getEmail()));
        if (emailExists) {
            return new UserOperationResult(false, item.userId, "DUPLICATE_EMAIL",
                "邮箱已存在: " + email);
        }

        User newUser = createUser(new User(
            null,
            item.userData.name,
            email,
            item.userData.department,
            item.userData.salary
        ));

        return new UserOperationResult(true, newUser.getId().toString(), "SUCCESS", "用户创建成功");
    }

    /**
     * 处理更新用户
     */
    private UserOperationResult handleUpdateUser(UserOperationItem item) {
        Long userId;
        if (item.userId == null || item.userId.trim().isEmpty()) {
            return new UserOperationResult(false, null, "VALIDATION_ERROR", "用户ID不能为空");
        }

        try {
            userId = Long.parseLong(item.userId);
        } catch (NumberFormatException e) {
            return new UserOperationResult(false, item.userId, "INVALID_ID", "用户ID格式错误");
        }

        User existingUser = userDatabase.get(userId);
        if (existingUser == null) {
            return new UserOperationResult(false, item.userId, "NOT_FOUND", "用户不存在");
        }

        if (item.userData != null) {
            if (item.userData.name != null) {
                existingUser.setName(item.userData.name);
            }
            if (item.userData.email != null) {
                existingUser.setEmail(item.userData.email);
            }
            if (item.userData.department != null) {
                existingUser.setDepartment(item.userData.department);
            }
            if (item.userData.salary != null) {
                existingUser.setSalary(item.userData.salary);
            }
        }

        userDatabase.put(userId, existingUser);
        return new UserOperationResult(true, item.userId, "SUCCESS", "用户更新成功");
    }

    /**
     * 处理删除用户
     */
    private UserOperationResult handleDeleteUser(UserOperationItem item) {
        Long userId;
        if (item.userId == null || item.userId.trim().isEmpty()) {
            return new UserOperationResult(false, null, "VALIDATION_ERROR", "用户ID不能为空");
        }

        try {
            userId = Long.parseLong(item.userId);
        } catch (NumberFormatException e) {
            return new UserOperationResult(false, item.userId, "INVALID_ID", "用户ID格式错误");
        }

        User removed = userDatabase.remove(userId);
        if (removed == null) {
            return new UserOperationResult(false, item.userId, "NOT_FOUND", "用户不存在");
        }

        return new UserOperationResult(true, item.userId, "SUCCESS", "用户删除成功");
    }

    // ========== 复杂参数内部类 ==========

    /**
     * 操作类型
     */
    public enum OperationType {
        CREATE("创建"),
        UPDATE("更新"),
        DELETE("删除");

        private final String description;

        OperationType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 批量操作请求
     */
    public static class BatchUserOperationsRequest {
        @AutoAiField(description = "请求ID，用于追踪", required = false, example = "REQ20240107001")
        public String requestId;

        @AutoAiField(description = "操作类型", required = false, example = "BATCH_UPDATE")
        public String operationType;

        @AutoAiField(description = "用户操作项列表", required = true)
        public List<UserOperationItem> operations;

        @AutoAiField(description = "是否在第一个失败时停止", required = false, example = "false")
        public boolean stopOnFirstError;

        @AutoAiField(description = "优先级", required = false, example = "HIGH")
        public String priority;

        @AutoAiField(description = "超时时间（秒）", required = false, example = "30")
        public int timeout;

        @AutoAiField(description = "自定义元数据", required = false)
        public Map<String, Object> metadata;

        @AutoAiField(description = "通知配置", required = false)
        public NotificationConfig notificationConfig;
    }

    /**
     * 用户操作项
     */
    public static class UserOperationItem {
        @AutoAiField(description = "用户ID（更新和删除时必填）", required = false, example = "1")
        public String userId;

        @AutoAiField(description = "操作类型", required = true, example = "CREATE")
        public OperationType operationType;

        @AutoAiField(description = "用户数据（创建和更新时需要）", required = false)
        public UserData userData;

        @AutoAiField(description = "备注", required = false, example = "部门调整")
        public String remarks;

        @AutoAiField(description = "标签", required = false)
        public List<String> tags;

        @AutoAiField(description = "自定义属性", required = false)
        public Map<String, String> customAttributes;
    }

    /**
     * 用户数据
     */
    public static class UserData {
        @AutoAiField(description = "用户姓名", required = true, example = "张三")
        public String name;

        @AutoAiField(description = "用户邮箱", required = true, example = "zhangsan@example.com")
        public String email;

        @AutoAiField(description = "部门", required = false, example = "技术部")
        public String department;

        @AutoAiField(description = "薪资", required = false, example = "50000.0")
        public Double salary;

        @AutoAiField(description = "职位", required = false, example = "高级工程师")
        public String position;

        @AutoAiField(description = "联系电话", required = false, example = "13800138000")
        public String phone;

        @AutoAiField(description = "入职日期", required = false, example = "2024-01-01")
        public String joinDate;

        @AutoAiField(description = "技能标签", required = false)
        public List<String> skills;

        @AutoAiField(description = "扩展信息", required = false)
        public Map<String, Object> extensions;
    }

    /**
     * 通知配置
     */
    public static class NotificationConfig {
        @AutoAiField(description = "是否发送邮件通知", required = false, example = "true")
        public boolean emailEnabled;

        @AutoAiField(description = "是否发送短信通知", required = false, example = "false")
        public boolean smsEnabled;

        @AutoAiField(description = "是否发送钉钉通知", required = false, example = "true")
        public boolean dingTalkEnabled;

        @AutoAiField(description = "通知接收人列表", required = false)
        public List<String> recipients;

        @AutoAiField(description = "通知模板", required = false, example = "BATCH_OPERATION")
        public String template;
    }

    /**
     * 用户操作结果
     */
    public static class UserOperationResult {
        public boolean success;
        public String userId;
        public String statusCode;
        public String message;

        public UserOperationResult() {}

        public UserOperationResult(boolean success, String userId, String statusCode, String message) {
            this.success = success;
            this.userId = userId;
            this.statusCode = statusCode;
            this.message = message;
        }
    }

    /**
     * 批量操作摘要
     */
    public static class BatchOperationSummary {
        public String requestId;
        public String operationType;
        public int totalCount;
        public int successCount;
        public int failureCount;
        public List<UserOperationResult> details;
        public long timestamp;
    }

    /**
     * 批量操作结果
     */
    public static class BatchOperationResult {
        public boolean success;
        public String message;
        public BatchOperationSummary summary;

        public BatchOperationResult() {}

        public BatchOperationResult(boolean success, String message, BatchOperationSummary summary) {
            this.success = success;
            this.message = message;
            this.summary = summary;
        }
    }
}
