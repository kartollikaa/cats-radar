package dev.catsradar.data.db

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CatsDatabaseMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        databaseClass = CatsDatabase::class,
        driver = BundledSQLiteDriver(),
        file = instrumentation.targetContext.getDatabasePath("cats-radar-migration-test"),
    )

    @Test
    fun schemaVersionOneOpensCleanly() = runTest {
        helper.createDatabase(1).close()
    }

    @Test
    fun versionOneBecomesTwoKeepingEveryCatAndAddingNoWalks() = runTest {
        helper.createDatabase(1).use { v1 ->
            v1.execSQL(
                "INSERT INTO encounters (id, occurredAt, tzOffsetMinutes, kind, origin, locationSource, " +
                    "deviceId, createdAt, updatedAt) VALUES ('cat', 1, 0, 'TALLY', 'APP', 'NONE', 'device', 1, 1)",
            )
        }

        helper.runMigrationsAndValidate(2).use { v2 ->
            assertEquals(1L, v2.count("encounters"))
            assertEquals(0L, v2.count("walks"))
            assertEquals(0L, v2.count("track_points"))
        }
    }

    @Test
    fun versionTwoBecomesThreeKeepingEveryCatWithNoPickedGalleryItem() = runTest {
        helper.createDatabase(2).use { v2 ->
            v2.execSQL(
                "INSERT INTO encounters (id, occurredAt, tzOffsetMinutes, kind, origin, locationSource, " +
                    "deviceId, createdAt, updatedAt) VALUES ('cat', 1, 0, 'PHOTO', 'GALLERY', 'NONE', 'device', 1, 1)",
            )
        }

        helper.runMigrationsAndValidate(3).use { v3 ->
            assertEquals(1L, v3.count("encounters"))
            v3.prepare("SELECT sourceMediaUri FROM encounters WHERE id = 'cat'").use { statement ->
                statement.step()
                assertTrue(statement.isNull(0), "a cat from before the column has no picked gallery item")
            }
        }
    }

    @Test
    fun versionFourBecomesFiveKeepingEveryCatAndPhotoWithNoShot() = runTest {
        val (catsBefore, photosBefore) = helper.createDatabase(4).use { v4 ->
            versionFourRows.forEach { v4.execSQL(it) }
            v4.rows("SELECT * FROM encounters ORDER BY id") to v4.rows("SELECT * FROM encounter_photos ORDER BY id")
        }

        helper.runMigrationsAndValidate(5, listOf(MigrationFrom4To5)).use { v5 ->
            assertEquals(catsBefore, v5.rows("SELECT * FROM encounters ORDER BY id"))
            assertEquals(photosBefore, v5.rows("SELECT $V4_PHOTO_COLUMNS FROM encounter_photos ORDER BY id"))
            assertEquals(
                photosBefore.map { null },
                v5.rows("SELECT shotId FROM encounter_photos ORDER BY id").map { it.single() },
            )
        }
    }

    @Test
    fun aPhotoWhoseCatIsGoneDoesNotStopTheMigrationToFive() = runTest {
        helper.createDatabase(4).use { v4 ->
            v4.execSQL(
                V4_PHOTO + "('orphan', 'no-such-cat', 'orphan.jpg', NULL, NULL, NULL, NULL, 'this-install', 1)",
            )
        }

        helper.runMigrationsAndValidate(5, listOf(MigrationFrom4To5)).use { v5 ->
            assertEquals(listOf(listOf("orphan", null)), v5.rows("SELECT id, shotId FROM encounter_photos"))
        }
    }

    @Test
    fun versionFiveBecomesSixNamingEveryPhotosShot() = runTest {
        val before = helper.createDatabase(5).use { v5 ->
            versionFiveRows.forEach { v5.execSQL(it) }
            v5.rows("SELECT $V4_PHOTO_COLUMNS, shotId FROM encounter_photos ORDER BY id")
        }

        helper.runMigrationsAndValidate(6, listOf(MigrationFrom5To6)).use { v6 ->
            assertEquals(
                before.map { row -> row.dropLast(1) + (row.last() ?: row.first()) },
                v6.rows("SELECT $V4_PHOTO_COLUMNS, shotId FROM encounter_photos ORDER BY id"),
            )
            assertEquals(
                listOf("1"),
                v6.rows("SELECT \"notnull\" FROM pragma_table_info('encounter_photos') WHERE name = 'shotId'")
                    .map { it.single() },
            )
            assertEquals(
                listOf(
                    "index_encounter_photos_encounterId",
                    "index_encounter_photos_shotId",
                    "index_encounter_photos_sourceDigest",
                ),
                v6.rows("SELECT name FROM pragma_index_list('encounter_photos') WHERE origin = 'c' ORDER BY name")
                    .map { it.single() },
            )
            assertEquals(2L, v6.count("encounters"))
        }
    }

    private fun SQLiteConnection.count(table: String): Long =
        prepare("SELECT COUNT(*) FROM $table").use { statement ->
            statement.step()
            statement.getLong(0)
        }

    private fun SQLiteConnection.rows(sql: String): List<List<String?>> = prepare(sql).use { statement ->
        buildList { while (statement.step()) add(statement.row()) }
    }

    private fun SQLiteStatement.row(): List<String?> =
        (0 until getColumnCount()).map { if (isNull(it)) null else getText(it) }

    private companion object {
        const val V4_PHOTO_COLUMNS =
            "id, encounterId, photoPath, thumbPath, galleryUri, sourceMediaUri, sourceDigest, deviceId, addedAt"

        private const val V4_CAT = "INSERT INTO encounters (id, occurredAt, tzOffsetMinutes, kind, origin, coat, " +
            "lat, lon, accuracyMeters, locationSource, locationFixedAt, geohash, placeCellId, deviceId, createdAt, " +
            "updatedAt, deletedAt) VALUES "
        private const val V4_PHOTO = "INSERT INTO encounter_photos ($V4_PHOTO_COLUMNS) VALUES "
        private const val V5_PHOTO = "INSERT INTO encounter_photos ($V4_PHOTO_COLUMNS, shotId) VALUES "

        val versionFiveRows = listOf(
            V4_CAT + "('alone', 1000, 0, 'PHOTO', 'CAMERA', 'GINGER', NULL, NULL, NULL, 'NONE', NULL, NULL, NULL, " +
                "'this-install', 1001, 1002, NULL)",
            V5_PHOTO + "('alone', 'alone', 'alone.jpg', 'alone_thumb.jpg', NULL, NULL, 'd-alone', 'this-install', " +
                "1001, NULL)",
            V4_CAT + "('with-two', 2000, 0, 'PHOTO', 'GALLERY', NULL, NULL, NULL, NULL, 'NONE', NULL, NULL, NULL, " +
                "'this-install', 2001, 2002, NULL)",
            V5_PHOTO + "('with-two', 'with-two', 'first.jpg', NULL, NULL, 'content://media/external/images/media/2', " +
                "'d-first', 'this-install', 2001, 'with-two')",
            V5_PHOTO + "('with-two-second', 'with-two', 'second.jpg', 'second_thumb.jpg', NULL, NULL, 'd-second', " +
                "'other-install', 2500, 'alone')",
            V5_PHOTO + "('orphan', 'no-such-cat', 'orphan.jpg', NULL, NULL, NULL, NULL, 'this-install', 3001, NULL)",
        )

        val versionFourRows = listOf(
            V4_CAT + "('camera', 1000, 180, 'PHOTO', 'CAMERA', 'GINGER', 55.75, 37.62, 12.5, 'EXIF', 1000, " +
                "'ucfv0hg7', 'ucfv0h', 'this-install', 1001, 1002, NULL)",
            V4_PHOTO + "('camera', 'camera', 'camera.jpg', 'camera_thumb.jpg', " +
                "'content://media/external/images/media/1', NULL, 'd-camera', 'this-install', 1001)",
            V4_CAT + "('attached', 2000, 0, 'TALLY', 'APP', NULL, NULL, NULL, NULL, 'NONE', NULL, NULL, NULL, " +
                "'this-install', 2001, 5002, NULL)",
            V4_PHOTO + "('attached', 'attached', 'attached.jpg', 'attached_thumb.jpg', NULL, " +
                "'content://media/external/images/media/2', 'd-attached', 'this-install', 2001)",
            V4_PHOTO + "('attached-second', 'attached', 'attached-2.jpg', 'attached-2_thumb.jpg', NULL, " +
                "'content://media/external/images/media/4', 'd-attached-2', 'this-install', 2500)",
            V4_CAT + "('no-thumb', 3000, -300, 'PHOTO', 'GALLERY', NULL, NULL, NULL, NULL, 'NONE', NULL, NULL, NULL, " +
                "'this-install', 3001, 3002, NULL)",
            V4_PHOTO + "('no-thumb', 'no-thumb', 'no-thumb.jpg', NULL, NULL, NULL, 'd-no-thumb', 'this-install', 3001)",
            V4_CAT + "('deleted', 4000, 0, 'PHOTO', 'CAMERA', 'BLACK', NULL, NULL, NULL, 'NONE', NULL, NULL, NULL, " +
                "'this-install', 4001, 4002, 4500)",
            V4_PHOTO + "('deleted', 'deleted', 'deleted.jpg', 'deleted_thumb.jpg', NULL, NULL, 'd-deleted', " +
                "'this-install', 4001)",
            V4_CAT + "('foreign', 5000, 60, 'PHOTO', 'CAMERA', NULL, NULL, NULL, NULL, 'NONE', NULL, NULL, NULL, " +
                "'other-install', 5001, 5002, NULL)",
            V4_PHOTO + "('foreign', 'foreign', 'foreign.jpg', 'foreign_thumb.jpg', " +
                "'content://media/external/images/media/9', NULL, 'd-foreign', 'other-install', 5001)",
            V4_CAT + "('tally', 6000, 0, 'TALLY', 'WIDGET', NULL, NULL, NULL, NULL, 'NONE', NULL, NULL, NULL, " +
                "'this-install', 6001, 6002, NULL)",
        )
    }
}
