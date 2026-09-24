package dev.catsradar.ui.counter

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.persistentListOf

// Must play as ic_cat_walking does in the status bar: the same frames, in order, at the same pace.
internal val WalkFrames = persistentListOf(
    R.drawable.cat_walk_0,
    R.drawable.cat_walk_1,
    R.drawable.cat_walk_2,
    R.drawable.cat_walk_3,
    R.drawable.cat_walk_4,
    R.drawable.cat_walk_5,
    R.drawable.cat_walk_6,
    R.drawable.cat_walk_7,
)
internal const val WalkFrameMillis = 100

/** The cat walking in place while [walking], and standing still on its first frame otherwise. */
@Composable
internal fun WalkingCat(walking: Boolean, modifier: Modifier = Modifier) {
    val frame = if (walking) rememberWalkFrame() else 0
    Icon(painter = painterResource(WalkFrames[frame]), contentDescription = null, modifier = modifier)
}

@Composable
private fun rememberWalkFrame(): Int {
    val step by rememberInfiniteTransition(label = "walk").animateValue(
        initialValue = 0,
        targetValue = WalkFrames.size,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(tween(WalkFrames.size * WalkFrameMillis, easing = LinearEasing)),
        label = "frame",
    )
    return step % WalkFrames.size
}

@ThemePreviews
@Composable
private fun WalkingCatPreview() {
    CatsRadarTheme {
        Row(modifier = Modifier.padding(16.dp)) {
            WalkingCat(walking = false, modifier = Modifier.size(48.dp))
            WalkingCat(walking = true, modifier = Modifier.size(48.dp))
        }
    }
}
