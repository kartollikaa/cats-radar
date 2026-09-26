package dev.catsradar.app.photo

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PendingCapturesTest {

    @Test
    fun eachResultBelongsToTheOldestCameraStillWaiting() {
        val pending = PendingCaptures()
        pending.launched(PendingCapture("closed-under-the-newer-one", catId = "cat-a"))
        pending.launched(PendingCapture("newer", catId = "cat-b"))

        assertEquals(PendingCapture("closed-under-the-newer-one", "cat-a"), pending.answered())
        assertEquals(PendingCapture("newer", "cat-b"), pending.answered())
        assertNull(pending.answered())
    }

    @Test
    fun theWaitingTargetsAndTheirCatsSurviveTheProcessDying() {
        val pending = PendingCaptures().apply {
            launched(PendingCapture("for-a-cat", catId = "cat-a"))
            launched(PendingCapture("for-a-new-cat", catId = null))
        }

        val restored = restore(save(pending))

        assertEquals(PendingCapture("for-a-cat", "cat-a"), restored.answered())
        assertEquals(PendingCapture("for-a-new-cat", null), restored.answered())
    }

    @Test
    fun aQueueSavedBeforeShotsNamedTheirCatRestoresEmptyRatherThanGuessingOne() {
        val restored = restore(listOf("content://captures/1", "content://captures/2"))

        assertNull(restored.answered())
    }

    private fun save(pending: PendingCaptures): Any =
        assertNotNull(with(PendingCaptures.Saver) { SaverScope { true }.save(pending) })

    private fun restore(saved: Any): PendingCaptures = assertNotNull(PendingCaptures.Saver.restore(saved))
}
