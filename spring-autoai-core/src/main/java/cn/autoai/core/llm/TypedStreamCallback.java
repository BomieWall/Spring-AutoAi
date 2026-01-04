package cn.autoai.core.llm;

import cn.autoai.core.react.ContentType;

/**
 * Typed streaming callback interface, supports content type identification
 */
public interface TypedStreamCallback extends StreamCallback {
    
    /**
     * Receive content chunk with type identifier
     *
     * @param contentType Content type
     * @param content Content text
     */
    void onTypedChunk(ContentType contentType, String content);
    
    /**
     * Default implementation: convert typed content to standard format
     */
    @Override
    default void onChunk(String content) {
        // Default handling as regular content
        onTypedChunk(ContentType.CONTENT, content);
    }

    /**
     * Send type marker
     *
     * @param contentType Content type
     */
    default void onTypeMarker(ContentType contentType) {
        onChunk(contentType.getMarker() + "\n");
    }
}