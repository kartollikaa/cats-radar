# In-app updates and build info — PR Decomposition Map

- **Created:** 2026-09-25
- **Epic reference:** [docs/superpowers/specs/2026-09-25-app-updates-design.md](../../superpowers/specs/2026-09-25-app-updates-design.md)
- **Trunk:** `main`
- **Size budgets:** target ≤600 reviewable lines, cap 1000 (see tbd:sizing-pull-requests). Excluded from the
  count: `*.md`, string resources.
- **Integration strategy:** every slice is **naturally safe** — U1 adds a read-only section, U2 adds a button
  that only reads a release list, U3 lets that button finish the job, U4 sharpens what U3 already does.

## Slices

| # | PR title | Purpose (one sentence) | Strategy | Size budget | Depends on | Status |
|---|----------|------------------------|----------|-------------|------------|--------|
| U1 | Build info in Settings | Settings shows the version, build and device, and copies a developer report to the clipboard. | safe | ~450 | — | in-review |
| U2 | Check for updates | A Settings button reads the configured GitHub release list and says whether a newer version exists. | safe | ~650 | U1 | in-review |
| U3 | Download and install an update | A newer version found by the check downloads in a worker, is verified, and installs through `PackageInstaller`. | safe | ~900 | U2 | planned |
| U4 | Install permission and failure reasons | A missing install permission leads to its system page, a failed install says why, and a finished update's package is deleted. | safe | ~450 | U3 | planned |

Status values: `planned · in-progress · in-review · merged · dropped`

U2–U4 stack on the previous slice because each extends the same Settings Store and screen; they merge
bottom-up, each retargeted to `main` after the one below merges.

## Slice details

### Slice U1 — Build info in Settings
- **In scope:** `InstalledApp` and `BuildInfo` in `:domain`, the `BuildInfoReader` port and its Android
  implementation; the commit and the database version into `BuildConfig`/a constant; `AboutStateMapper`
  (rows and report); the About section with the copy icon button; the copy effect and clipboard in `:app`, a
  toast below Android 13; EN/RU strings; `docs/features/build-info.md` (new), `strings.md`.
- **Out of scope:** anything about updates.
- **Ships safely because:** read-only; the only action writes to the clipboard.
- **Cleanup owed:** none.

### Slice U2 — Check for updates
- **In scope:** `AppVersion` (SemVer precedence), `PublishedRelease`, the `UpdateSource` port, `CheckForUpdate`;
  `GitHubReleaseFeed` (JSON in `commonMain`, HTTPS in `androidMain`) with the repository as a build setting;
  the Updates section with its check states (idle, checking, up to date, available, the three check
  failures); EN/RU strings; `docs/features/updates.md` (new), `map.md` (no longer the only network use).
- **Out of scope:** downloading and installing — an available version is reported, nothing more.
- **Ships safely because:** the button only reads a public list; "available" is information.
- **Cleanup owed:** none.

### Slice U3 — Download and install an update
- **In scope:** the `PackageDownloader` port and `DownloadUpdate` (SHA-256 and size check); `HttpPackageDownloader`;
  `DownloadUpdateWorker`, its scheduler and WorkInfo mapping; `PackageInstallerUpdater`,
  `UpdateInstallReceiver`, `REQUEST_INSTALL_PACKAGES`; the check starting the download, a finished download
  installing while Settings is open and offering *Install* otherwise; downloading, ready and installing states;
  one generic failure message for a failed download or install; EN/RU strings; `updates.md`.
- **Out of scope:** the explicit permission check (the system's own dialog covers a missing permission here);
  per-status failure messages; deleting a finished update's package.
- **Ships safely because:** the flow is complete end to end; Android's installer still asks the user.
- **Cleanup owed:** none.

### Slice U4 — Install permission and failure reasons
- **In scope:** the `InstallPermission` port and its Android implementation; the needs-permission state, the
  system page and the re-check on return; the archive check (this app, higher `versionCode`); failure reasons
  for cancelled, conflict, incompatible, storage, missing package; the start-up repair that deletes a package
  not newer than the installed version; EN/RU strings; `updates.md`.
- **Out of scope:** anything in *Not built* of the spec.
- **Ships safely because:** it narrows U3's generic failure into specific ones and adds a step before a commit
  that would otherwise hit the system's refusal dialog.
- **Cleanup owed:** none.

## Decision log

- 2026-09-25: the owner made `kartollikaa/cats-radar` public, so the committed update source now answers.
  Reading the live list showed v1.4.0-beta carrying a `-debug.apk` before its release APK; the feed now
  skips debug builds (U2).
- 2026-09-25: map created. U3 is the largest slice because download without install would leave a
  user-visible dead end ("downloaded", nothing to do); splitting it by layer instead would ship dormant code
  reviewed without its caller.
