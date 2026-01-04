package cn.autoai.core.llm;

/**
 * Streaming callback, used to receive incremental content from model output.
 */
@FunctionalInterface
public interface StreamCallback {
    void onChunk(String content);
}
