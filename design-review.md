# Design Review

## Outcome

Approved as a **local demo capstone**, not as a production test management system.

## Findings and decisions

| Risk / question | Decision |
|---|---|
| User requested a Java implementation but local browser persistence. | Use a Java 17 server for static files and the runner API, with browser local storage for cases and reports. |
| AC-04 says execute a UI automation test, but no real test harness was selected. | Use a clearly labeled simulator to demonstrate In Progress, Passed, and Failed. Document that it does not run browser automation. |
| AC-01 assumes a logged-in user. | Provide a demo-only login gate with no real credentials or security claim. |
| Duplicate IDs and required fields must be reliable. | Validate trimmed mandatory fields and unique IDs (case-insensitive) in the dashboard before saving. |
| User-entered content could be unsafe when displayed. | Escape all dynamic values before inserting into dashboard HTML. |
| Runs are asynchronous and need history. | Java returns a run ID and updates status; the browser polls and stores completed reports locally. |
| Deletion should not erase evidence. | Case deletion preserves existing execution reports. |

## Review checklist

- [x] Correctness: map each user-story criterion to a visible dashboard behavior.
- [x] Security: explicitly limit demo login; escape user content; bound and validate runner requests.
- [x] Error handling: handle duplicate IDs, validation errors, missing runs, and server failures.
- [x] Tests: cover runner state transitions and HTTP endpoint success/error behavior; verify client interactions with a manual browser checklist because no browser test harness exists.
- [x] Simplicity: use JDK HTTP server and plain browser assets instead of unnecessary frameworks or database.
- [x] Transparency: label execution and login as demo-only; explain local-storage limits.

## Residual limitations

- Browser local storage is per browser profile and can be cleared by the user.
- Demo login does not authenticate a user.
- Demo runner does not launch a browser or evaluate the test steps.

## US-103 — Filter test reports by status

### Review scope and approval status

Reviewed the approved US-103 requirements (AC-06 through AC-11) together with the US-103 architecture addition and the current report renderer, storage helpers, report-detail event handling, and available tests. This section supplements the US-102 review above; it does not change or revoke its outcome.

**Status: Approved for implementation under the user-approved US-103 plan.** The normal-path design covers the stated criteria and preserves the existing client-side report ownership boundary. The user accepted the three review recommendations below; the requirements and architecture artifacts record those decisions.

### Severity-ranked findings

| ID / severity | Classification and evidence | Impact | Recommendation |
|---|---|---|---|
| US103-F1 — **P2 (Medium)** | **Confirmed existing data-validation defect / integration risk.** `app.js` `readStorage` parses JSON but does not check the parsed value's shape; `renderReports` assumes `state.reports` has array methods (`.length` and `.map`). The proposed filter similarly depends on an array. Valid JSON such as `null` or an object in the report-storage key therefore bypasses the current parse-error fallback and can throw during rendering, preventing the reports page from displaying its empty/error state. | A browser-local storage value that is syntactically valid but not an array can break both the current report view and the planned All/status-filter views. This is not a defect in the specified filtering algorithm for valid report data. | **Accepted:** validate the loaded report value before array operations, display an actionable error, and leave the stored value untouched. The error is distinct from empty-report and no-match states. |
| US103-F2 — **P2 (Medium)** | **Verification risk.** The architecture explicitly notes that there is no browser JavaScript test harness. The available `DashboardServerTest` covers Java HTTP/static delivery, not selection changes, DOM rendering, storage preservation, or report-detail actions. | The central behavior for AC-06–AC-11 could regress without any automated test failing; server tests cannot demonstrate client-side filtering or data immutability. | **Accepted:** use a documented manual browser acceptance checklist covering default/All, each subset and stable order, details action, both empty states, reload-to-All, result announcements, unchanged stored data, and invalid-data handling, together with `mvn test`. Do not add test tooling or dependencies for this feature. |
| US103-F3 — **P3 (Low)** | **Accessibility risk / assumption.** The architecture selects a native control with a visible label, which provides standard keyboard operation. However, the current report count is an ordinary `<span>` and the report container has no live-region semantics; the architecture did not specify how a screen-reader user is informed that the result list changed after selection. | A user operating the filter with assistive technology may hear the changed select value without a clear announcement of the updated match count or no-match result. | **Accepted:** add a scoped polite live region that announces the visible result count or no-match state without moving focus. |

### Decisions and trade-offs

- **Supported normal path:** retain a transient, in-memory projection of the report array; All is the initial filter; Passed and Failed preserve source order; filtering does not request the server or write/delete reports; reloading returns the filter to All. These choices directly satisfy AC-06–AC-08 and preserve the stated local-storage boundary.
- **Empty states:** keep “No execution reports yet” distinct from the explicit no-matching-reports state required by AC-09. A no-match result is ordinary filtering, not a storage/runner error.
- **Report details and rendering:** keep actions resolved by original report ID against the full report collection, and continue escaping report-derived values before HTML insertion. The existing document-level delegated click handler and `showReport` lookup provide a compatible pattern, provided the filtered renderer does not replace the source collection.
- **Invalid report data:** guard against a parsed non-array report value, surface an actionable error, and preserve the stored value without automatic repair or deletion.
- **Accessibility:** announce the filtered result count and no-match state in a polite live region while retaining focus on the native filter.
- **Verification:** use a documented manual browser acceptance checklist for client behavior and run `mvn test` for Java regressions; no new test tooling or dependency is added.
- **Count semantics:** architecture says the count reflects visible rows. This is consistent with the displayed projection; requirements do not independently prescribe count semantics.
- **Privacy/security:** the planned filter uses only already-loaded local report data and sends neither report content nor filter choice to the server. No new trust boundary or persistence surface is proposed.
- **Scope and simplicity:** the native select and existing empty-state styling are proportionate to this two-status filter. No custom control, backend endpoint, or persisted preference is justified by the requirements.

### Approval status

The three recommendations are reflected in `requirements.md` (AC-10 and AC-11), `architecture.md`, and this review. The user approved the design and implementation plan for Phase 5. This approval does not authorize scope expansion or later SDLC phases.

### US-103 implementation review

The completed report-filter diff was checked against AC-06–AC-11, the approved architecture and design decisions, report rendering and storage call sites, available tests, Maven dependencies, and the CI workflow.

| Review area | Result |
|---|---|
| Correctness | The All/Passed/Failed view preserves source order and report-detail IDs, distinguishes the empty states, and leaves filter selection transient. |
| Security | Report fields continue through HTML escaping; filtering makes no server request and adds no trust boundary. The demo-login and local-storage limitations remain documented. |
| Error handling | Valid-JSON, non-array report data is handled without overwriting it. One additional finding below was fixed: failed report-storage writes could previously be followed by a success toast. |
| Test coverage | Java tests cover runner and HTTP behavior; they do not automate dashboard JavaScript. Manual browser checks covered the report acceptance checklist and a simulated storage-write failure. |
| Clarity / DRY | The report projection and persistence path use the existing renderer and storage helper; no review-worthy duplication was found. |
| Dependency safety | `mvn --batch-mode dependency:tree` showed JUnit Jupiter 5.11.4 and its transitive dependencies in test scope, with no production dependency declared. This is an inventory only; no CVE database or automated vulnerability scanner was run, so no vulnerability-free claim is made. |
| CI configuration | `.github/workflows/ci.yml` grants only `contents: read` and configures Java 17. |

#### Finding and disposition

