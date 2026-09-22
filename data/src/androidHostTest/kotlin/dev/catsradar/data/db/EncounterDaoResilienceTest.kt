package dev.catsradar.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.data.repository.EncounterRepositoryImpl
import dev.catsradar.domain.model.EncounterKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.time.Instant

private const val OUT_OF_RANGE_TZ_OFFSET_MINUTES = 9999
private const val MAX_TZ_OFFSET_MINUTES = 1080

/** Covers a bad row's blast radius: it must degrade in place, not take the whole table down with it. */
@RunWith(AndroidJUnit4::class)
class EncounterDaoResilienceTest {
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
    fun purgeKeepsARowExactlyAtTheCutoff() = runTest {
        val cutoff = Instant.parse("2026-08-01T00:00:00Z")
        val onTheBoundary = fullEncounterEntity(id = "on-boundary", deletedAt = cutoff)
        dao.insert(onTheBoundary)

        val purged = dao.purgeDeletedBefore(cutoff)

        assertEquals(0, purged)
        assertEquals(1, database.schemaProbeDao().encounterRowCount(onTheBoundary.id))
    }

    @Test
    fun reSoftDeletingAnAlreadyDeletedRowDoesNotRestartItsPurgeClock() = runTest {
        val entity = fullEncounterEntity(id = "twice-deleted")
        dao.insert(entity)
        dao.softDelete(entity.id, Instant.parse("2026-01-01T00:00:00Z"))

        dao.softDelete(entity.id, Instant.parse("2026-09-21T00:00:00Z"))

        val cutoff = Instant.parse("2026-02-01T00:00:00Z")
        val purged = dao.purgeDeletedBefore(cutoff)
        assertEquals(1, purged)
    }

    @Test
    fun aRowWithACorruptTzOffsetOrEnumIsDegradedNotLostAndDoesNotBlockOtherRows() = runTest {
        val schemaProbeDao = database.schemaProbeDao()
        schemaProbeDao.insertRawEncounter(
            id = "corrupt-tz",
            occurredAt = 1_000L,
            tzOffsetMinutes = OUT_OF_RANGE_TZ_OFFSET_MINUTES,
            kind = "TALLY",
        )
        schemaProbeDao.insertRawEncounter(
            id = "corrupt-kind",
            occurredAt = 2_000L,
            tzOffsetMinutes = 0,
            kind = "SOME_FUTURE_KIND",
        )
        val healthyEntity = fullEncounterEntity(id = "healthy")
        dao.insert(healthyEntity)

        // Goes through the repository, not the raw DAO: that's where toDomain() -- and the
        // Encounter.init guard the reviewer's crash came from -- actually runs.
        val encounters = EncounterRepositoryImpl(dao).observeAll().first().associateBy { it.id }

        assertEquals(setOf("corrupt-tz", "corrupt-kind", "healthy"), encounters.keys)
        assertEquals(MAX_TZ_OFFSET_MINUTES, encounters.getValue("corrupt-tz").tzOffsetMinutes)
        assertEquals(EncounterKind.TALLY, encounters.getValue("corrupt-kind").kind)

        val healthy = encounters.getValue("healthy")
        assertEquals(healthyEntity.occurredAt, healthy.occurredAt)
        assertEquals(healthyEntity.tzOffsetMinutes, healthy.tzOffsetMinutes)
        assertEquals(healthyEntity.kind, healthy.kind)
        assertEquals(healthyEntity.coat, healthy.coat)
        assertEquals(healthyEntity.deviceId, healthy.deviceId)
        assertEquals(healthyEntity.lat, healthy.lat)
        assertEquals(healthyEntity.lon, healthy.lon)
        assertEquals(healthyEntity.createdAt, healthy.createdAt)
        assertEquals(healthyEntity.updatedAt, healthy.updatedAt)
    }
}
