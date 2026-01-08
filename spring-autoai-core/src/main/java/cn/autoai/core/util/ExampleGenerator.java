package cn.autoai.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Example generator: generates default descriptions and example values based on types.
 */
public final class ExampleGenerator {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ExampleGenerator() {
    }

    /**
     * Generate default tool description.
     */
    public static String defaultToolDescription(String methodSignature) {
        return "Execute method: " + methodSignature;
    }

    /**
     * Generate default parameter description.
     */
    public static String defaultParamDescription(String name, String typeName) {
        return "Parameter " + name + ", type: " + simplifyType(typeName);
    }

    /**
     * Generate default return description.
     */
    public static String defaultReturnDescription(String typeName) {
        if (typeName == null || typeName.endsWith("void") || typeName.endsWith("Void")) {
            return "No return value";
        }
        return "Return type: " + simplifyType(typeName);
    }

    /**
     * Determine if it's a complex type (collections/arrays containing objects).
     * Complex type definition: elements in collections/arrays are custom objects (not basic types, String, dates, etc.).
     */
    public static boolean isComplexType(Class<?> rawType, String typeName) {
        if (rawType == null) {
            return false;
        }

        // Check if it's a collection type (List, Set, Collection)
        if (Collection.class.isAssignableFrom(rawType)) {
            String elementType = extractGeneric(typeName, 0);
            if (elementType != null) {
                return isComplexObjectType(elementType);
            }
            return false;
        }

        // Check if it's an array
        if (rawType.isArray()) {
            Class<?> componentType = rawType.getComponentType();
            if (componentType != null) {
                return isComplexObjectType(componentType.getName());
            }
            return false;
        }

        // Check if it's a Map
        if (Map.class.isAssignableFrom(rawType)) {
            String valueType = extractGeneric(typeName, 1);
            if (valueType != null) {
                return isComplexObjectType(valueType);
            }
            return false;
        }

        return false;
    }

    /**
     * Determine if it's a complex object type (not basic types, String, dates, etc.).
     */
    private static boolean isComplexObjectType(String typeName) {
        if (typeName == null) {
            return false;
        }

        String normalized = typeName.toLowerCase();

        // Basic types and wrapper classes
        if (normalized.equals("void") || normalized.contains("boolean") ||
            normalized.contains("byte") || normalized.contains("short") ||
            normalized.contains("int") || normalized.contains("long") ||
            normalized.contains("float") || normalized.contains("double") ||
            normalized.contains("char")) {
            return false;
        }

        // String and common simple types
        if (normalized.contains("string") || normalized.contains("charsequence")) {
            return false;
        }

        // Number types
        if (normalized.contains("bigdecimal") || normalized.contains("biginteger")) {
            return false;
        }

        // Date/time types
        if (normalized.contains("localdate") || normalized.contains("localdatetime") ||
            normalized.contains("instant") || normalized.contains("date") ||
            normalized.contains("timestamp")) {
            return false;
        }

        // Common types like URI, URL, UUID
        if (normalized.contains("uri") || normalized.contains("url") ||
            normalized.contains("uuid") || normalized.contains("locale")) {
            return false;
        }

        // If it's a common class in java.* or javax. packages, it's usually not a complex object
        if (typeName.startsWith("java.lang.") || typeName.startsWith("java.math.") ||
            typeName.startsWith("java.time.") || typeName.startsWith("java.util.")) {
            return false;
        }

        // Other custom object types are considered complex
        return true;
    }

    /**
     * Generate example value based on type.
     */
    public static Object exampleValue(Class<?> rawType, String typeName) {
        return exampleValue(rawType, typeName, null);
    }

