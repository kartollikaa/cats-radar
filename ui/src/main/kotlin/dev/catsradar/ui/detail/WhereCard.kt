package dev.catsradar.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.detail.DetailPlace
import dev.catsradar.presentation.detail.EncounterDetailState
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.map.MapPosition
import dev.catsradar.ui.R
import dev.catsradar.ui.components.Flag
import dev.catsradar.ui.components.SectionCard
import dev.catsradar.ui.encounters.labelRes
import dev.catsradar.ui.map.PinnedMap
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
internal fun WhereCard(
    state: EncounterDetailState.Loaded,
    modifier: Modifier = Modifier,
    onCoordinatesClick: () -> Unit = {},
) {
    val opensMap = if (state.mapPosition != null) {
        Modifier.clickable(
            onClickLabel = stringResource(R.string.detail_show_on_map),
            role = Role.Button,
            onClick = onCoordinatesClick,
        )
    } else {
        Modifier.semantics(mergeDescendants = true) {}
    }
    SectionCard(R.string.detail_where, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().then(opensMap)) {
            state.mapPosition?.let { position ->
                PinnedMap(
                    position = position,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .fillMaxWidth()
                        .aspectRatio(2f)
                        .clip(MaterialTheme.shapes.medium),
                )
            }
            WhereLines(state, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
    }
}

@Composable
private fun WhereLines(state: EncounterDetailState.Loaded, modifier: Modifier = Modifier) {
    val onTheMap = state.mapPosition != null
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.place?.let { PlaceLine(it, Modifier.padding(bottom = 4.dp)) }
        Text(text = stringResource(state.location.labelRes()), style = MaterialTheme.typography.bodyLarge)
        state.coordinatesLabel?.let { coordinates ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = coordinates,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (onTheMap) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (onTheMap) {
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

@Composable
private fun PlaceLine(place: DetailPlace, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        place.flag?.let { Flag(it, MaterialTheme.typography.headlineSmall) }
        Column {
            Text(text = place.title, style = MaterialTheme.typography.titleMedium)
            place.country?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@ThemePreviews
@Composable
private fun WhereCardPreview() {
    CatsRadarTheme { WhereCard(state = sampleOnTheMap, modifier = Modifier.padding(16.dp)) }
}

@ThemePreviews
@Composable
private fun WhereCardNotOnTheMapPreview() {
    CatsRadarTheme { WhereCard(state = sampleNotOnTheMap, modifier = Modifier.padding(16.dp)) }
}

private val sampleOnTheMap = EncounterDetailState.Loaded(
    dayLabel = "Today",
    timeLabel = "14:32",
    location = LocationLabel.CURRENT,
    coordinatesLabel = "41.40363, 2.17436",
    accuracyMeters = 12,
    mapPosition = MapPosition(latitude = 41.40363, longitude = 2.17436),
    place = DetailPlace(title = "Barcelona", country = "Spain", flag = "🇪🇸"),
)

private val sampleNotOnTheMap = EncounterDetailState.Loaded(
    dayLabel = "Today",
    timeLabel = "14:32",
    location = LocationLabel.NONE,
    coordinatesLabel = null,
    accuracyMeters = null,
)
