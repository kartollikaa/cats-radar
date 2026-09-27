package dev.catsradar.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class EncounterDaoInsertShotTest {
    private lateinit var database: TestCatsDatabase
    private lateinit var dao: EncounterDao

    private val source = fullEncounterEntity(id = "source")
    private val first = fullEncounterEntity(id = "first")
    private val second = fullEncounterEntity(id = "second")
    private val firstPhoto = photoEntity(encounterId = first.id, id = "first-photo", shotId = "source-photo")
    private val secondPhoto = photoEntity(encounterId = second.id, id = "second-photo", shotId = "source-photo")

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
    fun aLiveSourceAllowsEveryNewCatAndPhotoToLandTogether() = runTest {
        dao.insert(source)

        assertTrue(
            dao.insertAllIfSourceLive(
                source.id,
                listOf(first, second),
                listOf(firstPhoto, secondPhoto),
            ),
        )

        val stored = dao.loadEvery()
        assertEquals(setOf(source.id, first.id, second.id), stored.map { it.encounter.id }.toSet())
        assertEquals(setOf(firstPhoto, secondPhoto), stored.flatMap { it.photos }.toSet())
    }

    @Test
    fun aDeletedSourceRefusesTheWholeBatch() = runTest {
        dao.insert(source.copy(deletedAt = DELETED_AT))

        assertFalse(
            dao.insertAllIfSourceLive(
                source.id,
                listOf(first, second),
                listOf(firstPhoto, secondPhoto),
            ),
        )

        assertEquals(listOf(source.id), dao.loadEvery().map { it.encounter.id })
        assertEquals(0, database.schemaProbeDao().photoRowCount())
    }

    @Test
    fun aFailureOnTheSecondCatRollsTheFirstCatBack() = runTest {
        dao.insert(source)
        val duplicateSecond = second.copy(id = source.id)

        assertFails {
            dao.insertAllIfSourceLive(
                source.id,
                listOf(first, duplicateSecond),
                listOf(firstPhoto),
            )
        }

        assertEquals(listOf(source.id), dao.loadEvery().map { it.encounter.id })
        assertEquals(0, database.schemaProbeDao().photoRowCount())
    }

    private companion object {
        val DELETED_AT = Instant.parse("2026-09-26T10:00:00Z")
    }
}
