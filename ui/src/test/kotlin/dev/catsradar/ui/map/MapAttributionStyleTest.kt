package dev.catsradar.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class MapAttributionStyleTest {

    @Test
    fun expandedAttributionFadesItsThemeInkToTransparent() {
        val themeInk = Color(0xFF123456)

        assertEquals(themeInk.copy(alpha = 0f), attributionStyle(contentColor = themeInk).containerColor)
    }

    @Test
    fun expandedAttributionUsesTheMapsThemeInk() {
        val themeInk = Color(0xFF123456)

        assertEquals(themeInk, attributionStyle(contentColor = themeInk).contentColor)
    }

    @Test
    fun aDetailMapKeepsItsSmallerAttributionText() {
        val smallText = TextStyle(fontSize = 10.sp)

        assertEquals(smallText, attributionStyle(textStyle = smallText).textStyle)
    }
}
