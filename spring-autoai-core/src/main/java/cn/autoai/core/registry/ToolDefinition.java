package cn.autoai.core.registry;

import cn.autoai.core.model.ToolDetail;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

/**
 * Tool definition, containing target method and metadata.
 */
public class ToolDefinition {
    private final String name;
    private final Object bean;
    private final Method method;
    private final ToolDetail detail;
    private final List<ToolParamBinding> paramBindings;
    private final ToolType toolType;
    private final RestApiInfo restApiInfo;

    /**
     * Create method invocation type tool definition (original method).
     */
    public ToolDefinition(String name, Object bean, Method method, ToolDetail detail, List<ToolParamBinding> paramBindings) {
        this(name, bean, method, detail, paramBindings, ToolType.METHOD_INVOCATION, null);
    }

    /**
     * Create REST API call type tool definition.
     */
    public ToolDefinition(String name, Object bean, Method method, ToolDetail detail, List<ToolParamBinding> paramBindings, RestApiInfo restApiInfo) {
        this(name, bean, method, detail, paramBindings, ToolType.REST_API_CALL, restApiInfo);
    }

    private ToolDefinition(String name, Object bean, Method method, ToolDetail detail, List<ToolParamBinding> paramBindings,
                          ToolType toolType, RestApiInfo restApiInfo) {
        this.name = name;
        this.bean = bean;
        this.method = method;
        this.detail = detail;
        this.paramBindings = paramBindings;
        this.toolType = toolType;
        this.restApiInfo = restApiInfo;
    }

    public String getName() {
        return name;
    }

    public Object getBean() {
        return bean;
    }

    public Method getMethod() {
        return method;
    }

    public ToolDetail getDetail() {
        return detail;
    }

    public List<ToolParamBinding> getParamBindings() {
        return paramBindings;
    }

    public ToolType getToolType() {
        return toolType;
    }

    public RestApiInfo getRestApiInfo() {
        return restApiInfo;
    }

    /**
     * Tool invocation type.
     */
    public enum ToolType {
        /**
         * Method direct invocation (reflection call)
         */
        METHOD_INVOCATION,

        /**
         * REST API call (initiate HTTP request via RestTemplate)
         */
        REST_API_CALL
    }

    /**
     * REST API information.
     */
    public static class RestApiInfo {
        private final String httpMethod;
        private final String restPath;
        private final String consumes;
        private final String produces;

        public RestApiInfo(String httpMethod, String restPath, String consumes, String produces) {
            this.httpMethod = httpMethod;
            this.restPath = restPath;
            this.consumes = consumes;
            this.produces = produces;
        }

        public String getHttpMethod() {
            return httpMethod;
        }

        public String getRestPath() {
            return restPath;
        }

        public String getConsumes() {
            return consumes;
        }

        public String getProduces() {
            return produces;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RestApiInfo that = (RestApiInfo) o;
            return Objects.equals(httpMethod, that.httpMethod) &&
                   Objects.equals(restPath, that.restPath) &&
                   Objects.equals(consumes, that.consumes) &&
                   Objects.equals(produces, that.produces);
        }

        @Override
        public int hashCode() {
            return Objects.hash(httpMethod, restPath, consumes, produces);
        }

        @Override
        public String toString() {
            return "RestApiInfo{" +
                   "httpMethod='" + httpMethod + '\'' +
                   ", restPath='" + restPath + '\'' +
                   ", consumes='" + consumes + '\'' +
                   ", produces='" + produces + '\'' +
                   '}';
        }
    }
}
