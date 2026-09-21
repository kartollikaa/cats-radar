# Slice 1 — Build Skeleton and Convention Plugins Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A buildable multi-module Gradle project — `build-logic` convention plugins, version catalog, five empty modules, an app that launches — with `./gradlew check` green locally and in CI.

**Architecture:** Layered modules per `docs/rules/module-structure.md`: `:domain`, `:data`, `:presentation` are Kotlin Multiplatform libraries using AGP's `com.android.kotlin.multiplatform.library`; `:ui` is a Compose Android library; `:app` is the Android application. Every module applies exactly one or two `catsradar.*` convention plugins from `build-logic` and configures nothing else. detekt (default rules + formatting) is wired into `check` by the convention plugin; rule tuning is slice 2.

**Tech Stack:** Gradle 9.5 (Kotlin DSL, version catalog, included build), AGP 9.4 with built-in Kotlin, Kotlin 2.4, Compose BOM + Material 3, detekt, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-21-cats-radar-design.md` §6, §8; map row 1 in `docs/tbd/decompositions/2026-09-21-cats-radar-v1.md`.

## Global Constraints

- `minSdk 29`; `compileSdk`/`targetSdk` = latest stable (36 today) — values live in `gradle/libs.versions.toml`, nowhere else.
- Package root `dev.catsradar`; module namespaces `dev.catsradar.<module>`; `applicationId = dev.catsradar`.
- Every version, including SDK levels, lives in `gradle/libs.versions.toml`.
- Module build files apply convention plugins and declare dependencies only — no `android {}`, no `kotlin { android {} }`, no SDK levels.
- `commonMain` imports nothing from `android.*`/`androidx.*` (no sources in this slice; the rule is enforced from slice 2).
- Comments follow `docs/rules/code-commenting-standards.md` — default none.
- JDK 17 runs Gradle; no toolchain auto-provisioning.

## Acceptance criteria (frozen 2026-09-22, auto mode)

| id | Observable | Proven by |
|---|---|---|
| AC-1 | `./gradlew check` exits 0 on a clean checkout | local run tail + green `check` workflow run on the PR |
| AC-2 | `./gradlew :app:assembleDebug` produces `app/build/outputs/apk/debug/app-debug.apk`; installed on an emulator it opens to a screen reading "Cats Radar" | mechanical: file exists; launch: `adb` install + screenshot when an emulator is available, otherwise manual |
| AC-3 | No module build file configures Android or Kotlin directly | `grep -lE '^\s*(android|compileSdk|minSdk|namespace)' */build.gradle.kts` prints nothing; the same grep over `build-logic/convention/src` prints files (positive control) |
| AC-4 | No dependency coordinate carries an inline version outside the catalog | `grep -rnE '["'"'"'][A-Za-z0-9.\-]+:[A-Za-z0-9.\-]+:[0-9]' --include='*.gradle.kts' .` prints nothing; the same grep on a scratch file containing `"a:b:1.0"` matches (positive control) |
| AC-5 | detekt runs as part of `check` and fails the build on a violation | `./gradlew check --dry-run` lists `:<module>:detekt` for every module; adding an unused private property to `:app` makes `./gradlew :app:detekt` fail, removing it makes it pass |
| AC-6 | CI runs `./gradlew check` on pull requests and on pushes to `main` | `gh run list --branch tech/gradle-skeleton` shows a successful `check` run for the PR head |

Assumptions recorded (auto mode): modules other than `:app` ship with no sources — an empty KMP/Android module compiles, so the map's "one trivial source" is dropped rather than adding churn; `:app` shows a static "Cats Radar" text until slice 5.

## File structure

```
settings.gradle.kts                 included build + repositories + module list
build.gradle.kts                    pins plugin versions on the root classpath (apply false)
gradle.properties                   JVM args, caching, configuration cache, AndroidX
gradle/libs.versions.toml           every version and coordinate
gradle/wrapper/                     wrapper jar + properties (Gradle 9.5.1)
gradlew, gradlew.bat                wrapper scripts
.editorconfig                       Kotlin official style, 120 columns
.github/workflows/check.yml         `./gradlew check` on PR and main
config/detekt/detekt.yml            override file on top of detekt defaults (slice 2 fills it)
build-logic/settings.gradle.kts     shares the root catalog with the included build
build-logic/convention/build.gradle.kts             kotlin-dsl, plugin registrations
build-logic/convention/src/main/kotlin/
  dev/catsradar/buildlogic/Catalog.kt               `libs` accessor helpers
  dev/catsradar/buildlogic/AndroidCommon.kt         compileSdk/minSdk/Java 17 for Android modules
  KmpLibraryConventionPlugin.kt                     catsradar.kmp.library
  AndroidLibraryConventionPlugin.kt                 catsradar.android.library
  AndroidApplicationConventionPlugin.kt             catsradar.android.application
  ComposeConventionPlugin.kt                        catsradar.compose
  DetektConventionPlugin.kt                         catsradar.detekt
