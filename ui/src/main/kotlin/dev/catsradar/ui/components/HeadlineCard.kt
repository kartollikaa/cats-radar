package dev.catsradar.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

/** The primary-container card a screen's headline number sits in, above its rows. */
@Composable
internal fun HeadlineCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        content = content,
    )
}

@ThemePreviews
@Composable
private fun HeadlineCardPreview() {
    CatsRadarTheme {
        HeadlineCard(modifier = Modifier.padding(16.dp)) {
            Text(text = "147", style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(24.dp))
        }
    }
}
