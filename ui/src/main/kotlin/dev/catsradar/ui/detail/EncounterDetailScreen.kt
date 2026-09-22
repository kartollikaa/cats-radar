package dev.catsradar.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.detail.EncounterDetailState
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CoatPicker
import dev.catsradar.ui.encounters.labelRes
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
fun EncounterDetailScreen(
    state: EncounterDetailState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onDeleteClick: () -> Unit = {},
    onUndoClick: () -> Unit = {},
    onCoatClick: (CoatOption?) -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize().padding(contentPadding).padding(24.dp)) {
        when (state) {
            EncounterDetailState.Loading -> Unit
            is EncounterDetailState.Loaded -> LoadedDetail(
                state,
                onDeleteClick = onDeleteClick,
                onCoatClick = onCoatClick
            )
            is EncounterDetailState.Deleted -> DeletedDetail(state, onUndoClick = onUndoClick)
            EncounterDetailState.Missing -> CenteredMessage(R.string.detail_missing)
        }
    }
}

@Composable
private fun LoadedDetail(
    state: EncounterDetailState.Loaded,
    modifier: Modifier = Modifier,
    onDeleteClick: () -> Unit = {},
    onCoatClick: (CoatOption?) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val photoPath = state.photoPath
        if (photoPath != null) {
            AsyncImage(
                model = photoPath,
                contentDescription = stringResource(R.string.detail_photo_description),
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.large),
                contentScale = ContentScale.Crop,
            )
        }
        Text(text = state.dayLabel, style = MaterialTheme.typography.headlineSmall)
        Text(text = state.timeLabel, style = MaterialTheme.typography.displaySmall)
        Text(text = stringResource(state.location.labelRes()), style = MaterialTheme.typography.bodyLarge)
        val coordinates = state.coordinatesLabel
        val accuracy = state.accuracyMeters
        if (coordinates != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = stringResource(R.string.detail_coordinates), style = MaterialTheme.typography.labelMedium)
                Text(text = coordinates, style = MaterialTheme.typography.bodyLarge)
                if (accuracy != null) {
                    Text(
                        text = stringResource(R.string.detail_accuracy, accuracy),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Text(text = stringResource(R.string.detail_coat), style = MaterialTheme.typography.labelMedium)
        CoatPicker(selected = state.coat, onCoatClick = onCoatClick)
        Button(onClick = onDeleteClick, modifier = Modifier.padding(top = 16.dp)) {
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
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(text = stringResource(R.string.detail_deleted), style = MaterialTheme.typography.bodyLarge)
        if (state.undoVisible) {
            AssistChip(onClick = onUndoClick, label = { Text(text = stringResource(R.string.detail_undo)) })
        }
    }
}

@Composable
private fun CenteredMessage(@StringRes textRes: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(textRes), style = MaterialTheme.typography.bodyLarge)
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
)

private val sampleNoLocation = EncounterDetailState.Loaded(
    dayLabel = "Yesterday",
    timeLabel = "09:05",
    location = LocationLabel.NONE,
    coordinatesLabel = null,
    accuracyMeters = null,
)
