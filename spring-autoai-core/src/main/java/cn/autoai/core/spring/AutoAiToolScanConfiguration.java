package cn.autoai.core.spring;

import cn.autoai.core.annotation.AutoAiToolScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Configuration class for @AutoAiToolScan annotation
 * <p>
 * Loaded through @Import mechanism, extracts annotation parameters during configuration phase
 * Avoids runtime lookup, improves performance and reliability
 */
@Configuration
public class AutoAiToolScanConfiguration implements ImportAware {

    private AnnotationAttributes annotationAttributes;

    /**
     * Spring calls this method, passing in the annotation metadata that imports this configuration class
     */
    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        this.annotationAttributes = AnnotationAttributes.fromMap(
                importMetadata.getAnnotationAttributes(AutoAiToolScan.class.getName(), false));

        if (this.annotationAttributes == null) {
            throw new IllegalArgumentException(
                    "@AutoAiToolScan is not present on importing class " + importMetadata.getClassName());
        }

        // Register configuration to static storage for AutoAiToolScanner to use
        ScanConfigHolder.register(
                this.annotationAttributes.getStringArray("value"),
                this.annotationAttributes.getClassArray("classes")
        );
    }

    /**
     * Scan configuration holder (static storage)
     * Used to pass configuration between configuration class and scanner
     */
    public static class ScanConfigHolder {
        private static String[] packages = new String[0];
        private static Class<?>[] classes = new Class<?>[0];
        private static volatile boolean configured = false;

        /**
         * Register scan configuration
         * Can only register once, subsequent calls will be ignored
         */
        public static synchronized void register(String[] pkgs, Class<?>[] cls) {
            if (!configured) {
                packages = pkgs != null ? pkgs : new String[0];
                classes = cls != null ? cls : new Class<?>[0];
                configured = true;
            }
        }

        /**
         * Get configured package paths
         */
        public static String[] getPackages() {
            return packages;
        }

        /**
         * Get configured classes
         */
        public static Class<?>[] getClasses() {
            return classes;
        }

        /**
         * Whether configuration is complete
         */
        public static boolean isConfigured() {
            return configured;
        }

        /**
         * Reset configuration (mainly used for testing)
         */
        public static synchronized void reset() {
            packages = new String[0];
            classes = new Class<?>[0];
            configured = false;
        }
    }
}
