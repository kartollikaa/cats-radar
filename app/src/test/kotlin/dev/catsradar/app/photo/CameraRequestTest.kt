package dev.catsradar.app.photo

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraRequestTest {

    private val request = CameraRequest()

    @Test
    fun aPostedRequestIsConsumedExactlyOnce() {
        request.post()

        assertTrue(request.consume())
        assertFalse(request.consume())
        assertFalse(request.isPending)
    }

    @Test
    fun nothingPostedConsumesNothing() {
        assertFalse(request.consume())
    }

    @Test
    fun twoRequestsBeforeTheScreenArrivesOpenTheCameraOnce() {
        request.post()
        request.post()

        assertTrue(request.consume())
        assertFalse(request.consume())
    }
}
