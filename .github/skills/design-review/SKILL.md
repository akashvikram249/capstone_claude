---
name: design-review
description: "Conduct a structured architecture/design review against requirements, identifying risks, gaps, decisions, and mitigations before coding."
argument-hint: "Requirements and architecture documents"
---

# Design Review Phase

1. Read approved `requirements.md` and `architecture.md`; stop if either is missing or not approved.
2. Review requirement coverage, data flow, trust boundaries, failure behavior, security/privacy, scale, operability, testability, maintainability, and dependency assumptions.
3. Record only evidence-based, actionable findings; rank by severity and include impact, evidence, and mitigation.
4. Separate findings from open questions and design trade-offs; do not treat proposed decisions as approved.
5. Document findings and agreed decisions in `design-review.md`.
6. Propose precise architecture updates for accepted findings; ask before applying behavior-changing decisions.
7. Pause for human approval before implementation planning.

Do not write production code.
