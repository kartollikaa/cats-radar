package dev.catsradar.data.db

import android.content.Context
import androidx.room3.RoomRawQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class DatabaseSchemaTest {
    private lateinit var database: TestCatsDatabase

    @Before
    fun createDatabase() {
        database = buildInMemoryCatsDatabase(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun databaseOpensWithEveryTable() = runTest {
        val tables = database.schemaProbeDao().tableNames().toSet()
        assertEquals(setOf("encounters", "encounter_photos", "place_cells", "walks", "track_points"), tables)
    }

    @Test
    fun encountersTableHasEveryColumnWithExpectedNullability() = runTest {
        val expectedNotNull = mapOf(
            "id" to true,
            "occurredAt" to true,
            "tzOffsetMinutes" to true,
            "kind" to true,
            "origin" to true,
            "coat" to false,
            "lat" to false,
            "lon" to false,
            "accuracyMeters" to false,
            "locationSource" to true,
            "locationFixedAt" to false,
            "geohash" to false,
            "placeCellId" to false,
            "deviceId" to true,
            "createdAt" to true,
            "updatedAt" to true,
            "deletedAt" to false,
        )

        val columns = database.schemaProbeDao().tableInfo(RoomRawQuery("PRAGMA table_info(`encounters`)"))
        val actualNotNull = columns.associate { it.name to (it.notnull != 0) }

        assertEquals(expectedNotNull.keys, actualNotNull.keys)
        assertEquals(expectedNotNull, actualNotNull)
    }

    @Test
    fun encounterPhotosTableHasEveryColumnWithExpectedNullability() = runTest {
        val expectedNotNull = mapOf(
            "id" to true,
            "encounterId" to true,
            "photoPath" to true,
            "thumbPath" to false,
            "galleryUri" to false,
            "sourceMediaUri" to false,
            "sourceDigest" to false,
            "deviceId" to true,
            "addedAt" to true,
            "shotId" to true,
        )

        val columns = database.schemaProbeDao().tableInfo(RoomRawQuery("PRAGMA table_info(`encounter_photos`)"))

        assertEquals(expectedNotNull, columns.associate { it.name to (it.notnull != 0) })
    }

    @Test
    fun encounterPhotosAreIndexedByTheirCatTheirDigestAndTheirShot() = runTest {
        val indices = database.schemaProbeDao().indexList(
            RoomRawQuery("SELECT name FROM pragma_index_list('encounter_photos') WHERE origin = 'c' ORDER BY name"),
        )

        assertEquals(
            listOf(
                "index_encounter_photos_encounterId",
                "index_encounter_photos_shotId",
                "index_encounter_photos_sourceDigest",
            ),
            indices.map { it.name },
        )
    }
}
