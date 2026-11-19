---
name: code-review
description: "Perform a structured, read-only peer review for correctness, security, errors, tests, clarity, DRY, and dependency safety against the user story."
argument-hint: "Diff, commit, or changed files"
---

# Code Review Phase

1. Read requirements, approved architecture/design decisions, and the complete relevant diff.
2. Trace changed behavior to acceptance criteria and inspect related call sites/tests.
3. Check correctness, security and input validation, secrets, error handling, happy/error test coverage, readability, duplication, and dependencies where evidence is available.
4. Report only actionable defects or meaningful risks, ordered by severity. Include file/line, impact, evidence, recommended fix, and confidence.
5. Distinguish confirmed issues from questions. If no findings, state what was checked and verification limits.

This phase is read-only. Do not edit code, run destructive commands, or claim unrun tests passed.
