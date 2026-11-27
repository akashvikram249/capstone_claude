package org.example.dashboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    void listRecentIsEmptyWhenNoRunsExist() {
        try (ExecutionService service = new ExecutionService(Duration.ZERO)) {
            assertTrue(service.listRecent(20).isEmpty());
        }
    }

    @Test
    void listRecentReturnsRunsNewestFirst() {
        try (ExecutionService service = new ExecutionService(Duration.ZERO)) {
            List<String> startOrder = startRuns(service, 5);

            assertEquals(reversed(startOrder), ids(service.listRecent(20)));
        }
    }

    @Test
    void listRecentBreaksEqualStartTimesByInsertionOrder() {
        // Every run gets the same Instant, so only the sequence counter can decide the order.
        Clock fixed = Clock.fixed(Instant.parse("2026-11-27T10:15:30.123456Z"), ZoneOffset.UTC);
        try (ExecutionService service = new ExecutionService(Duration.ZERO, fixed)) {
            List<String> startOrder = startRuns(service, 6);
            List<ExecutionService.RunResult> listed = service.listRecent(20);

            assertEquals(reversed(startOrder), ids(listed));
            assertTrue(listed.stream().allMatch(run -> run.timestamp().equals("2026-11-27T10:15:30.123456Z")));
        }
    }

    @Test
    void listRecentOrdersByStartTimeBeforeInsertionOrder() {
        // The clock steps back for the second run: start time decides first, sequence only breaks ties.
        MutableClock clock = new MutableClock(Instant.parse("2026-11-27T10:00:10Z"));
        try (ExecutionService service = new ExecutionService(Duration.ZERO, clock)) {
            String later = service.start("TC-1", "Later", "pass").id();
            clock.set(Instant.parse("2026-11-27T10:00:05Z"));
            String earlier = service.start("TC-2", "Earlier", "pass").id();

            assertEquals(List.of(later, earlier), ids(service.listRecent(20)));
        }
    }

    @Test
    void listRecentSanityCheckForARapidBatch() {
        // Sanity check only: consecutive Instant.now() values usually differ, so this does not prove the
        // tie-break. The fixed-clock test above does.
        try (ExecutionService service = new ExecutionService(Duration.ZERO)) {
            List<String> startOrder = startRuns(service, 50);

            assertEquals(reversed(startOrder), ids(service.listRecent(50)));
        }
    }

    @Test
    void listRecentHonoursLimitSmallerEqualAndLargerThanTheRunCount() {
        try (ExecutionService service = new ExecutionService(Duration.ZERO)) {
            List<String> newestFirst = reversed(startRuns(service, 4));

            assertEquals(newestFirst.subList(0, 2), ids(service.listRecent(2)));
            assertEquals(newestFirst, ids(service.listRecent(4)));
            assertEquals(newestFirst, ids(service.listRecent(100)));
            assertEquals(newestFirst.subList(0, 1), ids(service.listRecent(1)));
        }
    }

    @Test
    void listRecentIncludesInProgressAndCompletedRuns() throws Exception {
        // The run duration is per service, so the first run must wait out the full 5 s. The second run then
        // has a fresh 5 s window, so a stalled machine cannot flip its "In Progress" status mid-test.
        try (ExecutionService service = new ExecutionService(Duration.ofSeconds(5))) {
            String finished = service.start("TC-1", "Finished", "fail").id();
            awaitNotInProgress(service, finished, 30);
            String running = service.start("TC-2", "Running", "pass").id();

            List<ExecutionService.RunResult> listed = service.listRecent(20);

            assertEquals(List.of(running, finished), ids(listed));
            assertEquals("In Progress", listed.get(0).status());
            assertEquals("", listed.get(0).failureDetails());
            assertEquals("Failed", listed.get(1).status());
            assertFalse(listed.get(1).failureDetails().isBlank());
        }
    }

    @Test
    void listRecentRejectsLimitsOutsideOneToOneHundred() {
        try (ExecutionService service = new ExecutionService(Duration.ZERO)) {
            assertThrows(IllegalArgumentException.class, () -> service.listRecent(0));
            assertThrows(IllegalArgumentException.class, () -> service.listRecent(101));
            assertThrows(IllegalArgumentException.class, () -> service.listRecent(-1));
            assertThrows(IllegalArgumentException.class, () -> service.listRecent(Integer.MIN_VALUE));
        }
    }

    @Test
    void listingDoesNotChangeFindResultsOrTheRunCount() {
        try (ExecutionService service = new ExecutionService(Duration.ofSeconds(30))) {
            ExecutionService.RunResult started = service.start("TC-1", "Stable", "pass");

            service.listRecent(20);
            service.listRecent(1);

            assertEquals(started.id(), service.find(started.id()).id());
            assertEquals(1, service.listRecent(100).size());
        }
    }

    @Test
    void rejectedStartsDoNotAppearInTheList() {
        try (ExecutionService service = new ExecutionService(Duration.ZERO)) {
            assertThrows(IllegalArgumentException.class, () -> service.start("", "Sign in", "pass"));

            assertTrue(service.listRecent(20).isEmpty());
        }
    }

    @Test
    @Timeout(30)
    void listingIsSafeWhileRunsStartAndComplete() throws Exception {
        int starters = 4;
        int runsPerStarter = 100;
        int limit = 10;
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        AtomicBoolean startersDone = new AtomicBoolean();
        CountDownLatch startersFinished = new CountDownLatch(starters);

        try (ExecutionService service = new ExecutionService(Duration.ofMillis(1))) {
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < starters; i++) {
                Thread thread = new Thread(() -> {
                    try {
                        for (int run = 0; run < runsPerStarter; run++) {
                            service.start("TC-" + run, "Concurrent", run % 2 == 0 ? "pass" : "fail");
                        }
                    } catch (Throwable error) {
                        failures.add(error);
                    } finally {
                        startersFinished.countDown();
                    }
                });
                threads.add(thread);
            }
            Thread lister = new Thread(() -> {
                try {
                    while (!startersDone.get()) {
                        if (service.listRecent(limit).size() > limit) {
                            throw new AssertionError("more runs than the limit");
                        }
                    }
                } catch (Throwable error) {
                    failures.add(error);
                }
            });
            threads.add(lister);
            threads.forEach(Thread::start);

            startersFinished.await();
            startersDone.set(true);
            lister.join();

            assertTrue(failures.isEmpty(), "failures: " + failures);
            assertEquals(limit, service.listRecent(limit).size());
            assertEquals(Math.min(100, starters * runsPerStarter), service.listRecent(100).size());
        }
    }

    private static List<String> startRuns(ExecutionService service, int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(service.start("TC-" + i, "Run " + i, "pass").id());
        }
        return ids;
    }

    private static List<String> reversed(List<String> values) {
        List<String> copy = new ArrayList<>(values);
        java.util.Collections.reverse(copy);
        return copy;
    }

    private static List<String> ids(List<ExecutionService.RunResult> results) {
        return results.stream().map(ExecutionService.RunResult::id).toList();
    }

    private static void awaitNotInProgress(ExecutionService service, String id) throws InterruptedException {
        awaitNotInProgress(service, id, 5);
    }

    private static void awaitNotInProgress(ExecutionService service, String id, long timeoutSeconds)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while ("In Progress".equals(service.find(id).status()) && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertFalse("In Progress".equals(service.find(id).status()), "run did not complete in time");
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void set(Instant value) {
            this.now = value;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
