package dev.catsradar.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.domain.model.PlaceStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class PlaceCellDaoTest {
    private lateinit var database: CatsDatabase
    private lateinit var dao: PlaceCellDao

    @Before
    fun createDatabase() {
        database = buildInMemoryCatsDatabase(ApplicationProvider.getApplicationContext<Context>())
        dao = database.placeCellDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun upsertingTheSameCellIdTwiceKeepsOneRowWithTheSecondWritesValues() = runTest {
        val cellId = "ucfv0h"
        dao.upsert(pendingPlaceCellEntity(cellId))

        val resolved = pendingPlaceCellEntity(cellId).copy(
            status = PlaceStatus.RESOLVED,
            countryName = "Russia",
            locality = "Moscow",
            attempts = 1,
        )
        dao.upsert(resolved)

        val rows = dao.observeAll().first()
        assertEquals(1, rows.size)
        assertEquals(resolved, rows.single())
        assertEquals("RESOLVED", database.schemaProbeDao().rawPlaceCellStatus(cellId))
    }

    @Test
    fun loadPageReturnsOnlyCellsWithTheRequestedStatus() = runTest {
        dao.upsert(pendingPlaceCellEntity("pending-1"))
        dao.upsert(pendingPlaceCellEntity("pending-2"))
        dao.upsert(pendingPlaceCellEntity("resolved-1").copy(status = PlaceStatus.RESOLVED))

        val page = dao.loadPage(PlaceStatus.PENDING, limit = 10, offset = 0)

        assertEquals(setOf("pending-1", "pending-2"), page.map { it.cellId }.toSet())
    }
}
