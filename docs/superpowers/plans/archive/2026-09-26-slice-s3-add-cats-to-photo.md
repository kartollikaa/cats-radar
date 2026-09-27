# Slice S3 — Add Cats To A Photo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a domain operation that safely creates one encounter per extra cat on an existing photo, with independent files, one atomic database write, copied shot metadata, and location-follow-up ids for later UI slices.

**Architecture:** `AddCatsToPhoto` reads the live source encounter and source photo, asks `PhotoStorage` for independent copies, builds one ordinary `Encounter` plus `EncounterPhoto` per coat, then hands the entire batch to one guarded repository transaction. The repository writes only while the source encounter is still live; the use case removes every copied file unless that transaction commits, and emits existing `cat_logged` analytics only after the commit. No screen calls this use case in S3.

**Tech Stack:** Kotlin Multiplatform common code, coroutines, Room 3, Android file storage, Koin, kotlin.test, Android host tests.

**Spec:** `docs/superpowers/specs/2026-09-25-several-cats-per-photo-design.md` § The model, § Storage, § Adding cats to a photo; map `docs/tbd/decompositions/2026-09-25-several-cats-per-photo.md` S3. Criteria: `several-cats-s3.md` in the acceptance directory.

## Global Constraints

- This is S3 only. No Compose, presentation state, Counter control, Encounters grouping, or detail-screen control.
- Every added cat remains one ordinary `Encounter`; totals keep the existing one-encounter-per-cat rule.
- A new encounter copies `occurredAt`, `tzOffsetMinutes`, `kind`, `origin`, and every location field from the source at read time.
- A new encounter has its own encounter id, photo id, current-install `deviceId`, coat, `createdAt`, and `updatedAt`.
- Each new photo has independent app-copy and thumbnail files, copies the source row's links, digest, and photo `deviceId`, and keeps the source photo's `shotId`.
- The whole encounter batch is one Room transaction guarded by the source encounter still being live.
- Any failed or refused attempt leaves no new encounter and removes every destination file created by that attempt.
- `cat_logged` is emitted once per committed cat and never for a refused or failed batch.
- `PhotoStorage` continues to reject paths escaping the app's photo directory.
- Update `photos.md`, `location.md`, and `analytics.md` in this PR; update the S3 map status when the slice reaches review.
- Focused tests run with `--no-build-cache`; the full gate is `CI=true ./gradlew check :app:assembleRelease --console=plain`.

## Review Focus

- The source is soft-deleted after files are copied but before the write: no cat survives and all destination files go.
- A later copy in a multi-cat batch fails after earlier copies succeeded: no repository call happens and every earlier destination goes.
- The second database insert fails after the first succeeded: Room rolls the first insert back with the rest of the batch.
- A source photo has no thumbnail: each added cat gets a photo copy and a null thumbnail without inventing a file.
- An empty coat list is a successful no-op: no ids, files, write, or analytics.

---

### Task 1: Copy A Stored Photo Under A New Name

**Files:**
- Modify: `domain/src/commonMain/kotlin/dev/catsradar/domain/platform/PhotoPlatform.kt`
- Modify: `data/src/androidMain/kotlin/dev/catsradar/data/platform/AndroidPhotoStorage.android.kt`
- Modify: `data/src/androidHostTest/kotlin/dev/catsradar/data/platform/AndroidPhotoStorageTest.kt`
- Modify: `domain/src/commonTest/kotlin/dev/catsradar/domain/testing/FakePhotoPlatform.kt`
- Modify: `domain/src/commonTest/kotlin/dev/catsradar/domain/usecase/PurgeDeletedTest.kt`
- Modify: `presentation/src/commonTest/kotlin/dev/catsradar/presentation/encounters/EncountersTestDoubles.kt`

**Interfaces:**
- Consumes: `StoredPhoto(photoPath: String, thumbPath: String?)` paths already confined to the app's photo directory.
- Produces: `PhotoStorage.copy(stored: StoredPhoto, baseName: String): StoredPhoto`; the destination names are `<baseName>.jpg` and `<baseName>_thumb.jpg`, and a failed call leaves neither destination behind.

- [ ] **Step 1: Write failing Android host tests for a complete copy, a missing thumbnail, and partial-copy cleanup**

Add to `AndroidPhotoStorageTest`:

