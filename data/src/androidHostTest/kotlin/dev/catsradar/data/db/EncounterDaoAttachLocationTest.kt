package dev.catsradar.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.domain.model.LocationSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** Covers EncounterDao.attachLocation's guards: a deleted row and a located one are never written. */
@RunWith(AndroidJUnit4::class)
class EncounterDaoAttachLocationTest {
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
    fun attachLocationUpdatesOnlyTheLocationColumnsOfALiveRow() = runTest {
        val entity = fullEncounterEntity(id = "live").withoutLocation()
        dao.insert(entity)

        val changed = dao.attachLocation(
            id = entity.id,
            lat = 1.23,
            lon = 4.56,
            accuracyMeters = 9f,
            locationSource = LocationSource.LAST_KNOWN,
            locationFixedAt = Instant.parse("2026-09-21T09:00:00Z"),
            geohash = "u4pruydq",
            placeCellId = "u4pruy",
            updatedAt = Instant.parse("2026-09-21T09:05:00Z"),
        )

        val updated = dao.observeById(entity.id).first()!!.encounter
        assertEquals(1, changed)
        assertEquals(expected = 1.23, actual = updated.lat)
        assertEquals(expected = 4.56, actual = updated.lon)
        assertEquals(9f, updated.accuracyMeters)
        assertEquals(LocationSource.LAST_KNOWN, updated.locationSource)
        assertEquals(Instant.parse("2026-09-21T09:00:00Z"), updated.locationFixedAt)
        assertEquals("u4pruydq", updated.geohash)
        assertEquals("u4pruy", updated.placeCellId)
        assertEquals(Instant.parse("2026-09-21T09:05:00Z"), updated.updatedAt)
        assertEquals(entity.kind, updated.kind)
        assertEquals(entity.occurredAt, updated.occurredAt)
    }

    @Test
    fun attachLocationOnASoftDeletedRowNeitherResurrectsItNorStampsIt() = runTest {
        // The state a fresh tally is in when its undo lands before the fix does: no location
        // yet, then soft-deleted.
        val entity = fullEncounterEntity(id = "undone", deletedAt = Instant.parse("2026-09-21T08:00:00Z"))
            .withoutLocation()
        dao.insert(entity)

        val changed = dao.attachLocation(
            id = entity.id,
            lat = 1.23,
            lon = 4.56,
            accuracyMeters = 9f,
            locationSource = LocationSource.CURRENT_FIX,
            locationFixedAt = Instant.parse("2026-09-21T09:00:00Z"),
            geohash = "u4pruydq",
            placeCellId = "u4pruy",
            updatedAt = Instant.parse("2026-09-21T09:05:00Z"),
        )

        val rawDeletedAt = database.schemaProbeDao().rawEncounterDeletedAt(entity.id)
        assertEquals(0, changed)
        assertEquals(1, database.schemaProbeDao().encounterRowCount(entity.id))
        assertNull(database.schemaProbeDao().rawEncounterLat(entity.id))
        assertEquals("NONE", database.schemaProbeDao().rawEncounterLocationSource(entity.id))
        assertEquals(entity.deletedAt, rawDeletedAt?.let(Instant::fromEpochMilliseconds))
        assertNull(dao.observeById(entity.id).first()?.encounter)
    }

    @Test
    fun attachLocationLeavesACatThatAlreadyHasALocationAsItWas() = runTest {
        val entity = fullEncounterEntity(id = "located")
            .copy(locationSource = LocationSource.EXIF, accuracyMeters = null)
        dao.insert(entity)

        val changed = dao.attachLocation(
            id = entity.id,
            lat = 1.23,
            lon = 4.56,
            accuracyMeters = 9f,
            locationSource = LocationSource.CURRENT_FIX,
            locationFixedAt = Instant.parse("2026-09-21T09:00:00Z"),
            geohash = "u4pruydq",
            placeCellId = "u4pruy",
            updatedAt = Instant.parse("2026-09-21T09:05:00Z"),
        )

        assertEquals(0, changed)
        assertEquals(entity, dao.observeById(entity.id).first()!!.encounter)
    }

    private fun EncounterEntity.withoutLocation() = copy(
        lat = null,
        lon = null,
        accuracyMeters = null,
        locationSource = LocationSource.NONE,
        locationFixedAt = null,
        geohash = null,
        placeCellId = null,
    )
}
