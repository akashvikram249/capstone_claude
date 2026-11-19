---
name: implementation
description: "Implement an approved, scoped software task with surgical changes, regression tests, related documentation, and truthful verification."
argument-hint: "Approved task ID or description"
---

# Implementation Phase

1. Read approved requirements, architecture, design-review decisions, and implementation plan.
2. Confirm the task is approved, unblocked, and sufficiently specific. Ask before changing user-visible behavior or architecture.
3. Inspect relevant implementation, tests, helpers, and local conventions before editing.
4. Make the smallest complete change; preserve unrelated user work and existing behavior.
5. Add focused tests for happy paths, validation, and relevant error/edge cases.
6. Update directly related documentation. Do not hand-wave limitations or soften failures.
7. Run the smallest relevant tests/build and record exact results; update task status only with evidence.
8. Stop before commit, push, deployment, or PR creation unless the user explicitly approves.