```kotlin
@Test
fun copyingMakesIndependentPhotoAndThumbnailFilesUnderTheNewName() = runTest {
    photoStorage.prepare("source.jpg").writeText("photo")
    photoStorage.prepare("source_thumb.jpg").writeText("thumb")

    val copied = photoStorage.copy(StoredPhoto("source.jpg", "source_thumb.jpg"), "new-photo")

    assertEquals(StoredPhoto("new-photo.jpg", "new-photo_thumb.jpg"), copied)
    assertEquals("photo", photoStorage.fileFor(copied.photoPath).readText())
    assertEquals("thumb", photoStorage.fileFor(copied.thumbPath!!).readText())
    assertTrue(photoStorage.fileFor("source.jpg").exists())
    assertTrue(photoStorage.fileFor("source_thumb.jpg").exists())
}

@Test
fun copyingAPhotoWithoutAThumbnailKeepsTheThumbnailAbsent() = runTest {
    photoStorage.prepare("source.jpg").writeText("photo")

    val copied = photoStorage.copy(StoredPhoto("source.jpg", null), "new-photo")

    assertEquals(StoredPhoto("new-photo.jpg", null), copied)
    assertFalse(photoStorage.fileFor("new-photo_thumb.jpg").exists())
}

@Test
fun aThumbnailCopyFailureRemovesThePhotoCopiedEarlierInTheCall() = runTest {
    photoStorage.prepare("source.jpg").writeText("photo")
    photoStorage.prepare("source_thumb.jpg").writeText("thumb")
    photoStorage.prepare("new-photo_thumb.jpg").mkdirs()

    assertFails { photoStorage.copy(StoredPhoto("source.jpg", "source_thumb.jpg"), "new-photo") }

    assertFalse(photoStorage.fileFor("new-photo.jpg").exists())
}
```

- [ ] **Step 2: Run the focused host test and observe the missing API failure**

Run:

```bash
./gradlew :data:testAndroidHostTest --tests dev.catsradar.data.platform.AndroidPhotoStorageTest --no-build-cache --console=plain
```

Expected: FAIL because `PhotoStorage.copy` does not exist.

- [ ] **Step 3: Add the storage capability and Android implementation**

Extend `PhotoStorage`:

```kotlin
/** Makes an independent app-owned copy named after [baseName]; a failed call leaves no destination files. */
suspend fun copy(stored: StoredPhoto, baseName: String): StoredPhoto
```

Implement it in `AndroidPhotoStorage` on `ioDispatcher`. Resolve source and destination with `fileFor`, refuse each pre-existing destination immediately before its own copy, copy the full photo first and the thumbnail second, and delete only destinations created by this call in `catch` before rethrowing. Return `StoredPhoto("$baseName.jpg", stored.thumbPath?.let { "${baseName}_thumb.jpg" })`. Add the same API to `RecordingPhotoStorage`, recording successful copies and allowing a test-selected copy number to throw. Add explicit unused-operation failures to the anonymous storage in `PurgeDeletedTest` and the presentation `FakePhotoStorage`; they render or delete paths but never copy files.

- [ ] **Step 4: Run the focused host test and the domain fake's compile suite**

Run:

```bash
./gradlew :data:testAndroidHostTest --tests dev.catsradar.data.platform.AndroidPhotoStorageTest :domain:testAndroid --no-build-cache --console=plain
```

Expected: PASS.

- [ ] **Step 5: Commit the independently testable storage capability**

```bash
git add domain/src/commonMain/kotlin/dev/catsradar/domain/platform/PhotoPlatform.kt \
  domain/src/commonTest/kotlin/dev/catsradar/domain/testing/FakePhotoPlatform.kt \
  domain/src/commonTest/kotlin/dev/catsradar/domain/usecase/PurgeDeletedTest.kt \
  data/src/androidMain/kotlin/dev/catsradar/data/platform/AndroidPhotoStorage.android.kt \
  data/src/androidHostTest/kotlin/dev/catsradar/data/platform/AndroidPhotoStorageTest.kt \
  presentation/src/commonTest/kotlin/dev/catsradar/presentation/encounters/EncountersTestDoubles.kt
git commit -m "feat: copy stored photos for another cat"
```

### Task 2: Insert A Shot's New Cats Atomically While The Source Is Live

