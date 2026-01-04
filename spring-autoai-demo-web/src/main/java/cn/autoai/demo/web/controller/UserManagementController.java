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
}
