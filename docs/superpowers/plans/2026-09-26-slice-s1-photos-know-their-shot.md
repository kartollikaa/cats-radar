# Slice S1 — Photos know their shot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `EncounterPhoto` carries `shotId`, stored in database v5 by an automatic migration proven on every kind of photo; nothing sets it to anything but null yet.

**Architecture:** `EncounterPhoto.shotId: String?` has no default, so every construction site states it; `shot` is `shotId ?: id`. `EncounterPhotoEntity` gains a nullable, indexed `shotId` column mapped both ways. `CatsDatabase` goes to version 5 with `AutoMigration(from = 4, to = 5)`, which Room generates as an `ALTER TABLE … ADD COLUMN` plus a `CREATE INDEX` — no rebuild. The archive format stays 4: its records do not carry the field until S2.

**Tech Stack:** Kotlin Multiplatform, Room 3 (`androidx.room3`) with the bundled SQLite driver, `MigrationTestHelper`, Robolectric host tests.

**Spec:** `docs/superpowers/specs/2026-09-25-several-cats-per-photo-design.md` § The model, § Storage, § Keeping export, import and the migration whole. Map: S1 in `docs/tbd/decompositions/2026-09-25-several-cats-per-photo.md`. Criteria: `several-cats-s1.md` in the acceptance directory.

## Global Constraints

- `CatsDatabase` version 5; `data/schemas/dev.catsradar.data.db.CatsDatabase/5.json` exported and copied byte-for-byte to `data/src/androidHostTest/assets/dev.catsradar.data.db.CatsDatabase/5.json`.
- `EncounterPhoto.shotId` has **no default value** in `:domain`; `EncounterPhotoEntity.shotId` has none either.
- Every production writer writes `shotId = null` in this slice; no behaviour a user can see changes.
- `BACKUP_FORMAT_VERSION` stays 4 and `EncounterPhotoRecord` is unchanged (S2 owns both).
- Comments: default none (`docs/rules/code-commenting-standards.md`); KDoc only where the signature cannot say it.
- Fresh test evidence: `--rerun` after every Gradle task; read JUnit XML under `<module>/build/test-results/`.

---

### Task 1: The field and every place that builds a photo

**Files:**
- Modify: `domain/src/commonMain/kotlin/dev/catsradar/domain/model/EncounterPhoto.kt`
- Modify (state `shotId = null`): `domain/…/usecase/LogPhoto.kt`, `domain/…/usecase/AttachPhoto.kt`, `domain/…/usecase/ImportPhotos.kt`, `data/src/commonMain/kotlin/dev/catsradar/data/backup/CarriedPhoto.kt`, `data/…/backup/BackupRecords.kt` (`EncounterPhotoRecord.toDomain`)
- Modify (fixtures and doubles): `domain/src/commonTest/…/testing/Fixtures.kt`, `domain/…/model/GalleryLinkTest.kt`, `domain/…/usecase/AttachPhotoTest.kt`, `data/src/commonTest/…/repository/EncounterFixtures.kt`, `data/src/commonTest/…/backup/CarriedPhotoTest.kt`, `data/src/androidHostTest/…/backup/ZipBackupPhotoListTest.kt`, `…/backup/ZipBackupReaderOlderFormatTest.kt`, `…/db/PhotosMigrationTest.kt`, `presentation/src/commonTest/…/encounters/EncountersTestDoubles.kt`, `app/src/test/…/navigation/PhotoViewerEntryTest.kt`
- Create: `domain/src/commonTest/kotlin/dev/catsradar/domain/model/EncounterPhotoShotTest.kt`

**Interfaces:**
- Produces: `EncounterPhoto(…, shotId: String?, …)` — positional after `addedAt`; `val EncounterPhoto.shot: String` (member property).

- [ ] **Step 1: Write the failing test**

```kotlin
package dev.catsradar.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class EncounterPhotoShotTest {
    @Test
    fun aPhotoThatStartsItsShotIsItsOwnShot() {
        assertEquals("p1", photo(id = "p1", shotId = null).shot)
    }

    @Test
    fun aPhotoThatRepeatsAShotBelongsToTheShotsFirstPhoto() {
        assertEquals("p1", photo(id = "p2", shotId = "p1").shot)
    }

    private fun photo(id: String, shotId: String?) = EncounterPhoto(
        id = id,
        encounterId = "cat-$id",
        photoPath = "$id.jpg",
        thumbPath = null,
        galleryUri = null,
        sourceMediaUri = null,
        sourceDigest = null,
        deviceId = "device",
        addedAt = Instant.fromEpochMilliseconds(1),
        shotId = shotId,
    )
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :domain:testAndroidHostTest --tests 'dev.catsradar.domain.model.EncounterPhotoShotTest' --console=plain`
Expected: compilation failure, `No parameter with name 'shotId'` / `Unresolved reference 'shot'`.

