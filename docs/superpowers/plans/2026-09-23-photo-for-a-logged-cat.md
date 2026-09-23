# Photo for a logged cat — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A cat logged without a photo can be given one — from the camera or the gallery — on its detail screen, keeping its time, place and coat.

**Architecture:** Two PRs. **P1a** (tasks 1–4): an `AttachPhoto` use case in `:domain` over a guarded
`attachPhoto` write in `:data` that touches only the photo columns of a live row with no photo; "With
photo" counted by `photoPath`. Nothing calls it. **P1b** (tasks 5–8): the detail Store/mapper/screen
offer "Take a photo" / "Choose from gallery", `:app` shares the Counter's camera launcher and adds a
single-image picker.

**Tech Stack:** Kotlin Multiplatform (Android target), Room, Compose Material 3, Koin, kotlinx-coroutines-test, Turbine, Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-21-cats-radar-design.md` — §2 F6, §3.1, §4.2a, §5 "With photo", §6.6. Decomposition: `docs/tbd/decompositions/2026-09-23-cat-photos.md` (P1a, P1b).

## Global Constraints

- Rules in `docs/rules/*.md` are binding: module boundaries, MVI shape, Compose patterns, commenting standards (default: no comment; 1 line, 2 max).
- No `java.time` outside `androidMain`; time is `kotlin.time.Instant` and an injected `Clock`.
- State is data: immutable, no lambdas, no platform types; user-facing text only in EN + RU resources.
- `./gradlew check` green before a PR. No detekt baseline; `@Suppress` only with its reason on the same line.
- Every behaviour change updates its `docs/features/*.md` in the same PR.
- Branches: P1a `feature/attach-photo-data`, P1b `feature/attach-photo-screen`. Merge commits.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- The Bash tool is zsh: no `#` lines in pasted commands, quote globs.

Run single test classes with:
- `:domain` — `./gradlew :domain:testAndroidHostTest --tests 'dev.catsradar.domain.usecase.AttachPhotoTest'`
- `:data` common — `./gradlew :data:testAndroidHostTest --tests 'dev.catsradar.data.repository.EncounterRepositoryImplTest'`
- `:data` Robolectric — `./gradlew :data:testAndroidHostTest --tests 'dev.catsradar.data.db.EncounterDaoAttachPhotoTest'`
- `:presentation` — `./gradlew :presentation:testAndroidHostTest --tests 'dev.catsradar.presentation.detail.*'`
- `:app` — `./gradlew :app:testDebugUnitTest --tests 'dev.catsradar.app.navigation.*'`

(If a task name differs, `./gradlew :<module>:tasks --all | grep -i test` finds it; do not guess further.)

---

# P1a — Attaching a photo, in domain and data

### Task 1: The guarded `attachPhoto` write

**Files:**
- Create: `domain/src/commonMain/kotlin/dev/catsradar/domain/model/PhotoStamp.kt`
- Modify: `domain/src/commonMain/kotlin/dev/catsradar/domain/repository/EncounterRepository.kt`
- Modify: `data/src/commonMain/kotlin/dev/catsradar/data/db/EncounterDao.kt`
- Modify: `data/src/commonMain/kotlin/dev/catsradar/data/repository/EncounterRepositoryImpl.kt`
- Modify fakes: `data/src/commonTest/kotlin/dev/catsradar/data/repository/FakeEncounterDao.kt`,
  `domain/src/commonTest/kotlin/dev/catsradar/domain/testing/FakeEncounterRepository.kt`,
  `presentation/src/commonTest/kotlin/dev/catsradar/presentation/counter/CounterStoreTestDoubles.kt`,
  `app/src/test/kotlin/dev/catsradar/app/widget/WidgetRefreshTest.kt`,
  `app/src/test/kotlin/dev/catsradar/app/notification/WalkingNotificationSyncTest.kt`,
  `app/src/test/kotlin/dev/catsradar/app/worker/AttachLocationWorkerTest.kt`
- Test: `data/src/androidHostTest/kotlin/dev/catsradar/data/db/EncounterDaoAttachPhotoTest.kt` (create),
  `data/src/commonTest/kotlin/dev/catsradar/data/repository/EncounterRepositoryImplTest.kt`

**Interfaces:**
- Produces: `data class PhotoStamp(photoPath: String, thumbPath: String?, galleryUri: String?, sourceDigest: String?, updatedAt: Instant)`;
  `EncounterRepository.attachPhoto(id: String, stamp: PhotoStamp): Boolean`;
  `EncounterDao.attachPhoto(id, photoPath, thumbPath, galleryUri, sourceDigest, updatedAt): Int`;
  domain `FakeEncounterRepository` gains `attachPhotoShouldThrow: Throwable?` and mirrors the guard;
  presentation `FakeEncounterRepository` mirrors the guard and gains `attachPhotoShouldThrow`.

- [ ] **Step 1: Write the failing DAO test**

```kotlin
package dev.catsradar.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class EncounterDaoAttachPhotoTest {
    private lateinit var database: TestCatsDatabase
    private lateinit var dao: EncounterDao

    @Before
    fun createDatabase() {
        database = buildInMemoryCatsDatabase(ApplicationProvider.getApplicationContext<Context>())
        dao = database.encounterDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun attachPhotoWritesOnlyThePhotoColumnsOfALiveRowWithoutOne() = runTest {
        val entity = tally("live")
        dao.insert(entity)

        val written = attach("live")

        assertEquals(1, written)
        assertEquals(
            entity.copy(
                photoPath = "p.jpg",
                thumbPath = "p_thumb.jpg",
                galleryUri = "content://gallery/7",
                sourceDigest = "sha",
                updatedAt = UPDATED,
            ),
            dao.observeById("live").first(),
        )
    }

    @Test
    fun attachPhotoOnASoftDeletedRowNeitherResurrectsItNorGivesItAPhoto() = runTest {
        val entity = tally("deleted", deletedAt = Instant.parse("2026-09-21T08:00:00Z"))
        dao.insert(entity)

        assertEquals(0, attach("deleted"))
        assertEquals(listOf(entity), dao.loadEvery())
    }

    @Test
    fun attachPhotoNeverReplacesAPhotoTheRowAlreadyHas() = runTest {
        val entity = fullEncounterEntity(id = "photo")
        dao.insert(entity)

        assertEquals(0, attach("photo"))
        assertEquals(entity, dao.observeById("photo").first())
    }

    private suspend fun attach(id: String): Int = dao.attachPhoto(
        id = id,
        photoPath = "p.jpg",
        thumbPath = "p_thumb.jpg",
        galleryUri = "content://gallery/7",
        sourceDigest = "sha",
        updatedAt = UPDATED,
    )

    private fun tally(id: String, deletedAt: Instant? = null) =
        fullEncounterEntity(id = id, sourceDigest = null, deletedAt = deletedAt).copy(
            kind = EncounterKind.TALLY,
            origin = EncounterOrigin.APP,
            photoPath = null,
            thumbPath = null,
            galleryUri = null,
        )

    private companion object {
        val UPDATED = Instant.parse("2026-09-21T09:05:00Z")
    }
}
```

- [ ] **Step 2: Run it — expect a compile failure** (`attachPhoto` is not on `EncounterDao`).

- [ ] **Step 3: Add `PhotoStamp`, the DAO query, the repository method**

`PhotoStamp.kt`:
```kotlin
package dev.catsradar.domain.model

import kotlin.time.Instant

/** The photo columns written together when a cat logged without a photo is given one. */
data class PhotoStamp(
    val photoPath: String,
    val thumbPath: String?,
    val galleryUri: String?,
    val sourceDigest: String?,
    val updatedAt: Instant,
)
```

`EncounterRepository`, after `attachLocation`:
```kotlin
    /** False, writing nothing, when the row is soft-deleted or already has a photo. */
    suspend fun attachPhoto(id: String, stamp: PhotoStamp): Boolean
