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
import dev.catsradar.presentation.settings.AboutState
import dev.catsradar.presentation.settings.InstallFailure
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
    private var permissionPages = 0

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
            UpdateStatus.NeedsInstallPermission(v) to text(R.string.settings_updates_needs_permission, v),
            UpdateStatus.Installing(v) to text(R.string.settings_updates_installing, v),
            UpdateStatus.InstallFailed(v, InstallFailure.OTHER) to text(R.string.settings_updates_install_failed, v),
            UpdateStatus.InstallFailed(v, InstallFailure.SIGNED_DIFFERENTLY) to
                text(R.string.settings_updates_install_conflict, v),
            UpdateStatus.InstallFailed(v, InstallFailure.INCOMPATIBLE) to
                text(R.string.settings_updates_install_incompatible, v),
            UpdateStatus.InstallFailed(v, InstallFailure.NO_SPACE) to
                text(R.string.settings_updates_install_storage, v),
            UpdateStatus.InstallFailed(v, InstallFailure.PACKAGE_GONE) to
                text(R.string.settings_updates_install_package_gone, v),
            UpdateStatus.InstallFailed(v, InstallFailure.NOT_THIS_APP) to
                text(R.string.settings_updates_install_not_this_app),
            UpdateStatus.InstallFailed(v, InstallFailure.NOT_NEWER) to
                text(R.string.settings_updates_install_not_newer, v),
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

    @Test
    fun `an install waiting for the permission opens its page from its own button`() {
        show()
        update = UpdateState(UpdateStatus.NeedsInstallPermission("1.5.0-beta"), UpdateAction.AllowInstalls)

        compose.onNodeWithText(text(R.string.settings_updates_allow_installs)).performScrollTo().performClick()

        assertEquals(1, permissionPages)
        assertEquals(0, installs)
    }

    @Test
    fun `while its switch is off there is no updates section, and About still shows`() {
        compose.setContent {
            CatsRadarTheme {
                SettingsScreen(state = SettingsState(updatesShown = false, about = sampleAbout))
            }
        }

        compose.onNodeWithText(text(R.string.settings_updates_check)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.settings_updates)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.settings_about)).performScrollTo()
    }

    private val sampleAbout = AboutState(
        version = "1.4.1-beta (7)",
        build = "release · 5989a92c1f3e",
        device = "Google Pixel 7",
        androidRelease = "16",
        sdkInt = 36,
    )

    private fun show() {
        compose.setContent {
            CatsRadarTheme {
                SettingsScreen(
                    state = SettingsState(updatesShown = true, update = update),
                    onCheckForUpdatesClick = { checks++ },
                    onInstallUpdateClick = { installs++ },
                    onAllowInstallsClick = { permissionPages++ },
                )
            }
        }
    }
}
