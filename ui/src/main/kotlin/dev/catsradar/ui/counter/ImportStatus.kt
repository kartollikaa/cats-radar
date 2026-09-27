package dev.catsradar.ui.counter

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.catsradar.presentation.counter.ImportProgressState
import dev.catsradar.presentation.counter.ImportSummaryState
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

const val ImportThumbTestTag = "import-thumb"
const val ImportCheckTestTag = "import-check"

private val ThumbTilts = listOf(-8f, -1f, 7f)

@Composable
internal fun ImportIsland(
    progress: ImportProgressState?,
    summary: ImportSummaryState?,
    modifier: Modifier = Modifier,
    onUndoClick: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    AnimatedVisibility(
        visible = progress != null || summary != null,
        modifier = modifier,
        enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it } +
            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = ExitTransition.None,
    ) {
        when {
            summary != null -> SwipeToDismissBox(
                state = rememberSwipeToDismissBoxState(),
                backgroundContent = {},
                onDismiss = { onDismiss() },
            ) {
                IslandCard { Summary(summary, onUndoClick = onUndoClick, onDismiss = onDismiss) }
            }
            progress != null -> IslandCard { Running(progress) }
        }
    }
}

@Composable
private fun IslandCard(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowScope.Running(progress: ImportProgressState) {
    RoundIcon(R.drawable.ic_photo_library)
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.counter_import_running_count, progress.done, progress.total),
            style = MaterialTheme.typography.titleSmall,
        )
        LinearWavyProgressIndicator(
            progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RowScope.Summary(
    summary: ImportSummaryState,
    onUndoClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (summary.thumbPaths.isEmpty()) {
        RoundIcon(R.drawable.ic_check, modifier = Modifier.testTag(ImportCheckTestTag))
    } else {
        PhotoStack(summary.thumbPaths)
    }
    Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
        Text(
            text = pluralStringResource(R.plurals.counter_import_added, summary.added, summary.added),
            style = MaterialTheme.typography.titleMedium,
        )
        restOf(summary)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (summary.undoable) {
        val undoLabel = stringResource(R.string.counter_import_undo)
        FilledTonalButton(
            onClick = onUndoClick,
            contentPadding = ButtonDefaults.SmallContentPadding,
            modifier = Modifier.semantics { contentDescription = undoLabel },
        ) {
            Text(text = stringResource(R.string.counter_undo), maxLines = 1)
        }
    }
    IconButton(onClick = onDismiss) {
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = stringResource(R.string.counter_import_close),
        )
    }
}

@Composable
private fun restOf(summary: ImportSummaryState): String? {
    val skipped = summary.skipped?.let { pluralStringResource(R.plurals.counter_import_skipped, it, it) }
    val failed = summary.failed?.let { pluralStringResource(R.plurals.counter_import_failed, it, it) }
    return when {
        skipped != null && failed != null -> stringResource(R.string.counter_import_parts, skipped, failed)
        else -> skipped ?: failed
    }
}

@Composable
private fun PhotoStack(paths: ImmutableList<String>) {
    val shown = paths.take(ThumbTilts.size)
    Box(modifier = Modifier.size(width = 40.dp + 11.dp * (shown.size - 1), height = 44.dp)) {
        shown.forEachIndexed { index, path ->
            AsyncImage(
                model = path,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset(x = 11.dp * index, y = 2.dp)
                    .size(40.dp)
                    .rotate(ThumbTilts[index])
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .testTag(ImportThumbTestTag),
            )
        }
    }
}

@Composable
private fun RoundIcon(@DrawableRes iconRes: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(20.dp),
        )
    }
}

@ThemePreviews
@Composable
private fun ImportIslandPreview() {
    CatsRadarTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(16.dp)) {
            ImportIsland(progress = sampleProgress, summary = null)
            ImportIsland(progress = null, summary = sampleMixedRun)
            ImportIsland(progress = null, summary = sampleUndoneRun)
        }
    }
}

private val sampleProgress = ImportProgressState(done = 7, total = 12)
private val sampleMixedRun = ImportSummaryState(
    added = 9,
    skipped = 2,
    failed = 1,
    undoable = true,
    thumbPaths = persistentListOf(),
)
private val sampleUndoneRun = ImportSummaryState(added = 12, skipped = null, failed = null, undoable = false)