- [ ] **Step 3: Add the field**

In `EncounterPhoto`, after `addedAt`:

```kotlin
    val addedAt: Instant,
    /** The first photo of the shot this one repeats, when several cats share one photo; null on that first photo. */
    val shotId: String?,
) {
    /** The id every photo of one shot has in common. */
    val shot: String get() = shotId ?: id
}
```

- [ ] **Step 4: State `shotId = null` at every construction site**

Every production call listed under **Files** gets `shotId = null` as its last named argument. Test fixtures that build a photo through a helper take `shotId: String? = null` as a helper parameter and pass it through; direct constructions in tests pass `shotId = null`. Find every site with:

Run: `grep -rn "EncounterPhoto(" --include='*.kt' domain data presentation app | grep -v "class EncounterPhoto\|EncounterPhotoEntity\|EncounterPhotoRecord"`
Expected: every hit is followed by a `shotId =` argument (the compiler enforces the production ones).

- [ ] **Step 5: Run the domain, data, presentation and app unit tests**

Run: `./gradlew :domain:testAndroidHostTest --rerun :data:testAndroidHostTest --rerun :presentation:testAndroidHostTest --rerun :app:testDebugUnitTest --rerun --console=plain`
Expected: BUILD SUCCESSFUL; `EncounterPhotoShotTest` 2 passed.

- [ ] **Step 6: Commit**

```bash
git add -A domain data presentation app
git commit -m "A photo knows the shot it belongs to"
```

---

### Task 2: The column, its index and database v5

**Files:**
- Modify: `data/src/commonMain/kotlin/dev/catsradar/data/db/EncounterPhotoEntity.kt`
- Modify: `data/src/commonMain/kotlin/dev/catsradar/data/repository/EncounterMapper.kt` (both photo mappers)
- Modify: `data/src/commonMain/kotlin/dev/catsradar/data/db/CatsDatabase.kt` (version 5, `AutoMigration(from = 4, to = 5)`)
- Modify: `data/src/androidHostTest/kotlin/dev/catsradar/data/db/TestCatsDatabase.kt` (version 5)
- Modify: `data/src/androidHostTest/kotlin/dev/catsradar/data/db/EncounterEntityFixtures.kt` (`shotId` parameter)
- Create: `data/schemas/dev.catsradar.data.db.CatsDatabase/5.json` (generated by the build), `data/src/androidHostTest/assets/dev.catsradar.data.db.CatsDatabase/5.json` (byte copy)
- Test: `data/src/commonTest/…/repository/EncounterMapperTest.kt`, `data/src/androidHostTest/…/db/EncounterDaoPhotosTest.kt`, `…/db/DatabaseSchemaTest.kt`, `…/db/CatsDatabaseMigrationTest.kt`

**Interfaces:**
- Consumes: `EncounterPhoto.shotId`, `EncounterPhoto.shot` (Task 1).
- Produces: `EncounterPhotoEntity(…, addedAt: Instant, shotId: String?)`; index `index_encounter_photos_shotId`; database version 5.

- [ ] **Step 1: Write the failing mapper and DAO tests**

`EncounterMapperTest` — the photo rows of a shot of three cats round-trip exactly:

```kotlin
    @Test
    fun thePhotosOfOneShotKeepTheirShotBothWays() {
        val rows = listOf(
            photoRow(id = "p1", encounterId = "ginger", shotId = null),
            photoRow(id = "p2", encounterId = "ginger-too", shotId = "p1"),
            photoRow(id = "p3", encounterId = "unseen", shotId = "p1"),
        )

        assertEquals(rows, rows.map { it.toDomain().toEntity() })
        assertEquals(listOf(null, "p1", "p1"), rows.map { it.toDomain().shotId })
        assertEquals(listOf("p1", "p1", "p1"), rows.map { it.toDomain().shot })
    }

    private fun photoRow(id: String, encounterId: String, shotId: String?) = EncounterPhotoEntity(
        id = id,
        encounterId = encounterId,
        photoPath = "$id.jpg",
        thumbPath = "${id}_thumb.jpg",
        galleryUri = null,
        sourceMediaUri = null,
        sourceDigest = "d-shot",
        deviceId = "device",
        addedAt = Instant.fromEpochMilliseconds(1_000),
        shotId = shotId,
    )
```

