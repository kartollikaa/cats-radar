package dev.catsradar.ui.counter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** A notice floating over the top of the Counter, dropping in when it becomes [visible]. */
@Composable
internal fun Island(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it } +
            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = ExitTransition.None,
    ) {
        content()
    }
}

@Composable
@ReadOnlyComposable
internal fun islandColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

@Composable
internal fun IslandCard(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = islandColor(),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}
