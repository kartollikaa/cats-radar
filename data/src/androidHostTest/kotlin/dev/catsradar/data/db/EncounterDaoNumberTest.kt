package dev.catsradar.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class EncounterDaoNumberTest {
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
    fun liveCatsAreNumberedOldestFirst() = runTest {
        dao.insert(fullEncounterEntity(id = "c-third", occurredAt = T0 + 20.minutes))
        dao.insert(fullEncounterEntity(id = "a-first", occurredAt = T0))
        dao.insert(fullEncounterEntity(id = "b-second", occurredAt = T0 + 10.minutes))

        assertEquals(listOf(1, 2, 3), numbersOf("a-first", "b-second", "c-third"))
    }

    @Test
    fun catsLoggedAtTheSameInstantAreNumberedBySmallerIdFirst() = runTest {
        dao.insert(fullEncounterEntity(id = "cat-b", occurredAt = T0))
        dao.insert(fullEncounterEntity(id = "cat-a", occurredAt = T0))
        dao.insert(fullEncounterEntity(id = "cat-c", occurredAt = T0))

        assertEquals(listOf(1, 2, 3), numbersOf("cat-a", "cat-b", "cat-c"))
    }

    @Test
    fun aDeleteRenumbersTheCatsAfterItAndLeavesTheOnesBeforeIt() = runTest {
        insertFour()

        dao.softDelete("second", deletedAt = T0 + 1.minutes)

        assertEquals(listOf(1, null, 2, 3), numbersOf("first", "second", "third", "fourth"))
    }

    @Test
    fun aDeletedCatAndAnUnknownIdHaveNoNumber() = runTest {
        dao.insert(fullEncounterEntity(id = "gone", occurredAt = T0, deletedAt = T0 + 1.minutes))

        assertEquals(listOf(null, null), numbersOf("gone", "never-logged"))
    }

    @Test
    fun undoingTheDeleteGivesEveryCatItsNumberBack() = runTest {
        insertFour()
        dao.softDelete("second", deletedAt = T0 + 1.minutes)

        dao.clearDeletedAt("second")

        assertEquals(listOf(1, 2, 3, 4), numbersOf("first", "second", "third", "fourth"))
    }

    @Test
    fun aCatLoggedEarlierRenumbersEveryCatAfterIt() = runTest {
        insertFour()

        dao.insert(fullEncounterEntity(id = "imported", occurredAt = T0 + 15.minutes))

        assertEquals(listOf(1, 2, 3, 4, 5), numbersOf("first", "second", "imported", "third", "fourth"))
    }

    private suspend fun insertFour() {
        dao.insert(fullEncounterEntity(id = "first", occurredAt = T0))
        dao.insert(fullEncounterEntity(id = "second", occurredAt = T0 + 10.minutes))
        dao.insert(fullEncounterEntity(id = "third", occurredAt = T0 + 20.minutes))
        dao.insert(fullEncounterEntity(id = "fourth", occurredAt = T0 + 30.minutes))
    }

    private suspend fun numbersOf(vararg ids: String): List<Int?> = ids.map { dao.observeNumber(it).first() }

    private companion object {
        val T0 = Instant.parse("2026-09-20T10:00:00Z")
    }
}
