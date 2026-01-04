package cn.autoai.core.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 国际化服务，用于根据语言配置加载和获取多语言资源
 */
public class I18nService {

    private static final String BUNDLE_NAME = "i18n/messages";
    private static final String DEFAULT_LANGUAGE = "en";

    private final String language;
    private final Map<String, ResourceBundle> bundleCache = new ConcurrentHashMap<>();
    private final Map<String, String> messageCache = new ConcurrentHashMap<>();
    private final ClassLoader classLoader;

    public I18nService(String language) {
        this.language = language != null ? language : DEFAULT_LANGUAGE;
        this.classLoader = I18nService.class.getClassLoader();
    }

    /**
     * 获取指定 key 的消息文本
     *
     * @param key 消息键
     * @return 消息文本，如果未找到则返回 key 本身
     */
    public String get(String key) {
        return get(key, new Object[0]);
    }

    /**
     * 获取指定 key 的消息文本，并替换参数
     *
     * @param key  消息键
     * @param args 参数数组
     * @return 消息文本，如果未找到则返回 key 本身
     */
    public String get(String key, Object... args) {
        if (key == null || key.isEmpty()) {
            return "";
        }

        String cacheKey = language + ":" + key;
        String cachedMessage = messageCache.get(cacheKey);
        if (cachedMessage != null) {
            return formatMessage(cachedMessage, args);
        }

        String message = loadMessage(key);
        messageCache.put(cacheKey, message);

        return formatMessage(message, args);
    }

    /**
     * 获取所有当前语言的消息（用于前端 lan.js 输出）
     *
     * @return 消息键值对映射
     */
    public Map<String, String> getAllMessages() {
        ResourceBundle bundle = getBundle();
        Map<String, String> messages = new ConcurrentHashMap<>();

        bundle.keySet().forEach(key -> {
            messages.put(key, bundle.getString(key));
        });

        return messages;
    }

    /**
     * 获取当前语言设置
     *
     * @return 语言代码，如 "en" 或 "zh_CN"
     */
    public String getLanguage() {
        return language;
    }

    /**
     * 切换语言
     *
     * @param newLanguage 新语言代码
     */
    public void setLanguage(String newLanguage) {
        if (newLanguage != null && !newLanguage.equals(this.language)) {
            // 清理缓存
            messageCache.clear();
        }
    }

    /**
     * 从资源文件加载消息
     *
     * @param key 消息键
     * @return 消息文本
     */
    private String loadMessage(String key) {
        try {
            ResourceBundle bundle = getBundle();
            if (bundle.containsKey(key)) {
                return bundle.getString(key);
            }
        } catch (Exception e) {
            // 忽略错误，返回 key 本身
        }
        return key;
    }

    /**
     * 获取资源包
     *
     * @return ResourceBundle
     */
    private ResourceBundle getBundle() {
        return bundleCache.computeIfAbsent(language, lang -> {
            try {
                // 将语言代码转换为 Locale
                Locale locale = parseLocale(lang);
                return ResourceBundle.getBundle(BUNDLE_NAME, locale, new I18nResourceBundleControl(classLoader));
            } catch (Exception e) {
                // 如果加载失败，使用默认语言
                try {
                    return ResourceBundle.getBundle(BUNDLE_NAME, Locale.ENGLISH, new I18nResourceBundleControl(classLoader));
                } catch (Exception ex) {
                    // 如果默认语言也加载失败，返回空的 ResourceBundle
                    throw new RuntimeException("Failed to load resource bundle for language: " + lang, ex);
                }
            }
        });
    }

    /**
     * 解析语言代码为 Locale
     *
     * @param languageCode 语言代码，如 "en" 或 "zh_CN"
     * @return Locale
     */
    private Locale parseLocale(String languageCode) {
        if (languageCode == null || languageCode.isEmpty()) {
            return Locale.ENGLISH;
        }

        String[] parts = languageCode.split("_");
        if (parts.length == 1) {
            return new Locale(parts[0]);
        } else if (parts.length >= 2) {
            return new Locale(parts[0], parts[1]);
        }

        return Locale.ENGLISH;
    }

    /**
     * 格式化消息，替换参数
     *
     * @param message 消息模板
     * @param args    参数数组
     * @return 格式化后的消息
     */
    private String formatMessage(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }

        try {
            return MessageFormat.format(message, args);
        } catch (IllegalArgumentException e) {
            // 格式化失败，返回原始消息
            return message;
        }
    }

    /**
     * 自定义 ResourceBundle Control，支持从 i18n/ 子目录加载资源文件
     */
    private static class I18nResourceBundleControl extends ResourceBundle.Control {
        private final ClassLoader classLoader;

        public I18nResourceBundleControl(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload) {
            // 自定义加载逻辑：从 i18n/ 目录加载
            String bundleName = baseName.replace('.', '/');
            String resourceName = toResourceName(bundleName, locale, "properties");
            InputStream stream = classLoader.getResourceAsStream(resourceName);

            if (stream == null) {
                throw new RuntimeException("Resource not found: " + resourceName);
            }

            try {
                Properties properties = new Properties();
                // 使用 UTF-8 编码读取 properties 文件
                java.io.InputStreamReader reader = new java.io.InputStreamReader(stream, StandardCharsets.UTF_8);
                properties.load(reader);
                reader.close();
                return new PropertyResourceBundle(properties);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load resource: " + resourceName, e);
            } finally {
                try {
                    stream.close();
                } catch (IOException e) {
                    // 忽略关闭错误
                }
            }
        }

        private String toResourceName(String baseName, Locale locale, String suffix) {
            StringBuilder builder = new StringBuilder();
            builder.append(baseName);

            if (locale != null && !locale.getLanguage().isEmpty()) {
                builder.append('_').append(locale.getLanguage());
                if (!locale.getCountry().isEmpty()) {
                    builder.append('_').append(locale.getCountry());
                }
            }

            builder.append('.').append(suffix);
            return builder.toString();
        }

        /**
         * 简单的 ResourceBundle 实现，基于 Properties
         */
        private static class PropertyResourceBundle extends ResourceBundle {
            private final Properties properties;

            public PropertyResourceBundle(Properties properties) {
                this.properties = properties;
            }

            @Override
            protected Object handleGetObject(String key) {
                return properties.get(key);
            }

            @Override
            public Enumeration<String> getKeys() {
                // 将 Enumeration<?> 转换为 Enumeration<String>
                return new Enumeration<String>() {
                    final Enumeration<?> delegate = properties.propertyNames();

                    @Override
                    public boolean hasMoreElements() {
                        return delegate.hasMoreElements();
                    }

                    @Override
                    public String nextElement() {
                        Object next = delegate.nextElement();
                        return next != null ? next.toString() : null;
                    }
                };
            }
        }
    }
}