```

`EncounterDao`, after `attachLocation`:
```kotlin
    // A full-row update here would resurrect a cat deleted while its photo was being copied.
    @Suppress("LongParameterList") // Room binds one :placeholder per parameter; no POJO destructuring in a raw @Query
    @Query(
        """
        UPDATE encounters SET
            photoPath = :photoPath, thumbPath = :thumbPath, galleryUri = :galleryUri,
            sourceDigest = :sourceDigest, updatedAt = :updatedAt
        WHERE id = :id AND deletedAt IS NULL AND photoPath IS NULL
        """
    )
    suspend fun attachPhoto(
        id: String,
        photoPath: String,
        thumbPath: String?,
        galleryUri: String?,
        sourceDigest: String?,
        updatedAt: Instant,
    ): Int
```

`EncounterRepositoryImpl`, after `attachLocation`:
```kotlin
    override suspend fun attachPhoto(id: String, stamp: PhotoStamp): Boolean = dao.attachPhoto(
        id = id,
        photoPath = stamp.photoPath,
        thumbPath = stamp.thumbPath,
        galleryUri = stamp.galleryUri,
        sourceDigest = stamp.sourceDigest,
        updatedAt = stamp.updatedAt,
    ) > 0
```

- [ ] **Step 4: Make every fake compile**

`FakeEncounterDao` — record the call and return a settable count:
```kotlin
internal data class AttachPhotoCall(
    val id: String,
    val photoPath: String,
    val thumbPath: String?,
    val galleryUri: String?,
    val sourceDigest: String?,
    val updatedAt: Instant,
)
```
```kotlin
    var attachPhotoResult: Int = 1
    var attachPhotoCall: AttachPhotoCall? = null

    @Suppress("LongParameterList") // mirrors EncounterDao.attachPhoto's own Room binding constraint
    override suspend fun attachPhoto(
        id: String,
        photoPath: String,
        thumbPath: String?,
        galleryUri: String?,
        sourceDigest: String?,
        updatedAt: Instant,
    ): Int {
        attachPhotoCall = AttachPhotoCall(id, photoPath, thumbPath, galleryUri, sourceDigest, updatedAt)
        return attachPhotoResult
    }
```

Domain `FakeEncounterRepository` — mirror the DAO guard, and make `observeById` hide soft-deleted
rows as the DAO's `WHERE deletedAt IS NULL` does:
```kotlin
    var attachPhotoShouldThrow: Throwable? = null

    override fun observeById(id: String): Flow<Encounter?> =
        encounters.map { list -> list.firstOrNull { it.id == id && it.deletedAt == null } }

    // Mirrors the DAO's WHERE deletedAt IS NULL AND photoPath IS NULL guard, checked at write time.
    override suspend fun attachPhoto(id: String, stamp: PhotoStamp): Boolean {
        attachPhotoShouldThrow?.let { throw it }
        val target = encounters.value.firstOrNull { it.id == id && it.deletedAt == null && it.photoPath == null }
            ?: return false
        update(
            target.copy(
                photoPath = stamp.photoPath,
                thumbPath = stamp.thumbPath,
                galleryUri = stamp.galleryUri,
                sourceDigest = stamp.sourceDigest,
                updatedAt = stamp.updatedAt,
            ),
        )
        return true
    }
```
`AttachLocationTest`'s row helper then reads through `loadEvery`, which still sees deleted rows:
```kotlin
    private suspend fun encounter(repository: FakeEncounterRepository, id: String) =
        repository.loadEvery().first { it.id == id }
```

Presentation `FakeEncounterRepository` (in `CounterStoreTestDoubles.kt`) — the same `attachPhoto`
body and `attachPhotoShouldThrow` field as the domain fake (its `observeById` already filters).

The three `:app` test fakes — the method is unused there:
```kotlin
    override suspend fun attachPhoto(id: String, stamp: PhotoStamp): Boolean =
        throw NotImplementedError("unused by this test")
```

- [ ] **Step 5: Add the repository forwarding test** to `EncounterRepositoryImplTest`:
```kotlin
    @Test
    fun attachPhotoForwardsEveryStampFieldAndReportsWhetherARowWasWritten() = runTest {
        val stamp = PhotoStamp(
            photoPath = "p.jpg",
            thumbPath = "p_thumb.jpg",
            galleryUri = "content://gallery/7",
            sourceDigest = "sha",
            updatedAt = Instant.parse("2026-02-01T00:00:00Z"),
        )

        assertEquals(true, repository.attachPhoto("id-1", stamp))
        assertEquals(
            AttachPhotoCall("id-1", "p.jpg", "p_thumb.jpg", "content://gallery/7", "sha", stamp.updatedAt),
            dao.attachPhotoCall,
        )

        dao.attachPhotoResult = 0
        assertEquals(false, repository.attachPhoto("id-1", stamp))
    }
