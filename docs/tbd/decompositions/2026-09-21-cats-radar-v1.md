# Cats Radar v1 — PR Decomposition Map

- **Created:** 2026-09-21
- **Epic reference:** [docs/superpowers/specs/2026-09-21-cats-radar-design.md](../../superpowers/specs/2026-09-21-cats-radar-design.md)
- **Trunk:** `main`
- **Size budgets:** target ≤600 reviewable lines, cap 1000 (see tbd:sizing-pull-requests). Excluded from the
  count: `*.md`, Gradle wrapper files, exported Room schemas (`data/schemas/**`), lockfiles, screenshot baselines.
- **Integration strategy:** the app is unreleased, so every slice is **naturally safe** — it is either a
  library/module nothing references yet, or a complete, self-contained behaviour wired end to end. No
  toggles, no branch-by-abstraction, no cleanup debt.

## Slices

| # | PR title | Purpose (one sentence) | Strategy | Size budget | Depends on | Status |
|---|----------|------------------------|----------|-------------|------------|--------|
| 1 | Gradle skeleton and convention plugins | Buildable multi-module project with `build-logic`, version catalog, empty modules, CI running `./gradlew check`. | safe | ~550 | — | merged |
| 2 | Quality gates: detekt, Lint, Konsist | Static analysis and architecture tests wired into `check`, failing on purpose once then passing. | safe | ~300 | 1 | merged |
| 3 | Domain core: models, Tuning, Geohash, SessionSplitter | Pure domain types and the two geometric/temporal primitives, fully unit-tested. | safe | ~500 | 1 | merged |
| 4 | Room database and repositories | Encounter and PlaceCell entities, DAOs, `CatsDatabase` (KMP driver), repository implementations, Robolectric tests. | safe | ~600 | 3 | merged |
| 5 | App shell: theme, Navigation 3 host, Koin, MVI Store | `CatsRadarTheme`, `@ThemePreviews`, `Store` base, `NavDisplay` with a single `Counter` entry showing a placeholder, Koin bootstrap. | safe | ~400 | 2 | merged |
| 6 | Tally: log a cat and undo | `LogTally`/`UndoLastTally` use cases, `CounterStore`, `CounterScreen` with big button, undo chip, haptic; count from Room `Flow`. | safe | ~450 | 4, 5 | merged |
| 7 | Location attach | `LocationProvider` (Fused), `LocationPolicy` with `LAST_KNOWN_MAX_AGE` and backfill, `AttachLocation` use case, expedited worker, permission request on first tally + hint. | safe | ~600 | 6 | merged |
| 8 | Encounters list and bottom navigation | `ObserveEncounters`, grouping by outing, `DateTimeFormatter`, `EncountersStore`/`Screen` with outing headers and empty state, bottom bar Counter · Encounters with root-stack back rule. | safe | ~500 | 6 | merged |
| 8a | String resources | Move every user-facing literal in `:ui` and `:presentation` into `ui/res/values/strings.xml`; mapper-chosen labels become presentation tokens resolved by the composable. Establishes the pattern slices 9–20 follow and shrinks slice 21 to `values-ru` alone. | safe | ~150 | 8 | merged |
| 9 | Encounter detail with delete and undo | `EncounterDetail` key/store/screen, soft delete from detail, undo on the detail screen for `UNDO_VISIBLE`, then back to the list. | safe | ~300 | 8 | in-progress |
| 10 | Photo pipeline in data | `ExifReader`, `ImageResizer`, `Digest`, `GallerySaver` (MediaStore), `PhotoStorage` interfaces + Android implementations, unit tests with fixture JPEGs. | safe | ~450 | 4 | planned |
| 11 | Photo capture flow | `LogPhoto` use case, camera button + `TakePicture`, `origin`/EXIF rules, thumbnails in list and detail via Coil, `saveOriginalsToGallery` setting in DataStore (default on, no UI yet). | safe | ~500 | 7, 9, 10 | planned |
| 12 | StatsCalculator | Totals, period counts, streaks, milestones, sessions, rate eligibility and auto-scaled rate, all as pure functions with exhaustive tests. | safe | ~550 | 3 | planned |
| 13 | Statistics screen and current outing | `ObserveStats`, `StatisticsStore`/`Screen` (headline, streak, rate block, outings, next milestone), current-outing block on Counter, milestone toast with `lastSeenMilestone`. | safe | ~500 | 8, 12 | planned |
| 14 | Reverse geocoding of place cells | `ReverseGeocoder` (Android `Geocoder`), PlaceCell creation on location attach, `ResolvePendingPlaces` use case, connected-network worker with backoff, region tree builder + tests. | safe | ~550 | 7, 12 | planned |
| 15 | Regions drill-down screens | `Regions(level, parentKey)` and `RegionEncounters(areaKey)` keys, stores, screens; Unresolved / No location pseudo-nodes; entry from Statistics. | safe | ~450 | 13, 14 | planned |
| 16 | Gallery import | `ImportRules` (recent-photo window, digest dedup, EXIF time offset) + tests, `ImportPhotos` use case, worker with progress notification, long-press entry, summary with undo. | safe | ~600 | 11 | planned |
| 17 | Backup format and merge rules | Serializable export models, ZIP writer/reader, `manifest.json` versioning, merge rules (newer `updatedAt`, delete-vs-live) with tests, `ExportBackup`/`ImportBackup` use cases. | safe | ~500 | 11, 14 | planned |
| 18 | Settings screen: backup export/import and gallery toggle | `Settings` key/store/screen, SAF contracts, export/import workers with progress, gallery toggle UI, round-trip test. | safe | ~450 | 17 | planned |
| 19 | Home-screen widget | Glance widget with today's count and "+1", receiver, manifest, refresh on table change and periodic. | safe | ~350 | 7 | planned |
| 20 | Purge soft-deleted encounters | Periodic worker removing files and rows older than `PURGE_AFTER`; scheduled at app start. | safe | ~200 | 11 | planned |
| 21 | Russian localisation | `values-ru` for every string resource; plural rules for cats/outings/days. | safe | ~200 | 18 | planned |
| 22 | Cat coat | `CatCoat` picker strip after tally/photo, coat on the encounter detail (set/clear), "By coat" block in Statistics; `SetCoat` use case. | safe | ~450 | 9, 13 | planned |

