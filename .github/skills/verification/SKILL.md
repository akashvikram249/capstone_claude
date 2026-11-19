---
name: verification
description: "Run appropriate unit/integration/build checks and verify acceptance criteria and final document/output quality using concrete evidence."
argument-hint: "Acceptance criteria, changed files, and expected outputs"
---

# Verification Phase

1. Read the approved acceptance criteria, implementation diff, review findings, and documented commands.
2. Select the smallest safe test/build commands that cover the change; inspect scripts before running unfamiliar or destructive commands.
3. Run checks and report exact commands, counts, results, and material failures. Do not fabricate logs or claim unrun checks.
4. Verify each criterion explicitly against behavior or its required output; compilation alone is not acceptance.
5. Check generated documents for required sections, consistency, and stale content when applicable.
6. Report limitations, untested paths, and known failures without hiding them.
7. Present evidence and wait for human acceptance before PR preparation.

Do not alter product code or suppress failing tests.
