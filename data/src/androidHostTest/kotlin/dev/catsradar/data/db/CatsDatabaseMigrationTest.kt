package dev.catsradar.data.db

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
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

    private fun SQLiteConnection.count(table: String): Long =
        prepare("SELECT COUNT(*) FROM $table").use { statement ->
            statement.step()
            statement.getLong(0)
        }
}
