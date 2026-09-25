package dev.catsradar.ui.encounters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.encounters.CellLead
import dev.catsradar.presentation.encounters.EncounterCell
import dev.catsradar.presentation.encounters.EncountersLayout
import dev.catsradar.presentation.encounters.EncountersRow
import dev.catsradar.presentation.encounters.EncountersState
import dev.catsradar.presentation.encounters.GroupPosition
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.encounters.OutingHeader
import dev.catsradar.presentation.encounters.PhotoCell
import dev.catsradar.ui.R
import dev.catsradar.ui.components.EmptyState
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList

private val RowInset = 16.dp
private val CardTextInset = 12.dp

private const val LEADING_ITEM_KEY = "leading"

/** Where a list row's text starts, for a heading above [EncounterRows] that lines up with it. */
internal val EncounterListTextInset = RowInset + CardTextInset

@Composable
fun EncountersScreen(
    state: EncountersState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onEncounterClick: (String) -> Unit = {},
    onEncounterLongClick: (String) -> Unit = {},
    onSelectionDismiss: () -> Unit = {},
    onDeleteSelectedClick: () -> Unit = {},
    onUndoClick: () -> Unit = {},
    onOutingMapClick: (String) -> Unit = {},
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
    SideEffect { listState.run { if (!canScrollBackward && !isScrollInProgress) requestScrollToItem(0) } }
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
                EncounterRows(
                    rows = state.rows,
                    layout = state.layout,
                    modifier = Modifier.fillMaxSize(),
                    listState = listState,
                    contentPadding = listPadding,
                    selecting = state.isSelecting,
                    onEncounterClick = onEncounterClick,
                    onEncounterLongClick = onEncounterLongClick,
                    onOutingMapClick = onOutingMapClick,
                )
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

/** Cats grouped by outing, drawn as the Encounters tab draws them in [layout], after [leadingItem] if any. */
@Composable
internal fun EncounterRows(
    rows: ImmutableList<EncountersRow>,
    layout: EncountersLayout,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    selecting: Boolean = false,
    onEncounterClick: (String) -> Unit = {},
    onEncounterLongClick: ((String) -> Unit)? = null,
    onOutingMapClick: (String) -> Unit = {},
    leadingItem: (@Composable () -> Unit)? = null,
) {
    val list = layout == EncountersLayout.LIST
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(if (list) ListRowGap else CellGap),
    ) {
        leadingItem?.let { item(key = LEADING_ITEM_KEY, contentType = LEADING_ITEM_KEY) { it() } }
        items(items = rows, key = { it.key }, contentType = { it::class }) { row ->
            val rowModifier = Modifier.fillMaxWidth().padding(horizontal = RowInset)
            when (row) {
                is OutingHeader -> OutingHeaderRow(
                    header = row,
                    alignWithCardText = list,
                    modifier = rowModifier,
                    onMapClick = onOutingMapClick,
                )
                is EncountersRow.PhotoPair ->
                    PhotoPairRow(row, rowModifier, selecting, onEncounterClick, onEncounterLongClick)
                is EncountersRow.Tiles -> TileRow(row, rowModifier, selecting, onEncounterClick, onEncounterLongClick)
                is EncountersRow.Cards -> CardRow(row, rowModifier, selecting, onEncounterClick, onEncounterLongClick)
                is EncountersRow.Single ->
                    SingleRow(row, rowModifier, selecting, onEncounterClick, onEncounterLongClick)
            }
        }
    }
}

@Composable
private fun OutingHeaderRow(
    header: OutingHeader,
    alignWithCardText: Boolean,
    modifier: Modifier = Modifier,
    onMapClick: (String) -> Unit = {},
) {
    Row(
        modifier = modifier
            .padding(top = 8.dp)
            .heightIn(min = 40.dp)
            .then(if (alignWithCardText) Modifier.padding(start = CardTextInset, bottom = 4.dp) else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = header.label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f, fill = false),
        )
        header.mapOutingId?.let { id ->
            val description = stringResource(R.string.encounters_outing_on_map_description, header.label)
            TextButton(
                onClick = { onMapClick(id) },
                modifier = Modifier.semantics { contentDescription = description },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_map),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.encounters_outing_on_map),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyEncounters(modifier: Modifier = Modifier) {
    EmptyState(
        iconRes = R.drawable.ic_nav_pets,
        title = stringResource(R.string.encounters_empty),
        modifier = modifier,
        hint = stringResource(R.string.encounters_empty_hint),
    )
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
private fun EncountersScreenListPreview() {
    CatsRadarTheme {
        Surface { EncountersScreen(state = sampleEncountersStateList) }
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

private val sampleEncountersStateList = EncountersState(
    rows = persistentListOf(
        OutingHeader(key = "header-1", label = "Today, 14:10"),
        EncountersRow.Single(
            EncounterCell("3", "14:28", LocationLabel.CURRENT, CellLead.Coat(CoatOption.GINGER)),
            GroupPosition.FIRST,
        ),
        EncountersRow.Single(EncounterCell("2", "14:20", LocationLabel.FROM_OUTING), GroupPosition.MIDDLE),
        EncountersRow.Single(
            EncounterCell("1", "14:10", LocationLabel.FROM_OUTING, CellLead.Coat(CoatOption.BLACK)),
            GroupPosition.LAST,
        ),
        OutingHeader(key = "header-4", label = "Today, 09:05"),
        EncountersRow.Single(EncounterCell("4", "09:05", LocationLabel.NONE), GroupPosition.ONLY),
    ),
    layout = EncountersLayout.LIST,
)

// Two outings on the same day, so the preview also shows how their headers tell them apart.
private val sampleEncountersStatePopulated = EncountersState(
    rows = persistentListOf(
        OutingHeader(key = "header-1", label = "Today, 14:10"),
        EncountersRow.PhotoPair(
            first = PhotoCell("1", "14:32", LocationLabel.FROM_PHOTO, "/photos/1.jpg", "/photos/1_thumb.jpg"),
            second = PhotoCell("2", "14:30", LocationLabel.CURRENT, "/photos/2.jpg", "/photos/2_thumb.jpg"),
        ),
        EncountersRow.Tiles(
            persistentListOf(
                EncounterCell("3", "14:28", LocationLabel.CURRENT, CellLead.Coat(CoatOption.GINGER)),
                EncounterCell("4", "14:20", LocationLabel.FROM_OUTING),
                EncounterCell("5", "14:10", LocationLabel.FROM_OUTING, CellLead.Coat(CoatOption.BLACK)),
            ),
        ),
        OutingHeader(key = "header-6", label = "Today, 09:05"),
        EncountersRow.Cards(
            persistentListOf(
                EncounterCell("6", "09:20", LocationLabel.LAST_KNOWN, CellLead.Coat(CoatOption.TRICOLOR_MOSTLY_WHITE)),
                EncounterCell("7", "09:05", LocationLabel.NONE),
            ),
        ),
    ),
)

private val sampleEncountersStateSelecting = sampleEncountersStatePopulated.copy(
    rows = sampleEncountersStatePopulated.rows.map { row ->
        when (row) {
            is EncountersRow.PhotoPair -> row.copy(first = row.first.copy(selected = true))
            is EncountersRow.Tiles -> row.copy(
                cells = row.cells.map { it.copy(selected = it.id == "4") }.toPersistentList(),
            )
            else -> row
        }
    }.toPersistentList(),
    selectedIds = persistentSetOf("1", "4"),
)

private val sampleEncountersStateUndo = sampleEncountersStatePopulated.copy(removedCount = 2)
