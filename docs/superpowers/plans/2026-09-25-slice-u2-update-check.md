# Slice U2 — Check for updates — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A *Check for updates* button in Settings reads the configured GitHub release list and says
whether a newer version exists, with one message per way the check can fail.

**Architecture:** `:domain` gets `AppVersion` (SemVer precedence), `PublishedRelease`/`ReleasePackage`, the
`UpdateSource` port and `CheckForUpdate`. `:data` maps GitHub's JSON in `commonMain` and fetches it over
`HttpURLConnection` in `androidMain`. `SettingsStore` runs the check and `UpdateStateMapper` turns the result
into a status token the Updates section resolves to words.

**Tech Stack:** Kotlin Multiplatform, kotlinx-serialization-json, `java.net.HttpURLConnection`, JDK
`com.sun.net.httpserver` in host tests, Compose Material 3.

**Spec:** `docs/superpowers/specs/2026-09-25-app-updates-design.md` (§ Updates → The check)

## Global Constraints

- Request: `GET https://api.github.com/repos/<repo>/releases?per_page=20`, headers `Accept: application/vnd.github+json`,
  `X-GitHub-Api-Version: 2022-11-28`, `User-Agent: CatsRadar/<versionName>`; never an `Authorization` header.
- The repository is `catsradar.updateRepository` in `gradle.properties`, into `BuildConfig.UPDATE_REPOSITORY`.
- Drafts and releases without an `.apk` asset are never offered; a pre-release is offered like a release.
- No user-facing words in `:presentation`; statuses are tokens.
- `CI=true ./gradlew check :app:assembleRelease` green.

---

### Task 1: `AppVersion`
**Files:** create `domain/src/commonMain/kotlin/dev/catsradar/domain/update/AppVersion.kt`; test
`domain/src/commonTest/kotlin/dev/catsradar/domain/update/AppVersionTest.kt`.
**Produces:** `data class AppVersion(major: Int, minor: Int, patch: Int, preRelease: List<String>) : Comparable<AppVersion>`,
`AppVersion.parse(text: String): AppVersion?`, `toString()` = normal form without build metadata.
- [ ] Failing tests, one per AC-10 case. - [ ] Implement per SemVer §11. - [ ] Pass. - [ ] Commit.

### Task 2: `CheckForUpdate`
**Files:** create `domain/.../update/PublishedRelease.kt`, `domain/.../platform/UpdateSource.kt`,
`domain/.../usecase/CheckForUpdate.kt`; test `domain/src/commonTest/.../usecase/CheckForUpdateTest.kt`.
**Produces:**
```kotlin
data class ReleasePackage(val url: String, val sizeBytes: Long, val sha256: String?)
data class PublishedRelease(val tag: String, val apk: ReleasePackage?)
sealed interface ReleaseFeed { data class Listed(val releases: List<PublishedRelease>); data class Failed(val reason: FeedFailure) }
enum class FeedFailure { OFFLINE, UNAVAILABLE, UNREADABLE }
fun interface UpdateSource { suspend fun releases(): ReleaseFeed }
sealed interface UpdateCheck { data object UpToDate; data class Available(val version: AppVersion, val apk: ReleasePackage); data class Failed(val reason: FeedFailure) }
class CheckForUpdate(source: UpdateSource, installed: InstalledApp) { suspend operator fun invoke(): UpdateCheck }
```
- [ ] Failing tests: newer → Available; equal/lower → UpToDate; newest without apk skipped; unparseable tag skipped;
  pre-release offered; feed failure passed through; empty list → UpToDate. - [ ] Implement. - [ ] Pass. - [ ] Commit.

### Task 3: GitHub mapping (`commonMain`)
**Files:** create `data/src/commonMain/kotlin/dev/catsradar/data/update/GitHubReleases.kt`; test
`data/src/commonTest/kotlin/dev/catsradar/data/update/GitHubReleasesTest.kt`.
**Produces:** `internal fun parseGitHubReleases(json: String): List<PublishedRelease>` — drafts dropped; `apk` =
first asset whose name ends `.apk`; `sha256` = `digest` without its `sha256:` prefix, null when absent or another
algorithm; throws `SerializationException` on a body that is not a release list.
- [ ] Failing tests on a fixture shaped like GitHub's answer. - [ ] Implement. - [ ] Pass. - [ ] Commit.

### Task 4: HTTPS feed (`androidMain`)
**Files:** create `data/src/androidMain/kotlin/dev/catsradar/data/update/GitHubReleaseFeed.android.kt`; test
`data/src/androidHostTest/kotlin/dev/catsradar/data/update/GitHubReleaseFeedTest.kt` (JDK `HttpServer` on 127.0.0.1).
**Produces:** `class GitHubReleaseFeed(repository: String, userAgent: String, apiBase: String = "https://api.github.com", ioDispatcher)`.
- [ ] Failing tests: request line and headers (no `Authorization`); 200 → Listed; 404/403/429 → UNAVAILABLE;
  refused connection → OFFLINE; garbage body → UNREADABLE. - [ ] Implement. - [ ] Pass. - [ ] Commit.

### Task 5: Settings Store and mapper
**Files:** create `presentation/.../settings/UpdateState.kt`, `UpdateStateMapper.kt`; modify `SettingsState`,
`SettingsIntent` (`UpdateCheckClicked`), `SettingsStore`; tests `UpdateStateMapperTest`, `SettingsStoreTest`.
**Produces:** `sealed interface UpdateStatus { Idle; Checking; UpToDate; Available(version: String); Failed(reason: UpdateFailure) }`,
`enum class UpdateFailure { OFFLINE, SOURCE_UNAVAILABLE, UNREADABLE_ANSWER }`, `SettingsState.update: UpdateStatus = Idle`.
- [ ] Failing tests: every result → status; failures distinct; checking state while pending; second tap while
  checking calls the source once. - [ ] Implement. - [ ] Pass. - [ ] Commit.

### Task 6: Updates section, config, wiring
**Files:** modify `SettingsScreen.kt` (Updates section before About), strings EN/RU, `gradle.properties`,
`AndroidApplicationConventionPlugin.kt` (`UPDATE_REPOSITORY`), `DataModule.kt`, `DomainModule.kt`,
`PresentationModule.kt`, `SettingsDestination.kt`; tests `UpdatesSectionTest`, `UpdateRepositoryConfigTest`.
- [ ] Failing tests: each status shows its own words; button disabled while checking; config equals the property.
- [ ] Implement. - [ ] `CI=true ./gradlew check`. - [ ] Commit.

### Task 7: Docs and gates
- [ ] `docs/features/updates.md` (new), README index, `map.md` network paragraph.
- [ ] Emulator: Check for updates against the real (private) repo → "source unavailable"; against a local feed →
  "available" / "up to date".
- [ ] `/code-review`, acceptance gate, PR stacked on U1.
