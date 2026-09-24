# Cats Radar — design spec

Date: 2026-09-21. Status: v4 (2026-09-22) — coat in v1, Map epic defined for after v1.
Research behind this spec: [docs/research/2026-09-21-competitor-scan.md](../../research/2026-09-21-competitor-scan.md).

## 1. Summary

Cats Radar is a personal cat-encounter counter. One tap logs a cat; one more tap logs a cat with a
photo; a home-screen widget logs a cat without opening the app. Every encounter carries location
metadata when it can be obtained. The app turns the log into statistics: totals, streaks, cats per
country → city → area, and an encounter rate derived from automatically detected outings.

### Goals (v1)

1. Logging a cat takes one tap and never waits for GPS, camera, or network.
2. Every encounter gets the best location available under a fixed precedence rule.
3. Statistics work fully offline; place names fill in when the network allows.
4. Your data is yours: photos also land in the phone gallery, and a ZIP export/import exists.
5. Data model is ready for a later sync/social layer without a migration of user data.
6. Android ships first on Compose + Navigation 3 + Room + coroutines; `:domain`, `:data`, and
   `:presentation` are Kotlin Multiplatform modules so iOS can be added without rewriting them.

### Non-goals (v1)

- Social features, accounts, cloud sync, and a *shared* heatmap (out of scope by owner).
- Map view, outing route, personal heatmap, distance-based rates — the **Map epic** that follows v1
  (§9); v1 only stores what that epic needs (coordinates, coat).
- Quantity per encounter, unique-cat identity ("same cat as yesterday?"), breed identification.
- Editing an encounter's time or location after the fact; captions/notes. (Coat *is* editable.)
- Manual outing start/stop; tablets/foldables layouts; wearables.
- iOS target and the shared-UI decision (Compose Multiplatform vs SwiftUI) — deferred until the
  Android app is releasable.

### Decisions taken with the owner (2026-09-21)

| Question | Decision |
|---|---|
| Code sharing | Android-first. KMP module structure from day one, only the Android target configured. |
| Backend | Local-first, no server. Schema is sync-ready (UUID keys, timestamps, soft delete, device id). |
| Rate denominator | Sessions detected automatically from gaps between encounters. |
| Region buckets | Geohash cells stored on write; names resolved lazily by the platform geocoder, cached per cell. |
| Counting | Strictly +1 per encounter. Several cats = several taps or several photos. |
| Rate unit | Auto-scale: cats/hour by default, cats/minute once the rate reaches 1 per minute. |
| Photos | Original goes to the phone gallery (opt-out); the app keeps a compressed copy + thumbnail. |
| Data safety | ZIP export/import in v1. |
| Bulk import | Multi-select from the gallery in v1; EXIF date and GPS become the encounter's. |
| Widget | Home-screen "+1" widget in v1. |
| Regions UX | Drill-down Country → City → Area; unresolved areas never show a raw geohash. |
| Gamification | Day streak + milestone toasts in v1. |
| Architecture | Layered modules `:domain` / `:data` / `:presentation` / `:ui` / `:app`; minimal MVI (`Store` with State/Intent/Effect). |
| Quality gates | detekt + formatting + compose-rules, Android Lint, Konsist architecture tests; all in `./gradlew check` and CI. |
| Coat (2026-09-22) | Optional cat coat from a fixed list of eleven, in v1: column in the first schema, picker after a tally or photo, editable in detail, statistics by coat. |
| Colour (2026-09-23) | Material You: the wallpaper's colours on Android 12+, in the app and the widget; the icon-teal palette below 12 and in previews. No in-app switch. |
| Map epic (2026-09-22) | Right after v1, on MapLibre + OpenStreetMap tiles: encounter markers coloured by coat, outing route as a polyline through encounter points first, real GPS track via an explicit "walk" later, personal heatmap by frequency with a coat filter, cats per km once distance exists. |

## 2. Users and core flows

Single user, on foot, phone in hand, often abroad, often without data.

**F1 Tally.** Counter screen → tap the big button, or one of the eleven coats in the grid below it,
which logs a cat of that coat in the same single tap. Counter increments instantly, haptic tick. An
"Undo" chip appears for `UNDO_VISIBLE`, and the grid rings the coat of the newest undoable cat. Undo
reverts the newest tap of the run: every tap and every Undo restarts the window, so a run of taps
can be undone one by one down to nothing. No debounce — rapid taps are several cats. Location is
attached in the background (§4.3).

