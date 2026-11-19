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

    private HttpResponse<String> get(DashboardServer server, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + server.port() + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
