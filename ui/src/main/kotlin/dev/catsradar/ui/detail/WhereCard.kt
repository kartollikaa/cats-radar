package dev.catsradar.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.detail.CatPage
import dev.catsradar.presentation.detail.DetailPlace
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.map.MapPosition
import dev.catsradar.ui.R
import dev.catsradar.ui.components.Flag
import dev.catsradar.ui.components.NoticeCard
import dev.catsradar.ui.encounters.labelRes
import dev.catsradar.ui.map.SpotMap
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

const val WhereCardTestTag = "where-card"
const val WherePillTestTag = "where-pill"
const val NoLocationNoticeTestTag = "no-location-notice"

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WhereCard(page: CatPage, modifier: Modifier = Modifier, onCoordinatesClick: () -> Unit = {}) {
    val position = page.mapPosition
    val opensMap = if (position != null) {
        Modifier.clickable(
            onClickLabel = stringResource(R.string.detail_show_on_map),
            role = Role.Button,
            onClick = onCoordinatesClick,
        )
    } else {
        Modifier.semantics(mergeDescendants = true) {}
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WhereCardTestTag)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(opensMap)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.detail_where_you_met),
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.semantics { heading() },
        )
        position?.let {
            SpotMap(
                position = it,
                coat = page.coat,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(MaterialTheme.shapes.medium),
            )
        }
        WhereLines(page)
        if (position != null) ShowOnMapPill()
    }
}

@Composable
private fun WhereLines(page: CatPage, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        page.place?.let { PlaceLine(it) }
        Text(
            text = sourceLine(page),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        page.coordinatesLabel?.let { coordinates ->
            Text(
                text = coordinates,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun sourceLine(page: CatPage): String {
    val source = stringResource(page.location.labelRes())
    return page.accuracyMeters?.let { stringResource(R.string.detail_source_accuracy, source, it) } ?: source
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaceLine(place: DetailPlace, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.titleMediumEmphasized
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        place.flag?.let { Flag(it, style) }
        Text(
            text = place.country?.let { stringResource(R.string.detail_place_city_country, place.title, it) }
                ?: place.title,
            style = style,
        )
    }
}

// The card is the button; the pill is only its visible cue.
@Composable
private fun ShowOnMapPill() {
    Row(
        modifier = Modifier
            .testTag(WherePillTestTag)
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_nav_map),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.detail_show_on_map),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
internal fun NoLocationNotice(modifier: Modifier = Modifier, onSetLocationClick: () -> Unit = {}) {
    NoticeCard(
        iconRes = R.drawable.ic_location_on,
        modifier = modifier.testTag(NoLocationNoticeTestTag),
        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
        iconContent = MaterialTheme.colorScheme.onTertiaryContainer,
        action = {
            FilledTonalButton(onClick = onSetLocationClick) {
                Text(text = stringResource(R.string.detail_set_location))
            }
        },
    ) {
        Text(text = stringResource(R.string.detail_no_location_title), style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(R.string.detail_no_location_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@ThemePreviews
@Composable
private fun WhereCardPreview() {
    CatsRadarTheme { WhereCard(page = sampleOnTheMap, modifier = Modifier.padding(16.dp)) }
}

@ThemePreviews
@Composable
private fun WhereCardOffTheGlobePreview() {
    CatsRadarTheme { WhereCard(page = sampleOffTheGlobe, modifier = Modifier.padding(16.dp)) }
}

@ThemePreviews
@Composable
private fun NoLocationNoticePreview() {
    CatsRadarTheme { NoLocationNotice(modifier = Modifier.padding(16.dp)) }
}

private val sampleOnTheMap = CatPage(
    id = "7c2e91a4",
    dayLabel = "Today",
    timeLabel = "14:32",
    location = LocationLabel.CURRENT,
    coordinatesLabel = "41.40363, 2.17436",
    accuracyMeters = 12,
    coat = CoatOption.GINGER_WHITE,
    mapPosition = MapPosition(latitude = 41.40363, longitude = 2.17436),
    place = DetailPlace(title = "Barcelona", country = "Spain", flag = "🇪🇸"),
)

private val sampleOffTheGlobe =
    sampleOnTheMap.copy(coordinatesLabel = "95.00000, 2.17436", mapPosition = null, place = null)
