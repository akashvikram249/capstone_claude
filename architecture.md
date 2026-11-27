# Architecture

## Overview

The capstone has a dependency-free Java 17 HTTP server, a browser-based dashboard, and a deliberately simulated test runner. The browser stores test cases and report history in local storage. The Java server only serves the app and simulates asynchronous run status.

```mermaid
flowchart LR
    Browser[Browser dashboard] --> Local[Browser localStorage]
    Browser -->|POST /api/runs| Server[Java HTTP server]
    Server --> Runner[Demo execution service]
    Browser -->|GET /api/runs/id| Runner
    Server -->|Static HTML/CSS/JS| Browser
    PR[Pull request] --> Actions[GitHub Actions]
    Actions --> Tests[Maven tests]
```

## Components

| Component | Responsibility |
|---|---|
| `DashboardServer` | Serves the UI assets and exposes health and demo-run endpoints using the JDK HTTP server. |
| `ExecutionService` | Creates in-progress run records, then completes them as Passed or Failed after a short scheduled delay. (US-107) Also lists the most recent runs. |
| `RunListQuery` | (US-107) Pure parser for the `limit` query parameter of `GET /api/runs`. |
| `static/index.html` | Dashboard layout, login gate, test-case form, reports and confirmation dialogs. |
| `static/app.js` | Local storage, create/read/update/delete, search/filter, runner polling, and report history. |
| `static/styles.css` | Responsive dashboard styling and status indicators. |
| `src/test/java` | JUnit tests for execution transitions and HTTP endpoint behavior. |
| `.github/workflows/ci.yml` | Runs the Maven test suite on pull requests and pushes to `main`. |

## Data flow

1. The dashboard starts with a demo login gate. No credential validation or session security is provided.
2. Cases are loaded from local storage, or a few sample cases are shown on first use.
3. Create/edit/delete operations update browser storage and rerender the table. Deletion requires explicit confirmation.
4. On Run Test, the browser creates a local In Progress state and submits the case ID, name, and selected demo outcome to the Java server.
5. The Java runner returns a run ID. The browser polls for completion, updates the case's last result, and saves a report locally.
6. The reports page displays status, time, duration, and simulated failure details.

## US-103: Client-side report status filter

The status filter is a view over the reports already loaded into `state.reports`; it does not introduce another data store, API, or report lifecycle. This keeps US-102's browser-local report ownership and simulated-run boundary unchanged.

```mermaid
flowchart LR
    Storage[Browser localStorage reports] -->|load at startup| State[app.js state.reports]
    Filter[Native status select: All / Passed / Failed]
    Filter -->|selected value| Projection[Filter report array in memory]
    State --> Projection
    Projection -->|ordered visible reports| Table[Existing reports table and detail actions]
    Projection -->|no matches| Empty[No matching reports message]
    Table -->|open by report ID| State
```

| Existing component / interface | US-103 responsibility |
|---|---|
| `static/index.html` reports page | Provide a visibly labeled native select with All, Passed, and Failed options. All is its default option; retain the existing reports table container and add a polite live region for the result count and no-match announcement. |
| `static/app.js` report state and renderer | Validate the loaded report value is an array before rendering. On selection change, filter `state.reports` in memory and render the resulting projection. Use `Array.filter` so the stored report order is retained. Keep report-detail actions keyed to the original report IDs in `state.reports`. |
| `static/styles.css` | Reuse the current `.filters select` form-control treatment and `.empty-state` visual pattern; no new styling system is required. |
| Browser `localStorage` | Remains the source of the full report list. The filter neither writes nor deletes report data. |

### Filter flow, behavior, and boundaries

At startup, `app.js` loads reports from local storage as it does today. The select starts at All and a render shows every report in existing order. A selection change calls the client-side report renderer without reloading the page; All uses the complete array, while Passed or Failed produces an in-memory filtered array. The displayed count represents the visible rows. Opening a report continues to resolve its ID against the full report collection, so filtering does not impair report details.

An empty stored report list keeps the existing “No execution reports yet” state. If reports exist but none match the selected status, show a distinct, clear “No matching reports” empty state. A no-match result is normal filter behavior, not a storage or runner error; changing the selection can reveal the reports again. The native select provides standard keyboard operation and the visible label explains its purpose.

After a filter change, update a polite live region with the visible result count or the no-match message without moving focus. If the stored report value parses as valid JSON but is not an array, do not call array methods on it or replace/delete it; show an actionable report-data error instead. This error is distinct from both empty states.

Filter selection is transient UI state only: do not add it to local/session storage or the report data model. It starts at All after a full page reload. In-page navigation may retain the current select value while the document remains loaded. Filtering issues no HTTP request and does not change run generation, report retention, or server responsibilities.

### Security, error handling, and testability

The browser processes only reports already available locally; no report data or selected status is sent to the Java server. Render report fields through the existing HTML-escaping helpers and retain existing storage read-error handling. Filtering must not mutate `state.reports` or call report persistence. The no-match state is separate from the existing storage-error toast behavior.

The behavior is deterministic for a given report array and selected status. Verify All/default and reload, each status subset, stable relative order, report-detail actions, no-match versus no-reports messaging, polite result announcements, unchanged stored report data, and the non-destructive invalid-data error state using a documented browser acceptance checklist. Run `mvn test` for existing Java regressions. The repository has no browser JavaScript test harness; this feature does not add test tooling or dependencies, so manual browser evidence is required for client behavior.

### Acceptance-criteria trace

| Criterion | Architectural path |
|---|---|
| AC-06 | Native labeled All/Passed/Failed select; All is the initial value and renders the full report array. |
| AC-07 | Change event filters in the browser, preserves input order, and leaves each row's report-detail action available. |
| AC-08 | Filter acts only on a derived in-memory view; it does not write/delete reports, and a document reload initializes the select to All. |
| AC-09 | An existing report list with zero matches renders the explicit no-matching-reports state, distinct from the no-reports-yet state. |
| AC-10 | A polite live region announces the visible result count or no-match state without moving focus. |
| AC-11 | A valid-JSON, non-array report value produces an actionable error without a rendering crash or change to stored data. |

**Approved decisions and trade-offs:** The two completed statuses remain Passed and Failed as specified; All includes every report currently loaded. A native select and an in-memory projection favor accessibility and minimal change over custom filter controls or cached derived state. The projection is transient, so a reload intentionally loses the selection. A polite live region supplements the native select so result changes are announced. Structurally invalid report data is reported without destructive repair. Because the repository has no browser test harness, use a documented manual browser checklist plus `mvn test` rather than adding test dependencies for this feature.

## US-104: Client-side export of visible reports as CSV

**Source and approval:** This architecture is based on the approved US-104 requirements in `requirements.md`, whose source is the user-provided chat story. No Jira or MCP retrieval is claimed. The approved requirements fix the fields, raw values, filename, empty-state behavior, and no-transfer/no-mutation boundary described below.

US-104 extends the existing Reports page without changing report creation, storage, filtering, report-detail actions, or the Java runner. The export uses the same ordered in-memory visible-report projection that supplies the table. All, Passed, and Failed therefore export exactly the rows currently visible, in their current order, without a second independent filter or a new storage read.

```mermaid
flowchart LR
    Storage[Browser localStorage reports] -->|existing load| State[app.js state.reports]
    State --> Projection[Existing ordered visible-report projection]
    Filter[Existing All / Passed / Failed select] --> Projection
    Projection -->|same rows| Table[Reports table]
    Projection -->|current rows on activation| Serializer[CSV serializer]
    Serializer -->|UTF-8 text/csv Blob| Link[Temporary download link]
    Link --> Browser[Browser download handling]
    Browser --> File[caseflow-reports.csv]
    Projection -->|empty| Empty[Disabled control + explanation]
    Invalid[Existing invalid-report-data state] -->|no export; actionable error| UI[Reports page]
```

| Component / interface | US-104 responsibility |
|---|---|
| `static/index.html` Reports page | Add an Export CSV button beside the existing filter and a visible explanation when no reports are visible. Preserve the existing status filter, table, detail actions, and live region. Use the existing native control and accessibility patterns. |
| `static/app.js` visible-report projection | Derive the ordered visible rows from `state.reports` and the current filter for both table rendering and export. Update disabled state and explanation whenever the projection changes. |
| `static/app.js` CSV serializer | Map each visible report's `caseId`, `title`, `status`, `durationMs`, `timestamp`, and `failureDetails` into the exact required column order; preserve the raw integer duration and stored ISO-8601 timestamp. Serialize CSV quoting without mutating report values. |
| `static/app.js` download lifecycle | Build UTF-8 CSV content as a browser `Blob`, use a temporary object URL and download link with the exact filename, initiate the browser download, then release temporary resources. Do not make a network request. |
| `static/styles.css` | Reuse the existing button, filter, disabled-control, empty-state, and text styles where practical; no styling framework or dependency is added. |
| Browser | Provide Blob, object-URL, and anchor-download behavior and handle the user's local download destination. The dashboard does not control that destination or receive a reliable completion signal. |

