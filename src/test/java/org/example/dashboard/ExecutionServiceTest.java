package org.example.dashboard;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionServiceTest {
    @Test
    void runTransitionsFromInProgressToPassed() throws Exception {
        try (ExecutionService service = new ExecutionService(Duration.ofMillis(70))) {
            ExecutionService.RunResult started = service.start("TC-1", "Sign in", "pass");

            assertEquals("In Progress", started.status());
            assertEquals("", started.failureDetails());

            TimeUnit.MILLISECONDS.sleep(130);
            ExecutionService.RunResult completed = service.find(started.id());

            assertNotNull(completed);
            assertEquals("Passed", completed.status());
            assertTrue(completed.durationMs() >= 0);
            assertNotNull(completed.timestamp());
            assertEquals("", completed.failureDetails());
        }
    }

    @Test
    void failedRunIncludesActionableFailureDetails() throws Exception {
        try (ExecutionService service = new ExecutionService(Duration.ZERO)) {
            ExecutionService.RunResult started = service.start("TC-2", "Checkout", "fail");
            TimeUnit.MILLISECONDS.sleep(30);

            ExecutionService.RunResult completed = service.find(started.id());

            assertEquals("Failed", completed.status());
            assertFalse(completed.failureDetails().isBlank());
        }
    }

    @Test
    void rejectsMissingFieldsUnsupportedOutcomesAndOversizedNames() {
        try (ExecutionService service = new ExecutionService(Duration.ZERO)) {
            assertThrows(IllegalArgumentException.class, () -> service.start("", "Sign in", "pass"));
            assertThrows(IllegalArgumentException.class, () -> service.start("TC-1", "Sign in", "maybe"));
            assertThrows(IllegalArgumentException.class,
                    () -> service.start("TC-1", "x".repeat(121), "pass"));
        }
    }

    @Test
    void defaultRunDurationIs1400Milliseconds() {
        try (ExecutionService service = new ExecutionService()) {
            assertEquals(Duration.ofMillis(1400), service.runDuration());
        }
    }

    @Test
    void parsedRunDurationIsUsedByTheService() {
        try (ExecutionService service = new ExecutionService(RunDurationConfig.parse("200"))) {
            assertEquals(Duration.ofMillis(200), service.runDuration());
        }
    }

    @Test
    void configuredDurationIsObservedInTheRun() throws Exception {
        // 200 ms is an exact lower bound: a scheduled task never fires early. 1000 ms is below the
        // 1400 ms default, so a service that ignored the setting would fail the upper bound.
        try (ExecutionService service = new ExecutionService(RunDurationConfig.parse("200"))) {
            ExecutionService.RunResult started = service.start("TC-3", "Configured duration", "pass");
            assertEquals("In Progress", started.status());

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            ExecutionService.RunResult result = service.find(started.id());
            while ("In Progress".equals(result.status()) && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(10);
                result = service.find(started.id());
            }

            assertEquals("Passed", result.status());
            assertTrue(result.durationMs() >= 200, "duration was " + result.durationMs());
            assertTrue(result.durationMs() < 1000, "duration was " + result.durationMs());
        }
    }

    @Test
    void unknownRunReturnsNull() {
        try (ExecutionService service = new ExecutionService(Duration.ZERO)) {
            assertNull(service.find("missing"));
        }
    }

    @Test
    void statsAreZeroWhenNoRunHasStarted() {
        try (ExecutionService service = new ExecutionService(Duration.ofSeconds(10))) {
            ExecutionService.RunStats stats = service.stats();

            assertEquals(0, stats.inProgress());
            assertEquals(0, stats.passed());
            assertEquals(0, stats.failed());
            assertEquals(0, stats.totalRuns());
        }
    }

    @Test
    void statsCountARunThatHasNotCompletedAsInProgress() {
        // 10 s cannot elapse during this test, so no sleep or tolerance is needed.
        try (ExecutionService service = new ExecutionService(Duration.ofSeconds(10))) {
            service.start("TC-1", "Sign in", "pass");

            ExecutionService.RunStats stats = service.stats();

            assertEquals(1, stats.inProgress());
            assertEquals(0, stats.passed());
            assertEquals(0, stats.failed());
            assertEquals(1, stats.totalRuns());
        }
    }

    @Test
    void statsCountCompletedPassAndFailRuns() throws Exception {
        try (ExecutionService service = new ExecutionService(Duration.ofMillis(30))) {
            service.start("TC-1", "Sign in", "pass");
            service.start("TC-2", "Checkout", "fail");

            ExecutionService.RunStats stats = awaitNoneInProgress(service);

            assertEquals(0, stats.inProgress());
            assertEquals(1, stats.passed());
            assertEquals(1, stats.failed());
            assertEquals(2, stats.totalRuns());
        }
    }

    @Test
    void rejectedStartsAreNotCounted() {
        try (ExecutionService service = new ExecutionService(Duration.ofSeconds(10))) {
            assertThrows(IllegalArgumentException.class, () -> service.start(" ", "Sign in", "pass"));
            assertThrows(IllegalArgumentException.class, () -> service.start("TC-1", "x".repeat(121), "pass"));
            assertThrows(IllegalArgumentException.class, () -> service.start("TC-1", "Sign in", "maybe"));

            ExecutionService.RunStats stats = service.stats();

            assertEquals(0, stats.inProgress());
            assertEquals(0, stats.passed());
            assertEquals(0, stats.failed());
            assertEquals(0, stats.totalRuns());
        }
    }

    @Test
    void statsAgreeWithFindForEachRun() throws Exception {
        try (ExecutionService service = new ExecutionService(Duration.ofMillis(30))) {
            String passId = service.start("TC-1", "Sign in", "pass").id();
            String failId = service.start("TC-2", "Checkout", "fail").id();
            String otherFailId = service.start("TC-3", "Search", "fail").id();

            ExecutionService.RunStats stats = awaitNoneInProgress(service);

            assertEquals("Passed", service.find(passId).status());
            assertEquals("Failed", service.find(failId).status());
            assertEquals("Failed", service.find(otherFailId).status());
            assertEquals(1, stats.passed());
            assertEquals(2, stats.failed());
            assertEquals(3, stats.totalRuns());
        }
    }

    @Test
    void statsMoveARunFromInProgressToItsFinalStatus() throws Exception {
        try (ExecutionService service = new ExecutionService(Duration.ofMillis(300))) {
            long startedAt = System.nanoTime();
            service.start("TC-1", "Sign in", "pass");
            ExecutionService.RunStats early = service.stats();
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            // Only meaningful if the test did not stall past most of the run duration.
            if (elapsedMs < 150) {
                assertEquals(1, early.inProgress());
                assertEquals(1, early.totalRuns());
            }

            ExecutionService.RunStats done = awaitNoneInProgress(service);

            assertEquals(0, done.inProgress());
            assertEquals(1, done.passed());
            assertEquals(0, done.failed());
            assertEquals(1, done.totalRuns());
        }
    }

    @Test
    void statsStayMonotonicWhileRunsComplete() throws Exception {
        // Sanity check only: the sum invariant holds by construction of RunStats, and this test may pass
        // vacuously if every run completes before the first stats() call.
        int batch = 50;
        try (ExecutionService service = new ExecutionService(Duration.ofMillis(20))) {
            for (int index = 0; index < batch; index++) {
                service.start("TC-" + index, "Case " + index, index % 2 == 0 ? "pass" : "fail");
            }

            ExecutionService.RunStats previous = service.stats();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (previous.inProgress() > 0 && System.nanoTime() < deadline) {
                ExecutionService.RunStats current = service.stats();
                assertTrue(current.inProgress() >= 0 && current.passed() >= 0 && current.failed() >= 0);
                assertTrue(current.passed() >= previous.passed(), "passed decreased");
                assertTrue(current.failed() >= previous.failed(), "failed decreased");
                assertTrue(current.totalRuns() >= previous.totalRuns(), "totalRuns decreased");
                previous = current;
                TimeUnit.MILLISECONDS.sleep(1);
            }

            ExecutionService.RunStats finalStats = awaitNoneInProgress(service);
            assertEquals(0, finalStats.inProgress());
            assertEquals(batch, finalStats.totalRuns());
            assertEquals(batch / 2, finalStats.passed());
            assertEquals(batch / 2, finalStats.failed());
        }
    }

    private static ExecutionService.RunStats awaitNoneInProgress(ExecutionService service) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        ExecutionService.RunStats stats = service.stats();
        while (stats.inProgress() > 0 && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
            stats = service.stats();
        }
        assertEquals(0, stats.inProgress(), "runs did not complete before the deadline");
        return stats;
    }
}
