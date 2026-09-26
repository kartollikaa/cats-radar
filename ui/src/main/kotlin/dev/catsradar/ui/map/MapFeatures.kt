package dev.catsradar.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.map.MapLine
import dev.catsradar.presentation.map.MapPoint
import dev.catsradar.ui.coat.CoatLook
import dev.catsradar.ui.coat.look
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

internal const val CAT_ID = "id"
internal const val CAT_COAT = "coat"
internal const val CAT_PHOTO = "photo"
internal const val CAT_PHOTO_RANK = "photo_rank"

private const val PHOTO_IMAGE_PREFIX = "cat-photo:"

internal fun photoImageId(thumbnailPath: String): String = PHOTO_IMAGE_PREFIX + thumbnailPath

/** The thumbnail an image of [photoImageId] draws; null for any image the map style itself names. */
internal fun thumbnailOf(imageId: String): String? =
    imageId.takeIf { it.startsWith(PHOTO_IMAGE_PREFIX) }?.removePrefix(PHOTO_IMAGE_PREFIX)

/** These points, with every thumbnail in [unreadable] taken off its cat. */
internal fun ImmutableList<MapPoint>.withoutThumbnails(unreadable: Set<String>): ImmutableList<MapPoint> =
    if (unreadable.isEmpty()) {
        this
    } else {
        map { point -> if (point.thumbnailPath in unreadable) point.copy(thumbnailPath = null) else point }
            .toImmutableList()
    }

/** The photo of every point that has one, in the points' order: a photo's rank is its index here. */
internal fun photoImages(points: ImmutableList<MapPoint>): ImmutableList<String> =
    points.mapNotNull { point -> point.thumbnailPath?.let(::photoImageId) }.toImmutableList()
internal const val HEAT_PREFIX = "heat"
internal const val UNNOTED_HEAT = "${HEAT_PREFIX}_unnoted"
private const val HALF = 0.5

/** One colour of a coat and the part of the cat it stands for; a coat's shares sum to 1. */
internal data class ColourShare(val colour: Color, val share: Double)

/** The fur is half the cat and the markings share the other half; a coat with none is all fur. */
internal fun CoatLook.shares(): List<ColourShare> {
    val markings = listOfNotNull(muzzle, leftCrown, rightCrown)
    if (markings.isEmpty()) return listOf(ColourShare(fur, 1.0))
    val parts = listOf(ColourShare(fur, HALF)) + markings.map { ColourShare(it, HALF / markings.size) }
    return parts.groupBy { it.colour }.map { (colour, same) -> ColourShare(colour, same.sumOf { it.share }) }
}

internal val CoatHeatColours: List<Color> =
    CoatOption.entries.flatMap { coat -> coat.look().shares().map { it.colour } }.distinct()

internal fun heatKey(colour: Color): String = HEAT_PREFIX + colour.toHex()

internal fun catFeatures(points: ImmutableList<MapPoint>): FeatureCollection<Point, JsonObject> {
    var photoRank = 0
    return FeatureCollection(
        points.map { point ->
            Feature(
                Point(Position(point.longitude, point.latitude)),
                buildJsonObject {
                    put(CAT_ID, point.id)
                    point.thumbnailPath?.let { thumbnail ->
                        put(CAT_PHOTO, photoImageId(thumbnail))
                        put(CAT_PHOTO_RANK, photoRank++)
                    }
                    val coat = point.coat
                    if (coat == null) {
                        put(UNNOTED_HEAT, 1.0)
                    } else {
                        put(CAT_COAT, coat.name)
                        coat.look().shares().forEach { put(heatKey(it.colour), it.share) }
                    }
                },
            )
        },
    )
}

/** The cats among tapped [features]; a cluster is not one. */
internal fun tappedCatIds(features: List<Feature<*, JsonObject?>>): List<String> =
    features.mapNotNull { it.properties?.get(CAT_ID)?.jsonPrimitive?.contentOrNull }

/** One line per entry of [lines] with at least two positions, or null when none has. */
internal fun routeLines(lines: ImmutableList<MapLine>): FeatureCollection<LineString, JsonObject>? {
    val drawable = lines.filter { it.positions.size >= 2 }
    if (drawable.isEmpty()) return null
    return FeatureCollection(
        drawable.map { line ->
            val coordinates = line.positions.map { Position(it.longitude, it.latitude) }
            Feature(LineString(coordinates), buildJsonObject {})
        },
    )
}

private fun Color.toHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)
