package dev.catsradar.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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

        val outsideStarted = CompletableDeferred<Unit>()
        val outside = launch {
            outsideStarted.complete(Unit)
            encounterDao.insert(fullEncounterEntity(id = "tapped", sourceDigest = null))
        }
        outsideStarted.await()
        withContext(Dispatchers.Default) { delay(OUTSIDE_WRITE_GRACE_MS) }
        assertFalse(outside.isCompleted)

        release.complete(Unit)
        joinAll(run, outside)
        assertEquals(setOf("imported", "tapped"), encounterDao.loadEvery().mapTo(mutableSetOf()) { it.id })
    }

    @Test
    fun anObserverOutsideSeesTheRowsOnceTheTransactionCommits() = runTest {
        withContext(Dispatchers.Default) {
            val seen = Channel<List<String>>(Channel.UNLIMITED)
            val observer = launch { encounterDao.observeAll().collect { rows -> seen.send(rows.map { it.id }) } }
            withTimeout(EMISSION_TIMEOUT_MS) { assertEquals(emptyList(), seen.receive()) }

            runner.inTransaction { encounterDao.insert(fullEncounterEntity(id = "imported")) }

            withTimeout(EMISSION_TIMEOUT_MS) { assertEquals(listOf("imported"), seen.receive()) }
            observer.cancel()
        }
    }

    @Test
    fun aTableObservedInsideAFailedTransactionStillNotifiesObserversAfterwards() = runTest {
        withContext(Dispatchers.Default) {
            assertFailsWith<IllegalStateException> {
                runner.inTransaction {
                    placeCellDao.observeAll().first()
                    placeCellDao.upsert(pendingPlaceCellEntity("ucfv0h"))
                    error("database or disk is full")
                }
            }
            val seen = Channel<List<String>>(Channel.UNLIMITED)
            val observer = launch { placeCellDao.observeAll().collect { rows -> seen.send(rows.map { it.cellId }) } }
            withTimeout(EMISSION_TIMEOUT_MS) { assertEquals(emptyList(), seen.receive()) }

            placeCellDao.upsert(pendingPlaceCellEntity("ucfv0j"))

            withTimeout(EMISSION_TIMEOUT_MS) { assertEquals(listOf("ucfv0j"), seen.receive()) }
            observer.cancel()
        }
    }

    private companion object {
        const val OUTSIDE_WRITE_GRACE_MS = 300L
        const val EMISSION_TIMEOUT_MS = 5_000L
    }
}
