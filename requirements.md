# Requirements

## User story

**US-102: Create and Manage UI Test Cases Through a Dashboard**

As a QA Automation Engineer, I want a web-based dashboard to create, view, edit, delete, and execute UI automation test cases, so that I can manage test scenarios efficiently, monitor execution results, and identify failures without manually reviewing test logs.

## Scope decisions

- Implement a Java 17 web server and a browser dashboard using plain HTML, CSS, and JavaScript.
- Keep test cases, the demo login name, and execution reports in the browser's local storage.
- Use a simple demo login gate only; there are no real accounts, passwords, or server-side authentication.
- The Run Test action uses a server-side simulator that returns Pass or Fail after a short delay. It does not launch a browser or run Playwright/Selenium.
- Seed sample test cases when this browser has no saved cases.

## Functional requirements

### FR-1: View, search, and filter test cases

After entering the demo dashboard, show a table with test ID/name, module, priority, test status, and last result. Allow search by relevant test fields and filtering by module, priority, and status.

### FR-2: Create test cases

Allow a user to create a case with a unique ID, name, module, priority, steps, and expected result. Validate required values and reject duplicate IDs without losing the form data. Confirm successful creation.

### FR-3: Edit and delete test cases

Allow saved case details to be edited and shown in the list. Ask for confirmation before deleting a case. Keep existing execution history available after a case is deleted.

### FR-4: Execute test cases

Let a user run a saved case and immediately show In Progress, followed by Passed or Failed. Demo-only outcome selection makes both results demonstrable; the simulator does not execute the written steps.

### FR-5: View execution reports

Show report status, duration, timestamp, associated case, and failure details when a run fails. Persist reports locally in the browser.

## Non-functional requirements

- Use Java 17 and Maven, without a database or frontend framework.
- Escape user-entered values before rendering them as HTML.
- Limit request size and validate values at the Java runner endpoint.
- Keep browser storage local to the current browser profile; it is not shared or backed up.
- Show actionable errors for storage or runner failures.
- Keep demo authentication and simulated execution clearly labeled as non-production.

## Acceptance criteria

- **AC-01 — View test cases:** The dashboard lists test name, module, priority, status, and last result; search and filters work.
- **AC-02 — Create a test case:** Mandatory fields are validated, duplicate IDs are rejected, and successful saves are confirmed.
- **AC-03 — Edit/delete:** Edits appear in the dashboard and deletion requires confirmation.
- **AC-04 — Execute:** Running a saved case shows In Progress followed by Passed or Failed.
- **AC-05 — Reports:** Completed reports show status, duration, timestamp, and failure details when applicable.

## Out of scope

- Production authentication, roles, access control, and multi-user data.
- Running real browser automation or connecting to an external test framework.
- Server/database persistence, audit retention, or cross-device synchronization.

## US-103: Filter Test Reports by Status

### User story

As a QA engineer, I want to filter test reports by status so I can find failed runs quickly.

**Source:** User-provided story in Copilot chat. No JIRA, Confluence, or document link was provided.

### Context and confirmed decisions

- The reports page displays completed reports stored in browser local storage.
- Current report statuses are Passed and Failed.
- The status filter provides All, Passed, and Failed options, with All selected by default.
- The selected filter is transient and is not saved to browser storage; a full page reload returns it to All.

### Functional requirements

#### FR-6: Filter execution reports by status

Provide a labeled status filter on the reports page. Selecting All shows every report; selecting Passed or Failed shows only reports with that status. Filtering updates the displayed report rows without a page reload, preserves their existing order, and does not modify or delete stored reports. When no reports match the selected status, show an explicit no-matching-reports message.

### Non-functional requirements

- Apply filtering in the browser to the report data already loaded by the dashboard; no server or persistence changes are required.
- Keep the filter operable and understandable using the dashboard's existing form-control and empty-state patterns.
- Announce the updated visible-result count and no-match state to assistive technology through a polite live region without moving focus.

### Acceptance criteria

- **AC-06 — Filter reports by status:** The reports page offers All, Passed, and Failed; All is selected by default and shows all reports.
- **AC-07 — Show matching reports:** Selecting Passed or Failed immediately displays only reports with the selected status, preserving the existing report order and report-detail actions.
- **AC-08 — Preserve report data:** Changing or resetting the filter does not change or remove reports in browser storage; after a full page reload the filter defaults to All.
- **AC-09 — Empty filter result:** If no reports match a selected status, the reports page displays a clear no-matching-reports message.
- **AC-10 — Announce filter results:** A polite live region announces the visible-result count after a filter change and announces when no reports match; focus remains on the filter.
- **AC-11 — Handle invalid report data safely:** If the stored report value is valid JSON but not an array, the dashboard shows an actionable report-data error, does not crash, and does not overwrite or delete the stored value.

