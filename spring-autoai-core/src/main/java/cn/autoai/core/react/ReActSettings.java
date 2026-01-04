package cn.autoai.core.react;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ReAct engine configuration.
 */
@ConfigurationProperties(prefix = "autoai.react")
public class ReActSettings {
    private String systemPrompt = "";
    private int maxSteps = 30;
    private String defaultModel;
    private boolean showToolDetails = true; // Whether to output tool details, default is true

    // Conversation history compression configuration
    private boolean enableCompression = true; // Whether to enable conversation history compression
    private int compressionThresholdTokens = 64000; // Token count threshold to trigger compression (Chinese ~1.5 chars=1token, English ~4 chars=1token)
    private int keepRecentTokens = 12000; // Recent context token count to keep during compression
    private int maxTokensAfterCompression = 16000; // Maximum token count after compression (safety boundary)

    // Session expiration configuration
    private boolean enableSessionExpiration = true; // Whether to enable session expiration cleanup
    private long sessionExpireMinutes = 60; // Session expiration time (minutes), default 60 minutes
    private long sessionCleanupIntervalMinutes = 10; // Session cleanup interval (minutes), default 10 minutes

    // Language configuration
    private String language = "en"; // Language setting, default is English (en). Supports: en, zh_CN, etc.

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public boolean isShowToolDetails() {
        return showToolDetails;
    }

    public void setShowToolDetails(boolean showToolDetails) {
        this.showToolDetails = showToolDetails;
    }

    public boolean isEnableCompression() {
        return enableCompression;
    }

    public void setEnableCompression(boolean enableCompression) {
        this.enableCompression = enableCompression;
    }

    public int getCompressionThresholdTokens() {
        return compressionThresholdTokens;
    }

    public void setCompressionThresholdTokens(int compressionThresholdTokens) {
        this.compressionThresholdTokens = compressionThresholdTokens;
    }

    public int getKeepRecentTokens() {
        return keepRecentTokens;
    }

    public void setKeepRecentTokens(int keepRecentTokens) {
        this.keepRecentTokens = keepRecentTokens;
    }

    public int getMaxTokensAfterCompression() {
        return maxTokensAfterCompression;
    }

    public void setMaxTokensAfterCompression(int maxTokensAfterCompression) {
        this.maxTokensAfterCompression = maxTokensAfterCompression;
    }

    public boolean isEnableSessionExpiration() {
        return enableSessionExpiration;
    }

    public void setEnableSessionExpiration(boolean enableSessionExpiration) {
        this.enableSessionExpiration = enableSessionExpiration;
    }

    public long getSessionExpireMinutes() {
        return sessionExpireMinutes;
    }

    public void setSessionExpireMinutes(long sessionExpireMinutes) {
        this.sessionExpireMinutes = sessionExpireMinutes;
    }

    public long getSessionCleanupIntervalMinutes() {
        return sessionCleanupIntervalMinutes;
    }

    public void setSessionCleanupIntervalMinutes(long sessionCleanupIntervalMinutes) {
        this.sessionCleanupIntervalMinutes = sessionCleanupIntervalMinutes;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