    /**
     * Generate example value based on type, supporting tool name (for complex field hints).
     * @param rawType Raw type
     * @param typeName Type name
     * @param toolName Tool name (can be null)
     */
    public static Object exampleValue(Class<?> rawType, String typeName, String toolName) {
        if (rawType == void.class || rawType == Void.class) {
            return null;
        }
        if (rawType == null) {
            return defaultByTypeName(typeName);
        }
        if (rawType.isPrimitive()) {
            return defaultPrimitive(rawType);
        }
        if (CharSequence.class.isAssignableFrom(rawType)) {
            return "Example text";
        }
        if (Number.class.isAssignableFrom(rawType)) {
            return defaultNumber(rawType);
        }
        if (Boolean.class.isAssignableFrom(rawType)) {
            return true;
        }
        if (rawType.isEnum()) {
            Object[] constants = rawType.getEnumConstants();
            return constants != null && constants.length > 0 ? constants[0].toString() : "Example enum";
        }
        if (Instant.class.isAssignableFrom(rawType)) {
            return Instant.parse("2024-01-01T00:00:00Z").toString();
        }
        if (LocalDate.class.isAssignableFrom(rawType)) {
            return LocalDate.of(2024, 1, 1).toString();
        }
        if (LocalDateTime.class.isAssignableFrom(rawType)) {
            return LocalDateTime.of(2024, 1, 1, 0, 0, 0).toString();
        }
        if (rawType.isArray()) {
            Object element = exampleValue(rawType.getComponentType(), rawType.getComponentType().getName());
            return List.of(element);
        }
        if (Collection.class.isAssignableFrom(rawType)) {
            String elementType = extractGeneric(typeName, 0);
            Object element = defaultByTypeName(elementType);
            return List.of(element);
        }
        if (Map.class.isAssignableFrom(rawType)) {
            String keyType = extractGeneric(typeName, 0);
            String valueType = extractGeneric(typeName, 1);
            Object key = defaultByTypeName(keyType);
            Object value = defaultByTypeName(valueType);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put(String.valueOf(key), value);
            return map;
        }

        // Enhancement: try to parse complex object field structure
        // Add debug information
        // System.out.println("DEBUG: Processing complex object type: " + rawType.getName());
        return generateComplexObjectExample(rawType, toolName);
    }

    /**
     * Generate parameter example string, preserve original value for string types.
     */
    public static String formatParamExample(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return toJson(value);
    }

    /**
     * Generate return example string (JSON format).
     */
    public static String formatResponseExample(Object value) {
        if (value == null) {
            return "null";
        }
        return toJson(value);
    }