**Files:**
- Modify: `domain/src/commonMain/kotlin/dev/catsradar/domain/repository/EncounterRepository.kt`
- Modify: `data/src/commonMain/kotlin/dev/catsradar/data/db/EncounterDao.kt`
- Modify: `data/src/commonMain/kotlin/dev/catsradar/data/repository/EncounterRepositoryImpl.kt`
- Modify: `data/src/commonTest/kotlin/dev/catsradar/data/repository/FakeEncounterDao.kt`
- Modify: `data/src/commonTest/kotlin/dev/catsradar/data/repository/EncounterRepositoryImplTest.kt`
- Create: `data/src/androidHostTest/kotlin/dev/catsradar/data/db/EncounterDaoInsertShotTest.kt`
- Modify: `domain/src/commonTest/kotlin/dev/catsradar/domain/testing/FakeEncounterRepository.kt`
- Modify: `presentation/src/commonTest/kotlin/dev/catsradar/presentation/counter/CounterStoreTestDoubles.kt`
- Modify: `app/src/test/kotlin/dev/catsradar/app/notification/WalkTestDoubles.kt`
- Modify: `app/src/test/kotlin/dev/catsradar/app/widget/FakeTodayRepository.kt`
- Modify: `app/src/test/kotlin/dev/catsradar/app/worker/AttachLocationWorkerTest.kt`

**Interfaces:**
- Consumes: a live `sourceEncounterId` and complete new `Encounter` aggregates, each with one photo.
- Produces: `EncounterRepository.insertAllIfSourceLive(sourceEncounterId: String, encounters: List<Encounter>): Boolean`; `false` means the source was no longer live and nothing was written, while an exception also rolls the whole batch back.

- [ ] **Step 1: Write failing Room tests for the live guard and transaction rollback**

Create `EncounterDaoInsertShotTest` using `buildInMemoryCatsDatabase`:

```kotlin
@Test
fun aLiveSourceAllowsEveryNewCatAndPhotoToLandTogether() = runTest {
    dao.insert(source)

    assertTrue(dao.insertAllIfSourceLive(source.id, listOf(first, second), listOf(firstPhoto, secondPhoto)))

    assertEquals(setOf(source.id, first.id, second.id), dao.loadEvery().map { it.encounter.id }.toSet())
}

@Test
fun aDeletedSourceRefusesTheWholeBatch() = runTest {
    dao.insert(source.copy(deletedAt = DELETED_AT))

    assertFalse(dao.insertAllIfSourceLive(source.id, listOf(first, second), listOf(firstPhoto, secondPhoto)))

    assertEquals(listOf(source.id), dao.loadEvery().map { it.encounter.id })
}

@Test
fun aFailureOnTheSecondCatRollsTheFirstCatBack() = runTest {
    dao.insert(source)
    val duplicateSecond = second.copy(id = source.id)

    assertFails { dao.insertAllIfSourceLive(source.id, listOf(first, duplicateSecond), listOf(firstPhoto)) }

    assertEquals(listOf(source.id), dao.loadEvery().map { it.encounter.id })
}
```

- [ ] **Step 2: Run the focused Room test and observe the missing transaction API failure**

Run:

```bash
./gradlew :data:testAndroidHostTest --tests dev.catsradar.data.db.EncounterDaoInsertShotTest --no-build-cache --console=plain
```

Expected: FAIL because `insertAllIfSourceLive` does not exist.

- [ ] **Step 3: Add the DAO transaction and repository mapping**

Add this shape to `EncounterDao`:

```kotlin
@Transaction
suspend fun insertAllIfSourceLive(
    sourceEncounterId: String,
    encounters: List<EncounterEntity>,
    photos: List<EncounterPhotoEntity>,
): Boolean {
    if (countLive(sourceEncounterId) == 0) return false
    encounters.forEach { insert(it) }
    insertPhotos(photos)
    return true
}
```

Expose the aggregate-level method on `EncounterRepository`. In `EncounterRepositoryImpl`, map every encounter row and flatten every encounter's photos before one DAO call. In the data and domain fakes, record the entire call and its result; `FakeEncounterRepository` must restore its in-memory list if an insert throws so its behavior matches Room. Add explicit unused-operation failures to the four direct test implementations in `:presentation` and `:app`; delegating wrappers inherit the new operation from their delegate and need no edit.

- [ ] **Step 4: Add the repository mapping test**

