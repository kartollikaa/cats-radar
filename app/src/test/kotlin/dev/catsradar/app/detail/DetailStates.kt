package dev.catsradar.app.detail

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performSemanticsAction
import dev.catsradar.presentation.detail.CatPage
import dev.catsradar.presentation.detail.EncounterDetailState
import kotlinx.collections.immutable.persistentListOf

internal fun loadedWith(page: CatPage): EncounterDetailState.Loaded =
    EncounterDetailState.Loaded(pages = persistentListOf(page), currentId = page.id, currentNumber = 1)

internal val scrollsVertically = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

// performScrollTo() scrolls only the nearest scrollable, the coat row, not the screen's list.
internal fun ComposeContentTestRule.scrollListToEnd() {
    onNode(scrollsVertically).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
}
