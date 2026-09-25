package dev.catsradar.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

/**
 * A top bar whose title stays centred on the screen however wide [startContent] and [endContent]
 * are. It draws no background and applies no insets: both belong to [modifier].
 */
@Composable
fun CenterAppBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit = {},
    startContent: @Composable () -> Unit = {},
    endContent: @Composable () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(horizontal = 4.dp),
    titlePadding: PaddingValues = PaddingValues(horizontal = 64.dp),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CenterAppBarDefaults.Height)
            .padding(contentPadding),
    ) {
        Box(modifier = Modifier.align(Alignment.CenterStart), contentAlignment = Alignment.Center) {
            startContent()
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(titlePadding)
                .semantics(mergeDescendants = true) { heading() },
        ) {
            ProvideTextStyle(MaterialTheme.typography.titleMedium) { title() }
        }
        Box(modifier = Modifier.align(Alignment.CenterEnd), contentAlignment = Alignment.Center) {
            endContent()
        }
    }
}

object CenterAppBarDefaults {
    val Height = 56.dp
}

@ThemePreviews
@Composable
private fun CenterAppBarPreview() {
    CatsRadarTheme {
        CenterAppBar(
            title = { Text(text = "Barcelona") },
            startContent = { PreviewIcon(R.drawable.ic_arrow_back) },
            endContent = { PreviewIcon(R.drawable.ic_photo_library) },
        )
    }
}

@ThemePreviews
@Composable
private fun CenterAppBarStartOnlyPreview() {
    CatsRadarTheme {
        CenterAppBar(startContent = { PreviewIcon(R.drawable.ic_arrow_back) })
    }
}

@Composable
private fun PreviewIcon(@DrawableRes icon: Int) {
    IconButton(onClick = {}) { Icon(painter = painterResource(icon), contentDescription = null) }
}