**F2 Photo.** Counter screen → tap camera → system camera. On return: original saved to the gallery
(if enabled), compressed copy + thumbnail stored privately, encounter saved with EXIF location if
present, else the background location chain. No coat control follows a photo; its coat is set on
the detail screen (F6).

**F3 Import.** Counter screen → the gallery half of the Photo split button (or Settings → Import
photos) → gallery multi-select. Each photo becomes a PHOTO encounter dated by EXIF (§4.6). Progress
bar, then a summary with "Undo import".

**F4 Widget.** Home-screen widget shows today's count and a "+1" button. Tap logs a tally through
the same path as F1, without opening the app (§4.8).

**F5 Statistics.** Statistics tab: headline totals, streak, rate block, outings, regions entry
point. Regions drill down Country → City → Area → encounters in that area.

**F6 Encounters.** Chronological list grouped by outing; photos as a grid; tap for detail; delete
from detail (soft delete, undo snackbar); the detail screen shows the coat swatch and lets it be
changed or cleared.

**F7 Export / import backup.** Settings → Export creates a ZIP via the system file picker; Import
merges a ZIP back (§4.7).

Screens (phone only): `Counter`, `Encounters`, `EncounterDetail(id)`, `Statistics`,
`Regions(level, parentKey)`, `RegionEncounters(areaKey)`, `Settings`. Bottom navigation:
Counter · Encounters · Statistics. Settings from the top bar. Back from Encounters/Statistics
returns to Counter; back from Counter exits.

## 3. Domain model

### 3.1 Encounter (stored)

| Field | Type | Notes |
|---|---|---|
| `id` | String (UUIDv4) | PK. Generated on device. |
| `occurredAt` | Long (epoch ms, UTC) | Tally/camera: now. Gallery: EXIF `DateTimeOriginal` (+ `OffsetTimeOriginal`, else device zone), else file date, else now. |
| `tzOffsetMinutes` | Int | UTC offset at `occurredAt`. Keeps "today"/streaks stable when travelling. |
| `kind` | enum `TALLY` \| `PHOTO` | |
| `origin` | enum `APP` \| `WIDGET` \| `CAMERA` \| `GALLERY` | How it was logged. Drives import rules (§4.6) and debugging. |
| `coat` | enum `CatCoat`? | `GINGER`, `GINGER_WHITE`, `WHITE`, `TRICOLOR_MOSTLY_WHITE`, `TRICOLOR_LITTLE_WHITE`, `BROWN`, `BROWN_WHITE`, `GREY`, `GREY_WHITE`, `BLACK`, `BLACK_WHITE`. Null = not specified. The only field editable after creation. |
| `photoPath` | String? | Compressed copy, relative to app-private photos dir. Null for TALLY. |
| `thumbPath` | String? | Generated thumbnail. |
| `galleryUri` | String? | MediaStore URI of the original if it was saved to the gallery. Informational; may dangle if the user deletes it. |
| `sourceDigest` | String? | SHA-256 of the bytes the source hands over — the picker's redacted copy when location is not shared; duplicate imports are skipped on it. |
| `lat`, `lon` | Double? | WGS84. Both null when no location. |
| `accuracyMeters` | Float? | From the fix; null for EXIF. |
| `locationSource` | enum `EXIF` \| `CURRENT_FIX` \| `LAST_KNOWN` \| `BACKFILLED` \| `NONE` | Which rung of §4.3 produced the coordinates. |
| `locationFixedAt` | Long? | When the fix was actually taken. |
| `geohash` | String? | Precision 8 (~38 m × 19 m). Coarser buckets are prefixes. |
| `placeCellId` | String? | `geohash.take(PLACE_CELL_PRECISION)`; joins to `PlaceCell`. |
| `deviceId` | String | Installation id, generated once. |
| `createdAt`, `updatedAt` | Long | Row lifecycle. |
| `deletedAt` | Long? | Soft delete. All reads filter `deletedAt IS NULL`. |