Status values: `planned · in-progress · in-review · merged · dropped`

## Slice details

### Slice 1 — Gradle skeleton and convention plugins
- **In scope:** `settings.gradle.kts`, root build, `gradle/libs.versions.toml`, wrapper; `build-logic` with
  `catsradar.kmp.library`, `catsradar.android.library`, `catsradar.android.application`, `catsradar.compose`,
  `catsradar.detekt` (plugin applied, default config); modules `:domain`, `:data`, `:presentation`, `:ui`, `:app`
  each with a build file and one trivial source so the module compiles; `MainActivity` showing nothing;
  `.editorconfig`, `.gitignore`; GitHub Actions workflow running `./gradlew check`; repo `README.md`.
- **Out of scope:** any product code; detekt rule tuning (slice 2).
- **Ships safely because:** the app installs and shows an empty activity; nothing user-facing is promised yet.
- **Cleanup owed:** none.

### Slice 2 — Quality gates
- **Reality check (2026-09-22):** AGP 9.4.1's KMP library plugin creates no lint task, so Lint is
  enforced on `:app` and `:ui` only; the DSL is set on the KMP modules and activates when AGP adds it.
  compose-rules is pinned to the 0.4.x line, which is what supports detekt 1.23.x.
- **In scope:** `config/detekt/detekt.yml` (overrides on top of the default config), `detekt-formatting` and
  `compose-rules` wired in `catsradar.detekt`; `lint.xml` + `warningsAsErrors`; Konsist tests in `:app`
  encoding `docs/rules/module-structure.md` (layer imports, `*Store`/`*State`/`*Intent`/`*Effect` naming,
  `Modifier` default). Each gate is shown failing on a deliberate violation in the PR description, then passing.
- **Out of scope:** baseline files (none allowed for new code).
- **Ships safely because:** tooling only.
- **Cleanup owed:** none.

