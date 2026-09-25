package dev.catsradar.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.detail.AddPhoto
import dev.catsradar.presentation.detail.DetailPhoto
import dev.catsradar.presentation.detail.EncounterDetailState
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CoatPicker
import dev.catsradar.ui.components.CenterAppBar
import dev.catsradar.ui.components.CenterAppBarDefaults
import dev.catsradar.ui.components.SectionCard
import dev.catsradar.ui.encounters.labelRes
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.persistentListOf

@Composable
fun EncounterDetailScreen(
    state: EncounterDetailState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onBackClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onUndoClick: () -> Unit = {},
    onCoatClick: (CoatOption?) -> Unit = {},
    onTakePhotoClick: () -> Unit = {},
    onPickPhotoClick: () -> Unit = {},
    onPhotoClick: (photoId: String) -> Unit = {},
    onCoordinatesClick: () -> Unit = {},
) {
    val layoutDirection = LocalLayoutDirection.current
    val start = contentPadding.calculateStartPadding(layoutDirection)
    val end = contentPadding.calculateEndPadding(layoutDirection)
    val top = contentPadding.calculateTopPadding()
    val belowBar = PaddingValues(
        start = start,
        top = top + CenterAppBarDefaults.Height,
        end = end,
        bottom = contentPadding.calculateBottomPadding(),
    )
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            EncounterDetailState.Loading -> Unit
            is EncounterDetailState.Loaded -> LoadedDetail(
                state,
                contentPadding = belowBar,
                onDeleteClick = onDeleteClick,
                onCoatClick = onCoatClick,
                onTakePhotoClick = onTakePhotoClick,
                onPickPhotoClick = onPickPhotoClick,
                onPhotoClick = onPhotoClick,
                onCoordinatesClick = onCoordinatesClick,
            )
            is EncounterDetailState.Deleted ->
                DeletedDetail(state, modifier = Modifier.padding(belowBar), onUndoClick = onUndoClick)
            EncounterDetailState.Missing -> CenteredMessage(R.string.detail_missing, Modifier.padding(belowBar))
        }
        CenterAppBar(
            modifier = Modifier.padding(start = start, top = top, end = end),
            startContent = {
                FilledTonalIconButton(onClick = onBackClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.detail_back),
                    )
                }
            },
        )
    }
}

@Composable
private fun LoadedDetail(
    state: EncounterDetailState.Loaded,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onDeleteClick: () -> Unit = {},
    onCoatClick: (CoatOption?) -> Unit = {},
    onTakePhotoClick: () -> Unit = {},
    onPickPhotoClick: () -> Unit = {},
    onPhotoClick: (photoId: String) -> Unit = {},
    onCoordinatesClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (state.photos.isNotEmpty()) DetailPhotoPager(state.photos, onPhotoClick = onPhotoClick)
        AddPhotoCard(state.addPhoto, onTakePhotoClick = onTakePhotoClick, onPickPhotoClick = onPickPhotoClick)
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = state.dayLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = state.timeLabel, style = MaterialTheme.typography.displayMedium)
        }
        WhereCard(state, onCoordinatesClick = onCoordinatesClick)
        SectionCard(R.string.detail_coat) {
            CoatPicker(
                selected = state.coat,
                modifier = Modifier.padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                onCoatClick = onCoatClick,
            )
        }
        OutlinedButton(
            onClick = onDeleteClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        ) {
            Text(text = stringResource(R.string.detail_delete))
        }
    }
}

@Composable
private fun WhereCard(
    state: EncounterDetailState.Loaded,
    modifier: Modifier = Modifier,
    onCoordinatesClick: () -> Unit = {},
) {
    val opensMap = if (state.onTheMap) {
        Modifier.clickable(
            onClickLabel = stringResource(R.string.detail_show_on_map),
            role = Role.Button,
            onClick = onCoordinatesClick,
        )
    } else {
        Modifier.semantics(mergeDescendants = true) {}
    }
    SectionCard(R.string.detail_where, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(opensMap)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = stringResource(state.location.labelRes()), style = MaterialTheme.typography.bodyLarge)
            state.coordinatesLabel?.let { coordinates ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = coordinates,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.onTheMap) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (state.onTheMap) {
                        Icon(
                            painter = painterResource(R.drawable.ic_nav_map),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            state.accuracyMeters?.let { accuracy ->
                Text(
                    text = stringResource(R.string.detail_accuracy, accuracy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeletedDetail(
    state: EncounterDetailState.Deleted,
    modifier: Modifier = Modifier,
    onUndoClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.detail_deleted),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (state.undoVisible) {
            AssistChip(onClick = onUndoClick, label = { Text(text = stringResource(R.string.detail_undo)) })
        }
    }
}

@Composable
private fun CenteredMessage(@StringRes textRes: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text = stringResource(textRes), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@ThemePreviews
@Composable
private fun EncounterDetailScreenLoadedPreview() {
    CatsRadarTheme {
        Surface { EncounterDetailScreen(state = sampleLoaded) }
    }
}

@ThemePreviews
@Composable
private fun EncounterDetailScreenNoLocationPreview() {
    CatsRadarTheme {
        Surface { EncounterDetailScreen(state = sampleNoLocation) }
    }
}

@ThemePreviews
@Composable
private fun EncounterDetailScreenDeletedPreview() {
    CatsRadarTheme {
        Surface { EncounterDetailScreen(state = EncounterDetailState.Deleted(undoVisible = true)) }
    }
}

@ThemePreviews
@Composable
private fun EncounterDetailScreenMissingPreview() {
    CatsRadarTheme {
        Surface { EncounterDetailScreen(state = EncounterDetailState.Missing) }
    }
}

private val sampleLoaded = EncounterDetailState.Loaded(
    dayLabel = "Today",
    timeLabel = "14:32",
    location = LocationLabel.CURRENT,
    coordinatesLabel = "41.39864, 2.17842",
    accuracyMeters = 12,
    photos = persistentListOf(
        DetailPhoto(id = "5f1c2d9e", path = "photos/5f1c2d9e-4b7a.jpg"),
        DetailPhoto(id = "8a03b6c1", path = "photos/8a03b6c1-77d2.jpg"),
    ),
    onTheMap = true,
)

private val sampleNoLocation = EncounterDetailState.Loaded(
    dayLabel = "Yesterday",
    timeLabel = "09:05",
    location = LocationLabel.NONE,
    coordinatesLabel = null,
    accuracyMeters = null,
    addPhoto = AddPhoto.READY,
)
