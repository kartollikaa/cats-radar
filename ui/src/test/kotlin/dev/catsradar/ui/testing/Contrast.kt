package dev.catsradar.ui.testing

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** WCAG contrast ratio between two colours, from 1 (identical) to 21 (black on white). */
internal fun contrast(a: Color, b: Color): Float {
    val (lighter, darker) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (lighter + 0.05f) / (darker + 0.05f)
}

// WCAG AA: body text, and the edges of shapes and icons that carry meaning.
internal const val MIN_TEXT_CONTRAST = 4.5f
internal const val MIN_SHAPE_CONTRAST = 3f
