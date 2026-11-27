package org.example.dashboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // ---- US-107: GET /api/runs ----

    private static final String INVALID_LIMIT_BODY =
            "{\"error\":\"limit must be a whole number from 1 to 100.\"}";
    private static final Pattern ID_FIELD = Pattern.compile("\"id\":\"([^\"]+)\"");

    @Test
    void listsNoRunsAsAnEmptyArray() throws Exception {
        try (ExecutionService execution = new ExecutionService(Duration.ofMillis(20));
             DashboardServer server = new DashboardServer(0, execution)) {
            server.start();

            HttpResponse<String> response = get(server, "/api/runs");

            assertEquals(200, response.statusCode());
            assertEquals("{\"runs\":[]}", response.body());
            assertJsonHeaders(response);
        }
    }

    @Test
    void listsRunsNewestFirstWithTheSameFieldsAsGetById() throws Exception {
        try (ExecutionService execution = new ExecutionService(Duration.ofMillis(20));
             DashboardServer server = new DashboardServer(0, execution)) {
            server.start();
            String first = idOf(post(server, "/api/runs", "caseId=TC-1&title=First&outcome=pass"));
            String second = idOf(post(server, "/api/runs", "caseId=TC-2&title=Second&outcome=fail"));
            awaitCompleted(server, first);
            awaitCompleted(server, second);

            HttpResponse<String> list = get(server, "/api/runs");

            assertEquals(200, list.statusCode());
            assertEquals(List.of(second, first), idsIn(list.body()));
            String expectedFirst = get(server, "/api/runs/" + first).body();
            String expectedSecond = get(server, "/api/runs/" + second).body();
            assertEquals("{\"runs\":[" + expectedSecond + "," + expectedFirst + "]}", list.body());
            assertTrue(list.body().contains("\"status\":\"Failed\""));
            assertTrue(list.body().contains("\"failureDetails\":\"\""));
            assertJsonHeaders(list);
        }
    }

    @Test
    void listsAnInProgressRunWithEmptyFailureDetails() throws Exception {
        try (ExecutionService execution = new ExecutionService(Duration.ofSeconds(30));
             DashboardServer server = new DashboardServer(0, execution)) {
            server.start();
            String id = idOf(post(server, "/api/runs", "caseId=TC-1&title=Slow&outcome=fail"));

            HttpResponse<String> list = get(server, "/api/runs");

            assertEquals(List.of(id), idsIn(list.body()));
            assertTrue(list.body().contains("\"status\":\"In Progress\""));
            assertTrue(list.body().contains("\"failureDetails\":\"\"}"));
        }
    }

    @Test
    void defaultsToTwentyRunsAndHonoursACustomLimit() throws Exception {
        try (ExecutionService execution = new ExecutionService(Duration.ofSeconds(30));
             DashboardServer server = new DashboardServer(0, execution)) {
            server.start();
            List<String> startOrder = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                startOrder.add(idOf(post(server, "/api/runs", "caseId=TC-" + i + "&title=Run&outcome=pass")));
            }
            List<String> newestFirst = new ArrayList<>(startOrder);
            Collections.reverse(newestFirst);

            assertEquals(newestFirst.subList(0, 20), idsIn(get(server, "/api/runs").body()));
            assertEquals(newestFirst.subList(0, 3), idsIn(get(server, "/api/runs?limit=3").body()));
            assertEquals(newestFirst.subList(0, 1), idsIn(get(server, "/api/runs?limit=1").body()));
            assertEquals(newestFirst.subList(0, 5), idsIn(get(server, "/api/runs?limit=05").body()));
            assertEquals(newestFirst, idsIn(get(server, "/api/runs?limit=100").body()));
            assertEquals(newestFirst.subList(0, 4), idsIn(get(server, "/api/runs?other=x&limit=4").body()));
            assertEquals(newestFirst.subList(0, 5), idsIn(get(server, "/api/runs?limit=5&limit=abc").body()));
        }
    }

    @Test
    void limitOfOneHundredReturnsTheHundredNewestOfMoreRuns() throws Exception {
        try (ExecutionService execution = new ExecutionService(Duration.ofSeconds(30));
             DashboardServer server = new DashboardServer(0, execution)) {
            server.start();
            List<String> startOrder = new ArrayList<>();
            for (int i = 0; i < 105; i++) {
                startOrder.add(execution.start("TC-" + i, "Run", "pass").id());
            }
            List<String> newestFirst = new ArrayList<>(startOrder);
            Collections.reverse(newestFirst);

            List<String> listed = idsIn(get(server, "/api/runs?limit=100").body());

            assertEquals(100, listed.size());
            assertEquals(newestFirst.subList(0, 100), listed);
        }
    }

    @Test
    void rejectsAnInvalidLimitWithTheExactErrorBody() throws Exception {
        // A malformed escape such as limit=%zz is covered in RunListQueryTest only: the JDK server rejects it
        // while parsing the request line, before the handler runs, and HttpClient cannot send it.
        String[] invalidQueries = {
                "limit=0", "limit=101", "limit=abc", "limit=2.5", "limit=", "limit", "limit=+5",
                "limit=99999999999", "limit=abc&limit=5", "limit=-1", "limit=%2B5"};
        try (ExecutionService execution = new ExecutionService(Duration.ofSeconds(30));
             DashboardServer server = new DashboardServer(0, execution)) {
            server.start();
            execution.start("TC-1", "Existing", "pass");

            for (String query : invalidQueries) {
                HttpResponse<String> response = get(server, "/api/runs?" + query);

                assertEquals(400, response.statusCode(), query);
                assertEquals(INVALID_LIMIT_BODY, response.body(), query);
                assertJsonHeaders(response);
            }
            assertEquals(1, execution.listRecent(100).size());
        }
    }

    @Test
    void postToRunsStillStartsARunAndValidatesInput() throws Exception {
        try (ExecutionService execution = new ExecutionService(Duration.ofSeconds(30));
             DashboardServer server = new DashboardServer(0, execution)) {
            server.start();

            // A query string on a POST is ignored, even an invalid limit.
            HttpResponse<String> started =
                    post(server, "/api/runs?limit=abc", "caseId=TC-9&title=Saved+case&outcome=fail");

            assertEquals(202, started.statusCode());
            assertTrue(started.body().matches(
                    "\\{\"id\":\"[^\"]+\",\"caseId\":\"TC-9\",\"title\":\"Saved case\","
                            + "\"status\":\"In Progress\",\"durationMs\":\\d+,\"timestamp\":\"[^\"]+\","
                            + "\"failureDetails\":\"\"\\}"), started.body());

            HttpResponse<String> badOutcome =
                    post(server, "/api/runs", "caseId=TC-9&title=Saved+case&outcome=other");
            assertEquals(400, badOutcome.statusCode());
            assertEquals("{\"error\":\"Demo outcome must be pass or fail.\"}", badOutcome.body());

            HttpResponse<String> tooLarge =
                    post(server, "/api/runs", "caseId=TC-9&title=" + "x".repeat(5000) + "&outcome=pass");
            assertEquals(413, tooLarge.statusCode());
            assertEquals("{\"error\":\"Request body must be 4096 bytes or fewer.\"}", tooLarge.body());

            assertEquals(1, execution.listRecent(100).size());
        }
    }

    @Test
    void otherMethodsOnRunsReturn405WithAllowGetPost() throws Exception {
        try (ExecutionService execution = new ExecutionService(Duration.ofSeconds(30));
             DashboardServer server = new DashboardServer(0, execution)) {
            server.start();

            for (String method : new String[] {"PUT", "DELETE", "PATCH"}) {
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(runsUri(server, "/api/runs"))
                                .method(method, HttpRequest.BodyPublishers.noBody()).build(),
                        HttpResponse.BodyHandlers.ofString());

                assertEquals(405, response.statusCode(), method);
                assertEquals("GET, POST", response.headers().firstValue("Allow").orElse(""), method);
                assertEquals("{\"error\":\"Use the GET or POST method for this endpoint.\"}",
                        response.body(), method);
                assertJsonHeaders(response);
            }
            assertTrue(execution.listRecent(100).isEmpty());
        }
    }

    @Test
    @Timeout(20)
    void headAndOptionsOnRunsReturn405WithAllowGetPost() throws Exception {
        try (ExecutionService execution = new ExecutionService(Duration.ofSeconds(30));
             DashboardServer server = new DashboardServer(0, execution)) {
            server.start();

            for (String method : new String[] {"HEAD", "OPTIONS"}) {
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(runsUri(server, "/api/runs"))
                                .method(method, HttpRequest.BodyPublishers.noBody()).build(),
                        HttpResponse.BodyHandlers.ofString());

                assertEquals(405, response.statusCode(), method);
                assertEquals("GET, POST", response.headers().firstValue("Allow").orElse(""), method);
                if (method.equals("HEAD")) {
                    assertEquals("", response.body());
                }
            }
            // The server is still healthy after both requests.
            assertEquals(200, get(server, "/api/health").statusCode());
        }
    }

    @Test
    void otherEndpointsKeepTheirOwnMethodHandling() throws Exception {
        try (ExecutionService execution = new ExecutionService(Duration.ofMillis(20));
             DashboardServer server = new DashboardServer(0, execution)) {
            server.start();

            HttpResponse<String> health = client.send(
                    HttpRequest.newBuilder(runsUri(server, "/api/health"))
                            .POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(405, health.statusCode());
            assertEquals("GET", health.headers().firstValue("Allow").orElse(""));
            assertEquals("{\"error\":\"Use the GET method for this endpoint.\"}", health.body());

            HttpResponse<String> byIdWrongMethod = client.send(
                    HttpRequest.newBuilder(runsUri(server, "/api/runs/abc"))
                            .DELETE().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(405, byIdWrongMethod.statusCode());
            assertEquals("GET", byIdWrongMethod.headers().firstValue("Allow").orElse(""));

            HttpResponse<String> unknownId = get(server, "/api/runs/missing");
            assertEquals(404, unknownId.statusCode());
            assertEquals("{\"error\":\"Execution report not found.\"}", unknownId.body());

            // A trailing slash is not /api/runs; it still reaches the by-id handler.
            assertEquals(404, get(server, "/api/runs/").statusCode());
            HttpResponse<String> trailingSlashPut = client.send(
                    HttpRequest.newBuilder(runsUri(server, "/api/runs/"))
                            .PUT(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(405, trailingSlashPut.statusCode());
            assertEquals("GET", trailingSlashPut.headers().firstValue("Allow").orElse(""));
        }
    }

    private HttpResponse<String> post(DashboardServer server, String path, String form) throws Exception {
        return client.send(
                HttpRequest.newBuilder(runsUri(server, path))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static URI runsUri(DashboardServer server, String path) {
        return URI.create("http://localhost:" + server.port() + path);
    }

    private static String idOf(HttpResponse<String> response) {
        assertEquals(202, response.statusCode(), response.body());
        return idsIn(response.body()).get(0);
    }

    private static List<String> idsIn(String body) {
        List<String> ids = new ArrayList<>();
        Matcher matcher = ID_FIELD.matcher(body);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    private static void assertJsonHeaders(HttpResponse<String> response) {
        assertEquals("application/json; charset=utf-8",
                response.headers().firstValue("Content-Type").orElse(""));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""));
    }

    /** Polls with a deadline instead of sleeping a fixed time. */
    private void awaitCompleted(DashboardServer server, String id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        String body = get(server, "/api/runs/" + id).body();
        while (body.contains("\"status\":\"In Progress\"") && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
            body = get(server, "/api/runs/" + id).body();
        }
        assertFalse(body.contains("\"status\":\"In Progress\""), "run did not complete in time: " + body);
    }
}
