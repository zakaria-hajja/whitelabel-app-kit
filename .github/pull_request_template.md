## What

<!-- The change, in a few lines. -->

## Why

<!-- The problem or plan phase this serves. -->

## How tested

<!-- Commands run (scripts/check.sh, connected tests, scripts/verify-variants.sh), suites added, manual checks per client. -->

## Risks & follow-ups

<!-- What could break, what was deferred. -->

---

- [ ] Commits follow `type(scope): summary`
- [ ] `scripts/check.sh` (unit tests + lint) passes locally
- [ ] No client-specific values outside `clients/<id>/`; no signing material committed
- [ ] Docs/plan updated if behaviour changed
