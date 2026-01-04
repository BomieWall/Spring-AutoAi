package cn.autoai.demo.web;

import cn.autoai.core.annotation.AutoAiField;
import cn.autoai.core.annotation.AutoAiParam;
import cn.autoai.core.annotation.AutoAiTool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Demo tools collection.
 */
@Component
public class DemoTools {

    // Simulated data storage
    private static final Map<String, Employee> employees = new HashMap<>();
    private static final Map<String, List<String>> departmentEmployees = new HashMap<>();
    private static final Map<String, Project> projects = new HashMap<>();
    private static final List<Meeting> meetings = new ArrayList<>();
    
    static {
        // Initialize employee data
        employees.put("Zhang San", new Employee("Zhang San", "Technology Department", 50000.0, "Senior Engineer", "2020-01-15"));
        employees.put("Li Si", new Employee("Li Si", "Sales Department", 75000.0, "Sales Manager", "2019-03-20"));
        employees.put("Chen Xiao", new Employee("Chen Xiao", "Finance Department", 1023445.21, "Finance Director", "2018-06-10"));
        employees.put("Wang Wu", new Employee("Wang Wu", "Technology Department", 45000.0, "Junior Engineer", "2021-09-01"));
        employees.put("Zhao Liu", new Employee("Zhao Liu", "Human Resources Department", 55000.0, "HR Specialist", "2020-11-12"));

        // Initialize department employee relationships
        departmentEmployees.put("Technology Department", new ArrayList<>(Arrays.asList("Zhang San", "Wang Wu")));
        departmentEmployees.put("Sales Department", new ArrayList<>(Arrays.asList("Li Si")));
        departmentEmployees.put("Finance Department", new ArrayList<>(Arrays.asList("Chen Xiao")));
        departmentEmployees.put("Human Resources Department", new ArrayList<>(Arrays.asList("Zhao Liu")));

        // Initialize project data
        projects.put("Project A", new Project("Project A", "E-commerce Platform Development", Arrays.asList("Zhang San", "Wang Wu"), "In Progress", "2024-01-01", "2024-06-30"));
        projects.put("Project B", new Project("Project B", "Customer Management System", Arrays.asList("Li Si"), "Completed", "2023-09-01", "2023-12-31"));
        projects.put("Project C", new Project("Project C", "Financial Reporting System", Arrays.asList("Chen Xiao"), "Planned", "2024-03-01", "2024-08-31"));

        // Initialize meeting data
        meetings.add(new Meeting("Technical Review Meeting", "2024-01-15", "14:00", Arrays.asList("Zhang San", "Wang Wu"), "Conference Room A"));
        meetings.add(new Meeting("Sales Summary Meeting", "2024-01-16", "10:00", Arrays.asList("Li Si"), "Conference Room B"));
        meetings.add(new Meeting("All-Staff Meeting", "2024-01-20", "09:00", Arrays.asList("Zhang San", "Li Si", "Chen Xiao", "Wang Wu", "Zhao Liu"), "Large Conference Room"));
    }

    // ========== Basic tools ==========

    /** Calculate the sum of two integers */
    @AutoAiTool
    public int add(int a, int b) {
        return a + b;
    }

    /** Calculate the difference between two integers */
    @AutoAiTool
    public int subtract(int a, int b) {
        return a - b;
    }

    /** Calculate the product of two integers */
    @AutoAiTool
    public int multiply(int a, int b) {
        return a * b;
    }

    /** Calculate the division of two numbers */
    @AutoAiTool
    public double divide(double a, double b) {
        if (b == 0) {
            throw new IllegalArgumentException("Divisor cannot be zero");
        }
        return a / b;
    }

    // ========== Employee Management Tools ==========

    /** Get the salary amount for a person by name */
    @AutoAiTool
    public double getMoney(String name) {
        Employee emp = employees.get(name);
        return emp != null ? emp.salary : 0.0;
    }
    
    /** Query person detailed information by name, optionally providing department information */
    @AutoAiTool
    public String getPersonInfo(
        String name,
        @AutoAiParam(required = false) String department
    ) {
        if (name == null || name.trim().isEmpty()) {
            return "Query parameter cannot be empty, please provide person name";
        }

        Employee emp = employees.get(name.trim());
        if (emp == null) {
            return "No person information found for: " + name;
        }

        StringBuilder info = new StringBuilder();
        info.append("Name: ").append(emp.name);
        info.append("\nDepartment: ").append(emp.department);
        info.append("\nPosition: ").append(emp.position);
        info.append("\nSalary: ").append(emp.salary).append(" yuan");
        info.append("\nJoin Date: ").append(emp.joinDate);

        return info.toString();
    }

