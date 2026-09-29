package dev.catsradar.app.navigation

import dev.catsradar.presentation.map.MapArea
import dev.catsradar.presentation.map.MapChoices
import dev.catsradar.presentation.map.MapFocus
import dev.catsradar.presentation.map.MapState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapBackTest {

    private val focused = MapState.Located(
        points = persistentListOf(),
        area = MapArea(south = 41.38, west = 2.16, north = 41.40, east = 2.18),
        focus = MapFocus(outingId = "cat-1", label = "Outing", lines = persistentListOf()),
    )

    @Test
    fun `back lets go of the outing on the Map tab's own map`() {
        assertTrue(focused.backLetsGoOfFocus(opening = null))
    }

    @Test
    fun `back leaves a map opened above another screen, whatever it shows`() {
        assertFalse(focused.backLetsGoOfFocus(opening = MapChoices(focus = "cat-1")))
        assertFalse(focused.backLetsGoOfFocus(opening = MapChoices(cat = "cat-1")))
    }

    @Test
    fun `back leaves the Map tab's own map when no outing is focused`() {
        assertFalse(focused.copy(focus = null).backLetsGoOfFocus(opening = null))
        assertFalse(MapState.Empty.backLetsGoOfFocus(opening = null))
        assertFalse(MapState.Loading.backLetsGoOfFocus(opening = null))
    }
}
