package cn.autoai.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tool marker annotation, used to declare methods that can be called by AutoAi.
 * Supports marking methods on regular Beans or Controllers.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoAiTool {
    /**
     * Tool name, auto-generated as ClassName.methodName if not specified.
     */
    String name() default "";

    /**
     * Tool description, auto-generated if not specified.
     */
    String description() default "";

    /**
     * Request example (JSON string), auto-generated if not specified.
     */
    String requestExample() default "";

    /**
     * Response example (JSON string), auto-generated if not specified.
     */
    String responseExample() default "";

    /**
     * REST API path (optional).
     * When annotated on Controller method, if not specified, it's automatically extracted from @RequestMapping and other annotations.
     * Can be specified as "/xxx/xxx" format path.
     */
    String restPath() default "";
}
