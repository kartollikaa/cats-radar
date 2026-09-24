---
name: release-app
description: Use when asked to cut, ship, or publish a new Cats Radar release, tag a version, or build a release/debug APK for GitHub — covers bumping the version, the tech/release PR, tagging, and gh release create with the APK attached.
---

# Release Cats Radar

## Overview

Turns `docs/reference/releasing.md` into a runnable checklist. A release is a GitHub
pre-release tagged `v<versionName>`, cut from a merged `tech/release-<version>` PR, with the
APK attached. Read that doc for the *why*; this skill is the *how*, including the gotchas that
doc doesn't cover because they're about the tooling, not the product.

## When to use

Triggers: "publish a release", "cut a release", "ship a new version", "tag a release", "build
the release/debug APK for GitHub", or any request naming a version bump plus a GitHub release.

Not for: an internal test build with no tag/release (`./gradlew :app:assembleDebug` and hand
over the APK — no version bump, no PR, no tag).

## Before you start

1. **Read `docs/reference/releasing.md`** — the versioning rule, the signing key properties,
   and why a debug build never installs over a release build.
2. **Check whether `main` moved.** `git fetch origin main && git log --oneline <last-tag>..origin/main`.
   Another session may have merged work you don't know about — the release should ship
   everything on `main`, not just what you expected. Re-run this check again right before
   merging the version-bump PR; `main` can move again while you work.
3. **In a worktree, copy `local.properties` from the primary checkout first** — a fresh worktree
   has no `sdk.dir` and Gradle will refuse to configure.

## Steps

1. **Pick the version.** Bump `app-versionCode` by exactly one (Android refuses an update whose
   code isn't higher) and `app-versionName` in `gradle/libs.versions.toml`. Decide the semantic
   bump from what actually shipped since the last tag (step 2's log), not from habit: a new
   epic slice is a minor bump, a batch of fixes/polish is a patch, in both cases keeping the
   `-beta`/`-alpha` suffix already in use.
2. **Draft the changelog from the first-parent log**, not the full log — every PR merge is a
   first-parent commit, and everything else is that PR's internal history (including its own
   `fix: review` / `test: gate` follow-up commits, which are noise here):
   ```
   git log --first-parent --oneline <last-tag>..origin/main
   ```
   Group the user-facing ones by feature area (see any past release body with
   `gh release view <tag> --json body`), and drop pure-test/tech PRs (`test/...`,
   `tech/...` with no behaviour change) from the notes — they still ship, they're just not
   worth a line.
3. **Branch, bump, PR.** `tech/release-<version>` off the latest `main` (never off a stale
   local branch — see step "Before you start"). Commit only the version bump. Run
   `./gradlew check` locally and confirm it's green — the merge rule is a green local check, not
   waiting on GitHub Actions to finish. Open the PR and run `/code-review` on it (trivial diff,
   but it's still a slice), then merge with a merge commit (`gh pr merge <n> --merge`), never
   squash — one PR per slice, landed with a merge commit, per this project's `CLAUDE.md`.
4. **Re-fetch and pin the merge commit.** `git fetch origin main` and read the merge commit's
   SHA off `git log --oneline -1 origin/main` — don't assume it's what you pushed; something
   else may have merged in the gap. Build from that exact SHA (`git checkout --detach <sha>`
   in a clean worktree, or check out `main` after a fast-forward pull).
5. **Build the APK(s).**
   - Debug: `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
   - Release (only when `~/.gradle/gradle.properties` has all four
     `catsradar.release.*` properties): `./gradlew :app:assembleRelease` →
     `app/build/outputs/apk/release/app-release.apk`, then confirm the signature with
     `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` — it must
     show the release key's certificate, not `Android Debug`. Without the properties,
     `assembleRelease` still succeeds but produces `app-release-unsigned.apk`, which no phone
     will install — don't attach that; ship debug-only and say so in the release body, the way
     past pre-releases have ("The signed release build will be added to this release").
   - The release build is minified by R8, which the debug build never is. Install the signed
     APK and walk the by-name paths listed in `docs/reference/releasing.md` (widget tap, a
     tally, backup export + import, a screen surviving a background kill) before tagging —
     a missing keep rule shows up nowhere else. On the shared emulator, a release build signed
     with the debug key and given an `applicationIdSuffix` through a scratchpad Gradle init
     script leaves other sessions' installs alone; give it a distinct `app_name` too, or its
     widget is indistinguishable from theirs in the launcher's picker.
   - Rename to the convention before attaching: `cats-radar-<versionName>.apk` (release) /
     `cats-radar-<versionName>-debug.apk` (debug), and the release build's
     `app/build/outputs/mapping/release/mapping.txt` → `cats-radar-<versionName>-mapping.txt`.
     The mapping from any other build does not fit this APK's stack traces.
6. **Tag and publish.**
   ```
   gh release create v<versionName> --prerelease --target <merge-commit-sha> \
     --title "v<versionName> — <one-line theme>" \
     --notes-file <release-notes.md> \
     <renamed-apk-path...> <renamed-mapping-path>
   ```
   `--target` matters: the tag doesn't exist yet, and without it the release would tag
   whatever `HEAD` happens to be in the *local* repo, not the merge commit on `main`. The
   upload can exceed the default tool timeout — expect it to run in the background and
   don't retry mid-upload.

## Release notes shape

Match the last release's structure (`gh release view <last-tag> --json body`): a one-line
tagline, `## What's new since v<last>` grouped by feature area (bold area name, bullets in
plain user language — no PR titles, no internal type names), `## Install` naming the exact
attached filename and what it installs over, and `## Honest caveats` — what wasn't tested on a
real device, what still needs a network connection, what a missing permission or Play Services
degrades to. Keep the voice consistent: describing user-visible behaviour, not implementation.

## Common mistakes

- **Building from your local branch tip instead of the merge commit.** If the PR merged as a
  non-fast-forward merge commit, your branch tip's tree can differ from what's actually on
  `main` if anything landed in between. Always re-fetch and build from `origin/main`'s SHA.
- **Squash-merging the release PR.** Breaks the per-PR merge-commit history convention; use
  `gh pr merge --merge`.
- **Attaching `app-release-unsigned.apk`.** It installs on nothing. Verify the signer before
  attaching, or don't attach a release build at all.
- **Writing release notes from the full commit log.** Pulls in every `fix: review` / `test:
  gate` micro-commit from every PR's internal history; use `--first-parent`.
- **Forgetting `--target` on `gh release create`.** Tags whatever commit is checked out
  locally instead of the actual merge commit.
