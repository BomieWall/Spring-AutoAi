package cn.autoai.core.web;

import cn.autoai.core.model.ToolDetail;
import cn.autoai.core.model.ToolSummary;
import cn.autoai.core.registry.ToolRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Tool list and detail query interface.
 */
@RestController
@RequestMapping("${autoai.web.base-path:/auto-ai}/v1/tools")
public class AutoAiToolController {
    private final ToolRegistry toolRegistry;

    public AutoAiToolController(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * Get tool list (name + brief description).
     */
    @GetMapping
    public List<ToolSummary> list() {
        return toolRegistry.listSummaries();
    }

    /**
     * Get detailed description of the specified tool.
     */
    @GetMapping("/{name}")
    public ResponseEntity<ToolDetail> detail(@PathVariable("name") String name) {
        Optional<ToolDetail> detail = toolRegistry.getDetail(name);
        return detail.map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}