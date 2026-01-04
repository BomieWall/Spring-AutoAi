package cn.autoai.core.builtin;

import cn.autoai.core.annotation.AutoAiField;
import cn.autoai.core.annotation.AutoAiParam;
import cn.autoai.core.annotation.AutoAiTool;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * System diagnostics toolkit
 * Provides built-in tools for thread stack analysis and system status monitoring,
 * helping AI analyze and diagnose application runtime status
 */
@Component
public class SystemDiagnosticsTool {

    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    /**
     * Get stack information of all threads
     * Used by AI to analyze the current runtime status of the application,
     * including thread deadlock, thread waiting, etc.
     */
    @AutoAiTool(
        description = "Get detailed stack information of all threads in the application,used to diagnose thread status, performance bottlenecks and deadlock issues"
    )
    public ThreadDumpResult getThreadDump(
        @AutoAiParam(
            description = "Whether to include waiting threads (WAITING, TIMED_WAITING states)",
            required = false,
            example = "true"
        ) Boolean includeWaiting,

        @AutoAiParam(
            description = "Maximum number of stack frames to display per thread",
            required = false,
            example = "50"
        ) Integer maxDepth,

        @AutoAiParam(
            description = "Whether to only show problematic threads (BLOCKED, deadlock, etc.)",
            required = false,
            example = "false"
        ) Boolean onlyProblematic
    ) {
        boolean includeWait = includeWaiting != null && includeWaiting;
        int depth = maxDepth != null && maxDepth > 0 ? maxDepth : 50;
        boolean onlyProblems = onlyProblematic != null && onlyProblematic;

        ThreadDumpResult result = new ThreadDumpResult();
        result.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        result.totalThreadCount = threadMXBean.getThreadCount();
        result.peakThreadCount = threadMXBean.getPeakThreadCount();
        result.daemonThreadCount = threadMXBean.getDaemonThreadCount();
        result.totalStartedThreadCount = threadMXBean.getTotalStartedThreadCount();

        // Get all thread information
        ThreadInfo[] allThreads = threadMXBean.dumpAllThreads(threadMXBean.isObjectMonitorUsageSupported(),
                                                               threadMXBean.isSynchronizerUsageSupported());

        // Detect deadlock
        long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();
        if (deadlockedThreads != null && deadlockedThreads.length > 0) {
            result.hasDeadlock = true;
            result.deadlockedThreadIds = Arrays.stream(deadlockedThreads)
                .boxed()
                .collect(Collectors.toList());
        } else {
            result.hasDeadlock = false;
        }

        // Collect thread information
        List<ThreadDetail> threads = new ArrayList<>();
        Set<Long> deadlockedSet = result.deadlockedThreadIds != null ?
            new HashSet<>(result.deadlockedThreadIds) : Collections.emptySet();

        for (ThreadInfo info : allThreads) {
            if (info == null) continue;

            Thread.State state = info.getThreadState();
            long threadId = info.getThreadId();

            // Filter conditions
            if (!includeWait && (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING)) {
                continue;
            }

            if (onlyProblems) {
                boolean isProblematic = deadlockedSet.contains(threadId) ||
                                      state == Thread.State.BLOCKED ||
                                      info.getLockName() != null;
                if (!isProblematic) {
                    continue;
                }
            }

            ThreadDetail detail = new ThreadDetail();
            detail.threadId = threadId;
            detail.threadName = info.getThreadName();
            detail.threadState = state.toString();
            detail.isDaemon = info.isDaemon();
            detail.priority = info.getPriority();

            // Lock information
            detail.lockName = info.getLockName();
            detail.lockOwnerName = info.getLockOwnerName();
            detail.lockOwnerId = info.getLockOwnerId();

            // Stack trace
            StackTraceElement[] stackTrace = info.getStackTrace();
            List<String> stackFrames = new ArrayList<>();
            int frameCount = 0;
            for (StackTraceElement frame : stackTrace) {
                if (frameCount >= depth) break;
                stackFrames.add("    at " + frame.toString());
                frameCount++;
            }
            detail.stackTrace = stackFrames;

            // Thread status determination
            if (deadlockedSet.contains(threadId)) {
                detail.status = "DEADLOCKED";
            } else if (state == Thread.State.BLOCKED) {
                detail.status = "BLOCKED";
            } else if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                detail.status = "WAITING";
            } else if (state == Thread.State.RUNNABLE) {
                detail.status = "RUNNING";
            } else {
                detail.status = "OTHER";
            }

            threads.add(detail);
        }

        result.threads = threads;

