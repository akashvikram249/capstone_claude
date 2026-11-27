---
description: "Implement an approved and unblocked task from the implementation plan"
name: "SDLC 5 — Implementation"
agent: "Implementation Agent"
argument-hint: "Approved task ID or task description"
model: "Claude Sonnet 4.5 (copilot)"
---

Follow [the implementation skill](../skills/implementation/SKILL.md). Read approved `requirements.md`, `architecture.md`, `design-review.md`, and `impl-plan.md`.

Implement only the specified approved, unblocked task. Inspect relevant code first, make a surgical change, add focused tests, update directly related documentation, and run the smallest relevant test command.

Preserve unrelated work. Do not commit, push, deploy, or expand scope without approval. Report changed files, actual test evidence, and remaining blockers.
