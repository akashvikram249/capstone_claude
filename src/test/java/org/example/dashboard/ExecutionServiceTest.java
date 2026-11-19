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
    void unknownRunReturnsNull() {
        try (ExecutionService service = new ExecutionService(Duration.ZERO)) {
            assertNull(service.find("missing"));
        }
    }
}
