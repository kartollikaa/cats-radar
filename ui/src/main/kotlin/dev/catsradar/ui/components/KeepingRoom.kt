package dev.catsradar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import kotlinx.collections.immutable.ImmutableList

/** [content] in the room the largest of [room] takes in [style], so trading one wording for another moves nothing. */
@Composable
internal fun KeepingRoomOf(
    room: ImmutableList<String>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = contentAlignment) {
        room.forEach { Text(text = it, style = style, modifier = Modifier.alpha(0f).clearAndSetSemantics {}) }
        content()
    }
}
