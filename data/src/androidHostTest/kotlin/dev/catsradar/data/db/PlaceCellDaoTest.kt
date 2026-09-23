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
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class PlaceCellDaoTest {
    private lateinit var database: TestCatsDatabase
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
    fun upsertPreservesLastAttemptAtAndResolvedAtThroughTheDatabase() = runTest {
        val cellId = "resolved-with-timestamps"
        val resolved = pendingPlaceCellEntity(cellId).copy(
            status = PlaceStatus.RESOLVED,
            attempts = 1,
            lastAttemptAt = Instant.parse("2026-09-20T08:00:00Z"),
            resolvedAt = Instant.parse("2026-09-20T08:05:00Z"),
        )

        dao.upsert(resolved)

        assertEquals(resolved, dao.loadById(cellId))
    }

    @Test
    fun loadPageReturnsOnlyCellsWithTheRequestedStatus() = runTest {
        dao.upsert(pendingPlaceCellEntity("pending-1"))
        dao.upsert(pendingPlaceCellEntity("pending-2"))
        dao.upsert(pendingPlaceCellEntity("resolved-1").copy(status = PlaceStatus.RESOLVED))

        val page = dao.loadPage(PlaceStatus.PENDING, afterCellId = null, limit = 10)

        assertEquals(setOf("pending-1", "pending-2"), page.map { it.cellId }.toSet())
    }

    @Test
    fun loadPageOrdersByCellIdSoConsecutivePagesNeitherSkipNorRepeat() = runTest {
        dao.upsert(pendingPlaceCellEntity("charlie"))
        dao.upsert(pendingPlaceCellEntity("alpha"))
        dao.upsert(pendingPlaceCellEntity("bravo"))

        val firstPage = dao.loadPage(PlaceStatus.PENDING, afterCellId = null, limit = 2)
        val secondPage = dao.loadPage(PlaceStatus.PENDING, afterCellId = "bravo", limit = 2)

        assertEquals(listOf("alpha", "bravo"), firstPage.map { it.cellId })
        assertEquals(listOf("charlie"), secondPage.map { it.cellId })
    }

    @Test
    fun theNextPageStartsAfterTheLastCellSeenEvenWhenEarlierOnesStoppedBeingPending() = runTest {
        listOf("alpha", "bravo", "charlie").forEach { dao.upsert(pendingPlaceCellEntity(it)) }
        val firstPage = dao.loadPage(PlaceStatus.PENDING, afterCellId = null, limit = 2)
        firstPage.forEach { dao.upsert(it.copy(status = PlaceStatus.RESOLVED)) }

        val secondPage = dao.loadPage(PlaceStatus.PENDING, afterCellId = firstPage.last().cellId, limit = 2)

        assertEquals(listOf("charlie"), secondPage.map { it.cellId })
    }
}
