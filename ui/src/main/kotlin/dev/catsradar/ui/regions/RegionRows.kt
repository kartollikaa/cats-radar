package dev.catsradar.ui.regions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.regions.RegionRowKey
import dev.catsradar.presentation.regions.RegionRowLabel
import dev.catsradar.presentation.regions.RegionRowState
import dev.catsradar.presentation.regions.RegionsHeader
import dev.catsradar.presentation.regions.RegionsTitle
import dev.catsradar.ui.R
import dev.catsradar.ui.components.HeadlineCard
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

private val ShareBarHeight = 4.dp

@Composable
internal fun RegionsHeadline(header: RegionsHeader, modifier: Modifier = Modifier) {
    HeadlineCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = header.title.text(),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = pluralStringResource(R.plurals.regions_cat_count, header.count, header.count),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
internal fun RegionRow(row: RegionRowState, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val nameColor = if (row.pseudo) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.label.text(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = nameColor,
                    modifier = Modifier.weight(1f),
                )
                Text(text = row.countLabel, style = MaterialTheme.typography.titleMedium)
            }
            ShareBar(share = row.share)
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShareBar(share: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ShareBarHeight)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        // At least a round dot, so a row holding one cat among hundreds never reads as holding none.
        Box(
            modifier = Modifier
                .widthIn(min = ShareBarHeight)
                .fillMaxWidth(share)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun RegionsTitle.text(): String = when (this) {
    RegionsTitle.AllPlaces -> stringResource(R.string.regions_title)
    is RegionsTitle.Of -> label.text()
}

@Composable
private fun RegionRowLabel.text(): String = when (this) {
    is RegionRowLabel.Named -> name
    is RegionRowLabel.Coordinates -> stringResource(R.string.regions_area_at, text)
    RegionRowLabel.Unresolved -> stringResource(R.string.regions_unresolved)
    RegionRowLabel.NoCity -> stringResource(R.string.regions_no_city)
    RegionRowLabel.NoLocation -> stringResource(R.string.regions_no_location)
}

@ThemePreviews
@Composable
private fun RegionsHeadlinePreview() {
    CatsRadarTheme {
        RegionsHeadline(
            header = RegionsHeader(RegionsTitle.Of(RegionRowLabel.Named("Barcelona")), count = 97),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@ThemePreviews
@Composable
private fun RegionRowPreview() {
    CatsRadarTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            RegionRow(RegionRowState(RegionRowKey.Country("ES"), RegionRowLabel.Named("Spain"), "128", 0.85f, false))
            RegionRow(RegionRowState(RegionRowKey.Unresolved, RegionRowLabel.Unresolved, "6", 0.04f, pseudo = true))
        }
    }
}