### 3.2 PlaceCell (stored, reverse-geocode cache)

One row per geohash cell of precision `PLACE_CELL_PRECISION = 6` (~1.2 km × 0.6 km).

| Field | Type | Notes |
|---|---|---|
| `cellId` | String | PK, geohash p6. |
| `centerLat`, `centerLon` | Double | Cell centre; the coordinate sent to the geocoder. |
| `countryCode` | String? | ISO 3166-1 alpha-2. |
| `countryName`, `adminArea`, `locality`, `subLocality` | String? | From the geocoder `Address`. |
| `status` | enum `PENDING` \| `RESOLVED` \| `FAILED` \| `UNAVAILABLE` | `UNAVAILABLE` = device has no geocoder. |
| `attempts` | Int | `FAILED` after `MAX_GEOCODE_ATTEMPTS`. |
| `lastAttemptAt`, `resolvedAt` | Long? | |

### 3.3 Settings (DataStore Preferences, not Room)

`saveOriginalsToGallery: Boolean = true`, `lastSeenMilestone: Int = 0`, and per reported background job
(gallery import, backup) `acknowledgedRun: String?` — the last run whose outcome the user has dealt
with, so a finished run read back from WorkManager is not reported again.

### 3.4 Region hierarchy (derived)

| Level | Key | Label | Offline? |
|---|---|---|---|
| Country | `countryCode` | `countryName` | after resolution |
| City | `(countryCode, locality ?: adminArea)` | `locality ?: adminArea` | after resolution |
| Area | the coordinates' geohash at `AREA_PRECISION = 5` (~4.9 km), computed from `lat`/`lon` rather than the stored geohash | most frequent non-null `subLocality` among the area's resolved cells; else `"Area · <lat>, <lon>"` with the area centre rounded to 2 decimals | always |

Pseudo-nodes: **"Unresolved"** (country level) holds encounters whose cell is
`PENDING`/`FAILED`/`UNAVAILABLE`; **"No city"** (city level, under its country) holds encounters
whose resolved cell names neither a locality nor an admin area; **"No location"** holds encounters
without a location — coordinates missing or off the globe, or `locationSource = NONE`. Each
pseudo-node drills down like a real one (Unresolved and No city → their areas; No location → its
encounters). Sums across siblings always equal the parent.

An area belongs to the node it is listed under: its key is that parent (a city, No city or
Unresolved) plus the area's geohash, and it holds only that parent's encounters in the patch — so
the encounters an area lists are always exactly the ones its row counts.

### 3.5 Session (derived, never stored)

Sort non-deleted encounters by `occurredAt`. A gap greater than `SESSION_GAP` (30 min) starts a new
session. `count = n`, `start = first.occurredAt`, `end = last.occurredAt`, `duration = end − start`.
Duration is first-cat-to-last-cat, so rates are optimistic; manual outings (roadmap) fix that.
`SESSION_GAP` is a constant, not a setting, in v1.

## 4. Behaviour rules

### 4.1 Tally (F1, F4)

1. Insert `Encounter(kind = TALLY, origin = APP|WIDGET, locationSource = NONE)`; haptic; UI updates
   from the Room `Flow`.
2. Enqueue `AttachLocationWorker(encounterId)` (expedited WorkManager). Using a worker for both app
   and widget keeps the location update alive if the process dies.
3. "Undo" chip (app only) soft-deletes that encounter and cancels its worker.

### 4.2 Photo (F2)

1. `TakePicture` into a `FileProvider` URI in the cache dir.
2. Read EXIF from the original: GPS, `DateTimeOriginal`, `OffsetTimeOriginal`.
3. If `saveOriginalsToGallery`: insert the original into MediaStore under `Pictures/Cats Radar`
   (no permission needed at `minSdk 29`); store the URI. Otherwise the original is discarded
   after step 4.
4. Produce the app copy: longest side `PHOTO_MAX_SIDE` (2048 px), JPEG `PHOTO_QUALITY` (85), no
   EXIF; thumbnail `THUMB_SIZE` (256 px). Both on `Dispatchers.IO`.
5. Insert the encounter (`origin = CAMERA`, `occurredAt = now`). EXIF GPS → `locationSource = EXIF`;
   otherwise enqueue `AttachLocationWorker`.
