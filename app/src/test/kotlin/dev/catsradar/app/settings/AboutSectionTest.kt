package dev.catsradar.app.settings

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.settings.AboutState
import dev.catsradar.presentation.settings.SettingsState
import dev.catsradar.ui.R
import dev.catsradar.ui.settings.SettingsScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class AboutSectionTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var copies = 0

    private val about = AboutState(
        version = "1.4.1-beta (7)",
        build = "release · 5989a92c1f3e",
        device = "Google Pixel 7",
        androidRelease = "16",
        sdkInt = 36,
        report = "Cats Radar 1.4.1-beta (7)",
    )

    @Test
    fun `each row of the about section reads its label next to its value`() {
        show(SettingsState(about = about))

        mapOf(
            R.string.settings_about_version to "1.4.1-beta (7)",
            R.string.settings_about_build to "release · 5989a92c1f3e",
            R.string.settings_about_device to "Google Pixel 7",
            R.string.settings_about_android to "16 (API 36)",
        ).forEach { (label, value) ->
            compose.onNode(hasText(context.getString(label)) and hasText(value)).performScrollTo()
        }
    }

    @Test
    fun `the copy button asks for the report to be copied`() {
        show(SettingsState(about = about))

        compose.onNodeWithContentDescription(context.getString(R.string.settings_about_copy))
            .performScrollTo()
            .performClick()

        assertEquals(1, copies)
    }

    private fun show(state: SettingsState) {
        compose.setContent {
            CatsRadarTheme { SettingsScreen(state = state, onCopyBuildInfoClick = { copies++ }) }
        }
    }
}
