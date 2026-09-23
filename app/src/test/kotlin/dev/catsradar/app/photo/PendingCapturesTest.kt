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
        pending.launched("closed-under-the-newer-one")
        pending.launched("newer")

        assertEquals("closed-under-the-newer-one", pending.answered())
        assertEquals("newer", pending.answered())
        assertNull(pending.answered())
    }

    @Test
    fun theWaitingTargetsSurviveTheProcessDying() {
        val pending = PendingCaptures().apply { launched("in-front-when-the-process-died") }

        val saved = assertNotNull(with(PendingCaptures.Saver) { SaverScope { true }.save(pending) })
        val restored = assertNotNull(PendingCaptures.Saver.restore(saved))

        assertEquals("in-front-when-the-process-died", restored.answered())
    }
}