**US103-CR1 — P2 (Medium), resolved.** `recordReport` previously returned success even when `saveStorage` failed, and updated the in-memory report list before persistence succeeded. A run could therefore show a success-shaped message and later render an unsaved report. The fix now persists a candidate list first, updates state and rerenders only after a successful write, and leaves the existing storage-error toast visible on failure. A targeted browser check simulated a report-storage quota error and confirmed a false result, unchanged in-memory and stored reports, and the storage error message.

**Review outcome:** no unresolved code-review findings. Limits remain: the browser UI has no automated JavaScript test harness; actual screen-reader speech and a dependency CVE scan were not performed.

## DOCSYNC-1 — Generate and check the API reference from OpenAPI

### Review scope and status

Reviewed the DOCSYNC-1 requirements and architecture against all seven DOCSYNC acceptance criteria, the repository's Maven/Java 17/JUnit conventions, `.github/workflows/ci.yml`, and the inspected `docs/` and `automated-api-documentation-sync/` locations. This section is scoped only to DOCSYNC-1 and does not change the earlier US-102 through US-104 reviews.

The user approved the DOCSYNC-1 architecture. That approval does not resolve the source/output paths, OpenAPI provenance/version, or CI command: the earlier “go forward” authorized proceeding with explicitly marked assumptions, not confirmation of those specifics. The source is reported as the story pasted in chat; no Jira/MCP retrieval is claimed.

**Review status: Conditional / blocked from implementation planning pending the decisions below.** The architecture gives a credible route through AC-01–AC-07, but a missing authoritative input prevents the proposed CI integration from being safely enabled, and the CLI's output-path behavior needs a data-preservation guard. No architecture edits or behavior changes are made in this review.

### Severity-ranked findings and risks

| ID / severity | Classification and evidence | Impact | Recommendation |
|---|---|---|---|
| DOCSYNC-F1 — **P1 (High; phase blocker)** | **Confirmed dependency/operational risk, not a defect in an implemented tool.** `requirements.md` says no OpenAPI source was found and its intended path is unconfirmed; creating/changing that definition is out of scope. `architecture.md` proposes attaching the check to `verify`, which the existing CI workflow runs, and itself notes CI cannot be green without an authoritative input. | If the verify hook is enabled before an approved source exists in the repository, the existing CI command will fail on every run. Guessing a path or fabricating a definition would misstate source authority and exceed scope. | **Proposed architecture update:** make an approved, checked-in OpenAPI definition a prerequisite to enabling the verify-bound check. Keep CI integration explicitly deferred until the maintainer supplies/identifies that input, or obtain approval to expand DOCSYNC-1 to provide it. Do not silently skip a missing source in CI, since that could hide a broken check. |
| DOCSYNC-F2 — **P2 (Medium)** | **Confirmed design gap.** The proposed CLI may accept explicit input/output paths, while the generation contract says only the designated generated reference may be changed. No rule rejects an output path equal to the source or pointing at unrelated/manual documentation. | A command typo or path override could overwrite the OpenAPI definition or unrelated documentation, violating the safe-input and documentation-preservation requirements despite the dedicated-file default. | **Proposed architecture update:** constrain generation to the agreed generated-reference target (or validate any override against an explicitly approved output boundary); reject source/output identity and unsafe aliases before writing. Retain the temporary-write/replace safeguard. |
| DOCSYNC-F3 — **P2 (Medium)** | **Operational-clarity risk.** AC-03 requires stale-check guidance that tells maintainers how to regenerate. The architecture requires the “exact regeneration command” but leaves the CLI/Maven launch mechanism and command unresolved; the current `exec-maven-plugin` is configured for the dashboard server, not this utility. | A stale check could identify drift but offer unusable or incorrect guidance; the proposed CI hook is also not yet tied to a concrete, reproducible command. | **Proposed architecture update:** settle and document one canonical local generation command and one check command, then require stale diagnostics to print the former verbatim. Confirm how that command coexists with the existing Maven server entry point before attaching check to `verify`. |
| DOCSYNC-F4 — **P2 (Medium)** | **Verification risk.** The architecture promises preservation on write errors and describes a temporary sibling file plus replacement, but its test list does not exercise temporary-file creation/write/replacement failure or the stated fallback behavior. AC-07 lists source errors and stale detection; the broader non-destructive-write guarantee is not thereby verified. | A failure during generation could still damage or remove a prior reference if the implementation's replacement/fallback path is incorrect. | **Proposed architecture update:** add a deterministic write-failure test strategy and cover failures before replacement, asserting the prior target remains byte-for-byte unchanged and unrelated docs remain untouched. Specify that a fallback must fail closed rather than delete the prior target first. |

### Acceptance-criteria trace and design trade-offs

| Criterion | Review assessment |
|---|---|
| DOCSYNC-AC-01 — Generate endpoint reference | Covered in the architecture by parsing operations and rendering path, method, and summary into a clearly identified reference. Missing-summary behavior remains an assumption/open decision below. |
| DOCSYNC-AC-02 — Deterministic output | Covered by canonical path/method order, stable rendering, UTF-8/LF, and exclusion of timestamps/environment-specific data; the proposed repeated-generation test is appropriate. |
| DOCSYNC-AC-03 — Stale check guidance | Byte comparison and stale/nonzero behavior are specified, but the actionable regeneration command is not yet resolved (DOCSYNC-F3). |
| DOCSYNC-AC-04 — Read-only check | The separate in-memory check path, no-write/no-temp-file rule, and read-only CI permission align with the criterion. The proposed isolated tests should also assert no repository mutation. |
| DOCSYNC-AC-05 — Safe input errors | Parse/validation before writes and preservation of existing output for missing/invalid input are specified. The output-target guard and write-failure evidence remain necessary (DOCSYNC-F2/F4). |
| DOCSYNC-AC-06 — Preserve other documentation | A dedicated generated file is a strong isolation choice; unrestricted output-path overrides undermine that guarantee (DOCSYNC-F2). |
| DOCSYNC-AC-07 — Required test coverage | The planned suite covers generation, repeatability, stale detection, and missing/invalid input; add the write-failure protection check noted in DOCSYNC-F4. |

The Java 17/Maven/JUnit approach fits the repository and avoids a separate runtime, but robust OpenAPI parsing introduces a new production dependency. Pin and review the chosen parser/version, document supported OpenAPI version/format, and keep external reference/network resolution disabled as already specified. The read-only workflow permission is a suitable boundary; no credentials, commit, or push capability is needed. Deterministic sorting and a standalone generated file favor stability and safe preservation over matching the source document's authored order or inserting generated content into a manual guide.

### Open questions and decisions required

