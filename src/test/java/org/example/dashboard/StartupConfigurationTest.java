package org.example.dashboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Starts the real {@code DashboardServer.main} in a child JVM to prove startup ordering and wiring.
 * Kept in its own class so it can be isolated or dropped if it proves unstable in CI.
 */
class StartupConfigurationTest {
    private static final String MESSAGE = "RUN_DURATION_MS must be a whole number from 100 to 10000.";
    private static final long CHILD_TIMEOUT_SECONDS = 25;
    // Require whitespace after the digits so a partially written port (e.g. "80" of "8080") cannot match.
    private static final Pattern RUNNING_AT = Pattern.compile("running at http://localhost:(\\d+)\\s");

    @Test
    @Timeout(60)
    void invalidDurationStopsStartupBeforeBindingTheOccupiedPort() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0)) {
            Child child = Child.start(String.valueOf(occupied.getLocalPort()), "abc");
            try {
                assertTrue(child.process.waitFor(CHILD_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                        "child did not exit; stdout=" + child.stdout() + " stderr=" + child.stderr());

                assertEquals(2, child.process.exitValue(), "stderr=" + child.stderr());
                List<String> stderrLines = child.stderr().lines().map(String::trim).toList();
                assertTrue(stderrLines.contains(MESSAGE), "stderr=" + child.stderr());
                assertFalse(child.stderr().contains("BindException"), "stderr=" + child.stderr());
                assertFalse(child.stderr().contains("Exception"), "stderr=" + child.stderr());
                assertFalse(child.stdout().contains("running at"), "stdout=" + child.stdout());
            } finally {
                child.destroy();
            }
        }
    }

    @Test
    @Timeout(60)
    void validDurationIsUsedByRunsStartedThroughMain() throws Exception {
        Child child = Child.start("0", " 300 ");
        try {
            int port = child.awaitPort();
            HttpClient client = HttpClient.newHttpClient();
            URI base = URI.create("http://localhost:" + port);

            HttpResponse<String> started = client.send(
                    HttpRequest.newBuilder(base.resolve("/api/runs"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString("caseId=TC-1&title=Startup+check&outcome=pass"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(202, started.statusCode(), started.body());
            assertTrue(started.body().contains("\"status\":\"In Progress\""), started.body());
            String id = started.body().split("\"id\":\"")[1].split("\"")[0];

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            String body;
            do {
                TimeUnit.MILLISECONDS.sleep(20);
                body = client.send(HttpRequest.newBuilder(base.resolve("/api/runs/" + id)).build(),
                        HttpResponse.BodyHandlers.ofString()).body();
            } while (body.contains("\"status\":\"In Progress\"") && System.nanoTime() < deadline);

            assertTrue(body.contains("\"status\":\"Passed\""), body);
            Matcher duration = Pattern.compile("\"durationMs\":(\\d+)").matcher(body);
            assertTrue(duration.find(), body);
            long durationMs = Long.parseLong(duration.group(1));
            // 300 is an exact lower bound; 1000 is below the 1400 ms default.
            assertTrue(durationMs >= 300, "duration was " + durationMs);
            assertTrue(durationMs < 1000, "duration was " + durationMs);
        } finally {
            child.destroy();
        }
    }

    private static final class Child {
        private final Process process;
        private final Path stdoutFile;
        private final Path stderrFile;

        private Child(Process process, Path stdoutFile, Path stderrFile) {
            this.process = process;
            this.stdoutFile = stdoutFile;
            this.stderrFile = stderrFile;
        }

        static Child start(String portArgument, String runDurationMs) throws IOException {
            // Surefire may launch tests through a manifest-only jar, so locate the classes from the code source.
            Path classes;
            try {
                classes = Paths.get(DashboardServer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            } catch (java.net.URISyntaxException e) {
                throw new IOException(e);
            }
            Path java = Path.of(System.getProperty("java.home"), "bin", "java");
            Path stdout = Files.createTempFile("dashboard-stdout", ".txt");
            Path stderr = Files.createTempFile("dashboard-stderr", ".txt");

            ProcessBuilder builder = new ProcessBuilder(java.toString(), "-cp", classes.toString(),
                    DashboardServer.class.getName(), portArgument);
            Map<String, String> env = builder.environment();
            env.remove("PORT");
            env.remove("JAVA_TOOL_OPTIONS");
            env.remove("_JAVA_OPTIONS");
            env.remove("JDK_JAVA_OPTIONS");
            env.put("RUN_DURATION_MS", runDurationMs);
            builder.redirectOutput(stdout.toFile());
            builder.redirectError(stderr.toFile());
            return new Child(builder.start(), stdout, stderr);
        }

        int awaitPort() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CHILD_TIMEOUT_SECONDS);
            while (System.nanoTime() < deadline) {
                Matcher matcher = RUNNING_AT.matcher(stdout());
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
                if (!process.isAlive()) {
                    fail("child exited early with " + process.exitValue() + "; stderr=" + stderr());
                }
                TimeUnit.MILLISECONDS.sleep(50);
            }
            fail("child did not report its port; stdout=" + stdout() + " stderr=" + stderr());
            return -1;
        }

        String stdout() throws IOException {
            return read(stdoutFile);
        }

        String stderr() throws IOException {
            return read(stderrFile);
        }

        private static String read(Path file) throws IOException {
            return Files.readString(file, StandardCharsets.UTF_8);
        }

        void destroy() throws InterruptedException, IOException {
            process.destroyForcibly();
            process.waitFor(CHILD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Files.deleteIfExists(stdoutFile);
            Files.deleteIfExists(stderrFile);
        }
    }
}
