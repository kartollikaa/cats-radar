package dev.catsradar.ui.encounters

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.encounters.EncounterListItem
import dev.catsradar.presentation.encounters.EncountersState
import dev.catsradar.presentation.encounters.GroupPosition
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.encounters.RowLead
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CatFace
import dev.catsradar.ui.coat.labelRes
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList

private val LeadingSize = 48.dp

// The header lines up with a row's content, so its inset is the row's two insets added.
private val RowOuterInset = 16.dp
private val RowInnerInset = 12.dp
private val OutingOuterCorner = 20.dp
private val OutingJoinCorner = 4.dp

@Composable
fun EncountersScreen(
    state: EncountersState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onRowClick: (String) -> Unit = {},
    onRowLongClick: (String) -> Unit = {},
    onSelectionDismiss: () -> Unit = {},
    onDeleteSelectedClick: () -> Unit = {},
    onUndoClick: () -> Unit = {},
) {
    val layoutDirection = LocalLayoutDirection.current
    // The selection bar takes the top inset, so the list below it must not add it a second time.
    val listPadding = if (state.isSelecting) {
        PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = contentPadding.calculateBottomPadding(),
        )
    } else {
        contentPadding
    }
    val listState = rememberLazyListState()
    // A keyed list keeps its first visible row in place when rows land above it, so an undone
    // outing would come back out of sight; a list resting at the top moves up to show them.
    SideEffect { if (!listState.canScrollBackward) listState.requestScrollToItem(0) }
    Column(modifier = modifier.fillMaxSize()) {
        if (state.isSelecting) {
            SelectionBar(
                selectedCount = state.selectedCount,
                topInset = contentPadding.calculateTopPadding(),
                onDismiss = onSelectionDismiss,
                onDeleteClick = onDeleteSelectedClick,
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            if (state.isEmpty) {
                EmptyEncounters(modifier = Modifier.fillMaxSize().padding(listPadding))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = listPadding,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(items = state.rows, key = { it.key }) { row ->
                        when (row) {
                            is EncounterListItem.OutingHeader -> OutingHeaderRow(row)
                            is EncounterListItem.Row -> EncounterRow(
                                row = row,
                                selecting = state.isSelecting,
                                onClick = { onRowClick(row.id) },
                                onLongClick = { onRowLongClick(row.id) },
                            )
                        }
                    }
                }
            }
            state.removedCount?.let { count ->
                UndoBar(
                    removedCount = count,
                    onUndoClick = onUndoClick,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = listPadding.calculateBottomPadding())
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun OutingHeaderRow(header: EncounterListItem.OutingHeader, modifier: Modifier = Modifier) {
    Text(
        text = header.label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RowOuterInset + RowInnerInset)
            .padding(top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun EncounterRow(
    row: EncounterListItem.Row,
    selecting: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val background = if (row.selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RowOuterInset)
            .clip(row.position.shape())
            .background(background)
            .then(if (selecting) Modifier.semantics { selected = row.selected } else Modifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(R.string.encounters_select),
            )
            .padding(horizontal = RowInnerInset, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EncounterLead(lead = row.lead, selected = row.selected)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.timeLabel, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(row.location.labelRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun GroupPosition.shape(): RoundedCornerShape {
    val top = if (this == GroupPosition.FIRST || this == GroupPosition.ONLY) OutingOuterCorner else OutingJoinCorner
    val bottom = if (this == GroupPosition.LAST || this == GroupPosition.ONLY) OutingOuterCorner else OutingJoinCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}

@Composable
private fun EncounterLead(lead: RowLead, selected: Boolean, modifier: Modifier = Modifier) {
    if (selected) {
        Box(
            modifier = modifier.size(LeadingSize).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        return
    }
    val shape = MaterialTheme.shapes.small
    val tile = modifier.size(LeadingSize).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
    when (lead) {
        is RowLead.Photo -> AsyncImage(
            model = lead.thumbnailPath,
            contentDescription = stringResource(R.string.encounters_photo_description),
            modifier = tile,
            contentScale = ContentScale.Crop,
        )
        is RowLead.Coat -> {
            val coatLabel = stringResource(lead.coat.labelRes())
            Box(modifier = tile.semantics { contentDescription = coatLabel }, contentAlignment = Alignment.Center) {
                CatFace(coat = lead.coat, modifier = Modifier.size(36.dp))
            }
        }
        RowLead.Paw -> Box(modifier = tile, contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_nav_pets),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyEncounters(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_nav_pets),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = stringResource(R.string.encounters_empty),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.encounters_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@ThemePreviews
@Composable
private fun EncountersScreenEmptyPreview() {
    CatsRadarTheme {
        Surface { EncountersScreen(state = sampleEncountersStateEmpty) }
    }
}

@ThemePreviews
@Composable
private fun EncountersScreenPopulatedPreview() {
    CatsRadarTheme {
        Surface { EncountersScreen(state = sampleEncountersStatePopulated) }
    }
}

@ThemePreviews
@Composable
private fun EncountersScreenSelectingPreview() {
    CatsRadarTheme {
        Surface { EncountersScreen(state = sampleEncountersStateSelecting) }
    }
}

@ThemePreviews
@Composable
private fun EncountersScreenUndoPreview() {
    CatsRadarTheme {
        Surface { EncountersScreen(state = sampleEncountersStateUndo) }
    }
}

private val sampleEncountersStateEmpty = EncountersState()

// Two outings on the same day, so the preview also shows how their headers tell them apart.
private val sampleEncountersStatePopulated = EncountersState(
    rows = persistentListOf(
        EncounterListItem.OutingHeader(key = "header-1", label = "Today, 14:10"),
        EncounterListItem.Row(
            id = "1",
            timeLabel = "14:32",
            location = LocationLabel.CURRENT,
            lead = RowLead.Coat(CoatOption.TRICOLOR_MOSTLY_WHITE),
            position = GroupPosition.FIRST,
        ),
        EncounterListItem.Row(
            id = "2",
            timeLabel = "14:20",
            location = LocationLabel.FROM_OUTING,
            lead = RowLead.Coat(CoatOption.BLACK),
            position = GroupPosition.MIDDLE,
        ),
        EncounterListItem.Row(
            id = "3",
            timeLabel = "14:10",
            location = LocationLabel.FROM_OUTING,
            position = GroupPosition.LAST,
        ),
        EncounterListItem.OutingHeader(key = "header-4", label = "Today, 09:05"),
        EncounterListItem.Row(id = "4", timeLabel = "09:05", location = LocationLabel.NONE),
    ),
)

private val sampleSelectedIds = persistentSetOf("1", "2")

private val sampleEncountersStateSelecting = sampleEncountersStatePopulated.copy(
    rows = sampleEncountersStatePopulated.rows.map { item ->
        if (item is EncounterListItem.Row) item.copy(selected = item.id in sampleSelectedIds) else item
    }.toPersistentList(),
    selectedIds = sampleSelectedIds,
)

private val sampleEncountersStateUndo = sampleEncountersStatePopulated.copy(removedCount = 2)
