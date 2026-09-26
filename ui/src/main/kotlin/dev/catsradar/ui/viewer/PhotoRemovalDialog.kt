package dev.catsradar.ui.viewer

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.catsradar.presentation.viewer.PhotoViewerState
import dev.catsradar.ui.R

@Composable
internal fun PhotoRemovalDialog(
    showing: PhotoViewerState.Showing,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (showing.removingPhotoId == null) return
    AlertDialog(
        onDismissRequest = { if (!showing.removalInFlight) onCancel() },
        title = { Text(stringResource(R.string.viewer_remove_photo_title)) },
        text = { Text(stringResource(R.string.viewer_remove_photo_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !showing.removalInFlight) {
                Text(stringResource(R.string.viewer_remove_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !showing.removalInFlight) {
                Text(stringResource(R.string.viewer_remove_cancel))
            }
        },
    )
}
