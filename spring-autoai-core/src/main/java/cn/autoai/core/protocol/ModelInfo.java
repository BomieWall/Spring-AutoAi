package cn.autoai.core.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Model info entry.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelInfo {
    private String id;
    private String object = "model";
    private Long created;
    private String ownedBy = "autoai";

    public ModelInfo() {
    }

    public ModelInfo(String id, Long created) {
        this.id = id;
        this.created = created;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public Long getCreated() {
        return created;
    }

    public void setCreated(Long created) {
        this.created = created;
    }

    public String getOwnedBy() {
        return ownedBy;
    }

    public void setOwnedBy(String ownedBy) {
        this.ownedBy = ownedBy;
    }
}