6. Cancelled camera → nothing. Decode failure → no encounter, toast "Photo not saved".

### 4.3 Location precedence

```
EXIF GPS (photo only)                                                        → EXIF
  → FusedLocationProvider.getCurrentLocation(BALANCED_POWER_ACCURACY),
    timeout LOCATION_TIMEOUT (8 s)                                           → CURRENT_FIX
  → FusedLocationProvider.lastLocation if age ≤ LAST_KNOWN_MAX_AGE (6 h)      → LAST_KNOWN
  → nothing                                                                  → NONE
```

Whenever a `CURRENT_FIX` lands, every `NONE` encounter in the current session (gap rule of §3.5
against `now`) is updated with the same coordinates as `BACKFILLED`.

Permission (`ACCESS_COARSE_LOCATION` + `ACCESS_FINE_LOCATION`, foreground only) is requested on the
first tally, never at app start. Not granted → straight to `NONE`; the Counter screen shows a
dismissible one-line hint with a "grant" button. The widget never prompts.

### 4.4 Reverse geocoding

- `ReverseGeocoder` interface in `commonMain`; Android impl wraps `android.location.Geocoder`
  (`isPresent()` false → all cells `UNAVAILABLE`).
- `GeocodePendingCellsWorker`: `NetworkType.CONNECTED`, exponential backoff, `FAILED` after
  `MAX_GEOCODE_ATTEMPTS`; a pass reads pending cells `GEOCODE_BATCH` at a time, keyed by id. It
  runs as two passes:
  - a one-time pass over cells never looked up, unique with `REPLACE`, requested when such a cell
    appears and on app start if one is waiting;
  - a periodic pass, unique with `KEEP`, which retries cells whose lookup failed.

  A pass writes a result only if the cell is unchanged since it read it.
- Statistics read whatever is resolved; the only "loading" state is the Unresolved node.

### 4.5 Delete and purge

Soft delete sets `deletedAt`; undo clears it. `PurgeDeletedWorker` (periodic) removes photo files
and hard-deletes rows with `deletedAt` older than `PURGE_AFTER` (30 days). Gallery originals are
never touched by the app.

### 4.6 Gallery import (F3)

1. `PickMultipleVisualMedia(maxItems = IMPORT_BATCH_MAX)`; single pick is the same path with one item.
2. Per photo: compute `sourceDigest`; skip if an encounter with that digest exists (counted as
   "skipped" in the summary). Read EXIF; `occurredAt` as in §3.1.
3. Location: EXIF GPS → `EXIF`. No EXIF GPS and `now − occurredAt ≤ RECENT_PHOTO_WINDOW` (1 h) →
   `AttachLocationWorker` (it was probably just taken with the system camera). Older → `NONE`.
   Historical photos never receive today's location.
4. Compressed copy + thumbnail as §4.2 step 4; `origin = GALLERY`; `galleryUri = null` (already in
   the gallery). Never copies the original to MediaStore.
5. Runs in `ImportPhotosWorker` (expedited, progress notification). Summary: added / skipped /
   failed, with "Undo import" (soft-deletes the ids created by this run).

### 4.7 Backup export / import (F7)

- **Export**: `CreateDocument("application/zip")`. ZIP layout: `manifest.json` (`formatVersion`,
  `exportedAt`, `deviceId`, `appVersion`), `encounters.json` (non-deleted rows, all fields),
  `placecells.json`, `photos/<id>.jpg` (compressed copies; thumbnails are regenerable). Streamed by
  `ExportWorker`, progress notification.
- **Import**: `OpenDocument`. Validate `manifest.formatVersion ≤ current`. Merge by `id`: unknown →
  insert; known → keep the row with the newer `updatedAt`; a soft-deleted local row wins over an
  imported live one only if its `deletedAt` is newer. Photos copied when missing; thumbnails
  regenerated. Place cells merged, `RESOLVED` wins. The merge reads and writes in one database
  transaction, so a failure part-way writes no rows. `ImportBackupWorker`, progress notification,
  summary at the end.
- Round-trip test: export → wipe → import → identical statistics.

### 4.8 Widget (F4)