        // Statistical information
        Map<String, Long> stateCount = threads.stream()
            .collect(Collectors.groupingBy(t -> t.threadState, Collectors.counting()));
        result.threadStateDistribution = stateCount;

        // Find threads with the longest wait time
        List<ThreadDetail> blockedThreads = threads.stream()
            .filter(t -> "BLOCKED".equals(t.status))
            .collect(Collectors.toList());
        result.blockedThreadCount = blockedThreads.size();

        return result;
    }

    /**
     * Get thread status summary
     * Quickly view the thread overview of the application
     */
    @AutoAiTool(description = "Get thread status summary, including the number of threads in each state,deadlock detection and other key information")
    public ThreadSummary getThreadSummary() {
        ThreadSummary summary = new ThreadSummary();
        summary.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        int totalCount = threadMXBean.getThreadCount();
        int daemonCount = threadMXBean.getDaemonThreadCount();
        int peakCount = threadMXBean.getPeakThreadCount();
        long totalStarted = threadMXBean.getTotalStartedThreadCount();

        summary.totalThreads = totalCount;
        summary.daemonThreads = daemonCount;
        summary.userThreads = totalCount - daemonCount;
        summary.peakThreads = peakCount;
        summary.totalStartedThreads = totalStarted;

        // Detect deadlock
        long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();
        summary.hasDeadlock = deadlockedThreads != null && deadlockedThreads.length > 0;
        summary.deadlockedThreadCount = summary.hasDeadlock ? deadlockedThreads.length : 0;

        // Get all thread information for statistics
        ThreadInfo[] allThreads = threadMXBean.dumpAllThreads(false, false);

        Map<String, Integer> stateCounts = new HashMap<>();
        stateCounts.put("NEW", 0);
        stateCounts.put("RUNNABLE", 0);
        stateCounts.put("BLOCKED", 0);
        stateCounts.put("WAITING", 0);
        stateCounts.put("TIMED_WAITING", 0);
        stateCounts.put("TERMINATED", 0);

        List<ThreadDetail> topCpuThreads = new ArrayList<>();

        for (ThreadInfo info : allThreads) {
            if (info == null) continue;

            String state = info.getThreadState().toString();
            stateCounts.put(state, stateCounts.getOrDefault(state, 0) + 1);

            // Collect threads in RUNNABLE state (may be CPU-intensive)
            if (info.getThreadState() == Thread.State.RUNNABLE && topCpuThreads.size() < 10) {
                ThreadDetail detail = new ThreadDetail();
                detail.threadId = info.getThreadId();
                detail.threadName = info.getThreadName();
                detail.threadState = state;
                detail.priority = info.getPriority();
                topCpuThreads.add(detail);
            }
        }

        summary.threadStateCounts = stateCounts;
        summary.topRunnableThreads = topCpuThreads;

        return summary;
    }

    /**
     * Analyze potential performance issues
     */
    @AutoAiTool(description = "Analyze current performance issues of the application, including thread blocking, deadlock risks, etc.")
    public PerformanceAnalysisResult analyzePerformance() {
        PerformanceAnalysisResult result = new PerformanceAnalysisResult();
        result.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        // Get thread information
        ThreadInfo[] allThreads = threadMXBean.dumpAllThreads(false, false);

        // Count threads in various states
        int blockedCount = 0;
        int waitingCount = 0;
        Map<String, Integer> lockWaitCount = new HashMap<>();

        for (ThreadInfo info : allThreads) {
            if (info == null) continue;

            Thread.State state = info.getThreadState();

            if (state == Thread.State.BLOCKED) {
                blockedCount++;
                String lockName = info.getLockName();
                if (lockName != null) {
                    lockWaitCount.put(lockName, lockWaitCount.getOrDefault(lockName, 0) + 1);
                }
            } else if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                waitingCount++;
            }
        }

        result.blockedThreadCount = blockedCount;
        result.waitingThreadCount = waitingCount;

        // Detect deadlock
        long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();
        if (deadlockedThreads != null && deadlockedThreads.length > 0) {
            issues.add("CRITICAL: Detected " + deadlockedThreads.length + " threads in deadlock!");
            recommendations.add("Immediately check the stack information of deadlocked threads, identify the cause and fix");
        }

        // Analyze blocked threads
        if (blockedCount > 10) {
            issues.add("WARNING: There are " + blockedCount + " threads in BLOCKED state, possible lock contention");
            recommendations.add("Check lock granularity, consider using concurrent collections or read-write locks");
        } else if (blockedCount > 5) {
            warnings.add("There are " + blockedCount + " threads in BLOCKED state");
        }

        // Analyze waiting threads
        if (waitingCount > result.totalThreads * 0.5) {
            warnings.add("More than 50% of threads are in waiting state, possible I/O bottleneck or resource contention");
        }

        // Analyze hotspot locks
        if (!lockWaitCount.isEmpty()) {
            Map.Entry<String, Integer> hottestLock = lockWaitCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

            if (hottestLock != null && hottestLock.getValue() > 3) {
                warnings.add("Detected hotspot lock: " + hottestLock.getKey() +
                            " has " + hottestLock.getValue() + " threads waiting");
                recommendations.add("Consider optimizing the lock strategy for " + hottestLock.getKey());
            }
        }

        result.issues = issues;
        result.warnings = warnings;
        result.recommendations = recommendations;
        result.hasIssues = !issues.isEmpty();
        result.hasWarnings = !warnings.isEmpty();

        return result;
    }

    // ========== Result Class Definitions ==========

    /** Thread detail information */
    public static class ThreadDetail {
        @AutoAiField(description = "Thread ID")
        public long threadId;

        @AutoAiField(description = "Thread name")
        public String threadName;

        @AutoAiField(description = "Thread state: NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED")
        public String threadState;

        @AutoAiField(description = "Whether it is a daemon thread")
        public boolean isDaemon;

        @AutoAiField(description = "Thread priority")
        public int priority;

        @AutoAiField(description = "Thread status flag: RUNNING, BLOCKED, WAITING, DEADLOCKED, OTHER")
        public String status;

        @AutoAiField(description = "Name of the lock being waited on")
        public String lockName;

        @AutoAiField(description = "Name of the thread holding the lock")
        public String lockOwnerName;

        @AutoAiField(description = "ID of the thread holding the lock")
        public long lockOwnerId;

        @AutoAiField(description = "Stack trace information")
        public List<String> stackTrace;
    }

    /** Thread stack dump result */
    public static class ThreadDumpResult {
        @AutoAiField(description = "Dump time")
        public String timestamp;

        @AutoAiField(description = "Total number of threads")
        public int totalThreadCount;

        @AutoAiField(description = "Peak number of threads")
        public int peakThreadCount;

        @AutoAiField(description = "Number of daemon threads")
        public int daemonThreadCount;

        @AutoAiField(description = "Total number of threads started")
        public long totalStartedThreadCount;

        @AutoAiField(description = "Whether there is a deadlock")
        public boolean hasDeadlock;

        @AutoAiField(description = "List of deadlocked thread IDs")
        public List<Long> deadlockedThreadIds;

        @AutoAiField(description = "List of thread details")
        public List<ThreadDetail> threads;

        @AutoAiField(description = "Thread state distribution")
        public Map<String, Long> threadStateDistribution;

        @AutoAiField(description = "Number of blocked threads")
        public int blockedThreadCount;
    }

    /** Thread summary information */
    public static class ThreadSummary {
        @AutoAiField(description = "Summary time")
        public String timestamp;

        @AutoAiField(description = "Total number of threads")
        public int totalThreads;

        @AutoAiField(description = "Number of daemon threads")
        public int daemonThreads;

        @AutoAiField(description = "Number of user threads")
        public int userThreads;

        @AutoAiField(description = "Peak number of threads")
        public int peakThreads;

        @AutoAiField(description = "Total number of threads started")
        public long totalStartedThreads;

        @AutoAiField(description = "Whether there is a deadlock")
        public boolean hasDeadlock;

        @AutoAiField(description = "Number of deadlocked threads")
        public int deadlockedThreadCount;

        @AutoAiField(description = "Number of threads in each state")
        public Map<String, Integer> threadStateCounts;

        @AutoAiField(description = "TOP 10 threads in RUNNABLE state")
        public List<ThreadDetail> topRunnableThreads;
    }

    /** Performance analysis result */
    public static class PerformanceAnalysisResult {
        @AutoAiField(description = "Analysis time")
        public String timestamp;

        @AutoAiField(description = "Number of blocked threads")
        public int blockedThreadCount;

        @AutoAiField(description = "Number of waiting threads")
        public int waitingThreadCount;

        @AutoAiField(description = "Total number of threads")
        public int totalThreads = ManagementFactory.getThreadMXBean().getThreadCount();

        @AutoAiField(description = "List of critical issues")
        public List<String> issues;

        @AutoAiField(description = "List of warnings")
        public List<String> warnings;

        @AutoAiField(description = "List of optimization recommendations")
        public List<String> recommendations;

        @AutoAiField(description = "Whether there are issues")
        public boolean hasIssues;

        @AutoAiField(description = "Whether there are warnings")
        public boolean hasWarnings;
    }
}
