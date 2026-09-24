package dev.catsradar.ui.map

import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.map.MapPoint
import dev.catsradar.ui.coat.Black
import dev.catsradar.ui.coat.Brown
import dev.catsradar.ui.coat.Ginger
import dev.catsradar.ui.coat.Grey
import dev.catsradar.ui.coat.White
import dev.catsradar.ui.coat.look
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
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

        val features = catFeatures(points).features

        assertEquals(listOf("ginger", "unnoted"), tappedCatIds(features))
        assertEquals(listOf(Position(2.17, 41.39), Position(2.18, 41.40)), features.map { it.geometry.coordinates })
    }

    @Test
    fun theFurTakesHalfADotAndTheMarkingsShareTheOtherHalf() {
        val expected = mapOf(
            CoatOption.GINGER to listOf(ColourShare(Ginger, 1.0)),
            CoatOption.GINGER_WHITE to listOf(ColourShare(Ginger, 0.5), ColourShare(White, 0.5)),
            CoatOption.WHITE to listOf(ColourShare(White, 1.0)),
            CoatOption.TRICOLOR_MOSTLY_WHITE to
                listOf(ColourShare(White, 0.5), ColourShare(Ginger, 0.25), ColourShare(Black, 0.25)),
            CoatOption.TRICOLOR_LITTLE_WHITE to
                listOf(ColourShare(Ginger, 0.5), ColourShare(White, 0.25), ColourShare(Black, 0.25)),
            CoatOption.BROWN to listOf(ColourShare(Brown, 1.0)),
            CoatOption.BROWN_WHITE to listOf(ColourShare(Brown, 0.5), ColourShare(White, 0.5)),
            CoatOption.GREY to listOf(ColourShare(Grey, 1.0)),
            CoatOption.GREY_WHITE to listOf(ColourShare(Grey, 0.5), ColourShare(White, 0.5)),
            CoatOption.BLACK to listOf(ColourShare(Black, 1.0)),
            CoatOption.BLACK_WHITE to listOf(ColourShare(Black, 0.5), ColourShare(White, 0.5)),
        )

        assertEquals(expected, CoatOption.entries.associateWith { it.look().shares() })
    }

    @Test
    fun eachCatFeedsTheHeatOfItsColoursInItsDotSharesAndACatWithNoCoatFeedsOnlyTheUnnotedHeat() {
        val points = persistentListOf(
            MapPoint("unnoted", 41.39, 2.17, coat = null),
            MapPoint("ginger", 41.39, 2.17, CoatOption.GINGER),
            MapPoint("tuxedo", 41.39, 2.17, CoatOption.BLACK_WHITE),
            MapPoint("calico", 41.39, 2.17, CoatOption.TRICOLOR_MOSTLY_WHITE),
        )

        val weights = catFeatures(points).features.map { heatWeights(it.properties) }

        assertEquals(
            listOf(
                mapOf(UNNOTED_HEAT to 1.0),
                mapOf(heatKey(Ginger) to 1.0),
                mapOf(heatKey(Black) to 0.5, heatKey(White) to 0.5),
                mapOf(heatKey(White) to 0.5, heatKey(Ginger) to 0.25, heatKey(Black) to 0.25),
            ),
            weights,
        )
    }

    @Test
    fun everyColourACatCanFeedHasAHeatLayer() {
        val fed = CoatOption.entries.flatMap { coat -> coat.look().shares().map { heatKey(it.colour) } }.toSet()

        assertEquals(fed, CoatHeatColours.map { heatKey(it) }.toSet())
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

    private fun heatWeights(properties: JsonObject): Map<String, Double> =
        properties.filterKeys { it.startsWith(HEAT_PREFIX) }.mapValues { it.value.jsonPrimitive.double }
}