domain/build.gradle.kts             catsradar.kmp.library
data/build.gradle.kts               catsradar.kmp.library + :domain
presentation/build.gradle.kts       catsradar.kmp.library + :domain
ui/build.gradle.kts                 catsradar.android.library + catsradar.compose + :presentation
app/build.gradle.kts                catsradar.android.application + catsradar.compose + all modules
app/src/main/AndroidManifest.xml
app/src/main/kotlin/dev/catsradar/app/MainActivity.kt
app/src/main/res/values/strings.xml
app/src/main/res/values/themes.xml
README.md
```

---

### Task 1: Root project, wrapper, catalog

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `.editorconfig`
- Copy: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` from `~/Projects/work/drinkit-mobile-android/` (same major Gradle line)
- Modify: `.gitignore` (already ignores `.gradle/`, `build/`, `local.properties`)

**Interfaces:**
- Produces: catalog aliases used by every later task — versions `android-compileSdk`, `android-minSdk`, `android-targetSdk`; plugins `android-application`, `android-library`, `android-kmp-library`, `kotlin-multiplatform`, `kotlin-compose`, `detekt`; libraries listed below.

- [ ] **Step 1: Write `settings.gradle.kts`** — with the full module list. Module directories arrive in Tasks 3–4, so the root build is first exercised in Task 3; Tasks 1–2 are verified through the included build (`-p build-logic`).

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "cats-radar"

include(":app")
include(":ui")
include(":presentation")
include(":domain")
include(":data")
```

- [ ] **Step 2: Write root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt) apply false
}
```

- [ ] **Step 3: Write `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8 -XX:+UseParallelGC
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
kotlin.daemon.jvmargs=-Xmx2g
kotlin.code.style=official
android.useAndroidX=true
```

- [ ] **Step 4: Write `gradle/libs.versions.toml`**

```toml
[versions]
android-compileSdk = "36"
android-minSdk = "29"
android-targetSdk = "36"

agp = "9.4.1"
kotlin = "2.4.20"
detekt = "1.23.8"
compose-bom = "2026.09.00"
androidx-activity = "1.13.0"
androidx-core = "1.19.0"
kotlinx-coroutines = "1.11.0"
junit4 = "4.13.2"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidx-core" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activity" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
junit4 = { module = "junit:junit", version.ref = "junit4" }
detekt-formatting = { module = "io.gitlab.arturbosch.detekt:detekt-formatting", version.ref = "detekt" }

# build-logic classpath
android-gradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "agp" }
kotlin-gradlePlugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
compose-compiler-gradlePlugin = { module = "org.jetbrains.kotlin:compose-compiler-gradle-plugin", version.ref = "kotlin" }
detekt-gradlePlugin = { module = "io.gitlab.arturbosch.detekt:detekt-gradle-plugin", version.ref = "detekt" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
android-kmp-library = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
```

- [ ] **Step 5: Wrapper.** Copy `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` from the Drinkit checkout; write `gradle/wrapper/gradle-wrapper.properties`:

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

If the 9.5.1 distribution is not cached under `~/.gradle/wrapper/dists` and the network is down, fall back to `gradle-9.5.0-bin.zip` (cached) and record it in the PR body.

- [ ] **Step 6: Write `.editorconfig`**

