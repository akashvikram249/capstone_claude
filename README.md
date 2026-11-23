# Caseflow — UI Test Case Management Dashboard

A Java 17 capstone demo for managing UI test cases and viewing execution results.

## What it does

- Shows test cases by name, module, priority, status, and last result.
- Searches cases and filters by module, priority, and status.
- Creates and edits test cases with required-field validation and unique IDs.
- Confirms before deletion and keeps existing run reports.
- Simulates asynchronous test runs that finish Passed or Failed.
- Stores cases and reports in browser local storage.
- Shows reports with status, duration, execution time, and simulated failure details.
- Filters report history by All, Passed, or Failed in the current page; the filter is not saved.

## Important demo limitations

- The sign-in screen is a demo gate, not real authentication. Any non-empty name works.
- The runner is simulated. It does not launch a browser or run Selenium/Playwright tests.
- Cases and reports live only in the current browser profile. Clearing browser data removes them.
- Structurally invalid saved report data is reported without automatic repair or deletion.
- Selecting Pass/Fail is only for demonstrating the two report outcomes.

## Requirements

- Java 17
- Maven 3.8+

## Run locally

```sh
mvn test
mvn exec:java
```

Open [http://localhost:8080](http://localhost:8080), enter any display name, and start exploring. Override the port with `mvn exec:java -Dexec.args="8090"` or set `PORT`.

Set the simulated test run duration with the `RUN_DURATION_MS` environment variable, in milliseconds. The default is 1400. Valid values are whole numbers from 100 to 10000; surrounding whitespace is ignored, and an unset or blank value uses the default. Any other value stops startup with the error `RUN_DURATION_MS must be a whole number from 100 to 10000.` and a non-zero exit code (2), and the server does not start. Put the variable on the same command line as the launcher, for example `RUN_DURATION_MS=500 mvn exec:java`.

Caution: if you reload or close the page while a run is in progress, that case keeps showing "In Progress" and its Run button stays disabled. This is a known limitation, and longer durations make it easier to hit.

## Run statistics API

`GET /api/stats` returns how many runs the server has started and their current status:

```sh
curl http://localhost:8080/api/stats
{"totalRuns":3,"inProgress":1,"passed":1,"failed":1}
```

The body always has exactly these four integer fields, in this order, and `totalRuns` equals `inProgress + passed + failed`. Counts are server-wide (not per browser, user, or case) and held in memory only, so they reset to 0 when the server restarts. Only runs accepted by `POST /api/runs` (HTTP 202) are counted; requests rejected with 400 or 413 are not. Any method other than GET returns 405 with an `Allow: GET` header.

## Test

```sh
mvn test
```

The tests cover execution state transitions, server endpoints, validation errors, missing runs, static page delivery, and run-duration startup configuration. GitHub Actions runs `mvn verify` for pushes to `main` and pull requests.

## Project guide

- [requirements.md](requirements.md) — user story and acceptance criteria
- [architecture.md](architecture.md) — components, data flow, API, and scope boundaries
- [design-review.md](design-review.md) — reviewed decisions and limitations
- [impl-plan.md](impl-plan.md) — dependency-ordered delivery plan
- [SDLC-PIPELINE.md](SDLC-PIPELINE.md) — guided eight-phase Copilot SDLC cycle and approval gates
- [CHANGELOG.md](CHANGELOG.md) — project changes
- [.github/PULL_REQUEST_TEMPLATE.md](.github/PULL_REQUEST_TEMPLATE.md) — PR description and reviewer checklist

## Copilot helpers

Select **SDLC Orchestrator** in Copilot Chat to guide work from a user story through PR preparation. The orchestrator delegates each phase to its own specialist agent, prompt, and skill:

1. Requirements
2. Architecture
3. Design review
4. Implementation planning
5. Implementation
6. Code review
7. Verification
8. PR preparation

The orchestrator pauses at human approval gates. It does not commit, push, deploy, or open a PR without your explicit approval. See [SDLC-PIPELINE.md](SDLC-PIPELINE.md) for phase details and direct invocation instructions. The separate Dashboard Reviewer is a read-only reviewer for US-102-specific dashboard changes.

Two workspace hooks are configured in [.github/hooks/hooks.json](.github/hooks/hooks.json): SessionStart prints concise SDLC/repository context; PostToolUse runs Maven tests when the pending Java/dashboard source snapshot changes. The test hook runs Maven and may take a little time; repeated tool calls with the same source snapshot are skipped.