Glance `AppWidget` showing today's count (local date) and a "+1" button. The action inserts the
encounter (§4.1 step 1) and enqueues the worker (step 2) inside the Glance action callback, then
`updateAll()`. No undo on the widget; a mis-tap is undone in the app. Widget count updates
whenever the encounter table changes (a `Flow` collector in the app process triggers
`updateAll()`; a periodic Glance refresh covers the process-dead case).

## 5. Statistics — exact definitions

`E` = non-deleted encounters; `S` = sessions (§3.5); local date = `occurredAt + tzOffsetMinutes`
truncated to a day; `today` = the device's current local date.

| Metric | Definition |
|---|---|
| Total | `|E|` |
| Today / 7 days / 30 days | count by local date relative to `today` |
| With photo | `|{e : kind = PHOTO}|` and share of total |
| By coat | count per `CatCoat` value plus one "Not specified" row for `coat = null`, sorted by count desc; rows with zero are hidden |
| Current streak | consecutive local dates with ≥ 1 encounter ending `today` or `today − 1`; 0 otherwise |
| Longest streak | max run of consecutive local dates with ≥ 1 encounter |
| Milestones | `MILESTONES = [1, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000]`. When `Total` crosses a value greater than `lastSeenMilestone`, show a toast once and persist it. Statistics shows the next milestone and the distance to it. |
| Rate-eligible session | `n ≥ 2` and `duration ≥ MIN_RATE_DURATION` (5 min) |
| Session rate | `n / duration` for an eligible session |
| Overall rate | `Σ n_i / Σ duration_i` over eligible sessions; "—" when none |
| Rate display | per hour by default; per minute when the value is `≥ 1 / min`. Session detail shows both. |
| Best session | highest session rate among eligible sessions, with `n`, duration, date |
| Outings | `|S|`; total active time `Σ duration_i` over all sessions |
| Current outing | if `now − last.occurredAt ≤ SESSION_GAP`: the open session's `n`, elapsed time since its first cat, live rate once eligible. Shown on the Counter screen. |
| Regions | counts per node of §3.4, children sorted by count desc, pseudo-nodes last |

All of it is computed by a pure `StatsCalculator(encounters, placeCells, now, settings)` in
`commonMain` from the full list — no SQL aggregates in v1. Revisit if `|E|` grows past what a phone
reads in a few ms (tens of thousands).

## 6. Architecture

Binding detail lives in `docs/rules/` — [module-structure.md](../../rules/module-structure.md),
[mvi-architecture.md](../../rules/mvi-architecture.md), [compose-patterns.md](../../rules/compose-patterns.md),
[date-time.md](../../rules/date-time.md), [static-analysis.md](../../rules/static-analysis.md). This section
maps the spec onto those modules.

### 6.1 Modules and what goes where

| Module | Kind | Holds |
|---|---|---|
| `:domain` | KMP | `Encounter`, `PlaceCell`, `Session`, `RegionNode`, `Stats`, `Tuning`; `Geohash`, `SessionSplitter`, `StatsCalculator`, `LocationPolicy`, `ImportRules`, backup merge rules; repository interfaces (`EncounterRepository`, `PlaceCellRepository`, `SettingsRepository`, `TransactionRunner`); platform interfaces (`LocationProvider`, `PhotoStorage`, `GallerySaver`, `ExifReader`, `ImageResizer`, `Digest`, `ReverseGeocoder`, `IdGenerator`, `DeviceIdProvider`, `Haptics`); use cases (`LogTally`, `LogPhoto`, `ImportPhotos`, `AttachLocation`, `ResolvePendingPlaces`, `ObserveStats`, `ObserveEncounters`, `ObserveRegion`, `DeleteEncounter`, `UndoDelete`, `ExportBackup`, `ImportBackup`, `PurgeDeleted`). `kotlin.time.Clock` injected. |
| `:data` | KMP | Room `CatsDatabase`, `EncounterDao`, `PlaceCellDao` (`BundledSQLiteDriver`, `RoomDatabaseConstructor` expect/actual, KSP); DataStore Preferences; repository implementations; entity ↔ domain mappers; backup ZIP (de)serialisation with `kotlinx.serialization`. `androidMain`: FusedLocationProvider, ExifInterface, `Geocoder`, MediaStore saver, SHA-256, bitmap resize. |
| `:presentation` | KMP | `Store` base; per screen `State`/`Intent`/`Effect`/`Store` + `*StateMapper` for `Counter`, `Encounters`, `EncounterDetail`, `Statistics`, `Regions`, `RegionEncounters`, `Settings`; `DateTimeFormatter` interface. |
| `:ui` | Android | `CatsRadarTheme`, `@ThemePreviews`, components, one file per screen, previews. Compose Multiplatform-ready: no Android imports beyond Compose. |
| `:app` | Android app | Navigation 3 host, Koin modules, workers (`AttachLocationWorker`, `GeocodePendingCellsWorker`, `PurgeDeletedWorker`, `ImportPhotosWorker`, `ExportWorker`, `ImportBackupWorker`), Glance widget, `FileProvider`, activity result contracts, string resources (EN, RU). |
| `:build-logic` | Gradle | Convention plugins: `catsradar.kmp.library`, `catsradar.android.library`, `catsradar.android.application`, `catsradar.compose`, `catsradar.detekt`. |

