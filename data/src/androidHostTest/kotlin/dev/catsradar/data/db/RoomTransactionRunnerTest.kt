package dev.catsradar.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class RoomTransactionRunnerTest {
    private lateinit var database: TestCatsDatabase
    private lateinit var encounterDao: EncounterDao
    private lateinit var placeCellDao: PlaceCellDao
    private lateinit var runner: RoomTransactionRunner

    @Before
    fun createDatabase() {
        database = buildInMemoryCatsDatabase(ApplicationProvider.getApplicationContext<Context>())
        encounterDao = database.encounterDao()
        placeCellDao = database.placeCellDao()
        runner = RoomTransactionRunner(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun aBlockThatThrowsAfterWritingLeavesNoneOfItsWritesBehind() = runTest {
        encounterDao.insert(fullEncounterEntity(id = "here"))
        val before = encounterDao.loadEvery()

        assertFailsWith<IllegalStateException> {
            runner.inTransaction {
                encounterDao.update(fullEncounterEntity(id = "here").copy(coat = null))
                encounterDao.insert(fullEncounterEntity(id = "imported", sourceDigest = "digest-2"))
                placeCellDao.upsert(pendingPlaceCellEntity("ucfv0h"))
                error("database or disk is full")
            }
        }

        assertEquals(before, encounterDao.loadEvery())
        assertEquals(emptyList(), placeCellDao.observeAll().first())
    }

    @Test
    fun aBlockThatFinishesKeepsEveryWriteAndReturnsItsResult() = runTest {
        val result = runner.inTransaction {
            encounterDao.insert(fullEncounterEntity(id = "imported"))
            placeCellDao.upsert(pendingPlaceCellEntity("ucfv0h"))
            placeCellDao.observeAll().first().size
        }

        assertEquals(1, result)
        assertEquals(listOf("imported"), encounterDao.loadEvery().map { it.id })
        assertEquals(listOf("ucfv0h"), placeCellDao.observeAll().first().map { it.cellId })
    }

    @Test
    fun aBlockCancelledAfterWritingLeavesNoneOfItsWritesBehind() = runTest {
        val written = CompletableDeferred<Unit>()
        val run = launch {
            runner.inTransaction {
                encounterDao.insert(fullEncounterEntity(id = "imported"))
                written.complete(Unit)
                awaitCancellation()
            }
        }

        written.await()
        run.cancelAndJoin()

        assertEquals(emptyList(), encounterDao.loadEvery())
    }

    @Test
    fun aWriteFromOutsideTheTransactionWaitsUntilItHasFinished() = runTest {
        val inside = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val run = launch {
            runner.inTransaction {
                encounterDao.insert(fullEncounterEntity(id = "imported"))
                inside.complete(Unit)
                release.await()
            }
        }
        inside.await()

        val outside = launch { encounterDao.insert(fullEncounterEntity(id = "tapped", sourceDigest = null)) }
        withContext(Dispatchers.Default) { delay(OUTSIDE_WRITE_GRACE_MS) }
        assertFalse(outside.isCompleted)

        release.complete(Unit)
        joinAll(run, outside)
        assertEquals(setOf("imported", "tapped"), encounterDao.loadEvery().mapTo(mutableSetOf()) { it.id })
    }

    private companion object {
        const val OUTSIDE_WRITE_GRACE_MS = 300L
    }
}
