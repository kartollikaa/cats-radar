---
name: create-pr
description: Use when a Cats Radar slice is committed and needs a correctly named remote branch, a draft pull request, a repaired stalled push, an updated PR body, or a draft moved to ready after its review and acceptance evidence are complete.
---

# Create a Cats Radar pull request

## Overview

Publish one review-sized slice without trusting a stalled command or creating duplicate PRs.
Push and PR creation are external mutations: require authority for the current task immediately
before performing them.

## Before the first push

Record `git branch --show-current`, `git rev-parse HEAD`, `git status --short --branch`, and the
divergence from `origin/main`. The tree must contain only the slice.

An agent-generated branch name never reaches GitHub. Before the first push, rename `claude/...`,
`codex/...` or any non-project prefix to this repo's `feature/`, `fix/` or `tech/` convention:

```bash
git branch -m claude/example fix/example
BRANCH=$(git branch --show-current)
```

Never rename a remote branch that already heads an open PR.

## Push and prove it landed

Run the push in the background when the tool supports it. A silent process may still be uploading;
do not start a second push while it lives. On a stall or HTTP/2 framing/disconnect error, wait for
the first process to end, then retry once over HTTP/1.1:

```bash
git push -u origin "$BRANCH"
git -c http.version=HTTP/1.1 push -u origin "$BRANCH"
```

Do not trust the push exit code. Prove the remote branch equals the committed local head:

```bash
LOCAL_SHA=$(git rev-parse HEAD)
REMOTE_SHA=$(git ls-remote --heads origin "$BRANCH" | awk '{print $1}')
test -n "$REMOTE_SHA" && test "$REMOTE_SHA" = "$LOCAL_SHA"
```

## Refuse a duplicate PR

Check the live API before creating anything:

```bash
gh api repos/kartollikaa/cats-radar/pulls -X GET \
  -f state=open -f "head=kartollikaa:$BRANCH" \
  --jq '.[] | {number,html_url,isDraft:.draft}'
```

If this returns a PR, update that PR; never create another one for the same head.

## Create the draft through REST

End the body with exactly:

```text
🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

The body includes scope, frozen criteria/verdicts, verification and positive controls, docs impact,
review state and remaining manual evidence.

```bash
gh api repos/kartollikaa/cats-radar/pulls -X POST \
  -f title="$TITLE" -f head="$BRANCH" -f base=main \
  -F draft=true -F body=@"$BODY_FILE" \
  --jq '{number,html_url,draft}'
```

Use `-F draft=true`, not `-f draft=false`: the former sends a JSON boolean. `gh pr create` is not
used here because it can hang silently and later create a duplicate.

## Mark ready through GraphQL

Only after review findings and acceptance evidence are resolved:

```bash
PR_NODE_ID=$(gh api "repos/kartollikaa/cats-radar/pulls/$PR" --jq .node_id)
gh api graphql \
  -f query='mutation($id:ID!){markPullRequestReadyForReview(input:{pullRequestId:$id}){pullRequest{isDraft}}}' \
  -f id="$PR_NODE_ID"
```

Read final truth from `gh api`, because the desktop PR cache can lag.

## Common mistakes

| Mistake | Correct action |
|---|---|
| Open a second PR after a silent command | Query open PRs by head first |
| Trust `git push` success | Compare `ls-remote` with `rev-parse HEAD` |
| Push `claude/...` or `codex/...` | Rename before the first push |
| Mark ready before review/gate | Keep the PR draft |