```

- [ ] **Step 6: Run** the DAO test, `EncounterRepositoryImplTest`, `./gradlew :domain:testAndroidHostTest`
  (the whole module — the `observeById` change must break nothing else) and `:app:testDebugUnitTest`.
  Expected: all PASS.

- [ ] **Step 7: Commit** — `Guarded attachPhoto write: only the photo columns of a live cat with none`.

---

### Task 2: `AttachPhoto`

**Files:**
- Create: `domain/src/commonMain/kotlin/dev/catsradar/domain/usecase/AttachPhoto.kt`
- Modify: `domain/src/commonMain/kotlin/dev/catsradar/domain/platform/PhotoPlatform.kt` (rename `encounterId` → `baseName`)
- Modify: `data/src/androidMain/kotlin/dev/catsradar/data/platform/AndroidImageResizer.android.kt` (same rename)
- Modify: `domain/src/commonTest/kotlin/dev/catsradar/domain/testing/FakePhotoPlatform.kt`,
  `presentation/src/commonTest/kotlin/dev/catsradar/presentation/counter/CounterStoreTestDoubles.kt` (rename; see below)
- Modify: `domain/src/commonTest/kotlin/dev/catsradar/domain/usecase/PurgeDeletedTest.kt` (use the shared storage fake)
- Test: `domain/src/commonTest/kotlin/dev/catsradar/domain/usecase/AttachPhotoTest.kt`

**Interfaces:**
- Consumes: `EncounterRepository.attachPhoto`, `PhotoStamp` (Task 1).
- Produces:
  ```kotlin
  enum class PhotoSource { CAMERA, GALLERY }
  sealed interface AttachResult { Attached; Unreadable; NotAttachable }
  class AttachPhoto(encounterRepository, settingsRepository, imageResizer, digest, gallerySaver, photoStorage, idGenerator, clock)
  suspend operator fun invoke(encounterId: String, sourceUri: String, source: PhotoSource): AttachResult
  ```
  `ImageResizer.store(sourceUri: String, baseName: String): StoredPhoto?`.

- [ ] **Step 1: Rename the resizer parameter and share the storage fake**

`PhotoPlatform.kt`:
```kotlin
interface ImageResizer {
    /**
     * Writes a downscaled copy and a thumbnail of [sourceUri], named after [baseName], stripped of
     * metadata. Null when the source cannot be decoded; a null [StoredPhoto.thumbPath] means the
     * copy was written but the thumbnail was not.
     */
    suspend fun store(sourceUri: String, baseName: String): StoredPhoto?
}
```
`AndroidImageResizer`: rename the parameter and its two uses (`"$baseName.jpg"`, `"$baseName$THUMB_SUFFIX"`).

Domain `FakeImageResizer` — rename, record names, and allow a hook that runs mid-copy:
```kotlin
    val baseNames = mutableListOf<String>()

    /** Runs while the copy is being made, for what else happens to the cat in the meantime. */
    var duringStore: suspend () -> Unit = {}

    override suspend fun store(sourceUri: String, baseName: String): StoredPhoto? {
        calls++
        baseNames += baseName
        duringStore()
        return result.takeIf { sourceUri !in undecodable }
    }
```
Presentation `FakeImageResizer` — rename the parameter to `baseName`.

Move `RecordingPhotoStorage` out of `PurgeDeletedTest.kt` into `FakePhotoPlatform.kt`, public, unchanged
body, and import it in `PurgeDeletedTest`.

- [ ] **Step 2: Write the failing tests** — `AttachPhotoTest.kt`:

```kotlin
package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeDigest
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.FakeGallerySaver
import dev.catsradar.domain.testing.FakeIdGenerator
import dev.catsradar.domain.testing.FakeImageResizer
import dev.catsradar.domain.testing.FakeSettingsRepository
import dev.catsradar.domain.testing.RecordingPhotoStorage
import dev.catsradar.domain.testing.encounterFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class AttachPhotoTest {

    private val encounters = FakeEncounterRepository()
    private val settings = FakeSettingsRepository()
    private val resizer = FakeImageResizer()
    private val gallery = FakeGallerySaver()
    private val storage = RecordingPhotoStorage()

    private val attachPhoto = AttachPhoto(
        encounterRepository = encounters,
        settingsRepository = settings,
        imageResizer = resizer,
        digest = FakeDigest(),
        gallerySaver = gallery,
        photoStorage = storage,
        idGenerator = FakeIdGenerator(),
        clock = FakeClock(NOW),
    )

    private val tally: Encounter = encounterFixture(
        ID,
        OCCURRED,
        locationSource = LocationSource.CURRENT_FIX,
        lat = 41.39864,
        lon = 2.17842,
    ).copy(coat = CatCoat.GINGER)

    private suspend fun stored(id: String = ID): Encounter = encounters.loadEvery().first { it.id == id }

    @Test
    fun `a cat without a photo gets the copy, thumbnail and digest, and keeps everything else`() = runTest {
        encounters.insert(tally)

        assertEquals(AttachResult.Attached, attachPhoto(ID, SOURCE, PhotoSource.GALLERY))

        assertEquals(
            tally.copy(
                photoPath = FakeImageResizer.PHOTO_PATH,
                thumbPath = FakeImageResizer.THUMB_PATH,
                sourceDigest = FakeDigest.SHA,
                updatedAt = NOW,
            ),
            stored(),
        )
    }

    @Test
    fun `the files are named afresh for the attempt, never after the cat`() = runTest {
        encounters.insert(tally)

        attachPhoto(ID, SOURCE, PhotoSource.GALLERY)

        assertEquals(listOf("id-1"), resizer.baseNames)
    }

    @Test
    fun `a camera original goes to the gallery when the setting is on`() = runTest {
        encounters.insert(tally)

        attachPhoto(ID, SOURCE, PhotoSource.CAMERA)

        assertEquals(1, gallery.calls)
        assertEquals(FakeGallerySaver.URI, stored().galleryUri)
    }

    @Test
    fun `a camera original stays out of the gallery when the setting is off`() = runTest {
        settings.saveOriginals = false
        encounters.insert(tally)

        attachPhoto(ID, SOURCE, PhotoSource.CAMERA)

        assertEquals(0, gallery.calls)
        assertEquals(null, stored().galleryUri)
    }

    @Test
    fun `a photo from the gallery is never copied back into it`() = runTest {
        encounters.insert(tally)

        attachPhoto(ID, SOURCE, PhotoSource.GALLERY)

        assertEquals(0, gallery.calls)
    }

    @Test
    fun `an undecodable photo leaves the cat as it was and nothing in the gallery`() = runTest {
        encounters.insert(tally)
        resizer.undecodable += SOURCE

        assertEquals(AttachResult.Unreadable, attachPhoto(ID, SOURCE, PhotoSource.CAMERA))

        assertEquals(tally, stored())
        assertEquals(0, gallery.calls)
    }

    @Test
    fun `a cat that already has a photo keeps it and nothing is copied`() = runTest {
        val photo = tally.copy(photoPath = "own.jpg", thumbPath = "own_thumb.jpg")
        encounters.insert(photo)

        assertEquals(AttachResult.NotAttachable, attachPhoto(ID, SOURCE, PhotoSource.GALLERY))

        assertEquals(photo, stored())
        assertEquals(0, resizer.calls)
    }

    @Test
    fun `a soft-deleted cat is not given a photo, and not brought back`() = runTest {
        val deleted = tally.copy(deletedAt = NOW)
        encounters.insert(deleted)

        assertEquals(AttachResult.NotAttachable, attachPhoto(ID, SOURCE, PhotoSource.GALLERY))

        assertEquals(deleted, stored())
        assertEquals(0, resizer.calls)
    }

    @Test
    fun `an id nobody knows is not attachable`() = runTest {
        assertEquals(AttachResult.NotAttachable, attachPhoto(ID, SOURCE, PhotoSource.GALLERY))
        assertEquals(0, resizer.calls)
    }

    @Test
    fun `a cat deleted while its photo was being copied keeps no files from the attempt`() = runTest {
        encounters.insert(tally)
        resizer.duringStore = { encounters.softDelete(ID, NOW) }

        assertEquals(AttachResult.NotAttachable, attachPhoto(ID, SOURCE, PhotoSource.GALLERY))

        assertEquals(tally.copy(deletedAt = NOW), stored())
        assertEquals(listOf(FakeImageResizer.PHOTO_PATH, FakeImageResizer.THUMB_PATH), storage.deleted)
    }

    @Test
    fun `a write that fails removes the files it had written`() = runTest {
        encounters.insert(tally)
        encounters.attachPhotoShouldThrow = IllegalStateException("disk full")

        assertFailsWith<IllegalStateException> { attachPhoto(ID, SOURCE, PhotoSource.GALLERY) }

        assertEquals(listOf(FakeImageResizer.PHOTO_PATH, FakeImageResizer.THUMB_PATH), storage.deleted)
    }

    @Test
    fun `the same photo can go on two cats`() = runTest {
        encounters.insert(tally)
        encounters.insert(tally.copy(id = "cat-2"))

        assertEquals(AttachResult.Attached, attachPhoto(ID, SOURCE, PhotoSource.GALLERY))
        assertEquals(AttachResult.Attached, attachPhoto("cat-2", SOURCE, PhotoSource.GALLERY))
        assertTrue(storage.deleted.isEmpty())
    }

    private companion object {
        const val ID = "cat-1"
        const val SOURCE = "content://picker/1"
        val OCCURRED = Instant.parse("2026-09-21T10:00:00Z")
        val NOW = Instant.parse("2026-09-23T12:00:00Z")
    }
}
```

- [ ] **Step 3: Run** — expect a compile failure (`AttachPhoto` does not exist).

- [ ] **Step 4: Implement** — `AttachPhoto.kt`:

```kotlin
package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.PhotoStamp
import dev.catsradar.domain.platform.Digest
import dev.catsradar.domain.platform.GallerySaver
import dev.catsradar.domain.platform.IdGenerator
import dev.catsradar.domain.platform.ImageResizer
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.platform.StoredPhoto
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.SettingsRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/** Where a photo being attached came from: only a camera original is the gallery's to keep. */
enum class PhotoSource { CAMERA, GALLERY }

