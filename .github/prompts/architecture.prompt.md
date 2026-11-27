---
description: "Design a high-level architecture based on approved requirements"
name: "SDLC 2 — Architecture"
agent: "Architecture Agent"
argument-hint: "Approved requirements document (default: requirements.md)"
model: "Claude Opus 4.5 (copilot)"
---

Follow [the architecture skill](../skills/architecture/SKILL.md). Use approved `requirements.md` as the source of truth and inspect repository conventions.

Create or update `architecture.md` with component responsibilities, a diagram, data flow, interfaces, technology choices, security/error handling, trade-offs, assumptions, and operational boundaries. Trace decisions to requirements, identify risks, and request approval before design review.

Do not implement production code or silently change requirements.