### Out of scope

- Filtering by date, case, module, or other report attributes.
- Persisting the selected filter across page reloads.
- Changing how reports are generated, stored, or retained.
- Automatically repairing or deleting structurally invalid report data.

## US-104: Export visible execution reports as CSV

### User story

As a QA engineer, I want to download the execution reports currently shown on the Reports page as a CSV file, so I can share test results with my team.

**Source:** User-provided story and acceptance criteria in this chat. No Jira, MCP, Confluence, or other external source was supplied or accessed. The final acceptance-criteria sentence was truncated in the chat message after “send report data to the”; its ending, “server.”, is carried forward from the immediately preceding assistant-provided story text and is recorded transparently here rather than treated as newly supplied text.

### Confirmed story details and project context

- The story's supplied criteria require an Export CSV action; export of exactly the currently visible reports in displayed order; respect for the All/Passed/Failed filter; the specified six columns and order; a UTF-8 headered CSV with commas, quotes, and line breaks escaped; the fixed filename `caseflow-reports.csv`; and a disabled action with an explanation when no reports are visible.
- The supplied column-value clarification is explicit: `Duration (ms)` is an integer number of milliseconds, and `Executed At` is the stored ISO-8601 timestamp, not either field's human-readable display formatting.
- The supplied final criterion intends export not to change saved reports or send report data to the server. As noted above, the words “server.” are restored from the immediately preceding assistant-provided story text because the user message was truncated.
- The existing Reports page has an All/Passed/Failed status filter; All is initially selected. Its rows follow the order of the report list, and filtering preserves that order (US-103).
- Existing report data includes case ID, test-case title, status, duration in milliseconds, execution timestamp, and failure details. The Reports page formats duration and timestamp for display, while the story requires the underlying integer duration and stored timestamp in the export.
- Existing report data is held in browser local storage. The new no-mutation/no-server-transfer constraints are part of this story and apply to the export operation.

### Functional requirements

#### FR-7: Export the visible execution reports

Provide an Export CSV action on the Reports page. Export exactly the reports currently visible under the selected status filter: All includes every report; Passed and Failed include only reports matching that status. Emit columns in this exact order and with these exact header labels: `Case ID`, `Test Case`, `Status`, `Duration (ms)`, `Executed At`, `Failure Details`. Include one header row, preserve the displayed report order, and escape fields containing commas, double quotes, or line breaks so the result is valid CSV. When no reports are visible, disable the action and explain why it is unavailable. Use the underlying integer-millisecond duration and stored ISO-8601 timestamp, rather than display-formatted values. Encode the CSV as UTF-8, name the download `caseflow-reports.csv`, and do not change saved reports or send report data to the server.

### Non-functional requirements

- The downloaded file must be UTF-8 encoded and parse as valid CSV, including correct handling of embedded commas, double quotes, and line breaks.
- The disabled state and its explanation must be perceivable and understandable to users.
- The export operation must be read-only with respect to saved reports and must not transmit report data to the server.

### Acceptance criteria

- **AC-12 — Export action:** The Reports page provides an Export CSV action.
- **AC-13 — Export the visible subset:** With All selected, the CSV contains every report; with Passed or Failed selected, it contains only reports of that status. The exported rows correspond to the reports visible on the page at the time of export.
- **AC-14 — CSV columns and values:** The first row contains exactly `Case ID`, `Test Case`, `Status`, `Duration (ms)`, `Executed At`, and `Failure Details`, in that order. Each data row places the corresponding report values under those columns; duration is an integer number of milliseconds and Executed At is the stored ISO-8601 timestamp, not display-formatted values.
- **AC-15 — Header and order:** The CSV includes one header row, and data rows retain the same order as the visible report rows.
- **AC-16 — UTF-8 CSV escaping:** The downloaded CSV is UTF-8 encoded. A CSV reader recovers the original field values when fields contain commas, double quotes, or line breaks.
- **AC-17 — No visible reports:** When the current filter yields no visible reports, Export CSV is disabled and the page explains that there are no visible reports to export. This applies both when there are no reports and when the selected status has no matches.
- **AC-18 — Filename:** The downloaded file is named exactly `caseflow-reports.csv`.
- **AC-19 — Read-only local export:** Exporting leaves saved reports unchanged and does not send report data to the server.

