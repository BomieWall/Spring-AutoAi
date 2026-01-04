package cn.autoai.core.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP request context, used to pass Cookie and Header information during tool invocation.
 */
public class RequestContext {

    private final Map<String, String> cookies;
    private final Map<String, String> headers;
    private final String baseUrl;

    public RequestContext(Map<String, String> cookies, Map<String, String> headers, String baseUrl) {
        this.cookies = cookies != null ? Collections.unmodifiableMap(new HashMap<>(cookies)) : Collections.emptyMap();
        this.headers = headers != null ? Collections.unmodifiableMap(new HashMap<>(headers)) : Collections.emptyMap();
        this.baseUrl = baseUrl;
    }

    /**
     * Extract request context information from HttpServletRequest.
     */
    public static RequestContext from(HttpServletRequest request) {
        Map<String, String> cookies = new HashMap<>();
        Cookie[] cookieArray = request.getCookies();
        if (cookieArray != null) {
            for (Cookie cookie : cookieArray) {
                cookies.put(cookie.getName(), cookie.getValue());
            }
        }

        Map<String, String> headers = new HashMap<>();
        Collections.list(request.getHeaderNames()).forEach(name ->
            headers.put(name, request.getHeader(name))
        );

        // Build base URL: scheme://host:port
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String baseUrl = String.format("%s://%s:%d", scheme, serverName, serverPort);

        return new RequestContext(cookies, headers, baseUrl);
    }

    /**
     * Get all cookies.
     */
    public Map<String, String> getCookies() {
        return cookies;
    }

    /**
     * Get the cookie value for the specified name.
     */
    public String getCookie(String name) {
        return cookies.get(name);
    }

    /**
     * Get all headers.
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Get the header value for the specified name.
     */
    public String getHeader(String name) {
        return headers.get(name);
    }

    /**
     * Get the base URL.
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Create an empty request context.
     */
    public static RequestContext empty() {
        return new RequestContext(Collections.emptyMap(), Collections.emptyMap(), null);
    }
}
