package dev.catsradar.app.navigation

import dev.catsradar.presentation.map.MapIntent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapFocusRequestTest {

    @Test
    fun `a posted outing is handed over once`() {
        val request = MapFocusRequest()

        request.postOuting("first")

        assertEquals(MapIntent.OutingFocused("first"), request.consume())
        assertNull(request.consume())
    }

    @Test
    fun `a posted cat is handed over once`() {
        val request = MapFocusRequest()

        request.postCat("first")

        assertEquals(MapIntent.CatRequested("first"), request.consume())
        assertNull(request.consume())
    }

    @Test
    fun `the request posted last is the one handed over`() {
        val request = MapFocusRequest()

        request.postOuting("first")
        request.postCat("second")

        assertEquals(MapIntent.CatRequested("second"), request.consume())
    }
}
