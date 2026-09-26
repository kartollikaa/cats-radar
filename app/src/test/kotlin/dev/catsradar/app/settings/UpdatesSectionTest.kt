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
import dev.catsradar.presentation.settings.UpdateAction
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
    private var installs = 0

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    @Test
    fun `each status reads in words of its own`() {
        show()
        val v = "1.5.0-beta"
        val words = mapOf(
            UpdateStatus.Idle to text(R.string.settings_updates_explained),
            UpdateStatus.UpToDate to text(R.string.settings_updates_up_to_date),
            UpdateStatus.DownloadStarting(v) to text(R.string.settings_updates_downloading, v),
            UpdateStatus.Downloading(v, percent = 45) to text(R.string.settings_updates_downloading_percent, v, 45),
            UpdateStatus.ReadyToInstall(v) to text(R.string.settings_updates_ready, v),
            UpdateStatus.Installing(v) to text(R.string.settings_updates_installing, v),
            UpdateStatus.InstallFailed(v) to text(R.string.settings_updates_install_failed, v),
            UpdateStatus.Failed(UpdateFailure.OFFLINE) to text(R.string.settings_updates_offline),
            UpdateStatus.Failed(UpdateFailure.SOURCE_UNAVAILABLE) to text(R.string.settings_updates_source_unavailable),
            UpdateStatus.Failed(UpdateFailure.UNREADABLE_ANSWER) to text(R.string.settings_updates_unreadable),
            UpdateStatus.Failed(UpdateFailure.DOWNLOAD_FAILED) to text(R.string.settings_updates_download_failed),
        )

        words.forEach { (status, shown) ->
            update = UpdateState(status)
            compose.onNodeWithText(shown).performScrollTo()
        }
        assertEquals(words.size, words.values.toSet().size)
    }

    @Test
    fun `the check button checks, and is taken away while something runs`() {
        show()
        val button = compose.onNodeWithText(text(R.string.settings_updates_check))

        button.performScrollTo().assertIsEnabled().performClick()
        update = UpdateState(UpdateStatus.Checking, UpdateAction.Busy)

        button.assertIsNotEnabled()
        assertEquals(1, checks)
    }

    @Test
    fun `a downloaded update is installed from its own button`() {
        show()
        update = UpdateState(UpdateStatus.ReadyToInstall("1.5.0-beta"), UpdateAction.Install("1.5.0-beta"))

        compose.onNodeWithText(text(R.string.settings_updates_install, "1.5.0-beta")).performScrollTo().performClick()

        assertEquals(1, installs)
        assertEquals(0, checks)
    }

    private fun show() {
        compose.setContent {
            CatsRadarTheme {
                SettingsScreen(
                    state = SettingsState(update = update),
                    onCheckForUpdatesClick = { checks++ },
                    onInstallUpdateClick = { installs++ },
                )
            }
        }
    }
}