### Assumptions and unresolved questions

- No additional assumptions or behavior questions remain from the supplied story. The sole reconstructed wording is the truncated final sentence's “server.” ending, explicitly sourced from the immediately preceding assistant-provided story text above.

### Out of scope

- Adding or changing report fields, report generation, retention, or persistence.
- Exporting reports that are not currently visible under the selected status filter.
- Changing report values or their display formatting as part of this export story.

## DOCSYNC-1: Generate and check the API reference from OpenAPI

### User story

As a repository maintainer, I want the API reference generated from the OpenAPI definition and checked for drift, so that the documentation stays accurate as the API changes.

**Source:** Demo story and acceptance criteria pasted directly in chat. No Jira, MCP, or other external source was retrieved.

**Context:** This is a separate story from US-102 through US-104. The repository inspection found no OpenAPI definition or existing API-reference file in the `docs/` or `automated-api-documentation-sync/` directories; the current CI workflow runs `mvn --batch-mode verify`. The intended OpenAPI source path, generated reference path, and CI invocation are not yet confirmed.

### Functional requirements

#### FR-8: Generate the API reference

Given a valid OpenAPI definition, generate or update a clearly identified API reference that includes every endpoint's path, HTTP method, and summary.

#### FR-9: Check the generated reference for drift

Provide a check that detects when the existing API reference differs from what would be generated from the current OpenAPI definition. When drift is detected, report that the reference is stale and explain how to regenerate it.

#### FR-10: Preserve repository files and handle invalid input

The drift check must be read-only: it must not modify files or commit or push changes. Missing or invalid OpenAPI input must produce an actionable error and must not overwrite an existing API reference. Updating the generated reference must preserve other documentation.

#### FR-11: Cover required behavior with tests

Tests must cover successful generation, deterministic unchanged output, detection of stale documentation, and missing or invalid OpenAPI input.

### Non-functional requirements and constraints

- For unchanged OpenAPI input, repeated generation must produce byte-for-byte identical output.
- Drift checking must not change any repository files, including the generated reference.
- Generation or validation errors must be actionable and non-destructive to an existing reference.
- A reference update must not remove or alter unrelated documentation.

### Acceptance criteria

- **DOCSYNC-AC-01 — Generate endpoint reference:** With a valid OpenAPI definition, generation creates or updates a clearly identified API reference containing each defined endpoint's path, HTTP method, and summary.
- **DOCSYNC-AC-02 — Deterministic output:** Running generation at least twice with unchanged input produces byte-for-byte identical output.
- **DOCSYNC-AC-03 — Stale check guidance:** When the checked-in reference does not match the output for the current definition, the check reports that it is stale and states how to regenerate it.
- **DOCSYNC-AC-04 — Read-only check:** Running the drift check makes no file modifications and performs no commit or push.
- **DOCSYNC-AC-05 — Safe input errors:** If the OpenAPI definition is missing or invalid, the command/check reports an actionable error and leaves any existing API reference unchanged.
- **DOCSYNC-AC-06 — Preserve other documentation:** Updating the generated reference does not alter or remove documentation outside that generated reference.
- **DOCSYNC-AC-07 — Required test coverage:** The test suite covers successful generation, byte-for-byte unchanged output for unchanged input, stale-reference detection, and missing and invalid OpenAPI input.

### Duplicate-criteria handling

The pasted acceptance criteria contained duplicates and restarted numbering. Exact duplicate statements were consolidated into the seven unique criteria above and assigned stable `DOCSYNC-AC-01` through `DOCSYNC-AC-07` identifiers. No distinct criterion has been intentionally dropped.

### Proposed assumptions — not confirmed

- “Stale” means the current generated reference differs from the checked-in reference when compared against the same OpenAPI input.
- The generated API reference is a repository file. Its format and exact location remain undecided.
- The drift check is expected to be usable in CI, but how it is invoked and whether/how it is integrated into the existing workflow remain undecided.

### Open questions for the maintainer

1. What is the authoritative OpenAPI definition path (and, if relevant, which definition/version should be used)?
2. Where should the generated API reference live, and is a specific output format (for example, Markdown) required?
3. How should CI invoke the drift check: should it be added to the existing GitHub Actions workflow, and if so, which command should that workflow run?

