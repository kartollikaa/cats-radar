# Slice U1 — Build info in Settings — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Settings ends with an About section showing version, build, device and Android version, and an
icon button that copies a developer report to the clipboard.

**Architecture:** `:domain` gets `InstalledApp`/`DeviceInfo`/`BuildInfo` and a `BuildInfoReader` port; `:data`
implements it on Android; `:app` supplies `InstalledApp` from `BuildConfig` (a new `GIT_COMMIT` field from
the convention plugin). `AboutStateMapper` builds the rows and the report; `SettingsStore` reads it once and
turns a copy click into a `CopyBuildInfo` effect that `:app` writes to the clipboard.

**Tech Stack:** Kotlin Multiplatform, Compose Material 3, Koin, Robolectric, AGP variant API.

**Spec:** `docs/superpowers/specs/2026-09-25-app-updates-design.md` (§ About)

## Global Constraints

- Report keys stay English in every locale; labels on screen come from EN/RU resources.
- No lambda in State; collections in State are `kotlinx.collections.immutable`.
- `:ui` never imports `dev.catsradar.data`; `:presentation` never imports `androidx.compose`.
- Only the composition root obtains platform services (`DependencyLookupTest`).
- Comments default to none; one line max.
- `CI=true ./gradlew check :app:assembleRelease` green before the PR.

---

### Task 1: Domain model and port

**Files:**
- Create: `domain/src/commonMain/kotlin/dev/catsradar/domain/about/BuildInfo.kt`
- Create: `domain/src/commonMain/kotlin/dev/catsradar/domain/platform/BuildInfoReader.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class InstalledApp(val versionName: String, val versionCode: Long, val buildType: String,
      val applicationId: String, val commit: String, val databaseVersion: Int)
  data class DeviceInfo(val manufacturer: String, val model: String, val deviceName: String,
      val androidRelease: String, val sdkInt: Int, val abis: List<String>, val localeTag: String,
      val timeZoneId: String, val installer: String?)
  data class BuildInfo(val app: InstalledApp, val device: DeviceInfo)
  fun interface BuildInfoReader { suspend fun read(): BuildInfo }
  ```

