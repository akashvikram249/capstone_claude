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
}