### Out of scope

- Changing API behavior or creating/changing the OpenAPI definition itself.
- Adding endpoint details beyond path, HTTP method, and summary unless separately requested.
- Automatically committing or pushing generated documentation.
- Modifying or regenerating unrelated documentation.

### Requirements-phase status

Draft only. The story is documented, but source/output locations and CI invocation are unresolved. Human answers and explicit approval are required before handing this story to architecture.

## US-105: Configure the simulated test run duration

### User story

As a demo presenter, I want to set how long a simulated test run takes when I start the dashboard, so that demos can be fast or slow without changing code.

**Source:** User-provided story, decisions, and acceptance criteria in this chat. No Jira, MCP, Confluence, or other external source was supplied or accessed.

### Context and confirmed decisions

- Current behavior (from code inspection): `ExecutionService()` hard-codes a 1400 ms simulated run duration (`Duration.ofMillis(1400)`); a package-private constructor already accepts a `Duration`. `DashboardServer.main` reads the port from the first command-line argument, or else from the `PORT` environment variable (default 8080), then constructs `ExecutionService` and starts listening. `README.md` documents the port override on the line that follows the "Open http://localhost:8080" instruction.
- The duration is configured with the environment variable `RUN_DURATION_MS`, in milliseconds.
- If `RUN_DURATION_MS` is unset or blank, the duration stays 1400 ms (current behavior).
- A valid value is a whole number from 100 to 10000, inclusive.
- Any other value (text, decimal, negative, below 100, above 10000) stops startup before the server listens, with the message `RUN_DURATION_MS must be a whole number from 100 to 10000.` and a non-zero exit code. The application must not silently fall back to the default.
- Existing `PORT` and port-argument behavior does not change.
- Backend only: no UI, API, or report-format changes, and no new dependencies.
- Runs remain simulated; this story only changes how long they take.

### Confirmed decisions on edge cases (resolved from the earlier open questions)

1. Surrounding Unicode whitespace around a numeric value is stripped (Java `String.strip()` semantics, which follows `isBlank()`), so `" 500 "` is accepted as 500, and so is a value surrounded by an EM SPACE (U+2003). A non-breaking space (U+00A0) is not whitespace under these semantics and is rejected. A whitespace-only value is blank and uses 1400 ms. (Design-review decision D3: this refines the earlier word "trimmed".)
2. After stripping, the value must be digits only. `+100` and `0100` are rejected as not whole numbers, and so are decimals, exponent notation, and signs.
3. A value too large for the integer type, such as `99999999999`, is rejected with the same message as any other invalid value. It must not crash with a `NumberFormatException` or any other unhandled error.

### Functional requirements

#### FR-12: Configure the simulated run duration at startup

At startup, read `RUN_DURATION_MS` from the environment and strip surrounding Unicode whitespace (`String.strip()` semantics). When it is unset or blank (empty or whitespace-only), use 1400 ms. When the stripped value consists of digits only, has no sign and no leading zero, and is a whole number from 100 to 10000 inclusive, use that many milliseconds as the simulated run duration for every run started by that server process. When it is any other value (including signs such as `+100`, leading zeros such as `0100`, decimals, exponent notation, text, and numbers too large for the integer type), print `RUN_DURATION_MS must be a whole number from 100 to 10000.`, exit with a non-zero exit code, and do not begin listening on the port. A started run reports In Progress and then Passed or Failed after the configured duration, as in US-102 FR-4.

### Non-functional requirements

- Validation must complete before the server listens, so an invalid configuration never results in a running server.
- An invalid value must produce a clear, actionable message and must never be silently replaced by the default.
- The change must not alter `PORT`/port-argument handling, the run-start and run-status API contract, the browser UI, or the stored/exported report format.
- No new dependencies; Java 17 and Maven only.
- Simulated duration is approximate: timing is scheduler-based, so tests should allow reasonable tolerance rather than exact equality.

### Acceptance criteria

