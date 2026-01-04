package cn.autoai.core.registry;

import cn.autoai.core.web.RequestContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Execute target method based on tool definition.
 * Supports both method direct invocation and REST API call methods.
 */
public class ToolInvoker {
    private final ObjectMapper objectMapper;
    private final RestToolInvoker restToolInvoker;

    public ToolInvoker(ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.restToolInvoker = new RestToolInvoker(restTemplate, objectMapper);
    }

    /**
     * Execute target method based on tool definition, converting JSON parameters to method parameters.
     */
    public Object invoke(ToolDefinition definition, String argumentsJson) {
        return invoke(definition, argumentsJson, null);
    }

    /**
     * Execute target method based on tool definition, converting JSON parameters to method parameters.
     * Supports passing request context for REST API calls.
     */
    public Object invoke(ToolDefinition definition, String argumentsJson, RequestContext context) {
        // Select invocation method based on tool type
        if (definition.getToolType() == ToolDefinition.ToolType.REST_API_CALL) {
            // REST API call
            return restToolInvoker.invoke(definition, argumentsJson, context);
        } else {
            // Method direct invocation (original method)
            return invokeByReflection(definition, argumentsJson);
        }
    }

    /**
     * Execute target method based on tool definition, mapping passed list parameters in parameter order.
     */
    public Object invokeWithArgs(ToolDefinition definition, List<Object> args) {
        return invokeWithArgs(definition, args, null);
    }

    /**
     * Execute target method based on tool definition, mapping passed list parameters in parameter order.
     * Supports passing request context for REST API calls.
     */
    public Object invokeWithArgs(ToolDefinition definition, List<Object> args, RequestContext context) {
        // REST API call temporarily does not support list parameter method, need to convert to JSON
        Map<String, Object> paramMap = convertArgsToParamMap(definition, args);
        try {
            String argumentsJson = objectMapper.writeValueAsString(paramMap);
            return invoke(definition, argumentsJson, context);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to convert args to JSON", ex);
        }
    }

    /**
     * Invoke method through reflection (original method).
     */
    private Object invokeByReflection(ToolDefinition definition, String argumentsJson) {
        Map<String, Object> arguments = parseArguments(argumentsJson);

        // Special handling: if there is only one parameter but the passed parameter name does not match, try to wrap passed fields into an object
        if (definition.getParamBindings().size() == 1 && !arguments.isEmpty()) {
            ToolParamBinding onlyBinding = definition.getParamBindings().get(0);
            // If the passed parameter does not have a matching parameter name, try to wrap
            if (!arguments.containsKey(onlyBinding.getName())) {
                // Check if it is a complex type parameter
                if (isComplexType(onlyBinding.getType())) {
                    // Wrap all passed parameters into an object
                    try {
                        Object converted = objectMapper.convertValue(arguments, onlyBinding.getType());
                        Object[] params = new Object[1];
                        params[0] = converted;
                        return definition.getMethod().invoke(definition.getBean(), params);
                    } catch (Exception ex) {
                        throw new IllegalArgumentException("Failed to convert wrapped arguments for parameter '" +
                            onlyBinding.getName() + "' of type " + onlyBinding.getType().getName() +
                            ". Error: " + ex.getMessage(), ex);
                    }
                }
            }
        }

        Object[] params = new Object[definition.getParamBindings().size()];
        for (ToolParamBinding binding : definition.getParamBindings()) {
            if (!arguments.containsKey(binding.getName())) {
                if (binding.isRequired()) {
                    throw new IllegalArgumentException("Missing required argument: " + binding.getName());
                }
                params[binding.getIndex()] = null;
                continue;
            }
            Object rawValue = arguments.get(binding.getName());
            try {
                Object converted = objectMapper.convertValue(rawValue, binding.getType());
                params[binding.getIndex()] = converted;
            } catch (Exception ex) {
                throw new IllegalArgumentException("Failed to convert argument '" + binding.getName() +
                    "' of type " + binding.getType().getName() + " from value: " + rawValue +
                    ". Error: " + ex.getMessage(), ex);
            }
        }
        try {
            return definition.getMethod().invoke(definition.getBean(), params);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            throw new IllegalStateException("Tool invocation failed: " + definition.getName() +
                ". Error: " + cause.getMessage(), cause);
        }
    }

    /**
     * Determine if it is a complex type (non-simple type object)
     */
    private boolean isComplexType(Class<?> type) {
        // Primitive types and wrapper types
        if (type.isPrimitive() ||
            type == String.class ||
            type == Integer.class || type == int.class ||
            type == Long.class || type == long.class ||
            type == Double.class || type == double.class ||
            type == Float.class || type == float.class ||
            type == Boolean.class || type == boolean.class ||
            type == Byte.class || type == byte.class ||
            type == Short.class || type == short.class ||
            type == Character.class || type == char.class) {
            return false;
        }
        // Collection types and Map types
        if (java.util.Collection.class.isAssignableFrom(type) ||
            java.util.Map.class.isAssignableFrom(type)) {
            return false;
        }
        // Other types are considered complex types
        return true;
    }

    /**
     * Convert parameter list to parameter Map.
     */
    private Map<String, Object> convertArgsToParamMap(ToolDefinition definition, List<Object> args) {
        Map<String, Object> paramMap = new java.util.LinkedHashMap<>();
        for (ToolParamBinding binding : definition.getParamBindings()) {
            if (binding.getIndex() < args.size()) {
                paramMap.put(binding.getName(), args.get(binding.getIndex()));
            }
        }
        return paramMap;
    }

    private Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid tool arguments JSON", ex);
        }
    }
}