### Data flow, CSV contract, and interface

The existing page loads reports from local storage into `state.reports`. The existing All/Passed/Failed selection is applied in memory; its ordered projection drives both the table and export availability. A filter change rerenders the table and updates the control from that projection. On activation, the serializer obtains the projection for the current filter and serializes those rows only. The All filter includes every report; Passed and Failed include only matching reports. The filter remains transient as specified by US-103.

The CSV contract is exactly six columns, in this order: `Case ID`, `Test Case`, `Status`, `Duration (ms)`, `Executed At`, `Failure Details`. Emit exactly one header record followed by one data record for each visible report. Map the corresponding stored report fields as follows:

| CSV column | Report value |
|---|---|
| `Case ID` | `caseId` |
| `Test Case` | `title` |
| `Status` | `status` |
| `Duration (ms)` | `durationMs` as the underlying integer milliseconds, not the humanized display string |
| `Executed At` | `timestamp` as the stored ISO-8601 value, not the locale-formatted display string |
| `Failure Details` | `failureDetails` |

For CSV field encoding, quote any field containing a comma, a double quote, or a line break (CR or LF); within a quoted field, represent each embedded double quote as two double quotes. This is the CSV escaping needed for a reader to recover the original field value, including embedded line breaks. Do not replace or normalize a field's contents as part of CSV escaping. Encode as UTF-8 and set the download name exactly to `caseflow-reports.csv`. CRLF between CSV records is a conventional implementation choice; line breaks inside values remain part of their fields and are escaped/quoted as above.

### Empty state, errors, and download lifecycle

When the visible projection is empty—either there are no reports or the current status has no matches—disable Export CSV and show a nearby visible, understandable explanation such as “No visible reports to export.” Do not create a header-only file. Continue to distinguish these empty states in the table as US-103 specifies. If the stored report value is invalid under the existing US-103 handling, retain the actionable invalid-data error, do not export an empty fallback, and do not alter the stored value; the export control must not imply that invalid data is a valid empty report list.

On successful serialization, create a UTF-8 CSV `Blob`, create a temporary object URL, and activate a temporary download link carrying the required filename. Release the object URL and temporary link after activation using browser-compatible cleanup timing. A synchronous serialization/Blob/object-URL failure leaves report state untouched and is surfaced through the existing actionable toast/error pattern. The browser does not reliably report that a user completed or accepted the download; browser-managed download outcomes are outside the dashboard's control.

### Security, privacy, and operational boundaries

Export executes entirely in the current browser from reports already loaded in memory. It does not write to local storage, change `state.reports`, change the selected filter, update report history, issue `fetch`/XHR, or add a server endpoint. The only output is the user's normal browser download. There is no new runtime/build dependency, Java-server responsibility, database, external service, or telemetry. The app's demo login is not access control; exported reports remain ordinary local demo data and are not protected from other users of the browser profile.

CSV quoting ensures field boundaries and round-trip fidelity; it does not prevent spreadsheet applications from interpreting formula-like text as formulas. The exact-value requirement means silently prefixing, escaping beyond CSV syntax, or otherwise changing such values is not part of this design. Preserve the original value and record formula interpretation as a residual spreadsheet risk. Safe spreadsheet transformation would require a separately agreed mode or changed fidelity requirement and is not included here.

### Testability, trade-offs, and acceptance trace

The serializer is deterministic for a given ordered report projection. Verify it with commas, double quotes, CR/LF, Unicode, empty failure details, and values combining multiple special characters; parse the result with a CSV reader and confirm that the original values and exact six-column header are recovered. Browser acceptance evidence should check the All/Passed/Failed subsets against the actual visible rows, order, exact raw duration and timestamp, filename, empty-list and no-match disabled/explanation states, invalid-data non-destructive handling, local download behavior, and unchanged local storage/report state. The repository has no browser JavaScript test harness, so this architecture adds none; existing Maven Java tests do not directly exercise client-side CSV. No tests have been run in this architecture phase.

| Criterion | Architectural path |
|---|---|
| AC-12 — Export action | Add an Export CSV control to the existing Reports page. |
| AC-13 — Export the visible subset | Use only the ordered in-memory projection shared with the table; All, Passed, and Failed therefore yield the currently visible rows at activation. |
| AC-14 — CSV columns and values | Emit the six exact header labels in order and map the specified report fields; use integer `durationMs` and stored ISO-8601 `timestamp`, not display formatting. |
| AC-15 — Header and order | Emit one header record, then data records in the same order as the visible projection/table. |
| AC-16 — UTF-8 CSV escaping | Encode as UTF-8 and quote fields containing commas, quotes, or line breaks; double embedded quotes so CSV parsing recovers original field values. |
| AC-17 — No visible reports | Disable export and explain there are no visible reports, both for an empty report list and a no-match filter result. |
| AC-18 — Filename | Set the browser download filename exactly to `caseflow-reports.csv`. |
| AC-19 — Read-only local export | Perform serialization and download in the browser without changing saved reports or transmitting report data to the server. |

**Assumptions and trade-offs:** The Java 17 / Maven / plain HTML-CSS-JavaScript stack and current browser-local report ownership remain unchanged. Sharing the current projection favors parity and minimal surface area over a new export query/service. CRLF is a conventional CSV record separator; the requirement does not otherwise mandate a record delimiter. Exact-value fidelity is preserved at the cost of the noted spreadsheet formula interpretation risk. No implementation, new dependency, server endpoint, persistence change, or change to approved requirements is proposed.

## API

- `GET /api/health` — returns server status.
- `POST /api/runs` — form-encoded case ID, title, and requested demo outcome; creates a run and returns its ID.
- `GET /api/runs/{id}` — returns In Progress or the final Passed/Failed status, duration, timestamp, and failure message.
- `GET /api/runs` — (US-107) returns the most recent runs, newest first, with an optional `limit` query parameter. See the US-107 section.

## Security and operational boundaries

- The login is a UI demonstration only and must not be used to protect real data.
- The app uses no real secrets and makes no external network requests.
- Java validates the run outcome and limits the request body size. Browser-rendered user text is HTML-escaped.
- Case data and reports are not sent to the server for storage.
- The simulated result is not evidence that a real UI test passed. The optional outcome selector exists only to demonstrate both result states.

## Agentic SDLC customization

Workspace instructions give Copilot the project rules. `SDLC Orchestrator` guides eight sequential phases and delegates to a requirements, architecture, design-review, planning, implementation, code-review, verification, or PR-preparation specialist. Each phase specialist links to its matching prompt and skill. The dashboard review agent remains a focused read-only reviewer for US-102.

No lifecycle hook is installed because hidden automatic execution is unnecessary. Human approval gates control transition between phases and all publishing actions; Maven and CI provide explicit deterministic verification.

## DOCSYNC-1: Generate and check the API reference from OpenAPI

This is a separate repository-documentation capability. It does not change the dashboard API, server behavior, or the preceding US-102 through US-104 designs. The repository currently has no OpenAPI definition or API-reference file in the inspected `docs/` or `automated-api-documentation-sync/` locations, and no existing OpenAPI generator or documentation-sync convention was found. Consequently, source/output paths and CI integration below are **proposed assumptions**, not confirmed repository facts or user decisions.

### Repository fit and proposed components

The project already uses Java 17, Maven, JUnit 5, and a read-only GitHub Actions workflow that runs `mvn --batch-mode verify`. A small Java command-line utility tested with JUnit is the closest fit to those conventions and avoids establishing an unrelated runtime or toolchain. The existing Maven dependencies do not include an OpenAPI parser, so robust OpenAPI parsing/validation would require an explicitly version-pinned parser dependency; its exact library and version are implementation choices to confirm, not existing project capabilities.

**Proposed path defaults (not confirmed):** use `docs/openapi.yaml` as the authoritative source and `docs/api-reference.md` as the generated artifact. The input must be supplied/approved independently: creating or changing the OpenAPI definition is explicitly outside DOCSYNC-1. If no authoritative definition is made available, the generator/check cannot complete successfully and CI integration cannot yet be made green.

```mermaid
flowchart LR
    Spec[Authoritative OpenAPI file<br/>proposed: docs/openapi.yaml] --> Parser[Java CLI<br/>parse and validate]
    Parser --> Model[Canonical endpoint model]
    Model --> Renderer[Deterministic Markdown renderer]
    Renderer -->|generate only| Output[Generated API reference<br/>proposed: docs/api-reference.md]
    Renderer -->|check only: compare bytes| Compare[Drift comparator]
    Output --> Compare
    Compare -->|match / exit success| Pass[Current]
    Compare -->|mismatch / nonzero + guidance| Stale[Stale reference]
    Tests[Maven JUnit tests] -. verify behavior .-> Parser
    Tests -. verify behavior .-> Renderer
    Tests -. verify behavior .-> Compare
    CI[GitHub Actions<br/>read-only contents permission] -->|proposed: mvn --batch-mode verify| Check[check command in verify lifecycle]
    Check --> Parser
```

