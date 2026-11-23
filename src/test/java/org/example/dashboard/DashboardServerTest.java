package org.example.dashboard;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardServerTest {
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void servesDashboardAndHealthEndpoint() throws Exception {
        try (ExecutionService execution = new ExecutionService(Duration.ofMillis(20));
             DashboardServer server = new DashboardServer(0, execution)) {
            server.start();

            HttpResponse<String> page = get(server, "/");
            HttpResponse<String> health = get(server, "/api/health");

            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("Caseflow"));
            assertTrue(page.headers().firstValue("Content-Security-Policy").orElse("").contains("default-src 'self'"));
            assertEquals(200, health.statusCode());
            assertEquals("{\"status\":\"ok\"}", health.body());
        }
    }

    @Test
    void startsAndPollsRunAndRejectsBadRequests() throws Exception {
        try (ExecutionService execution = new ExecutionService(Duration.ofMillis(40));
             DashboardServer server = new DashboardServer(0, execution)) {
            server.start();
            URI base = URI.create("http://localhost:" + server.port());

            HttpResponse<String> started = client.send(
                    HttpRequest.newBuilder(base.resolve("/api/runs"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString("caseId=TC-9&title=Saved+case&outcome=fail"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(202, started.statusCode());
            assertTrue(started.body().contains("\"status\":\"In Progress\""));
            String id = started.body().split("\"id\":\"")[1].split("\"")[0];

            Thread.sleep(100);
            HttpResponse<String> completed = get(server, "/api/runs/" + id);
            assertEquals(200, completed.statusCode());
            assertTrue(completed.body().contains("\"status\":\"Failed\""));
            assertTrue(completed.body().contains("Simulated UI assertion failed"));

            HttpResponse<String> invalid = client.send(
                    HttpRequest.newBuilder(base.resolve("/api/runs"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString("caseId=TC-9&title=Saved+case&outcome=other"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(400, invalid.statusCode());
            assertTrue(invalid.body().contains("\"error\""));

            HttpResponse<String> missing = get(server, "/api/runs/missing");
            assertEquals(404, missing.statusCode());
            assertFalse(missing.body().isBlank());
        }
    }

    @Test
    void returnsNotFoundForUnknownStaticPath() throws Exception {
        try (ExecutionService execution = new ExecutionService(Duration.ZERO);
             DashboardServer server = new DashboardServer(0, execution)) {
            server.start();
            HttpResponse<String> response = get(server, "/not-a-page");

            assertEquals(404, response.statusCode());
            assertTrue(response.body().contains("Not found"));
        }
    }

    @Test
    void statsOnAFreshServerAreZeroWithStandardHeaders() throws Exception {
        try (ExecutionService execution = new ExecutionService(Duration.ofSeconds(10));
             DashboardServer server = new DashboardServer(0, execution)) {
            server.start();

            HttpResponse<String> stats = send(server, "GET", "/api/stats", "");

            assertEquals(200, stats.statusCode());
            assertEquals("{\"totalRuns\":0,\"inProgress\":0,\"passed\":0,\"failed\":0}", stats.body());
            assertTrue(stats.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
            assertEquals("no-store", stats.headers().firstValue("Cache-Control").orElse(""));
            assertEquals("nosniff", stats.headers().firstValue("X-Content-Type-Options").orElse(""));
            assertTrue(stats.headers().firstValue("Content-Security-Policy").isPresent());
        }
    }

    @Test
    void statsCountCompletedPassAndFailRunsAndAgreeWithRunStatus() throws Exception {
        try (ExecutionService execution = new ExecutionService(Duration.ofMillis(30));
             DashboardServer server = new DashboardServer(0, execution)) {
            server.start();

            HttpResponse<String> passRun = send(server, "POST", "/api/runs", "caseId=TC-1&title=Sign+in&outcome=pass");
            HttpResponse<String> failRun = send(server, "POST", "/api/runs", "caseId=TC-2&title=Checkout&outcome=fail");
            assertEquals(202, passRun.statusCode());
            assertEquals(202, failRun.statusCode());

            String stats = awaitNoRunInProgress(server);

            assertEquals("{\"totalRuns\":2,\"inProgress\":0,\"passed\":1,\"failed\":1}", stats);
            assertTrue(send(server, "GET", "/api/runs/" + runId(passRun), "").body().contains("\"status\":\"Passed\""));
            assertTrue(send(server, "GET", "/api/runs/" + runId(failRun), "").body().contains("\"status\":\"Failed\""));
        }
    }

    @Test
    void statsCountAnInProgressRun() throws Exception {
        try (ExecutionService execution = new ExecutionService(Duration.ofSeconds(10));
             DashboardServer server = new DashboardServer(0, execution)) {
            server.start();

            assertEquals(202, send(server, "POST", "/api/runs", "caseId=TC-1&title=Sign+in&outcome=pass").statusCode());

            assertEquals("{\"totalRuns\":1,\"inProgress\":1,\"passed\":0,\"failed\":0}",
                    send(server, "GET", "/api/stats", "").body());
        }
    }

    @Test
    void statsRouteIgnoresQueryStringAndDoesNotMatchTrailingSlash() throws Exception {
        try (ExecutionService execution = new ExecutionService(Duration.ofSeconds(10));
             DashboardServer server = new DashboardServer(0, execution)) {
            server.start();

            HttpResponse<String> withQuery = send(server, "GET", "/api/stats?x=1", "");
            HttpResponse<String> trailingSlash = send(server, "GET", "/api/stats/", "");

            assertEquals(200, withQuery.statusCode());
            assertEquals("{\"totalRuns\":0,\"inProgress\":0,\"passed\":0,\"failed\":0}", withQuery.body());
            assertEquals(404, trailingSlash.statusCode());
            assertTrue(trailingSlash.body().contains("Not found"));
        }
    }

    @Test
    void rejectedRunRequestsDoNotChangeStats() throws Exception {
        try (ExecutionService execution = new ExecutionService(Duration.ofSeconds(10));
             DashboardServer server = new DashboardServer(0, execution)) {
            server.start();
            assertEquals(202, send(server, "POST", "/api/runs", "caseId=TC-1&title=Sign+in&outcome=pass").statusCode());
            String before = send(server, "GET", "/api/stats", "").body();

            HttpResponse<String> badOutcome = send(server, "POST", "/api/runs", "caseId=TC-2&title=Checkout&outcome=maybe");
            // Modestly over the 4096-byte limit; the server answers 413 without creating a run.
            HttpResponse<String> oversized = send(server, "POST", "/api/runs",
                    "caseId=TC-3&title=" + "x".repeat(5000) + "&outcome=pass");

            assertEquals(400, badOutcome.statusCode());
            assertEquals(413, oversized.statusCode());
            assertEquals("{\"totalRuns\":1,\"inProgress\":1,\"passed\":0,\"failed\":0}", before);
            assertEquals(before, send(server, "GET", "/api/stats", "").body());
        }
    }

    @Test
    void statsRejectNonGetMethodsWithoutChangingCounts() throws Exception {
        try (ExecutionService execution = new ExecutionService(Duration.ofSeconds(10));
             DashboardServer server = new DashboardServer(0, execution)) {
            server.start();

            for (String method : new String[] {"POST", "PUT", "DELETE"}) {
                HttpResponse<String> response = send(server, method, "/api/stats", "caseId=TC-1&title=Sign+in&outcome=pass");

                assertEquals(405, response.statusCode(), method);
                assertEquals("GET", response.headers().firstValue("Allow").orElse(""), method);
                assertTrue(response.body().contains("\"error\""), method);
            }

            assertEquals("{\"totalRuns\":0,\"inProgress\":0,\"passed\":0,\"failed\":0}",
                    send(server, "GET", "/api/stats", "").body());
        }
    }

    @Test
    void statsRejectHeadWithAllowHeader() throws Exception {
        try (ExecutionService execution = new ExecutionService(Duration.ofSeconds(10));
             DashboardServer server = new DashboardServer(0, execution)) {
            server.start();

            HttpResponse<Void> response = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/api/stats"))
                            .timeout(Duration.ofSeconds(5))
                            .method("HEAD", HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.discarding());

            // Only status and Allow are asserted. Expected noise in the test log: a JDK HttpServer
            // "content length for a HEAD request" warning and a "Dashboard connection failed: IOException"
            // line, because sendMethodNotAllowed writes a body that a HEAD response cannot carry.
            assertEquals(405, response.statusCode());
            assertEquals("GET", response.headers().firstValue("Allow").orElse(""));
        }
    }

    private String awaitNoRunInProgress(DashboardServer server) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        String body = send(server, "GET", "/api/stats", "").body();
        while (!body.contains("\"inProgress\":0") && System.nanoTime() < deadline) {
            Thread.sleep(10);
            body = send(server, "GET", "/api/stats", "").body();
        }
        assertTrue(body.contains("\"inProgress\":0"), "runs did not complete before the deadline: " + body);
        return body;
    }

    private static String runId(HttpResponse<String> started) {
        return started.body().split("\"id\":\"")[1].split("\"")[0];
    }

    private HttpResponse<String> send(DashboardServer server, String method, String path, String body)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + path))
                .timeout(Duration.ofSeconds(5));
        if (body.isEmpty()) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/x-www-form-urlencoded")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(DashboardServer server, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + server.port() + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
