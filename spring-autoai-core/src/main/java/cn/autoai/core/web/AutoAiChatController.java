package cn.autoai.core.web;

import cn.autoai.core.llm.AutoAiModel;
import cn.autoai.core.llm.ModelRegistry;
import cn.autoai.core.protocol.ChatCompletionRequest;
import cn.autoai.core.protocol.ChatCompletionResponse;
import cn.autoai.core.protocol.ModelInfo;
import cn.autoai.core.protocol.ModelListResponse;
import cn.autoai.core.react.ReActEngine;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * OpenAI compatible conversation interface controller.
 */
@RestController
@RequestMapping("${autoai.web.base-path:/auto-ai}/v1")
public class AutoAiChatController {
    private final ReActEngine reActEngine;
    private final ModelRegistry modelRegistry;

    public AutoAiChatController(ReActEngine reActEngine, ModelRegistry modelRegistry) {
        this.reActEngine = reActEngine;
        this.modelRegistry = modelRegistry;
    }

    /**
     * OpenAI compatible conversation entry point.
     * Extract request context (Cookie and Header) and pass to ReActEngine, used for authentication during REST API tool calls.
     */
    @PostMapping("/chat/completions")
    public ChatCompletionResponse chat(
            @RequestBody ChatCompletionRequest request,
            HttpServletRequest httpRequest
    ) {
        // Extract request context
        RequestContext context = RequestContext.from(httpRequest);
        return reActEngine.chat(request, null, context);
    }

    /**
     * Get current available model list.
     */
    @GetMapping("/models")
    public ModelListResponse models() {
        ModelListResponse response = new ModelListResponse();
        long created = Instant.now().getEpochSecond();
        for (AutoAiModel model : modelRegistry.list()) {
            response.getData().add(new ModelInfo(model.getName(), created));
        }
        return response;
    }
}