package dev.catsradar.app.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult

/** True when [text] has the width to show all of itself on one line, neither cut short nor wrapped. */
fun SemanticsNodeInteractionsProvider.isWhole(text: String): Boolean {
    val layouts = mutableListOf<TextLayoutResult>()
    onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    val layout = layouts.single()
    // didOverflowWidth misreads a text that does not wrap, so the text's own width is compared instead.
    return layout.size.width >= layout.multiParagraph.intrinsics.maxIntrinsicWidth
}
