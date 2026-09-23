package dev.catsradar.app.navigation

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapFocusRequestTest {

    @Test
    fun `a posted outing is handed over once`() {
        val request = MapFocusRequest()

        request.post("first")

        assertEquals("first", request.consume())
        assertNull(request.consume())
    }

    @Test
    fun `the outing posted last is the one handed over`() {
        val request = MapFocusRequest()

        request.post("first")
        request.post("second")

        assertEquals("second", request.consume())
    }
}
