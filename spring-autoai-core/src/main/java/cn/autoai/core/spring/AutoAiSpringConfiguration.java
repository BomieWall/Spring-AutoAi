package cn.autoai.core.spring;

import cn.autoai.core.i18n.I18nService;
import cn.autoai.core.llm.AutoAiModel;
import cn.autoai.core.llm.ModelProperties;
import cn.autoai.core.llm.ModelFactory;
import cn.autoai.core.llm.ModelRegistry;
import cn.autoai.core.react.ChatTaskManager;
import cn.autoai.core.react.FrontendToolManager;
import cn.autoai.core.react.ReActEngine;
import cn.autoai.core.react.ReActSettings;
import cn.autoai.core.registry.InMemoryToolRegistry;
import cn.autoai.core.registry.ToolInvoker;
import cn.autoai.core.registry.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Spring-AutoAi Spring basic configuration.
 */
@Configuration
@ConditionalOnClass(ApplicationContext.class)
@EnableConfigurationProperties({ReActSettings.class, ModelProperties.class})
@ComponentScan(basePackages = {"cn.autoai.core.builtin", "cn.autoai.core.llm"})
public class AutoAiSpringConfiguration {
    @Bean
    public ToolRegistry toolRegistry() {
        return new InMemoryToolRegistry();
    }

    /**
     * RestTemplate Bean, used for REST API tool calls.
     */
    @Bean
    @ConditionalOnMissingBean(RestTemplate.class)
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public ToolInvoker toolInvoker(ObjectProvider<ObjectMapper> objectMapperProvider, RestTemplate restTemplate) {
        ObjectMapper mapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new ToolInvoker(mapper, restTemplate);
    }

    @Bean
    public ModelFactory modelFactory(ObjectProvider<ObjectMapper> objectMapperProvider, List<cn.autoai.core.llm.ModelAdapter> adapters) {
        ObjectMapper mapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new ModelFactory(mapper, adapters);
    }

    @Bean
    public ModelRegistry modelRegistry(List<AutoAiModel> models) {
        ModelRegistry registry = new ModelRegistry();
        for (AutoAiModel model : models) {
            registry.register(model);
        }
        return registry;
    }

    /**
     * Automatically create model instance based on configuration.
     * If configuration is valid, create the model; otherwise do not create this Bean.
     */
    @Bean
    @ConditionalOnProperty(prefix = "autoai.model", name = "adapter")
    public AutoAiModel autoConfiguredModel(ModelProperties modelProperties, ModelFactory modelFactory) {
        if (modelProperties.isValid()) {
            return modelFactory.createModel(modelProperties);
        }
        return null;
    }

    /**
     * 国际化服务 Bean
     */
    @Bean
    public I18nService i18nService(ReActSettings settings) {
        return new I18nService(settings.getLanguage());
    }

    @Bean
    public ReActEngine reActEngine(ToolRegistry toolRegistry, ToolInvoker toolInvoker, ModelRegistry modelRegistry,
                                   ObjectProvider<ObjectMapper> objectMapperProvider, ReActSettings settings,
                                   FrontendToolManager frontendToolManager, I18nService i18nService) {
        ObjectMapper mapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new ReActEngine(toolRegistry, toolInvoker, modelRegistry, mapper, settings, frontendToolManager, i18nService);
    }

    /**
     * Frontend tool invoker manager Bean.
     */
    @Bean
    public FrontendToolManager frontendToolManager(ObjectProvider<ObjectMapper> objectMapperProvider, I18nService i18nService) {
        ObjectMapper mapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new FrontendToolManager(mapper, i18nService);
    }

    /**
     * Chat task manager Bean, used to track and abort ongoing AI reasoning tasks.
     */
    @Bean
    public ChatTaskManager chatTaskManager() {
        return new ChatTaskManager();
    }

    @Bean
    public AutoAiToolScanner autoAiToolScanner(ApplicationContext applicationContext, ToolRegistry toolRegistry) {
        return new AutoAiToolScanner(applicationContext, toolRegistry);
    }
}