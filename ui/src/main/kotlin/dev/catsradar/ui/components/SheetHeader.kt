package dev.catsradar.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
internal fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ChangingText(title) {
                Text(
                    text = it,
                    style = titleStyle,
                    modifier = Modifier.semantics { heading() },
                )
            }
            supporting?.let { words ->
                ChangingText(words) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val WordsOutMillis = 90
private const val WordsInMillis = 220

// Not Crossfade, which holds the longer wording's height and overlaps both: the old words leave, then the new arrive.
@Composable
private fun ChangingText(text: String, content: @Composable (String) -> Unit) {
    AnimatedContent(
        targetState = text,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = {
            fadeIn(tween(WordsInMillis, delayMillis = WordsOutMillis)) togetherWith fadeOut(tween(WordsOutMillis))
        },
        label = "words",
    ) {
        content(it)
    }
}

@Composable
internal fun SheetActions(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, content = content)
}

@ThemePreviews
@Composable
private fun SheetHeaderPreview() {
    CatsRadarTheme {
        SheetHeader(
            title = "Show cats by coat",
            supporting = "Only cats of the marked coats stay on the map",
            modifier = Modifier.padding(16.dp),
        )
    }
}
