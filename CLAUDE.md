# CLAUDE.md

## Git workflow

Applies to everyone working in this repo (the owner and Claude). These are the same rules as
`backend-api`. They are enforced by `.githooks/` and CI (`.github/workflows/ci.yml`, job
`ci`). Install the hooks once per clone with `git config core.hooksPath .githooks`. If the repo
is private on a free plan, GitHub cannot enforce branch protection; merging only with a green
`ci` check is then a rule we keep, not one GitHub blocks. `scripts/github-protect.sh owner/repo`
applies the merge settings, and also branch protection where the plan allows it.

1. **Commits**: Conventional Commits, `type(scope): summary`.
   - Types: `feat fix refactor test docs chore ci perf build revert`.
   - Scope is optional and lowercase kebab-case. Write the summary in the imperative mood. Header ≤ 72 chars.
   - The body explains *why* when it isn't obvious. One logical change per commit.
   - Claude's commits end with its `Co-Authored-By` trailer.
2. **Branches**: never commit to `main`. Names are `feat/`, `fix/`, `chore/`, `docs/` or `ci/`
   followed by lowercase kebab-case. One branch per plan phase or user-visible capability;
   small fixes get their own short branch.
3. **Before pushing**: `scripts/check.sh` must pass (Gradle `check`, meaning unit tests and lint
   for every module, plus the build-logic tests). The pre-push hook runs it. Instrumented
   (emulator) tests are run locally at phase checkpoints and in CI once Phase 5 adds that job.
4. **PRs**: one per phase/feature, filled in from the PR template, with CI green. Merged with a
   merge commit (squash/rebase are disabled), and the head branch is deleted on merge.
   `git log --first-parent main` gives the one-line-per-feature view.
5. **History**: never force-push `main`. On your own feature branch, use only
   `git push --force-with-lease`, and only before the PR is reviewed.
6. **Claude**: branches, commits, pushes and opens PRs (`gh pr create`) as part of
   planned work; **the owner merges**. Never bypass hooks (`--no-verify`) unless the owner
   says so for that instance.

Phase → branch mapping for the initial build: see "Phases and PRs" in
`docs/superpowers/plans/2026-11-09-whitelabel-android-foundation.md`.

## Project pointers

- Architecture (Phase 1, approved before code): `docs/superpowers/specs/2026-11-09-whitelabel-android-design.md`
- Brief: `docs/superpowers/specs/2026-11-09-whitelabel-android-brief.md`
- Backend API contract: `../backend-api` (design spec §9; generated `openapi/openapi.json`)
- Client-specific values live only in `clients/<id>/`. No Kotlin or Gradle file names a client.
