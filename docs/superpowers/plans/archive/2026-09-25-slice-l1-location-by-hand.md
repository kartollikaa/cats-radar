# Slice L1 — A location set by hand Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A cat with no location can be given a point by `SetLocationByHand`, stored as `MANUAL`; no location write ever replaces a location a cat already has; backups say format 5.

**Architecture:** `EncounterDao.attachLocation` gains `AND locationSource = 'NONE'` and returns the rows it changed; `EncounterRepository.attachLocation` returns whether it wrote. `AttachLocation` backfills only after its own write landed. `SetLocationByHand` stamps `MANUAL` through the same write. `closestLocatedInTime` is a pure `:domain` function the picker (L2) opens on.

**Tech Stack:** Kotlin Multiplatform, Room (Robolectric `androidHostTest`), kotlinx-serialization backups.

**Spec:** `docs/superpowers/specs/2026-09-25-location-by-hand-design.md` § The data. Map: L1.

## Global Constraints

- `LocationSource.MANUAL` sits before `NONE`; stored and backed up by name.
- A `MANUAL` stamp: `accuracyMeters = null`, `locationFixedAt` = the save time, `updatedAt` = the save time.
- EN "Set by hand", RU «Указано вручную».
- Comments per `docs/rules/code-commenting-standards.md`; `./gradlew check` green.

---

### Task 1: The guarded write

**Files:**
- Modify: `data/src/commonMain/kotlin/dev/catsradar/data/db/EncounterDao.kt` (`attachLocation`: guard, `: Int`)
- Modify: `domain/src/commonMain/kotlin/dev/catsradar/domain/repository/EncounterRepository.kt` (`attachLocation(...): Boolean`, KDoc)
- Modify: `data/src/commonMain/kotlin/dev/catsradar/data/repository/EncounterRepositoryImpl.kt` (`> 0`)
- Modify: `domain/src/commonMain/kotlin/dev/catsradar/domain/usecase/AttachLocation.kt` (backfill only when written)
- Modify fakes: `domain/…/testing/FakeEncounterRepository.kt` (guard `NONE`, return), `data/src/commonTest/…/FakeEncounterDao.kt`
  (`: Int`, returns 1), `presentation/src/commonTest/…/counter/CounterStoreTestDoubles.kt`,
  `app/src/test/…/worker/AttachLocationWorkerTest.kt`, `app/src/test/…/widget/WidgetRefreshTest.kt`,
  `app/src/test/…/notification/WalkTestDoubles.kt`
- Test: `data/src/androidHostTest/…/db/EncounterDaoAttachLocationTest.kt`, `domain/src/commonTest/…/usecase/AttachLocationTest.kt`

**Interfaces:**
- Produces: `suspend fun EncounterRepository.attachLocation(id: String, stamp: LocationStamp): Boolean` — true only when
  the row was live and still `NONE`.

- [ ] **Step 1: Failing DAO test** — a located row (`locationSource = EXIF`, coordinates set) given a `CURRENT_FIX`
  write returns 0 and reads back unchanged; the existing live-row test asserts 1, the deleted-row test 0.
- [ ] **Step 2: Failing domain test** — in `AttachLocationTest`, a provider whose `getCurrentFix` first writes a
  `MANUAL` stamp to the target through the repository, then returns `Fix`: the target keeps the manual point, and
  a second `NONE` cat of the same outing stays `NONE` (no backfill).
- [ ] **Step 3: Run both, see them fail** — `./gradlew :data:testAndroidHostTest --tests '*EncounterDaoAttachLocationTest*' :domain:allTests --rerun`
  (expect compile failure on `Int`/`Boolean`, then the assertions).
- [ ] **Step 4: Implement**

```kotlin
// EncounterDao
// Only a live row still without a location: a fix landing after an undo must not resurrect it, nor one landing
// after the cat got its location another way replace that location.
@Query("""UPDATE encounters SET … WHERE id = :id AND deletedAt IS NULL AND locationSource = 'NONE'""")
suspend fun attachLocation(…): Int

// EncounterRepository
/** Writes only a live cat that has no location yet; true when it did. */
suspend fun attachLocation(id: String, stamp: LocationStamp): Boolean

// AttachLocation
val attached = encounterRepository.attachLocation(encounterId, stamp)
if (attached && result.source == LocationSource.CURRENT_FIX) backfillOuting(target.occurredAt, encounterId, stamp)
```

  Fakes mirror the guard (`encounter.deletedAt == null && encounter.locationSource == LocationSource.NONE`) and
  return whether they changed a row; the throw-only doubles gain `: Boolean`.
- [ ] **Step 5: Run the tests above plus `:presentation:allTests :app:testDebugUnitTest --tests '*AttachLocation*'`, all green**
- [ ] **Step 6: Commit** — `fix: a location write never replaces a location a cat already has`

### Task 2: `MANUAL`, `SetLocationByHand`, the closest located cat

**Files:**
- Modify: `domain/…/model/Encounter.kt` (`MANUAL` before `NONE`), `domain/src/commonTest/…/model/EncounterTest.kt`
- Create: `domain/src/commonMain/kotlin/dev/catsradar/domain/usecase/SetLocationByHand.kt`
- Create: `domain/src/commonMain/kotlin/dev/catsradar/domain/location/ClosestLocated.kt`
- Modify: `presentation/…/encounters/LocationLabel.kt` (`BY_HAND`), `ui/…/encounters/LocationLabelRes.kt`,
  `ui/src/main/res/values/strings.xml`, `ui/src/main/res/values-ru/strings.xml` (`location_by_hand`)