### 6.2 Navigation (`:app`)

`NavDisplay` over `rememberNavBackStack(Counter)`; `@Serializable NavKey`s `Counter`, `Encounters`,
`EncounterDetail(id)`, `Statistics`, `Regions(level, parentKey)`, `RegionEncounters(areaKey)`,
`Settings`; `entryProvider` DSL; `rememberViewModelStoreNavEntryDecorator` +
`rememberSavedStateNavEntryDecorator`. Bottom bar keeps `Counter` as the root: selecting another tab
makes the stack `[Counter, Tab]`; back pops to Counter. No `Scene` strategies in v1.

### 6.3 Dependency injection

Koin. `:app` owns every module definition: `domainModule` (use cases), `dataModule` (Room, DataStore,
repositories, platform implementations), `presentationModule` (Stores, mappers, `DateTimeFormatter`),
`workerModule` (`KoinWorkerFactory`). Other modules expose constructors only.

### 6.4 Concurrency

Coroutines throughout; Room `Flow` drives every list and counter; anything that must outlive the
screen or the process (location attach, geocoding, import, export, purge) is a WorkManager worker in
`:app` calling a `:domain` use case. File I/O on `Dispatchers.IO`. No `runBlocking` outside tests.

### 6.5 Libraries

Compose BOM + Material 3, Navigation 3, `lifecycle-viewmodel` (KMP), Room (KMP), DataStore (KMP),
`kotlinx-datetime`, `kotlinx-serialization`, `kotlinx-collections-immutable`, Koin, Coil 3,
`play-services-location` + `kotlinx-coroutines-play-services`, `androidx.exifinterface`,
`androidx.glance`, `androidx.work`, `androidx.activity` result contracts. Versions only in
`gradle/libs.versions.toml`.

### 6.6 Error handling

| Failure | Behaviour |
|---|---|
| Location timeout / provider error | fall through §4.3; a tally never shows an error |
| Camera returns no file / corrupt JPEG | no encounter; toast |
| MediaStore insert fails | encounter still saved; `galleryUri = null`; one-line toast |
| Thumbnail decode fails | encounter saved, `thumbPath = null`, placeholder in UI |
| Geocoder throws / empty | `attempts++`, backoff, `FAILED` after budget; "Unresolved" node |
| Import photo unreadable | counted as failed in the summary; the batch continues |
| Backup ZIP invalid / newer format | import refused with a message; nothing written |
| Disk full | photo/import/export step fails with a toast; tally path has no disk-heavy step |
| DB migration | schemas exported to `data/schemas`; auto-migrations where possible; every bump adds a migration test |

## 7. Testing

- **commonTest** (pure Kotlin): `Geohash` against published vectors (`57.64911, 10.40744 →
  u4pruydqqvj`), prefixes; `SessionSplitter` boundaries (gap, gap + 1 ms, empty, single);
  `StatsCalculator` every row of §5 — bucket sums equal parents, pseudo-nodes, rate eligibility
  edges, auto-scale threshold, streak across a timezone change and across `today − 1`, milestone
  crossing exactly once; `LocationPolicy` each rung with fakes incl. `LAST_KNOWN_MAX_AGE` and
  backfill; `ImportRules` (`RECENT_PHOTO_WINDOW`, digest dedup, EXIF time with/without offset);
  backup merge rules (newer `updatedAt` wins, delete-vs-live).
