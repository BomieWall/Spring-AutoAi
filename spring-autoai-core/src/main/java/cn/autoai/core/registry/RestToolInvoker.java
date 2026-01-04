package cn.autoai.core.registry;

import cn.autoai.core.web.RequestContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API tool invoker that initiates HTTP requests via RestTemplate to invoke Controller methods.
 */
public class RestToolInvoker {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * URL construction result, containing the final URL and parameters that should be placed in the body.
     */
    private static class UrlResult {
        final String url;
        final Map<String, Object> bodyParams;

        UrlResult(String url, Map<String, Object> bodyParams) {
            this.url = url;
            this.bodyParams = bodyParams;
        }
    }

    public RestToolInvoker(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Invoke REST API tool via HTTP.
     *
     * @param definition    Tool definition
     * @param argumentsJson Parameter JSON
     * @param context       Request context (containing Cookie and Header)
     * @return Tool execution result
     */
    public Object invoke(ToolDefinition definition, String argumentsJson, RequestContext context) {
        ToolDefinition.RestApiInfo apiInfo = definition.getRestApiInfo();
        if (apiInfo == null) {
            throw new IllegalArgumentException("Tool definition does not contain REST API info: " + definition.getName());
        }

        Map<String, Object> arguments = parseArguments(argumentsJson);

        try {
            // Build request URL and categorize parameters
            UrlResult urlResult = buildUrl(apiInfo.getRestPath(), arguments, context);

            String httpMethod = apiInfo.getHttpMethod().toUpperCase();
            String finalUrl;
            ResponseEntity<String> response;

            if ("GET".equals(httpMethod) || "DELETE".equals(httpMethod)) {
                // GET/DELETE: All parameters are in the URL (path parameters + query parameters)
                finalUrl = addQueryParams(urlResult.url, urlResult.bodyParams);

                // Build request headers (without Content-Type)
                HttpHeaders headers = buildHeadersForNoBody(context, apiInfo);

                // Use HttpEntity with only headers, body is null
                HttpEntity<?> requestEntity = new HttpEntity<>(headers);

                // Send request
                response = restTemplate.exchange(
                        finalUrl,
                        HttpMethod.valueOf(httpMethod),
                        requestEntity,
                        String.class
                );
            } else {
                // POST/PUT/PATCH: Path parameters are in the URL, other parameters are in the body
                finalUrl = urlResult.url;

                // Build request headers (including Content-Type)
                HttpHeaders headers = buildHeaders(context, apiInfo);

                // Build request body (pass tool definition to correctly handle @RequestBody parameters)
                Object requestBody = buildRequestBody(definition, urlResult.bodyParams);

                // Create request entity
                HttpEntity<?> requestEntity = new HttpEntity<>(requestBody, headers);

                // Send request
                response = restTemplate.exchange(
                        finalUrl,
                        HttpMethod.valueOf(httpMethod),
                        requestEntity,
                        String.class
                );
            }

            // Return response body
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            // Handle 4xx errors (client errors)
            return handleClientError(ex, definition);
        } catch (HttpServerErrorException ex) {
            // Handle 5xx errors (server errors)
            return handleServerError(ex, definition);
        } catch (Exception ex) {
            // Handle other exceptions
            throw new IllegalStateException("REST API invocation failed: " + definition.getName() +
                    ". Error: " + ex.getMessage(), ex);
        }
    }

    /**
     * Handle 4xx client errors
     */
    private String handleClientError(HttpClientErrorException ex, ToolDefinition definition) {
        HttpStatusCode status = ex.getStatusCode();
        String responseBody = ex.getResponseBodyAsString();
        String errorMessage = buildErrorMessage(status, responseBody, definition);

        // Return formatted error message for AI understanding
        return String.format("{\"error\": true, \"status\": %d, \"message\": \"%s\", \"tool\": \"%s\"}",
                status.value(), errorMessage, definition.getName());
    }

    /**
     * Handle 5xx server errors
     */
    private String handleServerError(HttpServerErrorException ex, ToolDefinition definition) {
        HttpStatusCode status = ex.getStatusCode();
        String responseBody = ex.getResponseBodyAsString();
        String errorMessage = buildErrorMessage(status, responseBody, definition);

        // Return formatted error message for AI understanding
        return String.format("{\"error\": true, \"status\": %d, \"message\": \"%s\", \"tool\": \"%s\"}",
                status.value(), errorMessage, definition.getName());
    }

    /**
     * Build error message based on status code
     */
    private String buildErrorMessage(HttpStatusCode status, String responseBody, ToolDefinition definition) {
        // Return friendly error description based on status code
        String baseMessage = switch (status.value()) {
            case 400 -> "Request parameter error";
            case 401 -> "Unauthorized, login or authentication required";
            case 403 -> "Insufficient permissions, no access to this resource";
            case 404 -> "Requested resource does not exist";
            case 405 -> "Unsupported request method";
            case 409 -> "Request conflict, resource may already exist or state does not allow this operation";
            case 429 -> "Too many requests, rate limited";
            default -> "Client request error";
        };

        // If response body contains error information, try to extract it
        if (responseBody != null && !responseBody.isBlank()) {
            // Simple check if response body is JSON
            if (responseBody.trim().startsWith("{")) {
                try {
                    Map<String, Object> errorMap = objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
                    // Try to extract common error fields
                    Object message = errorMap.get("message");
                    Object error = errorMap.get("error");
                    Object msg = errorMap.get("msg");

                    if (message != null) {
                        return baseMessage + ": " + message;
                    } else if (error != null) {
                        return baseMessage + ": " + error;
                    } else if (msg != null) {
                        return baseMessage + ": " + msg;
                    }
                } catch (Exception parseEx) {
                    // JSON parsing failed, use original response body
                }
            }
            // If not JSON or parsing failed, extract part of response body (avoid too long)
            String preview = responseBody.length() > 100 ? responseBody.substring(0, 100) + "..." : responseBody;
            return baseMessage + " (" + preview + ")";
        }

        return baseMessage;
    }

    /**
     * Add parameters to URL as query parameters.
     */
    private String addQueryParams(String url, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            builder.queryParam(entry.getKey(), entry.getValue());
        }
        return builder.build().toUriString();
    }