`EncounterDaoPhotosTest.eachWayAPhotoRowIsWrittenKeepsItsShot` — `insertWithPhotos` for three cats of one shot (`p1` with `shotId = null`, `p2` and `p3` with `"p1"`), `addPhoto` for a fourth live cat (`p4`, `"p1"`), `addPhotos` for a fifth (`p5`, `"p1"`); `loadEvery()` returns each photo with the `shotId` it was written with. The entity fixture in `EncounterEntityFixtures.kt` takes `shotId: String? = null`.

- [ ] **Step 2: Write the failing schema and migration tests**

`DatabaseSchemaTest.encounterPhotosTableHasEveryColumnWithExpectedNullability` gains `"shotId" to false`; a new `encounterPhotosAreIndexedByShot` asserts `pragma_index_list('encounter_photos')` created indices (`origin = 'c'`) are exactly `index_encounter_photos_encounterId`, `index_encounter_photos_shotId`, `index_encounter_photos_sourceDigest`.

`CatsDatabaseMigrationTest`:

```kotlin
    @Test
    fun versionFourBecomesFiveKeepingEveryCatAndPhotoWithNoShot() = runTest {
        val (catsBefore, photosBefore) = helper.createDatabase(4).use { v4 ->
            versionFourRows.forEach { v4.execSQL(it) }
            v4.rows("SELECT * FROM encounters ORDER BY id") to v4.rows("SELECT * FROM encounter_photos ORDER BY id")
        }

        helper.runMigrationsAndValidate(5).use { v5 ->
            assertEquals(catsBefore, v5.rows("SELECT * FROM encounters ORDER BY id"))
            assertEquals(photosBefore, v5.rows("SELECT $V4_PHOTO_COLUMNS FROM encounter_photos ORDER BY id"))
            assertEquals(
                photosBefore.map { null },
                v5.rows("SELECT shotId FROM encounter_photos ORDER BY id").map { it.single() },
            )
        }
    }

```

`versionFourRows` inserts, into a v4 database, the kinds of photo the v3 → v4 matrix holds: a camera photo with a gallery original, a picked item, a photo with no thumbnail, a soft-deleted cat with a photo, another install's cat, and a tally with none — one `INSERT INTO encounters` and one `INSERT INTO encounter_photos` per photographed cat, with the v4 column lists. `V4_PHOTO_COLUMNS` is the v4 photo column list (`id, encounterId, photoPath, thumbPath, galleryUri, sourceMediaUri, sourceDigest, deviceId, addedAt`); `SELECT *` on v4 returns the same order. The `rows` helper is copied from `PhotosMigrationTest`.

`PhotosMigrationTest` (it holds the pre-v3 photographed cat and the builder already):

```kotlin
    @Test
    fun theAppsOwnBuilderBringsAPhotographedCatFromVersionsOneAndTwoToFive() = runTest {
        listOf(1, 2).forEach { version ->
            helper.createDatabase(version).use { it.execSQL(PHOTOGRAPHED_BEFORE_VERSION_THREE) }
            val database = catsDatabaseBuilder(instrumentation.targetContext, file.absolutePath).build()
            try {
                val cat = EncounterRepositoryImpl(database.encounterDao()).loadEvery().single()
                assertEquals(listOf("old.jpg" to null), cat.photos.map { it.photoPath to it.shotId }, "from v$version")
            } finally {
                database.close()
                file.delete()
            }
        }
    }
```

The v3 builder case is the existing `theAppsOwnBuilderOpensAVersionThreeFileWithEveryCatsPhotoAndPurgesThemWithTheirCat`, which now reaches v5 and still purges the photo rows with their cat; its `cameraPhoto` states `shotId = null`.

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew :data:testAndroidHostTest --tests 'dev.catsradar.data.*' --console=plain`
Expected: compilation failure (`EncounterPhotoEntity` has no `shotId`; `createDatabase(5)`-less schema).

- [ ] **Step 4: Add the column, the index, the mapping and version 5**

```kotlin
    indices = [Index("encounterId"), Index("sourceDigest"), Index("shotId")],
)
data class EncounterPhotoEntity(
    …
    val addedAt: Instant,
    val shotId: String?,
)
```

Both photo mappers in `EncounterMapper.kt` pass `shotId = shotId`. `CatsDatabase`: `version = 5`, `autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 4, to = 5)]`. `TestCatsDatabase`: `version = 5`.

- [ ] **Step 5: Export schema 5 and copy it**

Run: `./gradlew :data:kspAndroidMain --console=plain` (or any `:data` compile that runs KSP), then
`cp data/schemas/dev.catsradar.data.db.CatsDatabase/5.json data/src/androidHostTest/assets/dev.catsradar.data.db.CatsDatabase/5.json`
Expected: `5.json` lists `shotId` (`TEXT`, not null false) and `index_encounter_photos_shotId`; `4.json` unchanged (`git diff --stat data/schemas` shows only the new file).

- [ ] **Step 6: Read the migration Room generated**

Run: `find data/build/generated -name 'CatsDatabase_AutoMigration_4_5_Impl*' -exec cat {} \;`
Expected: exactly an `ALTER TABLE \`encounter_photos\` ADD COLUMN \`shotId\` TEXT DEFAULT NULL` and a `CREATE INDEX … index_encounter_photos_shotId …`; no `CREATE TABLE`, `DROP TABLE`, `RENAME` or `INSERT`. Record the SQL in the acceptance evidence. If it rebuilds a table, stop: the spec's claim that v4 → v5 needs no foreign-keys-on test no longer holds.

