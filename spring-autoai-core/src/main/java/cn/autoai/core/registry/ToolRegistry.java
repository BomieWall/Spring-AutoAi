package cn.autoai.core.registry;

import cn.autoai.core.model.ToolDetail;
import cn.autoai.core.model.ToolSummary;

import java.util.List;
import java.util.Optional;

/**
 * Tool registry interface.
 */
public interface ToolRegistry {
    void register(ToolDefinition definition);

    List<ToolSummary> listSummaries();

    Optional<ToolDetail> getDetail(String name);

    Optional<ToolDefinition> getDefinition(String name);
}
