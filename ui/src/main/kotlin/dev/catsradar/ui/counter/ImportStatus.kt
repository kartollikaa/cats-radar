package dev.catsradar.ui.counter

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.counter.ImportProgressState
import dev.catsradar.presentation.counter.ImportSummaryState
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
internal fun ImportProgress(state: ImportProgressState, modifier: Modifier = Modifier) {
    ImportCard(iconRes = R.drawable.ic_photo_library, modifier = modifier) {
        Text(text = stringResource(R.string.counter_import_running), style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(R.string.counter_import_progress, state.done, state.total),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            strokeCap = StrokeCap.Round,
        )
    }
}

@Composable
internal fun ImportSummary(
    state: ImportSummaryState,
    modifier: Modifier = Modifier,
    onUndoClick: () -> Unit = {},
    onDismissClick: () -> Unit = {},
) {
    ImportCard(
        iconRes = R.drawable.ic_check,
        modifier = modifier,
        trailing = {
            if (state.undoable) {
                TextButton(onClick = onUndoClick) { Text(text = stringResource(R.string.counter_undo)) }
            } else {
                TextButton(onClick = onDismissClick) { Text(text = stringResource(R.string.counter_import_ok)) }
            }
        },
    ) {
        Text(
            text = pluralStringResource(R.plurals.counter_import_added, state.added, state.added),
            style = MaterialTheme.typography.titleSmall,
        )
        state.skipped?.let { DetailLine(pluralStringResource(R.plurals.counter_import_skipped, it, it)) }
        state.failed?.let { DetailLine(pluralStringResource(R.plurals.counter_import_failed, it, it)) }
    }
}

@Composable
private fun DetailLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun ImportCard(
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = content,
            )
            trailing()
        }
    }
}

@ThemePreviews
@Composable
private fun ImportProgressPreview() {
    CatsRadarTheme { ImportProgress(state = sampleProgress, modifier = Modifier.padding(16.dp)) }
}

@ThemePreviews
@Composable
private fun ImportSummaryPreview() {
    CatsRadarTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(16.dp)) {
            ImportSummary(state = sampleCleanRun)
            ImportSummary(state = sampleMixedRun)
            ImportSummary(state = sampleUndoneRun)
        }
    }
}

private val sampleProgress = ImportProgressState(done = 7, total = 23)
private val sampleCleanRun = ImportSummaryState(added = 12, skipped = null, failed = null, undoable = true)
private val sampleMixedRun = ImportSummaryState(added = 9, skipped = 3, failed = 1, undoable = true)
private val sampleUndoneRun = ImportSummaryState(added = 12, skipped = null, failed = null, undoable = false)
