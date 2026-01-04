package cn.autoai.core.registry;

import cn.autoai.core.model.ToolDetail;
import cn.autoai.core.model.ToolSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of tool registry.
 */
public class InMemoryToolRegistry implements ToolRegistry {
    private final Map<String, ToolDefinition> definitions = new ConcurrentHashMap<>();

    @Override
    public void register(ToolDefinition definition) {
        definitions.put(definition.getName(), definition);
    }

    @Override
    public List<ToolSummary> listSummaries() {
        List<ToolSummary> summaries = new ArrayList<>();
        for (ToolDefinition definition : definitions.values()) {
            ToolDetail detail = definition.getDetail();
            summaries.add(new ToolSummary(detail.getName(), detail.getDescription()));
        }
        return summaries;
    }

    @Override
    public Optional<ToolDetail> getDetail(String name) {
        ToolDefinition definition = definitions.get(name);
        if (definition == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definition.getDetail());
    }

    @Override
    public Optional<ToolDefinition> getDefinition(String name) {
        return Optional.ofNullable(definitions.get(name));
    }
}
