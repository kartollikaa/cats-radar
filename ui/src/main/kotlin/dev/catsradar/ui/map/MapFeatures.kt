package dev.catsradar.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.catsradar.presentation.map.MapPoint
import dev.catsradar.ui.coat.look
import kotlinx.collections.immutable.ImmutableList
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
internal const val CAT_COLOR = "color"

internal fun catFeatures(points: ImmutableList<MapPoint>, unnoted: Color): FeatureCollection<Point, JsonObject> =
    FeatureCollection(
        points.map { point ->
            val color = point.coat?.look()?.fur ?: unnoted
            Feature(
                Point(Position(point.longitude, point.latitude)),
                buildJsonObject {
                    put(CAT_ID, point.id)
                    put(CAT_COLOR, color.toHex())
                },
            )
        },
    )

/** The cats among tapped [features]; a cluster is not one. */
internal fun tappedCatIds(features: List<Feature<*, JsonObject?>>): List<String> =
    features.mapNotNull { it.properties?.get(CAT_ID)?.jsonPrimitive?.contentOrNull }

/** A line through [points] in their order, or null when there are too few to draw one. */
internal fun routeLine(points: ImmutableList<MapPoint>): FeatureCollection<LineString, JsonObject>? {
    if (points.size < 2) return null
    val line = LineString(points.map { Position(it.longitude, it.latitude) })
    return FeatureCollection(listOf(Feature(line, buildJsonObject {})))
}

private fun Color.toHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)
