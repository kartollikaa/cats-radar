package dev.catsradar.app.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.settings.SettingsState
import dev.catsradar.ui.settings.SettingsScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SaveOriginalsSettingTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val toggles = mutableListOf<Boolean>()
    private var confirms = 0
    private var cancels = 0

    @Test
    fun `the switch says it saves full-size camera originals and that the app keeps a smaller copy`() {
        show(SettingsState())

        compose.onNodeWithText("Save camera photos to gallery").assertIsDisplayed()
        compose.onNodeWithText("Full-size originals, in the Cats Radar album. The app always keeps a smaller copy")
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ru")
    fun `the switch says the same in Russian`() {
        show(SettingsState())

        compose.onNodeWithText("Сохранять фото с камеры в галерею").assertIsDisplayed()
        compose.onNodeWithText(
            "Оригиналы в полном размере — в альбоме Cats Radar. В приложении всегда остаётся уменьшенная копия",
        ).assertIsDisplayed()
    }

    @Test
    fun `tapping the switch while it is on asks to turn it off`() {
        show(SettingsState(saveOriginalsToGallery = true))

        compose.onNodeWithText("Save camera photos to gallery").performClick()

        assertEquals(listOf(false), toggles)
    }

    @Test
    fun `no confirmation shows until one is asked for`() {
        show(SettingsState())

        compose.onNodeWithText("Stop saving to the gallery?").assertDoesNotExist()
    }

    @Test
    fun `the confirmation warns that new camera photos keep only the smaller copy`() {
        show(SettingsState(confirmingSaveOriginalsOff = true))

        compose.onNodeWithText("Stop saving to the gallery?").assertIsDisplayed()
        compose.onNodeWithText(
            "New camera photos will keep only the app's smaller copy. Their full-size originals can't be recovered later.",
        ).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ru")
    fun `the confirmation warns the same in Russian`() {
        show(SettingsState(confirmingSaveOriginalsOff = true))

        compose.onNodeWithText("Не сохранять в галерею?").assertIsDisplayed()
        compose.onNodeWithText(
            "У новых фото с камеры останется только уменьшенная копия в приложении. " +
                "Оригинал в полном размере потом не вернуть.",
        ).assertIsDisplayed()
    }

    @Test
    fun `turning it off from the confirmation confirms`() {
        show(SettingsState(confirmingSaveOriginalsOff = true))

        compose.onNodeWithText("Turn off").performClick()

        assertEquals(1 to 0, confirms to cancels)
    }

    @Test
    fun `cancelling the confirmation cancels`() {
        show(SettingsState(confirmingSaveOriginalsOff = true))

        compose.onNodeWithText("Cancel").performClick()

        assertEquals(0 to 1, confirms to cancels)
    }

    private fun show(state: SettingsState) {
        compose.setContent {
            CatsRadarTheme {
                SettingsScreen(
                    state = state,
                    onSaveOriginalsChange = { toggles += it },
                    onSaveOriginalsOffConfirm = { confirms++ },
                    onSaveOriginalsOffCancel = { cancels++ },
                )
            }
        }
    }
}
