# Agentic SDLC Pipeline

This workspace packages the full eight-phase SDLC as Copilot custom agents, prompts, and skills. Start with **SDLC Orchestrator** for a guided cycle, or invoke a phase agent/prompt/skill directly for one stage.

## Run a guided cycle

1. Open Copilot Chat and select **SDLC Orchestrator**.
2. Provide a user story or point to its source. For this capstone, the current story is [US-102 in requirements.md](requirements.md).
3. The orchestrator works through the phases below in order. It loads the matching skill and prompt for each phase, invokes that phase's specialist agent, and summarizes the outcome.
4. Review and approve requirements before architecture, architecture before implementation, and implementation before verification/PR preparation. The orchestrator must pause for your decision at these gates.
5. To pause or resume, ask it to stop at a phase boundary or continue from the last approved phase. It should inspect the repository and existing artifacts rather than assume the cycle state.

Agents can draft and edit repository artifacts. **Nothing is committed, pushed, deployed, or opened as a pull request without your explicit approval.** The PR phase prepares a description, changelog, and reviewer checklist; publishing requires an available GitHub integration and your confirmation.

## Phases

| # | Phase | Agent | Main artifact / outcome |
|---:|---|---|---|
| 1 | Requirements | Requirements Agent | `requirements.md` — clarified functional and non-functional requirements and acceptance criteria |
| 2 | Architecture | Architecture Agent | `architecture.md` — system components, data flow, technology choices, boundaries |
| 3 | Design review | Design Review Agent | `design-review.md` and agreed architecture updates |
| 4 | Implementation planning | Planning Agent | `impl-plan.md` — prioritized, dependency-ordered tasks and blockers |
| 5 | Implementation | Implementation Agent | Code, focused tests, and directly related documentation |
| 6 | Review | Code Review Agent | Structured findings against the acceptance criteria and review checklist |
| 7 | Verification | Verification Agent | Test/build/quality evidence and output-document checks |
| 8 | PR preparation | PR Preparation Agent | Changelog entry and PR description with test evidence, limitations, and checklist |

Every phase has three matching files:

- Agent: `.github/agents/<phase>.agent.md`
- Prompt: `.github/prompts/<phase>.prompt.md`
- Skill: `.github/skills/<phase>/SKILL.md`

The agent defines the role and boundaries; the prompt is a reusable task starter; the skill defines the repeatable step-by-step workflow. Specialists link to their matching prompt and skill so that using a phase agent includes both.

## Human approval gates

- **After requirements:** confirm assumptions, acceptance criteria, and scope.
- **After design review:** approve architecture and identified trade-offs before implementation.
- **After review and verification:** decide whether findings are fixed and checks are sufficient.
- **Before publishing:** explicitly approve any commit, push, deployment, or PR creation.

The pipeline records useful evidence in repository documents. It does not claim a phase is complete when required input, approval, or verification is missing; it records that phase as blocked and explains why.

## Useful commands

- Run the dashboard tests: `mvn test`
- Build and run all CI verification: `mvn --batch-mode verify`
- Start the dashboard: `mvn exec:java`

## Scope boundary

This workflow guides agent-assisted work in this repository; it does not implement a production SDLC platform, Jira/Confluence connector, autonomous deployment, or real authentication. The existing dashboard's demo limitations remain as stated in [README.md](README.md).

## Lifecycle hooks

Two workspace hooks are configured in `.github/hooks/hooks.json`:

- **SessionStart** runs `scripts/hooks/session_start.py`. It prints the repository, branch, whether pre-existing changes are present, the pipeline guide, and the approval/publishing reminders.
- **PostToolUse** runs `scripts/hooks/post_tool_use.py`. When pending changes include Java source, Java tests, dashboard assets, or `pom.xml`, it runs `mvn --batch-mode test`. It records the tested source snapshot in the system temporary directory so unrelated tool calls do not rerun the same test set. A changed source snapshot triggers a new run; test failure is reported with a non-zero hook exit code.

The test hook can take a couple of minutes and runs after Copilot tool actions, not only after file edits. When there are no relevant pending source changes, or the same source snapshot was already tested, it exits quietly.
