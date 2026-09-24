package dev.catsradar.app.regions

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.regions.RegionsEmptyLabel
import dev.catsradar.presentation.regions.RegionsState
import dev.catsradar.ui.R
import dev.catsradar.ui.regions.RegionsScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegionsScreenTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var state: RegionsState by mutableStateOf(RegionsState.Loading)

    @Test
    fun `a loading level draws no words, where the same level found empty draws one line`() {
        show(RegionsState.Loading)
        val words = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        words.assertCountEquals(0)

        state = RegionsState.Empty(RegionsEmptyLabel.NO_PLACES)

        words.assertCountEquals(1)
    }

    @Test
    fun `an empty level of places and an empty level of cats each read in their own words`() {
        show(RegionsState.Empty(RegionsEmptyLabel.NO_PLACES))
        compose.onNodeWithText(context.getString(R.string.regions_empty_places)).assertExists()

        state = RegionsState.Empty(RegionsEmptyLabel.NO_CATS)

        compose.onNodeWithText(context.getString(R.string.regions_empty_cats)).assertExists()
    }

    private fun show(initial: RegionsState) {
        state = initial
        compose.setContent { CatsRadarTheme { RegionsScreen(state = state) } }
    }
}
