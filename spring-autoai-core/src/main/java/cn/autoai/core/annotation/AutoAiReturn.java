package cn.autoai.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tool return value annotation, used to supplement return description and example.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoAiReturn {
    /**
     * Return description, auto-generated if not specified.
     */
    String description() default "";

    /**
     * Return example, auto-generated if not specified.
     */
    String example() default "";
}
