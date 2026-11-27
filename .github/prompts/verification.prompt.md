---
description: "Run tests and verify acceptance criteria and generated deliverables"
name: "SDLC 7 — Verification"
agent: "Verification Agent"
argument-hint: "Acceptance criteria and changed deliverables"
model: "Claude Sonnet 4.5 (copilot)"
---

Follow [the verification skill](../skills/verification/SKILL.md). Read requirements, changed files, and review findings.

Run relevant automated tests/builds and validate required generated documents or outputs against their exact acceptance criteria. Report exact commands, test counts/results, output checks, failures, and any criteria not verified.

Do not change code, suppress tests, or treat compilation alone as acceptance evidence. Request user acceptance before PR preparation.
