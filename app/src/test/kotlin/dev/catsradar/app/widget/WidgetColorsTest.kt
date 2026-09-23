package dev.catsradar.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.ui.theme.CatsRadarDarkColors
import dev.catsradar.ui.theme.CatsRadarLightColors
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class WidgetColorsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun tileColour(): Color = widgetColors().primaryContainer.getColor(context)

    @Test
    fun onAndroid12AndLaterTheLightWidgetTakesTheWallpapersColours() {
        assertEquals(Color(context.getColor(android.R.color.system_accent1_100)), tileColour())
    }

    @Test
    @Config(qualifiers = "night")
    fun onAndroid12AndLaterTheDarkWidgetTakesTheWallpapersColours() {
        assertEquals(Color(context.getColor(android.R.color.system_accent1_700)), tileColour())
    }

    @Test
    @Config(sdk = [30])
    fun belowAndroid12TheLightWidgetKeepsTheAppsTeal() {
        assertEquals(CatsRadarLightColors.primaryContainer, tileColour())
    }

    @Test
    @Config(sdk = [30], qualifiers = "night")
    fun belowAndroid12TheDarkWidgetKeepsTheAppsTeal() {
        assertEquals(CatsRadarDarkColors.primaryContainer, tileColour())
    }
}