- **AC-20 — Default duration:** With `RUN_DURATION_MS` unset or blank, runs take 1400 ms.
- **AC-21 — Accepted boundaries:** With `RUN_DURATION_MS=100` or `RUN_DURATION_MS=10000`, the value is accepted and used as the run duration. A value with surrounding whitespace, such as `" 500 "`, is stripped and accepted as 500.
- **AC-22 — Rejected values:** Values outside 100-10000, or that are not whole numbers (for example text, decimals, exponent notation such as `1e3`, signed values such as `+100` or `-5`, leading-zero values such as `0100`, 99, 10001, and values too large for the integer type such as `99999999999`), are rejected with the message `RUN_DURATION_MS must be a whole number from 100 to 10000.` and a non-zero exit code, before the server listens and without falling back to the default.
- **AC-23 — Configured duration observed:** Starting with `RUN_DURATION_MS=200`, a started run reports In Progress and then Passed or Failed after about 200 ms.
- **AC-24 — Unit tests:** Unit tests cover the default (unset and blank), both boundaries (100 and 10000), and rejected values.
- **AC-25 — README:** `README.md` documents `RUN_DURATION_MS` (unit, default, valid range, failure on invalid values) next to the existing `PORT` note, with a concrete example (for example `RUN_DURATION_MS=500 mvn exec:java`), and includes a caution that reloading or closing the page during a run leaves that case showing "In Progress" (known limitation; see Out of scope).

### Assumptions

- Because validation is part of startup, it applies whether the port comes from the command-line argument or from `PORT`; an invalid `RUN_DURATION_MS` stops startup in both cases.
- "Blank" means an empty string or one containing only whitespace; both behave as unset and use 1400 ms.
- Settled parsing rules (see "Confirmed decisions on edge cases"): surrounding Unicode whitespace is stripped; the stripped value must be digits only; signs, leading zeros, decimals, exponent notation, text, and values too large for the integer type are rejected with the standard message and no unhandled exception.
- "Non-zero exit code" is satisfied by any non-zero value; the specific code is a design detail.
- The message is written to the standard error stream (design may confirm); the requirement is only that it is shown to the person starting the server.
- The configured value is read once at startup and is not changeable while the server runs.
- Test cases are expected to use time tolerance for AC-23 since the scheduler is not exact.

### Open questions

None. The three earlier questions (surrounding whitespace, `+100`/`0100`, and values too large for the integer type) are resolved; see "Confirmed decisions on edge cases".

### Out of scope

- UI, API, or report-format changes, and any per-run or per-test-case duration setting.
- Changing the run outcome logic or making runs execute real browser automation.
- Changes to `PORT` or the port argument, or adding a command-line argument or config file for duration.
- Changing the duration while the server is running.
- New dependencies.
- Recovering a case left showing "In Progress" after a page reload or close during a run (existing behavior in `app.js`; its exposure grows with longer durations). US-105 makes no UI change; this is documented in the README (AC-25) and recorded as a **follow-up story** to be raised separately.

### Requirements-phase status

Approved by user. The user approved the requirements directly in the working session; this was relayed to the requirements and design-review agents by the coordinating agent (design-review decision D5), so those agents did not receive it first-hand. The design review decisions D1-D5 (see `design-review.md`) are reflected above: Unicode-whitespace stripping instead of "trimmed", and the README example and stale-state caution in AC-25. No open questions remain.

## US-106: Show run statistics from the dashboard server

### User story

As a QA Automation Engineer, I want a `GET /api/stats` endpoint that returns the number of test runs the server has started, so that I can quickly see how many runs are in progress, passed, or failed without opening the dashboard.

**Source:** User-provided story, requirements, and acceptance criteria in this chat. No Jira, MCP, Confluence, or other external source was supplied or accessed.

### Context and confirmed decisions

- Current behavior (from code inspection): `ExecutionService` keeps every started run in an in-memory `ConcurrentMap` keyed by run ID (`runs`), added by `start(...)` when `POST /api/runs` succeeds. A run reports `In Progress` until its scheduled completion, then `Passed` (outcome `pass`) or `Failed` (outcome `fail`). Runs are never removed while the server runs. There is no existing counter or statistics API.
- `DashboardServer` routes `/api/health`, `/api/runs`, and `/api/runs/{id}`; any other path falls through to the static-file handler. Wrong-method requests are answered by `sendMethodNotAllowed`, which sets the `Allow` header and returns status 405 with the JSON body `{"error":"Use the <METHOD> method for this endpoint."}`. JSON responses are sent as `application/json; charset=utf-8` with `Cache-Control: no-store`.
- Requests rejected by `POST /api/runs` validation (400) or the size limit (413) never create a run, so they are not "started" runs.
- The response is exactly the four fields in the story; no extra fields.

