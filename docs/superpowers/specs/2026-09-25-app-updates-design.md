# In-app updates and build info — design

- **Date:** 2026-09-25
- **Status:** decided autonomously on the owner's instruction ("continue in autonomous mode"), 2026-09-25;
  one owner call is open (see *Open owner call*)
- **Decomposition:** [docs/tbd/decompositions/2026-09-25-app-updates.md](../../tbd/decompositions/2026-09-25-app-updates.md)
- **Builds on:** [docs/reference/releasing.md](../../reference/releasing.md) (how a release is published)

## What the owner asked for

1. A button that checks for a new version and, when there is one, downloads the APK and installs it.
   Where updates come from is not settled; GitHub releases are the likely source.
2. On recent Android, installing from an app needs the user's permission ("Install unknown apps"):
   the flow must handle it, and a missing permission leads to that system setting.
3. Settings shows the version, the build number and whatever else helps debugging, with an icon button
   next to it that copies the lot in a form a developer can paste.

## Open owner call

`kartollikaa/cats-radar` is a **private** repository. GitHub answers an anonymous request for its
releases with `404`, so until one of these happens the check reports that the update source cannot be
reached:

- the repository becomes public, or
- releases are also published to a public repository (a releases-only mirror), and the build points at it.

The source is a build setting (`catsradar.updateRepository` in `gradle.properties`, `owner/name`), so
either choice is a one-line change. **A token is never built into the APK**: anyone holding the file
could read it out and, with it, the private repository.

## Settings, top to bottom

Existing sections stay as they are; two sections join the end:

- **Updates** — the installed version, one button, and a status line under it.
- **About** — the build and device facts, with a copy icon button in the section's header row.

## About

What it shows (labels translated, values as they are):

| Row | Example | From |
|---|---|---|
| Version | `1.4.1-beta (7)` | `versionName (versionCode)` |
| Build | `release · 5989a92c1f3e` | build type · the commit the APK was built from |
| Device | `Google Pixel 7` | `Build.MANUFACTURER` + `Build.MODEL` |
| Android | `16 (API 36)` | `Build.VERSION.RELEASE` + `SDK_INT` |

The **copy** icon puts a longer report on the clipboard: plain `key: value` lines, English keys in every
locale, so a developer can read a report from a Russian-speaking phone and a paste stays one block in a
chat or an issue:

```
Cats Radar 1.4.1-beta (7)
Build type: release
Commit: 5989a92c1f3e
Application id: com.kartollika.catsradar
Installed by: com.google.android.packageinstaller
Device: Google Pixel 7 (panther)
Android: 16 (API 36)
ABIs: arm64-v8a
Locale: ru-RU
Time zone: Europe/Moscow
Database: 4
```

- The **commit** is read by the build (`git rev-parse --short=12 HEAD`) into `BuildConfig`; a build with no
  git (a source archive) says `unknown`.
- **Installed by** is who Android thinks installed the app (a file manager, a browser, `adb` shows as none,
  Cats Radar itself after its first self-update). It explains whether an update will ask for confirmation.
- **Database** is the Room schema version, which is what a backup or migration question needs first.
- Android 13 and later confirm a copy themselves; below 13 the app shows a short toast.