sealed interface AttachResult {
    data object Attached : AttachResult

    /** The image could not be decoded; the cat is unchanged. */
    data object Unreadable : AttachResult

    /** The cat is gone or already has a photo; it is unchanged and no file of the attempt is kept. */
    data object NotAttachable : AttachResult
}

@Suppress("LongParameterList") // one parameter per collaborator; a holder would exist only to lower the count
class AttachPhoto(
    private val encounterRepository: EncounterRepository,
    private val settingsRepository: SettingsRepository,
    private val imageResizer: ImageResizer,
    private val digest: Digest,
    private val gallerySaver: GallerySaver,
    private val photoStorage: PhotoStorage,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) {
    suspend operator fun invoke(encounterId: String, sourceUri: String, source: PhotoSource): AttachResult {
        val target = encounterRepository.observeById(encounterId).first()
        if (target == null || target.photoPath != null) return AttachResult.NotAttachable

        // Not the cat's id: an attempt that loses the row to another must remove only its own files.
        val baseName = idGenerator.newId()
        val stored = imageResizer.store(sourceUri, baseName) ?: return AttachResult.Unreadable
        val galleryUri = if (source == PhotoSource.CAMERA && settingsRepository.saveOriginalsToGallery().first()) {
            gallerySaver.save(sourceUri, "$baseName.jpg")
        } else {
            null
        }
        val stamp = PhotoStamp(
            photoPath = stored.photoPath,
            thumbPath = stored.thumbPath,
            galleryUri = galleryUri,
            sourceDigest = digest.sha256(sourceUri),
            updatedAt = clock.now(),
        )

        var attached = false
        try {
            attached = encounterRepository.attachPhoto(encounterId, stamp)
        } finally {
            if (!attached) withContext(NonCancellable) { discard(stored) }
        }
        return if (attached) AttachResult.Attached else AttachResult.NotAttachable
    }

    private suspend fun discard(stored: StoredPhoto) {
        photoStorage.delete(stored.photoPath)
        stored.thumbPath?.let { photoStorage.delete(it) }
    }
}
```

- [ ] **Step 5: Run** `AttachPhotoTest`, then the whole `:domain` and `:presentation` test tasks
  (the rename touches `LogPhoto`/`ImportPhotos` callers positionally; nothing else should move). Expected: PASS.

- [ ] **Step 6: Break it on purpose** — change the guard to `if (target == null) return …` and confirm
  *a cat that already has a photo keeps it* fails; remove `withContext(NonCancellable) { discard(stored) }`
  and confirm both file-removal tests fail. Restore.

- [ ] **Step 7: Commit** — `AttachPhoto: a photo for a cat logged without one`.

---

### Task 3: "With photo" counts the photo, not the kind

**Files:**
- Modify: `domain/src/commonMain/kotlin/dev/catsradar/domain/stats/StatsCalculator.kt:39`
- Test: `domain/src/commonTest/kotlin/dev/catsradar/domain/stats/StatsCalculatorTest.kt:68-74`

- [ ] **Step 1: Replace the test** `photos are counted apart from tallies` with:
```kotlin
    @Test
    fun `cats with a photo are counted however they were logged`() {
        val stats = stats(
            listOf(
                at(NOON),
                at(NOON - 1.hours, kind = EncounterKind.PHOTO).copy(photoPath = "taken.jpg"),
                at(NOON - 2.hours).copy(photoPath = "attached.jpg"),
            ),
        )

        assertEquals(3, stats.total)
        assertEquals(2, stats.withPhoto)
    }