```ini
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true
indent_style = space
indent_size = 4
max_line_length = 120

[*.{kt,kts}]
ij_kotlin_allow_trailing_comma = true
ij_kotlin_allow_trailing_comma_on_call_site = true

[*.{yml,yaml,toml}]
indent_size = 2

[*.md]
max_line_length = 100
```

- [ ] **Step 7: Commit** — `build: root project, wrapper, version catalog` (verification of the root happens in Task 3 once the module directories exist).

### Task 2: `build-logic` convention plugins

**Files:**
- Create: `build-logic/settings.gradle.kts`, `build-logic/convention/build.gradle.kts`, the seven Kotlin files under `build-logic/convention/src/main/kotlin/`, `config/detekt/detekt.yml`

**Interfaces:**
- Produces plugin ids `catsradar.kmp.library`, `catsradar.android.library`, `catsradar.android.application`, `catsradar.compose`, `catsradar.detekt`.

- [ ] **Step 1: `build-logic/settings.gradle.kts`**

```kotlin
dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
```

- [ ] **Step 2: `build-logic/convention/build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "dev.catsradar.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "catsradar.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "catsradar.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "catsradar.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("compose") {
            id = "catsradar.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        register("detekt") {
            id = "catsradar.detekt"
            implementationClass = "DetektConventionPlugin"
        }
    }
}
```

- [ ] **Step 3: `dev/catsradar/buildlogic/Catalog.kt`**

```kotlin
package dev.catsradar.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

internal fun VersionCatalog.pluginId(alias: String): String = findPlugin(alias).get().get().pluginId

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).get()
```

- [ ] **Step 4: `dev/catsradar/buildlogic/AndroidCommon.kt`**

```kotlin
package dev.catsradar.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

internal fun Project.configureAndroid(extension: CommonExtension<*, *, *, *, *, *>) {
    extension.apply {
        compileSdk = libs.version("android-compileSdk").toInt()
        defaultConfig.minSdk = libs.version("android-minSdk").toInt()
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}

internal fun Project.moduleNamespace(): String = "dev.catsradar.$name"
```

If AGP 9.4 declares `CommonExtension` without type parameters, drop the `<*, *, *, *, *, *>`.

- [ ] **Step 5: `KmpLibraryConventionPlugin.kt`**

```kotlin
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import dev.catsradar.buildlogic.library
import dev.catsradar.buildlogic.libs
import dev.catsradar.buildlogic.moduleNamespace
import dev.catsradar.buildlogic.pluginId
import dev.catsradar.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-multiplatform"))
        pluginManager.apply(libs.pluginId("android-kmp-library"))
        pluginManager.apply("catsradar.detekt")

        extensions.configure<KotlinMultiplatformExtension> {
            val android = (this as ExtensionAware).extensions
                .getByName("android") as KotlinMultiplatformAndroidLibraryTarget
            android.apply {
                namespace = moduleNamespace()
                compileSdk = libs.version("android-compileSdk").toInt()
                minSdk = libs.version("android-minSdk").toInt()
                withHostTestBuilder {}.configure { isIncludeAndroidResources = true }
                compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
            }
            compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
            sourceSets.getByName("commonTest").dependencies {
                implementation(libs.library("kotlin-test"))
            }
        }
    }
}
```

If `getByName("android")` throws, the extension is registered under the legacy name — use `getByName("androidLibrary")` and note it in the PR.

- [ ] **Step 6: `AndroidLibraryConventionPlugin.kt`**

```kotlin
import com.android.build.api.dsl.LibraryExtension
import dev.catsradar.buildlogic.configureAndroid
import dev.catsradar.buildlogic.libs
import dev.catsradar.buildlogic.moduleNamespace
import dev.catsradar.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-library"))
        pluginManager.apply("catsradar.detekt")

        extensions.configure<LibraryExtension> {
            configureAndroid(this)
            namespace = moduleNamespace()
        }
    }
}
```

- [ ] **Step 7: `AndroidApplicationConventionPlugin.kt`**

