package dev.catsradar.ui.statistics

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class SmallUnitsTest {

    private val unit = SpanStyle(fontSize = 12.sp)

    private fun AnnotatedString.smallParts(): List<String> = spanStyles.map { text.substring(it.start, it.end) }

    @Test
    fun theWordsAroundTheNumbersTakeTheUnitStyleAndTheNumbersKeepTheirOwn() {
        val styled = "14 h 20 min".withSmallUnits(unit)

        assertEquals("14 h 20 min", styled.text)
        assertEquals(listOf(" h ", " min"), styled.smallParts())
        assertEquals(listOf(unit, unit), styled.spanStyles.map { it.item })
    }

    @Test
    fun aDecimalPointBetweenDigitsBelongsToTheNumber() {
        assertEquals(listOf(" / h"), "4.2 / h".withSmallUnits(unit).smallParts())
        assertEquals(listOf(" км"), "1,5 км".withSmallUnits(unit).smallParts())
    }

    @Test
    fun wordsBeforeAndBetweenNumbersAreSmallToo() {
        assertEquals(listOf(" cats in ", " min"), "9 cats in 42 min".withSmallUnits(unit).smallParts())
    }

    @Test
    fun aValueWithoutADigitStaysWhole() {
        assertEquals(emptyList<String>(), "—".withSmallUnits(unit).smallParts())
        assertEquals(emptyList<String>(), "38".withSmallUnits(unit).smallParts())
    }
}
