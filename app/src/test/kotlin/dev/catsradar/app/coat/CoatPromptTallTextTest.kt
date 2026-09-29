package dev.catsradar.app.coat

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.counter.CoatCountState
import dev.catsradar.presentation.counter.CoatPromptState
import dev.catsradar.ui.R
import dev.catsradar.ui.counter.CoatPrompt
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// A small screen at the largest text, where a full count is taller than the screen.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w360dp-h640dp", fontScale = 1.5f)
@RunWith(AndroidJUnit4::class)
class CoatPromptTallTextTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `a count taller than the screen opens on Save and Not now, and scrolls up to its title`() {
        val cats = List(CoatCountState.MOST_CATS) { CoatOption.entries[it % CoatOption.entries.size] }
        compose.setContent {
            CatsRadarTheme {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    CoatPrompt(prompt = CoatPromptState("cat", "cat", null, CoatCountState(cats.toImmutableList())))
                }
            }
        }

        val save = context.resources.getQuantityString(R.plurals.counter_coat_count_save, cats.size, cats.size)
        compose.onNodeWithText(save).assertIsDisplayed()
        val notNow = context.getString(R.string.counter_coat_prompt_skip)
        compose.onNodeWithText(notNow).assertIsDisplayed()
        val title = context.resources.getQuantityString(R.plurals.counter_coat_count_title, cats.size, cats.size)
        compose.onNodeWithText(title).assertIsNotDisplayed()
        compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
    }
}
