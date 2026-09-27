package dev.catsradar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

const val ShareBarTestTag = "share-bar"
const val ShareBarFillTestTag = "share-bar-fill"

private val ShareBarHeight = 4.dp

/** [share] of a whole, from 0 to 1, filled in [color]. */
@Composable
internal fun ShareBar(share: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .testTag(ShareBarTestTag)
            .fillMaxWidth()
            .height(ShareBarHeight)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        // At least a round dot, so a row holding one cat among hundreds never reads as holding none.
        Box(
            modifier = Modifier
                .testTag(ShareBarFillTestTag)
                .widthIn(min = ShareBarHeight)
                .fillMaxWidth(share)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color),
        )
    }
}

@ThemePreviews
@Composable
private fun ShareBarPreview() {
    CatsRadarTheme {
        ShareBar(share = 0.6f, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp))
    }
}