- Modify: `app/src/main/kotlin/dev/catsradar/app/di/DomainModule.kt` (`factoryOf(::SetLocationByHand)`)
- Test: `domain/src/commonTest/…/usecase/SetLocationByHandTest.kt`, `domain/src/commonTest/…/location/ClosestLocatedTest.kt`,
  `presentation/src/commonTest/…/encounters/LocationLabelTest.kt`

**Interfaces:**
- Consumes: `attachLocation(...): Boolean` (Task 1).
- Produces: `class SetLocationByHand(encounterRepository, placeCellRepository, clock) { suspend operator fun invoke(encounterId: String, lat: Double, lon: Double): Boolean }`;
  `fun List<Encounter>.closestLocatedInTime(target: Encounter): Encounter?`; `LocationLabel.BY_HAND`.

- [ ] **Step 1: Failing tests**
  - `SetLocationByHandTest`: stamps the point as `MANUAL` with no accuracy, fixed and updated at now, geohash at
    `GEOHASH_PRECISION`, place cell at `PLACE_CELL_PRECISION` remembered `PENDING`, returns true; a located cat, a
    deleted cat, an unknown id → false, cat unchanged, no cell remembered; a point off the globe (lat 91, lon 181,
    NaN) → false, nothing written.
  - `ClosestLocatedTest`: picks the smallest |Δ occurredAt| among other live cats with a point on the globe; ignores
    `NONE`, deleted, off-globe cats and the target itself; a tie goes to the earlier cat; none → null.
  - `LocationLabelTest`: every `LocationSource` maps to its token, `MANUAL` → `BY_HAND`, no two sources share one.
  - `EncounterTest`: six entries, `MANUAL` before `NONE`.
- [ ] **Step 2: Run, see them fail** — `./gradlew :domain:allTests :presentation:allTests --rerun`
- [ ] **Step 3: Implement**

```kotlin
class SetLocationByHand(
    private val encounterRepository: EncounterRepository,
    private val placeCellRepository: PlaceCellRepository,
    private val clock: Clock,
) {
    /** True when the cat now has this point; false when it is gone, already has a location, or the point is off the globe. */
    suspend operator fun invoke(encounterId: String, lat: Double, lon: Double): Boolean {
        if (!isOnGlobe(lat, lon)) return false
        encounterRepository.observeById(encounterId).first()
            ?.takeIf { it.deletedAt == null && it.locationSource == LocationSource.NONE }
            ?: return false
        val geohash = Geohash.encode(lat, lon, Tuning.GEOHASH_PRECISION)
        val now = clock.now()
        val stamp = LocationStamp(
            lat = lat, lon = lon, accuracyMeters = null, locationSource = LocationSource.MANUAL,
            locationFixedAt = now, geohash = geohash,
            placeCellId = PlaceCells.remember(placeCellRepository, geohash), updatedAt = now,
        )
        return encounterRepository.attachLocation(encounterId, stamp)
    }
}

/** The other located cat logged closest in time to [target], the earlier on a tie; null when there is none. */
fun List<Encounter>.closestLocatedInTime(target: Encounter): Encounter? =
    filter { it.id != target.id && it.deletedAt == null && it.locatedPoint() != null }
        .minWithOrNull(compareBy<Encounter> { (it.occurredAt - target.occurredAt).absoluteValue }.thenBy { it.occurredAt })
```

- [ ] **Step 4: Run, green; `./gradlew :app:testDebugUnitTest --tests '*Koin*'`** (the new factory resolves)
- [ ] **Step 5: Commit** — `feat: a cat's location can be set by hand`

### Task 3: Backup format 5

**Files:**
- Modify: `data/src/commonMain/kotlin/dev/catsradar/data/backup/BackupRecords.kt` (`BACKUP_FORMAT_VERSION = 5`)
- Test: `data/src/androidHostTest/…/backup/ZipBackupArchiveTest.kt`, `…/ZipBackupReaderOlderFormatTest.kt`

- [ ] **Step 1: Failing tests** — rename `anArchiveSaysItIsFormatFourSo…` to
  `anArchiveSaysItIsFormatFiveSoAnAppBeforeLocationsByHandRefusesIt`, asserting `"formatVersion":5`; add
  `aCatLocatedByHandSurvivesTheRoundTrip` (a `MANUAL` cat, null accuracy); add
  `aFormatFourArchiveStillReadsWithItsPhotoList` (format-4 manifest, every list, one cat, one photo).
- [ ] **Step 2: Run, see the format test fail** — `./gradlew :data:testAndroidHostTest --tests '*ZipBackup*' --rerun`
- [ ] **Step 3: Bump the constant**
- [ ] **Step 4: Run, green**
- [ ] **Step 5: Commit** — `feat: backups say format 5, so an app before locations by hand refuses them`

### Task 4: Docs, check, review

- `docs/features/location.md` (the `MANUAL` source, the guard, the retry and the race), `docs/features/backup.md`
  (format 5), `docs/features/data-model.md` (the enum), `docs/superpowers/specs/2026-09-21-cats-radar-design.md` §3.1
  (the enum), the map's L1 status.
- `./gradlew check`; `/code-review`; acceptance gate against `location-by-hand-l1.md`.
