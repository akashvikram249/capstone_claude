---
name: code-review
description: "Read-only review of a change against the user story, covering correctness, security, error handling, tests, clean code, duplication and dependencies."
argument-hint: "Diff, commit, or changed files"
---

# Code review

1. Read the requirements, the approved architecture and design decisions, and the whole diff.
2. Trace each changed behavior back to an acceptance criterion, and look at the code that calls it and the tests around it.
3. Check whether it is correct, whether input is validated, whether secrets are exposed, how errors are handled, whether both success and failure are tested, and whether new dependencies are safe. Only check what the evidence lets you.
4. Check the changed code against the clean code principles below.
5. Report defects and risks someone can act on, most severe first. For each, give the file and line, the impact, the evidence, a suggested fix, and how confident you are. For a clean code finding, also name the principle it breaks.
6. Label what is confirmed and what is a question. If you find nothing, say what you checked and what you couldn't check.

## Clean code principles

Judge only the lines this change adds or edits, not older code nearby.

- **Names say what a thing is or does.** No vague names (`data`, `tmp`, `handle2`), no abbreviations a new reader must decode, and a boolean reads as a yes/no question.
- **Small functions that do one thing.** A function you can't describe without "and" should be split. Watch for long parameter lists and flag arguments.
- **No duplication.** The same logic in two places will drift. Repeated code is a finding; three similar lines are not.
- **Plain control flow.** Early returns over deep nesting, no clever one-liners, no side effects hidden in a getter or a condition.
- **No magic values.** A bare number or string with a meaning gets a named constant.
- **No dead or speculative code.** Unused methods, parameters, commented-out code, and flexibility nobody asked for.
- **Comments explain why, not what.** A comment that restates the code, or that is out of date, is a finding. A missing comment is only a finding when a hidden constraint would surprise a reader.
- **Errors are handled in one honest way.** No swallowed exceptions, no error codes mixed with exceptions, no messages that leak internals.
- **Tests read as a specification.** One behavior per test, a name that states it, no logic in the test, and no dependence on timing or order.
- **Consistent with the code around it.** Follow the existing style and structure unless the change is meant to improve it.

Clean code findings are usually Low severity. Rate one higher only when it hides a defect or makes the next change risky, and say why. Don't ask for a rewrite of working code that the change didn't touch.

This phase is read-only. Don't edit code, don't run anything destructive, and don't say tests passed unless you ran them.