| Component | Responsibility and interface |
|---|---|
| OpenAPI source | Supplies the single authoritative definition. Its actual path, format (YAML/JSON), version, and ownership have not been confirmed. |
| `ApiReferenceTool` (proposed Java CLI) | Exposes separate `generate` and `check` modes. Accepts explicit input/output paths or uses agreed defaults; returns a success/non-success process exit code and actionable diagnostics. The command shape and class/package are not established conventions yet. |
| Parser/validator | Parses the OpenAPI document and validates it before rendering. Exposes only endpoint path, HTTP method, and summary to the renderer; unrelated schema details are not emitted. A missing summary on an operation should be treated as an actionable validation error rather than silently fabricated (assumption needed because OpenAPI can permit omitted summaries while DOCSYNC-AC-01 requires one). |
| Canonical endpoint model and renderer | Produces a clearly labeled Markdown API-reference section from every parsed operation. Sort paths lexicographically and methods in a fixed order, use stable escaping/formatting, UTF-8 and LF line endings, and exclude timestamps or environment-specific values so equivalent input always renders identical bytes. |
| Reference target | A dedicated generated file, proposed as `docs/api-reference.md`, rather than a generated region mixed into manually maintained docs. Updating that one target leaves all other documentation untouched. |
| Drift check | Renders in memory and byte-compares the result with the checked-in target. It reports a current reference on equality and a stale/missing reference on mismatch, with the exact regeneration guidance. It does not write output, temporary files, or repository state. |
| Maven/JUnit and GitHub Actions | Unit/integration tests exercise the utility with temporary fixtures. Proposed CI integration makes the read-only check part of the existing `verify` lifecycle while retaining the current workflow command and its `contents: read` permission. This integration is a recommendation, not an existing configured behavior. |

### Data flow, interfaces, and operations

For either mode, the utility reads the selected OpenAPI source, parses and validates it, constructs a canonical endpoint list, and renders the complete output in memory. Rendering includes every operation's path, method, and summary, with an explicit generated-reference heading/marker. Paths and methods are ordered independently of parser/map iteration so the output is repeatable. Method labels use a documented consistent case; Markdown-special characters in summaries are escaped without losing their meaning as displayed text.

In `generate` mode, write only the generated reference target after parsing, validation, and rendering all succeed. To avoid truncating a valid prior reference on an I/O failure, write to a temporary file in the target directory and replace the target only after the completed output is ready. This is a write to the generated target only; never rewrite or clean up unrelated docs. If the generated bytes equal existing bytes, generation may leave the file untouched. Atomic replacement behavior depends on filesystem support and should have a safe fallback that does not delete the old reference before replacement succeeds.

In `check` mode, perform no writes at all—including no temporary files—and compare rendered bytes directly with the checked-in reference. A mismatch (including a missing output target) is stale, yields a nonzero exit status, and prints the approved regeneration command. Equality yields success. The check must not invoke generation mode, stage files, commit, push, or use credentials. A missing or invalid source fails before either comparison or update and provides a path/validation-specific explanation; in generation mode an existing reference remains byte-for-byte unchanged.

**Proposed invocation/integration assumption:** define a Maven-executable `check` entry point attached to `verify`, so the existing CI workflow's `mvn --batch-mode verify` automatically performs the read-only drift check. Document a corresponding explicit local `generate` invocation as the regeneration instruction printed by stale checks. The current POM's `exec-maven-plugin` is already configured for the dashboard server, but there is no documentation-tool execution or command; how the utility is launched (dedicated plugin execution, plugin goal, or another Maven wrapper) remains a design/implementation decision. CI's current read-only repository permission is a suitable boundary; no write token or publishing step is needed.

### Security, error handling, testability, and boundaries

Treat the OpenAPI file as repository input: parse it as data, do not execute content, resolve external references over the network, or include secrets. Network resolution should be disabled unless later explicitly required and reviewed. Diagnostics should identify the source path and actionable parse/validation issue without dumping environment variables or unrelated document contents. The utility needs no server endpoint, database, external service, commit/push capability, or CI write permission.

Validation and rendering must complete before a generation write is attempted. Missing input, malformed/unsupported OpenAPI input, missing required operation summary, and read/write failures produce a nonzero result and concise corrective guidance. In every source parse/validation failure, preserve any existing output. A stale check is an expected failure state, distinct from an invalid-input/tool failure, but both must fail CI so stale or unprocessable documentation cannot pass silently.

Use the established Maven/JUnit 5 test stack and isolated temporary directories/fixtures; do not make test runs depend on repository mutation or network access. The DOCSYNC-AC-07 suite should test:

- valid definitions with multiple paths and methods generate a clearly labeled reference containing every path, method, and summary;
- repeated generation from unchanged input yields byte-for-byte identical output, including stable ordering and line endings;
- a deliberately changed expected reference is detected as stale, reports regeneration guidance, and remains unchanged after check;
- missing and invalid source inputs report actionable failures and preserve a pre-existing reference byte-for-byte;
- successful update changes only the designated target, leaving neighboring/manual documentation unchanged.

The final item directly verifies the DOCSYNC-AC-06 boundary in addition to the four required test categories. Avoid adding unrelated endpoints, schema prose, API behavior changes, automated publishing, or documentation cleanup. No implementation or tests have been run as part of this architecture phase.

### Acceptance-criteria trace

| Requirement | Architectural path |
|---|---|
| DOCSYNC-AC-01 — Generate endpoint reference | Parse all OpenAPI operations; render each path, method, and summary in one clearly identified generated reference. An operation with no summary is an actionable validation issue rather than fabricated content. |
| DOCSYNC-AC-02 — Deterministic output | Canonical path/method ordering, stable escaping/template, UTF-8/LF, and no timestamps/environment data; assert byte-for-byte repeat generation in tests. |
| DOCSYNC-AC-03 — Stale check guidance | In-memory rendering and byte comparison; mismatch or missing target returns nonzero and prints the agreed regeneration command. |
| DOCSYNC-AC-04 — Read-only check | Dedicated check path only reads input/target and compares in memory; no temp files, writes, commits, pushes, or credentials. Verify the target and surrounding files remain unchanged in tests. |
| DOCSYNC-AC-05 — Safe input errors | Parse/validate before writing; missing/invalid input returns a path-specific actionable diagnostic and cannot overwrite the existing target. |
| DOCSYNC-AC-06 — Preserve other documentation | Write a dedicated generated artifact only; test that updating it leaves other docs unchanged. |
| DOCSYNC-AC-07 — Required test coverage | JUnit tests cover successful generation, deterministic unchanged output, stale detection, missing input, and invalid input; add a preservation check for unrelated docs. |

### Assumptions, trade-offs, uncertainty, and scope

- **Path assumptions only:** `docs/openapi.yaml` and `docs/api-reference.md` are proposed defaults, not confirmed source/output locations. The source definition itself was not found and its creation is out of scope.
- **CI assumption only:** attaching the read-only check to Maven `verify` is recommended because that is the existing CI entry point; no workflow change has been made. This check will need an authoritative OpenAPI input before it can pass in CI.
- **Format/tool assumption:** Markdown and a Java-compatible OpenAPI parser fit the existing documentation/build ecosystem, at the cost of a new parser dependency and maintenance. Exact output format and parser/version remain for approval.
- **Validation assumption:** each endpoint operation needs a summary to satisfy the requirement; report missing summaries rather than infer one.
- **Isolation trade-off:** a standalone generated file is less seamless than inserting a generated block into an existing API guide, but strongly reduces the risk of altering manually authored documentation.
- **In scope:** generator/check behavior, deterministic rendering, safe target update, read-only drift reporting, tests, and proposed CI integration.
- **Out of scope:** creating or changing the authoritative OpenAPI definition; changing API implementation; unrelated documentation generation; auto-commits, pushes, or deployments.
- **Unresolved before implementation:** confirm authoritative source path/definition and version, reference destination and format, and whether the proposed Maven `verify` integration/command is desired. “Go forward” authorizes this architecture draft with assumptions; it does not resolve these decisions or establish Jira/MCP provenance.

**Approval request:** Please review the DOCSYNC-1 architecture and confirm or revise the proposed source/output paths, Markdown format, and read-only Maven `verify` integration. I am pausing here for human approval; design review has not started.

## US-105: Configure the simulated test run duration

