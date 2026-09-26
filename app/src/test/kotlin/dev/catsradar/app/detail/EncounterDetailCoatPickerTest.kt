package dev.catsradar.app.detail

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.detail.CatPage
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.ui.R
import dev.catsradar.ui.detail.EncounterDetailScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h800dp")
class EncounterDetailCoatPickerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    @Test
    fun `the last coat is on screen when the detail opens`() {
        showDetail(coat = CoatOption.BLACK_WHITE)

        assertWholeCellOnScreen(R.string.coat_black_white)
    }

    @Test
    fun `a coat in the middle of the row is on screen when the detail opens`() {
        showDetail(coat = CoatOption.GREY)

        assertWholeCellOnScreen(R.string.coat_grey)
    }

    @Test
    fun `a cat without a coat opens the picker at the first coat`() {
        showDetail(coat = null)

        assertWholeCellOnScreen(R.string.coat_ginger)
    }

    private fun showDetail(coat: CoatOption?) {
        compose.setContent {
            CatsRadarTheme {
                EncounterDetailScreen(
                    state = loadedWith(
                        CatPage(
                            id = "cat-1",
                            dayLabel = "Today",
                            timeLabel = "14:32",
                            location = LocationLabel.NONE,
                            coordinatesLabel = null,
                            accuracyMeters = null,
                            coat = coat,
                        ),
                    ),
                )
            }
        }
    }

    private fun assertWholeCellOnScreen(@StringRes name: Int) {
        val cell = compose.onNodeWithText(context.getString(name)).assertIsDisplayed().fetchSemanticsNode()

        assertEquals(cell.size.width.toFloat(), cell.boundsInWindow.width, 1f)
    }
}
