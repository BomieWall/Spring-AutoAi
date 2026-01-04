package cn.autoai.demo.web.controller;

import cn.autoai.core.annotation.AutoAiField;
import cn.autoai.core.annotation.AutoAiTool;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Order management Controller example
 * Demonstrates using @AutoAiTool annotation on Controller methods and calling via RestTemplate
 *
 * This Controller demonstrates:
 * 1. Annotating REST APIs with @AutoAiTool
 * 2. Spring Security authentication (retrieved from request)
 * 3. Complex business logic and data operations
 */
@RestController
@RequestMapping("/api/orders")
public class OrderManagementController {

    // Simulated database
    private static final Map<String, Order> orderDatabase = new ConcurrentHashMap<>();
    private static final Map<Long, Product> productDatabase = new ConcurrentHashMap<>();
    private static final Map<String, Customer> customerDatabase = new ConcurrentHashMap<>();
    private static String orderNoGenerator = "ORD2024010001";

    static {
        // Initialize product data
        productDatabase.put(1L, new Product(1L, "Laptop", "Electronics", new BigDecimal("5999.00"), 50));
        productDatabase.put(2L, new Product(2L, "Wireless Mouse", "Electronics", new BigDecimal("99.00"), 200));
        productDatabase.put(3L, new Product(3L, "Mechanical Keyboard", "Electronics", new BigDecimal("299.00"), 100));
        productDatabase.put(4L, new Product(4L, "Monitor", "Electronics", new BigDecimal("1299.00"), 80));

        // Initialize customer data
        customerDatabase.put("C001", new Customer("C001", "Zhang San", "zhangsan@example.com", "Beijing"));
        customerDatabase.put("C002", new Customer("C002", "Li Si", "lisi@example.com", "Shanghai"));

        // Initialize order data
        createOrderInternal("ORD2024010001", "C001", List.of(
                new OrderItem(1L, 1, new BigDecimal("5999.00")),
                new OrderItem(2L, 2, new BigDecimal("99.00"))
        ), "PENDING");

        createOrderInternal("ORD2024010002", "C002", List.of(
                new OrderItem(3L, 1, new BigDecimal("299.00"))
        ), "COMPLETED");

        createOrderInternal("ORD2024010003", "C001", List.of(
                new OrderItem(4L, 2, new BigDecimal("1299.00"))
        ), "SHIPPED");

        orderNoGenerator = "ORD2024010004";
    }

    /**
     * Get all orders
     */
    @GetMapping
    @AutoAiTool(description = "Get list of all orders in the system")
    public List<Order> getAllOrders(
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        System.out.println("Query all orders, Authorization: " + auth);
        return new ArrayList<>(orderDatabase.values());
    }

    /**
     * Query order by order number
     */
    @GetMapping("/{orderNo}")
    @AutoAiTool(description = "Query detailed information of a single order by order number")
    public Order getOrderByOrderNo(
            @PathVariable String orderNo,
            @CookieValue(value = "sessionId", required = false) String sessionId
    ) {
        System.out.println("Query order " + orderNo + ", Session ID: " + sessionId);

        Order order = orderDatabase.get(orderNo);
        if (order == null) {
            throw new RuntimeException("Order does not exist: " + orderNo);
        }
        return order;
    }

    /**
     * Query orders by customer ID
     */
    @GetMapping("/customer/{customerId}")
    @AutoAiTool(description = "Query all orders for a specified customer")
    public List<Order> getOrdersByCustomer(
            @PathVariable String customerId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId
    ) {
        System.out.println("Query orders for customer " + customerId + ", tenant ID: " + tenantId);

        if (!customerDatabase.containsKey(customerId)) {
            throw new RuntimeException("Customer does not exist: " + customerId);
        }

        return orderDatabase.values().stream()
                .filter(order -> customerId.equals(order.getCustomerId()))
                .collect(Collectors.toList());
    }