### Slice 3 — Domain core
- **In scope:** `Encounter`, `PlaceCell`, `Session`, enums `EncounterKind`, `EncounterOrigin`, `LocationSource`,
  `PlaceStatus`, `CatCoat`; `Tuning`; `Geohash.encode/decode/prefix` with published vectors; `SessionSplitter`; `localDate()`
  helpers per `docs/rules/date-time.md`. `commonTest` only.
- **Out of scope:** repositories, use cases, stats.
- **Ships safely because:** `:domain` is referenced by no runtime code yet.
- **Cleanup owed:** none.

### Slice 4 — Room database and repositories
- **In scope:** entities and DAOs for both tables (including the nullable `coat` column, so the first
  schema already carries it), `CatsDatabase` with `BundledSQLiteDriver` and the
  `RoomDatabaseConstructor` expect/actual, type converters, entity ↔ domain mappers, `EncounterRepository`
  and `PlaceCellRepository` interfaces (`:domain`) + implementations, Robolectric DAO tests (soft-delete
  filtering, digest lookup, cell upsert), exported schema.
- **Out of scope:** DataStore (arrives with its first consumer in slice 11), workers.
- **Ships safely because:** `:data` is unreferenced until slice 6 wires it.
- **Cleanup owed:** none.

### Slice 5 — App shell
- **In scope:** `CatsRadarTheme` (explicit light/dark schemes), `@ThemePreviews`, `Store<State, Intent, Effect>`
  base with tests, `NavDisplay` + `rememberNavBackStack(Counter)` + decorators, Koin `startKoin` with empty
  modules, placeholder `CounterScreen` rendering static text.
- **Out of scope:** real state, bottom bar (slice 8).
- **Ships safely because:** a single static screen; no behaviour promised.
- **Cleanup owed:** the placeholder text is replaced in slice 6 (in-scope there, not debt).

### Slice 6 — Tally
- **In scope:** `LogTally`, `UndoLastTally` use cases; `IdGenerator`, `DeviceIdProvider`, `Haptics` interfaces +
  Android impls; `CounterState/Intent/Effect/Store` + `CounterStateMapper`; `CounterScreen` with the big button,
  undo chip (`UNDO_VISIBLE`), no debounce; Koin `domainModule`/`dataModule`/`presentationModule`; Store tests.
- **Out of scope:** location (slice 7), current-outing block (slice 13).
- **Ships safely because:** tally works end to end and persists; that is a complete, honest behaviour.
- **Cleanup owed:** none.

### Slice 7 — Location attach
- **In scope:** `LocationProvider` + Fused implementation, `LocationPolicy` (chain, `LAST_KNOWN_MAX_AGE`) + tests,
  `AttachLocation` use case with backfill of `NONE` encounters in the open outing, `AttachLocationWorker`
  (expedited) + `KoinWorkerFactory`, permission request on first tally, hint row in Counter state, manifest.
- **Out of scope:** geocoding (slice 14).
- **Ships safely because:** tallies gain coordinates silently; denial degrades to `NONE` exactly as the spec says.
- **Cleanup owed:** none.

### Slice 8 — Encounters list and bottom navigation
- **In scope:** `ObserveEncounters`, `DateTimeFormatter` interface + Android impl, `EncountersStateMapper`
  (grouping by outing, day headers), `EncountersStore/Screen` with empty state, `Encounters` key, bottom bar
  Counter · Encounters with the `[Counter, Tab]` back rule.
- **Out of scope:** detail (slice 9), thumbnails (slice 11), Statistics tab (slice 13).
- **Ships safely because:** list shows tally rows fully; the third tab appears only when its screen exists.
- **Cleanup owed:** none.

### Slice 9 — Encounter detail
- **In scope:** `EncounterDetail(id)` key, store, screen (time, location source, coordinates), `DeleteEncounter`
  + `UndoDelete` use cases, undo snackbar effect.
- **Out of scope:** photo display (slice 11), editing (non-goal).
- **Ships safely because:** complete behaviour for the rows that exist.
- **Cleanup owed:** none.

