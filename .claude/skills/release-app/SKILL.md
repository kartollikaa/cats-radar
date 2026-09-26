---
name: release-app
description: Use when asked to cut, ship, or publish a new Cats Radar release, bump the app version for a release, build the signed release APK, or get a release's R8 mapping into Crashlytics so release crash reports are readable.
---

# Release Cats Radar

## Overview

Turns `docs/reference/releasing.md` into a runnable checklist, up to the moment one signed release
APK exists whose R8 mapping Crashlytics has received. Putting that build on GitHub is the
`publish-release` skill. Read the doc for the *why*; this skill is the *how*, including the gotchas
that doc doesn't cover because they're about the tooling, not the product.

A release is a GitHub pre-release tagged `v<versionName>` on the merge commit of a
`tech/release-<version>` PR, carrying the signed release APK and its zipped mapping — never a debug
APK.

## When to use

Triggers: "publish a release", "cut a release", "ship a new version", "build the release APK", or
any request naming a version bump plus a GitHub release. When the signed APK of a merged bump
already exists and only the GitHub release is missing, go straight to `publish-release`.

Not for: an internal test build with no tag/release (`./gradlew :app:assembleDebug` and hand over
the APK — no version bump, no PR, no tag).

## Before you start

1. **Read `docs/reference/releasing.md`** — the versioning rule, the signing key properties, and why
   a debug build never installs over a release build.
2. **Confirm the signing file first.** `~/.gradle/gradle.properties` must have a
   `kartollika.signingFile=` line and the file it names must exist. If either is missing, stop
   before bumping anything and tell the owner: without it `assembleRelease` leaves only
   `app-release-unsigned.apk`, which installs on nothing, and a debug APK is not a substitute.
3. **Check whether `main` moved.** `git fetch origin main && git log --oneline <last-tag>..origin/main`.
   Another session may have merged work you don't know about — the release ships everything on
   `main`, not just what you expected. Re-run this check right before merging the version-bump PR;
   `main` can move again while you work.
4. **In a worktree, copy `local.properties` from the primary checkout first** — a fresh worktree
   has no `sdk.dir` and Gradle will refuse to configure.

## Steps

