## What

<!-- The change, in a few lines. -->

## Why

<!-- The problem or plan phase this serves. -->

## How tested

<!-- Commands run (scripts/check.sh, connected tests, scripts/verify-apps.sh), suites added, manual checks per app. -->
<!-- Touches R8/ProGuard, dependencies, build-logic or release-only code? Add the `release-build` label. -->

## Risks & follow-ups

<!-- What could break, what was deferred. -->

---

- [ ] Commits follow `type(scope): summary`
- [ ] `scripts/check.sh` (unit tests + lint) passes locally
- [ ] No app identity outside `apps/<name>/`; no signing material committed
- [ ] Docs/plan updated if behaviour changed
