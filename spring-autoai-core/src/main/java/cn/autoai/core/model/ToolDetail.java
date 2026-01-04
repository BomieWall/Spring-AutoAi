package cn.autoai.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool detail structure, includes parameters, return values and examples.
 */
public class ToolDetail {
    private String name;
    private String description;
    private List<ToolParamSpec> params = new ArrayList<>();
    private ToolReturnSpec returns;
    private String requestExample;
    private String responseExample;
    private String methodSignature;
    private String declaringClass;

    public ToolDetail() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<ToolParamSpec> getParams() {
        return params;
    }

    public void setParams(List<ToolParamSpec> params) {
        this.params = params;
    }

    public ToolReturnSpec getReturns() {
        return returns;
    }

    public void setReturns(ToolReturnSpec returns) {
        this.returns = returns;
    }

    public String getRequestExample() {
        return requestExample;
    }

    public void setRequestExample(String requestExample) {
        this.requestExample = requestExample;
    }

    public String getResponseExample() {
        return responseExample;
    }

    public void setResponseExample(String responseExample) {
        this.responseExample = responseExample;
    }

    public String getMethodSignature() {
        return methodSignature;
    }

    public void setMethodSignature(String methodSignature) {
        this.methodSignature = methodSignature;
    }

    public String getDeclaringClass() {
        return declaringClass;
    }

    public void setDeclaringClass(String declaringClass) {
        this.declaringClass = declaringClass;
    }
}
