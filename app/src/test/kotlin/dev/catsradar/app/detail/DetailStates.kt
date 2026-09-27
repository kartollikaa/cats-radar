package dev.catsradar.app.detail

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import dev.catsradar.presentation.detail.CatPage
import dev.catsradar.presentation.detail.EncounterDetailState
import dev.catsradar.ui.detail.DetailCarouselTestTag
import kotlinx.collections.immutable.persistentListOf

internal fun loadedWith(page: CatPage): EncounterDetailState.Loaded =
    EncounterDetailState.Loaded(pages = persistentListOf(page), currentId = page.id, currentNumber = 1)

internal fun loadedOn(onScreen: CatPage, vararg pages: CatPage): EncounterDetailState.Loaded =
    EncounterDetailState.Loaded(
        pages = persistentListOf(*pages),
        currentId = onScreen.id,
        currentNumber = pages.indexOf(onScreen) + 1,
    )

internal val scrollsVertically = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

// performScrollTo() scrolls only the nearest scrollable, the coat row, not the screen's list.
internal fun ComposeContentTestRule.scrollListToEnd() {
    onNode(scrollsVertically).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
}

// By index: a ScrollBy runs on into the outing's pages once the row reaches its end.
internal fun ComposeContentTestRule.scrollCarouselToEnd(photos: Int) {
    onNodeWithTag(DetailCarouselTestTag).performScrollToIndex(photos + 1)
    waitForIdle()
}
