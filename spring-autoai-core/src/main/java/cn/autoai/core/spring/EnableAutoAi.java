package cn.autoai.core.spring;

import cn.autoai.core.web.AutoAiWebConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Spring annotation to enable AutoAi.
 *
 * <p>This annotation imports AutoAi's core configuration, including:
 * <ul>
 *   <li>Tool registry and invoker</li>
 *   <li>Model factory and registry</li>
 *   <li>ReAct engine</li>
 *   <li>Web interface (controlled by autoai.web.enabled configuration in application.yml)</li>
 * </ul>
 *
 * <p>Configuration example (application.yml):
 * <pre>
 * autoai:
 *   web:
 *     enabled: true  # Whether to enable Web interface, default is true
 *     base-path: /auto-ai/  # Base path for Web interface
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({AutoAiSpringConfiguration.class, AutoAiWebConfiguration.class})
public @interface EnableAutoAi {
}