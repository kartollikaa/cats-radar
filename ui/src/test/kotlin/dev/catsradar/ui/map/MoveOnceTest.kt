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

class MoveOnceTest {

    @Test
    fun aMoveThatEndsReportsItsTargetReached() = runBlocking {
        var reached = 0

        moveOnce(move = {}, onReach = { reached++ })

        assertEquals(1, reached)
    }

    @Test
    fun aPanThatCancelsTheMoveStillReportsItsTargetReached() = runBlocking {
        var reached = 0

        moveOnce(move = { cancelledByAPan() }, onReach = { reached++ })

        assertEquals(1, reached)
    }

    @Test
    fun aMapLeavingMidMoveReportsNothing() = runBlocking {
        var reached = 0
        val moving = launch { moveOnce(move = { awaitCancellation() }, onReach = { reached++ }) }
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