    /** Get all employees in a specified department */
    @AutoAiTool
    public String getDepartmentEmployees(String department) {
        List<String> empList = departmentEmployees.get(department);
        if (empList == null || empList.isEmpty()) {
            return "Department " + department + " has no employees or does not exist";
        }

        StringBuilder result = new StringBuilder();
        result.append(department).append(" employee list:\n");
        for (String emp : empList) {
            Employee employee = employees.get(emp);
            if (employee != null) {
                result.append("- ").append(emp).append(" (").append(employee.position).append(")\n");
            }
        }
        return result.toString().trim();
    }

    /** Get list of all departments */
    @AutoAiTool
    public String getAllDepartments() {
        return "Company department list:\n" + String.join("\n", departmentEmployees.keySet());
    }

    /** Compare salaries of two employees */
    @AutoAiTool
    public String compareSalary(String name1, String name2) {
        Employee emp1 = employees.get(name1);
        Employee emp2 = employees.get(name2);

        if (emp1 == null) return "Employee not found: " + name1;
        if (emp2 == null) return "Employee not found: " + name2;

        double diff = emp1.salary - emp2.salary;
        if (diff > 0) {
            return name1 + "'s salary is " + diff + " yuan higher than " + name2;
        } else if (diff < 0) {
            return name1 + "'s salary is " + Math.abs(diff) + " yuan lower than " + name2;
        } else {
            return name1 + " and " + name2 + " have the same salary";
        }
    }

    // ========== Project Management Tools ==========

    /** Get project information */
    @AutoAiTool
    public String getProjectInfo(String projectName) {
        Project project = projects.get(projectName);
        if (project == null) {
            return "Project not found: " + projectName;
        }

        StringBuilder info = new StringBuilder();
        info.append("Project Name: ").append(project.name).append("\n");
        info.append("Project Description: ").append(project.description).append("\n");
        info.append("Project Status: ").append(project.status).append("\n");
        info.append("Start Date: ").append(project.startDate).append("\n");
        info.append("End Date: ").append(project.endDate).append("\n");
        info.append("Participants: ").append(String.join(", ", project.members));

        return info.toString();
    }

    /** Get projects an employee participates in */
    @AutoAiTool
    public String getEmployeeProjects(String employeeName) {
        List<String> employeeProjects = new ArrayList<>();

        for (Project project : projects.values()) {
            if (project.members.contains(employeeName)) {
                employeeProjects.add(project.name + " (" + project.status + ")");
            }
        }

        if (employeeProjects.isEmpty()) {
            return employeeName + " is not currently participating in any projects";
        }

        return employeeName + " participating projects:\n" + String.join("\n", employeeProjects);
    }

    /** Get list of all projects */
    @AutoAiTool
    public String getAllProjects() {
        if (projects.isEmpty()) {
            return "There are currently no projects";
        }

        StringBuilder result = new StringBuilder("All projects list:\n");
        for (Project project : projects.values()) {
            result.append("- ").append(project.name)
                  .append(" (").append(project.status).append(")\n");
        }
        return result.toString().trim();
    }

    // ========== Meeting Management Tools ==========

    /** Get meeting schedule for a specified date */
    @AutoAiTool
    public String getMeetingsByDate(String date) {
        List<Meeting> dayMeetings = new ArrayList<>();

        for (Meeting meeting : meetings) {
            if (meeting.date.equals(date)) {
                dayMeetings.add(meeting);
            }
        }

        if (dayMeetings.isEmpty()) {
            return date + " has no meetings scheduled";
        }

        StringBuilder result = new StringBuilder(date + " meeting schedule:\n");
        for (Meeting meeting : dayMeetings) {
            result.append("- ").append(meeting.time).append(" ")
                  .append(meeting.title).append(" (").append(meeting.location).append(")\n");
        }
        return result.toString().trim();
    }

    /** Get meeting schedule for an employee */
    @AutoAiTool
    public String getEmployeeMeetings(String employeeName) {
        List<Meeting> employeeMeetings = new ArrayList<>();

        for (Meeting meeting : meetings) {
            if (meeting.attendees.contains(employeeName)) {
                employeeMeetings.add(meeting);
            }
        }

        if (employeeMeetings.isEmpty()) {
            return employeeName + " has no meetings scheduled";
        }

        StringBuilder result = new StringBuilder(employeeName + " meeting schedule:\n");
        for (Meeting meeting : employeeMeetings) {
            result.append("- ").append(meeting.date).append(" ").append(meeting.time)
                  .append(" ").append(meeting.title).append("\n");
        }
        return result.toString().trim();
    }

    // ========== Time Tools ==========

