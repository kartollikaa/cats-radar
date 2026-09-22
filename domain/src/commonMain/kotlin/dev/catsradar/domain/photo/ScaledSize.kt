package dev.catsradar.domain.photo

data class ScaledSize(val width: Int, val height: Int)

/**
 * The size [width] x [height] becomes when its longest side is capped at [maxSide], keeping the
 * aspect ratio. Never enlarges: a photo smaller than the cap is already small enough, and blowing
 * it up would cost bytes and quality for no extra detail.
 */
fun scaleToFit(width: Int, height: Int, maxSide: Int): ScaledSize {
    require(width > 0 && height > 0) { "size must be positive, was ${width}x$height" }
    require(maxSide > 0) { "maxSide must be positive, was $maxSide" }

    val longest = maxOf(width, height)
    if (longest <= maxSide) return ScaledSize(width, height)

    val ratio = maxSide.toDouble() / longest
    // A side that rounds to zero would make an undecodable image; one pixel is the floor.
    return ScaledSize(
        width = (width * ratio).roundToAtLeastOne(),
        height = (height * ratio).roundToAtLeastOne(),
    )
}

private fun Double.roundToAtLeastOne(): Int = maxOf(1, kotlin.math.round(this).toInt())
