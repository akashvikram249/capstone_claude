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
