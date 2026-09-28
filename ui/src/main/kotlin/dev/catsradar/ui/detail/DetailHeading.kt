package dev.catsradar.ui.detail

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.detail.CatPage
import dev.catsradar.presentation.detail.DetailPlace
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.titleRes
import dev.catsradar.ui.components.Flag
import dev.catsradar.ui.components.OutlinedLabel
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

const val DetailFactsTestTag = "detail-facts"

@Composable
internal fun DetailHeading(page: CatPage, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(page.coat.titleRes()),
            style = MaterialTheme.typography.headlineMediumEmphasized,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            // The labels are not buttons: TalkBack hears the row as one line of facts.
            modifier = Modifier.testTag(DetailFactsTestTag).semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FactLabel(R.drawable.ic_calendar, page.dayLabel)
            FactLabel(R.drawable.ic_schedule, page.timeLabel)
            page.place?.let { PlaceLabel(it) }
        }
    }
}

@Composable
private fun FactLabel(@DrawableRes iconRes: Int, text: String) {
    OutlinedLabel {
        Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(16.dp))
        Text(text = text)
    }
}

@Composable
private fun PlaceLabel(place: DetailPlace) {
    OutlinedLabel {
        place.flag?.let { Flag(flag = it, style = MaterialTheme.typography.labelLarge) }
        Text(text = place.title)
    }
}

@ThemePreviews
@Composable
private fun DetailHeadingPreview() {
    CatsRadarTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            DetailHeading(page = samplePlaced)
            DetailHeading(page = sampleBare)
        }
    }
}

private val samplePlaced = CatPage(
    id = "5f1c2d9e",
    dayLabel = "Yesterday",
    timeLabel = "4:12 PM",
    location = LocationLabel.CURRENT,
    coordinatesLabel = "41.40150, 2.16000",
    accuracyMeters = 10,
    coat = CoatOption.GINGER_WHITE,
    place = DetailPlace(title = "Barcelona", country = "Spain", flag = "🇪🇸"),
)

private val sampleBare = CatPage(
    id = "2b6d0f73",
    dayLabel = "Sep 24, 2026",
    timeLabel = "11:17 PM",
    location = LocationLabel.NONE,
    coordinatesLabel = null,
    accuracyMeters = null,
)
