package dev.catsradar.data.db

import androidx.room3.testing.MigrationTestHelper
import androidx.room3.useReaderConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.catsradar.data.repository.EncounterRepositoryImpl
import dev.catsradar.domain.model.EncounterPhoto
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class PhotosMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val file = instrumentation.targetContext.getDatabasePath("cats-radar-photos-migration-test")

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        databaseClass = CatsDatabase::class,
        driver = BundledSQLiteDriver(),
        file = file,
    )

    @Test
    fun versionThreeBecomesFourMovingEveryCatsPhotoIntoARowOfItsOwn() = runTest {
        val before = helper.createDatabase(3).use { v3 ->
            versionThreeCats.forEach { v3.execSQL(it) }
            v3.rows("SELECT $CAT_COLUMNS FROM encounters ORDER BY id")
        }

        helper.runMigrationsAndValidate(4, listOf(MigrationFrom3To4)).use { v4 ->
            assertEquals(expectedPhotoRows, v4.rows("SELECT $PHOTO_COLUMNS FROM encounter_photos ORDER BY id"))
            assertEquals(before, v4.rows("SELECT $CAT_COLUMNS FROM encounters ORDER BY id"))
        }
    }

    // Room turns foreign keys on only after migrating, so only a connection that has them on shows the cascade.
    @Test
    fun theMigrationKeepsEveryPhotoOnAConnectionWithForeignKeysOn() = runTest {
        helper.createDatabase(3).use { v3 -> versionThreeCats.forEach { v3.execSQL(it) } }

        BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            connection.execSQL("PRAGMA foreign_keys = ON")
            connection.execSQL("BEGIN")
            MigrationFrom3To4.migrate(connection)
            connection.execSQL("COMMIT")

            assertEquals(expectedPhotoRows, connection.rows("SELECT $PHOTO_COLUMNS FROM encounter_photos ORDER BY id"))
        }
    }

    @Test
    fun versionFourHasNoPhotoColumnOnItsCatsAndNoDigestIndexThere() = runTest {
        helper.createDatabase(3).close()

        helper.runMigrationsAndValidate(4, listOf(MigrationFrom3To4)).use { v4 ->
            val columns = v4.rows("SELECT name FROM pragma_table_info('encounters')").map { it.single() }
            assertEquals(emptyList(), columns.filter { it in PHOTO_FIELDS })
            val indices = v4.rows(
                "SELECT name FROM pragma_index_list('encounters') WHERE origin = 'c'"
            ).map { it.single() }
            assertEquals(listOf("index_encounters_deletedAt_occurredAt"), indices)
        }
    }

    @Test
    fun aPhotographedCatFromVersionOneReachesVersionFourWithItsPhotoAsARow() = runTest {
        helper.createDatabase(1).use { it.execSQL(PHOTOGRAPHED_BEFORE_VERSION_THREE) }

        helper.runMigrationsAndValidate(4, listOf(MigrationFrom3To4)).use { v4 ->
            assertEquals(listOf(oldPhotoRow), v4.rows("SELECT $OLD_PHOTO_COLUMNS FROM encounter_photos"))
        }
    }

    @Test
    fun aPhotographedCatFromVersionTwoReachesVersionFourWithItsPhotoAsARow() = runTest {
        helper.createDatabase(2).use { it.execSQL(PHOTOGRAPHED_BEFORE_VERSION_THREE) }

        helper.runMigrationsAndValidate(4, listOf(MigrationFrom3To4)).use { v4 ->
            assertEquals(listOf(oldPhotoRow), v4.rows("SELECT $OLD_PHOTO_COLUMNS FROM encounter_photos"))
        }
    }

    @Test
    fun theAppsOwnBuilderOpensAVersionThreeFileWithEveryCatsPhotoAndPurgesThemWithTheirCat() = runTest {
        helper.createDatabase(3).use { v3 -> versionThreeCats.forEach { v3.execSQL(it) } }

        val database = catsDatabaseBuilder(instrumentation.targetContext, file.absolutePath).build()
        try {
            val cats = EncounterRepositoryImpl(database.encounterDao()).loadEvery().associateBy { it.id }
            assertEquals(
                mapOf(
                    "attached" to listOf("attached.jpg"),
                    "camera" to listOf("camera.jpg"),
                    "deleted" to listOf("deleted.jpg"),
                    "foreign" to listOf("foreign.jpg"),
                    "no-thumb" to listOf("no-thumb.jpg"),
                    "orphan-columns" to emptyList(),
                    "tally" to emptyList(),
                ),
                cats.mapValues { (_, cat) -> cat.photos.map { it.photoPath } }.toSortedMap(),
            )
            assertEquals(cameraPhoto, cats.getValue("camera").cover)

            assertEquals(1, database.encounterDao().purgeDeletedBefore(Instant.parse("2027-01-01T00:00:00Z")))

            val photoCats = database.useReaderConnection { connection ->
                connection.usePrepared("SELECT encounterId FROM encounter_photos ORDER BY encounterId") { statement ->
                    buildList { while (statement.step()) add(statement.getText(0)) }
                }
            }
            assertEquals(listOf("attached", "camera", "foreign", "no-thumb"), photoCats)
        } finally {
            database.close()
        }
    }

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
                instrumentation.targetContext.deleteDatabase(file.name)
            }
        }
    }

    private fun SQLiteConnection.rows(sql: String): List<List<String?>> = prepare(sql).use { statement ->
        buildList { while (statement.step()) add(statement.row()) }
    }

    private fun SQLiteStatement.row(): List<String?> =
        (0 until getColumnCount()).map { if (isNull(it)) null else getText(it) }

    private companion object {
        val PHOTO_FIELDS = listOf("photoPath", "thumbPath", "galleryUri", "sourceMediaUri", "sourceDigest")

        const val CAT_COLUMNS = "id, occurredAt, tzOffsetMinutes, kind, origin, coat, lat, lon, accuracyMeters, " +
            "locationSource, locationFixedAt, geohash, placeCellId, deviceId, createdAt, updatedAt, deletedAt"
        const val PHOTO_COLUMNS =
            "id, encounterId, photoPath, thumbPath, galleryUri, sourceMediaUri, sourceDigest, deviceId, addedAt"
        const val OLD_PHOTO_COLUMNS =
            "id, encounterId, photoPath, thumbPath, galleryUri, sourceDigest, deviceId, addedAt"

        private const val V3_INSERT = "INSERT INTO encounters (id, occurredAt, tzOffsetMinutes, kind, origin, coat, " +
            "photoPath, thumbPath, galleryUri, sourceMediaUri, sourceDigest, lat, lon, accuracyMeters, " +
            "locationSource, locationFixedAt, geohash, placeCellId, deviceId, createdAt, updatedAt, deletedAt) VALUES "

        val versionThreeCats = listOf(
            V3_INSERT + "('camera', 1000, 180, 'PHOTO', 'CAMERA', 'GINGER', 'camera.jpg', 'camera_thumb.jpg', " +
                "'content://media/external/images/media/1', NULL, 'd-camera', 55.75, 37.62, 12.5, 'EXIF', 1000, " +
                "'ucfv0hg7', 'ucfv0h', 'this-install', 1001, 1002, NULL)",
            V3_INSERT + "('attached', 2000, 0, 'TALLY', 'APP', NULL, 'attached.jpg', 'attached_thumb.jpg', NULL, " +
                "'content://media/external/images/media/2', 'd-attached', NULL, NULL, NULL, 'NONE', NULL, NULL, " +
                "NULL, 'this-install', 2001, 5002, NULL)",
            V3_INSERT + "('no-thumb', 3000, -300, 'PHOTO', 'GALLERY', NULL, 'no-thumb.jpg', NULL, NULL, NULL, " +
                "'d-no-thumb', NULL, NULL, NULL, 'NONE', NULL, NULL, NULL, 'this-install', 3001, 3002, NULL)",
            V3_INSERT + "('deleted', 4000, 0, 'PHOTO', 'CAMERA', 'BLACK', 'deleted.jpg', 'deleted_thumb.jpg', NULL, " +
                "NULL, 'd-deleted', NULL, NULL, NULL, 'NONE', NULL, NULL, NULL, 'this-install', 4001, 4002, 4500)",
            V3_INSERT + "('foreign', 5000, 60, 'PHOTO', 'CAMERA', NULL, 'foreign.jpg', 'foreign_thumb.jpg', " +
                "'content://media/external/images/media/9', NULL, 'd-foreign', NULL, NULL, NULL, 'NONE', NULL, NULL, " +
                "NULL, 'other-install', 5001, 5002, NULL)",
            V3_INSERT + "('tally', 6000, 0, 'TALLY', 'WIDGET', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, " +
                "NULL, 'NONE', NULL, NULL, NULL, 'this-install', 6001, 6002, NULL)",
            V3_INSERT + "('orphan-columns', 7000, 0, 'TALLY', 'APP', NULL, NULL, 'orphan_thumb.jpg', " +
                "'content://media/external/images/media/3', NULL, 'd-orphan', NULL, NULL, NULL, 'NONE', NULL, NULL, " +
                "NULL, 'this-install', 7001, 7002, NULL)",
        )

        val expectedPhotoRows = listOf(
            listOf(
                "attached", "attached", "attached.jpg", "attached_thumb.jpg", null,
                "content://media/external/images/media/2", "d-attached", "this-install", "2001",
            ),
            listOf(
                "camera", "camera", "camera.jpg", "camera_thumb.jpg", "content://media/external/images/media/1",
                null, "d-camera", "this-install", "1001",
            ),
            listOf(
                "deleted", "deleted", "deleted.jpg", "deleted_thumb.jpg", null, null, "d-deleted", "this-install",
                "4001",
            ),
            listOf(
                "foreign", "foreign", "foreign.jpg", "foreign_thumb.jpg", "content://media/external/images/media/9",
                null, "d-foreign", "other-install", "5001",
            ),
            listOf("no-thumb", "no-thumb", "no-thumb.jpg", null, null, null, "d-no-thumb", "this-install", "3001"),
        )

        val cameraPhoto = EncounterPhoto(
            id = "camera",
            encounterId = "camera",
            photoPath = "camera.jpg",
            thumbPath = "camera_thumb.jpg",
            galleryUri = "content://media/external/images/media/1",
            sourceMediaUri = null,
            sourceDigest = "d-camera",
            deviceId = "this-install",
            addedAt = Instant.fromEpochMilliseconds(1001),
            shotId = null,
        )

        const val PHOTOGRAPHED_BEFORE_VERSION_THREE = "INSERT INTO encounters (id, occurredAt, tzOffsetMinutes, " +
            "kind, origin, photoPath, thumbPath, galleryUri, sourceDigest, locationSource, deviceId, createdAt, " +
            "updatedAt) VALUES ('old', 1, 0, 'PHOTO', 'CAMERA', 'old.jpg', 'old_thumb.jpg', " +
            "'content://media/external/images/media/5', 'd-old', 'NONE', 'device', 7, 8)"

        val oldPhotoRow = listOf(
            "old",
            "old",
            "old.jpg",
            "old_thumb.jpg",
            "content://media/external/images/media/5",
            "d-old",
            "device",
            "7",
        )
    }
}
