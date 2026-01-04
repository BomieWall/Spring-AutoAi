package cn.autoai.core.web;

import cn.autoai.core.i18n.I18nService;
import cn.autoai.core.llm.ModelRegistry;
import cn.autoai.core.react.ChatTaskManager;
import cn.autoai.core.react.FrontendToolManager;
import cn.autoai.core.react.ReActEngine;
import cn.autoai.core.registry.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.lang.NonNull;

/**
 * Spring-AutoAi Web related Spring configuration.
 */
@Configuration
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnProperty(prefix = "autoai.web", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AutoAiWebProperties.class)
public class AutoAiWebConfiguration implements WebMvcConfigurer {
    
    private final AutoAiWebProperties properties;
    
    public AutoAiWebConfiguration(AutoAiWebProperties properties) {
        this.properties = properties;
    }
    
    @Bean
    public AutoAiChatController autoAiChatController(ReActEngine reActEngine, ModelRegistry modelRegistry) {
        return new AutoAiChatController(reActEngine, modelRegistry);
    }

    @Bean
    public AutoAiToolController autoAiToolController(ToolRegistry toolRegistry) {
        return new AutoAiToolController(toolRegistry);
    }
    
    @Bean
    public AutoAiStreamController autoAiStreamController(ReActEngine reActEngine, ObjectMapper objectMapper, ChatTaskManager taskManager, FrontendToolManager frontendToolManager, I18nService i18nService) {
        return new AutoAiStreamController(reActEngine, objectMapper, taskManager, frontendToolManager, i18nService);
    }

    @Bean
    public AutoAiHealthController autoAiHealthController() {
        return new AutoAiHealthController();
    }

    @Bean
    public I18nController i18nController(I18nService i18nService) {
        return new I18nController(i18nService);
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // Add static resource mapping, map /auto-ai/ path to classpath:/static/auto-ai/
        String basePath = properties.getBasePath();
        if (!basePath.startsWith("/")) {
            basePath = "/" + basePath;
        }
        if (!basePath.endsWith("/")) {
            basePath = basePath + "/";
        }

        // Configure static resource locations, use multiple locations to ensure they can be found in shaded jar
        String resourceLocation = "classpath:/static" + basePath;

        registry.addResourceHandler(basePath + "**")
                .addResourceLocations(
                    resourceLocation,
                    "classpath:/static/"  // Add root path as fallback
                )
                .setCachePeriod(3600);  // Cache for 1 hour
    }
}