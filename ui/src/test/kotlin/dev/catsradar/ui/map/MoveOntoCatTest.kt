package dev.catsradar.ui.map

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class MoveOntoCatTest {

    @Test
    fun aMoveThatEndsReportsTheCatReached() = runBlocking {
        var reached = 0

        moveOntoCat(move = {}, onReach = { reached++ })

        assertEquals(1, reached)
    }

    @Test
    fun aPanThatCancelsTheMoveStillReportsTheCatReached() = runBlocking {
        var reached = 0

        moveOntoCat(move = { cancelledByAPan() }, onReach = { reached++ })

        assertEquals(1, reached)
    }

    @Test
    fun aMapLeavingMidMoveReportsNothing() = runBlocking {
        var reached = 0
        val moving = launch { moveOntoCat(move = { awaitCancellation() }, onReach = { reached++ }) }
        yield()

        moving.cancelAndJoin()

        assertEquals(0, reached)
    }

    // The map's own camera calls run in a scope of their own, and a gesture cancels that scope alone.
    private suspend fun cancelledByAPan(): Nothing = coroutineScope {
        coroutineContext.job.cancel()
        awaitCancellation()
    }
}