```kotlin
import com.android.build.api.dsl.ApplicationExtension
import dev.catsradar.buildlogic.configureAndroid
import dev.catsradar.buildlogic.libs
import dev.catsradar.buildlogic.moduleNamespace
import dev.catsradar.buildlogic.pluginId
import dev.catsradar.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-application"))
        pluginManager.apply("catsradar.detekt")

        extensions.configure<ApplicationExtension> {
            configureAndroid(this)
            namespace = moduleNamespace()
            defaultConfig {
                applicationId = "dev.catsradar"
                targetSdk = libs.version("android-targetSdk").toInt()
                versionCode = 1
                versionName = "0.1.0"
            }
        }
    }
}
```

- [ ] **Step 8: `ComposeConventionPlugin.kt`**

```kotlin
import com.android.build.api.dsl.CommonExtension
import dev.catsradar.buildlogic.library
import dev.catsradar.buildlogic.libs
import dev.catsradar.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-compose"))

        extensions.getByType<CommonExtension<*, *, *, *, *, *>>().buildFeatures.compose = true

        dependencies {
            val bom = platform(libs.library("compose-bom"))
            add("implementation", bom)
            add("implementation", libs.library("compose-ui"))
            add("implementation", libs.library("compose-ui-tooling-preview"))
            add("implementation", libs.library("compose-material3"))
            add("debugImplementation", libs.library("compose-ui-tooling"))
        }
    }
}
```

- [ ] **Step 9: `DetektConventionPlugin.kt`**

```kotlin
import dev.catsradar.buildlogic.library
import dev.catsradar.buildlogic.libs
import dev.catsradar.buildlogic.pluginId
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("detekt"))

        extensions.configure<DetektExtension> {
            buildUponDefaultConfig = true
            parallel = true
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            source.setFrom(files("src"))
        }

        dependencies {
            add("detektPlugins", libs.library("detekt-formatting"))
        }
    }
}
```

- [ ] **Step 10: `config/detekt/detekt.yml`** — the override file slice 2 fills; valid and nearly empty now:

```yaml
build:
  maxIssues: 0
```

- [ ] **Step 11: Verify the included build compiles**

Run: `./gradlew -p build-logic :convention:build --console=plain 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`. Compile errors here are API-shape mismatches with AGP 9.4 — fix per the notes on Steps 4–5.

- [ ] **Step 12: Commit** — `build: convention plugins for kmp, android, compose, detekt`.

### Task 3: KMP modules `:domain`, `:data`, `:presentation`

**Files:**
- Create: `domain/build.gradle.kts`, `data/build.gradle.kts`, `presentation/build.gradle.kts`, and empty `src/commonMain/kotlin/` directories with a `.gitkeep` each

**Interfaces:**
- Produces: project accessors `projects.domain`, `projects.data`, `projects.presentation`.

- [ ] **Step 1: `domain/build.gradle.kts`**

```kotlin
plugins {
    id("catsradar.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
```

- [ ] **Step 2: `data/build.gradle.kts`**

```kotlin
plugins {
    id("catsradar.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.domain)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
```

- [ ] **Step 3: `presentation/build.gradle.kts`** — identical to `data` (depends on `:domain` and coroutines).

- [ ] **Step 4: Verify the KMP modules configure and assemble**

Run: `./gradlew :domain:assemble :data:assemble :presentation:assemble --console=plain 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`; the first run also downloads the 9.5.1 distribution if not cached. `:ui` and `:app` are still missing build files, so run with `-x` nothing — Gradle skips directories without build scripts but *does* fail on `include` of a non-existent directory only when it has no build file **and** no directory; the empty module dirs from Task 4 Step 0 avoid that. If configuration fails on `:ui`/`:app`, create the two directories with placeholder `build.gradle.kts` containing only the plugin block from Task 4 first.

- [ ] **Step 5: Confirm the Android host-test source set exists**

Run: `./gradlew :domain:tasks --all --console=plain 2>&1 | grep -iE 'hosttest|allTests' | head`
Expected: tasks such as `testAndroidHostTest` (or `androidHostTest`) and `allTests` are listed.

- [ ] **Step 6: Commit** — `build: kmp modules domain, data, presentation`.

### Task 4: `:ui` and `:app`

**Files:**
- Create: `ui/build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/dev/catsradar/app/MainActivity.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`

- [ ] **Step 1: `ui/build.gradle.kts`**

```kotlin
plugins {
    id("catsradar.android.library")
    id("catsradar.compose")
}

dependencies {
    implementation(projects.presentation)
}
```

