# Cats Radar

Personal cat-encounter counter. Android-first Kotlin Multiplatform; design spec in
`docs/superpowers/specs/`, competitor research in `docs/research/`.

## Read before coding

- Design: `docs/superpowers/specs/2026-09-21-cats-radar-design.md` — the source of truth for behaviour,
  data model, and statistics definitions.
- Rules (all binding):
  - @docs/rules/module-structure.md
  - @docs/rules/mvi-architecture.md
  - @docs/rules/compose-patterns.md
  - @docs/rules/compose-preview-patterns.md
  - @docs/rules/date-time.md
  - @docs/rules/code-commenting-standards.md
  - @docs/rules/static-analysis.md

## Conventions

- Gradle Kotlin DSL; every version lives in `gradle/libs.versions.toml`; every module applies a
  convention plugin from `build-logic` and configures nothing else.
- Package root `dev.catsradar`. Module packages: `dev.catsradar.domain`, `.data`, `.presentation`,
  `.ui`, `.app`.
- Tests first (`commonTest` with fakes; Robolectric only for Room). `./gradlew check` must be green
  before a PR.
- Branches `feature/ | fix/ | tech/`; one PR per slice; merge commits. Plans live in
  `docs/superpowers/plans/` and are archived when the slice ships.
- Comments: default none. Re-read every comment you add against the tests in
  `docs/rules/code-commenting-standards.md` before committing.
- Strings: EN and RU resources; no hard-coded user-facing text.
