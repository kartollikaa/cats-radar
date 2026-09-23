package dev.catsradar.app.theme

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
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
class DeviceColorSchemeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun systemColor(id: Int): Color = Color(context.getColor(id))

    @Test
    fun onAndroid12AndLaterTheLightSchemeIsTheWallpapers() {
        assertEquals(
            systemColor(android.R.color.system_primary_light),
            deviceColorScheme(context, darkTheme = false).primary,
        )
    }

    @Test
    fun onAndroid12AndLaterTheDarkSchemeIsTheWallpapers() {
        assertEquals(
            systemColor(android.R.color.system_primary_dark),
            deviceColorScheme(context, darkTheme = true).primary,
        )
    }

    @Test
    @Config(sdk = [31])
    fun onAndroid12ItselfTheLightSchemeIsTheWallpapers() {
        assertEquals(
            systemColor(android.R.color.system_accent1_600),
            deviceColorScheme(context, darkTheme = false).primary,
        )
    }

    @Test
    @Config(sdk = [31])
    fun onAndroid12ItselfTheDarkSchemeIsTheWallpapers() {
        assertEquals(
            systemColor(android.R.color.system_accent1_200),
            deviceColorScheme(context, darkTheme = true).primary,
        )
    }

    @Test
    @Config(sdk = [30])
    fun belowAndroid12TheLightSchemeIsTheAppsOwnTeal() {
        assertSame(CatsRadarLightColors, deviceColorScheme(context, darkTheme = false))
    }

    @Test
    @Config(sdk = [30])
    fun belowAndroid12TheDarkSchemeIsTheAppsOwnTeal() {
        assertSame(CatsRadarDarkColors, deviceColorScheme(context, darkTheme = true))
    }
}