1. What is the authoritative OpenAPI file path, format/version, and source/ownership? It was not found in the inspected locations; no Jira/MCP provenance is asserted.
2. Is `docs/api-reference.md` the approved generated destination and Markdown the required format, or should another location/format be used?
3. Should the read-only check be attached to the existing `mvn --batch-mode verify` CI path? This can only be enabled once the approved source exists. What canonical Maven invocation should local generation and checking use?
4. OpenAPI permits operations without a `summary`, while DOCSYNC-AC-01 requests one for every endpoint. Confirm whether such an input should fail with actionable guidance (the architecture's current assumption), or whether a documented fallback is required.
5. Confirm the output-boundary rule in DOCSYNC-F2: generation should not allow an arbitrary output path to overwrite the input or unrelated repository documentation.

### Proposed architecture updates and approval gate

The recommendations in DOCSYNC-F1–F4 are proposals only; they have not been applied to `architecture.md` and are not recorded as approved decisions. Before planning, please confirm the source/output/format and CI choices, the missing-summary policy, and agreement to constrain the generation target and test write-failure preservation. Once those are resolved, the architecture can be updated precisely and reviewed for approval. No planning, implementation, or tests were performed as part of this phase.

## US-104 — Export visible execution reports as CSV

### Review scope and approval status

Reviewed the approved US-104 requirements (AC-12 through AC-19) and approved revised architecture against the existing Reports page, report storage/projection logic, HTML/CSS patterns, available tests, and Maven setup. This review is scoped only to US-104; prior US-102, US-103, and DOCSYNC-1 sections remain unchanged. The story source is the user-provided chat story recorded in `requirements.md`; no Jira or MCP retrieval is claimed.

The user approved the revised architecture immediately before this review. That approval is recorded as context, not as approval of additional changes proposed here. **Review status: pending human agreement before implementation planning.** The core design covers the specified CSV contract and local-only boundary. Findings below identify details that need to be accepted or clarified. No architecture edits, implementation, planning, or tests were performed.

### Severity-ranked findings and risks

| ID / severity | Classification and evidence | Impact | Recommendation |
|---|---|---|---|
| US104-F1 — **P2 (Medium)** | **Confirmed design/code mismatch.** The architecture says an existing ordered visible-report projection supplies both the table and export. In `app.js`, `renderReports` currently creates `visibleReports` inline from the filter; there is no shared projection helper available to the not-yet-implemented exporter. | If export adds its own filtering or re-reads storage, it can diverge from the rows displayed when the user activates export, risking AC-13/AC-15 and the no-mutation boundary. | **Proposed behavior-preserving clarification:** extract the existing All/Passed/Failed derivation into a single `getVisibleReports()` (or equivalent) used by both `renderReports` and export. Keep the report source/order and filter transient; serialize synchronously from the current projection at activation. |
| US104-F2 — **P2 (Medium)** | **Confirmed residual security/privacy risk; not a CSV syntax defect.** The architecture correctly notes that CSV quoting preserves field boundaries but does not stop spreadsheet software interpreting formula-like report values. Reports contain case titles and failure details that may originate from user-entered or runner data. AC-14/AC-16 require original values to round-trip, while AC-19 prohibits server transfer; neither condition neutralizes spreadsheet formula interpretation on the user's device. | Opening a downloaded file in spreadsheet software can evaluate formula-like values. Prefixing or transforming them in the sole export would violate the approved raw-value/round-trip contract; no-server behavior is not a mitigation for local spreadsheet evaluation. | **Decision for this review:** retain exact values and do not silently sanitize or mutate CSV fields, as the approved architecture specifies. Keep the risk clearly disclosed. A spreadsheet-safe transformation, warning, or alternate export mode would be a behavior change; obtain explicit user approval and reconcile it with value fidelity before adding one. |
| US104-F3 — **P2 (Medium)** | **Accessibility and interaction risk / design detail.** The architecture calls for a disabled button and visible explanation, but the inspected CSS has no general `.button:disabled` treatment (only `.row-action:disabled`), and the HTML has no existing export control or explanatory-text association pattern. The current report filter does have a visible label and native select. | A disabled primary-styled button may not look disabled, and assistive-technology users may not understand why it is unavailable if the explanation is visually nearby but not meaningfully associated. This risks the disabled-state requirement and the stated perceivable/understandable explanation. | **Proposed behavior-preserving clarification:** keep the explanation visible and adjacent, associate it with the export control where supported (for example, `aria-describedby`), and provide a clear disabled visual style that does not rely on color alone. Verify the empty-list and no-match cases with keyboard and assistive-technology inspection. For invalid stored report data, disable export without replacing the existing actionable data-error message with an empty-list explanation. |
| US104-F4 — **P2 (Medium)** | **Verification risk, not a requirements failure.** The architecture explicitly chooses manual browser acceptance because the repository has no browser JavaScript test harness. The inspected tests exercise Java server/static HTTP behavior; `pom.xml` declares JUnit Jupiter only in test scope. Neither those tests nor Maven verification directly establish CSV serialization, browser download, visible-subset parity, or DOM accessibility. | A regression in CSV quoting, raw field preservation, the disabled state, or download behavior could pass the available automated suite. | **Accepted architecture trade-off, verification condition:** do not characterize the export as browser-automated-tested. Require recorded manual browser evidence for AC-12–AC-19: compare exported rows to visible rows/order; parse edge-case CSV and verify UTF-8/raw values; check filename, both empty cases, invalid-data state, unchanged storage, no report-data request, and the download action. No new test dependency is proposed. |
| US104-F5 — **P3 (Low)** | **Lifecycle/failure-handling risk.** The architecture requires temporary link and object-URL cleanup after activation and mentions synchronous serialization/Blob/URL failures, but leaves the cleanup path if link activation throws and the exact deferred revocation timing unspecified. The browser does not provide a reliable download-completion signal, as the architecture correctly notes. | Immediate revocation can race browser download handling in some implementations; an activation failure may leave a temporary link/URL or produce no download without clear cleanup. | **Proposed architecture clarification:** specify cleanup for both normal and exceptional activation paths: remove the temporary link and revoke the URL after a browser-compatible deferred interval following the click; ensure a thrown click/creation failure is surfaced through the existing error pattern and does not alter report state. Treat download completion/cancellation as browser-managed, not as an app-confirmed success. |

### Acceptance-criteria trace

| Criterion | Review assessment |
|---|---|
| AC-12 — Export action | Covered by the planned Reports-page button; it is not present yet, consistent with this pre-implementation review. |
| AC-13 — Export the visible subset | Architecture's shared-projection intent is correct; implement one derivation shared with the renderer (US104-F1) to ensure current-filter parity. |
| AC-14 — CSV columns and values | Exact six-column order and raw integer milliseconds/stored ISO-8601 mapping are specified. Preserve values; formula interpretation remains the documented residual risk (US104-F2). |
| AC-15 — Header and order | One header and ordered rows from the table projection are specified; the shared-projection clarification addresses the main parity risk. |
| AC-16 — UTF-8 CSV escaping | Commas, quotes, CR/LF quoting and doubled quote handling are specified with exact-value round trip. Manual CSV-reader verification is needed because no client-side automated harness exists. |
| AC-17 — No visible reports | Both empty list and filter-no-match are covered. Ensure disabled affordance is visibly clear and the explanation is accessible; preserve invalid-data error as a distinct state. |
| AC-18 — Filename | Exact filename `caseflow-reports.csv` is specified; browser acceptance must verify it. |
| AC-19 — Read-only local export | Browser-only Blob/link flow and no-storage-write/no-network boundary are specified. Verify unchanged storage and no report-data request manually; formula interpretation risk is local to opening the file, not a server transfer. |

### Decisions, risks, and trade-offs

- **Approved constraints respected:** export only the currently visible ordered reports; preserve the exact six columns, underlying integer duration, and stored ISO-8601 timestamp; encode valid UTF-8 CSV; use the fixed filename; do not mutate saved reports or transmit report data.
- **Formula interpretation decision:** the approved architecture chooses source-value fidelity over spreadsheet-specific neutralization. CSV escaping is not formula mitigation. This is a disclosed residual risk, not a confirmed implementation defect. A safe-transformation behavior cannot be added without explicit approval because it changes exported values and conflicts with the round-trip criterion.
- **Download lifecycle:** temporary URL/link handling is appropriately browser-side and adds no server endpoint or dependency. The app must not claim that a download completed when the browser offers no reliable completion signal.
- **Dependencies and maintainability:** the design adds no runtime/build dependency and uses the existing plain HTML/CSS/JavaScript stack. A shared projection helper is a small behavior-preserving way to prevent the renderer/export filters from drifting.
- **Browser testing:** available Java tests cover server concerns, not CSV or browser UI. The approved manual-check approach is viable for this repository but leaves no automated regression coverage for export behavior; retain that limitation in any later verification report.

### Proposed architecture updates and questions for the human gate

No update has been applied. Proposed precise clarifications are: (1) require a shared `getVisibleReports()`-style derivation used by rendering and export; (2) specify an accessible, visibly disabled state and preserve the invalid-data error separately; and (3) specify deferred object-URL cleanup on both successful and exceptional activation paths. These are intended to preserve approved behavior. The formula-safe transformation/warning/alternate-mode question is behavior-changing and remains excluded unless separately approved.

Please confirm agreement with the behavior-preserving clarifications and explicitly acknowledge that exact-value CSV export retains the documented spreadsheet formula interpretation risk (or request a behavior-changing mitigation for design review). **No implementation planning or implementation should proceed until the human design-review gate is satisfied.**

## US-105 — Configure the simulated test run duration

### Review scope and approval status

Reviewed the US-105 requirements (FR-12, AC-20 through AC-25) and the US-105 architecture against the real code: `DashboardServer.java`, `ExecutionService.java`, `ExecutionServiceTest.java`, `DashboardServerTest.java`, `pom.xml`, `README.md`, `.github/workflows/ci.yml`, and the run/poll logic in `static/app.js`. This section is scoped only to US-105; earlier US-102 through US-104 and DOCSYNC-1 sections are unchanged. The story source is the user-provided chat story recorded in `requirements.md`; no Jira or MCP retrieval is claimed.

Provenance note: the user's approvals of the requirements, the architecture, and decisions D1-D5 below reached this review through the coordinating agent, which reported that the user answered directly in the working session. This reviewer did not receive them first-hand, so they are recorded as "approved by user, as relayed" and the status lines in `requirements.md` and `architecture.md` say so.

**Review status: Decisions D1-D5 recorded (approved by user, as relayed). The earlier High-impact finding US105-F1 is resolved at the design level; `architecture.md` and `requirements.md` were updated to agree.** The core design (pure parser, validation before any bind, exit code 2 with a fixed stderr message, backend only, no dependency) is sound and covers AC-20 through AC-25. Remaining risks are Medium or Low and are listed under "Remaining risks" below. No code or test was written or run in this phase. The human gate before implementation planning remains: the user should confirm the plan to proceed.

### Severity-ranked findings and risks

| ID / severity | Classification and evidence | Impact | Recommendation |
|---|---|---|---|
| US105-F1 — **P1 (High) — RESOLVED (decision D1; architecture updated, not yet implemented or run)** | **Confirmed defect in the architecture's AC-23 test design.** The test is meant to fail "if the configured value were ignored", with an upper bound of "for example 1500 ms ... far below the 1400 ms default". 1500 ms is above 1400 ms. A service that ignored the setting and ran for the 1400 ms default would report `durationMs` of about 1400, which passes both the lower bound (about 190) and the 1500 upper bound. The early "still In Progress at about 50 ms" check also passes for 1400. The test as written cannot tell a 200 ms run from a 1400 ms run. In addition, `ExecutionService.runDuration` is private (`ExecutionService.java:25`), so no test can check the AC-20 default or the `ExecutionService()` to `DEFAULT_RUN_DURATION` delegation directly. | AC-23 (and the AC-20 delegation) could appear verified while the configured duration is ignored. This is false assurance on the story's core behavior. | **Proposed architecture update (needs approval):** (a) lower the upper bound to clearly below 1400 ms with jitter headroom, for example 1000 ms, and state the reasoning correctly; (b) add a package-private `Duration runDuration()` accessor to `ExecutionService`, so tests can assert deterministically that `new ExecutionService()` is 1400 ms and `new ExecutionService(RunDurationConfig.parse("200"))` is 200 ms, with the timing test as a smoke check; (c) tighten the lower bound to 200 ms (see US105-F5). Item (b) adds one small package-private method to a component the architecture lists as "unchanged run logic", so it needs agreement. **Disposition: approved as D1; `architecture.md` now specifies the accessor, a lower bound of 200 ms, and an upper bound below 1000 ms.** |
| US105-F2 — **P2 (Medium)** | **Confirmed coverage gap.** The only code that connects the environment to the service is `main` (`DashboardServer.java:50-57`), and today it calls `new ExecutionService()`. The planned AC-23 test builds `ExecutionService` from the parser directly, and the planned subprocess test covers only the invalid path (the valid-value subprocess test is explicitly "not required"). If an implementer parses the value but still passes `new ExecutionService()`, or discards the result, every planned automated test passes while `RUN_DURATION_MS=200` has no effect. | The most likely regression for this story (wiring in `main`) is undetected by the plan. AC-21 and AC-23 ("Starting with RUN_DURATION_MS=200") are verified only at the parser and service level, not end to end. | **Decision needed.** Option A (recommended if the subprocess test is adopted): add one valid-value case to the same subprocess class, run with `RUN_DURATION_MS=" 300 "` and port 0, read the port from the "running at" line, start a run over HTTP, poll to completion, assert the reported duration is at least 300 and below the default, then destroy the child. This also proves AC-21 trimming end to end. Option B: no valid-value subprocess test; accept the gap, confirm the wiring in code review, and record a manual run (`RUN_DURATION_MS=200`, observe the run) as verification evidence. Do not claim end-to-end coverage under Option B. |
| US105-F3 — **P2 (Medium)** | **Confirmed gap in the proposed subprocess test.** The architecture's own startup-order risk ("if validation moves after `new DashboardServer` the port would already be bound") names this test as the mitigation. But the test uses port `0` and asserts non-zero exit, stderr text, and no "running at" on stdout. If validation were moved after `new DashboardServer(...)` (socket already bound by `HttpServer.create`, `DashboardServer.java:27`) but before `start()`, the process would still exit 2, print the message, and never print "running at". The test passes either way. | AC-22's "before the server listens" ordering is not actually verified, and the stated mitigation does not mitigate. | **Proposed test-design update:** make the ordering observable by occupying the port. The test opens a `ServerSocket(0)` and keeps it open, starts the child with that port as the argument and an invalid `RUN_DURATION_MS`, and asserts exit code 2 with the exact message and no `BindException`/stack trace on stderr. If validation ran after the bind, the child would fail with a bind error instead. This is deterministic and avoids the "find a free port, close it, reuse it" race. Keep the port-0 stdout check as a secondary assertion only. |
| US105-F4 — **P2 (Medium)** | **Flakiness risk (subprocess test).** Evidence and mitigations: **Classpath and working directory:** a relative `target/classes` depends on the process working directory (Surefire normally uses the module directory, but an IDE or other runner may not). **Hang risk:** the JDK `HttpServer` dispatcher thread is non-daemon (the app stays up after `main` returns, which is how it runs today), so a regression that lets the child start would never exit. A bare `waitFor()` would hang CI. **Output handling:** reading stderr before the process ends can block, and unread output can fill a pipe. **Environment leakage:** the child inherits the parent's environment (`PORT`, `RUN_DURATION_MS`, `JAVA_TOOL_OPTIONS`). A `Picked up JAVA_TOOL_OPTIONS` line on stderr breaks an exact-equality stderr assertion. **Line endings:** `println` uses the platform separator. **Port conflicts:** a fixed or reused port can collide. **CI:** the workflow runs `mvn --batch-mode verify` on `ubuntu-latest`, which launches a second JVM in the same job (about 1 s). | A flaky or hanging test in the single required CI check blocks merges and erodes trust in the suite. | **Recommended conditions if the subprocess test is included:** locate classes from `DashboardServer.class.getProtectionDomain().getCodeSource()` rather than a relative path (confirm at implementation how Surefire exposes `java.class.path` before relying on it); launch `Path.of(System.getProperty("java.home"), "bin", "java")`; pass the port as an explicit argument so `PORT` is never read; set or remove `RUN_DURATION_MS` explicitly in `ProcessBuilder.environment()`; send output to temp files or drain it; use `waitFor(timeout)` (for example 20-30 s) and `destroyForcibly()` in a `finally`, with a JUnit `@Timeout` as a second guard; assert that the message appears as a trimmed line on stderr (do not require equality of the whole stream or the platform line separator); keep it in its own test class so it can be dropped or isolated if CI proves unstable. No new dependency is needed (`@Timeout` is part of JUnit Jupiter, already in test scope). |
| US105-F5 — **P3 (Low)** | **Timing-test precision and flake risk (AC-23).** (a) The architecture justifies a lower bound of about 190 ms by "millisecond truncation". The run's start nanotime is taken before `schedule` (`ExecutionService.java:52-55`) and a scheduled task never fires before its trigger time, so the elapsed nanoseconds are at least 200 ms and the truncated millisecond value is at least 200. The lower bound can be exact and still never flake. (b) The "still In Progress at about 50 ms" assertion can fail if the test thread stalls for more than about 150 ms (GC pause, loaded CI runner). The `start()` return value already asserts In Progress deterministically. (c) The existing tests use fixed sleeps with small margins (`ExecutionServiceTest` 70 ms then sleep 130 ms; `DashboardServerTest` 40 ms then sleep 100 ms). | (a) none; (b) a rare false failure; (c) existing, unchanged, and not caused by US-105. | **Recommend:** assert `durationMs >= 200`; make the early check conditional on a measured elapsed time (assert In Progress only when the wall-clock time around the `find` call is under about 150 ms) or rely on the `start()` result; keep the polling loop with a generous deadline. Leave the existing sleep-based tests alone. |
| US105-F6 — **P2 (Medium)** | **Existing defect whose exposure grows with this story.** `runCase` saves the case as "In Progress" to local storage before the request (`app.js:446-452`) and the Run button is disabled while that status is stored (`app.js:218`, and the guard at line 448). Nothing on page load resets a stale "In Progress" status. Reloading or closing the page mid-run therefore leaves the case permanently "Running..." and not runnable until its stored status is changed some other way. At 1400 ms that window is small; at up to 10 000 ms it is about seven times larger, and slower demos are the very purpose of this story. Client polling itself has no deadline (`app.js:469-473`, 350 ms interval), so a 10 s run completes normally and reports the server's `durationMs`. | A presenter who sets a long duration and refreshes mid-run strands a case in the demo. This is not a regression in US-105 and fixing it is a UI change that the story excludes. | **Decision needed:** keep it out of US-105 (recommended, since requirements prohibit UI changes) and either (a) add a one-line README caution beside the new note, or (b) open a separate follow-up story to recover stale "In Progress" cases on load. Record it as a known residual limitation either way. |
| US105-F7 — **P3 (Low)** | **Unverified assumption.** The README's primary run path is `mvn exec:java`, which runs `main` inside the Maven JVM. `System.exit(2)` there terminates the Maven process. The expected result is that stderr shows the message and the shell sees exit code 2 without a Maven failure summary, but this was not tested in this review. The architecture considers only `java -cp`. | AC-22 is probably still met on the documented path, but the user experience and the exit code are unconfirmed. | **Recommend:** at verification time run `RUN_DURATION_MS=abc mvn exec:java` and `RUN_DURATION_MS=abc java -cp target/classes org.example.dashboard.DashboardServer 0`, and record exit code and stderr. Word the README example so the variable is set in the same command line as `mvn exec:java`. |
| US105-F8 — **P3 (Low)** | **Imprecise claim in `architecture.md`.** "If both a port and RUN_DURATION_MS are invalid, the existing port failure happens first" is true only for a non-numeric port (the unguarded `Integer.parseInt` in `main`). An out-of-range port (for example 70000) fails in `new InetSocketAddress` and an in-use port fails in `HttpServer.create`, and both happen inside `new DashboardServer(...)`, after duration validation. In those cases the duration message wins. The behavior is acceptable and is what the F3 ordering test relies on. | Documentation accuracy only. | **Proposed wording fix:** state that only the numeric parse of the port precedes duration validation; bind and range failures follow it. |
| US105-F9 — **P3 (Low)** | **Wording difference to confirm.** The approved decision and FR-12 say the value is "trimmed". The architecture uses `String.strip()` (Unicode whitespace) instead of `trim()` (characters up to U+0020). They differ only for exotic input: an EM SPACE (U+2003) around a number is accepted by `strip()` but rejected by `trim()`, and a control character such as U+0001 is blank under `trim()` but a rejection under `strip()`. A non-breaking space (U+00A0) is rejected by both. | Very low practical effect; it is a deliberate deviation from the literal word "trim" in an approved decision. | **Recommend** confirming `strip()` (it matches `isBlank()` as the architecture argues) and adding unit cases for EM SPACE (accepted) and NBSP (rejected), written with `\u` escapes. |
| US105-F10 — **P3 (Low)** | **Test-quality risk.** The architecture exposes the message as a constant in `RunDurationConfig`. A test that compares the thrown message to that constant would still pass if the message text were edited. | The exact AC-22 text is not protected. | **Recommend** that unit and subprocess tests assert the literal string `RUN_DURATION_MS must be a whole number from 100 to 10000.`. |
| US105-F11 — **P3 (Low)** | **Documentation touchpoints.** AC-25 requires only the README note beside the `PORT` sentence (`README.md:36`). The README's later sentence about what tests cover does not mention a startup test. `CHANGELOG.md` has per-story entries (none yet for US-104 or US-105). The top-level component table in `architecture.md` does not list `RunDurationConfig`. | Minor drift in project docs. | **Recommend** the README note include a concrete example (`RUN_DURATION_MS=500 mvn exec:java`), the unit, default, range, and the fail-fast behavior; optionally add a `CHANGELOG.md` line. Treat the others as optional. |

### Claims in `architecture.md` checked against the code

| Claim | Result |
|---|---|
| `ExecutionService()` hard-codes `Duration.ofMillis(1400)`; a package-private `ExecutionService(Duration)` exists and tests use it. | **Correct** (`ExecutionService.java:33-42`; tests use it throughout). It rejects only negative durations. |
| The `DashboardServer` constructor binds the port, so validation must precede `new DashboardServer`. | **Correct** (`HttpServer.create(new InetSocketAddress(port), 0)`, line 27). Good catch by the architecture. |
| At exit time no `HttpServer`, scheduler thread, or shutdown hook exists. | **Correct.** The hook is registered after `start()` (line 56); the scheduler thread is created lazily on first `schedule`. |
| `main` parses the port from argument 0, else `PORT` (default 8080), with an unguarded parse. | **Correct** (lines 51-52); a malformed port still throws `NumberFormatException`. |
| `README.md` line 36 holds the `PORT` sentence. | **Correct.** |
| `System.exit` appears nowhere today, so adding it only in `main` is contained. | **Correct.** |
| Run completion is scheduled after `runDuration.toMillis()` and `durationMs` is measured from run start to completion. | **Correct** (`ExecutionService.java:54-55, 99-101`). The lower bound is guaranteed (see US105-F5). |
| The AC-23 upper bound of 1500 ms is "far below the 1400 ms default so the test would fail if the configured value were ignored". | **Wrong** (US105-F1). |
| The subprocess test checks that the server "never listens"; it mitigates a startup-order regression. | **Not supported by the described test** (US105-F3). |
| "If both a port and `RUN_DURATION_MS` are invalid, the existing port failure happens first." | **Partly wrong** (US105-F8). |
| Rule 5: at most 5 ASCII digits cannot overflow `int`, and `99999999999` fails the length check first. | **Correct.** |
| Browser behavior is unaffected by a longer duration. | **Confirmed** for polling (no client deadline). The stale-status exposure in US105-F6 is separate. |

### Acceptance-criteria trace

| Criterion | Component(s) | Review assessment |
|---|---|---|
| AC-20 — Default duration | `RunDurationConfig.parse`, `ExecutionService.DEFAULT_RUN_DURATION`, `ExecutionService()` | Covered for the parser (null, empty, whitespace-only). The delegation `ExecutionService()` to the constant is not observable without the accessor in US105-F1. |
| AC-21 — Accepted boundaries | `RunDurationConfig.parse` (`strip`, digit scan, 100 to 10000) | Covered by unit tests for 100, 10000, and `" 500 "`. End-to-end use depends on the `main` wiring (US105-F2). `strip()` versus `trim()` needs confirmation (US105-F9). |
| AC-22 — Rejected values | `RunDurationConfig.parse`, `DashboardServer.main` (stderr, `System.exit(2)`) | Parser rules are sound and complete against the listed values, including leading zeros, `+100`, `-5`, `1e3`, `99999999999`, and non-ASCII digits. Message and exit code depend on `main`. The "before the server listens" ordering is not proved by the described subprocess test (US105-F3). |
| AC-23 — Configured duration observed | `main` to `ExecutionService(Duration)` to scheduler | The planned test cannot distinguish 200 ms from 1400 ms (US105-F1) and does not exercise `main` (US105-F2). |
| AC-24 — Unit tests | `RunDurationConfigTest` | Planned coverage (default, both boundaries, trim, a broad rejected set) is adequate. Assert the literal message (US105-F10). |
| AC-25 — README | `README.md` | Planned placement is correct. Add an example and the exec:java caveat (US105-F7, US105-F11); the stale-status caution is optional (US105-F6). |

### Security, failure behavior, and maintainability

- **Trust boundary:** `RUN_DURATION_MS` is operator-controlled process configuration. No HTTP request can set it, and no endpoint exposes it. The bounded range (100 to 10 000 ms) rules out zero or negative delays and limits how long a run can stay In Progress. No new attack surface was found.
- **Failure behavior:** fail-fast with a fixed message and no echo of the raw value is appropriate and testable; the trade-off is that the operator cannot see what was rejected. The message wording is the approved text and is not changed here.
- **Overflow and exception safety:** the allow-list design avoids `NumberFormatException` entirely. The catch wraps only the parser call, so unrelated failures are not mislabeled.
- **Maintainability:** one new package-private class and one constant is proportionate. The numbers 100 and 10000 appear both in the code and in the fixed message string; keep them as named constants next to the message and cover them with the literal-string test so they cannot drift apart.
- **Dependencies:** none added; `pom.xml` is unchanged in the design (JUnit Jupiter 5.11.4 in test scope only).

### Decisions, risks, and trade-offs

- **Separate parser class versus inline in `main`, throwing `IllegalArgumentException`, strict digit scan, exit code 2 with stderr:** each is reasonable and within the approved decisions. They are not re-opened here.
- **Subprocess test:** the value is real but the planned form does not deliver it (US105-F3), and it adds flake surface (US105-F4). If included, adopt the F3 and F4 conditions. If excluded, the "before listens" and wiring guarantees rest on code review and manual verification, and the verification report must say so.
- **Not an issue:** no new runtime or build dependency, no API or UI change, no change to report format or `PORT` handling.

### Decisions recorded (approved by user, as relayed by the coordinating agent)

| Decision | Outcome | Findings addressed | Where reflected |
|---|---|---|---|
| D1 — AC-23 test fix and accessor | Approved. Upper bound below 1400 ms (for example 1000 ms), lower bound 200 ms, package-private `ExecutionService.runDuration()` accessor so tests check the AC-20 default, the no-arg delegation, and the parsed-value wiring deterministically. The conditional or removed "about 50 ms" check (F5) is adopted. | F1 (resolved), F5 | `architecture.md` components table, testability section, AC trace |
| D2 — Subprocess tests | Approved, both cases, in their own class `StartupConfigurationTest`: (1) invalid value with an occupied port, child exits 2, prints the exact message, no `BindException`; (2) valid value, child starts the server and a run completes over HTTP. Conditions: classes from the code source, `waitFor` with timeout, `destroyForcibly` in a `finally`, drained output, explicit `RUN_DURATION_MS`, port passed as an argument, trimmed-stderr-line assertion. | F2, F3, F4 | `architecture.md` startup behavior, risks |
| D3 — Whitespace handling | Approved. Use `String.strip()`; add EM SPACE (accepted) and NBSP (rejected) test cases. Requirements wording changed from "trimmed" to "Unicode whitespace is stripped". | F9 | `requirements.md` (decisions, FR-12, AC-21, assumptions), `architecture.md` parser rule 2 and test table |
| D4 — Stale "In Progress" after reload | Approved. Add a README caution; record a follow-up story; no UI change in US-105. | F6 | `requirements.md` AC-25 and out of scope, `architecture.md` README row and risks |
| D5 — Approval provenance | The user approved the requirements and the architecture directly in the working session, and approved the design-review decisions. Recorded as relayed. Status lines updated to "Approved by user". | provenance note | `requirements.md` and `architecture.md` status lines |

Also applied without separate decision: F10 (tests assert the literal message, not the constant), F8 (corrected the port-first wording in `architecture.md`), and F11 (README example `RUN_DURATION_MS=500 mvn exec:java` added to AC-25). F7 (`mvn exec:java` with `System.exit(2)`) is recorded as a **manual verification item**: record exit code and stderr for `RUN_DURATION_MS=abc mvn exec:java` and for the direct `java -cp` launch at the verification phase.

### Remaining risks

None is High impact. Nothing has been implemented or run yet, so all of the following are unverified until code and tests exist.

| Risk | Impact | Status |
|---|---|---|
| Subprocess tests can hang or flake in CI (JVM spawn, classpath discovery, port handling, timeouts). | Medium | Mitigated by the D2 conditions and a separate class; isolate or drop if CI proves unstable, and state so in the verification report. |
| Stale "In Progress" case after reloading during a longer run (existing `app.js` behavior, exposure up to about seven times larger). | Medium | Accepted limitation (D4): README caution now, follow-up story to be raised; no UI change here. |
| `mvn exec:java` with `System.exit(2)` ends the Maven JVM; exit code and message presentation are not yet confirmed. | Low | Manual verification item (F7). |
| AC-23 smoke test still has one timing-sensitive assertion (completion below 1000 ms) on a loaded CI runner. | Low | Accepted; 800 ms headroom, and the configured value is separately asserted without timing. |
| Existing sleep-based tests (70 ms/130 ms and 40 ms/100 ms) can flake independently. | Low | Existing and unchanged by US-105. |
| The classpath-from-code-source approach and `@Timeout` interplay with Surefire should be confirmed when the test is written. | Low | Confirm at implementation. |
| `strip()` deviates from the literal word "trim" only for exotic whitespace; tests pin the behavior. | Low | Accepted (D3). |

### Approval status

Design decisions D1-D5 are recorded as approved by the user (relayed by the coordinating agent). US105-F1 is resolved at the design level. This review does not authorize implementation planning or implementation by itself: the next phase starts only when the user confirms moving on, as for the earlier stories.

## US-107 — List recent runs with GET /api/runs

### Review scope and approval status

Reviewed the US-107 requirements (FR-13 to FR-16, US-107 AC-01 to AC-07, with the approved defaults for the four open questions) against the US-107 architecture and the real code: `DashboardServer.java` (`handle`, `handleStartRun`, `handleGetRun`, `sendMethodNotAllowed`, `sendJson`, `send`, `jsonRun`, `quote`, `parseForm`), `ExecutionService.java` (`start`, `find`, `Run`, `toResult`), `DashboardServerTest.java`, a search of all of `src/test` for 405, `Allow`, HEAD and OPTIONS, `README.md`, and the `fetch` calls in `static/app.js`. This section is scoped only to US-107; earlier sections are unchanged. Story source is the text supplied in the /sdlc command; no Jira or MCP retrieval is claimed.

Provenance: the approval status of the requirements is recorded in `requirements.md`. The approval of the US-107 architecture was relayed by the coordinating agent; `architecture.md` still ends with "Approval request ... Design review has not started" and carries no approval line. This reviewer did not receive that approval first-hand, so it is recorded as "approved by user, as relayed". It is not a substitute for the user's own approval of this review.

**Review status: Complete. Decisions D1 to D4 below were all answered YES by the user (relayed by the coordinating agent), and the matching architecture updates were applied to `architecture.md`.** The design covers FR-13 to FR-16 and every US-107 acceptance criterion. No High-impact risk is unresolved. `requirements.md` was not changed by this review. No code or test was written or run, so everything about runtime behavior below is reasoned from the source, not observed.

### Confirmed defects and gaps (ranked)

| ID / severity | Evidence | Impact | Recommendation |
|---|---|---|---|
| US107-F1 — **P2 (Medium)** | **Test plan cannot prove the approved tie-break.** `ExecutionService.start` takes `Instant.now()` (`ExecutionService.java:58`). The architecture's test "start 50 runs in a tight loop ... equal `Instant` values are likely, so the counter must decide" depends on clock ties. From JDK knowledge (not measured here), `Instant.now()` has nanosecond precision on Linux since JDK 15 and microsecond precision on macOS, and each `start` call does a `UUID.randomUUID`, a map put and a schedule, so consecutive runs usually get different instants on the CI runner (`ubuntu-latest`). The test would then pass even if `sequence` were missing or wrong. | The one behavior the user explicitly chose (insertion-counter tie-break) could be untested while the suite is green. False assurance, not a runtime defect. | **Decision D1 (recommended): add a package-private test seam, `ExecutionService(Duration, java.time.Clock)`, with `start` using `Instant.now(clock)`; the public and existing constructors use `Clock.systemUTC()`.** A fixed clock gives every run the same `Instant`, so a test can assert strict reverse start order from `sequence` alone. Behavior is unchanged. Keep the tight-loop test only as a sanity check and say in the plan that it does not prove the tie-break. |
| US107-F2 — **P2 (Medium)** | **HEAD 405 is unverified and noisy by design.** `sendMethodNotAllowed` calls `sendJson` and then `send`, which calls `exchange.sendResponseHeaders(status, body.length)` followed by `getResponseBody().write(body)` (`DashboardServer.java:178-181, 218-237`). For a HEAD request the JDK server is expected (from JDK knowledge; no JDK source or runtime was available in this phase) to log a warning that a content length was given for HEAD, force the length to 0, and then reject the body write with an `IOException`. That exception is caught by `catch (IOException)` in `handle` (line 94), which prints "Dashboard connection failed: IOException" to stderr and then `exchange.close()` runs. Headers are already sent, so the client should still see 405 and `Allow`, with no hang. Precedent: HEAD already takes this exact path today on `/`, `/api/health` and `/api/runs/{id}`, and no test covers it. | Expected client result is correct, but each HEAD request writes a JDK warning and an error line. If the expectation is wrong, a client could see a reset or a hang instead of a clean 405. | **Decision D2 (recommended): in the new 405 overload only, for HEAD send headers with no body (`sendResponseHeaders(405, -1)`) and skip the write; leave the existing one-argument helper untouched.** Add HEAD and OPTIONS tests with `HttpRequest.method("HEAD", BodyPublishers.noBody())` asserting 405, `Allow: GET, POST`, and that the request completes. Record the observed stderr output at verification. |
| US107-F3 — **P3 (Low)** | **Malformed percent-escapes are not testable or deliverable as specified at HTTP level.** The architecture and its AC-04 trace claim `%zz` returns the exact 400 body. The JDK server builds a `URI` from the request line; a malformed escape is expected to be rejected by `java.net.URI` and answered with a bare 400 before the handler runs (JDK knowledge, unverified here). Also, `HttpClient` cannot send it because `URI.create` throws. Separately, rule 2 decodes every segment name with `URLDecoder`, which throws `IllegalArgumentException` on a malformed escape; the handler maps any such exception to the limit 400, so an undecodable name on an unrelated parameter would wrongly reject the request, contradicting "unrelated query parameters are ignored". | At HTTP level the exact-body promise holds only for escapes that survive `URI` parsing. The unrelated-name case is unreachable if the JDK behavior is as expected, but the parser should not depend on that. | **Decision D3 (recommended):** cover malformed escapes in `RunListQueryTest` only, and say in the plan that HTTP-level `%zz` is JDK-handled. Make the parser skip a segment whose name cannot be decoded (treat it as not `limit`), and treat an undecodable `limit` value as invalid. Optionally record the real `%zz` response once with a raw socket at verification. |
| US107-F4 — **P3 (Low)** | **README has no API section to extend.** A search of `README.md` finds no `api/runs` mention; the architecture says to document the endpoint "next to the other API notes", which do not exist (only the `PORT` and `RUN_DURATION_MS` sentences at lines 36-38). `architecture.md` already lists `GET /api/runs` in its API list. | AC-07 needs a new README section, not an edit. Risk of an inaccurate example. | Add a short "API" subsection. Example accuracy checklist from the code: fields in the order `id`, `caseId`, `title`, `status`, `durationMs`, `timestamp`, `failureDetails`; compact JSON with no spaces (state if the example is pretty-printed); `timestamp` is `Instant.toString()` (for example `2026-11-27T10:15:30.123456Z`, 0, 3, 6 or 9 fractional digits); In Progress items have `"failureDetails":""` and a live `durationMs`; a Failed item has `failureDetails` equal to "Simulated UI assertion failed. Review the test steps and expected result."; a Passed item has `""`; use sample IDs only. Mention default 20, `limit` 1 to 100, newest first, the 400 text, that runs are in memory since startup, and that the endpoint is unauthenticated. Compare against a real response at verification. |
| US107-F5 — **P3 (Low)** | **Dead 405 branch with a conflicting message.** After the dispatcher, `handleStartRun` still contains `if (!methodIs(exchange, "POST")) sendMethodNotAllowed(exchange, "POST")` (`DashboardServer.java:105-108`) saying `Allow: POST`. | Two sources of truth for `/api/runs` method handling; a later edit could reintroduce the old header. | Recommended: remove that check once the dispatcher and the POST regression test are in place (the dispatcher is its only caller). Keeping it is harmless but leaves the stale message. |
| US107-F6 — **P4 (Info)** | **Wording slips in `architecture.md`.** (a) "`/api/runs/` ... returns 404 as today": true for GET only; any other method on `/api/runs/` gets 405 `Allow: GET` from `handleGetRun` before the id check (lines 121-129). (b) The AC-04 trace lists malformed-escape forms as returning the exact body (see F3). (c) The status line still says "Design review has not started". (d) The top-level component table does not list the new classes. | Documentation accuracy only. | Fix wording at the next architecture update; no behavior change. |

### Risks, accepted or watched (not defects)

| Risk | Impact | Evidence and disposition |
|---|---|---|
| Listing cost is O(n log n) and allocates an n-element copy per request, while the store is never evicted and `POST /api/runs` is unauthenticated. | Low to Medium | The user approved sorting the whole store. Growth is already possible through POST, but GET adds a repeatable cost per request on 8 server threads. **Accepted as approved.** Revisit trigger: a store of tens of thousands of runs. A bounded selection of the top 100 over the same snapshot would need no second structure and give the same output; it is optional and not recommended now. |
| Unauthenticated enumeration of run IDs, case IDs and titles. | Low | The server binds all interfaces (`new InetSocketAddress(port)`, line 28) and has no authentication by design. Run data contains no secrets, and `GET /api/runs/{id}` was already open. The story accepts this. Document it in the README (F4); no control added. |
| Clock steps or concurrent starts can order two runs by a few microseconds differently from their start. | Low | `startedAt` and `sequence` are captured separately in `start()`. List order follows `timestamp`, which is the documented meaning of "newest". Accepted. |
| HEAD on `/api/runs` answers 405 although RFC 9110 expects HEAD wherever GET is supported. | Low | Approved decision 4 chooses the literal reading; the server already behaves this way for the other endpoints. Accepted. |
| Existing sleep-based tests (40 ms then 100 ms) are timing-sensitive; new HTTP list tests that wait for completion could inherit that. | Low | Use a polling loop with a deadline, as US-105 did, for any test that needs a completed run. |

### Assumptions and open questions

- Assumption (not in the approved text): parameter names are case-sensitive, so `Limit=500` is ignored and the default 20 applies. Names decoded through `URLDecoder`, so `%6Cimit` is `limit`. This follows "unrelated query parameters are ignored" and is low risk.
- Assumption: `limit=%35` (decodes to "5") is accepted. Standard query decoding; consistent with the approved digits-only rule applied to the decoded value.
- Assumption: leading zeros are accepted for `limit` (`05`, `0100` give 5 and 100) but are rejected for `RUN_DURATION_MS`. This is approved and intentional; an implementer must not copy `RunDurationConfig.parse` verbatim. Add a test naming the difference.
- Open question for the user: none beyond D1 to D4.

### Checks requested by the caller, with results

| Check | Result |
|---|---|
| `limit` rules against `URLDecoder` | **Sound.** `URLDecoder` turns `+` into a space, so `+5` becomes " 5" and is rejected, `%2B5` becomes "+5" and is rejected, `%35` becomes "5" and is accepted. Invalid UTF-8 decodes to U+FFFD, which is not a digit and is rejected. Malformed escapes throw `IllegalArgumentException`, which the parser must catch itself (the existing `parseForm` does not, so it leaks the decoder message through `handle`; the architecture correctly avoids reusing it). Splitting the raw query on `&` and `=` before decoding is right. Dropping leading zeros then capping at 3 digits makes `NumberFormatException` impossible, and `99999999999` is rejected. All approved defaults hold: empty and valueless give 400, first repeated `limit` wins, `05` accepted. Gaps: F3 only. |
| 405 for HEAD and OPTIONS | Same helper path already used for HEAD elsewhere; expected to return 405 with `Allow`, but noisy and unverified (F2). OPTIONS has no special JDK handling and is plain. |
| Dispatcher and `POST /api/runs` | **No behavior change found.** POST goes to the unchanged `handleStartRun` (read body up to 4097 bytes, 413, `parseForm`, `start`, 202). A query string on a POST is never read. The generic `IllegalArgumentException` to 400 mapping in `handle` still applies to POST. The sole change is that GET no longer reaches the old 405. Add a POST regression test (202 and body shape, 400 for a bad outcome, 413 for an oversized body). |
| Sort key and comparator | **Safe as designed.** Keys are the final `startedAt` and a final unique `long sequence`, so the order is total and cannot violate the `Comparator` contract. `completedAtNanos`, `durationMs` and status must stay out of the comparator. Assign `sequence` after validation in `start()` so rejected requests do not consume numbers. Convert only the top N with `toResult()`. `ConcurrentHashMap.values()` copying is weakly consistent and never throws. The tie-break itself needs the F1 seam to be testable. |
| Existing tests asserting `Allow: POST` | **None.** `src/test` contains no 405, `Allow`, HEAD or OPTIONS assertions (the only `GET` helper is `DashboardServerTest.get`). The only `Allow: POST` text is in `DashboardServer.java:106` and in `requirements.md` and `architecture.md` prose describing today's behavior. `static/app.js` only calls `POST /api/runs` and `GET /api/runs/{id}`, so no client relies on the old 405. No existing test needs updating. All 405 coverage is new. |
| Response size claim | **Correct.** At most 6 bytes per control character, 120-character title and 40-character case ID, about 1.3 KB per item worst case, so roughly 130 KB for 100 items. |
| README example accuracy | See F4. |

### Acceptance-criteria trace

| Criterion | Review assessment |
|---|---|
| US-107 AC-01 Newest first | Covered by `listRecent` with immutable keys. Tie-break proof needs D1. |
| US-107 AC-02 Empty list | Covered; exact body `{"runs":[]}`. |
| US-107 AC-03 Default and custom limit | Covered; tests for 25 runs without a limit, `limit=3`, 1, 100, `05`. |
| US-107 AC-04 Invalid limit | Covered for `0`, `101`, `abc`, `2.5` and the approved extras at HTTP level; malformed escapes at parser level only (D3). |
| US-107 AC-05 Methods | Covered for PUT, DELETE, HEAD, OPTIONS, plus a POST regression test; HEAD needs D2. |
| US-107 AC-06 Unit tests | Planned lists are adequate once F1 and F2 are addressed. |
| US-107 AC-07 README | Needs a new section (F4). |

### Decisions (D1 to D4 approved by the user, relayed)

| ID | Proposal | Recommendation |
|---|---|---|
| D1 | Add a package-private `ExecutionService(Duration, Clock)` constructor so tests can force equal start times and prove the `sequence` tie-break. | **Yes.** No behavior change; small seam. |
| D2 | In the new 405 overload, send no body for HEAD (`sendResponseHeaders(405, -1)`); add HEAD and OPTIONS tests; record observed stderr at verification. | **Yes.** The other endpoints' helper stays untouched. |
| D3 | Cover malformed escapes in parser unit tests only; make the parser ignore undecodable names on other parameters. | **Yes.** |
| D4 | Add a README "API" subsection with the example checklist in F4, and remove the redundant POST check in `handleStartRun` (F5). | **Yes.** |

If accepted, the exact architecture updates are: (1) in the `ExecutionService` row of the components table, add the package-private `Clock` constructor and say `start` uses `Instant.now(clock)`; (2) in Testability, replace the tight-loop tie-break claim with a fixed-clock test and keep the tight loop as a sanity check; (3) in the `sendMethodNotAllowed` row and the HEAD paragraph, state that the overload sends no body for HEAD; (4) in rule 2 of the `limit` rules, state that segments with undecodable names are skipped and add the HTTP-level `%zz` note; (5) fix the F6 wording and the README row. None changes behavior the user approved. Applied to `architecture.md` after the user approved D1 to D4.

### Approval status

**No High-impact risk is unresolved.** The two Medium findings (F1, F2) and the Low findings (F3 to F6) are resolved at design level by the approved decisions D1 to D4 (the user answered YES to all four, relayed by the coordinating agent). Implementation planning may proceed; implementation itself waits for the user's approval of `impl-plan.md`.
