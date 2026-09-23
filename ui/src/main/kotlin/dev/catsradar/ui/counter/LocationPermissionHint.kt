package dev.catsradar.ui.counter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.catsradar.ui.R

@Composable
internal fun LocationPermissionHint(
    onAction: (LocationHintAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.counter_location_hint),
            style = MaterialTheme.typography.bodySmall,
        )
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
