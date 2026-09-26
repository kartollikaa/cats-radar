---
name: publish-release
description: Use when a signed Cats Radar release APK and its R8 mapping are built and need to go up as a GitHub pre-release, when writing a release's notes, when a release upload stalls or dies, or when a published release's tag or assets look wrong.
---

# Publish a Cats Radar release

## Overview

The second half of a release. `release-app` merges the version bump and builds one signed APK whose
mapping Crashlytics received; this skill puts exactly that build on GitHub as a pre-release tagged on
the merge commit, and proves it from GitHub's side, never from the upload command's exit code.

## Inputs — stop if one is missing

- `<sha>`: the pinned merge commit of `tech/release-<version>`, all 40 characters — GitHub rejects a
  short sha as an invalid `target_commitish`.
- From that one `assembleRelease` run: `app/build/outputs/apk/release/app-release.apk` and
  `app/build/outputs/mapping/release/mapping.txt`. `apksigner verify --print-certs` on the APK shows
  the release key, not `Android Debug`.
- Whether that APK was walked on a device or the owner waived the walk — it goes into the notes.

Only `app-release-unsigned.apk`, or no release build at all → **stop and tell the owner**. A release
carries the signed release APK and its mapping, nothing else: no debug APK, not even as a stand-in.
Rebuilt since that run → back to `release-app`; only the run whose mapping Crashlytics got may ship.

## Steps

1. **Stage the two assets** in `build/` (gitignored; a `.zip` in the repo root is not):
   ```
   V=<versionName>
   mkdir -p build/release
   cp app/build/outputs/apk/release/app-release.apk "build/release/cats-radar-$V.apk"
   zip -j "build/release/cats-radar-$V-mapping.zip" app/build/outputs/mapping/release/mapping.txt
   ```
2. **Write `build/release/notes.md`** from `git log --first-parent --oneline v<last>..<sha>` in the
   shape below.
3. **Create the release in the background** — the upload can outlast the tool timeout. With assets, `gh` creates a draft, uploads, then publishes; the tag appears only at
   the publish.
   ```
   gh release create "v$V" --prerelease --target <sha> --title "v$V — <one-line theme>" \
     --notes-file build/release/notes.md "build/release/cats-radar-$V.apk" "build/release/cats-radar-$V-mapping.zip"
   ```
   Without `--target`, GitHub tags the default branch's head at publish time, not the merge commit.
4. **Verify from GitHub.** Look the release up in the list — a draft is not found by tag:
   ```
   gh api repos/kartollikaa/cats-radar/releases --jq ".[] | select(.tag_name==\"v$V\") | {draft, prerelease, target_commitish, assets: [.assets[] | {name, size, state}]}"
   stat -f '%N %z' build/release/cats-radar-"$V"*
   git ls-remote origin "refs/tags/v$V"
   ```
   Done when: `draft` false, `prerelease` true, `target_commitish` is `<sha>`, exactly the two
   assets, each `state` `uploaded` with the local file's size, and the remote tag is `<sha>`.

## When the upload stalls or dies

- While your upload lives (`pgrep -fl "gh release (create|upload) v$V"`), start nothing else that
  uploads: a second create fails on the existing draft, a second upload races the first.
- Progress is the release list above, not the process's silence.
- Process gone, release still a draft: `gh release upload "v$V" <each missing or non-uploaded file> --clobber`,
  then `gh release edit "v$V" --draft=false`, then step 4 again.
- Process gone, no release in the list: nothing was kept, so step 3 again.
- Wrong tag or wrong asset on a published release: stop and tell the owner — with immutable releases
  on, neither can be changed afterwards.

## Release notes shape

Match the last release (`gh release view <last-tag> --json body`):

- a one-line tagline;
- `## What's new since v<last>` — grouped by feature area (bold area name), bullets in plain user
  language, no PR titles or internal type names; pure `test/` and `tech/` PRs with no behaviour
  change ship but get no line;
- `## Install` — names `cats-radar-<version>.apk`, says it installs over the previous release build,
  that a debug build must be removed first (export a backup), and that it runs on 64-bit ARM
  (`arm64-v8a`) only;
- `## Honest caveats` — what wasn't walked on a device (say so if the owner waived the walk), what
  needs a network, what a missing permission or Play Services degrades to.

## Common mistakes

| Mistake | Instead |
|---|---|
| Notes from the full log | `--first-parent`: the full log carries every PR's `fix: review` / `test: gate` commits |
| Trusting the exit code or the `\| tail` after it | Step 4's list, sizes and tag sha |
| Re-running `gh release create` while a draft exists | Finish the draft with `upload --clobber` + `edit --draft=false` |
| Attaching a debug APK "until the signed one exists" | No signed APK, no release — tell the owner |
