# Cats Radar — design spec

Date: 2026-09-21. Status: draft for owner review.
Research behind this spec: [docs/research/2026-09-21-competitor-scan.md](../../research/2026-09-21-competitor-scan.md).

## 1. Summary

Cats Radar is a personal cat-encounter counter. One tap logs a cat; one more tap logs a cat with a
photo. Every encounter carries location metadata when it can be obtained. The app turns the log into
statistics: total cats, cats per period, cats per country / city / area, and a "cats per minute"
rate derived from automatically detected outings.

### Goals (v1)

1. Logging a cat takes one tap and never waits for GPS, camera, or network.
2. Every encounter gets the best location available under a fixed precedence rule.
3. Statistics work fully offline; place names fill in when the network allows.
4. Data model is ready for a later sync/social layer without a migration of user data.
5. Android ships first on Compose + Navigation 3 + Room + coroutines; the domain and data layers
   live in a Kotlin Multiplatform module so iOS can be added without rewriting them.

### Non-goals (v1)

- Cat heatmap / map view, social features, accounts, cloud sync (explicitly out of scope by owner).
- Breed identification, per-cat identity ("is this the same cat?"), notes/captions.
- Export/import, widgets, wearables, tablets/foldables layouts.
- iOS target and any shared-UI decision (Compose Multiplatform vs SwiftUI) — deferred until the
  Android app is releasable.

### Decisions taken with the owner (2026-09-21)

| Question | Decision |
|---|---|
| Code sharing | Android-first. KMP module structure from day one, only the Android target configured. |
| Backend | Local-first, no server. Schema is sync-ready (UUID keys, timestamps, soft delete, device id). |
| "Cats per minute" denominator | Sessions detected automatically from gaps between encounters. |
| Region buckets | Geohash cells stored on write; country/city names resolved lazily by the platform geocoder and cached per cell. |

## 2. Users and core flows

Single user, on foot, phone in hand, often abroad, often without data. Three flows matter:

**F1 Tally.** Home screen → tap the big counter. Counter increments instantly. Location is
attached in the background (§4.3). Nothing blocks.

**F2 Photo.** Home screen → tap camera → system camera (or gallery picker via long-press). On
return the photo is stored privately, a thumbnail is generated, an encounter is saved with the
photo, EXIF location if present, else the same background location chain as F1.

**F3 Look at stats.** Statistics tab: headline totals, rate block, period counts, region
breakdown. Encounters tab: chronological list grouped by outing, photos as a grid; tap for detail;
delete from the detail screen (soft delete, undo snackbar).

Screens (phone only): `Counter`, `Encounters`, `EncounterDetail`, `Statistics`, `Settings`.
Bottom navigation: Counter · Encounters · Statistics. Settings is reached from the top bar.

## 3. Domain model

### 3.1 Encounter (stored)

| Field | Type | Notes |
|---|---|---|
| `id` | String (UUIDv4) | PK. Generated on device. |
| `occurredAt` | Long (epoch ms, UTC) | Camera/tally: capture time. Gallery pick: EXIF `DateTimeOriginal` when present, else pick time. |
| `tzOffsetMinutes` | Int | Device UTC offset at `occurredAt`. Makes "today"/"this week" stable when travelling. |
| `kind` | enum `TALLY` \| `PHOTO` | |
| `photoPath` | String? | Relative to app-private photos dir. Null for TALLY. |
| `thumbPath` | String? | Relative path to generated thumbnail. |
| `lat`, `lon` | Double? | WGS84. Both null when no location. |
| `accuracyMeters` | Float? | From the fix; null for EXIF. |
| `locationSource` | enum `EXIF` \| `CURRENT_FIX` \| `LAST_KNOWN` \| `NONE` | Which rung of the precedence chain produced the coordinates. |
| `locationFixedAt` | Long? | When the fix was actually taken. For `LAST_KNOWN` this can be much earlier than `occurredAt`. |
| `geohash` | String? | Precision 8 (~38 m × 19 m). Coarser buckets are prefixes of this. |
| `placeCellId` | String? | `geohash.take(PLACE_CELL_PRECISION)`; joins to `PlaceCell`. |
| `deviceId` | String | Installation id, generated once. Sync-readiness. |
| `createdAt`, `updatedAt` | Long | Row lifecycle. |
| `deletedAt` | Long? | Soft delete. All queries filter `deletedAt IS NULL`. |

### 3.2 PlaceCell (stored, reverse-geocode cache)

One row per geohash cell of precision `PLACE_CELL_PRECISION = 6` (~1.2 km × 0.6 km). Resolved once,
shared by every encounter in the cell.

