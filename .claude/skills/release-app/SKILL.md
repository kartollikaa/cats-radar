---
name: release-app
description: Use when asked to cut, ship, or publish a new Cats Radar release, tag a version, build a release/debug APK for GitHub, or get a release's R8 mapping into Crashlytics so release crash reports are readable.
---

# Release Cats Radar

## Overview

Turns `docs/reference/releasing.md` into a runnable checklist. A release is a GitHub
pre-release tagged `v<versionName>`, cut from a merged `tech/release-<version>` PR, with the
APK and its zipped R8 mapping attached, and the same mapping uploaded to Crashlytics. Read that doc for the *why*; this skill is the *how*, including the gotchas that
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
   - Release (only when `~/.gradle/gradle.properties` has `kartollika.signingFile` and the
     file it names exists — see `releasing.md` "The signing key"), **one invocation that builds the APK and uploads its
     mapping to Crashlytics**, with `CI` unset (the upload is off whenever `CI` is set) and the
     network up:
     ```
     mkdir -p build && env -u CI ./gradlew :app:assembleRelease --console=plain > build/release-build.log 2>&1; echo "exit=$?"
     ```
     → `app/build/outputs/apk/release/app-release.apk` and
     `app/build/outputs/mapping/release/mapping.txt`. A rebuild can stamp a new mapping id into
     its APK, so the APK you attach, the mapping you zip and the mapping Crashlytics received must
     all come from this one run: after it, never rebuild, and never run
     `uploadCrashlyticsMappingFileRelease` on its own "to confirm". Confirm from what this run
     left behind instead:
     - the log shows `> Task :app:uploadCrashlyticsMappingFileRelease` with nothing after it —
       `SKIPPED`, `UP-TO-DATE`, `FROM-CACHE`, `NO-SOURCE` or `FAILED` all mean no upload in this run;
     - `aapt2 dump resources app/build/outputs/apk/release/app-release.apk | grep -A1 crashlytics.mapping_file_id`
       (`aapt2` is in the SDK's `build-tools/<version>/`)
       is not `00000000000000000000000000000000` (all zeros means the upload was off).

     If the upload failed (offline), run the whole command again once online and ship that run's
     APK and mapping. Then confirm the signature with
     `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` — it must
     show the release key's certificate, not `Android Debug`. Without the signing file,
     `assembleRelease` still succeeds but produces `app-release-unsigned.apk`, which no phone
     will install — don't attach that; ship debug-only and say so in the release body, the way
     past pre-releases have ("The signed release build will be added to this release").
   - The release build is minified by R8, which the debug build never is. Install **that same
     signed APK** and walk the by-name paths listed in `docs/reference/releasing.md` (widget tap,
     a tally, backup export + import, a screen surviving a background kill) before tagging — a
     missing keep rule shows up nowhere else. Use a throwaway AVD (copy
     `~/.android/avd/Pixel_7.avd/config.ini` into a new `<Name>.avd/` plus a `<Name>.ini` pointing at
     it, boot it on a free port, delete both afterwards): the shared emulator holds
     other sessions' debug-signed `com.kartollika.catsradar`, which a release-signed APK cannot
     update without uninstalling it and their data. An `applicationIdSuffix` build no longer
     compiles — `app/google-services.json` has a client for `com.kartollika.catsradar` only.
     If the walk finds a bug, fix it through a PR and restart from step 4; any local release
     build made just to try something runs with `CI=true`, so it uploads nothing.
   - A debug-only release (no signing file) has no mapping — debug builds are not
     minified — so there is nothing to upload to Crashlytics or zip; say so in the release body.
   - Rename into `build/` (gitignored; a `.zip` in the repo root is not) before attaching:
     `cats-radar-<versionName>.apk` (release) / `cats-radar-<versionName>-debug.apk` (debug), and
     zip the release build's
     `app/build/outputs/mapping/release/mapping.txt` into `cats-radar-<versionName>-mapping.zip`
     (plain, it is bigger than the APK). The mapping from any other build does not fit this
     APK's stack traces.
   - The release APK installs only on 64-bit ARM (`arm64-v8a`); say so in the release body's
     `## Install` section. The emulator on an Apple-Silicon Mac is arm64 too.
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
- **Rebuilding after the upload.** The new APK can carry a mapping id that Crashlytics never
  received, so its crash reports stay obfuscated. One `assembleRelease` run supplies the APK, the
  zip and the upload.
- **Building the release with `CI` set** (or from a CI job). The upload is skipped and the APK's
  mapping id is all zeros — fine for trying a fix, wrong for the build you ship.
