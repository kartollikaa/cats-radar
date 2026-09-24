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
import kotlin.test.assertEquals

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

        state = RegionsState.Empty(RegionsEmptyLabel.NO_PLACES_YET)

        words.assertCountEquals(1)
    }

    @Test
    fun `each kind of empty level reads in words of its own`() {
        val words = mapOf(
            RegionsEmptyLabel.NO_PLACES_YET to R.string.regions_no_places_yet,
            RegionsEmptyLabel.NO_PLACES_HERE to R.string.regions_no_places_here,
            RegionsEmptyLabel.NO_CATS_HERE to R.string.regions_no_cats_here,
        )
        assertEquals(RegionsEmptyLabel.entries.toSet(), words.keys)
        show(RegionsState.Loading)

        words.forEach { (label, text) ->
            state = RegionsState.Empty(label)
            compose.onNodeWithText(context.getString(text)).assertExists()
        }
    }

    private fun show(initial: RegionsState) {
        state = initial
        compose.setContent { CatsRadarTheme { RegionsScreen(state = state) } }
    }
}
