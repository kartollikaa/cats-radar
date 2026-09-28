package dev.catsradar.ui.counter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

/** The ask for location after it was denied: Grant asks again, and × or a swipe dismisses it. */
@Composable
internal fun LocationIsland(
    visible: Boolean,
    modifier: Modifier = Modifier,
    onAction: (LocationHintAction) -> Unit = {},
) {
    Island(visible = visible, modifier = modifier) {
        SwipeToDismissBox(
            state = rememberSwipeToDismissBoxState(),
            backgroundContent = {},
            onDismiss = { onAction(LocationHintAction.DISMISS) },
        ) {
            IslandCard {
                NoticeIcon(R.drawable.ic_location_on)
                Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Text(
                        text = stringResource(R.string.counter_location_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FilledTonalButton(
                    onClick = { onAction(LocationHintAction.GRANT) },
                    contentPadding = ButtonDefaults.SmallContentPadding,
                ) {
                    Text(text = stringResource(R.string.counter_location_grant), maxLines = 1)
                }
                IconButton(onClick = { onAction(LocationHintAction.DISMISS) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.counter_location_dismiss),
                    )
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun LocationIslandPreview() {
    CatsRadarTheme { LocationIsland(visible = true, modifier = Modifier.padding(16.dp)) }
}