- [ ] **Step 1:** Write both files exactly as the interfaces above (KDoc only on `commit` — "`unknown` when the
  build had no git" — and `installer` — "null when Android names no installer").
- [ ] **Step 2:** `./gradlew :domain:compileAndroidMain` — compiles.

### Task 2: `AboutStateMapper`

**Files:**
- Create: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/settings/AboutState.kt`
- Create: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/settings/AboutStateMapper.kt`
- Test: `presentation/src/commonTest/kotlin/dev/catsradar/presentation/settings/AboutStateMapperTest.kt`

**Interfaces:**
- Consumes: `BuildInfo` (Task 1).
- Produces:
  ```kotlin
  data class AboutState(val version: String, val build: String, val device: String,
      val androidRelease: String, val sdkInt: Int, val report: String)
  class AboutStateMapper { fun map(info: BuildInfo): AboutState }
  ```

- [ ] **Step 1: failing tests** — the whole `AboutState` for a Pixel build; a model that already starts with its
  manufacturer (`Xiaomi` / `Xiaomi 13` → `Xiaomi 13`, case-insensitive); a lowercase manufacturer is capitalised
  (`samsung` / `SM-S911B` → `Samsung SM-S911B`); a null installer reports `Installed by: none`; several ABIs are
  joined with `, `. Expected report for the Pixel fixture:
  ```
  Cats Radar 1.4.1-beta (7)
  Build type: release
  Commit: 5989a92c1f3e
  Application id: com.kartollika.catsradar
  Installed by: com.google.android.packageinstaller
  Device: Google Pixel 7 (panther)
  Android: 16 (API 36)
  ABIs: arm64-v8a, armeabi-v7a
  Locale: ru-RU
  Time zone: Europe/Moscow
  Database: 3
  ```
- [ ] **Step 2:** run `:presentation:testAndroidHostTest --tests '*AboutStateMapperTest'` — fails (no class).
- [ ] **Step 3:** implement:
  ```kotlin
  class AboutStateMapper {
      fun map(info: BuildInfo): AboutState = AboutState(
          version = "${info.app.versionName} (${info.app.versionCode})",
          build = "${info.app.buildType} · ${info.app.commit}",
          device = deviceName(info.device),
          androidRelease = info.device.androidRelease,
          sdkInt = info.device.sdkInt,
          report = report(info),
      )
      // report(): the eleven lines above, joined with "\n", no trailing newline.
      // deviceName(): model alone when it starts with the manufacturer, else "Manufacturer model".
  }
  ```
- [ ] **Step 4:** tests pass. **Step 5:** commit.

### Task 3: `SettingsStore` reads build info and copies it

**Files:**
- Modify: `presentation/.../settings/SettingsState.kt` (`val about: AboutState? = null`)
- Modify: `presentation/.../settings/SettingsIntent.kt` (`data object BuildInfoCopyClicked`)
- Modify: `presentation/.../settings/SettingsEffect.kt` (`data class CopyBuildInfo(val report: String)`)
- Modify: `presentation/.../settings/SettingsStore.kt` (constructor `buildInfoReader`, `aboutStateMapper`)
- Test: `presentation/src/commonTest/.../settings/SettingsStoreTest.kt`

- [ ] **Step 1: failing tests** — after `runCurrent()` the state's `about` equals the mapper's result for the
  fake's `BuildInfo`; `BuildInfoCopyClicked` emits `CopyBuildInfo(report)`; a click before the read finished
  emits nothing (fake reader suspended on a `CompletableDeferred`).
- [ ] **Step 2:** tests fail. **Step 3:** implement (`init { viewModelScope.launch { … setState { copy(about = …) } } }`;
  `BuildInfoCopyClicked -> state.value.about?.let { emit(SettingsEffect.CopyBuildInfo(it.report)) }`).
- [ ] **Step 4:** whole `SettingsStoreTest` passes. **Step 5:** commit.

### Task 4: Android reader, commit and database version

**Files:**
- Create: `data/src/androidMain/kotlin/dev/catsradar/data/platform/AndroidBuildInfoReader.android.kt`
- Modify: `data/src/commonMain/kotlin/dev/catsradar/data/db/CatsDatabase.kt` (`const val CATS_DATABASE_VERSION = 3`,
  used by `@Database(version = …)`)
- Modify: `build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt` (`GIT_COMMIT`)
- Test: `data/src/androidHostTest/kotlin/dev/catsradar/data/platform/AndroidBuildInfoReaderTest.kt`
- Test: `app/src/test/kotlin/dev/catsradar/app/BuildCommitTest.kt`

- [ ] **Step 1: failing tests** — Robolectric: with `ShadowBuild` manufacturer/model/device/release set and an
  install source registered on the shadow package manager, `read()` returns them, the configuration's first
  locale as a tag, the zone id, and the `InstalledApp` it was given unchanged; with no install source the
  installer is null. `:app`: `BuildConfig.GIT_COMMIT` matches `[0-9a-f]{12}|unknown`.
- [ ] **Step 2:** fail. **Step 3:** implement the reader (installer from `getInstallSourceInfo` on API 30+,
  `getInstallerPackageName` on 29, `runCatching` → null); the plugin adds the field lazily:
  ```kotlin
  onVariants { variant ->
      variant.buildConfigFields?.put("GIT_COMMIT", gitCommit.map { BuildConfigField("String", "\"$it\"", null) })
  }
  ```
  where `gitCommit` is `providers.exec { commandLine("git", "rev-parse", "--short=12", "HEAD"); isIgnoreExitValue = true }`
  mapped to its trimmed output, `unknown` when blank or when git cannot run.
- [ ] **Step 4:** pass. **Step 5:** commit.

### Task 5: About section and copy in the app

**Files:**
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/components/SectionCard.kt` (optional `action` slot in the header)
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/settings/SettingsScreen.kt` (About section, `onCopyBuildInfoClick`)
- Create: `ui/src/main/res/drawable/ic_copy.xml`
- Modify: `ui/src/main/res/values/strings.xml`, `ui/src/main/res/values-ru/strings.xml`
- Create: `app/src/main/kotlin/dev/catsradar/app/navigation/BuildInfoClipboard.kt`
- Modify: `app/src/main/kotlin/dev/catsradar/app/navigation/CatsRadarNavHost.kt` (effect, callback)
- Modify: `app/src/main/kotlin/dev/catsradar/app/di/DataModule.kt`, `PresentationModule.kt`
- Test: `app/src/test/kotlin/dev/catsradar/app/settings/AboutSectionTest.kt`,
  `app/src/test/kotlin/dev/catsradar/app/navigation/BuildInfoClipboardTest.kt`

- [ ] **Step 1: failing tests** — Compose: the section shows the four labels with their values and a click on
  the node described "Copy build info" calls the callback. Clipboard: `copyBuildInfo(clipboard, …)` puts the
  report as plain text on the clipboard; a toast appears at SDK 32 and not at SDK 33.
- [ ] **Step 2:** fail. **Step 3:** implement; bind `InstalledApp` from `BuildConfig` and
  `BuildInfoReader` in `dataModule`, `factoryOf(::AboutStateMapper)` in `presentationModule`.
- [ ] **Step 4:** `CI=true ./gradlew check` green. **Step 5:** commit.

### Task 6: Docs and gates

- [ ] `docs/features/build-info.md` (new), `docs/features/README.md`, `docs/features/strings.md` (report keys are
  not UI text).
- [ ] `CI=true ./gradlew check :app:assembleRelease`; run on the emulator: open Settings, copy, paste into a
  text field via `adb shell cmd clipboard`/`dumpsys clipboard`, screenshot the section in light and dark.
- [ ] `/code-review`, acceptance gate, PR.
