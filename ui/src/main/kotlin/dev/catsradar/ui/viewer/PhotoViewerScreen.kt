package dev.catsradar.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.catsradar.presentation.viewer.PhotoViewerState
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import dev.catsradar.ui.theme.ViewerColors
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage

@Composable
fun PhotoViewerScreen(
    state: PhotoViewerState,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
) {
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    SystemBarsVisibility(visible = chromeVisible)
    Box(modifier = modifier.fillMaxSize().background(ViewerColors.Stage)) {
        if (state is PhotoViewerState.Showing) {
            ZoomableAsyncImage(
                model = state.photoPath,
                contentDescription = stringResource(R.string.detail_photo_description),
                modifier = Modifier.fillMaxSize(),
                onClick = { chromeVisible = !chromeVisible },
            )
        }
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopStart),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ViewerTopBar(onBackClick = onBackClick)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ViewerTopBar(modifier: Modifier = Modifier, onBackClick: () -> Unit = {}) {
    // safeDrawing drops the status bar's height while it is hidden, so the arrow would slide as the bar returns.
    val insets = WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    val scrim = Brush.verticalGradient(listOf(ViewerColors.ChromeScrim, ViewerColors.ChromeScrim.copy(alpha = 0f)))
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(scrim)
            .windowInsetsPadding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.viewer_back),
                tint = ViewerColors.OnStage,
            )
        }
    }
}

@Composable
private fun SystemBarsVisibility(visible: Boolean) {
    val view = LocalView.current
    // Outside a dialog, in a preview or a test, the viewer has no window of its own to change.
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    SideEffect {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@ThemePreviews
@Composable
private fun PhotoViewerScreenPreview() {
    CatsRadarTheme { PhotoViewerScreen(state = sampleShowing) }
}

@ThemePreviews
@Composable
private fun PhotoViewerScreenLoadingPreview() {
    CatsRadarTheme { PhotoViewerScreen(state = PhotoViewerState.Loading) }
}

private val sampleShowing =
    PhotoViewerState.Showing(photoPath = "/data/user/0/dev.catsradar/files/photos/5f1c2d9e-4b7a.jpg")
