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

## US-107: List recent runs with GET /api/runs

### User story

As a QA engineer, I want to list the most recent test runs from the API, so that I can see what has run without already knowing each run ID.

**Source:** Story text supplied in the /sdlc command. No Jira, MCP, Confluence, or other external source was supplied or accessed.

### Facts from the story and confirmed decisions

- `GET /api/runs` returns 200 with `{"runs":[...]}`, newest first. Each item has the same fields as `GET /api/runs/{id}`.
- At most 20 runs are returned by default. An optional `limit` query parameter accepts whole numbers from 1 to 100.
- A `limit` that is not a whole number from 1 to 100 returns 400 with `{"error":"limit must be a whole number from 1 to 100."}`.
- With no runs yet, the response is 200 with `{"runs":[]}`.
- `POST /api/runs` keeps its current behavior. Methods other than GET and POST on `/api/runs` return 405 with `Allow: GET, POST`.
- No open questions were raised by the story; the decisions above were confirmed by the user.

### Current behavior (from code inspection)

- `ExecutionService` keeps runs in an in-memory `ConcurrentHashMap` keyed by random UUID. It has no ordered view or list method, only `start` and `find`. Each run records `startedAt` (an `Instant`, serialized as the `timestamp` field) and a monotonic `startedAtNanos`.
- `GET /api/runs/{id}` returns `id`, `caseId`, `title`, `status`, `durationMs`, `timestamp`, and `failureDetails`. For a run still in progress, `durationMs` is the live elapsed time.
- `DashboardServer` routes `/api/runs` straight to the POST handler. Today any non-POST method on `/api/runs` returns 405 with `Allow: POST` and the message "Use the POST method for this endpoint." (so GET currently returns 405).
- Runs are never evicted, so the store grows for the life of the server process.

### Functional requirements

#### FR-13: List recent runs

`GET /api/runs` returns 200 with a JSON object `{"runs":[...]}`. Runs are ordered newest first by start time. Each element has exactly the fields returned by `GET /api/runs/{id}` (`id`, `caseId`, `title`, `status`, `durationMs`, `timestamp`, `failureDetails`), including runs that are still In Progress. With no runs, the array is empty (`{"runs":[]}`).

#### FR-14: Limit the number of runs returned

Without `limit`, at most 20 runs are returned. With a valid `limit=N` (whole number from 1 to 100), at most N runs are returned, being the N newest. If fewer runs exist than the limit, all of them are returned.

#### FR-15: Reject an invalid limit

A `limit` value that is not a whole number from 1 to 100 (for example `0`, `101`, `abc`, `2.5`) returns 400 with the body `{"error":"limit must be a whole number from 1 to 100."}`. The run store is not changed.

#### FR-16: Method handling on /api/runs

`POST /api/runs` keeps its current behavior (status code, response body, and validation). Methods other than GET and POST on `/api/runs` return 405 with the header `Allow: GET, POST`. The 405 JSON error text may change to mention both methods. Other endpoints (`/api/health`, `/api/runs/{id}`, static files) are unchanged.

### Non-functional requirements

- Backend only: no UI change, no new dependencies, Java 17 and Maven only.
- Listing is read-only and must not start, modify, or remove runs.
- Listing must be safe while runs are being started and completed concurrently, and must not throw or return a partly built list.
- The response must use the same JSON conventions as the existing endpoints (UTF-8, `Content-Type: application/json; charset=utf-8`, `Cache-Control: no-store`, string escaping).
- Response size is bounded by the 100-run maximum regardless of how many runs the store holds.
- Ordering must be deterministic for runs with distinct start times.

### Acceptance criteria

The story's criteria are numbered AC-01 to AC-07. Those numbers are already used by earlier stories in this file, so this section's IDs carry a prefix (`US-107 AC-nn`) to stay unique. The numbering and wording are otherwise as supplied.

