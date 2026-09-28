package dev.catsradar.ui.counter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
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
import dev.catsradar.ui.components.NoticeIcon
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

const val ImportThumbTestTag = "import-thumb"
const val ImportCheckTestTag = "import-check"
const val ImportGalleryTestTag = "import-gallery"
const val ImportIslandTestTag = "import-island"

private val ThumbTilts = listOf(-8f, -1f, 7f)
private val ThumbSize = 40.dp
private val ThumbStep = 11.dp

@Composable
internal fun ImportIsland(
    progress: ImportProgressState?,
    summary: ImportSummaryState?,
    modifier: Modifier = Modifier,
    onUndoClick: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    Island(visible = progress != null || summary != null, modifier = modifier.testTag(ImportIslandTestTag)) {
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowScope.Running(progress: ImportProgressState) {
    if (progress.previewUris.isEmpty()) {
        NoticeIcon(R.drawable.ic_photo_library, modifier = Modifier.testTag(ImportGalleryTestTag))
    } else {
        PhotoStack(progress.previewUris, slots = ThumbTilts.size)
    }
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
        NoticeIcon(R.drawable.ic_check, modifier = Modifier.testTag(ImportCheckTestTag))
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

/** As wide as [slots] photos, so a stack that fills later keeps its neighbours in place. */
@Composable
private fun PhotoStack(paths: ImmutableList<String>, slots: Int = paths.size) {
    val shown = paths.take(ThumbTilts.size)
    val ringColor = islandColor()
    val shape = MaterialTheme.shapes.small
    val width = ThumbSize + ThumbStep * (slots.coerceIn(1, ThumbTilts.size) - 1)
    Box(modifier = Modifier.size(width = width, height = 44.dp)) {
        shown.forEachIndexed { index, path ->
            AsyncImage(
                model = path,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset(x = ThumbStep * index, y = 2.dp)
                    .size(ThumbSize)
                    .rotate(ThumbTilts[index])
                    .ring(ringColor, shape)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .testTag(ImportThumbTestTag),
            )
        }
    }
}

// Outside the edge rather than a border, which would eat into the photo itself.
private fun Modifier.ring(color: Color, shape: Shape): Modifier = drawBehind {
    val width = 2.dp.toPx()
    val outline = shape.createOutline(Size(size.width + 2 * width, size.height + 2 * width), layoutDirection, this)
    translate(-width, -width) { drawOutline(outline, color) }
}

@ThemePreviews
@Composable
private fun ImportIslandPreview() {
    CatsRadarTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(16.dp)) {
            ImportIsland(progress = sampleProgress, summary = null)
            ImportIsland(progress = sampleFilling, summary = null)
            ImportIsland(progress = null, summary = sampleMixedRun)
            ImportIsland(progress = null, summary = sampleUndoneRun)
        }
    }
}

private val sampleProgress = ImportProgressState(done = 7, total = 12)
private val sampleFilling = ImportProgressState(
    done = 1,
    total = 12,
    previewUris = persistentListOf(
        "content://media/external/images/media/1041",
        "content://media/external/images/media/1042",
    ),
)
private val sampleMixedRun = ImportSummaryState(
    added = 9,
    skipped = 2,
    failed = 1,
    undoable = true,
    thumbPaths = persistentListOf(),
)
private val sampleUndoneRun = ImportSummaryState(added = 12, skipped = null, failed = null, undoable = false)
