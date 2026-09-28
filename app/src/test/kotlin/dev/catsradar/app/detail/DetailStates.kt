package dev.catsradar.app.detail

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.detail.CatPage
import dev.catsradar.presentation.detail.EncounterDetailState
import dev.catsradar.ui.detail.DetailCarouselTestTag
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.assertEquals

internal fun loadedWith(page: CatPage): EncounterDetailState.Loaded =
    EncounterDetailState.Loaded(pages = persistentListOf(page), currentId = page.id, currentNumber = 1)

internal fun loadedOn(onScreen: CatPage, vararg pages: CatPage): EncounterDetailState.Loaded =
    EncounterDetailState.Loaded(
        pages = persistentListOf(*pages),
        currentId = onScreen.id,
        currentNumber = pages.indexOf(onScreen) + 1,
    )

internal val scrollsVertically = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

// performScrollTo() scrolls only the nearest scrollable, the photo carousel, not the screen's list.
internal fun ComposeContentTestRule.scrollListToEnd() {
    onNode(scrollsVertically).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
}

// By index: a ScrollBy runs on into the outing's pages once the row reaches its end.
internal fun ComposeContentTestRule.scrollCarouselToEnd(photos: Int) {
    onNodeWithTag(DetailCarouselTestTag).performScrollToIndex(photos + 1)
    waitForIdle()
}

// Only a large corner splits these two points: a medium one covers the first, an extra large one misses the second.
internal fun ComposeContentTestRule.assertLargeCorner(box: Rect, outside: Color, inside: Color) {
    val pixels = onRoot().captureToImage().toPixelMap()
    with(density) {
        val out = pixels[(box.left + 7.dp.toPx()).toInt(), (box.top + 7.dp.toPx()).toInt()]
        val deep = pixels[(box.left + 9.5.dp.toPx()).toInt(), (box.top + 9.5.dp.toPx()).toInt()]
        assertEquals(outside to inside, out to deep, "outside and inside a large corner of $box")
    }
}

// No coat's ghost shape: its hem dips at the middle and swells towards the corner.
internal fun ComposeContentTestRule.assertGhostHem(box: Rect, outside: Color, inside: Color) {
    val pixels = onRoot().captureToImage().toPixelMap()
    val y = (box.top + box.height * 0.92f).toInt()
    val notch = pixels[(box.left + box.width * 0.5f).toInt(), y]
    val swell = pixels[(box.left + box.width * 0.8f).toInt(), y]
    assertEquals(outside to inside, notch to swell, "the ghost's hem in $box")
}
