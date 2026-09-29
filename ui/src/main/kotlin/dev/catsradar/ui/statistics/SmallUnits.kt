package dev.catsradar.ui.statistics

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/** This value with every character outside its numbers in [unit], so "14 h 20 min" reads as two large numbers. */
internal fun String.withSmallUnits(unit: SpanStyle): AnnotatedString {
    val value = this
    if (value.none(Char::isDigit)) return AnnotatedString(value)
    return buildAnnotatedString {
        var start = 0
        while (start < value.length) {
            val numeric = value.isNumeric(start)
            var end = start + 1
            while (end < value.length && value.isNumeric(end) == numeric) end++
            val run = value.substring(start, end)
            if (numeric) append(run) else withStyle(unit) { append(run) }
            start = end
        }
    }
}

/** A value in [style] with the words around its numbers at [unitStyle]'s size. */
@Composable
internal fun Figure(value: String, style: TextStyle, unitStyle: TextStyle, modifier: Modifier = Modifier) {
    Text(text = value.withSmallUnits(SpanStyle(fontSize = unitStyle.fontSize)), style = style, modifier = modifier)
}

private fun String.isNumeric(index: Int): Boolean {
    val char = this[index]
    val betweenDigits = index in 1 until lastIndex && this[index - 1].isDigit() && this[index + 1].isDigit()
    return char.isDigit() || (char in DECIMAL_SEPARATORS && betweenDigits)
}

private const val DECIMAL_SEPARATORS = ".,"
