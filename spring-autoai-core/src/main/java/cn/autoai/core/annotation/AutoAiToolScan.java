package cn.autoai.core.annotation;

import cn.autoai.core.spring.AutoAiToolScanConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Spring-Spring-AutoAi tool scanning configuration annotation
 * <p>
 * Used to specify package paths to scan, avoiding performance issues from scanning all Beans in large-scale projects.
 * <p>
 * Usage:
 * <pre>
 * // Use on startup class (recommended)
 * &#64;SpringBootApplication
 * &#64;AutoAiToolScan(classes = {UserManagementController.class})
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 *
 * // Or specify package paths
 * &#64;SpringBootApplication
 * &#64;AutoAiToolScan({"com.example.myapp.tools", "com.example.myapp.services"})
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 *
 * // When this annotation is not used, all Beans will be scanned (default behavior)
 * &#64;SpringBootApplication
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 * </pre>
 * <p>
 * <b>Note:</b> Framework built-in tools (cn.autoai.core.builtin package) will always be scanned, not restricted by this annotation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(AutoAiToolScanConfiguration.class)
public @interface AutoAiToolScan {

    /**
     * Specify package paths to scan.
     * <p>
     * Only scan methods with {@link AutoAiTool} annotation in these packages.
     * If not specified, all Spring Beans will be scanned (default behavior).
     * <p>
     * Supports wildcards:
     * <ul>
     *   <li>"com.example.tools" - scan this package and its sub-packages</li>
     *   <li>"com.example.**" - scan com.example and all its sub-packages (same as above)</li>
     *   <li>"com.example.*" - scan only com.example package, excluding sub-packages</li>
     * </ul>
     *
     * @return Array of package paths to scan
     */
    String[] value() default {};

    /**
     * Specify classes to scan.
     * <p>
     * Only scan tool methods in these specified classes.
     * Can be used in combination with {@link #value()}.
     *
     * @return Array of classes to scan
     */
    Class<?>[] classes() default {};
}