    /** Get current date and time */
    @AutoAiTool
    public String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /** Calculate the number of days between two dates */
    @AutoAiTool
    public long daysBetween(String startDate, String endDate) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            return java.time.temporal.ChronoUnit.DAYS.between(start, end);
        } catch (Exception e) {
            throw new IllegalArgumentException("Date format error, please use yyyy-MM-dd format");
        }
    }

    // ========== Statistical Analysis Tools ==========

    /** Calculate department average salary */
    @AutoAiTool
    public String getDepartmentAverageSalary(String department) {
        List<String> empList = departmentEmployees.get(department);
        if (empList == null || empList.isEmpty()) {
            return "Department " + department + " does not exist or has no employees";
        }

        double totalSalary = 0;
        int count = 0;

        for (String empName : empList) {
            Employee emp = employees.get(empName);
            if (emp != null) {
                totalSalary += emp.salary;
                count++;
            }
        }

        if (count == 0) {
            return "Cannot calculate average salary for " + department;
        }

        double average = totalSalary / count;
        return department + " average salary: " + String.format("%.2f", average) + " yuan";
    }

    /** Get the employee with the highest salary */
    @AutoAiTool
    public String getHighestPaidEmployee() {
        Employee highest = null;

        for (Employee emp : employees.values()) {
            if (highest == null || emp.salary > highest.salary) {
                highest = emp;
            }
        }

        if (highest == null) {
            return "No employee data";
        }

        return "Highest paid employee: " + highest.name + " (" + highest.department + "), salary: " + highest.salary + " yuan";
    }

    /** Generate random number */
    @AutoAiTool
    public int generateRandomNumber(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("Minimum value cannot be greater than maximum value");
        }
        return new Random().nextInt(max - min + 1) + min;
    }
    
    // ========== System command tools ==========

    /** Execute Mac system command */
    @AutoAiTool(description = "Execute local Mac system command, return command execution result")
    public String executeCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return "Command cannot be empty";
        }

        // Security check: prohibit dangerous commands
        String[] dangerousCommands = {"rm -rf", "sudo", "chmod 777", "dd if=", "mkfs", "format"};
        String lowerCommand = command.toLowerCase();
        for (String dangerous : dangerousCommands) {
            if (lowerCommand.contains(dangerous)) {
                return "For security reasons, dangerous commands are prohibited: " + dangerous;
            }
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (Scanner scanner = new Scanner(process.getInputStream())) {
                while (scanner.hasNextLine()) {
                    output.append(scanner.nextLine()).append("\n");
                }
            }

            int exitCode = process.waitFor();
            String result = output.toString().trim();

            if (exitCode == 0) {
                return result.isEmpty() ? "Command executed successfully, no output" : result;
            } else {
                return "Command execution failed (exit code: " + exitCode + ")\n" + result;
            }

        } catch (Exception e) {
            return "Command execution exception: " + e.getMessage();
        }
    }

    // ========== Prometheus monitoring query tools ==========

    /** Query Prometheus monitoring data for various environments (time range query) */
    @AutoAiTool(description = "Query Prometheus monitoring data for various environments (time range query). Notes: 1) Query time range cannot exceed 24 hours, exceeding will return error; 2) Try to use larger query step size to reduce data points; 3) Try to use specific label filtering in query statements to reduce returned sequence count; 4) Recommended step sizes: 15 seconds for 1 hour, 60 seconds for 6 hours, 300 seconds or more for 24 hours.")
    public PrometheusQueryResult queryPrometheus(
        @AutoAiParam(description = "Prometheus environment ID", required = true) String cluster,
        @AutoAiParam(description = "PromQL query statement, try to use label filtering to reduce returned sequence count", required = true) String query,
        @AutoAiParam(description = "Query start time, format: yyyy-MM-dd HH:mm:ss, query range cannot exceed 24 hours") String startTime,
        @AutoAiParam(description = "Query end time, format: yyyy-MM-dd HH:mm:ss") String endTime,
        @AutoAiParam(required = false, description = "Query step (seconds), recommended: 15 seconds for 1 hour, 60 seconds for 6 hours, 300 seconds or more for 24 hours, default 60 seconds") String step
    ) {
        if (cluster == null || cluster.trim().isEmpty()) {
            return new PrometheusQueryResult(false, "Environment ID cannot be empty", null);
        }

        if (query == null || query.trim().isEmpty()) {
            return new PrometheusQueryResult(false, "Query statement cannot be empty", null);
        }

        String normalizedQuery = query.trim();

        if (startTime == null || startTime.trim().isEmpty()) {
            return new PrometheusQueryResult(false, "Start time cannot be empty", null);
        }

        if (endTime == null || endTime.trim().isEmpty()) {
            return new PrometheusQueryResult(false, "End time cannot be empty", null);
        }

        try {
            // Parse time
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            long startEpochSeconds = LocalDateTime.parse(startTime, formatter)
                    .atZone(ZoneId.systemDefault())
                    .toEpochSecond();
            long endEpochSeconds = LocalDateTime.parse(endTime, formatter)
                    .atZone(ZoneId.systemDefault())
                    .toEpochSecond();

            // Check if time range exceeds 24 hours
            long timeRangeSeconds = endEpochSeconds - startEpochSeconds;
            long maxTimeRangeSeconds = 24 * 60 * 60; // 24 hours
            if (timeRangeSeconds > maxTimeRangeSeconds) {
                return new PrometheusQueryResult(false,
                        "Query time range cannot exceed 24 hours, current query range is " + (timeRangeSeconds / 3600) + " hours. Please shorten the query time range.", null);
            }

            if (timeRangeSeconds <= 0) {
                return new PrometheusQueryResult(false,
                        "End time must be greater than start time", null);
            }

            // Default step is 60 seconds
            String stepValue = (step != null && !step.trim().isEmpty()) ? step : "60";

            // Add cluster condition to query statement
            String finalQuery = addClusterFilter(normalizedQuery, cluster);

            // Build request URL
            String baseUrl = "https://monitor-query-frontend.mypaas.com/prometheus/api/v1/query_range";
            String encodedQuery = URLEncoder.encode(finalQuery, StandardCharsets.UTF_8);
            String url = String.format("%s?query=%s&start=%d&end=%d&step=%s",
                    baseUrl, encodedQuery, startEpochSeconds, endEpochSeconds, stepValue);

            // Send HTTP request
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return new PrometheusQueryResult(true, "Query successful", response.body());
            } else {
                return new PrometheusQueryResult(false,
                        "Query failed (HTTP " + response.statusCode() + ")", response.body());
            }

        } catch (java.time.format.DateTimeParseException e) {
            return new PrometheusQueryResult(false, "Time format error, please use format: yyyy-MM-dd HH:mm:ss", null);
        } catch (Exception e) {
            return new PrometheusQueryResult(false, "Query exception: " + e.getMessage(), null);
        }
    }

    /** Query Prometheus instant data (single point query) */
    @AutoAiTool(description = "Query Prometheus monitoring data for various environments (instant query), returns value at specified moment. Notes: 1) Try to use specific label filtering in query statements to reduce returned sequence count; 2) This method returns data for a single time point, suitable for querying current status or metric values at a specific time point; 3) If time parameter is not specified, queries data for current time point.")
    public PrometheusQueryResult queryPrometheusInstant(
            @AutoAiParam(description = "Prometheus environment number", required = true) String cluster,
            @AutoAiParam(description = "PromQL query statement, try to use label filtering to reduce returned sequence count", required = true) String query,
            @AutoAiParam(required = false, description = "Query time, format: yyyy-MM-dd HH:mm:ss, if not specified query current time") String time
    ) {
        if (cluster == null || cluster.trim().isEmpty()) {
            return new PrometheusQueryResult(false, "Environment ID cannot be empty", null);
        }

        if (query == null || query.trim().isEmpty()) {
            return new PrometheusQueryResult(false, "Query statement cannot be empty", null);
        }

        String normalizedQuery = query.trim();

        try {
            // Add cluster condition to query statement
            String finalQuery = addClusterFilter(normalizedQuery, cluster);

            // Build request URL
            String baseUrl = "https://monitor-query-frontend.mypaas.com/prometheus/api/v1/query";
            String encodedQuery = URLEncoder.encode(finalQuery, StandardCharsets.UTF_8);
            String url = baseUrl + "?query=" + encodedQuery;

            // If time is specified, add time parameter
            if (time != null && !time.trim().isEmpty()) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                long epochSeconds = LocalDateTime.parse(time, formatter)
                        .atZone(ZoneId.systemDefault())
                        .toEpochSecond();
                url += "&time=" + epochSeconds;
            }

            // Send HTTP request
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return new PrometheusQueryResult(true, "Query successful", response.body());
            } else {
                return new PrometheusQueryResult(false,
                        "Query failed (HTTP " + response.statusCode() + ")", response.body());
            }

        } catch (java.time.format.DateTimeParseException e) {
            return new PrometheusQueryResult(false, "Time format error, please use format: yyyy-MM-dd HH:mm:ss", null);
        } catch (Exception e) {
            return new PrometheusQueryResult(false, "Query exception: " + e.getMessage(), null);
        }
    }

    /**
     * Add cluster filter condition to PromQL query statement
     *
     * @param query Original PromQL query statement
     * @param cluster cluster environment ID
     * @return Query statement with cluster filter condition added
     */
    private String addClusterFilter(String query, String cluster) {
        if (cluster == null || cluster.trim().isEmpty()) {
            return query;
        }

        String normalizedCluster = cluster.trim();

        // Check if query statement already contains curly braces
        int leftBraceIndex = query.indexOf('{');
        if (leftBraceIndex == -1) {
            // No curly braces, directly add {cluster="xxx"} after the metric name
            return query + "{cluster=\"" + normalizedCluster + "\"}";
        }

        // There are curly braces, need to find the position of the right brace
        int rightBraceIndex = query.indexOf('}', leftBraceIndex);
        if (rightBraceIndex == -1) {
            // Only left brace, format error, return original query
            return query;
        }

        // Extract label content within the curly braces
        String labelsContent = query.substring(leftBraceIndex + 1, rightBraceIndex);

        // Check if cluster label is already included
        if (labelsContent.contains("cluster=")) {
            // Already contains cluster, return original query
            return query;
        }

        // Add cluster condition within curly braces
        String newLabelsContent;
        if (labelsContent.trim().isEmpty()) {
            newLabelsContent = "cluster=\"" + normalizedCluster + "\"";
        } else {
            newLabelsContent = labelsContent + ",cluster=\"" + normalizedCluster + "\"";
        }

        // Rebuild query statement
        return query.substring(0, leftBraceIndex + 1) + newLabelsContent + query.substring(rightBraceIndex);
    }

    // ========== Complex Type Parameter Tools ==========

    /** Create employee (accepts employee object as parameter) */
    @AutoAiTool
    public EmployeeResult createEmployee(EmployeeRequest request) {
        if (request == null) {
            return new EmployeeResult(false, "Employee information cannot be empty", null);
        }

        if (request.name == null || request.name.trim().isEmpty()) {
            return new EmployeeResult(false, "Employee name cannot be empty", null);
        }

        if (employees.containsKey(request.name)) {
            return new EmployeeResult(false, "Employee " + request.name + " already exists", null);
        }

        // Create new employee
        Employee newEmployee = new Employee(
            request.name,
            request.department != null ? request.department : "Unassigned",
            request.salary != null ? request.salary : 0.0,
            request.position != null ? request.position : "Employee",
            request.joinDate != null ? request.joinDate : getCurrentTime()
        );

        employees.put(request.name, newEmployee);

        // Update department employee list
        departmentEmployees.computeIfAbsent(newEmployee.department, k -> new ArrayList<>()).add(newEmployee.name);

        return new EmployeeResult(true, "Employee created successfully", newEmployee);
    }

    /** Get employee detailed information (returns employee object) */
    @AutoAiTool
    public EmployeeDetail getEmployeeDetail(String name) {
        Employee emp = employees.get(name);
        if (emp == null) {
            return new EmployeeDetail(name, false, "Employee does not exist", null, null, null);
        }

        // Get projects employee participates in
        List<ProjectSummary> employeeProjects = new ArrayList<>();
        for (Project project : projects.values()) {
            if (project.members.contains(name)) {
                employeeProjects.add(new ProjectSummary(project.name, project.status, project.description));
            }
        }

        // Get employee's meetings
        List<MeetingSummary> employeeMeetings = new ArrayList<>();
        for (Meeting meeting : meetings) {
            if (meeting.attendees.contains(name)) {
                employeeMeetings.add(new MeetingSummary(meeting.title, meeting.date, meeting.time));
            }
        }
        
        return new EmployeeDetail(name, true, "Query successful", emp, employeeProjects, employeeMeetings);
    }

    /** Batch operation on employees (accepts list of employee operation requests) */
    @AutoAiTool
    public BatchOperationResult batchUpdateSalary(SalaryUpdateBatch batchRequest) {
        if (batchRequest == null || batchRequest.updates == null || batchRequest.updates.isEmpty()) {
            return new BatchOperationResult(false, "Batch update request cannot be empty", new ArrayList<>());
        }

        List<SalaryUpdateResult> results = new ArrayList<>();
        int successCount = 0;

        for (SalaryUpdate update : batchRequest.updates) {
            Employee emp = employees.get(update.employeeName);
            if (emp == null) {
                results.add(new SalaryUpdateResult(update.employeeName, false, "Employee does not exist", 0.0, 0.0));
                continue;
            }

            double oldSalary = emp.salary;
            double newSalary;

            if ("INCREASE".equals(update.operation)) {
                newSalary = oldSalary + update.amount;
            } else if ("DECREASE".equals(update.operation)) {
                newSalary = Math.max(0, oldSalary - update.amount);
            } else if ("SET".equals(update.operation)) {
                newSalary = update.amount;
            } else {
                results.add(new SalaryUpdateResult(update.employeeName, false, "Invalid operation type: " + update.operation, oldSalary, oldSalary));
                continue;
            }

            emp.salary = newSalary;
            results.add(new SalaryUpdateResult(update.employeeName, true, "Update successful", oldSalary, newSalary));
            successCount++;
        }

        String message = String.format("Batch update completed, success: %d, failed: %d", successCount, results.size() - successCount);
        return new BatchOperationResult(successCount > 0, message, results);
    }

    /** Department statistical analysis (returns complex statistical object) */
    @AutoAiTool
    public DepartmentAnalysis analyzeDepartment(String departmentName) {
        List<String> empList = departmentEmployees.get(departmentName);
        if (empList == null || empList.isEmpty()) {
            return new DepartmentAnalysis(departmentName, false, "Department does not exist or has no employees", null);
        }

        List<Employee> deptEmployees = empList.stream()
            .map(employees::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (deptEmployees.isEmpty()) {
            return new DepartmentAnalysis(departmentName, false, "Department has no valid employee data", null);
        }

        // Calculate statistical data
        double totalSalary = deptEmployees.stream().mapToDouble(emp -> emp.salary).sum();
        double avgSalary = totalSalary / deptEmployees.size();
        double maxSalary = deptEmployees.stream().mapToDouble(emp -> emp.salary).max().orElse(0);
        double minSalary = deptEmployees.stream().mapToDouble(emp -> emp.salary).min().orElse(0);

        String highestPaid = deptEmployees.stream()
            .max(Comparator.comparing(emp -> emp.salary))
            .map(emp -> emp.name)
            .orElse("None");

        // Position distribution
        Map<String, Long> positionCount = deptEmployees.stream()
            .collect(Collectors.groupingBy(emp -> emp.position, Collectors.counting()));

        List<PositionStat> positionStats = positionCount.entrySet().stream()
            .map(entry -> new PositionStat(entry.getKey(), entry.getValue().intValue()))
            .collect(Collectors.toList());

        // Project participation
        Map<String, Integer> projectParticipation = new HashMap<>();
        for (Project project : projects.values()) {
            for (String member : project.members) {
                if (empList.contains(member)) {
                    projectParticipation.put(member, projectParticipation.getOrDefault(member, 0) + 1);
                }
            }
        }

        DepartmentStats stats = new DepartmentStats(
            deptEmployees.size(),
            avgSalary,
            maxSalary,
            minSalary,
            totalSalary,
            highestPaid,
            positionStats,
            projectParticipation
        );

        return new DepartmentAnalysis(departmentName, true, "Analysis complete", stats);
    }

    /** Create complex project (accepts project creation request object) */
    @AutoAiTool
    public ProjectCreationResult createComplexProject(ProjectCreationRequest request) {
        if (request == null) {
            return new ProjectCreationResult(false, "Project creation request cannot be empty", null);
        }
        
        if (request.projectName == null || request.projectName.trim().isEmpty()) {
            return new ProjectCreationResult(false, "Project name cannot be empty", null);
        }

        if (projects.containsKey(request.projectName)) {
            return new ProjectCreationResult(false, "Project already exists", null);
        }

        // Validate members
        List<String> validMembers = new ArrayList<>();
        List<String> invalidMembers = new ArrayList<>();

        if (request.members != null) {
            for (String member : request.members) {
                if (employees.containsKey(member)) {
                    validMembers.add(member);
                } else {
                    invalidMembers.add(member);
                }
            }
        }

        if (!invalidMembers.isEmpty()) {
            return new ProjectCreationResult(false, "The following members do not exist: " + String.join(", ", invalidMembers), null);
        }

        // Create project
        Project newProject = new Project(
            request.projectName,
            request.description != null ? request.description : "No description",
            validMembers,
            request.status != null ? request.status : "Planned",
            request.startDate != null ? request.startDate : getCurrentTime(),
            request.endDate != null ? request.endDate : "To be determined"
        );

        projects.put(request.projectName, newProject);

        // Create project detail return object
        ProjectDetail projectDetail = new ProjectDetail(
            newProject.name,
            newProject.description,
            newProject.status,
            newProject.startDate,
            newProject.endDate,
            validMembers,
            validMembers.size(),
            request.budget != null ? request.budget : 0.0,
            request.priority != null ? request.priority : "Medium"
        );
        
        return new ProjectCreationResult(true, "Project created successfully", projectDetail);
    }

    // ========== Complex Type Entity Class Definitions ==========

    /** Employee creation request */
    public static class EmployeeRequest {
        @AutoAiField(description = "Employee name", required = true, example = "Zhang San")
        public String name;

        @cn.autoai.core.annotation.AutoAiField(description = "Department", example = "Technology Department")
        public String department;

        @cn.autoai.core.annotation.AutoAiField(description = "Monthly salary (yuan)", example = "50000.0")
        public Double salary;

        @cn.autoai.core.annotation.AutoAiField(description = "Position name", example = "Senior Engineer")
        public String position;

        @cn.autoai.core.annotation.AutoAiField(description = "Hire date, format: yyyy-MM-dd", example = "2024-01-15")
        public String joinDate;

        public EmployeeRequest() {}

        public EmployeeRequest(String name, String department, Double salary, String position, String joinDate) {
            this.name = name;
            this.department = department;
            this.salary = salary;
            this.position = position;
            this.joinDate = joinDate;
        }
    }

    /** Employee operation result */
    public static class EmployeeResult {
        public boolean success;
        public String message;
        public Employee employee;

        public EmployeeResult(boolean success, String message, Employee employee) {
            this.success = success;
            this.message = message;
            this.employee = employee;
        }
    }

    /** Employee detailed information */
    public static class EmployeeDetail {
        public String name;
        public boolean found;
        public String message;
        public Employee basicInfo;
        public List<ProjectSummary> projects;
        public List<MeetingSummary> meetings;

        public EmployeeDetail(String name, boolean found, String message, Employee basicInfo,
                            List<ProjectSummary> projects, List<MeetingSummary> meetings) {
            this.name = name;
            this.found = found;
            this.message = message;
            this.basicInfo = basicInfo;
            this.projects = projects;
            this.meetings = meetings;
        }
    }

    /** Project summary */
    public static class ProjectSummary {
        public String name;
        public String status;
        public String description;

        public ProjectSummary(String name, String status, String description) {
            this.name = name;
            this.status = status;
            this.description = description;
        }
    }

    /** Meeting summary */
    public static class MeetingSummary {
        public String title;
        public String date;
        public String time;
        
        public MeetingSummary(String title, String date, String time) {
            this.title = title;
            this.date = date;
            this.time = time;
        }
    }

    /** Salary update request */
    public static class SalaryUpdate {
        @cn.autoai.core.annotation.AutoAiField(description = "Employee name", required = true, example = "Zhang San")
        public String employeeName;

        @cn.autoai.core.annotation.AutoAiField(description = "Operation type: INCREASE (increase), DECREASE (decrease), SET (set)", required = true, example = "INCREASE")
        public String operation; // INCREASE, DECREASE, SET

        @cn.autoai.core.annotation.AutoAiField(description = "Amount value", required = true, example = "5000.0")
        public double amount;

        public SalaryUpdate() {}

        public SalaryUpdate(String employeeName, String operation, double amount) {
            this.employeeName = employeeName;
            this.operation = operation;
            this.amount = amount;
        }
    }

    /** Batch salary update request */
    public static class SalaryUpdateBatch {
        @cn.autoai.core.annotation.AutoAiField(description = "Salary update operation list", required = true)
        public List<SalaryUpdate> updates;

        @cn.autoai.core.annotation.AutoAiField(description = "Update reason description", example = "Annual salary adjustment")
        public String reason;

        public SalaryUpdateBatch() {}

        public SalaryUpdateBatch(List<SalaryUpdate> updates, String reason) {
            this.updates = updates;
            this.reason = reason;
        }
    }

    /** Salary update result */
    public static class SalaryUpdateResult {
        public String employeeName;
        public boolean success;
        public String message;
        public double oldSalary;
        public double newSalary;

        public SalaryUpdateResult(String employeeName, boolean success, String message, double oldSalary, double newSalary) {
            this.employeeName = employeeName;
            this.success = success;
            this.message = message;
            this.oldSalary = oldSalary;
            this.newSalary = newSalary;
        }
    }

    /** Batch operation result */
    public static class BatchOperationResult {
        public boolean overallSuccess;
        public String message;
        public List<SalaryUpdateResult> results;

        public BatchOperationResult(boolean overallSuccess, String message, List<SalaryUpdateResult> results) {
            this.overallSuccess = overallSuccess;
            this.message = message;
            this.results = results;
        }
    }

    /** Department statistics data */
    public static class DepartmentStats {
        public int employeeCount;
        public double averageSalary;
        public double maxSalary;
        public double minSalary;
        public double totalSalary;
        public String highestPaidEmployee;
        public List<PositionStat> positionDistribution;
        public Map<String, Integer> projectParticipation;
        
        public DepartmentStats(int employeeCount, double averageSalary, double maxSalary, double minSalary,
                             double totalSalary, String highestPaidEmployee, List<PositionStat> positionDistribution,
                             Map<String, Integer> projectParticipation) {
            this.employeeCount = employeeCount;
            this.averageSalary = averageSalary;
            this.maxSalary = maxSalary;
            this.minSalary = minSalary;
            this.totalSalary = totalSalary;
            this.highestPaidEmployee = highestPaidEmployee;
            this.positionDistribution = positionDistribution;
            this.projectParticipation = projectParticipation;
        }
    }

    /** Position statistics */
    public static class PositionStat {
        public String position;
        public int count;

        public PositionStat(String position, int count) {
            this.position = position;
            this.count = count;
        }
    }

    /** Department analysis result */
    public static class DepartmentAnalysis {
        public String departmentName;
        public boolean success;
        public String message;
        public DepartmentStats stats;

        public DepartmentAnalysis(String departmentName, boolean success, String message, DepartmentStats stats) {
            this.departmentName = departmentName;
            this.success = success;
            this.message = message;
            this.stats = stats;
        }
    }

    /** Project creation request */
    public static class ProjectCreationRequest {
        @cn.autoai.core.annotation.AutoAiField(description = "Project name", required = true, example = "New Product Development")
        public String projectName;

        @cn.autoai.core.annotation.AutoAiField(description = "Project description", example = "Develop new mobile application product")
        public String description;

        @cn.autoai.core.annotation.AutoAiField(description = "Project member name list", example = "[\"Zhang San\", \"Li Si\"]")
        public List<String> members;

        @cn.autoai.core.annotation.AutoAiField(description = "Project status: Planned, In Progress, Completed, Paused", example = "Planned")
        public String status;

        @cn.autoai.core.annotation.AutoAiField(description = "Project start date, format: yyyy-MM-dd", example = "2024-02-01")
        public String startDate;

        @cn.autoai.core.annotation.AutoAiField(description = "Project end date, format: yyyy-MM-dd", example = "2024-08-31")
        public String endDate;

        @cn.autoai.core.annotation.AutoAiField(description = "Project budget amount (yuan)", example = "500000.0")
        public Double budget;

        @cn.autoai.core.annotation.AutoAiField(description = "Project priority: High, Medium, Low", example = "High")
        public String priority;
        
        public ProjectCreationRequest() {}
        
        public ProjectCreationRequest(String projectName, String description, List<String> members,
                                    String status, String startDate, String endDate, Double budget, String priority) {
            this.projectName = projectName;
            this.description = description;
            this.members = members;
            this.status = status;
            this.startDate = startDate;
            this.endDate = endDate;
            this.budget = budget;
            this.priority = priority;
        }
    }

    /** Project details */
    public static class ProjectDetail {
        public String name;
        public String description;
        public String status;
        public String startDate;
        public String endDate;
        public List<String> members;
        public int memberCount;
        public double budget;
        public String priority;

        public ProjectDetail(String name, String description, String status, String startDate, String endDate,
                           List<String> members, int memberCount, double budget, String priority) {
            this.name = name;
            this.description = description;
            this.status = status;
            this.startDate = startDate;
            this.endDate = endDate;
            this.members = members;
            this.memberCount = memberCount;
            this.budget = budget;
            this.priority = priority;
        }
    }

    /** Project creation result */
    public static class ProjectCreationResult {
        public boolean success;
        public String message;
        public ProjectDetail project;

        public ProjectCreationResult(boolean success, String message, ProjectDetail project) {
            this.success = success;
            this.message = message;
            this.project = project;
        }
    }

    public static class Employee {
        /** Employee name */
        public String name;
        /** Department */
        public String department;
        /** Monthly salary */
        public double salary;
        /** Position name */
        public String position;
        /** Hire date */
        public String joinDate;

        public Employee() {}

        public Employee(String name, String department, double salary, String position, String joinDate) {
            this.name = name;
            this.department = department;
            this.salary = salary;
            this.position = position;
            this.joinDate = joinDate;
        }
    }
    
    public static class Project {
        public String name;
        public String description;
        public List<String> members;
        public String status;
        public String startDate;
        public String endDate;
        
        public Project() {}
        
        public Project(String name, String description, List<String> members, String status, String startDate, String endDate) {
            this.name = name;
            this.description = description;
            this.members = members;
            this.status = status;
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }
    
    public static class Meeting {
        public String title;
        public String date;
        public String time;
        public List<String> attendees;
        public String location;
        
        public Meeting() {}
        
        public Meeting(String title, String date, String time, List<String> attendees, String location) {
            this.title = title;
            this.date = date;
            this.time = time;
            this.attendees = attendees;
            this.location = location;
        }
    }

    /** Prometheus query result */
    public static class PrometheusQueryResult {
        public boolean success;
        public String message;
        public String data;

        public PrometheusQueryResult(boolean success, String message, String data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
    }

    /**
     * Infer department from name (simulated logic)
     */
    private String inferDepartment(String name) {
        Employee emp = employees.get(name);
        return emp != null ? emp.department : "Unknown department";
    }
}
