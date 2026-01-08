package cn.autoai.core.util;

import cn.autoai.core.annotation.AutoAiParam;
import cn.autoai.core.annotation.AutoAiReturn;
import cn.autoai.core.annotation.AutoAiTool;
import cn.autoai.core.model.ToolDetail;
import cn.autoai.core.model.ToolParamSpec;
import cn.autoai.core.model.ToolReturnSpec;
import cn.autoai.core.registry.ToolDefinition;
import cn.autoai.core.registry.ToolParamBinding;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Build tool definition from annotated methods.
 */
public final class ToolDefinitionBuilder {
    private static final Pattern SYNTHETIC_PARAM = Pattern.compile("arg\\d+");

    private ToolDefinitionBuilder() {
    }

    /**
     * Build tool definition from annotated methods, return null if conditions are not met.
     */
    public static ToolDefinition fromMethod(Object bean, Method method) {
        AutoAiTool tool = method.getAnnotation(AutoAiTool.class);
        if (tool == null) {
            return null;
        }
        String toolName = tool.name();
        if (toolName == null || toolName.isBlank()) {
            // 使用 AopProxyUtils 获取目标类，避免 CGLIB 代理类名问题
            Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
            toolName = targetClass.getSimpleName() + "." + method.getName();
        }

        ToolDetail detail = new ToolDetail();
        detail.setName(toolName);
        detail.setDescription(emptyToNull(tool.description()));
        detail.setRequestExample(emptyToNull(tool.requestExample()));
        detail.setResponseExample(emptyToNull(tool.responseExample()));
        detail.setDeclaringClass(method.getDeclaringClass().getName());
        detail.setMethodSignature(buildSignature(method));

        List<ToolParamSpec> paramSpecs = new ArrayList<>();
        List<ToolParamBinding> bindings = new ArrayList<>();
        Map<String, Object> requestExamplePayload = new LinkedHashMap<>();
        Parameter[] parameters = method.getParameters();

        // Check if there are complex parameters
        boolean hasComplexParams = false;
        StringBuilder complexParamHints = new StringBuilder();

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            AutoAiParam param = parameter.getAnnotation(AutoAiParam.class);
            String paramName = param != null && !param.name().isBlank() ? param.name() : parameter.getName();
            if (paramName == null || paramName.isBlank() || SYNTHETIC_PARAM.matcher(paramName).matches()) {
                paramName = "param" + (i + 1);
            }
            String paramDesc = param != null ? emptyToNull(param.description()) : null;
            boolean required = param == null || param.required();
            String example = param != null ? emptyToNull(param.example()) : null;
            String typeName = parameter.getParameterizedType().getTypeName();

            // Check if it's a complex parameter
            boolean isComplex = ExampleGenerator.isComplexType(parameter.getType(), typeName);

            Object exampleValue;
            Object requestExampleValue;

            if (isComplex && example == null) {
                // Complex parameter, don't generate detailed example, use hint
                hasComplexParams = true;
                if (complexParamHints.length() > 0) {
                    complexParamHints.append("、");
                }
                complexParamHints.append(paramName);

                exampleValue = "[Complex parameter, use autoai.tool_detail(\"" + toolName + "\") to query detailed structure]";
                requestExampleValue = exampleValue;
            } else {
                // Pass tool name to detect internal complex fields when generating object examples
                exampleValue = ExampleGenerator.exampleValue(parameter.getType(), typeName, toolName);
                requestExampleValue = exampleValue;
            }

            if (paramDesc == null) {
                paramDesc = ExampleGenerator.defaultParamDescription(paramName, typeName);
            }
            if (example == null) {
                example = ExampleGenerator.formatParamExample(exampleValue);
            } else {
                requestExampleValue = ExampleGenerator.parseExampleText(example, parameter.getType(), typeName);
            }
            requestExamplePayload.put(paramName, requestExampleValue);

            ToolParamSpec spec = new ToolParamSpec(paramName, typeName, paramDesc, required, example);
            paramSpecs.add(spec);
            bindings.add(new ToolParamBinding(paramName, parameter.getType(), i, required));
        }
        detail.setParams(paramSpecs);

        AutoAiReturn autoReturn = method.getAnnotation(AutoAiReturn.class);
        String returnType = method.getGenericReturnType().getTypeName();
        String returnDesc = autoReturn != null ? emptyToNull(autoReturn.description()) : null;
        String returnExample = autoReturn != null ? emptyToNull(autoReturn.example()) : null;

        Object returnExampleValue = ExampleGenerator.exampleValue(method.getReturnType(), returnType);
        if (returnDesc == null) {
            returnDesc = ExampleGenerator.defaultReturnDescription(returnType);
        }
        if (returnExample == null) {
            returnExample = ExampleGenerator.formatResponseExample(returnExampleValue);
        }
        detail.setReturns(new ToolReturnSpec(returnType, returnDesc, returnExample));

