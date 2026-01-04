package cn.autoai.core.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Health check endpoint controller
 */
@RestController
@RequestMapping("${autoai.web.base-path:/auto-ai}/v1")
public class AutoAiHealthController {

    /**
     * Health check endpoint, returns service status
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "online");
        response.put("timestamp", Instant.now().toEpochMilli());
        return response;
    }
}
