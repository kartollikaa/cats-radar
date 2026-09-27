package dev.catsradar.app.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
class CatsRadarThemeTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    @Test
    fun `the theme moves with the expressive springs`() {
        var motion: MotionScheme? = null
        compose.setContent { CatsRadarTheme { motion = MaterialTheme.motionScheme } }
        compose.waitForIdle()

        val expressive = MotionScheme.expressive()
        assertEquals(expressive.defaultSpatialSpec<Float>(), motion?.defaultSpatialSpec<Float>())
        assertEquals(expressive.defaultEffectsSpec<Float>(), motion?.defaultEffectsSpec<Float>())
    }

    @Test
    fun `the colour scheme it is given is the one the screen draws with`() {
        val given = darkColorScheme(primary = Color(0xFF123456))
        var drawn: ColorScheme? = null
        compose.setContent { CatsRadarTheme(colorScheme = given) { drawn = MaterialTheme.colorScheme } }
        compose.waitForIdle()

        assertSame(given, drawn)
    }

    @Test
    fun `the theme keeps its own corners and weights`() {
        var shapes: Shapes? = null
        var typography: Typography? = null
        compose.setContent {
            CatsRadarTheme {
                shapes = MaterialTheme.shapes
                typography = MaterialTheme.typography
            }
        }
        compose.waitForIdle()

        assertEquals(RoundedCornerShape(28.dp), shapes?.large)
        assertEquals(FontWeight.Bold, typography?.displayLarge?.fontWeight)
    }
}