**Where it lives.** A `BuildInfo` model in `:domain` (the app's own facts in `InstalledApp`, plus the
device's), read by a `BuildInfoReader` port whose Android implementation in `:data` reads `Build`,
`PackageManager` and the locale each time; `:app` hands it `InstalledApp` from `BuildConfig`.
`AboutStateMapper` in `:presentation` builds both the rows and the report. The report's English keys are
not UI text, so they live in the mapper and are pinned by its test.

## Updates

### The check

**The feed.** The GitHub REST API's release list for the configured repository,
`GET https://api.github.com/repos/<owner>/<name>/releases?per_page=20`, anonymously. Not
`/releases/latest`: that one skips pre-releases, and every Cats Radar release so far is one. Drafts are
ignored. A release counts only if it has an `.apk` asset; its version is its tag without the leading `v`.

**Newer.** Versions compare by [SemVer 2.0](https://semver.org/#spec-item-11) precedence: numbers first,
then a pre-release ranks below the same numbers without one (`1.5.0-beta` < `1.5.0`), pre-release fields
compared field by field, build metadata ignored. A tag that is not a version is skipped. The newest release
is offered only when it is **higher** than the installed `versionName`; the same or lower is "up to date".
Android still refuses a package whose `versionCode` is not higher, which `releasing.md` guarantees by
raising it every release.

**One button.** *Check for updates*. The owner's words were that the check downloads and installs, so it
does: a newer release starts downloading at once, and a finished download installs at once while Settings
is open. There is no automatic or background check and no "new version" notification.

**Failures of the check**, each with its own message: no connection; the source answered but refused
(`404` for a private or wrong repository, `403`/`429` for GitHub's anonymous rate limit); an answer that is
not a release list.

### The download

- A `WorkManager` worker (`DownloadUpdateWorker`, unique work, `KEEP`), so leaving Settings or the app does
  not stop it, with a network constraint and progress reported as it goes.
- It writes to the app's cache, `cacheDir/updates/<version>.apk`, clearing that folder first, so there is
  only ever one package. No storage permission is involved.
- While it streams, it computes the file's SHA-256 and compares it with the `sha256:` digest GitHub reports
  for the asset, and the byte count with the asset's size. A mismatch deletes the file and reports a damaged
  download. A release whose asset has no digest is checked on size alone.
- The finished worker's output (version and path) is what tells a Settings screen opened later that a
  package is ready: the screen then offers **Install** instead of installing by itself.

### The install

- **Permission first.** `REQUEST_INSTALL_PACKAGES` is declared. Before installing, the app asks
  `PackageManager.canRequestPackageInstalls()`. When it is false the app opens the system page for this app,
  `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` with `package:<id>`, and on returning asks again: granted
  installs at once; still refused shows a line explaining it with a button back to that page.
- **Checking the package.** `PackageManager.getPackageArchiveInfo` must name this application id with a
  higher `versionCode`; anything else is refused before Android is asked, with its own message.
- **Installing.** A `PackageInstaller` session (`MODE_FULL_INSTALL`) streams the file in and is committed
  with a `PendingIntent` to a non-exported `UpdateInstallReceiver`. `STATUS_PENDING_USER_ACTION` carries
  the system's confirmation screen, which the receiver starts; the user confirms there. On success Android
  replaces the app and ends its process, so nothing reports success; the next launch is the new version.
- **Outcomes reported back** through the receiver: the user cancelled (`STATUS_FAILURE_ABORTED`, silently
  back to *Install*); signed with another key (`STATUS_FAILURE_CONFLICT` — a debug build cannot update to a
  release one); not for this phone (`STATUS_FAILURE_INCOMPATIBLE` — the release APK is `arm64-v8a` only);
  not enough space (`STATUS_FAILURE_STORAGE`); anything else as a generic failure. The package file missing
  (the system cleared the cache) says to check again.
- **Clean-up.** A start-up repair deletes a package in `updates/` that is no longer newer than the installed
  version, so the file from a finished update does not sit in the cache.

### States shown under the button

| State | Shows | Button |
|---|---|---|
| Idle | what the button does (the version is in About) | Check for updates |
| Checking | indeterminate progress | disabled |
| Up to date | "You have the latest version" | Check for updates |
| Download starting | "Downloading 1.5.0-beta", indeterminate progress (no network yet, or no size) | disabled |
| Downloading | "Downloading 1.5.0-beta · 45 %", progress with percent | disabled |
| Ready | "1.5.0-beta is ready to install" | Install 1.5.0-beta |
| Needs permission | "Allow Cats Radar to install apps" | Open settings |
| Installing | indeterminate progress while the system asks | disabled |
| Failed | the reason | Check for updates (a failed install downloads the package afresh) |

### Where it lives

- `:domain` — `AppVersion` (parse and compare), `PublishedRelease`, the `UpdateSource` port, `CheckForUpdate`
  (newest release with a package vs `InstalledApp`), the `PackageDownloader` port and `DownloadUpdate`
  (digest and size verification), the `InstallPermission` port.
- `:data` — `GitHubReleaseFeed`: JSON mapping in `commonMain` (kotlinx-serialization), HTTPS over
  `HttpURLConnection` in `androidMain`; `HttpPackageDownloader`; `AndroidInstallPermission`.
- `:presentation` — the Updates part of `SettingsState`/`Intent`/`Effect`/`Store` and `UpdateStateMapper`.
- `:ui` — the two sections.
- `:app` — `DownloadUpdateWorker` and its scheduler, `PackageInstallerUpdater` and `UpdateInstallReceiver`,
  the settings-page launcher, the clipboard, the Koin bindings, `BuildConfig` fields.

## Not built

A background or periodic check; a "new version" notification; release notes in the app; silent updates
(`USER_ACTION_NOT_REQUIRED`); a download on Wi-Fi only; analytics events for updates; any source other than
a GitHub release list.

## Decisions

| # | Decision | Why |
|---|---|---|
| 1 | GitHub release list, pre-releases included, source configurable | The owner's likely source; every release is a pre-release; the repository is private today. |
| 2 | No token in the APK | Anyone with the APK could extract it. |
| 3 | SemVer precedence on the tag vs `versionName` | The tag is the only version a release carries. |
| 4 | Check → download → install on one tap | The owner's words; the system's confirmation screen is the user's last word. |
| 5 | Download in a worker, verified by SHA-256 and size | Survives leaving the screen; a truncated or swapped file never reaches the installer. |
| 6 | `PackageInstaller` session, not `ACTION_VIEW` | Reports why an install failed; the file never leaves the app through a content URI. |
| 7 | Explicit permission check leading to the system page | The owner asked for the install to lead to settings rather than to a system refusal dialog. |
| 8 | Report keys in English | A developer reads the report, in any locale. |
