package cn.autoai.core.react;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat task manager, used for tracking and terminating ongoing AI inference tasks.
 */
public class ChatTaskManager {

    /**
     * Task information wrapper class
     */
    public static class ChatTask {
        private final String taskId;
        private final String sessionId;
        private volatile boolean aborted;
        private volatile boolean connectionClosed;
        private final long createdAt;

        public ChatTask(String taskId, String sessionId) {
            this.taskId = taskId;
            this.sessionId = sessionId;
            this.aborted = false;
            this.connectionClosed = false;
            this.createdAt = System.currentTimeMillis();
        }

        public String getTaskId() {
            return taskId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public boolean isAborted() {
            return aborted;
        }

        public void abort() {
            this.aborted = true;
        }

        public boolean isConnectionClosed() {
            return connectionClosed;
        }

        public void markConnectionClosed() {
            this.connectionClosed = true;
            // Automatically abort task when connection is closed
            this.aborted = true;
        }

        public long getCreatedAt() {
            return createdAt;
        }
    }

    // Store active tasks
    private final Map<String, ChatTask> activeTasks = new ConcurrentHashMap<>();

    /**
     * Create a new chat task
     */
    public ChatTask createTask(String sessionId) {
        String taskId = UUID.randomUUID().toString();
        ChatTask task = new ChatTask(taskId, sessionId);
        activeTasks.put(taskId, task);
        return task;
    }

    /**
     * Abort task
     */
    public boolean abortTask(String taskId) {
        ChatTask task = activeTasks.get(taskId);
        if (task != null) {
            task.abort();
            return true;
        }
        return false;
    }

    /**
     * Get task
     */
    public ChatTask getTask(String taskId) {
        return activeTasks.get(taskId);
    }

    /**
     * Remove completed task
     */
    public void removeTask(String taskId) {
        activeTasks.remove(taskId);
    }

    /**
     * Clean up expired tasks (over 30 minutes)
     */
    public void cleanupExpiredTasks() {
        long now = System.currentTimeMillis();
        long expireTime = 30 * 60 * 1000; // 30 minutes

        activeTasks.entrySet().removeIf(entry -> {
            ChatTask task = entry.getValue();
            return (now - task.getCreatedAt()) > expireTime;
        });
    }
}
