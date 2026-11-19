---
description: "Run a structured read-only peer review against requirements and implementation"
name: "SDLC 6 — Code Review"
agent: "Code Review Agent"
argument-hint: "Diff, commit, or changed files to review"
---

Follow [the code-review skill](../skills/code-review/SKILL.md). Review the supplied diff against `requirements.md`, `architecture.md`, and `design-review.md`.

Evaluate correctness, security, error handling, test coverage, clarity, duplication, and dependencies where evidence exists. Return only actionable findings, ranked by severity with file/line, impact, and confidence. If no findings, state what evidence you inspected and any limits.

This is read-only. Do not fix findings or assert tests passed without evidence.
