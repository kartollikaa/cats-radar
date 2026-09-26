package dev.catsradar.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.detail.AddPhoto
import dev.catsradar.presentation.detail.DetailPhoto
import dev.catsradar.presentation.detail.DetailPlace
import dev.catsradar.presentation.detail.EncounterDetailState
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.map.MapPosition
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CoatPicker
import dev.catsradar.ui.components.BackBar
import dev.catsradar.ui.components.SectionCard
import dev.catsradar.ui.components.belowBackBar
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
    onSetLocationClick: () -> Unit = {},
) {
    val belowBar = belowBackBar(contentPadding)
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
                onSetLocationClick = onSetLocationClick,
            )
            is EncounterDetailState.Deleted ->
                DeletedDetail(state, modifier = Modifier.padding(belowBar), onUndoClick = onUndoClick)
            EncounterDetailState.Missing -> CenteredMessage(R.string.detail_missing, Modifier.padding(belowBar))
        }
        BackBar(
            contentDescription = stringResource(R.string.detail_back),
            contentPadding = contentPadding,
            onBackClick = onBackClick,
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
    onSetLocationClick: () -> Unit = {},
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
        AddPhotoCard(
            state.addPhoto,
            progress = state.attachProgress,
            onTakePhotoClick = onTakePhotoClick,
            onPickPhotoClick = onPickPhotoClick,
        )
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = state.dayLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = state.timeLabel, style = MaterialTheme.typography.displayMedium)
        }
        WhereCard(state, onCoordinatesClick = onCoordinatesClick, onSetLocationClick = onSetLocationClick)
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
    mapPosition = MapPosition(latitude = 41.39864, longitude = 2.17842),
    place = DetailPlace(title = "Barcelona", country = "Spain", flag = "🇪🇸"),
)

private val sampleNoLocation = EncounterDetailState.Loaded(
    dayLabel = "Yesterday",
    timeLabel = "09:05",
    location = LocationLabel.NONE,
    coordinatesLabel = null,
    accuracyMeters = null,
    addPhoto = AddPhoto.READY,
)