**Source and approval:** Based on the user-approved US-105 section of `requirements.md` (FR-12, AC-20 through AC-25). This is a backend startup-configuration change only. It does not change the browser UI, `/api/health`, `/api/runs`, `/api/runs/{id}`, the report format, `PORT`/port-argument handling, or the simulated nature of runs, and it adds no dependency. Code facts below come from inspecting `DashboardServer.java`, `ExecutionService.java`, `ExecutionServiceTest.java`, and `README.md`.

### Current state and key constraint

`DashboardServer.main` parses the port (argument 0, else `PORT`, default 8080), then runs `new DashboardServer(port, new ExecutionService())` and `start()`. `ExecutionService()` hard-codes `Duration.ofMillis(1400)`, and a package-private `ExecutionService(Duration)` constructor already exists and is used by tests. Note that the `DashboardServer` constructor calls `HttpServer.create(new InetSocketAddress(port), 0)`, which binds the port, not just `start()`. "Before the server listens" therefore means validation must run **before `new DashboardServer(...)`**, not merely before `start()`.

```mermaid
flowchart LR
    Env[RUN_DURATION_MS env var] -->|raw String or null| Parser[RunDurationConfig.parse]
    Parser -->|valid or unset/blank: Duration| Main[DashboardServer.main]
    Parser -->|invalid: IllegalArgumentException with standard message| Main
    Main -->|invalid| Stderr[System.err message, System.exit 2, no server created]
    Main -->|valid| Exec[new ExecutionService Duration]
    Exec --> Server[new DashboardServer, start]
    Server -->|POST /api/runs| Sched[scheduler.schedule after runDuration]
    Sched -->|GET /api/runs/id| Status[In Progress, then Passed/Failed]
```

### Components touched

| Component | US-105 responsibility |
|---|---|
| `RunDurationConfig` (new, package-private final class in `org.example.dashboard`) | Pure parsing and validation. Exposes `static Duration parse(String raw)` and the constant message `RUN_DURATION_MS must be a whole number from 100 to 10000.` It does not read the environment, print, or exit, so unit tests need no server and no `System.exit`. |
| `ExecutionService` | Unchanged run logic. Introduce a package-private `static final Duration DEFAULT_RUN_DURATION = Duration.ofMillis(1400)` and have the public no-arg constructor delegate to it, so 1400 has a single source of truth shared with the parser. The existing package-private `ExecutionService(Duration)` constructor is the injection point; it is reachable from `DashboardServer.main` because both classes share the package, so it need not become public. Add a package-private read-only accessor `Duration runDuration()` (design-review decision D1) so tests can assert the AC-20 default, the no-arg-constructor delegation, and the parsed value deterministically; it has no effect on run behavior. |
| `DashboardServer.main` | Only place that reads `System.getenv("RUN_DURATION_MS")`, calls the parser, and on failure writes to stderr and exits. Passes the resulting `Duration` to `new ExecutionService(duration)`. The `DashboardServer` constructor, `start()`, handlers, and API are untouched. |
| `README.md` | Add a `RUN_DURATION_MS` note directly beside the existing `PORT` sentence (line 36): unit (ms), default 1400, valid range 100-10000 whole numbers, that an invalid value stops startup with an error, a concrete example such as `RUN_DURATION_MS=500 mvn exec:java`, and a caution that reloading or closing the page during a run leaves that case showing "In Progress" (existing behavior, made more likely by longer durations; recovery is a separate follow-up story, decision D4) (AC-25). |
| `src/test/java` | New `RunDurationConfigTest`; additions to `ExecutionServiceTest`; new `StartupConfigurationTest` holding the two subprocess startup tests (decision D2, below). |

### Parsing and validation unit (`RunDurationConfig.parse`)

Input is `String raw` (possibly `null`). Output is a `Duration` or an `IllegalArgumentException` whose message is exactly the standard message. Rules, applied in order:

1. `null` returns `DEFAULT_RUN_DURATION`.
2. `s = raw.strip()`. If `s` is empty (so empty and whitespace-only inputs) return `DEFAULT_RUN_DURATION`. `strip()` is used rather than `trim()` so "blank" matches `String.isBlank()` Unicode whitespace semantics (confirmed decision D3; the requirements now say "stripped"). A value surrounded by an EM SPACE (U+2003) is accepted; a non-breaking space (U+00A0) is not whitespace under `strip()`, so it remains and causes rejection, as does a zero-width space.
3. Reject unless `s.length()` is 3 to 5 and every character is an ASCII digit `'0'..'9'`. Do not use `Character.isDigit`, which accepts non-ASCII Unicode digits that `Integer.parseInt` would also accept. This rejects signs, decimals, `1e3`, text, and embedded whitespace.
4. Reject if `s.charAt(0) == '0'` (leading zeros such as `0100`). With no leading zero, a 3-digit value is automatically at least 100.
5. `Integer.parseInt(s)` is now provably safe: at most 5 ASCII digits cannot overflow `int`, so no `NumberFormatException` can occur. Values such as `99999999999` fail at the length check and never reach parsing. Reject if the value is greater than 10000.
6. Return `Duration.ofMillis(value)`.

All rejection paths throw the same exception type and the same message; the invalid input is deliberately not echoed. The method has no side effects and no timing dependence, so it is fully deterministic.

### Where validation and exit happen, and how the error reaches stderr

In `main`, after the existing (unchanged) port parse and before any `DashboardServer` or `ExecutionService` is constructed:

```
Duration runDuration;
try {
    runDuration = RunDurationConfig.parse(System.getenv("RUN_DURATION_MS"));
} catch (IllegalArgumentException error) {
    System.err.println(error.getMessage());
    System.exit(2);
    return;
}
```

- The message goes to `System.err` through `println`, satisfying the "shown to the person starting the server" assumption and keeping stdout for the existing "running at" line. The catch wraps only the parser call, so unrelated failures are not mislabeled.
- Exit code 2 is a design choice (any non-zero value satisfies AC-22). It differs from the JVM's code 1 for an uncaught exception, so a configuration error is distinguishable.
- `System.exit` appears only in `main`. At that point nothing has been bound or scheduled: no `HttpServer`, no `ExecutionService` scheduler thread, and no shutdown hook, so exit is clean and the server never listens.
- Because the check sits after port selection, it applies identically whether the port came from the CLI argument or `PORT` (the earlier assumption). Existing port parsing is left exactly as is, including its current behavior on a malformed port. Only the numeric parse of the port (`Integer.parseInt`) precedes duration validation: if the port is non-numeric and `RUN_DURATION_MS` is also invalid, the existing port `NumberFormatException` happens first. An out-of-range port (`new InetSocketAddress`) or an in-use port (`HttpServer.create`) fails inside `new DashboardServer(...)`, after duration validation, so an invalid duration is reported first in those cases (and the subprocess ordering test below relies on this). This ordering keeps port behavior unchanged and is recorded as a decision.
- The value is read once at startup and the `Duration` is stored in the final field `ExecutionService.runDuration`, so it cannot change while the server runs.

### Data flow

1. The operator starts the JVM, optionally with `RUN_DURATION_MS` set (and a port argument or `PORT`).
2. `main` resolves the port as before, then reads `RUN_DURATION_MS` and calls `RunDurationConfig.parse`.
3. Invalid: print the standard message to stderr, exit 2. No bind, no threads.
4. Valid, unset, or blank: `new ExecutionService(duration)` then `new DashboardServer(port, service)` then `start()`; the "running at" line is printed as before.
5. For each `POST /api/runs`, `ExecutionService.start` records the run as In Progress and schedules completion after `runDuration.toMillis()`. `GET /api/runs/{id}` reports In Progress until the scheduled task sets `completedAtNanos`, then Passed or Failed with `durationMs` measured from run start to completion. Request/response shapes are unchanged.

### Testability, including AC-23

**AC-24 unit tests (`RunDurationConfigTest`, plain JUnit 5, no server, no `System.exit`):**

| Group | Inputs | Expected |
|---|---|---|
| Default | `null`, `""`, `"   "`, tab/newline-only | 1400 ms |
| Boundaries and strip | `"100"`, `"10000"`, `" 500 "`, `"200"`, `" 500 "` (EM SPACE) | 100, 10000, 500, 200, 500 ms |
| Rejected | `"99"`, `"10001"`, `"0"`, `"0100"`, `"+100"`, `"-5"`, `"100.5"`, `"1e3"`, `"abc"`, `"1 000"`, `"99999999999"`, `"2147483648"`, a very long digit string, a non-ASCII digit string, `" 500"` / `"500 "` (NBSP) | `IllegalArgumentException` with exactly the standard message (assert no other exception type) |

Write non-ASCII inputs with `\u` escapes. Assert the message against the **literal** text `RUN_DURATION_MS must be a whole number from 100 to 10000.`, not the `RunDurationConfig` constant, so an accidental edit of the message is caught (design-review F10). Also assert `parse(null)` equals `ExecutionService.DEFAULT_RUN_DURATION` and is 1400 ms.

