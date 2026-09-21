# Cats Radar

A personal cat-encounter counter. One tap logs a cat, one more logs a cat with a photo; every
encounter carries the best location available, and the app turns the log into statistics — totals,
streaks, cats per country → city → area, and an encounter rate over automatically detected outings.
Android first, with the domain, data, and presentation layers in Kotlin Multiplatform modules.

## Where things are

- Design spec: [`docs/superpowers/specs/2026-09-21-cats-radar-design.md`](docs/superpowers/specs/2026-09-21-cats-radar-design.md)
- Rules for the code (modules, MVI, Compose, dates, comments, static analysis): [`docs/rules/`](docs/rules/)
- PR decomposition map: [`docs/tbd/decompositions/2026-09-21-cats-radar-v1.md`](docs/tbd/decompositions/2026-09-21-cats-radar-v1.md)
- Competitor research: [`docs/research/`](docs/research/)

## Build

Requires JDK 17 and an Android SDK (`local.properties` with `sdk.dir`, or `ANDROID_HOME`).

```bash
./gradlew check
```

```bash
./gradlew :app:installDebug
```

```bash
./gradlew detekt --auto-correct
```
