---
name: merge-pr
description: Use when the owner asks to merge a Cats Radar pull request, main may have moved, a reviewed branch needs its final local gate, GitHub Actions are still running, mergeability is stale, or a stack of pull requests must land safely.
---

# Merge a Cats Radar pull request

## Overview

Merge the exact reviewed head with a merge commit after proving it contains one pinned current
`main` and passes the local gate. A request to implement or review is not permission to merge;
require the owner's merge instruction in this task.

Never squash, force-push a reviewed branch, or wait for GitHub Actions after the equivalent local
gate is green.

Do not wait for GitHub Actions: the local gate is the merge gate for this repository.

## Pin main before integrating it

Fetch once, capture the value, and use that SHA throughout conflict resolution:

```bash
test -z "$(git status --porcelain)"
git fetch origin main
TARGET_MAIN=$(git rev-parse origin/main)
git merge "$TARGET_MAIN"
```

Never merge the live name `origin/main`: another session can move the shared ref while the merge is
in progress. Resolve only conflicts that belong to this slice. A conflict in another epic's spec or
`docs/tbd/decompositions/` is an owner decision: stop and ask instead of combining the prose.
After any manual conflict resolution, commit it and rerun `/code-review` plus the acceptance gate
over the whole final diff. Do not treat the earlier review as covering newly written resolution.

## Gate the tree that will land

```bash
test -z "$(git status --porcelain)"
CI=true ./gradlew check :app:assembleRelease --console=plain
test -z "$(git status --porcelain)"
```

`CI=true` is required so this verification build cannot upload an R8 mapping to Crashlytics. A
failed command stops the chain; never grep display output and continue to a merge.

Push the integrated head with the `create-pr` push/`ls-remote` procedure. Do not rewrite reviewed
commits.

## Close the moving-main race

Immediately before merging:

```bash
test -z "$(git status --porcelain)"
git fetch origin main
BEHIND=$(git rev-list --count HEAD..origin/main)
test "$BEHIND" = 0
HEAD_SHA=$(git rev-parse HEAD)
REMOTE_SHA=$(git ls-remote --heads origin "$(git branch --show-current)" | awk '{print $1}')
test "$REMOTE_SHA" = "$HEAD_SHA"
gh pr merge "$PR" --merge --match-head-commit "$HEAD_SHA"
```

If `BEHIND` is not zero, pin the new main SHA, merge it, rerun the local gate, push and repeat these
guards. A transient 405 or non-clean `mergeable_state` just after a push means GitHub may still be
recomputing; wait for the live API state, but do not wait for Actions.

## Merge a stack

1. Merge the bottom PR first with the same guards. Keep its branch; deleting it can close the PR
   above before retargeting.
2. Retarget the upper PR to `main`:
   ```bash
   gh api "repos/kartollikaa/cats-radar/pulls/$UPPER" -X PATCH -f base=main
   ```
3. Inspect every remaining file before proceeding:
   ```bash
   gh api "repos/kartollikaa/cats-radar/pulls/$UPPER/files" --paginate --jq '.[].filename'
   ```
   The list must contain only the upper slice.
4. Pin current main into the upper branch, run the local gate, push, re-check zero behind and merge
   with the upper head SHA.

## Common mistakes

| Mistake | Correct action |
|---|---|
| `git merge origin/main` | Capture `TARGET_MAIN`, then merge that SHA |
| Merge while `HEAD..origin/main` is non-zero | Integrate and re-gate current main |
| Wait for GitHub Actions | Use the green local gate and merge on owner instruction |
| Delete the lower stack branch immediately | Retarget and inspect the upper PR first |
| Resolve another epic's docs conflict | Stop and ask the owner |
| Gate a dirty tree | Stop; commit the intended slice, then review and gate that exact head |