    /**
     * Query orders by status
     */
    @GetMapping("/status/{status}")
    @AutoAiTool(description = "Query all related orders by order status")
    public List<Order> getOrdersByStatus(
            @PathVariable String status,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        System.out.println("User " + userId + " querying orders with status " + status);

        return orderDatabase.values().stream()
                .filter(order -> status.equalsIgnoreCase(order.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * Create new order
     */
    @PostMapping
    @AutoAiTool(description = "Create a new order")
    public OrderResponse createOrder(
            @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        System.out.println("User " + userId + " creating order");

        // Verify customer
        if (!customerDatabase.containsKey(request.getCustomerId())) {
            throw new RuntimeException("Customer does not exist: " + request.getCustomerId());
        }

        // Verify products and calculate total amount
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();

        for (OrderItemRequest itemReq : request.getItems()) {
            Product product = productDatabase.get(itemReq.getProductId());
            if (product == null) {
                throw new RuntimeException("Product does not exist: " + itemReq.getProductId());
            }

            if (product.getStock() < itemReq.getQuantity()) {
                throw new RuntimeException("Insufficient product stock: " + product.getName());
            }

            BigDecimal itemTotal = product.getPrice().multiply(new BigDecimal(itemReq.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);

            items.add(new OrderItem(itemReq.getProductId(), itemReq.getQuantity(), product.getPrice()));
        }

        // Generate order number
        String orderNo = generateOrderNo();

        // Create order
        Order order = new Order(
                orderNo,
                request.getCustomerId(),
                items,
                totalAmount,
                "PENDING",
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        );

        orderDatabase.put(orderNo, order);

        // Update inventory (simplified handling)
        for (OrderItem item : items) {
            Product product = productDatabase.get(item.getProductId());
            product.setStock(product.getStock() - item.getQuantity());
        }

        return new OrderResponse(true, "Order created successfully", order);
    }

    /**
     * Update order status
     */
    @PutMapping("/{orderNo}/status")
    @AutoAiTool(description = "Update status of the specified order")
    public Order updateOrderStatus(
            @PathVariable String orderNo,
            @RequestBody UpdateStatusRequest request,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        System.out.println("Authorization: " + auth);

        Order order = orderDatabase.get(orderNo);
        if (order == null) {
            throw new RuntimeException("Order does not exist: " + orderNo);
        }

        // Verify if status transition is valid
        List<String> validTransitions = getValidTransitions(order.getStatus());
        if (!validTransitions.contains(request.getNewStatus().toUpperCase())) {
            throw new RuntimeException("Invalid status transition: " + order.getStatus() + " -> " + request.getNewStatus());
        }

        order.setStatus(request.getNewStatus().toUpperCase());
        orderDatabase.put(orderNo, order);

        return order;
    }

    /**
     * Cancel order
     */
    @DeleteMapping("/{orderNo}")
    @AutoAiTool(description = "Cancel the specified order")
    public Map<String, Object> cancelOrder(
            @PathVariable String orderNo,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Reason", required = false) String reason
    ) {
        System.out.println("User " + userId + " canceling order " + orderNo + ", reason: " + reason);

        Order order = orderDatabase.get(orderNo);
        if (order == null) {
            throw new RuntimeException("Order does not exist: " + orderNo);
        }

        if ("COMPLETED".equals(order.getStatus()) || "SHIPPED".equals(order.getStatus())) {
            throw new RuntimeException("Order has been completed or shipped and cannot be canceled");
        }

        // Restore inventory
        for (OrderItem item : order.getItems()) {
            Product product = productDatabase.get(item.getProductId());
            if (product != null) {
                product.setStock(product.getStock() + item.getQuantity());
            }
        }

        order.setStatus("CANCELLED");
        orderDatabase.put(orderNo, order);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Order has been canceled");
        result.put("order", order);
        return result;
    }

    /**
     * Get product list
     */
    @GetMapping("/products")
    @AutoAiTool(description = "Get list of all available products")
    public List<Product> getAllProducts() {
        return new ArrayList<>(productDatabase.values());
    }

    /**
     * Get customer list
     */
    @GetMapping("/customers")
    @AutoAiTool(description = "Get list of all customers")
    public List<Customer> getAllCustomers() {
        return new ArrayList<>(customerDatabase.values());
    }

    /**
     * Calculate total order amount
     */
    @GetMapping("/statistics/total-amount")
    @AutoAiTool(description = "Calculate total amount of all orders")
    public Map<String, Object> getTotalAmount(
            @RequestParam(required = false) String status,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId
    ) {
        System.out.println("Tenant ID: " + tenantId);

        List<Order> ordersToSum = status == null
                ? new ArrayList<>(orderDatabase.values())
                : orderDatabase.values().stream()
                    .filter(order -> status.equalsIgnoreCase(order.getStatus()))
                    .collect(Collectors.toList());

        BigDecimal totalAmount = ordersToSum.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int orderCount = ordersToSum.size();
        BigDecimal averageAmount = orderCount > 0
                ? totalAmount.divide(new BigDecimal(orderCount), 2, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> result = new HashMap<>();
        result.put("totalAmount", totalAmount);
        result.put("orderCount", orderCount);
        result.put("averageAmount", averageAmount);
        result.put("status", status != null ? status : "ALL");

        return result;
    }

    // =============== Helper Methods ===============

    private String generateOrderNo() {
        String orderNo = orderNoGenerator;
        int num = Integer.parseInt(orderNo.substring(orderNo.length() - 4));
        num++;
        orderNoGenerator = "ORD202401" + String.format("%04d", num);
        return orderNo;
    }

    private List<String> getValidTransitions(String currentStatus) {
        return switch (currentStatus) {
            case "PENDING" -> List.of("CONFIRMED", "CANCELLED");
            case "CONFIRMED" -> List.of("SHIPPED", "CANCELLED");
            case "SHIPPED" -> List.of("COMPLETED");
            case "COMPLETED" -> List.of();
            case "CANCELLED" -> List.of();
            default -> List.of();
        };
    }

    private static void createOrderInternal(String orderNo, String customerId, List<OrderItem> items, String status) {
        BigDecimal totalAmount = items.stream()
                .map(item -> item.getPrice().multiply(new BigDecimal(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order(
                orderNo,
                customerId,
                items,
                totalAmount,
                status,
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        );

        orderDatabase.put(orderNo, order);
    }

    // =============== Entity Class Definitions ===============

    /**
     * Order entity
     */
    public static class Order {
        private String orderNo;
        private String customerId;
        private List<OrderItem> items;
        private BigDecimal totalAmount;
        private String status;
        private String createDate;

        public Order() {}

        public Order(String orderNo, String customerId, List<OrderItem> items,
                    BigDecimal totalAmount, String status, String createDate) {
            this.orderNo = orderNo;
            this.customerId = customerId;
            this.items = items;
            this.totalAmount = totalAmount;
            this.status = status;
            this.createDate = createDate;
        }

        // Getters and Setters
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }

        public List<OrderItem> getItems() { return items; }
        public void setItems(List<OrderItem> items) { this.items = items; }

        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getCreateDate() { return createDate; }
        public void setCreateDate(String createDate) { this.createDate = createDate; }
    }

    /**
     * Order item
     */
    public static class OrderItem {
        private Long productId;
        private Integer quantity;
        private BigDecimal price;

        public OrderItem() {}

        public OrderItem(Long productId, Integer quantity, BigDecimal price) {
            this.productId = productId;
            this.quantity = quantity;
            this.price = price;
        }

        // Getters and Setters
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
    }

    /**
     * Product entity
     */
    public static class Product {
        private Long id;
        private String name;
        private String category;
        private BigDecimal price;
        private Integer stock;

        public Product() {}

        public Product(Long id, String name, String category, BigDecimal price, Integer stock) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.price = price;
            this.stock = stock;
        }

        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }

        public Integer getStock() { return stock; }
        public void setStock(Integer stock) { this.stock = stock; }
    }

    /**
     * Customer entity
     */
    public static class Customer {
        private String id;
        private String name;
        private String email;
        private String address;

        public Customer() {}

        public Customer(String id, String name, String email, String address) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.address = address;
        }

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
    }

    /**
     * Create order request
     */
    public static class CreateOrderRequest {
        @AutoAiField(description = "Customer ID", required = true, example = "C001")
        private String customerId;

        @AutoAiField(description = "List of order items", required = true)
        private List<OrderItemRequest> items;

        // Getters and Setters
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }

        public List<OrderItemRequest> getItems() { return items; }
        public void setItems(List<OrderItemRequest> items) { this.items = items; }
    }

    /**
     * Order item request
     */
    public static class OrderItemRequest {
        @AutoAiField(description = "Product ID", required = true, example = "1")
        private Long productId;

        @AutoAiField(description = "Purchase quantity", required = true, example = "2")
        private Integer quantity;

        // Getters and Setters
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }

    /**
     * Update status request
     */
    public static class UpdateStatusRequest {
        @AutoAiField(description = "New order status: CONFIRMED (confirmed), SHIPPED (shipped), COMPLETED (completed), CANCELLED (canceled)", required = true, example = "CONFIRMED")
        private String newStatus;

        // Getters and Setters
        public String getNewStatus() { return newStatus; }
        public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
    }

    /**
     * Order response
     */
    public static class OrderResponse {
        private boolean success;
        private String message;
        private Order order;

        public OrderResponse() {}

        public OrderResponse(boolean success, String message, Order order) {
            this.success = success;
            this.message = message;
            this.order = order;
        }

        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public Order getOrder() { return order; }
        public void setOrder(Order order) { this.order = order; }
    }
}