Add to `EncounterRepositoryImplTest`:

```kotlin
@Test
fun insertAllIfSourceLiveHandsOneCompleteBatchToTheDaoAndReturnsItsAnswer() = runTest {
    val cats = listOf(distinctEncounter(), distinctEncounter().copy(id = "encounter-id-2"))

    assertTrue(repository.insertAllIfSourceLive("source", cats))

    assertEquals(cats.map { it.toEntity() }, dao.insertAllIfSourceLiveCall?.encounters)
    assertEquals(cats.flatMap { it.photos }.map { it.toEntity() }, dao.insertAllIfSourceLiveCall?.photos)
}
```

- [ ] **Step 5: Run focused data and domain tests**

Run:

```bash
./gradlew :data:testAndroidHostTest --tests dev.catsradar.data.db.EncounterDaoInsertShotTest \
  :data:testAndroid --tests dev.catsradar.data.repository.EncounterRepositoryImplTest \
  :domain:testAndroid --no-build-cache --console=plain
```

Expected: PASS.

- [ ] **Step 6: Commit the guarded atomic repository write**

```bash
git add domain/src/commonMain/kotlin/dev/catsradar/domain/repository/EncounterRepository.kt \
  domain/src/commonTest/kotlin/dev/catsradar/domain/testing/FakeEncounterRepository.kt \
  data/src/commonMain/kotlin/dev/catsradar/data/db/EncounterDao.kt \
  data/src/commonMain/kotlin/dev/catsradar/data/repository/EncounterRepositoryImpl.kt \
  data/src/commonTest/kotlin/dev/catsradar/data/repository/FakeEncounterDao.kt \
  data/src/commonTest/kotlin/dev/catsradar/data/repository/EncounterRepositoryImplTest.kt \
  data/src/androidHostTest/kotlin/dev/catsradar/data/db/EncounterDaoInsertShotTest.kt \
  presentation/src/commonTest/kotlin/dev/catsradar/presentation/counter/CounterStoreTestDoubles.kt \
  app/src/test/kotlin/dev/catsradar/app/notification/WalkTestDoubles.kt \
  app/src/test/kotlin/dev/catsradar/app/widget/FakeTodayRepository.kt \
  app/src/test/kotlin/dev/catsradar/app/worker/AttachLocationWorkerTest.kt
git commit -m "feat: insert a shot's cats atomically"
```

### Task 3: Build And Save The New Cats

**Files:**
- Create: `domain/src/commonMain/kotlin/dev/catsradar/domain/usecase/AddCatsToPhoto.kt`
- Create: `domain/src/commonTest/kotlin/dev/catsradar/domain/usecase/AddCatsToPhotoTest.kt`
- Modify: `app/src/main/kotlin/dev/catsradar/app/di/DomainModule.kt`
- Modify: `app/src/test/kotlin/dev/catsradar/app/di/KoinRuntimeResolutionTest.kt`

**Interfaces:**
- Consumes: `AddCatsToPhoto(sourceEncounterId: String, photoId: String, coats: List<CatCoat?>)`.
- Produces: `AddCatsResult.Added(cats: List<AddedCat>)`, where each `AddedCat(id, needsLocation)` follows the established `ImportedPhoto` result pattern; `AddCatsResult.NotAddable` means the source encounter/photo is absent or the source loses its live guard. Exceptions propagate after cleanup so S5/S6 can show their existing write-failure message.

- [ ] **Step 1: Write failing use-case tests for the complete copied aggregate and result**

The happy-path test seeds a source with deliberately distinct values for every copied field and calls with `listOf(CatCoat.BLACK, null)`. Assert:

```kotlin
assertEquals(
    AddCatsResult.Added(listOf(AddedCat("id-1", needsLocation = true), AddedCat("id-3", needsLocation = true))),
    result,
)
assertEquals(listOf(CatCoat.BLACK, null), inserted.map { it.coat })
assertTrue(inserted.all { it.occurredAt == source.occurredAt && it.kind == source.kind && it.origin == source.origin })
    assertTrue(inserted.all {
        it.lat == source.lat && it.lon == source.lon &&
            it.accuracyMeters == source.accuracyMeters && it.locationSource == source.locationSource &&
            it.locationFixedAt == source.locationFixedAt && it.geohash == source.geohash &&
            it.placeCellId == source.placeCellId && it.deviceId == "this-device"
    })
assertEquals(listOf("id-2.jpg", "id-4.jpg"), inserted.map { it.cover!!.photoPath })
assertTrue(inserted.all { it.cover!!.shotId == sourcePhoto.shotId })
assertTrue(inserted.all { it.cover!!.galleryUri == sourcePhoto.galleryUri })
assertTrue(inserted.all { it.cover!!.sourceMediaUri == sourcePhoto.sourceMediaUri })
assertTrue(inserted.all { it.cover!!.sourceDigest == sourcePhoto.sourceDigest })
assertTrue(inserted.all { it.cover!!.deviceId == sourcePhoto.deviceId })
```

Add a located-source case proving every returned cat has `needsLocation = false` while all location fields are copied.

- [ ] **Step 2: Write failing use-case tests for every refusal and cleanup boundary**

Add named tests proving:

- an unknown source or a `photoId` not owned by it returns `NotAddable` without copying or writing;
- an empty coat list returns `Added(emptyList())` without copying, writing, or analytics;
- the second copy throwing deletes the first completed photo and thumbnail and never calls the repository;
- a repository `false` after all copies returns `NotAddable`, deletes all completed copies, and logs nothing;
- a repository exception deletes all completed copies, logs nothing, and is rethrown;
- a successful batch logs `source.copy(coat = requestedCoat).logged()` once per new cat, after the write.

- [ ] **Step 3: Run the new use-case test and observe the missing class failure**

Run:

```bash
./gradlew :domain:testAndroid --tests dev.catsradar.domain.usecase.AddCatsToPhotoTest --no-build-cache --console=plain
```

Expected: FAIL because `AddCatsToPhoto` and `AddCatsResult` do not exist.

- [ ] **Step 4: Implement `AddCatsToPhoto` with one cleanup owner**

Use this public result shape:

```kotlin
sealed interface AddCatsResult {
    data class Added(val cats: List<AddedCat>) : AddCatsResult
    data object NotAddable : AddCatsResult
}

data class AddedCat(val id: String, val needsLocation: Boolean)
```

The use case must:

1. read the live source and find `photoId` inside that source;
2. return the empty success before any platform or repository call when `coats` is empty;
3. inside one `try/finally`, create an encounter id and photo id for each coat, call `PhotoStorage.copy`, and remember each completed `StoredPhoto`;
4. build each encounter with copied occurrence/kind/origin/location fields, current-install encounter `deviceId`, one copied photo retaining the source photo's links/digest/photo `deviceId` and `shotId`, and one `clock.now()` used for that encounter's `createdAt`, `updatedAt`, and photo `addedAt`;
5. call `encounterRepository.insertAllIfSourceLive` inside `withContext(NonCancellable)`;
6. set `committed = true` only after the repository returns `true`, then log `cat_logged` for each encounter and return one `AddedCat` per encounter with `needsLocation = (locationSource == NONE)`;
7. in `finally`, when not committed, delete every completed copy and thumbnail inside `withContext(NonCancellable)`.

Register `factoryOf(::AddCatsToPhoto)` in `DomainModule`; all constructor parameters are real collaborators with no default value. Add `assertNotNull(koin.get<AddCatsToPhoto>())` to `KoinRuntimeResolutionTest`: no shipped caller consumes the binding until S5, so graph verification alone cannot detect an omitted binding in S3.

- [ ] **Step 5: Run the focused use-case tests and Koin verification**

Run:

```bash
./gradlew :domain:testAndroid --tests dev.catsradar.domain.usecase.AddCatsToPhotoTest \
  :app:testDebugUnitTest --tests dev.catsradar.app.di.KoinModulesTest \
  --tests dev.catsradar.app.di.KoinRuntimeResolutionTest --no-build-cache --console=plain
```

Expected: PASS.

- [ ] **Step 6: Commit the domain operation**

```bash
git add domain/src/commonMain/kotlin/dev/catsradar/domain/usecase/AddCatsToPhoto.kt \
  domain/src/commonTest/kotlin/dev/catsradar/domain/usecase/AddCatsToPhotoTest.kt \
  app/src/main/kotlin/dev/catsradar/app/di/DomainModule.kt \
  app/src/test/kotlin/dev/catsradar/app/di/KoinRuntimeResolutionTest.kt
git commit -m "feat: add cats to an existing photo"
```

