package dev.catsradar.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.domain.model.PlaceCellAssignment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class EncounterDaoSetPlaceCellTest {
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

    private fun unplaced(id: String) =
        fullEncounterEntity(id = id, sourceDigest = "digest-$id").copy(geohash = null, placeCellId = null)

    @Test
    fun setPlaceCellsWritesTheGeohashAndCellAndNothingElse() = runTest {
        val entity = unplaced("placed")
        dao.insert(entity)

        dao.setPlaceCells(listOf(PlaceCellAssignment(entity.id, entity.lat!!, entity.lon!!, "ucfv0hg7", "ucfv0h")))

        assertEquals(
            entity.copy(geohash = "ucfv0hg7", placeCellId = "ucfv0h"),
            dao.observeById(entity.id).first(),
        )
    }

    @Test
    fun setPlaceCellsLeavesARowThatHasMovedSinceAlone() = runTest {
        val moved = unplaced("moved")
        val still = unplaced("still")
        dao.insert(moved)
        dao.insert(still)

        dao.setPlaceCells(
            listOf(
                PlaceCellAssignment(moved.id, 41.39864, 2.17842, "sp3e986k", "sp3e98"),
                PlaceCellAssignment(still.id, still.lat!!, still.lon!!, "ucfv0hg7", "ucfv0h"),
            ),
        )

        assertEquals(moved, dao.observeById(moved.id).first())
        assertEquals("ucfv0h", dao.observeById(still.id).first()!!.placeCellId)
    }

    @Test
    fun setPlaceCellsAlsoWritesASoftDeletedRow() = runTest {
        val deleted = fullEncounterEntity(deletedAt = Instant.parse("2026-09-21T08:00:00Z"))
            .copy(geohash = null, placeCellId = null)
        dao.insert(deleted)

        dao.setPlaceCells(listOf(PlaceCellAssignment(deleted.id, deleted.lat!!, deleted.lon!!, "ucfv0hg7", "ucfv0h")))

        assertEquals("ucfv0h", dao.loadEvery().single().placeCellId)
    }
}
