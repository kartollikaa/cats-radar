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
- Tests first (`commonTest` with fakes). Robolectric only where the platform is what's under test:
  Room in `:data`, and Android, Compose and Glance behaviour in `:app`, the one module whose test
  harness hosts them. `./gradlew check` must be green before a PR.
- Every slice ends with an independent code review (`/code-review` on the PR) and the acceptance
  gate against its frozen criteria; findings are fixed before the PR is marked ready.
- **Every change to a feature's behaviour updates that feature's document in `docs/features/` in the
  same PR.** The documents are the human-readable account of what the app does and how it behaves at
  the edges; a stale one is worse than none. See `docs/features/README.md`.
- Branches `feature/ | fix/ | tech/`; one PR per slice; merge commits. The PR decomposition map is
  `docs/tbd/decompositions/`; slice plans live in `docs/superpowers/plans/` and are archived when the
  slice ships.
- Comments: default none. Re-read every comment you add against the tests in
  `docs/rules/code-commenting-standards.md` before committing.
- Strings: EN and RU resources; no hard-coded user-facing text.
