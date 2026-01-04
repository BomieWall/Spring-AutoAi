package cn.autoai.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tool input parameter annotation, used to describe parameter name, description, and example.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoAiParam {
    /**
     * Parameter name, uses compiled parameter name or auto-generated fallback name if not specified.
     */
    String name() default "";

    /**
     * Parameter description, auto-generated if not specified.
     */
    String description() default "";

    /**
     * Whether required, default true.
     */
    boolean required() default true;

    /**
     * Parameter example, auto-generated if not specified.
     */
    String example() default "";
}