- [ ] **Step 2: `app/build.gradle.kts`**

```kotlin
plugins {
    id("catsradar.android.application")
    id("catsradar.compose")
}

dependencies {
    implementation(projects.ui)
    implementation(projects.presentation)
    implementation(projects.domain)
    implementation(projects.data)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
```

- [ ] **Step 3: `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.CatsRadar">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 4: `MainActivity.kt`**

```kotlin
package dev.catsradar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Placeholder() }
    }
}

@Composable
private fun Placeholder() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.app_name))
            }
        }
    }
}
```

- [ ] **Step 5: resources**

`strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Cats Radar</string>
</resources>
```

`themes.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.CatsRadar" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 6: Build the APK**

Run: `./gradlew :app:assembleDebug --console=plain 2>&1 | tail -20 && ls -la app/build/outputs/apk/debug/`
Expected: `BUILD SUCCESSFUL`, `app-debug.apk` listed (AC-2 mechanical half).

- [ ] **Step 7: Full check**

Run: `./gradlew check --console=plain 2>&1 | tail -30`
Expected: `BUILD SUCCESSFUL` (AC-1). detekt formatting findings on the new files are fixed with `./gradlew detekt --auto-correct` and re-run — never suppressed.

- [ ] **Step 8: AC-5 positive control** — add `private val unused = 1` inside `MainActivity`, run `./gradlew :app:detekt --console=plain 2>&1 | tail -5`, expect `BUILD FAILED` with `UnusedPrivateProperty`; remove it, rerun, expect success. Paste both tails into the PR body.

- [ ] **Step 9: AC-3 / AC-4 greps** — run the two commands from the criteria table with their positive controls; paste output into the PR body.

- [ ] **Step 10: Launch (AC-2 second half)** — if `adb devices` lists an emulator: `adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n dev.catsradar/dev.catsradar.app.MainActivity`, then `adb exec-out screencap -p > /tmp/…/launch.png` and inspect. Otherwise record "manual — not run" in the PR body.

- [ ] **Step 11: Commit** — `build: ui and app modules with a launchable placeholder activity`.

### Task 5: CI and README

**Files:**
- Create: `.github/workflows/check.yml`, `README.md`

- [ ] **Step 1: workflow**

```yaml
name: check

on:
  push:
    branches: [main]
  pull_request:

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  check:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew check --console=plain
```

- [ ] **Step 2: `README.md`** — one paragraph (what the app is), links to the spec, the rules, the decomposition map, and the three commands: `./gradlew check`, `./gradlew :app:installDebug`, `./gradlew detekt --auto-correct`.

- [ ] **Step 3: Commit and push** — `ci: run check on pull requests and main`; `git push -u origin tech/gradle-skeleton`.

- [ ] **Step 4: Verify CI (AC-6)** — after the PR is opened: `gh run list --branch tech/gradle-skeleton --limit 3`, then `gh run watch <id> --exit-status`. Expected: `check` succeeds. A failure specific to the runner (SDK licence, platform download) is fixed in the workflow, not by skipping the step.

### Task 6: Size check, PR, gate

- [ ] **Step 1: Size** — `git diff --stat origin/main...HEAD -- . ':!*.md' ':!gradlew' ':!gradlew.bat' ':!gradle/wrapper/*'`; state the number in the PR body (budget ~550, cap 600 target).
- [ ] **Step 2: Update the map** — slice 1 → `in-review`; decision log gets a line if anything deviated (wrapper version, extension name).
- [ ] **Step 3: PR** — `gh pr create --base main --head tech/gradle-skeleton` with the criteria table, evidence per criterion, size, and the attribution footer.
- [ ] **Step 4: acceptance-gate** — run `acceptance:acceptance-gate` against the six criteria before requesting review.

## Self-review

- Spec coverage: §6.1 module list ✔ (five modules + build-logic), §8 conventions ✔ (catalog, minSdk 29, package root, convention plugins), CI ✔. Static-analysis *tuning* is slice 2 by design.
- Placeholder scan: clean.
- Type consistency: helper names `libs`, `version`, `pluginId`, `library`, `configureAndroid`, `moduleNamespace` are used identically across Tasks 2–4.
