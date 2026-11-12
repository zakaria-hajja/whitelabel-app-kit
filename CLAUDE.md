# CLAUDE.md

## Git workflow

Applies to everyone working in this repo (the owner and Claude). These are the same rules as
`backend-api`. They are enforced by `.githooks/` and CI (`.github/workflows/ci.yml`, job
`ci`). Install the hooks once per clone with `git config core.hooksPath .githooks`. The repo is
public, so GitHub enforces branch protection on `main` too: changes land through a PR, the `ci`
check must pass before merging (admins included), and force-pushes and deletion are blocked.
`scripts/github-protect.sh owner/repo` applies these settings; re-run it if they drift. On a
private repo on a free plan, GitHub only applies the merge settings, and a green `ci` before
merging is a rule we keep rather than one GitHub blocks.

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

Phase → branch mapping: see the roadmap (section 7) of
`docs/superpowers/specs/2026-11-10-reusable-app-foundation-design.md`.

## Project pointers

- What this is: a foundation for many Android apps. Shared modules live in `core/*` (and, from Phase 4,
  `feature/*`). Each app is a thin module under `apps/<name>/`. See `docs/architecture.md`.
- New app: `docs/creating-an-app.md`. New shared feature: `docs/adding-a-shared-feature.md`.
- Design: `docs/superpowers/specs/2026-11-10-reusable-app-foundation-design.md` (it supersedes
  the white-label parts of `2026-11-09-whitelabel-android-design.md`).
- Rule: no app identity (name, application ID, tenant, URL, color) outside `apps/<name>/`, and
  shared modules never depend on `apps/*`. `scripts/verify-apps.sh` checks every built app.
- Backend API contract: `../backend-api` (design spec section 9; generated `openapi/openapi.json`).
