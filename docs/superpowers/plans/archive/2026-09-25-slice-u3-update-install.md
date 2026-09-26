# Slice U3 — Download and install an update — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A check that finds a newer version downloads it in a worker, verifies it, and installs it through
`PackageInstaller`; a download that finishes while nobody waits offers *Install*.

**Architecture:** `:domain` `DownloadUpdate` verifies what a `PackageDownloader` port wrote. `:data`
`HttpPackageDownloader` streams into the cache's `updates/` folder, hashing as it goes. `:app` runs it in
`DownloadUpdateWorker` (unique work), maps its `WorkInfo` to Settings intents, and installs with
`PackageInstallerUpdater`, whose session status comes back through `UpdateInstallReceiver` and `InstallResults`.

**Tech Stack:** WorkManager, `PackageInstaller` sessions, `HttpURLConnection`, Robolectric `ShadowPackageInstaller`.

**Spec:** `docs/superpowers/specs/2026-09-25-app-updates-design.md` (§ The download, § The install, § States)

## Global Constraints

- One package on disk: `cacheDir/updates/<version>.apk`; the folder is cleared before every download.
- A package is installable only when its size equals the asset's and, when the asset has one, its SHA-256 equals the digest.
- `REQUEST_INSTALL_PACKAGES` declared; the receiver is not exported; the status `PendingIntent` is explicit and mutable.
- The API base becomes a build setting beside the repository (`catsradar.updateApi`), so a verification build
  can point at a local feed.
- `CI=true ./gradlew check :app:assembleRelease` green; a device run of a real update (debug) and of a release build.

---

### Task 1: `DownloadUpdate`
**Files:** `domain/.../platform/PackageDownloader.kt`, `domain/.../usecase/DownloadUpdate.kt`, test `DownloadUpdateTest`.
**Produces:** `data class DownloadedPackage(path: String, sizeBytes: Long, sha256: String)`;
`interface PackageDownloader { suspend fun download(url: String, fileName: String, onProgress: suspend (Long) -> Unit): DownloadedPackage?; suspend fun discard(path: String) }`;
`sealed interface DownloadResult { Downloaded(path); Failed(reason: DownloadFailure) }`, `enum DownloadFailure { NETWORK, DAMAGED }`;
`class DownloadUpdate(downloader) { suspend operator fun invoke(version: String, apk: ReleasePackage, onProgress): DownloadResult }`.
- [ ] Tests: intact → Downloaded; digest mismatch → discarded + DAMAGED; size mismatch → discarded + DAMAGED;
  no digest → size only; broken download → NETWORK. - [ ] Implement. - [ ] Commit.

### Task 2: `HttpPackageDownloader`
**Files:** `data/src/androidMain/.../update/HttpPackageDownloader.android.kt`, test in `androidHostTest` (JDK `HttpServer`).
- [ ] Tests: bytes, size and SHA-256 of what was served; progress rises to the size; an earlier package is gone;
  a redirect is followed; non-2xx and a refused connection → null, no file left. - [ ] Implement. - [ ] Commit.

### Task 3: worker, scheduler, WorkInfo mapping
**Files:** `app/.../worker/DownloadUpdateWorker.kt`, `UpdateDownloadScheduler.kt`, `UpdateWorkInfo.kt`,
`KoinWorkerFactory.kt`; tests `DownloadUpdateWorkerTest`, `WorkManagerUpdateDownloadSchedulerTest`, `UpdateWorkInfoTest`.
**Produces:** `interface UpdateDownloadScheduler { fun download(version: String, apk: ReleasePackage); fun observe(): Flow<WorkInfo?> }`;
`fun WorkInfo.toUpdateIntent(): SettingsIntent.Update?`.
- [ ] Tests: unique work with KEEP and a network constraint; progress/finished/failed mapping; worker output. - [ ] Implement. - [ ] Commit.

### Task 4: installer and receiver
**Files:** `app/.../update/PackageInstallerUpdater.kt`, `UpdateInstallReceiver.kt`, `InstallResults.kt`, manifest;
tests `PackageInstallerUpdaterTest`, `UpdateInstallReceiverTest`, `RequestedPermissionsTest`.
**Produces:** `interface UpdateInstaller { suspend fun install(path: String): Boolean }`; `class InstallResults { fun post(InstallOutcome); fun observe(): Flow<InstallOutcome> }`.
- [ ] Tests: session gets the file's bytes and is committed with an explicit mutable PendingIntent to the receiver;
  missing file → false, no session left; PENDING_USER_ACTION starts the carried intent; ABORTED → CANCELLED;
  other failures → FAILED. - [ ] Implement. - [ ] Commit.

### Task 5: Store and mapper
**Files:** `presentation/.../settings/UpdateState.kt`, `UpdateStateMapper.kt`, `SettingsIntent.kt` (`Update` group),
`SettingsEffect.kt`, `SettingsStore.kt`; domain `InstalledApp.isOlderThan(version)`; tests.
- [ ] Tests: available → StartUpdateDownload + Downloading; progress; finished for this Store → InstallUpdate;
  finished seen fresh → Ready with Install; finished for a version not newer → ignored; install cancelled → Ready;
  download or install failure → one message each with its button. - [ ] Implement. - [ ] Commit.

### Task 6: UI, wiring, config, docs
- [ ] Updates section actions (Check / busy / Install <version>), strings EN/RU, destination effects and collectors,
  DI, `catsradar.updateApi`, `updates.md`.
- [ ] `CI=true ./gradlew check :app:assembleRelease`; device: debug 7 → 8 through a local feed; release build.
- [ ] `/code-review`, acceptance gate, PR stacked on U2.
