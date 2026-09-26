package dev.catsradar.ui.counter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
internal fun LocationPermissionHint(
    onAction: (LocationHintAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    NoticeCard(iconRes = R.drawable.ic_location_on, modifier = modifier) {
        Text(text = stringResource(R.string.counter_location_hint), style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onAction(LocationHintAction.GRANT) }) {
                Text(text = stringResource(R.string.counter_location_grant))
            }
            TextButton(onClick = { onAction(LocationHintAction.DISMISS) }) {
                Text(text = stringResource(R.string.counter_location_dismiss))
            }
        }
    }
}

@ThemePreviews
@Composable
private fun LocationPermissionHintPreview() {
    CatsRadarTheme { LocationPermissionHint(onAction = {}, modifier = Modifier.padding(16.dp)) }
}