| Field | Type | Notes |
|---|---|---|
| `cellId` | String | PK, geohash p6. |
| `centerLat`, `centerLon` | Double | Cell centre; the coordinate sent to the geocoder. |
| `countryCode` | String? | ISO 3166-1 alpha-2. |
| `countryName`, `adminArea`, `locality`, `subLocality` | String? | Straight from the geocoder `Address`. |
| `status` | enum `PENDING` \| `RESOLVED` \| `FAILED` \| `UNAVAILABLE` | `UNAVAILABLE` = device has no geocoder; skip forever. |
| `attempts` | Int | Retry budget; `FAILED` after `MAX_GEOCODE_ATTEMPTS`. |
| `lastAttemptAt`, `resolvedAt` | Long? | |

### 3.3 Region hierarchy (derived)

| Level | Key | Display label | Offline? |
|---|---|---|---|
| Country | `PlaceCell.countryCode` | `countryName` | After resolution |
| City | `(countryCode, locality ?: adminArea)` | `locality ?: adminArea` | After resolution |
| Area | `geohash.take(AREA_PRECISION = 5)` (~4.9 km square) | Most frequent non-null `subLocality` among the area's resolved cells, else the geohash string | Always |

Encounters without location fall into a single **"No location"** bucket at every level. Encounters
whose cell is `PENDING`/`FAILED`/`UNAVAILABLE` fall into **"Unresolved"** at Country and City level
and still count normally at Area level. Sums across buckets always equal the total.

### 3.4 Session (derived, never stored)

Sort non-deleted encounters by `occurredAt`. Start a new session whenever the gap to the previous
encounter exceeds `SESSION_GAP` (initial default 30 min). A session has `count = n`,
`start = first.occurredAt`, `end = last.occurredAt`, `duration = end − start`.

Sessions are recomputed from the encounter list on read (thousands of rows at most — cheap).
`SESSION_GAP` is a constant in code, not a user setting, in v1.

## 4. Behaviour rules

### 4.1 Tally (F1)

1. Insert `Encounter(kind = TALLY, locationSource = NONE)` immediately; UI counter updates from the
   Room `Flow`.
2. Launch location resolution (§4.3) in an application-scoped coroutine. On result, `UPDATE` the
   same row (`lat/lon/accuracy/source/fixedAt/geohash/placeCellId`, `updatedAt`).
3. Ensure a `PlaceCell(PENDING)` exists for `placeCellId`; enqueue the geocode worker (§4.4).

### 4.2 Photo (F2)

1. Tap → `TakePicture` into a `FileProvider` URI in the cache dir. Long-press → `PickVisualMedia`.
2. On success: move/copy the JPEG to `files/photos/<id>.jpg`; decode a `THUMB_SIZE` (256 px
   longest side) thumbnail to `files/thumbs/<id>.jpg`. Both on `Dispatchers.IO`.
3. Read EXIF. If GPS present → `locationSource = EXIF`, `locationFixedAt = EXIF datetime ?: occurredAt`.
   For gallery picks, `occurredAt = EXIF DateTimeOriginal ?: now`. For camera captures, `occurredAt = now`.
4. Insert the encounter with whatever is known. If no EXIF GPS → run §4.3 exactly as for a tally.
5. Cancelled camera/picker → no encounter, no error shown.

Photos stay in app-private storage (they carry GPS). Nothing is written to the public gallery.

### 4.3 Location precedence (shared by 4.1 and 4.2)

```
EXIF GPS (photo only)
  → current fix: FusedLocationProvider.getCurrentLocation(BALANCED_POWER_ACCURACY),
    timeout LOCATION_TIMEOUT (initial default 8 s)               → CURRENT_FIX
  → FusedLocationProvider.lastLocation (any age)                  → LAST_KNOWN
  → nothing                                                       → NONE
```

Permission not granted → skip straight to `NONE`; the counter screen shows a one-line dismissible
hint with a button to grant. Permission is requested on first tally, not at app start.
Only foreground (`ACCESS_COARSE_LOCATION` + `ACCESS_FINE_LOCATION`) — no background location.

### 4.4 Reverse geocoding (place names)

- `ReverseGeocoder` interface in `commonMain`; Android impl wraps `android.location.Geocoder`
  (`isPresent()` false → mark cells `UNAVAILABLE`).
- A WorkManager one-time unique worker (`KEEP` policy) with `NetworkType.CONNECTED`. It loads up to
  `GEOCODE_BATCH` pending cells, geocodes the cell centre, writes `RESOLVED` or increments
  `attempts` (→ `FAILED` at `MAX_GEOCODE_ATTEMPTS`, exponential backoff between runs).
