# Cats Radar — AI Agent Guidelines

Personal cat-encounter counter. Android-first Kotlin Multiplatform; design spec in `docs/superpowers/specs/`, competitor research in `docs/research/`.

---

## Read before coding

- **Design**: `docs/superpowers/specs/2026-09-21-cats-radar-design.md` — the source of truth for behaviour, data model, and statistics definitions.
- **Rules** (all binding):
  - `@docs/rules/module-structure.md`
  - `@docs/rules/mvi-architecture.md`
  - `@docs/rules/dependency-injection.md`
  - `@docs/rules/compose-patterns.md`
  - `@docs/rules/compose-preview-patterns.md`
  - `@docs/rules/date-time.md`
  - `@docs/rules/code-commenting-standards.md`
  - `@docs/rules/static-analysis.md`

---

## Module Structure

- **Package Root**: `dev.catsradar`
- **Modules**:
  - `:app` — Android Application entry point, Koin DI setup, background WorkManager workers, widgets, and distribution product flavors (`play`, `sideload`).
  - `:ui` — Jetpack Compose UI components and screens.
  - `:presentation` — ViewModels, MVI state management, and UI state mapping.
  - `:domain` — Business logic, use cases, models, and platform interfaces.
  - `:data` — Database (Room), DataStore, network repositories, system service implementations.
  - `:build-logic` — Gradle convention plugins.

---

## Product Flavors (`distribution` dimension)

The `:app` module defines product flavors on the `distribution` dimension:

- **`play`**:
  - Purpose: Build intended for Google Play Store release.
  - Manifest Rule: **MUST NOT** include `REQUEST_INSTALL_PACKAGES` permission in its merged manifest.
  - Source set: `app/src/play/` and tests in `app/src/testPlay/`.

- **`sideload`**:
  - Purpose: Standalone APK distribution (e.g. GitHub releases).
  - Manifest Rule: Declares `REQUEST_INSTALL_PACKAGES` permission in `app/src/sideload/AndroidManifest.xml` for self-update package installations.
  - Source set: `app/src/sideload/` and tests in `app/src/testSideload/`.

---

## Conventions

- **Gradle**: Kotlin DSL; every version lives in `gradle/libs.versions.toml`; every module applies a convention plugin from `build-logic` and configures nothing else.
- **Testing**:
  - Tests first (`commonTest` with fakes).
  - Robolectric only where the subject is Android itself — Room, resources, a `Bundle`, a widget — never for logic a plain JVM test reaches.
  - Flavor permission tests live in `app/src/testPlay/` and `app/src/testSideload/`.
- **Build & Check Commands**:
  - Assemble: `./gradlew :app:assemblePlayDebug :app:assembleSideloadDebug`
  - Unit Tests: `./gradlew :app:testPlayDebugUnitTest :app:testSideloadDebugUnitTest`
  - Full Check: `./gradlew check`
- **Feature Documentation**: **Every change to a feature's behaviour updates that feature's document in `docs/features/` in the same PR.** The documents are the human-readable account of what the app does and how it behaves at the edges; a stale one is worse than none. See `docs/features/README.md`.
- **Git & Workflow**: Branches `feature/ | fix/ | tech/`; one PR per slice; merge commits. The PR decomposition map is `docs/tbd/decompositions/`; slice plans live in `docs/superpowers/plans/` and are archived when the slice ships.
- **Code Reviews**: Every slice ends with an independent code review (`/code-review` on the PR) and the acceptance gate against its frozen criteria; findings are fixed before the PR is marked ready.
- **Comments**: Default none. Re-read every comment you add against the tests in `docs/rules/code-commenting-standards.md` before committing.
- **Strings**: EN and RU resources; no hard-coded user-facing text.