- **US-107 AC-01 — Newest first:** `GET /api/runs` returns 200 and a `runs` array ordered newest first. (FR-13)
- **US-107 AC-02 — Empty list:** With no runs, `GET /api/runs` returns `{"runs":[]}`. (FR-13)
- **US-107 AC-03 — Default and custom limit:** Without `limit`, at most 20 runs are returned; with `limit=N`, at most N are returned. (FR-14)
- **US-107 AC-04 — Invalid limit:** `limit=0`, `limit=101`, `limit=abc`, and `limit=2.5` each return 400 with `{"error":"limit must be a whole number from 1 to 100."}` exactly. (FR-15)
- **US-107 AC-05 — Methods:** `POST /api/runs` still starts a run exactly as before, and PUT or DELETE on `/api/runs` returns 405 with `Allow: GET, POST`. (FR-16)
- **US-107 AC-06 — Unit tests:** Unit tests cover ordering, the default and custom limit, each invalid limit, the empty list, and the 405 case. (all FRs)
- **US-107 AC-07 — README:** `README.md` documents `GET /api/runs` with an example response. (FR-13, FR-14)

### Assumptions

- "Newest" means most recently started, using each run's start time. The store is unordered today, so ordering is applied when listing.
- Each list item has the same JSON field names and types as `GET /api/runs/{id}`; the same serialization is reused.
- Runs still In Progress are included, with their live `durationMs`.
- Boundary values `limit=1` and `limit=100` are valid. Valid means digits only; `limit=2.5`, `limit=abc`, `limit=0`, and `limit=101` are the invalid examples given.
- The 400 error uses the existing error JSON shape (`{"error":"..."}`) and the existing JSON response headers.
- Run data lives only in memory, so the list holds only runs started since the server process started (persistence is out of scope).
- The `limit` parameter is read from the request query string only, and unrelated query parameters are ignored.

### Open questions

These gaps are not settled by the confirmed decisions. Proposed defaults are shown so the story can proceed if they are accepted.

1. **Tie-break for equal start times.** `Instant.now()` can return the same value for runs started in the same clock tick, and the store has no sequence number. Proposed: break ties by the monotonic start time (`startedAtNanos`) or an insertion counter so the newest-first order is stable. Needs a decision only if "deterministic for ties" must be tested.
2. **Repeated or empty `limit`.** Behavior for `limit=` (empty), `limit` with no value, and `limit=5&limit=10` is not stated. Proposed: an empty or valueless `limit` is invalid and returns 400; for repeated `limit`, the first value is used (or reject with 400).
3. **Number formats for `limit`.** Behavior for `+5`, `05`, ` 5` (surrounding space), and very large values such as `99999999999` is not stated. Proposed: digits only, so `+5` and ` 5` are rejected with 400; leading zeros such as `05` and very large values are handled without an unhandled exception (large values return the same 400). This is consistent with US-105's `RUN_DURATION_MS` rules.
4. **HEAD and OPTIONS.** "Methods other than GET and POST" returns 405 literally, which includes HEAD and OPTIONS. Proposed: follow the story literally.

### Out of scope

- Filtering by status (or by any other field).
- Pagination beyond `limit` (no offset, cursor, or page number).
- Persistence across restarts.
- Any UI change, including showing the run list in the dashboard.
- Evicting or capping stored runs, or changing how runs are created, timed, or reported.
- Changes to `GET /api/runs/{id}`, `/api/health`, `PORT`, or `RUN_DURATION_MS` handling.

### Requirements-phase status

Approved by user. The user approved this section together with the proposed defaults for the four open questions above: (1) ties on equal start time are broken by the monotonic start time or an insertion counter; (2) an empty or valueless `limit` returns 400, and with a repeated `limit` the first value is used; (3) `limit` is digits only, so `+5`, ` 5`, and very large values such as `99999999999` return the same 400 with no unhandled exception, while leading zeros such as `05` are accepted as 5; (4) HEAD and OPTIONS on `/api/runs` return 405 with `Allow: GET, POST`. The requirement text above is otherwise unchanged.