```
- [ ] **Step 2: Run** — expect FAIL (`withPhoto` is 1).
- [ ] **Step 3: Implement** — `withPhoto = live.count { it.photoPath != null },` and drop the now-unused
  `EncounterKind` import if detekt flags it.
- [ ] **Step 4: Run** `StatsCalculatorTest` — PASS.
- [ ] **Step 5: Commit** — `"With photo" counts cats that have one, however they were logged`.

---

### Task 3b: A coat write that cannot undo a photo

Added during execution. `SetCoat` reads the row and writes it back whole with `update(target.copy(...))`.
A photo attached between that read and write — reachable in one tap once P1b puts both controls on the
detail screen — is overwritten with `null`, and so is a location the background worker attached in the
same gap. Same cure as `attachLocation` / `attachPhoto`: a guarded write of the coat column only.

**Files:**
- Modify: `EncounterDao.kt`, `EncounterRepository.kt`, `EncounterRepositoryImpl.kt`, `SetCoat.kt`
- Modify fakes: `FakeEncounterDao.kt`, domain and presentation `FakeEncounterRepository`, the three `:app` test fakes
- Test: `data/src/androidHostTest/kotlin/dev/catsradar/data/db/EncounterDaoSetCoatTest.kt` (create),
  `EncounterRepositoryImplTest.kt`, `domain/src/commonTest/kotlin/dev/catsradar/domain/usecase/SetCoatTest.kt` (create)

**Interfaces:**
- Produces: `EncounterDao.setCoat(id: String, coat: CatCoat?, updatedAt: Instant): Int` —
  `UPDATE encounters SET coat = :coat, updatedAt = :updatedAt WHERE id = :id AND deletedAt IS NULL`;
  `EncounterRepository.setCoat(id: String, coat: CatCoat?, updatedAt: Instant)` with KDoc
  `/** No-ops if the row was soft-deleted in the meantime; never resurrects it. */` (same as `attachLocation`);
  domain fake `var beforeSetCoat: suspend () -> Unit = {}` run inside its `setCoat` before it writes.

- [ ] **Step 1: DAO test** `EncounterDaoSetCoatTest` (same harness as `EncounterDaoAttachPhotoTest`):
  - `setCoatWritesOnlyTheCoatAndUpdatedAtOfALiveRow` — insert `fullEncounterEntity(id = "live")` (it has a
    photo, a location and `GINGER_WHITE`); `setCoat("live", CatCoat.BLACK, UPDATED)` returns 1 and the row
    equals `entity.copy(coat = CatCoat.BLACK, updatedAt = UPDATED)`.
  - `setCoatToNullClearsIt` — returns 1, row equals `entity.copy(coat = null, updatedAt = UPDATED)`.
  - `setCoatOnASoftDeletedRowNeitherResurrectsItNorChangesIt` — returns 0, `loadEvery()` equals `listOf(entity)`.
- [ ] **Step 2: Domain test** `SetCoatTest` (fakes from `domain/testing`, `FakeClock(NOW)`):
  - *setting a coat stamps it and nothing else* — whole-row `assertEquals(tally.copy(coat = GINGER, updatedAt = NOW), …)`;
  - *setting the coat that is already set writes nothing* — row unchanged, `updatedAt` included;
  - *a soft-deleted cat is not edited and not brought back*;
  - *a photo attached between the read and the write survives the coat* — `encounters.beforeSetCoat = {
    encounters.attachPhoto(ID, stamp) }`; after `setCoat(ID, GINGER)` the row has both `GINGER` and the stamp's
    `photoPath`.
- [ ] **Step 3: Run** — compile failures. **Implement** the DAO query (no comment needed beyond what
  `attachLocation`'s neighbour already says; if one is added it states the full-row hazard in one line),
  the repository method (`dao.setCoat(id, coat, updatedAt)`), and `SetCoat`:
  ```kotlin
      suspend operator fun invoke(encounterId: String, coat: CatCoat?) {
          val target = encounterRepository.observeById(encounterId).first() ?: return
          if (target.coat == coat) return
          encounterRepository.setCoat(encounterId, coat, clock.now())
      }
  ```
  keeping the existing KDoc and dropping the comment that only explained the soft-delete read.
  Fakes: domain and presentation fakes change only `coat` and `updatedAt` of a live row, in one
  `encounters.update { list -> list.map { … } }`; while there, rewrite both fakes' `attachPhoto` in the same
  single-`update` shape (Task 1 deferred minor). `FakeEncounterDao` records the call. `:app` fakes throw
  `NotImplementedError("unused by this test")`.
- [ ] **Step 4:** also fold in Task 1's deferred minors: `EncounterRepository.attachPhoto` KDoc becomes
  `/** True only when a live row without a photo was written; otherwise writes nothing. */`, and
  `EncounterDaoAttachPhotoTest` gains `attachPhotoOnAnUnknownIdWritesNothing` (returns 0).
- [ ] **Step 5: Run** the new tests, then `:domain`, `:data`, `:presentation` host tests and `:app:testDebugUnitTest`,
  plus detekt on those modules — PASS.
- [ ] **Step 6:** `data-model.md` *At the edges*: one sentence that the coat, too, is written column-only and
  only on a live row, so changing it cannot undo a photo or a location attached a moment earlier
  (`EncounterDaoSetCoatTest`). `coat.md` *At the edges*: "**A coat set while a photo or a location is being
  attached keeps both** — only the coat is written."
- [ ] **Step 7: Commit** — `SetCoat writes only the coat, so it cannot undo a photo attached meanwhile`.

---

### Task 4: P1a docs, gate and PR

**Files:**
- Modify: `docs/features/statistics.md:18`, `docs/features/data-model.md`

- [ ] **Step 1: `statistics.md`** — replace the "With photo" line with: "**With photo** — cats that have a
  photo of their own, whether it was taken, imported, or given later to a cat logged without one."
- [ ] **Step 2: `data-model.md`** —
  - in the field groups: "the cat (`coat`, which the user can change after creation, see `coat.md`);
    photo fields (…, covered in `photos.md`; set once — at creation, or later on a cat that had none)";
  - in *At the edges*, after the `softDelete` guard: "`attachPhoto` is guarded both ways at once: it
    writes only the photo columns, and only `WHERE deletedAt IS NULL AND photoPath IS NULL`, so giving
    a cat a photo can neither bring back a deleted one nor replace a photo it has
    (`EncounterDaoAttachPhotoTest`)."
  - *Where the code lives*: add `PhotoStamp.kt` beside `LocationStamp.kt`.
- [ ] **Step 3:** `./gradlew check` — green.
- [ ] **Step 4:** acceptance gate on the P1a criteria; `/code-review` on the diff; fix findings.
- [ ] **Step 5:** push `feature/attach-photo-data`, open the PR, map row P1a → `in-review`.

---

# P1b — Photo for a logged cat, on screen

Branch `feature/attach-photo-screen` from `main` once P1a is merged (or stacked on P1a's branch until then).

### Task 5: Detail state, intents, effects, mapper and Store

**Files:**
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/detail/EncounterDetailState.kt`,
  `EncounterDetailIntent.kt`, `EncounterDetailEffect.kt`, `EncounterDetailStateMapper.kt`, `EncounterDetailStore.kt`
- Modify: `presentation/src/commonTest/kotlin/dev/catsradar/presentation/counter/CounterStoreTestDoubles.kt`
  (`FakeImageResizer` gains `var storeDelay: Duration = Duration.ZERO`, awaited with `delay(storeDelay)` in `store`)
- Test: `presentation/src/commonTest/kotlin/dev/catsradar/presentation/detail/EncounterDetailStateMapperTest.kt`,
  `EncounterDetailStoreTest.kt`

**Interfaces:**
- Consumes: `AttachPhoto`, `PhotoSource`, `AttachResult` (Task 2).
- Produces:
  ```kotlin
  enum class AddPhoto { READY, ATTACHING }                       // EncounterDetailState.kt
  EncounterDetailState.Loaded(..., val addPhoto: AddPhoto? = null)
  EncounterDetailIntent.TakePhotoClicked, PickPhotoClicked, PhotoTaken(uri: String?), PhotoPicked(uri: String?)
  EncounterDetailEffect.OpenCamera, OpenPhotoPicker, PhotoNotAttached, DiscardCapture(uri: String)
  EncounterDetailStateMapper.map(encounter, today, attachingPhoto: Boolean = false)
  EncounterDetailStore(..., attachPhoto: AttachPhoto, ...)        // after setCoat
  ```

- [ ] **Step 1: Mapper tests.** In `an encounter with a fix maps every field…` add `addPhoto = AddPhoto.READY`
  to the expected `Loaded`. Add:
```kotlin
    @Test
    fun `a cat without a photo is offered one, and shows one being attached`() {
        val tally = encounterFixture("e1", OCCURRED)

        assertEquals(AddPhoto.READY, mapper.map(tally, today).addPhoto)
        assertEquals(AddPhoto.ATTACHING, mapper.map(tally, today, attachingPhoto = true).addPhoto)
    }

    @Test
    fun `a cat with a photo is never offered another, even while one is being attached`() {
        val photo = encounterFixture("e1", OCCURRED).copy(photoPath = "e1.jpg")

        assertEquals(null, mapper.map(photo, today).addPhoto)
        assertEquals(null, mapper.map(photo, today, attachingPhoto = true).addPhoto)
    }
```
- [ ] **Step 2: Run** — compile failure. **Implement:**

`EncounterDetailState.kt` — in `Loaded`, after `coat`:
```kotlin
        /** Null when the cat has a photo of its own, which is never replaced. */
        val addPhoto: AddPhoto? = null,
```
and at the bottom of the file:
```kotlin
enum class AddPhoto { READY, ATTACHING }
```
Mapper: `fun map(encounter: Encounter, today: LocalDate, attachingPhoto: Boolean = false)` and
```kotlin
            addPhoto = when {
                encounter.photoPath != null -> null
                attachingPhoto -> AddPhoto.ATTACHING
                else -> AddPhoto.READY
            },
```
Run the mapper test — PASS.

- [ ] **Step 3: Store tests.** Extend `newStore()` with
```kotlin
        attachPhoto = AttachPhoto(
            encounterRepository = repository,
            settingsRepository = FakeSettingsRepository(),
            imageResizer = resizer,
            digest = FakeDigest(),
            gallerySaver = FakeGallerySaver(),
            photoStorage = FakePhotoStorage(),
            idGenerator = FakeIdGenerator(),
            clock = clock,
        ),
```
with `private val resizer = FakeImageResizer()` as a field, and add:
```kotlin
    @Test
    fun `take a photo opens the camera and choose from gallery opens the picker`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.TakePhotoClicked)
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenCamera, awaitItem())
            store.dispatch(EncounterDetailIntent.PickPhotoClicked)
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenPhotoPicker, awaitItem())
        }
    }

    @Test
    fun `a cat that already has a photo opens neither`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED).copy(photoPath = "own.jpg"))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.TakePhotoClicked)
            store.dispatch(EncounterDetailIntent.PickPhotoClicked)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `a photo from the camera lands on the cat and its original is discarded`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoTaken(CAPTURE))
            runCurrent()
            assertEquals(EncounterDetailEffect.DiscardCapture(CAPTURE), awaitItem())
        }
        val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals("/data/photos/cat.jpg", state.photoPath)
        assertEquals(null, state.addPhoto)
    }

    @Test
    fun `a photo from the gallery lands on the cat and nothing is discarded`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoPicked(PICKED))
            runCurrent()
            expectNoEvents()
        }
        assertEquals("/data/photos/cat.jpg", assertIs<EncounterDetailState.Loaded>(store.state.value).photoPath)
    }

    @Test
    fun `a cancelled camera or picker changes nothing`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()
        val before = store.state.value

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoTaken(null))
            store.dispatch(EncounterDetailIntent.PhotoPicked(null))
            runCurrent()
            expectNoEvents()
        }
        assertEquals(before, store.state.value)
    }

    @Test
    fun `the offer shows the photo being attached until it lands`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        resizer.storeDelay = 1.seconds
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.PhotoPicked(PICKED))
        runCurrent()
        assertEquals(AddPhoto.ATTACHING, assertIs<EncounterDetailState.Loaded>(store.state.value).addPhoto)

        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(null, assertIs<EncounterDetailState.Loaded>(store.state.value).addPhoto)
    }

    @Test
    fun `an unreadable photo says so and the offer comes back`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        resizer.result = null
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoTaken(CAPTURE))
            runCurrent()
            assertEquals(EncounterDetailEffect.PhotoNotAttached, awaitItem())
            assertEquals(EncounterDetailEffect.DiscardCapture(CAPTURE), awaitItem())
        }
        assertEquals(AddPhoto.READY, assertIs<EncounterDetailState.Loaded>(store.state.value).addPhoto)
    }

    @Test
    fun `a failed write says the photo was not attached`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.attachPhotoShouldThrow = IllegalStateException("disk full")
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoPicked(PICKED))
            runCurrent()
            assertEquals(EncounterDetailEffect.PhotoNotAttached, awaitItem())
        }
        assertEquals(AddPhoto.READY, assertIs<EncounterDetailState.Loaded>(store.state.value).addPhoto)
    }
```
with `const val CAPTURE = "content://captures/1"` and `const val PICKED = "content://picker/1"` in the companion.

- [ ] **Step 4: Run** — compile failure. **Implement:**

`EncounterDetailIntent`:
```kotlin
    data object TakePhotoClicked : EncounterDetailIntent
    data object PickPhotoClicked : EncounterDetailIntent

    /** [uri] is null when the camera was cancelled. */
    data class PhotoTaken(val uri: String?) : EncounterDetailIntent

    /** [uri] is null when the picker was dismissed. */
    data class PhotoPicked(val uri: String?) : EncounterDetailIntent
```
`EncounterDetailEffect`:
```kotlin
    data object OpenCamera : EncounterDetailEffect
    data object OpenPhotoPicker : EncounterDetailEffect
    data object PhotoNotAttached : EncounterDetailEffect

    /** The camera's original at [uri] is no longer needed. */
    data class DiscardCapture(val uri: String) : EncounterDetailEffect
```
`EncounterDetailStore` — constructor gains `private val attachPhoto: AttachPhoto,` after `setCoat`; then:
```kotlin
    private var attachingPhoto = false

    // reduce(): encounter != null -> stateMapper.map(encounter, clock.today(timeZone), attachingPhoto)

    // handle():
            EncounterDetailIntent.TakePhotoClicked -> if (photoOffered()) emit(EncounterDetailEffect.OpenCamera)
            EncounterDetailIntent.PickPhotoClicked -> if (photoOffered()) emit(EncounterDetailEffect.OpenPhotoPicker)
            is EncounterDetailIntent.PhotoTaken -> onPhotoChosen(intent.uri, PhotoSource.CAMERA)
            is EncounterDetailIntent.PhotoPicked -> onPhotoChosen(intent.uri, PhotoSource.GALLERY)

    private fun photoOffered(): Boolean =
        (state.value as? EncounterDetailState.Loaded)?.addPhoto == AddPhoto.READY

    private suspend fun onPhotoChosen(uri: String?, source: PhotoSource) {
        if (uri == null) return
        showAttaching(true)
        var result: AttachResult? = null
        runWrite(onFailure = {}) { result = attachPhoto(encounterId, uri, source) }
        showAttaching(false)
        if (result == null || result == AttachResult.Unreadable) emit(EncounterDetailEffect.PhotoNotAttached)
        if (source == PhotoSource.CAMERA) emit(EncounterDetailEffect.DiscardCapture(uri))
    }

    private fun showAttaching(attaching: Boolean) {
        attachingPhoto = attaching
        setState { if (this is EncounterDetailState.Loaded) reduce(lastSeen) else this }
    }
```
`NotAttachable` stays silent on purpose: the cat was deleted or given a photo meanwhile, and the
screen already shows that.