Parameterized tests are fine only if no new dependency is needed; the repository's existing JUnit setup should be checked at implementation time. A loop over an array within plain `@Test` methods avoids the question.

**Deterministic configuration checks (`ExecutionServiceTest`, decision D1):** using the package-private `runDuration()` accessor, assert `new ExecutionService().runDuration()` equals `Duration.ofMillis(1400)` (AC-20 and the no-arg delegation) and `new ExecutionService(RunDurationConfig.parse("200")).runDuration()` equals `Duration.ofMillis(200)` (parser-to-service wiring). These involve no timing and cannot flake.

**AC-23 timing smoke test with tolerance (`ExecutionServiceTest`):** construct `new ExecutionService(RunDurationConfig.parse("200"))`, call `start`, and:

- assert the returned status is `In Progress` immediately (this is the deterministic In Progress check; no separate "about 50 ms later" assertion is required, and if one is kept it must be conditional on a measured elapsed time below about 150 ms);
- poll `find(id)` every ~10 ms with a generous overall deadline (for example 3 s) rather than a single fixed `sleep`, to avoid the flakiness in the existing sleep-based tests;
- once the status is Passed or Failed, assert `durationMs` is **at least 200** and **below 1000 ms**.

The lower bound is exact and reliable: the run's start nanotime is captured before the schedule call and a `ScheduledThreadPoolExecutor` never fires early, so elapsed time is at least 200 ms and the truncated millisecond value is at least 200 (no truncation tolerance is needed). The 1000 ms upper bound leaves 800 ms of scheduler/CI jitter headroom and is clearly below the 1400 ms default, so the smoke test fails if a default-length run is observed. It is the only timing-sensitive assertion; no assertion depends on exact equality (per the requirements' tolerance note). The earlier draft's 1500 ms upper bound was above the 1400 ms default and could not distinguish the two, so it was corrected (design-review US105-F1). The test uses the service-reported duration, so it does not depend on `Thread.sleep` accuracy for correctness.

**Startup behavior (two subprocess tests, decision D2; own class `StartupConfigurationTest`):** because `System.exit` lives only in `main`, AC-21 to AC-23 end to end are checked by launching the real `main` in a child JVM. No new dependency is needed. Both tests follow these conditions:

- locate the classes from `DashboardServer.class.getProtectionDomain().getCodeSource()`, not a relative `target/classes`, and launch `java.home/bin/java` with `-cp` that location;
- pass the port as an explicit argument (so `PORT` is never read) and set or remove `RUN_DURATION_MS` explicitly in `ProcessBuilder.environment()`;
- send child output to temp files (or drain it), use `waitFor(timeout)` (about 20-30 s) and `destroyForcibly()` in a `finally`, with a JUnit `@Timeout` as a second guard, because a regression that lets the child start would never exit;
- assert the message as a trimmed stderr line equal to the literal standard message (not whole-stream equality, since `JAVA_TOOL_OPTIONS` may add a line, and not the platform line separator).

1. **Invalid value with an occupied port (AC-22 ordering).** The test opens and holds a `ServerSocket(0)`, starts the child with that port and `RUN_DURATION_MS=abc`, and asserts: exit code 2, the exact message on stderr, no `BindException` or stack trace on stderr, and no "running at" on stdout. If validation ran after the bind, the child would fail with a bind error instead, so this proves validation precedes the bind without a "find a free port, close it, reuse it" race.
2. **Valid value, end to end (AC-21, AC-23 wiring).** The child is started with port `0` and `RUN_DURATION_MS=" 300 "`; the test reads the bound port from the "running at" stdout line (with a timeout), starts a run with `POST /api/runs`, polls `GET /api/runs/{id}` to completion, asserts the reported `durationMs` is at least 300 and below 1000 ms, then destroys the child. This also shows that the whitespace-stripped value reached `ExecutionService` through `main`.

Spawning a JVM is slower and environment-sensitive. Keeping both tests in their own class means they can be isolated or dropped if CI proves unstable (the unit tests and the deterministic accessor checks stand on their own).

**Manual verification (decision for design-review US105-F7):** because the README's main run path is `mvn exec:java`, which runs `main` inside the Maven JVM where `System.exit(2)` ends the Maven process, record at verification time the exit code and stderr of `RUN_DURATION_MS=abc mvn exec:java` and of `RUN_DURATION_MS=abc java -cp target/classes org.example.dashboard.DashboardServer 0`. This is an unverified assumption until then.

Run `mvn test` for Java regressions. No browser-side change exists, so no browser checklist is needed. No tests have been run in this architecture phase.

### Security, error handling, and boundaries

- `RUN_DURATION_MS` is operator-controlled process configuration, not remote input. No HTTP request can set or change it, and no new endpoint or field exposes it.
- Strict allow-list parsing (ASCII digits, 3-5 characters) means no exception escapes from parsing, no integer overflow, and no ambiguous numeric forms. The bounded range (100 to 10000 ms) prevents zero or negative delays and caps how long a simulated run can hold In Progress state.
- The raw value is not echoed in the error, which avoids reflecting arbitrary environment content into logs and keeps the message fixed and testable.
- No secrets, files, network calls, or persistence are involved. Runs remain simulated; the duration only shifts the scheduled completion time.
- Failure is fail-fast and explicit: invalid configuration never silently becomes the default and never produces a running server.

### Acceptance-criteria trace

| Criterion | Architectural path |
|---|---|
| AC-20 | `parse` returns `DEFAULT_RUN_DURATION` (1400 ms) for `null`, empty, or whitespace-only input; `ExecutionService()` delegates to the same constant. Covered by unit tests and by the deterministic `runDuration()` accessor check on `new ExecutionService()`. |
| AC-21 | Digits-only 3-5 char values from `100` through `10000` are accepted; `strip()` makes `" 500 "` valid as 500. Unit tests cover 100, 10000, `" 500 "`, and EM SPACE; the valid-value subprocess test covers the path through `main`. |
| AC-22 | All other inputs hit the single rejection path in `parse`; `main` prints the exact message to stderr and calls `System.exit(2)` before `new DashboardServer` binds the port; no fallback exists. The `99999999999` case is rejected by the length check before `parseInt`. Unit tests assert the literal message and exception type; the occupied-port subprocess test asserts exit code 2, stderr, no `BindException`, and no listening, which proves the ordering. |
| AC-23 | `main` passes the parsed `Duration` to `ExecutionService`, whose scheduler completes runs after that delay. Verified by the `runDuration()` accessor check, the timing smoke test (at least 200 and below 1000 ms), and the valid-value subprocess test. |
| AC-24 | New `RunDurationConfigTest` covers default (unset and blank), both boundaries, strip (including EM SPACE), and the rejected set (including NBSP). |
| AC-25 | README line beside the existing `PORT` note documents unit, default, range, failure on invalid values, a concrete example, and the stale "In Progress" caution. |

### Assumptions, trade-offs, risks, and scope

- **Separate parser class versus inline code in `main`:** a small package-private class adds one file but makes validation testable without `System.exit`, the environment, or a server. Inline code would need process-level tests for every rejected value. Chosen: separate class.
- **Parser throws versus returns a result type:** an `IllegalArgumentException` with the fixed message is the simplest fit with existing conventions (the codebase already uses it for validation). A dedicated exception or result type is more explicit but adds ceremony for one caller.
- **Manual digit scan versus `Integer.parseInt`/regex only:** the length cap makes parsing overflow-proof without relying on catching `NumberFormatException`, and avoids Unicode-digit acceptance. Slightly more code than a regex, but every rule is explicit and individually testable.
- **`strip()` versus `trim()`:** `strip()` aligns "blank" with `isBlank()` semantics, so a Unicode-whitespace-only value uses the default. A value containing other invisible characters (for example a zero-width space) is rejected, which is the safer outcome.
- **Exit code 2 and stderr:** a design detail within the approved "non-zero exit code"; stderr is confirmed here as the output stream.
- **Risk, startup order:** if a future change moves validation after `new DashboardServer`, the port would already be bound. Mitigate with a code comment at the call site and the occupied-port subprocess test, which fails if the bind happens first.
- **Risk, timing tests:** AC-23 is scheduler-based. Mitigated by deterministic accessor assertions for the configured value, polling with a deadline, an exact lower bound (never early), and an upper bound of 1000 ms that is below the 1400 ms default. The existing sleep-based tests are not changed here.
- **Risk, subprocess tests:** depend on a launchable JVM and classes located from the code source; they carry hang/flake risk, mitigated by the timeout, `destroyForcibly`, output draining, explicit environment, and a separate test class. Drop or isolate them if CI proves flaky, and say so in the verification report.
- **Risk, `mvn exec:java` (manual verification item):** `System.exit(2)` runs inside the Maven JVM on that path; confirm exit code and stderr manually at verification.
- **Known limitation, existing and out of scope (decision D4):** `app.js` stores "In Progress" before a run and nothing resets it on page load, so reloading or closing the page mid-run leaves the case showing "Running...". Longer durations make this more likely. US-105 makes no UI change; the README carries a caution and a follow-up story will be raised for recovery.
- **Residual behavior, not changed:** a malformed port still fails with the existing unhandled `NumberFormatException`, and an `ExecutionService` created by other code paths still defaults to 1400 ms.
- **In scope:** `RunDurationConfig`, the `DEFAULT_RUN_DURATION` constant, the `runDuration()` accessor, the `main` wiring, tests (including the two subprocess tests), and the README note.
- **Out of scope:** UI, API, report-format, per-run or per-case duration, a CLI argument or config file for duration, runtime changes, real browser automation, stale "In Progress" recovery, and new dependencies. The only requirement wording change is "stripped" instead of "trimmed" (decision D3), plus the README example and caution in AC-25.