- Triggered when a `PlaceCell` is created and on app start if pending cells exist.
- Statistics read whatever is resolved; there is no "resolving" spinner beyond the "Unresolved"
  bucket label.

### 4.5 Delete

Soft delete: set `deletedAt`. Photo files are removed by a periodic cleanup for rows with
`deletedAt` older than `PURGE_AFTER` (initial default 30 days), which then hard-deletes the rows.
Undo snackbar clears `deletedAt`.

## 5. Statistics — exact definitions

Let `E` be non-deleted encounters, `S` the sessions derived from `E` (§3.4). Local date of an
encounter = `occurredAt + tzOffsetMinutes` truncated to a day.

| Metric | Definition |
|---|---|
| Total cats | `|E|` |
| Cats today / 7 days / 30 days | count by local date of the encounter, relative to the device's current local date |
| With photo | `|{e : kind = PHOTO}|` and its share of total |
| Rate-eligible session | `n ≥ 2` and `duration ≥ MIN_RATE_DURATION` (initial default 5 min). Filters out "two taps five seconds apart = 24 cats/min". |
| Session rate | `n / duration_minutes` for a rate-eligible session |
| Overall cats/min | `Σ n_i / Σ duration_i` over rate-eligible sessions. Undefined (shown as "—") when there are none. |
| Best session | Highest session rate among rate-eligible sessions; shown with `n`, duration, and date |
| Outings | `|S|`, plus total active time `Σ duration_i` over all sessions |
| Current outing | If `now − last.occurredAt ≤ SESSION_GAP`: the open session's `n`, elapsed time, and live rate (rate shown once eligible). Displayed on the Counter screen. |
| By region | Counts per Country / City / Area bucket (§3.3), sorted by count desc; include "Unresolved" and "No location" rows when non-empty |

All statistics are computed by a pure `StatsCalculator(encounters, placeCells, now)` in
`commonMain` from the full list — no SQL aggregates in v1. Revisit if `|E|` grows past what a
phone reads in a few ms (tens of thousands).

## 6. Architecture

### 6.1 Modules

```
Cats Radar/
  shared/          Kotlin Multiplatform library (targets: android now; ios* later)
    commonMain/    domain, data (Room), platform interfaces, StatsCalculator, Geohash
    commonTest/    pure-Kotlin tests (kotlin.test)
    androidMain/   Room builder actual, Android platform impls (location, EXIF, geocoder, photo store)
    androidUnitTest/ Room DAO tests on Robolectric
  androidApp/      Compose UI, Navigation 3, ViewModels, Koin wiring, WorkManager worker, FileProvider
```

Rule: `commonMain` imports nothing from `android.*`. Platform capabilities are Kotlin interfaces in
`commonMain` with Android implementations in `androidMain`, so `commonTest` runs with fakes and an
iOS target later only adds implementations.

### 6.2 Layers inside `shared`

- **domain** — `Encounter`, `PlaceCell`, `Session`, `RegionStats`, `Stats`; pure functions
  `Geohash.encode/decode/prefix`, `SessionSplitter`, `StatsCalculator`, `LocationPolicy`;
  repository interfaces `EncounterRepository`, `PlaceCellRepository`; use cases `LogTally`,
  `LogPhoto`, `ResolvePendingPlaces`, `ObserveStats`, `ObserveEncounters`, `DeleteEncounter`.
- **data** — Room `CatsDatabase`, `EncounterDao`, `PlaceCellDao`, entity ↔ domain mappers,
  repository implementations. Room KMP with `BundledSQLiteDriver`, `RoomDatabaseConstructor`
  expect/actual, KSP 2.
- **platform** (interfaces) — `LocationProvider`, `PhotoStore`, `ExifReader`, `ReverseGeocoder`,
  `Clock` (kotlinx-datetime), `IdGenerator`, `DeviceIdProvider`.

### 6.3 `androidApp`

- **Navigation 3**: `NavDisplay` over `rememberNavBackStack(Counter)`; keys are `@Serializable
  NavKey` objects/data classes (`Counter`, `Encounters`, `EncounterDetail(id)`, `Statistics`,
  `Settings`). `entryProvider` DSL, `rememberViewModelStoreNavEntryDecorator` for per-entry
  `ViewModel` scope, `rememberSavedStateNavEntryDecorator`. Bottom bar swaps the root key
  (single back stack, top-level keys not stacked). No `Scene` strategies in v1.
