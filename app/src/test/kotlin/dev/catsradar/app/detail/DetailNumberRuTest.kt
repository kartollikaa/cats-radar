package dev.catsradar.app.detail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.detail.CatPage
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.ui.detail.EncounterDetailScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@Config(qualifiers = "ru-w411dp-h1400dp-xxhdpi")
@RunWith(AndroidJUnit4::class)
class DetailNumberRuTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    @Test
    fun `in russian the number reads № 62 and is spoken as котик номер 62`() {
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                CatsRadarTheme {
                    Surface { EncounterDetailScreen(state = loadedWith(page), contentPadding = PaddingValues()) }
                }
            }
        }

        val number = compose.onNodeWithText("№ 62", useUnmergedTree = true).assertIsDisplayed()
        val spoken = number.fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        assertEquals(listOf("Котик номер 62"), spoken)
    }

    private companion object {
        val page = CatPage(
            id = "cat-7",
            dayLabel = "Вчера",
            timeLabel = "16:12",
            location = LocationLabel.NONE,
            coordinatesLabel = null,
            accuracyMeters = null,
            numberInLog = 62,
        )
    }
}
