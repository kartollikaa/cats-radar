package dev.catsradar.app.worker

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkManagerLocationAttachSchedulerTest {
    @Test
    fun `expedited work is requested only from API 31 onward`() {
        assertFalse(shouldExpedite(29))
        assertFalse(shouldExpedite(30))
        assertTrue(shouldExpedite(31))
        assertTrue(shouldExpedite(34))
    }
}
