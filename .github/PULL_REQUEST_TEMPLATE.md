## Summary

Describe the dashboard behavior added or changed and how it supports US-102.

## Author Profile

[GitHub profile: @akashvikram249](https://github.com/akashvikram249)

## Changes Made

- List the implementation and documentation files changed, with a short reason for each.

## Test Evidence

- `mvn test`:
- UI/API checks:
- CI result:

## Known Limitations

- Demo sign-in is not real authentication.
- Test execution is simulated; no browser automation suite is invoked.
- Case data and reports are browser-local and do not sync across users/devices.
- List any additional limitations or “Not Found” behavior.

## Reviewer Checklist

- [ ] Dashboard case list shows name, module, priority, status, and last result.
- [ ] Search and module/priority/status filters work.
- [ ] Required fields are validated and duplicate IDs are rejected.
- [ ] Edits update the table; deletion requires confirmation.
- [ ] Running a case shows In Progress then Passed/Failed.
- [ ] Reports show status, duration, timestamp, and failure details when needed.
- [ ] User-entered content is escaped and error states are understandable.
- [ ] `mvn test` passes and demo-only limitations are clearly labeled.
