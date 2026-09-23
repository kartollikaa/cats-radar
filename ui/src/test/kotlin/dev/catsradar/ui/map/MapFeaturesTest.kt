package dev.catsradar.ui.map

import androidx.compose.ui.graphics.Color
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.map.MapPoint
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

class MapFeaturesTest {

    @Test
    fun everyDotCarriesItsCatAndATapReadsTheCatsBack() {
        val points = persistentListOf(
            MapPoint("ginger", 41.39, 2.17, CoatOption.GINGER),
            MapPoint("unnoted", 41.40, 2.18, coat = null),
        )

        val features = catFeatures(points, unnoted = Color.Red).features

        assertEquals(listOf("ginger", "unnoted"), tappedCatIds(features))
        assertEquals(listOf(Position(2.17, 41.39), Position(2.18, 41.40)), features.map { it.geometry.coordinates })
    }

    @Test
    fun aRouteRunsThroughItsCatsInTheOrderGivenAndOneCatMakesNoRoute() {
        val points = persistentListOf(
            MapPoint("first", 41.39, 2.17, coat = null),
            MapPoint("second", 41.40, 2.18, coat = null),
            MapPoint("third", 41.38, 2.19, coat = null),
        )

        val route = routeLine(points)

        assertEquals(
            listOf(Position(2.17, 41.39), Position(2.18, 41.40), Position(2.19, 41.38)),
            route?.features?.single()?.geometry?.coordinates,
        )
        assertNull(routeLine(points.subList(0, 1).toPersistentList()))
    }

    @Test
    fun aClusterUnderATapIsNoCat() {
        val cluster = Feature(Point(Position(2.17, 41.39)), buildJsonObject { put("point_count", 3) })

        assertEquals(emptyList<String>(), tappedCatIds(listOf(cluster)))
    }
}
