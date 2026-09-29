package dev.catsradar.ui.components

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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
    titleRoom: ImmutableList<String> = persistentListOf(),
    supportingRoom: ImmutableList<String> = persistentListOf(),
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KeepingRoomOf(titleRoom, titleStyle) {
                Text(
                    text = title,
                    style = titleStyle,
                    modifier = Modifier.semantics { heading() },
                )
            }
            supporting?.let {
                KeepingRoomOf(supportingRoom, MaterialTheme.typography.bodyMedium) {
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