- [ ] **Step 5: Run** the detail tests — PASS. Break on purpose: drop the `DiscardCapture` emit and
  confirm the camera test fails; restore.
- [ ] **Step 6: Commit** — `Detail Store offers a photo to a cat without one`.

---

### Task 6: The offer on screen

**Files:**
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/detail/EncounterDetailScreen.kt`
- Create: `ui/src/main/res/drawable/ic_photo_library.xml`
- Modify: `ui/src/main/res/values/strings.xml`, `ui/src/main/res/values-ru/strings.xml`

**Interfaces:**
- Consumes: `AddPhoto`, `Loaded.addPhoto` (Task 5).
- Produces: `EncounterDetailScreen(..., onTakePhotoClick: () -> Unit = {}, onPickPhotoClick: () -> Unit = {})`.

- [ ] **Step 1: Strings** (EN / RU), next to the other `detail_*`:
```xml
    <string name="detail_photo">Photo</string>
    <string name="detail_take_photo">Take a photo</string>
    <string name="detail_pick_photo">Choose from gallery</string>
    <string name="detail_photo_not_attached">Photo not attached</string>
```
```xml
    <string name="detail_photo">Фото</string>
    <string name="detail_take_photo">Сфотографировать</string>
    <string name="detail_pick_photo">Выбрать из галереи</string>
    <string name="detail_photo_not_attached">Фото не прикрепилось</string>
```
- [ ] **Step 2: Icon** `ic_photo_library.xml` — same shape of file as `ic_photo_camera.xml` (960 viewport,
  `translateY="960"` group, white fill), with the Material Symbols *photo_library* path:
  `M360-400h400L622-580l-92 120-62-80-108 140Zm-40 160q-33 0-56.5-23.5T240-320v-480q0-33 23.5-56.5T320-880h480q33 0 56.5 23.5T880-800v480q0 33-23.5 56.5T800-240H320Zm0-80h480v-480H320v480ZM160-80q-33 0-56.5-23.5T80-160v-560h80v560h560v80H160Zm160-720v480-480Z`
- [ ] **Step 3: The composable.** In `LoadedDetail`, replace the `if (photoPath != null) { AsyncImage … }`
  block with:
```kotlin
        val photoPath = state.photoPath
        val addPhoto = state.addPhoto
        if (photoPath != null) {
            AsyncImage(
                model = photoPath,
                contentDescription = stringResource(R.string.detail_photo_description),
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.extraLarge),
                contentScale = ContentScale.Crop,
            )
        } else if (addPhoto != null) {
            AddPhotoCard(addPhoto, onTakePhotoClick = onTakePhotoClick, onPickPhotoClick = onPickPhotoClick)
        }