- [ ] **Step 7: Run the data tests**

Run: `./gradlew :data:testAndroidHostTest --rerun --console=plain`
Expected: BUILD SUCCESSFUL; the new tests pass; `SchemaAssetSyncTest` passes with `5.json`.

- [ ] **Step 8: Commit**

```bash
git add -A data
git commit -m "Database v5 stores each photo's shot"
```

- [ ] **Step 9: See every new test fail on purpose**

Commit first (done). For each mutant: edit, run the named class, read its JUnit XML, `git checkout -- <file>`.

| Mutant | Edit | Must fail |
|---|---|---|
| entity → domain drops it | `EncounterPhotoEntity.toDomain()` passes `shotId = null` | `EncounterMapperTest.thePhotosOfOneShotKeepTheirShotBothWays`, `EncounterDaoPhotosTest.eachWayAPhotoRowIsWrittenKeepsItsShot` |
| domain → entity drops it | `EncounterPhoto.toEntity()` passes `shotId = null` | same two |
| migration loses a row | in the migration test only, pass `listOf(object : Migration(4, 5) { … the generated SQL plus DELETE FROM encounter_photos WHERE id = 'camera' })` to `runMigrationsAndValidate` | `versionFourBecomesFive…` |
| migration invents a shot | the same stand-in migration, with `UPDATE encounter_photos SET shotId = id` instead of the delete | `versionFourBecomesFive…` |
| index missing | drop `Index("shotId")` from the entity (and let KSP regenerate) | `DatabaseSchemaTest.encounterPhotosAreIndexedByShot` |

Expected: each listed test red on its mutant, green again after the restore.

---

### Task 3: Docs, check, device, review

**Files:**
- Modify: `docs/features/data-model.md` (§ Photos: the shot; the version paragraph for 5)
- Modify: `docs/tbd/decompositions/2026-09-25-several-cats-per-photo.md` (S1 status, decision log)

- [ ] **Step 1: Update `data-model.md`**

In § Photos, after the fields sentence: each photo also names its **shot** — `shotId`, the first photo of the shot a photo repeats, null on that first photo — so the cats of one photo can be found together; nothing writes a shot of several cats yet. After the version 4 paragraph: version 5 adds `shotId` and its index by an automatic migration that only adds them; `CatsDatabaseMigrationTest.versionFourBecomesFiveKeepingEveryCatAndPhotoWithNoShot` migrates every kind of photo and finds each one unchanged and starting its own shot. Name `index_encounter_photos_shotId` nowhere else.

- [ ] **Step 2: Full check**

Run: `./gradlew check --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Upgrade in place on the emulator**

Build and install `origin/main`'s debug APK on the shared emulator, log a tally, a camera photo, a gallery photo, delete one cat, start and stop a walk; install this branch's debug APK over it (`adb install -r`); open Encounters, a photographed cat's detail and its viewer, Statistics and Map: every cat and photo is there, the deleted one is not. `adb shell run-as com.kartollika.catsradar sqlite3 databases/cats_radar.db 'PRAGMA user_version'` → `5` (skip the query if the image has no `sqlite3`; the screens are the evidence).

- [ ] **Step 4: Review and gate**

`/code-review` on the PR; fix findings. Acceptance gate against `several-cats-s1.md`. Update the map: S1 `in-review`, a decision-log line with anything learned.

- [ ] **Step 5: Commit and open the PR**

```bash
git add docs
git commit -m "Docs: each photo names its shot; database v5"
```

Open the PR from `tech/photos-know-their-shot` with the repo's PR flow; the description links the spec and the map and lists the mutants and their failing tests.