**Approval status:** Approved by user. The user approved this architecture directly in the working session; this was relayed to the design-review agent by the coordinating agent, so that agent did not receive it first-hand. Design review decisions D1-D5 (see `design-review.md`) are reflected in this section: the `runDuration()` accessor and corrected AC-23 test (D1), two subprocess startup tests (D2), `strip()` with EM SPACE and NBSP cases (D3), the README caution and follow-up story (D4).

## US-107: List recent runs with GET /api/runs

**Source and approval:** Based on the user-approved US-107 section of `requirements.md` (FR-13 to FR-16, US-107 AC-01 to AC-07), including the approved defaults for its four open questions (tie-break, empty or repeated `limit`, `limit` number formats, HEAD and OPTIONS). This is a backend-only, read-only change: no UI, no new dependency, no change to `POST /api/runs`, `GET /api/runs/{id}`, `/api/health`, static files, `PORT`, or `RUN_DURATION_MS`. Code facts below come from `DashboardServer.java`, `ExecutionService.java`, `RunDurationConfig.java`, `DashboardServerTest.java`, and `README.md`.

### Current state and key constraints

- `ExecutionService` keeps runs in `ConcurrentMap<String, Run> runs` (a `ConcurrentHashMap` keyed by random UUID). It exposes only `start` and `find`. `Run` is a private nested class with final `startedAt` (`Instant`, serialized as `timestamp`) and final `startedAtNanos`, plus a volatile `completedAtNanos` that the scheduler thread sets. `Run.toResult()` reads `completedAtNanos` once into a local, so each item it builds is internally consistent, and for an unfinished run it computes a live `durationMs`.
- `DashboardServer.handle` routes `path.equals("/api/runs")` to `handleStartRun`, which rejects any non-POST with `sendMethodNotAllowed(exchange, "POST")`. That is why `GET /api/runs` returns 405 today. `/api/runs/{id}` goes to `handleGetRun`; `jsonRun(RunResult)` is the single serializer for one run, `quote` does JSON escaping, and `sendJson` adds `Cache-Control: no-store` and `Content-Type: application/json; charset=utf-8`.
- `sendMethodNotAllowed(exchange, expected)` sets `Allow` to `expected` and builds the message "Use the {expected} method for this endpoint." Passing `"GET, POST"` would give an awkward message, so it needs a small overload (see below). `/health`, `/api/runs/{id}`, and static files keep calling the existing form unchanged.
- The existing form parser (`parseForm`) uses `URLDecoder.decode`, which throws `IllegalArgumentException` on malformed percent escapes, and `handle` turns any `IllegalArgumentException` into a 400 that echoes the exception message. The limit parser must not rely on that path, or `limit=%zz` would return the decoder's message instead of the required exact text.
- The store is never evicted, so a list request that scans the whole store costs time proportional to every run since startup. The response size is still bounded by the 100-run cap.
- The server binds the wildcard address with no authentication (demo scope). Until now a run was only reachable by its unguessable UUID; listing makes recent run IDs, case IDs, and titles readable by anyone who can reach the port. The story accepts this, so the design notes it as a residual exposure rather than adding a control.

```mermaid
flowchart LR
    Client[HTTP client] -->|GET /api/runs?limit=N| Handler[DashboardServer.handle]
    Handler -->|path equals /api/runs| Router{Method}
    Router -->|POST, unchanged| Start[handleStartRun]
    Router -->|GET| List[handleListRuns]
    Router -->|other incl. HEAD, OPTIONS, PUT, DELETE| Deny[405 + Allow: GET, POST]
    List -->|raw query string| Query[RunListQuery.parseLimit]
    Query -->|invalid| Bad[400 limit must be a whole number from 1 to 100.]
    Query -->|limit 1..100, default 20| Svc[ExecutionService.listRecent]
    Svc -->|snapshot of runs.values, sort by startedAt then sequence, newest first, take limit| Items[List of RunResult]
    Items --> Ser[jsonRuns reusing jsonRun]
    Ser -->|200 runs array| Client
    Start --> Svc2[ExecutionService.start]
```

### Components and the change in each

| Component | US-107 change |
|---|---|
| `ExecutionService` | Add `public List<RunResult> listRecent(int limit)`. Add constants `DEFAULT_LIST_LIMIT = 20` and `MAX_LIST_LIMIT = 100` (package-private). Add a per-run `sequence` (a `long` assigned from a new `AtomicLong` in `start()` and stored as a final field in `Run`). `listRecent` rejects `limit` outside 1..`MAX_LIST_LIMIT` with `IllegalArgumentException` as a defensive check (the HTTP layer already validates, so this is not the user-facing error path). Add a package-private constructor `ExecutionService(Duration, java.time.Clock)` as a test seam (design-review D1): `start` captures `startedAt` with `Instant.now(clock)`. The public no-argument constructor and the existing `ExecutionService(Duration)` constructor use `Clock.systemUTC()`, so production behavior is unchanged. `find`, scheduling, and `toResult` are otherwise unchanged. |
| `RunListQuery` (new, package-private final class in `org.example.dashboard`) | Pure query parsing, in the style of `RunDurationConfig`: `static int parseLimit(String rawQuery)` returns the default 20 when `limit` is absent and otherwise a value in 1..100, or throws `IllegalArgumentException` carrying the constant `INVALID_LIMIT_MESSAGE = "limit must be a whole number from 1 to 100."`. It takes the raw (still percent-encoded) query so `&` and `=` are split before decoding. No HTTP types, so it is unit-testable without a server. |
| `DashboardServer` routing | Replace the direct `/api/runs` to `handleStartRun` mapping with a small dispatcher `handleRuns`: GET calls `handleListRuns`, POST calls `handleStartRun`, anything else calls the new 405 overload with `Allow: GET, POST`. `handleStartRun` keeps its status code, body, size limit, and validation; its own POST check is removed (design-review D4/F5) because the dispatcher is its only caller and the stale `Allow: POST` text would otherwise be a second source of truth. `/api/runs/{id}`, `/api/health`, and static routing are untouched. A trailing-slash path `/api/runs/` is not `/api/runs`, so it still reaches `handleGetRun`: a GET returns 404 as today, and any other method returns 405 with `Allow: GET` from `handleGetRun` before the id check. |
| `DashboardServer.handleListRuns` (new) | Calls `RunListQuery.parseLimit(exchange.getRequestURI().getRawQuery())` inside its own `try/catch (IllegalArgumentException)` that answers `sendJson(400, jsonError(INVALID_LIMIT_MESSAGE))`. The catch wraps only the parse, so an unrelated exception is not mislabeled as a limit error. On success it calls `executionService.listRecent(limit)` and answers `sendJson(200, jsonRuns(items))`. It never reads the request body and never calls `start`, so the store cannot change. |
| `DashboardServer.jsonRuns` (new) | Builds `{"runs":[` + the existing `jsonRun(item)` for each item joined by `,` + `]}`. Reusing `jsonRun` guarantees the same fields, order, types, and escaping as `GET /api/runs/{id}` (FR-13). An empty list yields exactly `{"runs":[]}`. |
| `DashboardServer.sendMethodNotAllowed` | Add an overload that takes the `Allow` value and the message separately. The `/api/runs` 405 uses `Allow: GET, POST` and the text "Use the GET or POST method for this endpoint." For HEAD requests the overload sends the headers with no body (`sendResponseHeaders(405, -1)`) and writes nothing (design-review D2/F2). The existing one-argument form and its message stay for the other endpoints, so their responses are byte-for-byte unchanged. |
| `README.md` | Add a new "API" subsection (the README has none today; design-review D4/F4) documenting `GET /api/runs`: default 20, `limit` 1 to 100, newest first, the 400 error, and an example response (AC-07). Use a real response shape with the seven fields and at least one In Progress item. |
| `src/test/java` | New `RunListQueryTest`; additions to `ExecutionServiceTest` and `DashboardServerTest` (see Testability). |