```
and add, below `WhereCard`:
```kotlin
@Composable
private fun AddPhotoCard(
    addPhoto: AddPhoto,
    modifier: Modifier = Modifier,
    onTakePhotoClick: () -> Unit = {},
    onPickPhotoClick: () -> Unit = {},
) {
    val enabled = addPhoto == AddPhoto.READY
    SectionCard(R.string.detail_photo, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = onTakePhotoClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                ButtonLabel(R.drawable.ic_photo_camera, R.string.detail_take_photo)
            }
            OutlinedButton(onClick = onPickPhotoClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                ButtonLabel(R.drawable.ic_photo_library, R.string.detail_pick_photo)
            }
            if (addPhoto == AddPhoto.ATTACHING) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ButtonLabel(@DrawableRes iconRes: Int, @StringRes textRes: Int) {
    Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Text(text = stringResource(textRes), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
}
```
Thread `onTakePhotoClick` / `onPickPhotoClick` through `EncounterDetailScreen` and `LoadedDetail` as
`{}`-defaulted parameters after `onCoatClick`.
- [ ] **Step 4: Previews** — `sampleNoLocation` gains `addPhoto = AddPhoto.READY`; add
  `EncounterDetailScreenAttachingPreview` rendering `sampleNoLocation.copy(addPhoto = AddPhoto.ATTACHING)`.
- [ ] **Step 5:** `./gradlew :ui:check` — green (detekt, Lint `MissingTranslation`).
- [ ] **Step 6: Commit** — `Detail screen offers a photo from the camera or the gallery`.

---

### Task 7: `:app` wiring

**Files:**
- Create: `app/src/main/kotlin/dev/catsradar/app/navigation/PhotoLaunchers.kt`
- Create: `app/src/main/kotlin/dev/catsradar/app/navigation/EncounterDetailDestination.kt`
- Modify: `app/src/main/kotlin/dev/catsradar/app/navigation/CounterEffectHandler.kt` (move the four photo
  `fun interface`s out), `CounterDestination.kt` (use the shared launcher), `CatsRadarNavHost.kt` (drop the
  private destination), `app/src/main/kotlin/dev/catsradar/app/di/DomainModule.kt`, `PresentationModule.kt`,
  `app/src/main/kotlin/dev/catsradar/app/photo/CaptureTarget.kt` (KDoc no longer names only `LogPhoto`)
- Test: `app/src/test/kotlin/dev/catsradar/app/navigation/EncounterDetailEffectHandlerTest.kt`

**Interfaces:**
- Produces (all `internal`, package `dev.catsradar.app.navigation`):
  `fun interface CameraLauncher`, `PhotoFailureReporter`, `CaptureDiscarder`, `PhotoPickerLauncher` (moved, unchanged);
  `@Composable fun rememberCameraLauncher(onResult: (String?) -> Unit): CameraLauncher`;
  `@Composable fun rememberSinglePhotoPicker(onResult: (String?) -> Unit): PhotoPickerLauncher`;
  `@Composable fun rememberPhotoFailureReporter(@StringRes messageRes: Int): PhotoFailureReporter`;
  `@Composable fun rememberCaptureDiscarder(): CaptureDiscarder`;
  `fun handleEncounterDetailEffect(effect, onNavigateBack, cameraLauncher, photoPickerLauncher, photoFailureReporter, captureDiscarder)`.

- [ ] **Step 1: Failing handler test** — `EncounterDetailEffectHandlerTest.kt`:
```kotlin
package dev.catsradar.app.navigation

import dev.catsradar.presentation.detail.EncounterDetailEffect
import org.junit.Test
import kotlin.test.assertEquals

class EncounterDetailEffectHandlerTest {
    private val calls = mutableListOf<String>()

    private fun handle(effect: EncounterDetailEffect) = handleEncounterDetailEffect(
        effect,
        onNavigateBack = { calls += "back" },
        cameraLauncher = { calls += "camera" },
        photoPickerLauncher = { calls += "picker" },
        photoFailureReporter = { calls += "failure" },
        captureDiscarder = { uri -> calls += "discard $uri" },
    )

    @Test
    fun `each effect reaches exactly its own collaborator`() {
        handle(EncounterDetailEffect.NavigateBack)
        handle(EncounterDetailEffect.OpenCamera)
        handle(EncounterDetailEffect.OpenPhotoPicker)
        handle(EncounterDetailEffect.PhotoNotAttached)
        handle(EncounterDetailEffect.DiscardCapture("content://captures/1"))

        assertEquals(listOf("back", "camera", "picker", "failure", "discard content://captures/1"), calls)
    }
}
```
- [ ] **Step 2: Run** — compile failure.
- [ ] **Step 3: `PhotoLaunchers.kt`** — move `CameraLauncher`, `PhotoFailureReporter`, `CaptureDiscarder`,
  `PhotoPickerLauncher` here verbatim (keep the `CameraLauncher` KDoc), plus:
```kotlin
@Composable
internal fun rememberCameraLauncher(onResult: (String?) -> Unit): CameraLauncher {
    val context = LocalContext.current
    // Saveable: the process can die while a camera is in front, and its result says nothing about
    // where it wrote.
    val pending = rememberSaveable(saver = PendingCaptures.Saver) { PendingCaptures() }
    val resultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val target = pending.answered()
        onResult(target.takeIf { saved })
        if (!saved && target != null) CaptureTarget.discard(context, target)
    }
    return remember(resultLauncher, context, pending) {
        CameraLauncher {
            val target = CaptureTarget.newUri(context)
            pending.launched(target.toString())
            resultLauncher.launch(target)
        }
    }
}

@Composable
internal fun rememberSinglePhotoPicker(onResult: (String?) -> Unit): PhotoPickerLauncher {
    val resultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        onResult(uri?.toString())
    }
    return remember(resultLauncher) {
        PhotoPickerLauncher {
            resultLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

@Composable
internal fun rememberPhotoFailureReporter(@StringRes messageRes: Int): PhotoFailureReporter {
    val context = LocalContext.current
    return remember(context, messageRes) {
        PhotoFailureReporter { Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show() }
    }
}

@Composable
internal fun rememberCaptureDiscarder(): CaptureDiscarder {
    val context = LocalContext.current
    return remember(context) { CaptureDiscarder { uri -> CaptureTarget.discard(context, uri) } }
}
```
`CounterDestination`: delete its private `rememberCameraLauncher` and `rememberPhotoFailureReporter`;
use `rememberCameraLauncher { uri -> store.dispatch(CounterIntent.PhotoCaptured(uri)) }`,
`rememberPhotoFailureReporter(R.string.counter_photo_not_saved)` and `rememberCaptureDiscarder()`.
- [ ] **Step 4: `EncounterDetailDestination.kt`** — move `EncounterDetailDestination` out of
  `CatsRadarNavHost.kt` (now `internal`), and add the handler:
```kotlin
@Suppress("LongParameterList") // one collaborator per effect the screen has to carry out
internal fun handleEncounterDetailEffect(
    effect: EncounterDetailEffect,
    onNavigateBack: () -> Unit,
    cameraLauncher: CameraLauncher,
    photoPickerLauncher: PhotoPickerLauncher,
    photoFailureReporter: PhotoFailureReporter,
    captureDiscarder: CaptureDiscarder,
) {
    when (effect) {
        EncounterDetailEffect.NavigateBack -> onNavigateBack()
        EncounterDetailEffect.OpenCamera -> cameraLauncher.launch()
        EncounterDetailEffect.OpenPhotoPicker -> photoPickerLauncher.launch()
        EncounterDetailEffect.PhotoNotAttached -> photoFailureReporter.report()
        is EncounterDetailEffect.DiscardCapture -> captureDiscarder.discard(effect.uri)
    }
}

@Composable
internal fun EncounterDetailDestination(
    key: EncounterDetail,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    val store = koinViewModel<EncounterDetailStore> { parametersOf(key.id) }
    val state by store.state.collectAsStateWithLifecycle()
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)
    val cameraLauncher = rememberCameraLauncher { uri -> store.dispatch(EncounterDetailIntent.PhotoTaken(uri)) }
    val photoPicker = rememberSinglePhotoPicker { uri -> store.dispatch(EncounterDetailIntent.PhotoPicked(uri)) }
    val photoFailureReporter = rememberPhotoFailureReporter(R.string.detail_photo_not_attached)
    val captureDiscarder = rememberCaptureDiscarder()
    LaunchedEffect(store, cameraLauncher, photoPicker, photoFailureReporter, captureDiscarder) {
        store.effects.collect { effect ->
            handleEncounterDetailEffect(
                effect,
                onNavigateBack = { currentOnNavigateBack() },
                cameraLauncher = cameraLauncher,
                photoPickerLauncher = photoPicker,
                photoFailureReporter = photoFailureReporter,
                captureDiscarder = captureDiscarder,
            )
        }
    }
    EncounterDetailScreen(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        onDeleteClick = { store.dispatch(EncounterDetailIntent.DeleteClicked) },
        onUndoClick = { store.dispatch(EncounterDetailIntent.UndoClicked) },
        onCoatClick = { coat -> store.dispatch(EncounterDetailIntent.CoatPicked(coat)) },
        onTakePhotoClick = { store.dispatch(EncounterDetailIntent.TakePhotoClicked) },
        onPickPhotoClick = { store.dispatch(EncounterDetailIntent.PickPhotoClicked) },
    )
}
```
- [ ] **Step 5: Koin** — `DomainModule`: `factoryOf(::AttachPhoto)`; `PresentationModule`: pass
  `attachPhoto = get(),` to `EncounterDetailStore`. If a Koin `verify()`/module test exists in `:app`, run it.
- [ ] **Step 6: Run** `:app:testDebugUnitTest` — PASS; then `./gradlew check` — green.
- [ ] **Step 7: Commit** — `Detail screen takes or picks a photo for a cat without one`.

---

### Task 8: P1b docs, device check, gate and PR

- [ ] **Step 1: `docs/features/photos.md`** — new section *Giving a cat a photo later* (what the detail
  offers; camera vs gallery and the gallery setting; only the photo columns change, never time, place,
  coat, `kind` or `origin`; fresh file names and why; unreadable → "Photo not attached"; deleted or
  already-photographed meanwhile → nothing kept; the same photo on two cats is allowed and a later
  import of it is skipped). *Where the code lives*: `AttachPhoto.kt`, `PhotoStamp.kt`. *Not built yet*:
  replacing or removing a photo.
- [ ] **Step 2: `docs/features/encounter-detail.md`** — the photo area (photo, or the two buttons with
  progress while attaching); rewrite the stale *Not built yet* (photo and coat are built; left: place name,
  map, replacing a photo); *Where the code lives*: `EncounterDetailDestination.kt`, `PhotoLaunchers.kt`.
- [ ] **Step 3: Device** — on the shared emulator (`emulator-5554`, see memory): tally a cat, open it,
  *Take a photo* → it lands, Encounters shows the thumbnail; another tally, *Choose from gallery* → lands,
  gallery gains nothing; cancel both → nothing; Statistics "With photo" counts both.
- [ ] **Step 4:** acceptance gate on the P1b criteria; `/code-review`; fix findings; `./gradlew check`.
- [ ] **Step 5:** push `feature/attach-photo-screen`, open the PR, map row P1b → `in-review`.