### Functional requirements

#### FR-13: Report run statistics

Provide `GET /api/stats`, which responds with HTTP 200 and a JSON body of the form `{"totalRuns":N,"inProgress":N,"passed":N,"failed":N}`, where each `N` is a non-negative integer. `totalRuns` is the number of runs the server process has started through `POST /api/runs`; `inProgress`, `passed`, and `failed` are the numbers of those runs currently with status In Progress, Passed, and Failed respectively, using the same status values that `GET /api/runs/{id}` reports. On a server that has started no runs, all four values are 0. The counts reflect runs as they are started and as they complete, and `totalRuns` equals `inProgress + passed + failed` in every response. Any method other than GET on `/api/stats` returns 405 with an `Allow: GET` header, using the same JSON error format as the existing endpoints. The change is backend-only: it adds no change to the browser UI, browser local storage, or the behavior of `POST /api/runs`.

### Non-functional requirements

- Each response must be internally consistent: the four values come from a single view of the runs so that `totalRuns` equals the sum of the other three even while runs are completing concurrently.
- The endpoint is read-only: calling it must not start, change, or remove any run.
- Follow the existing response conventions (JSON content type, `Cache-Control: no-store`, security headers) and existing 405 handling.
- No new dependencies; Java 17 and Maven only.
- Counts are held in memory only; no persistence or database.

### Acceptance criteria

- **AC-26 — Stats response:** `GET /api/stats` returns HTTP 200 with a JSON body containing exactly the integer fields `totalRuns`, `inProgress`, `passed`, and `failed`.
- **AC-27 — Empty state:** On a freshly started server with no runs started, all four values are 0.
- **AC-28 — Counts after completed runs:** After runs are started through `POST /api/runs` with outcomes `pass` and `fail` and have completed, `passed` equals the number of completed `pass` runs, `failed` equals the number of completed `fail` runs, `inProgress` is 0, and `totalRuns` equals the number of runs started.
- **AC-29 — In-progress run:** While a started run has not yet completed, `inProgress` counts it and `totalRuns` includes it; after it completes it moves to `passed` or `failed` and `inProgress` decreases accordingly.
- **AC-30 — Totals invariant:** In every response, `totalRuns` equals `inProgress + passed + failed`. Requests rejected by `POST /api/runs` (400 or 413) do not change any count.
- **AC-31 — Method not allowed:** A non-GET request to `/api/stats` (for example POST) returns 405 with an `Allow: GET` header and a JSON `error` body, and does not change any count.
- **AC-32 — Backend only:** No browser UI, local-storage, or `POST /api/runs` behavior changes; existing tests continue to pass.
- **AC-33 — Unit/integration tests:** JUnit tests cover the empty state, counts after a pass run and a fail run, an in-progress run, and the 405 case.
- **AC-34 — Build:** `mvn verify` passes.
- **AC-35 — Documentation:** `README.md` and `CHANGELOG.md` document `GET /api/stats`, including the response shape and that counts are server-wide and reset on restart.

### Assumptions

- Counts are server-wide and held in memory for the lifetime of the server process; they reset to 0 on restart. They are not per browser, per user, or per test case.
- "Started" means a run accepted by `POST /api/runs` (HTTP 202); it counts toward `totalRuns` immediately and is `inProgress` until it completes.
- The statistics are derived from the same run states exposed by `GET /api/runs/{id}`, so a run's status in the two endpoints agrees.
- Because the existing handler matches paths exactly, only `/api/stats` is the endpoint; query strings are ignored as for other endpoints and a trailing-slash variant is not required.
- Method handling follows existing endpoints: any method other than GET, including HEAD, returns 405 with `Allow: GET`.
- Timing-based tests (in-progress versus completed) use a tolerance, since completion is scheduler-based, as in US-105.

### Open questions

None. No ambiguity changes behavior, security, or acceptance beyond the assumptions above.

### Out of scope

- Any browser UI, local-storage, or report-format change, including showing stats in the dashboard.
- Changing `POST /api/runs`, `GET /api/runs/{id}`, run outcome logic, or run duration behavior.
- Persisting counts across restarts, per-user or per-case statistics, filtering or time windows, and additional response fields.
- Resetting statistics, pruning old runs, or authentication/authorization for the endpoint.
- New dependencies.

### Requirements-phase status

Approved by user (as relayed by the coordinating agent).
