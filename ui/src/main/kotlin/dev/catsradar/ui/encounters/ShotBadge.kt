package dev.catsradar.ui.encounters

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

/** Hidden from screen readers: the cell showing it must say how many cats it holds. */
@Composable
internal fun ShotBadge(
    count: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            // Its own merge root, or the card around it would read the bare number out with the rest.
            .semantics(mergeDescendants = true) { hideFromAccessibility() }
            .background(containerColor, CircleShape)
            .padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_nav_pets),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp),
        )
        Text(text = count.toString(), style = MaterialTheme.typography.labelMedium, color = contentColor)
    }
}

@Composable
internal fun shotDescription(count: Int): String =
    pluralStringResource(R.plurals.encounters_shot_description, count, count)

@ThemePreviews
@Composable
private fun ShotBadgePreview() {
    CatsRadarTheme {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShotBadge(count = 3)
            ShotBadge(
                count = 12,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