### Task 4: Pin Lifecycle Edges And Document The Shipped Capability

**Files:**
- Modify: `domain/src/commonTest/kotlin/dev/catsradar/domain/usecase/PurgeDeletedTest.kt`
- Modify: `domain/src/commonTest/kotlin/dev/catsradar/domain/usecase/UndoImportTest.kt`
- Modify: `docs/features/photos.md`
- Modify: `docs/features/location.md`
- Modify: `docs/features/analytics.md`
- Modify: `docs/superpowers/specs/2026-09-24-firebase-analytics-crashlytics-design.md`
- Modify: `docs/tbd/decompositions/2026-09-25-several-cats-per-photo.md`

**Interfaces:**
- Consumes: the independent file paths and encounter ids produced by `AddCatsToPhoto`.
- Produces: regression evidence that ordinary purge and import undo affect only the intended encounter, plus current shipped-behavior docs.

- [ ] **Step 1: Add the purge regression**

Extend `PurgeDeletedTest` with two cats whose photos share one `shotId` but use different `photoPath` and `thumbPath`. Soft-delete only the first, run `PurgeDeleted`, and assert the recording storage deleted only the first cat's two paths while the second encounter and paths remain untouched.

- [ ] **Step 2: Add the import-undo regression**

Extend `UndoImportTest`: seed an imported source cat and a second live cat representing one added through `AddCatsToPhoto`; call `UndoImport(listOf(source.id))`; assert `observeById(source.id).first()` is null and `observeById(added.id).first()` still returns the added cat with its photo.

- [ ] **Step 3: Run the lifecycle tests**

Run:

```bash
./gradlew :domain:testAndroid \
  --tests dev.catsradar.domain.usecase.PurgeDeletedTest \
  --tests dev.catsradar.domain.usecase.UndoImportTest \
  --no-build-cache --console=plain
```

Expected: PASS.

- [ ] **Step 4: Update human-readable behavior docs and the map**

Document in `photos.md` that the same shot can now have several encounter rows, each with independent app files and one shared `shotId`, and that no shipped screen invokes the capability yet. Document in `location.md` that added cats copy the source's current location and that each result item marks whether a Counter caller must schedule a fix later; a detail caller will not schedule one. Add the per-new-cat post-commit `cat_logged` rule to `analytics.md`. Clarify the older analytics design: `photos_imported` replaces per-cat events for the original import, while a cat added later to that photo emits `cat_logged` with copied origin `gallery`. Change only S3 from `planned` to `in-review` in the decomposition map; leave S4–S6 planned.

- [ ] **Step 5: Validate the three critical guards with positive controls**

Run each mutation against the named focused test, restore it, and rerun green:

1. make `PhotoStorage.copy` return the source paths instead of copying — `AndroidPhotoStorageTest.copyingMakesIndependentPhotoAndThumbnailFilesUnderTheNewName` must fail;
2. remove `@Transaction` from `EncounterDao.insertAllIfSourceLive` — `EncounterDaoInsertShotTest.aFailureOnTheSecondCatRollsTheFirstCatBack` must fail;
3. make `AddCatsToPhoto` set `committed = true` before the repository answer — the repository-false cleanup test must fail.

- [ ] **Step 6: Run the full CI-equivalent gate**

Run:

```bash
CI=true ./gradlew check :app:assembleRelease --console=plain
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit docs and lifecycle evidence**

```bash
git add domain/src/commonTest/kotlin/dev/catsradar/domain/usecase/PurgeDeletedTest.kt \
  domain/src/commonTest/kotlin/dev/catsradar/domain/usecase/UndoImportTest.kt \
  docs/features/photos.md docs/features/location.md docs/features/analytics.md \
  docs/superpowers/specs/2026-09-24-firebase-analytics-crashlytics-design.md \
  docs/tbd/decompositions/2026-09-25-several-cats-per-photo.md
git commit -m "docs: describe cats added to a photo"
```

- [ ] **Step 8: Review, size, and acceptance gate**

Run the TBD size check before PR creation, perform an independent whole-diff review, fix every Critical or Important finding, rerun affected checks, then run `acceptance-gate` against the frozen `several-cats-s3.md`. The PR body carries the criteria, verdicts, commands, positive-control results, documentation impact, and review result.
