package dev.catsradar.app.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.settings.SettingsState
import dev.catsradar.presentation.settings.UpdateFailure
import dev.catsradar.presentation.settings.UpdateState
import dev.catsradar.presentation.settings.UpdateStatus
import dev.catsradar.ui.R
import dev.catsradar.ui.settings.SettingsScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class UpdatesSectionTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var update: UpdateState by mutableStateOf(UpdateState())
    private var checks = 0

    @Test
    fun `each status reads in words of its own`() {
        show()
        val words = mapOf(
            UpdateStatus.UpToDate to context.getString(R.string.settings_updates_up_to_date),
            UpdateStatus.Available("1.5.0-beta") to
                context.getString(R.string.settings_updates_available, "1.5.0-beta"),
            UpdateStatus.Failed(UpdateFailure.OFFLINE) to context.getString(R.string.settings_updates_offline),
            UpdateStatus.Failed(UpdateFailure.SOURCE_UNAVAILABLE) to
                context.getString(R.string.settings_updates_source_unavailable),
            UpdateStatus.Failed(UpdateFailure.UNREADABLE_ANSWER) to
                context.getString(R.string.settings_updates_unreadable),
        )

        words.forEach { (status, text) ->
            update = UpdateState(status)
            compose.onNodeWithText(text).performScrollTo()
        }
        assertEquals(words.size, words.values.toSet().size)
    }

    @Test
    fun `the button checks, and is taken away while a check runs`() {
        show()
        val button = compose.onNodeWithText(context.getString(R.string.settings_updates_check))

        button.performScrollTo().assertIsEnabled().performClick()
        update = UpdateState(UpdateStatus.Checking)

        button.assertIsNotEnabled()
        assertEquals(1, checks)
    }

    private fun show() {
        compose.setContent {
            CatsRadarTheme {
                SettingsScreen(state = SettingsState(update = update), onCheckForUpdatesClick = { checks++ })
            }
        }
    }
}
