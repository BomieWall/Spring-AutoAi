package cn.autoai.core.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AutoAi Web configuration properties.
 */
@ConfigurationProperties(prefix = "autoai.web")
public class AutoAiWebProperties {

    /**
     * Whether to enable the web service, default is true
     */
    private boolean enabled = true;

    /**
     * Base path for the web service, default is /auto-ai
     */
    private String basePath = "/auto-ai";
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getBasePath() {
        return basePath;
    }
    
    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }
}