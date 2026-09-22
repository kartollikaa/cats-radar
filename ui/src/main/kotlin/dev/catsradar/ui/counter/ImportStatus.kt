package dev.catsradar.ui.counter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.counter_import_progress, state.done, state.total),
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(
            progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
            modifier = Modifier.fillMaxWidth(),
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
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = pluralStringResource(R.plurals.counter_import_added, state.added, state.added),
                style = MaterialTheme.typography.bodyMedium,
            )
            state.skipped?.let {
                Text(
                    text = pluralStringResource(R.plurals.counter_import_skipped, it, it),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            state.failed?.let {
                Text(
                    text = pluralStringResource(R.plurals.counter_import_failed, it, it),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (state.undoable) {
            AssistChip(onClick = onUndoClick, label = { Text(text = stringResource(R.string.counter_undo)) })
        } else {
            AssistChip(onClick = onDismissClick, label = { Text(text = stringResource(R.string.counter_import_ok)) })
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
