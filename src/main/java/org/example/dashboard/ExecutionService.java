package org.example.dashboard;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ExecutionService implements AutoCloseable {
    public record RunResult(
            String id,
            String caseId,
            String title,
            String status,
            long durationMs,
            String timestamp,
            String failureDetails) {}

    private static final String FAILURE_DETAILS =
            "Simulated UI assertion failed. Review the test steps and expected result.";

    static final Duration DEFAULT_RUN_DURATION = Duration.ofMillis(1400);

    private final Duration runDuration;
    private final ConcurrentMap<String, Run> runs = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "demo-test-runner");
        thread.setDaemon(true);
        return thread;
    });

    public ExecutionService() {
        this(DEFAULT_RUN_DURATION);
    }

    ExecutionService(Duration runDuration) {
        if (runDuration.isNegative()) {
            throw new IllegalArgumentException("Run duration cannot be negative.");
        }
        this.runDuration = runDuration;
    }

    Duration runDuration() {
        return runDuration;
    }

    public RunResult start(String caseId, String title, String outcome) {
        String cleanCaseId = requireText(caseId, "Test case ID", 40);
        String cleanTitle = requireText(title, "Test case name", 120);
        if (!"pass".equals(outcome) && !"fail".equals(outcome)) {
            throw new IllegalArgumentException("Demo outcome must be pass or fail.");
        }

        String id = UUID.randomUUID().toString();
        Run run = new Run(id, cleanCaseId, cleanTitle, outcome, Instant.now(), System.nanoTime());
        runs.put(id, run);
        scheduler.schedule(() -> run.completedAtNanos = System.nanoTime(),
                runDuration.toMillis(), TimeUnit.MILLISECONDS);
        return run.toResult();
    }

    public RunResult find(String id) {
        Run run = runs.get(id);
        return run == null ? null : run.toResult();
    }

    private static String requireText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(label + " must be " + maxLength + " characters or fewer.");
        }
        return trimmed;
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }

    private static final class Run {
        private final String id;
        private final String caseId;
        private final String title;
        private final String outcome;
        private final Instant startedAt;
        private final long startedAtNanos;
        private volatile long completedAtNanos;

        private Run(String id, String caseId, String title, String outcome, Instant startedAt, long startedAtNanos) {
            this.id = id;
            this.caseId = caseId;
            this.title = title;
            this.outcome = outcome;
            this.startedAt = startedAt;
            this.startedAtNanos = startedAtNanos;
        }

        private RunResult toResult() {
            long endNanos = completedAtNanos;
            boolean complete = endNanos != 0;
            long duration = TimeUnit.NANOSECONDS.toMillis((complete ? endNanos : System.nanoTime()) - startedAtNanos);
            String status = complete ? ("pass".equals(outcome) ? "Passed" : "Failed") : "In Progress";
            return new RunResult(
                    id,
                    caseId,
                    title,
                    status,
                    duration,
                    startedAt.toString(),
                    "Failed".equals(status) ? FAILURE_DETAILS : "");
        }
    }
}