        if (detail.getDescription() == null) {
            detail.setDescription(ExampleGenerator.defaultToolDescription(detail.getMethodSignature()));
        }

        // If there are complex parameters, add hint to description
        if (hasComplexParams) {
            String originalDesc = detail.getDescription();
            String hint = "\n\nNote: This tool contains complex parameters (" + complexParamHints + "), use autoai.tool_detail(\"" + toolName + "\") to query detailed structure.";
            detail.setDescription(originalDesc + hint);
        }

        if (detail.getRequestExample() == null) {
            detail.setRequestExample(ExampleGenerator.buildRequestExample(requestExamplePayload));
        }
        if (detail.getResponseExample() == null) {
            detail.setResponseExample(returnExample);
        }

        return new ToolDefinition(toolName, bean, method, detail, bindings);
    }

    /**
     * Build REST API tool definition from annotated Controller method.
     *
     * @param bean         Spring Bean (Controller instance)
     * @param method       Method annotated with @AutoAiTool
     * @param httpMethod   HTTP method (GET/POST/PUT/DELETE, etc.)
     * @param restPath     REST API path
     * @param consumes     Request content type
     * @param produces     Response content type
     * @return Tool definition
     */
    public static ToolDefinition fromMethodWithRestInfo(
            Object bean,
            Method method,
            String httpMethod,
            String restPath,
            String consumes,
            String produces
    ) {
        AutoAiTool tool = method.getAnnotation(AutoAiTool.class);
        if (tool == null) {
            return null;
        }
        String toolName = tool.name();
        if (toolName == null || toolName.isBlank()) {
            // 使用 AopProxyUtils 获取目标类，避免 CGLIB 代理类名问题
            Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
            toolName = targetClass.getSimpleName() + "." + method.getName();
        }

        ToolDetail detail = new ToolDetail();
        detail.setName(toolName);
        detail.setDescription(emptyToNull(tool.description()));
        detail.setRequestExample(emptyToNull(tool.requestExample()));
        detail.setResponseExample(emptyToNull(tool.responseExample()));
        detail.setDeclaringClass(method.getDeclaringClass().getName());
        detail.setMethodSignature(buildSignature(method) + " [" + httpMethod + " " + restPath + "]");

        List<ToolParamSpec> paramSpecs = new ArrayList<>();
        List<ToolParamBinding> bindings = new ArrayList<>();
        Map<String, Object> requestExamplePayload = new LinkedHashMap<>();
        Parameter[] parameters = method.getParameters();

        // Used to record actual parameter index (index after skipping Headers and Cookies)
        int actualParamIndex = 0;

        // Check if there are complex parameters
        boolean hasComplexParams = false;
        StringBuilder complexParamHints = new StringBuilder();

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];

            // Check parameter annotation type
            PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
            RequestBody requestBody = parameter.getAnnotation(RequestBody.class);
            RequestHeader requestHeader = parameter.getAnnotation(RequestHeader.class);
            CookieValue cookieValue = parameter.getAnnotation(CookieValue.class);

            // Skip @RequestHeader and @CookieValue parameters, these are automatically obtained from request context
            if (requestHeader != null || cookieValue != null) {
                // System.out.println("Skip parameter (from request context): " + parameter.getName());
                continue;
            }

            // Only process @PathVariable, @RequestParam, @RequestBody parameters
            if (pathVariable == null && requestParam == null && requestBody == null) {
                // No annotation, might be an error, but skip anyway
                continue;
            }

            // Determine parameter name, priority: @AutoAiParam.name() > @PathVariable/@RequestParam/@RequestBody value/name > parameter.getName()
            AutoAiParam param = parameter.getAnnotation(AutoAiParam.class);
            String paramName = null;

            // 1. Prioritize @AutoAiParam.name()
            if (param != null && !param.name().isBlank()) {
                paramName = param.name();
            }
            // 2. Next use @PathVariable's value or name attribute
            else if (pathVariable != null) {
                String pathVarValue = pathVariable.value();
                if (pathVarValue != null && !pathVarValue.isBlank()) {
                    paramName = pathVarValue;
                } else {
                    String pathVarName = pathVariable.name();
                    if (pathVarName != null && !pathVarName.isBlank()) {
                        paramName = pathVarName;
                    }
                }
            }
            // 3. Next use @RequestParam's value or name attribute
            else if (requestParam != null) {
                String requestParamValue = requestParam.value();
                if (requestParamValue != null && !requestParamValue.isBlank()) {
                    paramName = requestParamValue;
                } else {
                    String requestParamName = requestParam.name();
                    if (requestParamName != null && !requestParamName.isBlank()) {
                        paramName = requestParamName;
                    }
                }
            }
            // 4. Finally use parameter.getName()
            if (paramName == null || paramName.isBlank() || SYNTHETIC_PARAM.matcher(paramName).matches()) {
                paramName = parameter.getName();
                if (paramName == null || paramName.isBlank() || SYNTHETIC_PARAM.matcher(paramName).matches()) {
                    paramName = "param" + (actualParamIndex + 1);
                }
            }
            String paramDesc = param != null ? emptyToNull(param.description()) : null;

            // For @PathVariable, default to required
            // For @RequestParam, check required attribute (default is true)
            boolean required;
            if (pathVariable != null || requestBody != null) {
                required = true;
            } else if (requestParam != null) {
                required = requestParam.required();
            } else {
                required = param == null || param.required();
            }

            String example = param != null ? emptyToNull(param.example()) : null;
            String typeName = parameter.getParameterizedType().getTypeName();

            // Check if it's a complex parameter
            boolean isComplex = ExampleGenerator.isComplexType(parameter.getType(), typeName);

            Object exampleValue;
            Object requestExampleValue;

            if (isComplex && example == null) {
                // Complex parameter, don't generate detailed example, use hint
                hasComplexParams = true;
                if (complexParamHints.length() > 0) {
                    complexParamHints.append("、");
                }
                complexParamHints.append(paramName);

                exampleValue = "[Complex parameter, use autoai.tool_detail(\"" + toolName + "\") to query detailed structure]";
                requestExampleValue = exampleValue;
            } else {
                // Pass tool name to detect internal complex fields when generating object examples
                exampleValue = ExampleGenerator.exampleValue(parameter.getType(), typeName, toolName);
                requestExampleValue = exampleValue;
            }

            if (paramDesc == null) {
                paramDesc = ExampleGenerator.defaultParamDescription(paramName, typeName);
            }
            if (example == null) {
                example = ExampleGenerator.formatParamExample(exampleValue);
            } else {
                requestExampleValue = ExampleGenerator.parseExampleText(example, parameter.getType(), typeName);
            }
            requestExamplePayload.put(paramName, requestExampleValue);

            // Determine parameter source
            ToolParamBinding.ParamSource paramSource;
            if (requestBody != null) {
                paramSource = ToolParamBinding.ParamSource.REQUEST_BODY;
            } else if (pathVariable != null) {
                paramSource = ToolParamBinding.ParamSource.PATH_VARIABLE;
            } else if (requestParam != null) {
                paramSource = ToolParamBinding.ParamSource.REQUEST_PARAM;
            } else {
                paramSource = ToolParamBinding.ParamSource.OTHER;
            }

            ToolParamSpec spec = new ToolParamSpec(paramName, typeName, paramDesc, required, example, paramSource);
            paramSpecs.add(spec);

            // Use actualParamIndex instead of original i to ensure correct parameter index
            bindings.add(new ToolParamBinding(paramName, parameter.getType(), actualParamIndex, required, paramSource));

            actualParamIndex++;
        }
        detail.setParams(paramSpecs);

        AutoAiReturn autoReturn = method.getAnnotation(AutoAiReturn.class);
        String returnType = method.getGenericReturnType().getTypeName();
        String returnDesc = autoReturn != null ? emptyToNull(autoReturn.description()) : null;
        String returnExample = autoReturn != null ? emptyToNull(autoReturn.example()) : null;

        Object returnExampleValue = ExampleGenerator.exampleValue(method.getReturnType(), returnType);
        if (returnDesc == null) {
            returnDesc = ExampleGenerator.defaultReturnDescription(returnType);
        }
        if (returnExample == null) {
            returnExample = ExampleGenerator.formatResponseExample(returnExampleValue);
        }
        detail.setReturns(new ToolReturnSpec(returnType, returnDesc, returnExample));

        if (detail.getDescription() == null) {
            detail.setDescription(ExampleGenerator.defaultToolDescription(detail.getMethodSignature()));
        }

        // If there are complex parameters, add hint to description
        if (hasComplexParams) {
            String originalDesc = detail.getDescription();
            String hint = "\n\nNote: This tool contains complex parameters (" + complexParamHints + "), use autoai.tool_detail(\"" + toolName + "\") to query detailed structure.";
            detail.setDescription(originalDesc + hint);
        }

        if (detail.getRequestExample() == null) {
            detail.setRequestExample(ExampleGenerator.buildRequestExample(requestExamplePayload));
        }
        if (detail.getResponseExample() == null) {
            detail.setResponseExample(returnExample);
        }

        // Create REST API information
        ToolDefinition.RestApiInfo restApiInfo = new ToolDefinition.RestApiInfo(
                httpMethod,
                restPath,
                consumes != null ? consumes : "",
                produces != null ? produces : ""
        );

        return new ToolDefinition(toolName, bean, method, detail, bindings, restApiInfo);
    }

    private static String buildSignature(Method method) {
        StringBuilder builder = new StringBuilder();
        builder.append(method.getDeclaringClass().getSimpleName());
        builder.append('#').append(method.getName()).append('(');
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            builder.append(types[i].getSimpleName());
            if (i < types.length - 1) {
                builder.append(", ");
            }
        }
        builder.append(')');
        return builder.toString();
    }

    private static String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
