package cn.autoai.core.spring;

import cn.autoai.core.annotation.AutoAiTool;
import cn.autoai.core.annotation.AutoAiToolScan;
import cn.autoai.core.registry.ToolDefinition;
import cn.autoai.core.registry.ToolRegistry;
import cn.autoai.core.util.ToolDefinitionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Controller;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Scans Spring Beans and registers tool methods.
 * Supports scanning tool methods in both regular Beans and Controllers.
 * <p>
 * Supports configuring scan scope through {@link AutoAiToolScan} annotation to improve startup performance in large-scale projects.
 */
public class AutoAiToolScanner implements SmartLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(AutoAiToolScanner.class);
    private static final String BUILTIN_PACKAGE = "cn.autoai.core.builtin";

    private final ApplicationContext applicationContext;
    private final ToolRegistry toolRegistry;
    private volatile boolean running;

    // Scan configuration
    private ScanConfig scanConfig;

    public AutoAiToolScanner(ApplicationContext applicationContext, ToolRegistry toolRegistry) {
        this.applicationContext = applicationContext;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }

        long startTime = System.currentTimeMillis();

        // Parse scan configuration
        scanConfig = parseScanConfig();

        // Execute scan
        int toolCount = doScan();

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Spring-Spring-AutoAi tool scanning completed, registered {} tools, took {} ms", toolCount, duration);

        running = true;
    }

    /**
     * Parse scan configuration
     */
    private ScanConfig parseScanConfig() {
        ScanConfig config = new ScanConfig();

        // Read configuration imported from @Import mechanism
        if (AutoAiToolScanConfiguration.ScanConfigHolder.isConfigured()) {
            config.scanAll = false;

            // Collect package paths
            String[] packages = AutoAiToolScanConfiguration.ScanConfigHolder.getPackages();
            if (packages.length > 0) {
                config.packages.addAll(Arrays.asList(packages));
            }

            // Collect specified classes
            Class<?>[] classes = AutoAiToolScanConfiguration.ScanConfigHolder.getClasses();
            if (classes.length > 0) {
                for (Class<?> clazz : classes) {
                    config.classes.add(clazz.getName());
                }
            }

            logger.info("Read scan configuration from @AutoAiToolScan annotation");
            logger.info("  - Specified package paths: {}", config.packages);
            logger.info("  - Specified classes: {}", config.classes);
            logger.info("  - Built-in tools: always scanned");
        } else {
            // @AutoAiToolScan not configured, use default configuration (scan all)
            config.scanAll = true;
            logger.info("@AutoAiToolScan not configured, will scan all Beans (default behavior)");
            logger.info("Tip: Adding @AutoAiToolScan annotation on the startup class can improve startup performance");
        }

        return config;
    }

    /**
     * Execute scan
     */
    private int doScan() {
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        int toolCount = 0;

        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
                if (targetClass == null) {
                    continue;
                }

                // Check if this Bean should be scanned
                if (!shouldScan(targetClass)) {
                    continue;
                }

                // Check if it is a Controller
                boolean isController = isControllerClass(targetClass);

                // Extract class-level @RequestMapping path
                String classLevelPath = extractClassLevelPath(targetClass);

                // Scan methods and register tools
                int[] countHolder = new int[1];
                ReflectionUtils.doWithMethods(targetClass,
                        method -> {
                            if (registerTool(bean, method, isController, classLevelPath)) {
                                countHolder[0]++;
                            }
                        },
                        method -> method.isAnnotationPresent(AutoAiTool.class));

                toolCount += countHolder[0];
            } catch (Exception e) {
                logger.warn("Error while scanning Bean {}: {}", beanName, e.getMessage());
            }
        }

        return toolCount;
    }

    /**
     * Determine if the specified class should be scanned
     */
    private boolean shouldScan(Class<?> targetClass) {
        String className = targetClass.getName();
        String packageName = targetClass.getPackage() != null ?
            targetClass.getPackage().getName() : "";

        // 1. Built-in tool package: always scanned regardless of user configuration
        if (packageName.startsWith(BUILTIN_PACKAGE)) {
            return true;
        }

        // 2. If configured to scan all
        if (scanConfig.scanAll) {
            return true;
        }

        // 3. Check if in the specified package list
        for (String packagePattern : scanConfig.packages) {
            if (matchesPackage(packageName, packagePattern)) {
                return true;
            }
        }

        // 4. Check if in the specified class list
        for (String classNamePattern : scanConfig.classes) {
            if (className.equals(classNamePattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determine if the package name matches the pattern
     */
    private boolean matchesPackage(String packageName, String pattern) {
        // Support wildcards
        if (pattern.endsWith(".*")) {
            // "com.example.*" - scan only this package, excluding sub-packages
            String basePackage = pattern.substring(0, pattern.length() - 2);
            return packageName.equals(basePackage);
        } else if (pattern.endsWith(".**")) {
            // "com.example.**" - scan this package and all sub-packages
            String basePackage = pattern.substring(0, pattern.length() - 3);
            return packageName.startsWith(basePackage);
        } else {
            // "com.example" - scan this package and all sub-packages (default behavior)
            return packageName.startsWith(pattern);
        }
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    /**
     * Determine if the class is a Controller.
     */
    private boolean isControllerClass(Class<?> targetClass) {
        return targetClass.isAnnotationPresent(Controller.class) ||
               targetClass.isAnnotationPresent(RestController.class);
    }

    /**
     * Extract class-level @RequestMapping path.
     */
    private String extractClassLevelPath(Class<?> targetClass) {
        RequestMapping classMapping = targetClass.getAnnotation(RequestMapping.class);
        if (classMapping != null && classMapping.path().length > 0) {
            return classMapping.path()[0];
        }
        if (classMapping != null && classMapping.value().length > 0) {
            return classMapping.value()[0];
        }
        return "";
    }

    /**
     * Register tool.
     *
     * @return whether registration was successful
     */
    private boolean registerTool(Object bean, Method method, boolean isController, String classLevelPath) {
        ToolDefinition definition;

        try {
            if (isController) {
                // Controller method, extract REST API information
                RestMappingInfo mappingInfo = extractRestMappingInfo(method, classLevelPath);
                if (mappingInfo != null) {
                    definition = ToolDefinitionBuilder.fromMethodWithRestInfo(
                            bean,
                            method,
                            mappingInfo.httpMethod,
                            mappingInfo.path,
                            mappingInfo.consumes,
                            mappingInfo.produces
                    );
                } else {
                    // If unable to extract REST mapping info, fall back to regular method
                    definition = ToolDefinitionBuilder.fromMethod(bean, method);
                }
            } else {
                // Regular Bean method
                definition = ToolDefinitionBuilder.fromMethod(bean, method);
            }

            if (definition != null) {
                toolRegistry.register(definition);
                return true;
            }
        } catch (Exception e) {
            logger.warn("Failed to register tool {}#{}: {}",
                method.getDeclaringClass().getSimpleName(), method.getName(), e.getMessage());
        }

        return false;
    }

    /**
     * Extract REST mapping information from the method.
     */
    private RestMappingInfo extractRestMappingInfo(Method method, String classLevelPath) {
        // Check if restPath is specified in the annotation
        AutoAiTool tool = method.getAnnotation(AutoAiTool.class);
        String explicitPath = tool.restPath();

        // Prioritize explicitly specified path
        if (StringUtils.hasText(explicitPath)) {
            return new RestMappingInfo(
                    determineHttpMethod(method),
                    explicitPath,
                    extractConsumes(method),
                    extractProduces(method)
            );
        }

        // Otherwise extract from @RequestMapping and other annotations
        String methodLevelPath = null;
        String httpMethod = null;

        // Check various mapping annotations
        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        if (getMapping != null) {
            httpMethod = "GET";
            methodLevelPath = getFirstPath(getMapping.path(), getMapping.value());
        }

        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        if (postMapping != null) {
            httpMethod = "POST";
            methodLevelPath = getFirstPath(postMapping.path(), postMapping.value());
        }

        PutMapping putMapping = method.getAnnotation(PutMapping.class);
        if (putMapping != null) {
            httpMethod = "PUT";
            methodLevelPath = getFirstPath(putMapping.path(), putMapping.value());
        }

        DeleteMapping deleteMapping = method.getAnnotation(DeleteMapping.class);
        if (deleteMapping != null) {
            httpMethod = "DELETE";
            methodLevelPath = getFirstPath(deleteMapping.path(), deleteMapping.value());
        }

        PatchMapping patchMapping = method.getAnnotation(PatchMapping.class);
        if (patchMapping != null) {
            httpMethod = "PATCH";
            methodLevelPath = getFirstPath(patchMapping.path(), patchMapping.value());
        }

        RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
        if (requestMapping != null && httpMethod == null) {
            // If no specific annotation found, check @RequestMapping
            methodLevelPath = getFirstPath(requestMapping.path(), requestMapping.value());
            RequestMethod[] requestMethods = requestMapping.method();
            if (requestMethods.length > 0) {
                httpMethod = requestMethods[0].name();
            }
        }

        // If still no path info found, return null
        if (methodLevelPath == null) {
            return null;
        }

        // Combine class-level and method-level paths
        String fullPath = combinePaths(classLevelPath, methodLevelPath);

        return new RestMappingInfo(
                httpMethod != null ? httpMethod : "GET",
                fullPath,
                extractConsumes(method),
                extractProduces(method)
        );
    }

    /**
     * Determine the HTTP method type for the method.
     */
    private String determineHttpMethod(Method method) {
        if (method.isAnnotationPresent(GetMapping.class)) return "GET";
        if (method.isAnnotationPresent(PostMapping.class)) return "POST";
        if (method.isAnnotationPresent(PutMapping.class)) return "PUT";
        if (method.isAnnotationPresent(DeleteMapping.class)) return "DELETE";
        if (method.isAnnotationPresent(PatchMapping.class)) return "PATCH";

        RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
        if (requestMapping != null && requestMapping.method().length > 0) {
            return requestMapping.method()[0].name();
        }

        return "GET"; // Default
    }

    /**
     * Extract consumes information.
     */
    private String extractConsumes(Method method) {
        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        if (getMapping != null && getMapping.consumes().length > 0) {
            return getMapping.consumes()[0];
        }

        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        if (postMapping != null && postMapping.consumes().length > 0) {
            return postMapping.consumes()[0];
        }

        PutMapping putMapping = method.getAnnotation(PutMapping.class);
        if (putMapping != null && putMapping.consumes().length > 0) {
            return putMapping.consumes()[0];
        }

        DeleteMapping deleteMapping = method.getAnnotation(DeleteMapping.class);
        if (deleteMapping != null && deleteMapping.consumes().length > 0) {
            return deleteMapping.consumes()[0];
        }

        PatchMapping patchMapping = method.getAnnotation(PatchMapping.class);
        if (patchMapping != null && patchMapping.consumes().length > 0) {
            return patchMapping.consumes()[0];
        }

        RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
        if (requestMapping != null && requestMapping.consumes().length > 0) {
            return requestMapping.consumes()[0];
        }

        return null;
    }

    /**
     * Extract produces information.
     */
    private String extractProduces(Method method) {
        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        if (getMapping != null && getMapping.produces().length > 0) {
            return getMapping.produces()[0];
        }

        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        if (postMapping != null && postMapping.produces().length > 0) {
            return postMapping.produces()[0];
        }

        PutMapping putMapping = method.getAnnotation(PutMapping.class);
        if (putMapping != null && putMapping.produces().length > 0) {
            return putMapping.produces()[0];
        }

        DeleteMapping deleteMapping = method.getAnnotation(DeleteMapping.class);
        if (deleteMapping != null && deleteMapping.produces().length > 0) {
            return deleteMapping.produces()[0];
        }

        PatchMapping patchMapping = method.getAnnotation(PatchMapping.class);
        if (patchMapping != null && patchMapping.produces().length > 0) {
            return patchMapping.produces()[0];
        }

        RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
        if (requestMapping != null && requestMapping.produces().length > 0) {
            return requestMapping.produces()[0];
        }

        return null;
    }

    /**
     * Get the first non-empty path.
     */
    private String getFirstPath(String[] paths1, String[] paths2) {
        if (paths1 != null && paths1.length > 0 && StringUtils.hasText(paths1[0])) {
            return paths1[0];
        }
        if (paths2 != null && paths2.length > 0 && StringUtils.hasText(paths2[0])) {
            return paths2[0];
        }
        return "";
    }

    /**
     * Combine class-level and method-level paths.
     */
    private String combinePaths(String classLevelPath, String methodLevelPath) {
        if (!StringUtils.hasText(classLevelPath)) {
            return methodLevelPath;
        }
        if (!StringUtils.hasText(methodLevelPath)) {
            return classLevelPath;
        }

        // Ensure path format is correct
        String classPath = classLevelPath.startsWith("/") ? classLevelPath : "/" + classLevelPath;
        String methodPath = methodLevelPath.startsWith("/") ? methodLevelPath : "/" + methodLevelPath;

        // Remove trailing slash
        if (classPath.endsWith("/")) {
            classPath = classPath.substring(0, classPath.length() - 1);
        }

        return classPath + methodPath;
    }

    /**
     * REST mapping information.
     */
    private static class RestMappingInfo {
        final String httpMethod;
        final String path;
        final String consumes;
        final String produces;

        RestMappingInfo(String httpMethod, String path, String consumes, String produces) {
            this.httpMethod = httpMethod;
            this.path = path;
            this.consumes = consumes;
            this.produces = produces;
        }
    }

    /**
     * Scan configuration
     */
    private static class ScanConfig {
        boolean scanAll = true;           // Default: scan all
        Set<String> packages = new HashSet<>();  // Packages to scan
        Set<String> classes = new HashSet<>();   // Classes to scan
        // Note: excludeBuiltin field has been removed, built-in tools are always scanned
    }
}