### Slice 10 — Photo pipeline in data
- **In scope:** `ExifReader` (GPS, `DateTimeOriginal`, `OffsetTimeOriginal`), `ImageResizer` (`PHOTO_MAX_SIDE`,
  `PHOTO_QUALITY`, `THUMB_SIZE`), `Digest` (SHA-256), `GallerySaver` (MediaStore `Pictures/Cats Radar`),
  `PhotoStorage` (private files) — interfaces in `:domain`, Android impls in `:data`, Robolectric tests with
  small fixture JPEGs.
- **Out of scope:** any UI or use case.
- **Ships safely because:** unreferenced until slice 11.
- **Cleanup owed:** none.

### Slice 11 — Photo capture flow
- **In scope:** `LogPhoto` use case (EXIF → location chain, `origin = CAMERA`), camera button + `TakePicture`
  + `FileProvider`, `SettingsRepository` on DataStore with `saveOriginalsToGallery` (default true), Coil
  thumbnails in Encounters grid and full photo in detail, error toasts per spec §6.6, Store tests.
- **Out of scope:** gallery import (slice 16), Settings UI (slice 18).
- **Ships safely because:** capture works end to end with the default setting.
- **Cleanup owed:** none.

### Slice 12 — StatsCalculator
- **In scope:** `StatsCalculator(encounters, placeCells, now, settings)` producing `Stats`: totals, today/7/30,
  with-photo share, current/longest streak, milestones, sessions with rate eligibility, overall and best rate
  with auto-scaled unit, outings; exhaustive `commonTest` incl. timezone-change and day-boundary cases.
- **Out of scope:** region tree (slice 14), UI.
- **Ships safely because:** pure function, unreferenced.
- **Cleanup owed:** none.

### Slice 13 — Statistics screen and current outing
- **In scope:** `ObserveStats`, `StatisticsStateMapper` (labels, units, `—`), `StatisticsStore/Screen`, third
  bottom-bar tab, current-outing block on Counter with ticking elapsed time, milestone toast + `lastSeenMilestone`.
- **Out of scope:** regions entry point (slice 15).
- **Ships safely because:** every number shown is defined and tested in slice 12.
- **Cleanup owed:** none.

### Slice 14 — Reverse geocoding
- **In scope:** `ReverseGeocoder` + Android `Geocoder` impl (`UNAVAILABLE` when absent), PlaceCell creation in
  `AttachLocation`, `ResolvePendingPlaces` use case, `GeocodePendingCellsWorker` (unique, connected, backoff,
  `MAX_GEOCODE_ATTEMPTS`), `RegionTree` builder (Country → City → Area, pseudo-nodes, area labels) + tests.
- **Out of scope:** screens (slice 15).
- **Ships safely because:** background resolution has no UI until slice 15; data accumulates correctly.
- **Cleanup owed:** none.

### Slice 15 — Regions drill-down screens
- **In scope:** `Regions(level, parentKey)`, `RegionEncounters(areaKey)` keys/stores/screens, entry row on
  Statistics, empty and pseudo-node rendering.
- **Out of scope:** map view (non-goal).
- **Ships safely because:** reads only what slice 14 produced.
- **Cleanup owed:** none.

### Slice 16 — Gallery import
- **In scope:** `ImportRules` + tests, `ImportPhotos` use case (digest skip, EXIF time, `RECENT_PHOTO_WINDOW`),
  `ImportPhotosWorker` with progress notification and `IMPORT_BATCH_MAX` (≤ `getPickImagesMaxLimit()`),
  `PickMultipleVisualMedia` on long-press camera, summary dialog with undo; `ACCESS_MEDIA_LOCATION` +
  `setRequireOriginal` spike on a real device first — result recorded in the decision log.
- **Out of scope:** Settings entry point (slice 18 adds the second entry).
- **Ships safely because:** complete behaviour; historical photos never get today's location by construction.
- **Cleanup owed:** none.

### Slice 17 — Backup format and merge rules
- **In scope:** `kotlinx.serialization` models, ZIP writer/reader (`manifest.json`, `encounters.json`,
  `placecells.json`, `photos/`), `formatVersion` check, merge rules + tests, `ExportBackup`/`ImportBackup`
  use cases over a `BackupSink`/`BackupSource` abstraction.
- **Out of scope:** UI, SAF (slice 18).
- **Ships safely because:** unreferenced until slice 18.
- **Cleanup owed:** none.