- **androidHostTest** (Robolectric, in-memory Room): DAO filters soft-deleted rows; digest lookup;
  place-cell upsert; migration tests from exported schemas.
- **Store tests** (`:presentation` commonTest, `runTest` + turbine + fakes): tally increments
  immediately, undo chip, photo with and without EXIF, import summary, region drill-down; every
  `*StateMapper` asserted on the whole `State`.
- **Architecture tests** (Konsist, `:app`): layer boundaries and naming from
  `docs/rules/module-structure.md`.
- **Static analysis** in `./gradlew check`: detekt (+ formatting, compose-rules), Android Lint.
- **Instrumented smoke** (one test): tap counter → count shows 1.
- Backup round-trip test: export → wipe → import → identical `Stats`.
- Acceptance criteria per slice are frozen before code (acceptance plugin) and audited
  independently before the PR.

## 8. Repository conventions

- Gradle Kotlin DSL; `gradle/libs.versions.toml` is the single source of library versions — this
  spec names libraries, not versions. `minSdk 29`, `targetSdk` = latest stable. Every module applies
  a `build-logic` convention plugin.
- Package root `dev.catsradar`; `applicationId = dev.catsradar` (placeholder until the owner confirms).
- Strings in EN and RU.
- Branches `feature/ | fix/ | tech/`, one PR per task, merge commits.
- Docs: `docs/superpowers/specs/` (kept), `docs/superpowers/plans/` (archived when shipped),
  `docs/research/` (kept), repo `CLAUDE.md` for conventions.
- Every constant named here — `SESSION_GAP`, `MIN_RATE_DURATION`, `LOCATION_TIMEOUT`,
  `LAST_KNOWN_MAX_AGE`, `RECENT_PHOTO_WINDOW`, `UNDO_VISIBLE`, `PLACE_CELL_PRECISION`,
  `AREA_PRECISION`, `PHOTO_MAX_SIDE`, `PHOTO_QUALITY`, `THUMB_SIZE`, `GEOCODE_BATCH`,
  `MAX_GEOCODE_ATTEMPTS`, `PURGE_AFTER`, `IMPORT_BATCH_MAX`, `MILESTONES` — lives in one `Tuning`
  object in `commonMain`. Values in this spec are initial defaults; code is the source of truth.

## 9. Roadmap after v1

1. iOS target: add `iosArm64`/`iosSimulatorArm64` to `shared`, implement the platform interfaces,
   then choose Compose Multiplatform (nav3 multiplatform port exists) vs SwiftUI over the shared
   ViewModels.
2. **Map epic** (first after v1), on MapLibre with OpenStreetMap tiles so no API key or billing is
   needed and the same map component can later serve iOS through Compose Multiplatform:
   1. map screen with encounter markers coloured by coat, tap → detail;
   2. outing route as a polyline through that outing's encounter points, selectable from the
      Encounters list;
   3. explicit "walk" mode — start/stop button, foreground service recording a GPS track, outing
      bound to the walk, distance → cats per km alongside cats per hour;
   4. personal heatmap layer by encounter frequency with a coat filter.
   The shared/social heatmap stays out of scope.
3. Sync and social layer — needs the sync layer the schema anticipates; backup ZIP is the seed.
4. Manual outing start/stop overriding auto-sessions (subsumed by the walk mode above); quantity per
   encounter; captions; editing time/location; 30-day cats-per-day chart.
5. Offline GeoNames fallback if geocoder coverage proves poor.

## 10. Open items

- `applicationId` / package name placeholder `dev.catsradar` until confirmed.
- ~~**EXIF GPS from gallery photos is redacted under scoped storage.**~~ Resolved with no runtime
  permission: the Photo Picker hands GPS over when the launch intent carries
  `MediaStore.EXTRA_REQUEST_LOCATION_METADATA_ACCESS` and the user agrees in the picker.
  Declined, or on a picker without that extra, import falls back to `NONE` for location while dates
  still come from EXIF — see `docs/features/import.md`.
