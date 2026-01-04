package cn.autoai.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark entity class field description information, used to provide clearer parameter descriptions in tool details.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoAiField {
    /**
     * Field description
     */
    String description() default "";

    /**
     * Field example value
     */
    String example() default "";

    /**
     * Whether required
     */
    boolean required() default false;
}