### Slice 18 — Settings screen
- **In scope:** `Settings` key/store/screen (export, import, import photos, gallery toggle), `CreateDocument`/
  `OpenDocument` contracts, `ExportWorker`/`ImportBackupWorker` with progress, top-bar entry, export → wipe →
  import round-trip test.
- **Out of scope:** account/sync (non-goal).
- **Ships safely because:** complete behaviour.
- **Cleanup owed:** none.

### Slice 19 — Home-screen widget
- **In scope:** Glance `CatsRadarWidget` + receiver + `widget_info`, today's count, "+1" action inserting via
  `LogTally` and enqueueing `AttachLocationWorker`, `updateAll()` on table change, periodic refresh.
- **Out of scope:** undo on widget (by design).
- **Ships safely because:** complete behaviour; widget is opt-in by the user.
- **Cleanup owed:** none.

### Slice 20 — Purge soft-deleted encounters
- **In scope:** `PurgeDeleted` use case, `PurgeDeletedWorker` (periodic), scheduling at app start, test that
  files and rows older than `PURGE_AFTER` go and newer stay.
- **Ships safely because:** only touches rows already hidden from every screen.
- **Cleanup owed:** none.

### Slice 21 — Russian localisation
- **In scope:** `values-ru/strings.xml` for every resource, plurals, Lint `MissingTranslation` enforced.
- **Ships safely because:** additive resources.
- **Cleanup owed:** none.

### Slice 22 — Cat coat
- **In scope:** `CatCoat` enum already in `:domain` (slice 3) gets its swatch colours and labels in
  `:ui`; `SetCoat(encounterId, coat?)` use case; coat strip shown with the Undo chip on Counter after a
  tally or a photo, bound to the latest encounter; coat row on `EncounterDetail` with set/clear;
  `StatsCalculator` "By coat" rows and their block on Statistics; Store and mapper tests.
- **Out of scope:** coat on the widget; coat filter on the map (Map epic).
- **Ships safely because:** additive UI on screens that already exist; `coat` stays null until used.
- **Cleanup owed:** none.

## Decision log
- 2026-09-22: slice 1 built. AGP 9.4 requires Gradle ≥ 9.6 (wrapper 9.7.1); Compose 1.12 requires
  `compileSdk 37`, so compile/target SDK are 37. Modules other than `:app` ship without placeholder
  sources — an empty KMP/Android module compiles. `detekt.yml` carries one early override
  (`FunctionNaming.ignoreAnnotated: [Composable]`) because the default rule rejects every composable;
  the rest of the tuning stays in slice 2. The owner's new requirements (coat, map with route,
  personal heatmap, cats-per-km) are recorded for a spec v4 and a "Map" epic after v1; a coat
  column and its UI become a v1 slice (see next entry once the spec is amended).
- 2026-09-22: spec v4 — owner's household wish-list. Coat becomes v1 (column in slice 3/4, UI as new
  slice 22); map, route polyline, GPS-track walks, personal heatmap and cats-per-km form the Map epic
  after v1 on MapLibre + OSM, so nothing here changes. `androidUnitTest` → `androidHostTest` in the
  docs to match the AGP KMP plugin's source-set names.
- 2026-09-22: slice 8a inserted. Slices 6–8 shipped with hard-coded English in `:ui` and one
  mapper, against `CLAUDE.md`'s "no hard-coded user-facing text", and no review caught it. Fixed now,
  while it is twelve strings, rather than after thirteen more slices; slice 21 becomes translation
  only. Rule sharpened in `docs/rules/compose-patterns.md` (token in State, words in resources).
- 2026-09-22: slice 16 gains a device spike — scoped storage redacts EXIF GPS; Photo Picker URIs may not honour
  `setRequireOriginal`. Slice scope unchanged; outcome decides whether imports carry EXIF location.
- 2026-09-21: initial map from spec v3. The 11 slices discussed in review were split to keep every PR under
  the 600-line target: scaffold → 1+2, tally → 5+6, encounters → 8+9, photos → 10+11, statistics → 12+13,
  regions → 14+15, backup → 17+18; empty states moved into each screen's slice instead of a final polish PR.