### Interface and contract

| Request | Response |
|---|---|
| `GET /api/runs` | 200, `{"runs":[...]}`, at most 20 items, newest first. |
| `GET /api/runs?limit=N`, N a whole number 1 to 100 | 200, at most N items, the N newest. Fewer runs than N returns all of them. |
| `GET /api/runs` with no runs started | 200, `{"runs":[]}`. |
| `GET /api/runs?limit=<invalid>` | 400, exactly `{"error":"limit must be a whole number from 1 to 100."}`. |
| `POST /api/runs` | Unchanged (202 with the run, 400 for validation errors, 413 for oversized bodies, as today). A query string on a POST is ignored. |
| Any other method (PUT, DELETE, PATCH, HEAD, OPTIONS, ...) on `/api/runs` | 405, header `Allow: GET, POST`, JSON error body. |

Success and error bodies go through `sendJson`, so they carry `Content-Type: application/json; charset=utf-8`, `Cache-Control: no-store`, and the existing `X-Content-Type-Options` and `Content-Security-Policy` headers. Each list element has the fields `id`, `caseId`, `title`, `status`, `durationMs`, `timestamp`, `failureDetails`, in the same order and types as `GET /api/runs/{id}`. A run that is still In Progress is included with its live `durationMs` and an empty `failureDetails`.

### `limit` parsing rules (approved decisions 2 and 3)

`RunListQuery.parseLimit(rawQuery)` applies these rules in order:

1. `rawQuery` null or empty returns 20. Empty segments from `&&` or a leading or trailing `&` are skipped.
2. Split the raw query on `&`, then each segment on the first `=`. Percent-decode the name with `URLDecoder` using UTF-8 (so `%6Cimit` is `limit`). A segment whose name cannot be decoded (malformed escape) is skipped, as if it were not `limit` (design-review D3/F3). Names are case-sensitive: `Limit` and every other name are ignored, as are other parameters. Note: at HTTP level a malformed escape in the request line is expected to be rejected by the JDK's `java.net.URI` parsing with a bare 400 before the handler runs, and `HttpClient` cannot send one, so `%zz` is covered by `RunListQueryTest` only.
3. The first segment whose decoded name is `limit` decides the result; later `limit` segments are never examined. So `limit=5&limit=abc` returns 5 and `limit=abc&limit=5` returns 400.
4. A `limit` segment with no `=` (`?limit`) or an empty value (`?limit=`) is invalid. Decode the value with `URLDecoder`; a malformed escape (`%zz`) is invalid and is caught inside the parser, so the decoder's own message never reaches the client. Because `URLDecoder` turns `+` into a space, `limit=+5` becomes " 5" and is rejected; `limit=%2B5` becomes "+5" and is rejected; `limit=%35` becomes "5" and is accepted. This is standard query decoding and is not treated as a defect.
5. The decoded value must consist only of ASCII digits `'0'..'9'` (not `Character.isDigit`, which accepts other scripts). Anything else, including spaces, signs, decimals such as `2.5`, exponents, and text, is invalid.
6. Drop leading zeros. If nothing remains (`0`, `00`) the value is 0 and invalid. If more than 3 digits remain, the value is invalid without parsing; this catches `99999999999` and any very long digit string and means no `NumberFormatException` is possible. Otherwise parse the 1 to 3 remaining digits with `Integer.parseInt` and require 1..100. So `05` returns 5 and `000000000000000000005` returns 5.

Every rejection path throws the same exception with the same message, and the rejected input is not echoed in the response.

### Ordering, snapshot, and concurrency

`listRecent(limit)` does the following:

1. Copy `runs.values()` into a local `ArrayList`. `ConcurrentHashMap` iteration is weakly consistent: it never throws `ConcurrentModificationException`, and it may or may not include a run inserted during the copy. Because `start()` does `runs.put` before `schedule`, a run can never be listed in a half-built state, and the copy reads only fully constructed `Run` objects.
2. Sort the copy by `startedAt` descending, then by `sequence` descending (approved decision 1). Both keys are immutable fields set at construction. The sort must never use a mutable key such as `completedAtNanos`, `durationMs`, or `status`, because a key that changes during the sort can violate the comparator contract and make `List.sort` throw. `sequence` is unique, so the order is total and the result is stable and repeatable even when `Instant.now()` returns the same value for runs started in one clock tick.
3. Take the first `limit` entries and call `toResult()` on each. Each item is read at that moment, so a run that completes during the call is shown either In Progress or completed, never a mix. Different items may be evaluated a few microseconds apart, which is acceptable for a live view.

The insertion counter was chosen over `startedAtNanos` for the tie-break because it is unique (nano-time values can still collide on coarse clocks) and needs no overflow-safe subtraction. Sorting by `startedAt` first keeps list order consistent with the `timestamp` the client sees. Two runs started concurrently on different HTTP threads can obtain their `Instant` and their `sequence` in opposite orders by a few microseconds; the list then follows `timestamp`, which is the documented meaning of "newest". If the system clock is stepped backwards, `timestamp` order can differ from start order for runs on either side of the step; this is accepted for a demo and noted as a risk.

Cost is O(n log n) in the number of runs held, because the store has no ordered index and runs are never evicted. A bounded selection or a `ConcurrentSkipListMap` index keyed on (`startedAt`, `sequence`) would make listing O(limit), at the price of a second structure kept in sync with `runs`. Because eviction and persistence are out of scope and the app is a single-user demo, the simple sort is chosen. Revisit it if the store is expected to grow large.

The listing path holds no lock, writes nothing, and does not touch the scheduler, so it cannot delay run completion or alter the store (NFR "read-only", "safe while runs are started and completed concurrently").

### Data flow

1. A client sends `GET /api/runs` with an optional `limit`.
2. `handle` matches `/api/runs` on the path (the query string is not part of `getPath()`) and dispatches by method.
3. `handleListRuns` parses the limit from the raw query. Invalid input returns 400 immediately, before the service is touched.
4. `ExecutionService.listRecent` snapshots, sorts, and trims the runs and converts each to a `RunResult`.
5. `jsonRuns` serializes the items through the existing `jsonRun` and `quote`, and `sendJson` writes a 200 response. Any unexpected exception falls to the existing generic handler (500, fixed message), and an `IOException` is logged by class name only, as today.

### Security, input validation, and error handling

- The only input is the query string. It is parsed with an ASCII-digit allow-list and bounded to 3 significant digits before conversion, so there is no overflow, no unhandled exception, and no ambiguity between numeric forms. Unknown parameters are ignored and never reflected.
- Output values come from the existing run store (case ID and title are already length-limited to 40 and 120 characters and validated on `start`). They are escaped by the existing `quote` function, so they cannot break the JSON, and the existing `nosniff` and CSP headers still apply.
- The response size is capped by the 100-run limit: with titles at most 120 characters and a worst case of six-character `\uXXXX` escapes per control character, a response stays well under 200 KB. The request is a GET with no body, so the 4096-byte body limit is not relevant. The size of the query string is bounded by the JDK HTTP server's own request-line limits (not verified here; confirm during implementation or verification).
- 400 is returned only for an invalid `limit`. 405 is returned for non-GET/POST methods on this path, with the `Allow` header required by RFC 9110. 500 comes only from the existing generic handler.
- Residual exposure, accepted by the story and unchanged in scope: listing reveals recent run IDs, case IDs, and titles to anyone who can reach the server, whereas before a client had to know a run's UUID. The server has no authentication by design (see "Security and operational boundaries"). No new control is added; the README example should use sample data only.
- HEAD and OPTIONS return 405 literally (approved decision 4). This means no CORS preflight support, which is consistent with a same-origin dashboard. For HEAD, the existing one-argument error helper writes a body after `sendResponseHeaders`, which the JDK server is expected to reject (logged by the existing `IOException` catch). The new 405 overload therefore sends no body for HEAD (`sendResponseHeaders(405, -1)`, no write) and leaves the one-argument helper untouched (design-review D2). A HEAD test and an OPTIONS test confirm 405, `Allow: GET, POST`, and that the request completes without a hang; the observed stderr output is recorded at verification.

### Technology choices and trade-offs

| Choice | Reason | Cost |
|---|---|---|
| Service method `listRecent` plus unique insertion counter | Total, repeatable order with no new data store; keeps ordering logic next to the data it orders. | One more `long` per run and an `AtomicLong`. |
| Sort a snapshot instead of an ordered index | Smallest change, no second structure to keep in sync, correct under concurrent starts. | O(n log n) per request over a store that never shrinks. |
| New `RunListQuery` class versus inline parsing in the handler | Mirrors `RunDurationConfig`; every rule is testable without a server. | One extra small file. |
| Hand-rolled query split instead of reusing `parseForm` | `parseForm` throws decoder messages, last value wins, and treats a missing `=` as an empty value; US-107 needs first value wins, a fixed error text, and no leaks of internals. | A few lines of duplicated splitting logic. |
| Explicit `catch` around the parse instead of relying on the generic `IllegalArgumentException` to 400 mapping | The error text is exact and cannot be replaced by an unrelated exception message. | Slightly more code in the handler. |
| Reuse `jsonRun` for list items | One serializer, so list and single-run output cannot drift. | None. |
| Overload for the 405 helper instead of changing its signature | Other endpoints' 405 responses stay identical. | A second small helper. |

