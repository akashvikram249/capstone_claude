---
description: "Run a structured read-only peer review against requirements and implementation"
name: "SDLC 6 — Code Review"
agent: "Code Review Agent"
argument-hint: "Diff, commit, or changed files to review"
model: "Claude Opus 4.5 (copilot)"
---

Follow [the code-review skill](../skills/code-review/SKILL.md). Review the supplied diff against `requirements.md`, `architecture.md`, and `design-review.md`.

Evaluate correctness, security, error handling, test coverage, dependencies, and the clean code principles in the skill (names, small single-purpose functions, no duplication, plain control flow, no magic values or dead code, comments that explain why, honest error handling, readable tests) where evidence exists. Return only actionable findings, ranked by severity with file/line, impact, and confidence; name the principle for a clean code finding. If no findings, state what evidence you inspected and any limits.

This is read-only. Do not fix findings or assert tests passed without evidence.
