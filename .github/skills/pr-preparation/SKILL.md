---
name: pr-preparation
description: "Prepare a truthful pull request summary, changes list, test evidence, known limitations, changelog, and reviewer checklist from verified work."
argument-hint: "Verified diff and actual test evidence"
---

# PR Preparation Phase

1. Read the approved story, verified diff, verification results, known limitations, changelog, and PR template.
2. Draft a 2–3 sentence summary explaining what changed and why.
3. List files changed and their purpose; include only files present in the diff.
4. Include exact test evidence and CI links only when they exist.
5. List unresolved findings, missing evidence, out-of-scope behavior, or "Not Found" outcomes as limitations.
6. Add a concise changelog entry and a reviewer checklist mapped to acceptance/security/test requirements.
7. Ask for explicit approval before commit, push, deploy, or opening a PR. If remote capability is missing, leave preparation local and say so.

Do not write code or claim a PR was opened when only its description was prepared.