### Testability

All tests use the existing JUnit 5 and `HttpClient` setup, with a short run duration (as in `DashboardServerTest`); no new dependency is needed. No tests have been run in this architecture phase.

- **`RunListQueryTest` (pure):** absent or empty query returns 20; `limit=1`, `limit=100`, `limit=05`, and a long zero-padded 5 are accepted; `0`, `101`, `abc`, `2.5`, empty value, missing `=`, `+5`, `%2B5`, space (` 5` and `5%20`), `-1`, `1e1`, a non-ASCII digit, `99999999999`, `2147483648`, a very long digit string, and `%zz` each throw `IllegalArgumentException` with a message equal to the literal text `limit must be a whole number from 1 to 100.` (assert the literal, not the constant); `limit=5&limit=10` returns 5; `limit=abc&limit=5` is rejected; `limit=5&limit=abc` returns 5; unrelated parameters, `Limit=500`, and `&&` are ignored.
- **`ExecutionServiceTest`:** with no runs, `listRecent(20)` is empty; after starting several runs, results are in reverse start order (compare against the IDs returned by `start`); a test that builds `new ExecutionService(duration, fixedClock)` with a fixed `java.time.Clock` (every run gets the same `Instant`), starts several runs, and asserts strict reverse start order, which proves the `sequence` tie-break (design-review D1); starting a large batch in a tight loop (for example 50) and requesting all of them in reverse start order is kept only as a sanity check and does not prove the tie-break, because consecutive `Instant.now()` values often differ; `limit` smaller than, equal to, and larger than the number of runs; a mix of an unfinished and a finished run, including the In Progress status; `limit` 0, 101, and negative throw `IllegalArgumentException` (defensive check); listing does not change `find` results or the run count.
- **`DashboardServerTest` (HTTP, AC-06):** empty list body exactly `{"runs":[]}`; two runs listed newest first; each list element equals the `GET /api/runs/{id}` body for the same run when the run has completed (field names, types, and order); no `limit` returns at most 20 when 25 exist; `limit=3` returns 3; `limit=100` with more than 100 runs returns exactly 100 and the 100 newest; the invalid set `0`, `101`, `abc`, `2.5` returns 400 with the body exactly `{"error":"limit must be a whole number from 1 to 100."}`, plus the extra approved cases (empty, `+5`, `99999999999`, `05` accepted); a rejected request leaves the run count unchanged; `Content-Type` and `Cache-Control` headers on 200 and 400; PUT and DELETE return 405 with `Allow: GET, POST`, and so do HEAD and OPTIONS; `POST /api/runs` still returns 202 with the same body shape and a 400 for a bad outcome; `/api/health` and `GET /api/runs/{id}` still pass (the existing tests cover them and must stay green); a 405 on `/api/health` still has `Allow: GET`.
- **Concurrency smoke test (optional, low flake risk):** several threads start runs while another thread repeatedly lists; assert no exception and that every response parses to at most the limit. Keep it bounded in time. It supports the NFR but is not tied to one acceptance criterion.
- **Verification:** run `mvn test`. Record the HEAD behavior observation noted above.

### Acceptance-criteria trace

| Criterion | Architectural path |
|---|---|
| US-107 AC-01 — Newest first | `GET` dispatches to `handleListRuns`; `ExecutionService.listRecent` sorts a snapshot by `startedAt` descending then `sequence` descending; `jsonRuns` returns 200 with the `runs` array in that order. Tests: `ExecutionServiceTest` order cases; `DashboardServerTest` two-run order; `ExecutionServiceTest` fixed-clock tie-break case; each element matches `GET /api/runs/{id}` via the shared `jsonRun`. |
| US-107 AC-02 — Empty list | An empty snapshot yields an empty `List`; `jsonRuns` emits exactly `{"runs":[]}` with status 200. Tests at service and HTTP level. |
| US-107 AC-03 — Default and custom limit | `RunListQuery.parseLimit` returns 20 when `limit` is absent and N for a valid value; `listRecent` takes the first N after sorting, so it returns the N newest or all runs when fewer exist. Tests: 25 runs with no limit returns 20; `limit=3`; `limit=1`; `limit=100`; `limit=05`; limit larger than the run count. |
| US-107 AC-04 — Invalid limit | `parseLimit` rejects `0`, `101`, `abc`, `2.5` (and empty, valueless, signed, spaced, malformed-escape, and overflow forms; the malformed-escape form is tested at parser level only, since the JDK handles it before the handler at HTTP level) with one exception and one message; `handleListRuns` catches it and sends 400 with the exact body from `jsonError`. The service is never called, so the store is unchanged. Tests assert the literal body. |
| US-107 AC-05 — Methods | `handleRuns` sends POST to the unchanged `handleStartRun`, and every other method to the 405 overload with `Allow: GET, POST` (including PUT, DELETE, HEAD, OPTIONS). Tests: PUT and DELETE for the criterion, HEAD and OPTIONS for the approved decision, and a POST regression test that checks 202, the body shape, and a validation 400. |
| US-107 AC-06 — Unit tests | The Testability section lists tests for ordering, default and custom limit, each invalid limit, the empty list, and the 405 case at the pure, service, and HTTP levels. |
| US-107 AC-07 — README | The README change documents `GET /api/runs`, `limit`, the 400 error, and an example response whose items use the seven fields. The example must match the real output shape; a reviewer should compare it with an actual response. |

| Requirement | Component |
|---|---|
| FR-13 List recent runs | `ExecutionService.listRecent`, `DashboardServer.handleListRuns`, `jsonRuns` reusing `jsonRun` |
| FR-14 Limit | `RunListQuery.parseLimit` (default 20, 1..100), `listRecent(limit)` |
| FR-15 Invalid limit | `RunListQuery.parseLimit` rejection, 400 branch in `handleListRuns` |
| FR-16 Methods | `handleRuns` dispatcher, 405 overload |
| NFR read-only, concurrent, bounded, deterministic | Snapshot with no writes, immutable sort keys, 100-run cap, unique sequence tie-break |

### Assumptions, risks, and scope

- **Risk, listing cost grows with the store:** no eviction exists, so each request scans every run since startup. Mitigation: response size stays bounded; if growth matters, add an ordered index or an eviction story (out of scope here).
- **Risk, ordering under clock steps or concurrent starts:** `startedAt` and `sequence` are captured a few microseconds apart, and the wall clock can step. The list then follows `timestamp`, and a test that relies on wall-clock order across a step could flake. Tests use single-threaded tight loops and compare against start order.
- **Risk, HEAD handling in the JDK server:** see Security above; unverified until a HEAD test runs.
- **Risk, widened exposure of run data:** listing makes run IDs, case IDs, and titles discoverable without knowing a UUID. Accepted by the story; this is a demo with no authentication. If the server is ever exposed beyond localhost, an access control story is needed first.
- **Risk, 405 message and `Allow` formatting:** `Allow: GET, POST` is a single header value, as the story specifies. The 405 text changes only for `/api/runs`.
- **Risk, README example drifting from real output:** mitigated by checking it against an actual response at verification.
- **Assumption, `limit` is read from the query string only:** form bodies and headers are ignored on GET. A `limit` on a POST is ignored.
- **Assumption, trailing-slash and unknown sub-paths:** `/api/runs/` and `/api/runs/<anything>` keep their current behavior (404 via `handleGetRun`); only the exact path `/api/runs` gains GET.
- **In scope:** `ExecutionService.listRecent` with sequence and constants, `RunListQuery`, `DashboardServer` routing and handlers, the 405 overload, tests, and the README section.
- **Out of scope:** filtering by status or any field, pagination beyond `limit`, persistence, any UI change, eviction or capping of stored runs, changes to how runs are created, timed, or reported, and changes to `GET /api/runs/{id}`, `/api/health`, `PORT`, and `RUN_DURATION_MS`.

**Approval request:** Please review the US-107 architecture. The one design choice not dictated by the approved defaults is that the tie-break uses a new insertion counter (rather than `startedAtNanos`) and that the full store is sorted per request instead of keeping an ordered index.

**Status:** Architecture approved by the user (relayed by the coordinating agent). Design review is complete and decisions D1 to D4 (see `design-review.md`) are approved by the user; they are reflected in this section.
