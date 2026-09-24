package dev.catsradar.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.detail.AddPhoto
import dev.catsradar.presentation.detail.EncounterDetailState
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CoatPicker
import dev.catsradar.ui.components.SectionCard
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
    onTakePhotoClick: () -> Unit = {},
    onPickPhotoClick: () -> Unit = {},
    onPhotoClick: () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        when (state) {
            EncounterDetailState.Loading -> Unit
            is EncounterDetailState.Loaded -> LoadedDetail(
                state,
                onDeleteClick = onDeleteClick,
                onCoatClick = onCoatClick,
                onTakePhotoClick = onTakePhotoClick,
                onPickPhotoClick = onPickPhotoClick,
                onPhotoClick = onPhotoClick,
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
    onTakePhotoClick: () -> Unit = {},
    onPickPhotoClick: () -> Unit = {},
    onPhotoClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val photoPath = state.photoPath
        val addPhoto = state.addPhoto
        if (photoPath != null) {
            AsyncImage(
                model = photoPath,
                contentDescription = stringResource(R.string.detail_photo_description),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .clickable(
                        onClickLabel = stringResource(R.string.detail_open_photo),
                        role = Role.Image,
                        onClick = onPhotoClick,
                    ),
                contentScale = ContentScale.Crop,
            )
        } else if (addPhoto != null) {
            AddPhotoCard(addPhoto, onTakePhotoClick = onTakePhotoClick, onPickPhotoClick = onPickPhotoClick)
        }
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = state.dayLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = state.timeLabel, style = MaterialTheme.typography.displayMedium)
        }
        WhereCard(state)
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
private fun WhereCard(state: EncounterDetailState.Loaded, modifier: Modifier = Modifier) {
    SectionCard(R.string.detail_where, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = stringResource(state.location.labelRes()), style = MaterialTheme.typography.bodyLarge)
            state.coordinatesLabel?.let { coordinates ->
                Text(
                    text = coordinates,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
)

private val sampleNoLocation = EncounterDetailState.Loaded(
    dayLabel = "Yesterday",
    timeLabel = "09:05",
    location = LocationLabel.NONE,
    coordinatesLabel = null,
    accuracyMeters = null,
    addPhoto = AddPhoto.READY,
)
