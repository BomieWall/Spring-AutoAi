package cn.autoai.core.web;

import cn.autoai.core.i18n.I18nService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.DispatcherServlet;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 国际化控制器，提供虚拟的 lan.js 文件输出
 */
@RestController
@ConditionalOnClass(DispatcherServlet.class)
public class I18nController {

    private final I18nService i18nService;

    public I18nController(I18nService i18nService) {
        this.i18nService = i18nService;
    }

    /**
     * 输出 lan.js 虚拟文件，将多语言字典转换为 JavaScript 对象
     * 前端可以通过 window.LANG 访问所有翻译文本
     */
    @GetMapping("${autoai.web.base-path:/auto-ai}/lan.js")
    public void getLanguageFile(HttpServletResponse response) throws IOException {
        // 设置响应头，告知浏览器这是 JavaScript 文件
        response.setContentType("text/javascript");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        // 获取当前语言的所有消息
        Map<String, String> messages = i18nService.getAllMessages();

        // 构建 JavaScript 输出
        StringBuilder js = new StringBuilder();
        js.append("// AutoAi Language Resource\n");
        js.append("// Language: ").append(i18nService.getLanguage()).append("\n");
        js.append("// Generated automatically from backend\n");
        js.append("\n");
        js.append("window.LANG = {\n");

        // 将所有消息转换为 JavaScript 对象属性
        boolean first = true;
        for (Map.Entry<String, String> entry : messages.entrySet()) {
            if (!first) {
                js.append(",\n");
            }
            first = false;

            String key = entry.getKey();
            String value = escapeJavaScriptString(entry.getValue());

            js.append("  \"").append(key).append("\": \"").append(value).append("\"");
        }

        js.append("\n};\n");

        // 输出到响应
        response.getWriter().write(js.toString());
    }

    /**
     * 转义 JavaScript 字符串中的特殊字符
     *
     * @param str 原始字符串
     * @return 转义后的字符串
     */
    private String escapeJavaScriptString(String str) {
        if (str == null) {
            return "";
        }

        // 转义反斜杠、双引号、换行符等特殊字符
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