    /**
     * Parse parameter JSON.
     */
    private Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid tool arguments JSON", ex);
        }
    }

    /**
     * Build request URL, replacing path parameters and query parameters in the URL.
     * Returns UrlResult containing the constructed URL and parameters that should be placed in the body.
     */
    private UrlResult buildUrl(String restPath, Map<String, Object> arguments, RequestContext context) {
        // If path is already an absolute URL (starts with http:// or https://), use it directly
        if (restPath.startsWith("http://") || restPath.startsWith("https://")) {
            return buildUrlWithPath(restPath, arguments);
        }

        // Otherwise, build absolute URL using baseUrl from context
        String baseUrl = (context != null && context.getBaseUrl() != null) ? context.getBaseUrl() : "http://localhost:8080";
        String fullPath = baseUrl + (restPath.startsWith("/") ? restPath : "/" + restPath);
        return buildUrlWithPath(fullPath, arguments);
    }

    /**
     * Build URL based on the given complete path.
     * Parameters are categorized into three types:
     * 1. Path parameters: Replace {id} placeholders in the URL
     * 2. Query parameters: Add after the ? in the URL
     * 3. Request body parameters: Parameters not in the URL, returned to caller to put in body
     */
    private UrlResult buildUrlWithPath(String fullPath, Map<String, Object> arguments) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(fullPath);
        String path = builder.build().toUriString();

        // For storing parameter usage
        Map<String, Object> queryParams = new java.util.LinkedHashMap<>();
        Map<String, Object> bodyParams = new java.util.LinkedHashMap<>();

        // Check if path contains path parameters (like {id})
        if (path.contains("{")) {
            // Replace path parameters
            String url = path;
            java.util.Set<String> remainingPlaceholders = new java.util.HashSet<>();

            for (Map.Entry<String, Object> entry : arguments.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                if (url.contains(placeholder)) {
                    // This is a path parameter, replace it in the URL
                    Object value = entry.getValue();
                    String paramValue;
                    // Handle different types of values, ensure correct conversion to string
                    if (value == null) {
                        paramValue = "";
                    } else if (value instanceof String) {
                        paramValue = (String) value;
                    } else if (value instanceof Number || value instanceof Boolean) {
                        // For numbers and booleans, ensure correct conversion
                        paramValue = String.valueOf(value);
                    } else {
                        // For other types (like Map, List), try to serialize as JSON
                        try {
                            paramValue = objectMapper.writeValueAsString(value);
                        } catch (Exception e) {
                            // Serialization failed, use toString
                            paramValue = String.valueOf(value);
                        }
                    }
                    url = url.replace(placeholder, paramValue);
                } else {
                    // Not a path parameter, store temporarily
                    bodyParams.put(entry.getKey(), entry.getValue());
                }
            }

            // Check if there are any unreplaced placeholders
            if (url.contains("{")) {
                // Extract all unreplaced placeholder names
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{([^}]+)\\}");
                java.util.regex.Matcher matcher = pattern.matcher(url);
                while (matcher.find()) {
                    remainingPlaceholders.add(matcher.group(1));
                }

                // If there are unreplaced placeholders, this is an error condition
                if (!remainingPlaceholders.isEmpty()) {
                    throw new IllegalArgumentException("Missing required path parameters: " + remainingPlaceholders +
                            ". Available parameters: " + arguments.keySet());
                }
            }

            return new UrlResult(url, bodyParams);
        } else {
            // No path parameters, temporarily put all parameters in bodyParams
            // The caller will decide whether to use them as query parameters or request body based on HTTP method
            bodyParams.putAll(arguments);
            return new UrlResult(path, bodyParams);
        }
    }

    /**
     * Build request headers, including Cookie and Header passed from context.
     */
    private HttpHeaders buildHeaders(RequestContext context, ToolDefinition.RestApiInfo apiInfo) {
        HttpHeaders headers = new HttpHeaders();

        // Set Content-Type
        if (apiInfo.getConsumes() != null && !apiInfo.getConsumes().isEmpty()) {
            headers.set("Content-Type", apiInfo.getConsumes());
        } else {
            headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        }

        // Set Accept
        if (apiInfo.getProduces() != null && !apiInfo.getProduces().isEmpty()) {
            headers.set("Accept", apiInfo.getProduces());
        } else {
            headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        }

        // Pass headers from context
        if (context != null && context.getHeaders() != null) {
            for (Map.Entry<String, String> entry : context.getHeaders().entrySet()) {
                // Skip Content-Type and Accept as they are already set
                if (!"Content-Type".equalsIgnoreCase(entry.getKey()) &&
                    !"Accept".equalsIgnoreCase(entry.getKey()) &&
                    !"content-length".equalsIgnoreCase(entry.getKey())) {
                    headers.set(entry.getKey(), entry.getValue());
                }
            }
        }

        // Set Cookie
        if (context != null && context.getCookies() != null && !context.getCookies().isEmpty()) {
            String cookieHeader = context.getCookies().entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("; "));
            headers.set("Cookie", cookieHeader);
        }

        return headers;
    }

    /**
     * Build request headers (for requests without body, like GET/DELETE).
     * Do not set Content-Type.
     */
    private HttpHeaders buildHeadersForNoBody(RequestContext context, ToolDefinition.RestApiInfo apiInfo) {
        HttpHeaders headers = new HttpHeaders();

        // Do not set Content-Type because there is no request body

        // Set Accept
        if (apiInfo.getProduces() != null && !apiInfo.getProduces().isEmpty()) {
            headers.set("Accept", apiInfo.getProduces());
        } else {
            headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        }

        // Pass headers from context (skip Content-Type)
        if (context != null && context.getHeaders() != null) {
            for (Map.Entry<String, String> entry : context.getHeaders().entrySet()) {
                // Skip Content-Type and Accept as they are already handled
                if (!"Content-Type".equalsIgnoreCase(entry.getKey()) &&
                    !"Accept".equalsIgnoreCase(entry.getKey()) &&
                    !"content-length".equalsIgnoreCase(entry.getKey())) {
                    String value = entry.getValue();
                    if (value != null) {
                        headers.set(entry.getKey(), value);
                    }
                }
            }
        }

        // Set Cookie
        if (context != null && context.getCookies() != null && !context.getCookies().isEmpty()) {
            String cookieHeader = context.getCookies().entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("; "));
            headers.set("Cookie", cookieHeader);
        }

        return headers;
    }

    /**
     * Build request body.
     * For POST/PUT/PATCH requests, use @RequestBody parameter as JSON request body.
     *
     * Special handling: If there is only one @RequestBody parameter, use its value directly as request body,
     * instead of wrapping it in parameter name.
     */
    private Object buildRequestBody(ToolDefinition definition, Map<String, Object> bodyParams) {
        if (bodyParams == null || bodyParams.isEmpty()) {
            return null;
        }

        // Check if there are @RequestBody parameters
        List<ToolParamBinding> requestBodyBindings = definition.getParamBindings().stream()
                .filter(binding -> binding.getParamSource() == ToolParamBinding.ParamSource.REQUEST_BODY)
                .toList();

        // If there are @RequestBody parameters
        if (!requestBodyBindings.isEmpty()) {
            // If there is only one @RequestBody parameter, use its value directly as request body
            if (requestBodyBindings.size() == 1) {
                ToolParamBinding requestBodyBinding = requestBodyBindings.get(0);
                Object bodyValue = bodyParams.get(requestBodyBinding.getName());

                if (bodyValue != null) {
                    try {
                        // Check if it's an empty Map (AI may pass {} to indicate no request body)
                        if (bodyValue instanceof Map && ((Map<?, ?>) bodyValue).isEmpty()) {
                            // Empty Map is treated as no request body
                            return null;
                        }
                        // If already a string, return directly; otherwise convert to JSON
                        if (bodyValue instanceof String) {
                            return bodyValue;
                        } else {
                            return objectMapper.writeValueAsString(bodyValue);
                        }
                    } catch (Exception ex) {
                        throw new IllegalArgumentException("Failed to build request body for parameter: " +
                                requestBodyBinding.getName(), ex);
                    }
                } else {
                    // requestBody value is null, check if required
                    if (requestBodyBinding.isRequired()) {
                        throw new IllegalArgumentException("Missing required request body parameter: " +
                                requestBodyBinding.getName());
                    }
                    return null;
                }
            }
        }

        // No @RequestBody parameter, or multiple @RequestBody parameters (uncommon)
        // Convert entire Map to JSON (only include non-path, non-query parameters)
        try {
            Map<String, Object> nonPathParams = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : bodyParams.entrySet()) {
                String paramName = entry.getKey();
                // Check if this parameter is a path or query parameter
                boolean isPathOrQueryParam = definition.getParamBindings().stream()
                        .filter(binding -> binding.getName().equals(paramName))
                        .anyMatch(binding -> binding.getParamSource() == ToolParamBinding.ParamSource.PATH_VARIABLE ||
                                            binding.getParamSource() == ToolParamBinding.ParamSource.REQUEST_PARAM);
                // Only put in request body if not a path or query parameter
                if (!isPathOrQueryParam) {
                    nonPathParams.put(paramName, entry.getValue());
                }
            }
            if (nonPathParams.isEmpty()) {
                return null;
            }
            return objectMapper.writeValueAsString(nonPathParams);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to build request body", ex);
        }
    }
}
