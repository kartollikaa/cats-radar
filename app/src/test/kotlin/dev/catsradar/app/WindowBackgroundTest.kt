package dev.catsradar.app

import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.ui.theme.CatsRadarDarkColors
import dev.catsradar.ui.theme.CatsRadarLightColors
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

// The window is painted before Compose draws anything, so a mismatch shows as a flash of the wrong
// colour on every cold start.
@RunWith(AndroidJUnit4::class)
class WindowBackgroundTest {

    private fun windowBackground(): Color =
        Color(ContextCompat.getColor(ApplicationProvider.getApplicationContext(), R.color.window_background))

    @Test
    fun theLightWindowIsTheLightThemesSurface() {
        assertEquals(CatsRadarLightColors.surface, windowBackground())
    }

    @Test
    @Config(qualifiers = "night")
    fun theDarkWindowIsTheDarkThemesSurface() {
        assertEquals(CatsRadarDarkColors.surface, windowBackground())
    }
}
