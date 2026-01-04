package cn.autoai.core.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * Model list response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelListResponse {
    private String object = "list";
    private List<ModelInfo> data = new ArrayList<>();

    public ModelListResponse() {
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public List<ModelInfo> getData() {
        return data;
    }

    public void setData(List<ModelInfo> data) {
        this.data = data;
    }
}