- **ViewModels**: one per screen, expose a `StateFlow<UiState>`, take use cases via Koin
  (`koinViewModel()`). Kept in `androidApp` in v1; they move to `shared` if the iOS path becomes
  Compose Multiplatform.
- **DI**: Koin. `sharedModule` (repos, use cases, Room) in `shared/androidMain`;
  `appModule` (ViewModels, platform impls that need `Context`) in `androidApp`.
- **Work**: `GeocodePendingCellsWorker` (§4.4), `PurgeDeletedWorker` (§4.5), both via
  `WorkManager` with Koin-provided dependencies (`KoinWorkerFactory` or manual `WorkerFactory`).
- **Images**: Coil 3 for thumbnails/full photos (KMP-ready).
- **Location**: `play-services-location` `FusedLocationProviderClient`, awaited with
  `kotlinx-coroutines-play-services`.
- **EXIF**: `androidx.exifinterface`.

### 6.4 Concurrency

Coroutines throughout. Room `Flow` drives every list and counter. Location and geocoding run in
an application-scoped `CoroutineScope(SupervisorJob() + Dispatchers.Default)` so a tally's location
update survives leaving the screen. File I/O on `Dispatchers.IO`. No `runBlocking` outside tests.

### 6.5 Error handling

| Failure | Behaviour |
|---|---|
| Location timeout / provider error | Fall through the chain (§4.3); never surface an error for a tally. |
| Camera returns without a file / corrupt JPEG | No encounter; toast "Photo not saved". |
| Thumbnail decode fails | Encounter saved with `thumbPath = null`; UI shows placeholder. |
| Geocoder throws / returns empty | `attempts++`, retry with backoff; `FAILED` after budget; stats show "Unresolved". |
| Disk full on photo copy | No encounter; toast. Tally path has no disk-heavy step. |
| DB migration | Room schema exported to `shared/schemas`; auto-migrations where possible; every schema bump adds a migration test. |

## 7. Testing

- **commonTest** (pure Kotlin, fast): `Geohash` against published vectors (e.g. `57.64911,
  10.40744 → u4pruydqqvj`), prefix/precision behaviour; `SessionSplitter` gap boundaries (equal to
  gap, gap+1 ms, empty list, single encounter); `StatsCalculator` every metric in §5 including
  bucket sums equal total, "Unresolved"/"No location" rows, rate-eligibility edges, local-date
  boundaries across a timezone change; `LocationPolicy` precedence with fakes for each rung.
- **androidUnitTest** (Robolectric, in-memory Room): DAO queries filter soft-deleted rows; place
  cell upsert; migration tests from exported schemas.
- **androidApp unit tests**: ViewModels with fake use cases — tally increments immediately, photo
  flow with/without EXIF, delete + undo.
- **Instrumented smoke** (one test): tap counter → count shows 1.
- Acceptance criteria for each slice are frozen before code via the acceptance plugin and audited
  independently before the PR (owner's working agreement).

## 8. Repository conventions

- Gradle Kotlin DSL, version catalog (`gradle/libs.versions.toml`) is the single source of library
  versions — this spec names libraries, not versions.
- Package root `dev.catsradar`; `applicationId = dev.catsradar` (changeable before first release).
- Branches `feature/ | fix/ | tech/`, one PR per task, merge commits.
- Docs: `docs/superpowers/specs/` (design, kept), `docs/superpowers/plans/` (archived when shipped),
  `docs/research/` (kept), repo `CLAUDE.md` for conventions.
- Constants named in this spec (`SESSION_GAP`, `MIN_RATE_DURATION`, `LOCATION_TIMEOUT`,
  `PLACE_CELL_PRECISION`, `AREA_PRECISION`, `THUMB_SIZE`, `GEOCODE_BATCH`,
  `MAX_GEOCODE_ATTEMPTS`, `PURGE_AFTER`) live in one `Tuning` object in `commonMain`; the values
  above are initial defaults, the code is the source of truth.

## 9. Roadmap after v1

1. iOS target: add `iosArm64`/`iosSimulatorArm64` to `shared`, implement the platform interfaces,
   then decide Compose Multiplatform (nav3 multiplatform port exists) vs SwiftUI.
2. Heatmap and social layer — requires the sync layer the schema already anticipates.
3. Manual outing start/stop as an override of auto-sessions; captions; export; 30-day cats-per-day chart.
4. Offline GeoNames fallback if geocoder coverage proves poor in practice.

## 10. Open items

- `applicationId`/package name is a placeholder until the owner confirms.
- No remote repository yet; GitHub remote to be created by the owner.