1. **Pick the version.** Bump `app-versionCode` by exactly one (Android refuses an update whose code
   isn't higher) and `app-versionName` in `gradle/libs.versions.toml`. Decide the semantic bump from
   what actually shipped since the last tag, not from habit — read the first-parent log, where every
   PR merge is one line and each PR's own follow-up commits are hidden:
   ```
   git log --first-parent --oneline <last-tag>..origin/main
   ```
   A new epic slice is a minor bump, a batch of fixes/polish is a patch, in both cases keeping the
   `-beta`/`-alpha` suffix already in use.
2. **Branch, bump, PR.** `tech/release-<version>` off the latest `main` (never off a stale local
   branch). The PR carries the version bump and, for each epic whose slices reach a release for the
   first time, a Decision-log line in its map under `docs/tbd/decompositions/` saying they first
   shipped in `v<version>` — `releasing.md` requires it. Those epics are the maps that name a PR from
   step 1's log and have no `First shipped in` line for it yet. Run `./gradlew check` in a worktree —
   the primary checkout fails it for reasons unrelated to the change — and confirm it's green; the
   merge rule is a green local check, not waiting on GitHub Actions. Open the PR
   and run `/code-review` on it (trivial diff, but it's still a slice). Record its number as
   `RELEASE_PR`, then merge with a merge commit (`gh pr merge "$RELEASE_PR" --merge`), never squash.
3. **Re-fetch and pin that PR's merge commit.** Do not substitute the current tip of `main`: another
   PR may land in the gap. Resolve and verify the exact release merge instead:
   ```bash
   git fetch origin main
   MERGE_SHA=$(gh api "repos/kartollikaa/cats-radar/pulls/$RELEASE_PR" --jq .merge_commit_sha)
   test -n "$MERGE_SHA"
   git merge-base --is-ancestor "$MERGE_SHA" origin/main
   git show --no-patch --oneline "$MERGE_SHA"
   ```
   If `origin/main` is newer, report the later commits but still build the release from
   `$MERGE_SHA`; silently widening the release would make its tag target the wrong PR. Check out
   that object detached in a clean worktree.
4. **Build the release APK — one invocation that builds it and uploads its mapping to Crashlytics**,
   with `CI` unset (the upload is off whenever `CI` is set) and the network up:
   ```
   mkdir -p build && env -u CI ./gradlew :app:assembleRelease --console=plain > build/release-build.log 2>&1; echo "exit=$?"
   ```
   → `app/build/outputs/apk/release/app-release.apk` and
   `app/build/outputs/mapping/release/mapping.txt`. A rebuild can stamp a new mapping id into its
   APK, so the APK you ship, the mapping you zip and the mapping Crashlytics received must all come
   from this one run: after it, never rebuild, and never run `uploadCrashlyticsMappingFileRelease`
   on its own "to confirm". Confirm from what this run left behind instead:
   - the log shows `> Task :app:uploadCrashlyticsMappingFileRelease` with nothing after it —
     `SKIPPED`, `UP-TO-DATE`, `FROM-CACHE`, `NO-SOURCE` or `FAILED` all mean no upload in this run;
   - `aapt2 dump resources app/build/outputs/apk/release/app-release.apk | grep -A1 crashlytics.mapping_file_id`
     (`aapt2` and `apksigner` are in `~/Library/Android/sdk/build-tools/<newest>/`) is not `00000000000000000000000000000000`
     (all zeros means the upload was off).

   If the upload failed (offline), run the whole command again once online and ship that run's APK
   and mapping. Then `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`
   must show the release key's certificate, not `Android Debug`.
5. **Walk the minified build on a device.** R8 minifies the release build, which the debug build
   never is. Install **that same signed APK** and walk the by-name paths listed in
   `docs/reference/releasing.md` (widget tap, a tally, backup export + import, a screen surviving a
   background kill) — a missing keep rule shows up nowhere else. The owner may waive the walk for
   one release; ask, and never carry a past waiver over. Use a throwaway AVD (copy
   `~/.android/avd/Pixel_7.avd/config.ini` into a new `<Name>.avd/` plus a `<Name>.ini` pointing at
   it, boot it on a free port, delete both afterwards): the shared emulator holds other sessions'
   debug-signed `com.kartollika.catsradar`, which a release-signed APK cannot update without
   uninstalling it and their data. The AVDs here run `arm64-v8a` system images, so the
   `arm64-v8a`-only release APK installs on them. If the walk finds a bug, fix it through a PR and restart from
   step 3; any local release build made just to try something runs with `CI=true`, so it uploads
   nothing.
6. **Publish. REQUIRED NEXT SKILL: `publish-release`**, handing it the pinned merge SHA and this
   run's APK and `mapping.txt`, plus whether the walk ran or was waived.

## Common mistakes

- **Building from a branch tip or the latest `main` instead of the release PR merge.** Resolve the
  release PR's `merge_commit_sha`, prove it is on `origin/main`, and build that exact object.
- **Squash-merging the release PR.** Breaks the per-PR merge-commit history convention; use
  `gh pr merge --merge`.
- **Shipping without the signing file.** `app-release-unsigned.apk` installs on nothing, and a
  debug APK in its place is not a release. Stop and tell the owner.
- **Rebuilding after the upload.** The new APK can carry a mapping id that Crashlytics never
  received, so its crash reports stay obfuscated. One `assembleRelease` run supplies the APK, the
  zip and the upload.
- **Building the release with `CI` set** (or from a CI job). The upload is skipped and the APK's
  mapping id is all zeros — fine for trying a fix, wrong for the build you ship.
