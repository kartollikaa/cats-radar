package dev.catsradar.ui.statistics

import kotlin.math.roundToInt

/**
 * Where each label starts: centred on [centres] and kept inside [width], or null where it would come within
 * [minGap] of the label after it. Placed from the last back, so the last label always shows.
 */
internal fun axisLabelStarts(centres: List<Float>, widths: List<Int>, width: Int, minGap: Int): List<Int?> {
    val starts = MutableList<Int?>(centres.size) { null }
    var end = width + minGap
    for (index in centres.indices.reversed()) {
        val labelWidth = widths[index]
        val start = (centres[index] - labelWidth / 2f).roundToInt().coerceIn(0, (width - labelWidth).coerceAtLeast(0))
        if (labelWidth > 0 && start + labelWidth + minGap <= end) {
            starts[index] = start
            end = start
        }
    }
    return starts
}
