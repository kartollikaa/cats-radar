package dev.catsradar.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
internal fun NoticeCard(
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    iconContainer: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconContent: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NoticeIcon(iconRes, container = iconContainer, content = iconContent)
            Column(
                modifier = Modifier.weight(1f).padding(end = 8.dp).semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = content,
            )
            action?.invoke()
        }
    }
}

@Composable
internal fun NoticeIcon(
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Box(
        modifier = modifier.size(40.dp).background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(20.dp),
        )
    }
}

@ThemePreviews
@Composable
private fun NoticeCardPreview() {
    CatsRadarTheme {
        NoticeCard(iconRes = R.drawable.ic_location_on, modifier = Modifier.padding(16.dp)) {
            Text(text = "Allow location to see where you meet cats", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
