# Slice U4 — Install permission and failure reasons — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A missing "Install unknown apps" permission leads to its system page and the install proceeds on return;
a refused or failed install says why; a finished update's package is deleted at start-up.

**Architecture:** `:domain` gets the `InstallPermission` port and `PruneInstalledUpdates`; `:data` implements the
permission with `PackageManager.canRequestPackageInstalls()` and lists kept packages. `SettingsStore` checks the
permission before every install. `:app` opens `ACTION_MANAGE_UNKNOWN_APP_SOURCES` for the package, checks the
archive before a session (`getPackageArchiveInfo`), and maps every session status to its own reason.

**Spec:** `docs/superpowers/specs/2026-09-25-app-updates-design.md` (§ The install)

## Global Constraints

- The permission page is `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` with `package:<applicationId>`.
- Returning with the permission installs without another tap; returning without it shows a line and a button back.
- A package for another application id, or whose `versionCode` is not above the installed one, never reaches a session.
- Every failure reason has its own words; no two collapse.
- `CI=true ./gradlew check :app:assembleRelease` green; device: fresh install without the permission.

---

### Task 1: permission port and Store flow
**Files:** `domain/.../platform/InstallPermission.kt`; `data/src/androidMain/.../update/AndroidInstallPermission.android.kt`
(+ Robolectric test); `presentation/.../settings/*` (`NeedsInstallPermission`, `UpdateAction.AllowInstalls`,
intents `AllowInstallsClicked`, `InstallPermissionReturned`, effect `OpenInstallPermission`); tests.
- [ ] Tests: install without the permission → effect + NeedsInstallPermission; return granted → InstallUpdate;
  return refused → stays with AllowInstalls; AllowInstalls tap → effect again. - [ ] Implement. - [ ] Commit.

### Task 2: the settings page and the archive check in `:app`
**Files:** `app/.../update/InstallPermissionPage.kt` (+ test), `PackageInstallerUpdater.kt` (`InstallStart`, archive
reader), `SettingsDestination.kt`, DI; tests.
- [ ] Tests: the page intent's action and data; another package / not newer → refused before a session. - [ ] Implement. - [ ] Commit.

### Task 3: failure reasons
**Files:** `InstallOutcome` (reasons), `UpdateInstallReceiver` (status → reason), `UpdateState` (`InstallFailed(version, reason)`),
mapper, UI strings EN/RU; tests incl. distinctness.
- [ ] Implement with tests. - [ ] Commit.

### Task 4: start-up clean-up
**Files:** `PackageDownloader.kept()`, `HttpPackageDownloader`, `domain/.../usecase/PruneInstalledUpdates.kt`,
`CatsRadarApplication` repairs; tests.
- [ ] Tests: not newer deleted, newer kept. - [ ] Implement. - [ ] Commit.

### Task 5: docs and gates
- [ ] `updates.md`; `check`; device run; `/code-review`; gate; PR stacked on U3.
