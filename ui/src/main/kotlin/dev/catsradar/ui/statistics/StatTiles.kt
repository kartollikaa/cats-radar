package dev.catsradar.ui.statistics

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.statistics.StatisticsState
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

const val StatTileTestTag = "stat-tile"

/** Today's, the week's and the month's cats, those with a photo, and the two streaks, three to a row. */
@Composable
internal fun StatTiles(state: StatisticsState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TileRow {
            StatTile(state.todayLabel, R.string.statistics_today, Modifier.weight(1f), highlighted = true)
            StatTile(state.weekLabel, R.string.statistics_week, Modifier.weight(1f))
            StatTile(state.monthLabel, R.string.statistics_month, Modifier.weight(1f))
        }
        TileRow {
            StatTile(state.withPhotoLabel, R.string.statistics_with_photo, Modifier.weight(1f))
            StatTile(
                value = streakDays(state.currentStreak),
                labelRes = R.string.statistics_current_streak,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = streakDays(state.longestStreak),
                labelRes = R.string.statistics_longest_streak,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun streakDays(days: Int): String = pluralStringResource(R.plurals.statistics_streak_days, days, days)

@Composable
private fun TileRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun StatTile(
    value: String,
    @StringRes labelRes: Int,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .semantics(mergeDescendants = true) {}
            .testTag(StatTileTestTag),
        shape = MaterialTheme.shapes.medium,
        color = if (highlighted) colors.primaryContainer else colors.surfaceContainerLow,
        contentColor = if (highlighted) colors.onPrimaryContainer else colors.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Figure(
                value = value,
                style = MaterialTheme.typography.titleLargeEmphasized,
                unitStyle = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.bodySmall,
                color = if (highlighted) LocalContentColor.current else colors.onSurfaceVariant,
            )
        }
    }
}

@ThemePreviews
@Composable
private fun StatTilesPreview() {
    CatsRadarTheme {
        StatTiles(
            state = StatisticsState(
                todayLabel = "3",
                weekLabel = "19",
                monthLabel = "64",
                withPhotoLabel = "41",
                currentStreak = 6,
                longestStreak = 23,
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}
