---
name: dashboard-feature
description: "Implement and verify UI test case dashboard features. Use when changing case CRUD, search and filters, simulated test runs, execution reports, or dashboard styling."
argument-hint: "Dashboard feature or acceptance criterion"
---

# Dashboard Feature Workflow

1. Read [requirements.md](../../../requirements.md), [architecture.md](../../../architecture.md), and [design-review.md](../../../design-review.md).
2. Identify which acceptance criterion the requested change affects.
3. Follow the existing division: Java HTTP service and demo runner; browser state and UI in `src/main/resources/static/`.
4. Preserve duplicate-ID validation, report history on deletion, browser-only storage, and explicit demo boundaries.
5. Escape untrusted values before rendering HTML and handle missing/invalid data visibly.
6. Add or update focused tests in `src/test/java/`.
7. Run `mvn test`; report the command and its actual outcome.
8. Update README/design docs if the behavior or scope changes.