    /**
     * Generate request example JSON.
     */
    public static String buildRequestExample(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }
        return toJson(payload);
    }

    /**
     * Parse example text provided by user into actual example value.
     */
    public static Object parseExampleText(String exampleText, Class<?> rawType, String typeName) {
        if (exampleText == null || exampleText.isBlank()) {
            return exampleValue(rawType, typeName);
        }
        if (rawType == void.class || rawType == Void.class) {
            return null;
        }
        if (rawType != null && CharSequence.class.isAssignableFrom(rawType)) {
            return exampleText;
        }
        if (rawType == boolean.class || rawType == Boolean.class) {
            return Boolean.parseBoolean(exampleText.trim());
        }
        if (rawType == byte.class || rawType == Byte.class) {
            return parseNumber(exampleText, Byte.class);
        }
        if (rawType == short.class || rawType == Short.class) {
            return parseNumber(exampleText, Short.class);
        }
        if (rawType == int.class || rawType == Integer.class) {
            return parseNumber(exampleText, Integer.class);
        }
        if (rawType == long.class || rawType == Long.class) {
            return parseNumber(exampleText, Long.class);
        }
        if (rawType == float.class || rawType == Float.class) {
            return parseNumber(exampleText, Float.class);
        }
        if (rawType == double.class || rawType == Double.class) {
            return parseNumber(exampleText, Double.class);
        }
        if (rawType == BigDecimal.class) {
            return parseNumber(exampleText, BigDecimal.class);
        }
        if (rawType == BigInteger.class) {
            return parseNumber(exampleText, BigInteger.class);
        }
        if (rawType != null && rawType.isEnum()) {
            Object[] constants = rawType.getEnumConstants();
            if (constants != null) {
                for (Object constant : constants) {
                    if (constant.toString().equalsIgnoreCase(exampleText.trim())) {
                        return constant.toString();
                    }
                }
            }
        }
        try {
            return OBJECT_MAPPER.readValue(exampleText, Object.class);
        } catch (Exception ex) {
            return exampleText;
        }
    }

    private static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private static Object parseNumber(String value, Class<?> targetType) {
        try {
            if (targetType == Byte.class) {
                return Byte.parseByte(value.trim());
            }
            if (targetType == Short.class) {
                return Short.parseShort(value.trim());
            }
            if (targetType == Integer.class) {
                return Integer.parseInt(value.trim());
            }
            if (targetType == Long.class) {
                return Long.parseLong(value.trim());
            }
            if (targetType == Float.class) {
                return Float.parseFloat(value.trim());
            }
            if (targetType == Double.class) {
                return Double.parseDouble(value.trim());
            }
            if (targetType == BigDecimal.class) {
                return new BigDecimal(value.trim());
            }
            if (targetType == BigInteger.class) {
                return new BigInteger(value.trim());
            }
        } catch (Exception ex) {
            return value;
        }
        return value;
    }

    private static Object defaultByTypeName(String typeName) {
        if (typeName == null) {
            return "Example value";
        }
        String normalized = typeName.toLowerCase(Locale.ROOT);
        if (normalized.contains("void")) {
            return null;
        }
        if (normalized.contains("string")) {
            return "Example text";
        }
        if (normalized.contains("boolean")) {
            return true;
        }
        if (normalized.contains("bigdecimal")) {
            return BigDecimal.valueOf(1.23);
        }
        if (normalized.contains("biginteger")) {
            return BigInteger.valueOf(1);
        }
        if (normalized.contains("double") || normalized.contains("float")) {
            return 1.23;
        }
        if (normalized.contains("int") || normalized.contains("long") || normalized.contains("short")
            || normalized.contains("byte")) {
            return 1;
        }
        if (normalized.contains("list") || normalized.contains("set") || normalized.contains("collection")) {
            String elementType = extractGeneric(typeName, 0);
            List<Object> list = new ArrayList<>();
            list.add(defaultByTypeName(elementType));
            return list;
        }
        if (normalized.contains("map")) {
            String keyType = extractGeneric(typeName, 0);
            String valueType = extractGeneric(typeName, 1);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put(String.valueOf(defaultByTypeName(keyType)), defaultByTypeName(valueType));
            return map;
        }
        if (normalized.contains("[]")) {
            List<Object> list = new ArrayList<>();
            list.add("Example value");
            return list;
        }
        return "Example value";
    }

    private static Object defaultPrimitive(Class<?> type) {
        if (type == boolean.class) {
            return true;
        }
        if (type == byte.class || type == short.class || type == int.class || type == long.class) {
            return 1;
        }
        if (type == float.class || type == double.class) {
            return 1.23;
        }
        if (type == char.class) {
            return "A";
        }
        return "Example value";
    }

    private static Object defaultNumber(Class<?> type) {
        if (type == Integer.class || type == Short.class || type == Long.class || type == Byte.class) {
            return 1;
        }
        if (type == BigDecimal.class) {
            return BigDecimal.valueOf(1.23);
        }
        if (type == BigInteger.class) {
            return BigInteger.valueOf(1);
        }
        return 1.23;
    }

    private static String simplifyType(String typeName) {
        if (typeName == null) {
            return "unknown";
        }
        StringBuilder result = new StringBuilder();
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < typeName.length(); i++) {
            char ch = typeName.charAt(i);
            if (Character.isJavaIdentifierPart(ch) || ch == '.' || ch == '$') {
                token.append(ch);
            } else {
                appendToken(result, token);
                result.append(ch);
            }
        }
        appendToken(result, token);
        return result.toString();
    }

    private static void appendToken(StringBuilder result, StringBuilder token) {
        if (token.length() == 0) {
            return;
        }
        String raw = token.toString();
        int lastDot = raw.lastIndexOf('.');
        String simple = lastDot >= 0 ? raw.substring(lastDot + 1) : raw;
        result.append(simple.replace('$', '.'));
        token.setLength(0);
    }

    private static String extractGeneric(String typeName, int index) {
        if (typeName == null) {
            return null;
        }
        int start = typeName.indexOf('<');
        int end = typeName.lastIndexOf('>');
        if (start < 0 || end < 0 || end <= start) {
            return null;
        }
        String inner = typeName.substring(start + 1, end);
        String[] parts = inner.split(",");
        if (index < 0 || index >= parts.length) {
            return null;
        }
        return parts[index].trim();
    }
    
    /**
     * Generate example for complex objects by parsing field structure through reflection
     */
    private static Object generateComplexObjectExample(Class<?> rawType) {
        return generateComplexObjectExample(rawType, null);
    }

    /**
     * Generate example for complex objects, supporting complex field hints
     * @param rawType Object type
     * @param toolName Tool name (used to generate hint information), can be null
     */
    private static Object generateComplexObjectExample(Class<?> rawType, String toolName) {
        Map<String, Object> example = new LinkedHashMap<>();

        try {
            // Get all public fields
            java.lang.reflect.Field[] fields = rawType.getFields();

            for (java.lang.reflect.Field field : fields) {
                // Skip static fields
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                String fieldName = field.getName();
                Class<?> fieldType = field.getType();
                String fieldTypeName = field.getGenericType().getTypeName();

                // Check if field is complex type
                boolean isComplexField = isComplexType(fieldType, fieldTypeName);

                Object fieldExample;
                if (isComplexField) {
                    // Complex field, generate hint information
                    if (toolName != null && !toolName.isBlank()) {
                        fieldExample = "[Complex field, use autoai.tool_detail(\"" + toolName + "\") to query detailed structure]";
                    } else {
                        fieldExample = "[Complex field, need to query detailed structure]";
                    }
                } else {
                    // Non-complex field, generate normal example
                    fieldExample = generateFieldExample(fieldType, fieldTypeName, fieldName);
                }

                example.put(fieldName, fieldExample);
            }

            // If no public fields, try to infer fields through constructor or getter methods
            if (example.isEmpty()) {
                example = generateExampleFromMethods(rawType, toolName);
            }

            // If still no fields, return default example
            if (example.isEmpty()) {
                example.put("Example field", "Example value");
            }

        } catch (Exception e) {
            // If reflection fails, return default example
            example.put("Example field", "Example value");
        }

        return example;
    }
    
    /**
     * Infer field structure by analyzing class methods (for classes without public fields)
     */
    private static Map<String, Object> generateExampleFromMethods(Class<?> rawType) {
        return generateExampleFromMethods(rawType, null);
    }

    /**
     * Infer field structure by analyzing class methods (for classes without public fields)
     * @param rawType Object type
     * @param toolName Tool name (used to generate hint information), can be null
     */
    private static Map<String, Object> generateExampleFromMethods(Class<?> rawType, String toolName) {
        Map<String, Object> example = new LinkedHashMap<>();

        try {
            java.lang.reflect.Method[] methods = rawType.getMethods();

            for (java.lang.reflect.Method method : methods) {
                String methodName = method.getName();

                // Find setter methods to infer fields
                if (methodName.startsWith("set") && methodName.length() > 3 &&
                    method.getParameterCount() == 1 &&
                    method.getReturnType() == void.class) {

                    String fieldName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
                    Class<?> paramType = method.getParameterTypes()[0];
                    // Use generic type name, preserve generic information
                    String paramTypeName = method.getGenericParameterTypes()[0].getTypeName();

                    // Check if field is complex type
                    boolean isComplexField = isComplexType(paramType, paramTypeName);

                    Object fieldExample;
                    if (isComplexField) {
                        // Complex field, generate hint information
                        if (toolName != null && !toolName.isBlank()) {
                            fieldExample = "[Complex field, use autoai.tool_detail(\"" + toolName + "\") to query detailed structure]";
                        } else {
                            fieldExample = "[Complex field, need to query detailed structure]";
                        }
                    } else {
                        // Non-complex field, generate normal example
                        fieldExample = generateFieldExample(paramType, paramTypeName, fieldName);
                    }

                    example.put(fieldName, fieldExample);
                }
            }

        } catch (Exception e) {
            // Ignore exceptions
        }

        return example;
    }
    
    /**
     * Generate example value for fields, avoid infinite recursion
     */
    private static Object generateFieldExample(Class<?> fieldType, String typeName, String fieldName) {
        // Basic types and common types
        if (fieldType.isPrimitive()) {
            return defaultPrimitive(fieldType);
        }
        if (CharSequence.class.isAssignableFrom(fieldType)) {
            // Generate more meaningful examples based on field name
            return generateMeaningfulStringExample(fieldName);
        }
        if (Number.class.isAssignableFrom(fieldType)) {
            return generateMeaningfulNumberExample(fieldName);
        }
        if (Boolean.class.isAssignableFrom(fieldType)) {
            return true;
        }
        if (fieldType.isEnum()) {
            Object[] constants = fieldType.getEnumConstants();
            return constants != null && constants.length > 0 ? constants[0].toString() : "Example enum";
        }
        if (Collection.class.isAssignableFrom(fieldType)) {
            // Generate more meaningful examples for List types
            return generateMeaningfulListExample(fieldType, typeName, fieldName);
        }
        if (Map.class.isAssignableFrom(fieldType)) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("Example key", "Example value");
            return map;
        }

        // For complex objects, return simplified example to avoid infinite recursion
        return generateComplexObjectExample(fieldType);
    }
    
    /**
     * Generate meaningful string example based on field name
     */
    private static String generateMeaningfulStringExample(String fieldName) {
        if (fieldName == null) return "Example text";

        String lowerName = fieldName.toLowerCase();
        if (lowerName.contains("name")) {
            return "Zhang San";
        }
        if (lowerName.contains("department")) {
            return "Technical Department";
        }
        if (lowerName.contains("position")) {
            return "Engineer";
        }
        if (lowerName.contains("operation")) {
            return "INCREASE";
        }
        if (lowerName.contains("status")) {
            return "In Progress";
        }
        if (lowerName.contains("date")) {
            return "2024-01-01";
        }
        if (lowerName.contains("reason")) {
            return "Example reason";
        }
        return "Example text";
    }

    /**
     * Generate meaningful number example based on field name
     */
    private static Object generateMeaningfulNumberExample(String fieldName) {
        if (fieldName == null) return 1;

        String lowerName = fieldName.toLowerCase();
        if (lowerName.contains("salary") || lowerName.contains("amount")) {
            return 50000.0;
        }
        if (lowerName.contains("age")) {
            return 25;
        }
        if (lowerName.contains("count")) {
            return 10;
        }
        if (lowerName.contains("price")) {
            return 99.99;
        }
        return 1;
    }
    
    /**
     * Generate meaningful list example based on field name
     */
    private static Object generateMeaningfulListExample(Class<?> fieldType, String typeName, String fieldName) {
        if (fieldName == null) return List.of("Example element");

        String lowerName = fieldName.toLowerCase();

        // Try to get element type from generic type
        String elementTypeName = extractGeneric(typeName, 0);
        if (elementTypeName != null) {
            try {
                // Load element type
                String elementClassName = elementTypeName;
                if (elementClassName.contains("<")) {
                    elementClassName = elementClassName.substring(0, elementClassName.indexOf("<"));
                }
                Class<?> elementClass = Class.forName(elementClassName);

                // If element is a complex object, recursively generate example
                if (!elementClass.isPrimitive() &&
                    !CharSequence.class.isAssignableFrom(elementClass) &&
                    !Number.class.isAssignableFrom(elementClass) &&
                    !Boolean.class.isAssignableFrom(elementClass) &&
                    !elementClass.isEnum()) {

                    Object elementExample = exampleValue(elementClass, elementTypeName);
                    return List.of(elementExample);
                }
            } catch (Exception e) {
                // Ignore exceptions, use default logic
            }
        }

        // Special handling for updates field, try to parse generic type
        if (lowerName.contains("update") && typeName != null) {
            if (typeName.contains("SalaryUpdate") || typeName.contains("ComplexClass")) {
                // Generate example for SalaryUpdate or ComplexClass list
                Map<String, Object> updateExample = new LinkedHashMap<>();
                updateExample.put("employeeName", "Zhang San");
                updateExample.put("operation", "INCREASE");
                updateExample.put("amount", 5000.0);
                return List.of(updateExample);
            }
        }

        // Special handling for items field (order items)
        if (lowerName.contains("item") && typeName != null) {
            if (typeName.contains("OrderItem")) {
                Map<String, Object> itemExample = new LinkedHashMap<>();
                itemExample.put("productId", 1);
                itemExample.put("quantity", 2);
                return List.of(itemExample);
            }
        }

        if (lowerName.contains("member")) {
            return List.of("Zhang San", "Li Si");
        }
        if (lowerName.contains("attendee")) {
            return List.of("Zhang San");
        }
        if (lowerName.contains("tag")) {
            return List.of("Important", "Urgent");
        }

        return List.of("Example element");
    }

    /**
     * Generate tool call example in JSON format for new tag-based protocol.
     * Format: {"name": "ToolName", "arguments": {...}}
     */
    public static String buildToolCallExample(String toolName, Map<String, Object> arguments) {
        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("name", toolName);
        toolCall.put("arguments", arguments);
        return toJson(toolCall);
    }
}
