package dev.catsradar.ui.map

import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.map.MapLine
import dev.catsradar.presentation.map.MapPoint
import dev.catsradar.presentation.map.MapPosition
import dev.catsradar.ui.coat.Black
import dev.catsradar.ui.coat.Brown
import dev.catsradar.ui.coat.Ginger
import dev.catsradar.ui.coat.Grey
import dev.catsradar.ui.coat.White
import dev.catsradar.ui.coat.look
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
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
        assertEquals(setOf(Ginger, White, Black, Brown, Grey), CoatHeatColours.toSet())
    }

    @Test
    fun twoLinesBecomeTwoFeaturesWhoseCoordinatesAreLongitudeFirstInOrder() {
        val lines = persistentListOf(
            MapLine(persistentListOf(MapPosition(41.39, 2.17), MapPosition(41.40, 2.18))),
            MapLine(persistentListOf(MapPosition(41.38, 2.19), MapPosition(41.37, 2.20))),
        )

        val features = routeLines(lines)?.features

        assertEquals(
            listOf(
                listOf(Position(2.17, 41.39), Position(2.18, 41.40)),
                listOf(Position(2.19, 41.38), Position(2.20, 41.37)),
            ),
            features?.map { it.geometry.coordinates },
        )
    }

    @Test
    fun aLineOfOnePositionIsDropped() {
        val lines = persistentListOf(
            MapLine(persistentListOf(MapPosition(41.39, 2.17), MapPosition(41.40, 2.18))),
            MapLine(persistentListOf(MapPosition(41.38, 2.19))),
        )

        val features = routeLines(lines)?.features

        assertEquals(listOf(Position(2.17, 41.39), Position(2.18, 41.40)), features?.single()?.geometry?.coordinates)
    }

    @Test
    fun allLinesTooShortGivesNull() {
        val lines = persistentListOf(
            MapLine(persistentListOf(MapPosition(41.39, 2.17))),
            MapLine(persistentListOf()),
        )

        assertNull(routeLines(lines))
    }

    @Test
    fun aCatWithAThumbnailCarriesItsPhotoAndItsRankAmongPhotographedCatsAndOneWithoutCarriesNeither() {
        val points = persistentListOf(
            MapPoint("newest", 41.39, 2.17, coat = null, thumbnailPath = "/photos/newest_thumb.jpg"),
            MapPoint("tally", 41.39, 2.17, CoatOption.GINGER),
            MapPoint("older", 41.39, 2.17, CoatOption.BLACK, thumbnailPath = "/photos/older_thumb.jpg"),
        )

        val properties = catFeatures(points).features.map { it.properties }

        assertEquals(
            listOf(photoImageId("/photos/newest_thumb.jpg"), null, photoImageId("/photos/older_thumb.jpg")),
            properties.map { it[CAT_PHOTO]?.jsonPrimitive?.content },
        )
        assertEquals(listOf(0, null, 1), properties.map { it[CAT_PHOTO_RANK]?.jsonPrimitive?.int })
    }

    @Test
    fun theCoverTableHoldsEveryPhotographedCatAtItsRankAndNoOtherCat() {
        val points = persistentListOf(
            MapPoint("tally", 41.39, 2.17, coat = null),
            MapPoint("newest", 41.39, 2.17, coat = null, thumbnailPath = "/photos/newest_thumb.jpg"),
            MapPoint("older", 41.39, 2.17, coat = null, thumbnailPath = "/photos/older_thumb.jpg"),
            MapPoint("oldest tally", 41.39, 2.17, CoatOption.GREY),
        )

        assertEquals(
            listOf(photoImageId("/photos/newest_thumb.jpg"), photoImageId("/photos/older_thumb.jpg")),
            photoImages(points),
        )
    }

    @Test
    fun aThumbnailThatWouldNotDrawLeavesItsCatWithoutAPhotoAndEveryOtherCatAsItWas() {
        val points = persistentListOf(
            MapPoint("unreadable", 41.39, 2.17, CoatOption.GINGER, thumbnailPath = "/photos/unreadable_thumb.jpg"),
            MapPoint("readable", 41.39, 2.17, coat = null, thumbnailPath = "/photos/readable_thumb.jpg"),
            MapPoint("tally", 41.39, 2.17, CoatOption.BLACK),
        )

        val drawable = points.withoutThumbnails(setOf("/photos/unreadable_thumb.jpg"))

        assertEquals(
            listOf(points[0].copy(thumbnailPath = null), points[1], points[2]),
            drawable,
        )
        assertEquals(listOf(photoImageId("/photos/readable_thumb.jpg")), photoImages(drawable))
    }

    @Test
    fun aPhotoImageNamesItsThumbnailAndAnImageTheMapStyleAsksForNamesNone() {
        assertEquals("/photos/a_thumb.jpg", thumbnailOf(photoImageId("/photos/a_thumb.jpg")))
        assertNull(thumbnailOf("bus_stop"))
    }

    @Test
    fun aClusterUnderATapIsNoCat() {
        val cluster = Feature(Point(Position(2.17, 41.39)), buildJsonObject { put("point_count", 3) })

        assertEquals(emptyList<String>(), tappedCatIds(listOf(cluster)))
    }

    private fun heatWeights(properties: JsonObject): Map<String, Double> =
        properties.filterKeys { it.startsWith(HEAT_PREFIX) }.mapValues { it.value.jsonPrimitive.double }
}
