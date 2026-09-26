package dev.catsradar.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

/**
 * The pinned back arrow of a screen pushed above a tab. It draws no background, so the screen scrolls under
 * it; [contentPadding] is the screen's own, and the content goes below [belowBackBar] of it.
 */
@Composable
fun BackBar(
    contentDescription: String,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
) {
    val layoutDirection = LocalLayoutDirection.current
    CenterAppBar(
        modifier = modifier.padding(
            start = contentPadding.calculateStartPadding(layoutDirection),
            top = contentPadding.calculateTopPadding(),
            end = contentPadding.calculateEndPadding(layoutDirection),
        ),
        startContent = {
            FilledTonalIconButton(onClick = onBackClick) {
                Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = contentDescription)
            }
        },
    )
}

/** [contentPadding] with the back bar's height added to its top. */
@Composable
fun belowBackBar(contentPadding: PaddingValues): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection),
        top = contentPadding.calculateTopPadding() + CenterAppBarDefaults.Height,
        end = contentPadding.calculateEndPadding(layoutDirection),
        bottom = contentPadding.calculateBottomPadding(),
    )
}

@ThemePreviews
@Composable
private fun BackBarPreview() {
    CatsRadarTheme { BackBar(contentDescription = "Back", contentPadding = PaddingValues()) }
}
