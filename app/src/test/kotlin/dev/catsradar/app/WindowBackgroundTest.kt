package dev.catsradar.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.theme.deviceColorScheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

// The window is painted before Compose draws anything, so a mismatch shows as a flash of the wrong
// colour on every cold start.
@RunWith(AndroidJUnit4::class)
class WindowBackgroundTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun windowBackground(): Color = Color(ContextCompat.getColor(context, R.color.window_background))

    @Test
    fun theLightWindowIsTheLightSchemesSurface() {
        assertEquals(deviceColorScheme(context, darkTheme = false).surface, windowBackground())
    }

    @Test
    @Config(qualifiers = "night")
    fun theDarkWindowIsTheDarkSchemesSurface() {
        assertEquals(deviceColorScheme(context, darkTheme = true).surface, windowBackground())
    }

    @Test
    @Config(sdk = [30])
    fun belowAndroid12TheLightWindowIsTheLightSchemesSurface() {
        assertEquals(deviceColorScheme(context, darkTheme = false).surface, windowBackground())
    }

    @Test
    @Config(sdk = [30], qualifiers = "night")
    fun belowAndroid12TheDarkWindowIsTheDarkSchemesSurface() {
        assertEquals(deviceColorScheme(context, darkTheme = true).surface, windowBackground())
    }
}
