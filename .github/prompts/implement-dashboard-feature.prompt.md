---
description: "Implement or improve a dashboard feature while preserving US-102 behavior and demo boundaries"
name: "Implement Dashboard Feature"
agent: "agent"
---

Read [requirements.md](../../requirements.md), [architecture.md](../../architecture.md), and [design-review.md](../../design-review.md). Implement the requested dashboard feature using the existing Java and browser patterns.

- Preserve validation, duplicate-ID protection, report history, and confirmation before deletion.
- Escape user-controlled strings when rendering HTML.
- Keep demo sign-in, local browser storage, and simulated test execution clearly labeled; do not imply production security or real browser automation.
- Add focused tests for the changed behavior and its relevant error/edge cases.
- Run `mvn test` and report actual results. Update the related documentation when behavior changes.
