package dev.catsradar.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.catsradar.presentation.viewer.PhotoViewerState
import dev.catsradar.presentation.viewer.ViewerPhoto
import dev.catsradar.ui.R
import dev.catsradar.ui.components.CenterAppBar
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import dev.catsradar.ui.theme.ViewerColors
import kotlinx.collections.immutable.persistentListOf
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage

@Composable
fun PhotoViewerScreen(
    state: PhotoViewerState,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onOpenInGalleryClick: (photoId: String) -> Unit = {},
) {
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    SystemBarsVisibility(visible = chromeVisible)
    Box(modifier = modifier.fillMaxSize().background(ViewerColors.Stage)) {
        val showing = state as? PhotoViewerState.Showing
        val pagerState = showing?.let { rememberPagerState(initialPage = it.firstPage) { it.photos.size } }
        val onScreen = if (showing != null && pagerState != null) {
            showing.photos.getOrNull(pagerState.currentPage)
        } else {
            null
        }
        if (showing != null && pagerState != null) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { showing.photos[it].id },
            ) { page ->
                ZoomableAsyncImage(
                    model = showing.photos[page].path,
                    contentDescription = stringResource(R.string.detail_photo_description),
                    modifier = Modifier.fillMaxSize(),
                    onClick = { chromeVisible = !chromeVisible },
                )
            }
        }
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopStart),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ViewerTopBar(
                showing = showing,
                opensInGallery = onScreen?.opensInGallery == true,
                onBackClick = onBackClick,
                onOpenInGalleryClick = { onScreen?.let { onOpenInGalleryClick(it.id) } },
            )
        }
        if (showing != null && pagerState != null && showing.photos.size > 1) {
            AnimatedVisibility(
                visible = chromeVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                PagePosition(page = pagerState.currentPage, count = showing.photos.size)
            }
        }
    }
}

@Composable
private fun PagePosition(page: Int, count: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.viewer_position_description, page + 1, count)
    Text(
        text = stringResource(R.string.viewer_position, page + 1, count),
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 24.dp)
            .background(ViewerColors.ChromeScrim, CircleShape)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .semantics { contentDescription = description },
        color = ViewerColors.OnStage,
        style = MaterialTheme.typography.labelLarge,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ViewerTopBar(
    showing: PhotoViewerState.Showing?,
    opensInGallery: Boolean,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onOpenInGalleryClick: () -> Unit = {},
) {
    // safeDrawing drops the status bar's height while it is hidden, so the arrow would slide as the bar returns.
    val insets = WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    val scrim = Brush.verticalGradient(listOf(ViewerColors.ChromeScrim, ViewerColors.ChromeScrim.copy(alpha = 0f)))
    CompositionLocalProvider(LocalContentColor provides ViewerColors.OnStage) {
        CenterAppBar(
            modifier = modifier
                .background(scrim)
                .windowInsetsPadding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(bottom = 16.dp),
            title = if (showing != null) {
                { TakenAt(showing) }
            } else {
                null
            },
            startContent = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.viewer_back),
                    )
                }
            },
            endContent = {
                if (opensInGallery) {
                    IconButton(onClick = onOpenInGalleryClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_photo_library),
                            contentDescription = stringResource(R.string.viewer_open_in_gallery),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun TakenAt(state: PhotoViewerState.Showing, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = state.timeLabel, maxLines = 1)
        Text(
            text = state.dayLabel,
            style = MaterialTheme.typography.bodySmall,
            color = ViewerColors.OnStageVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

private val sampleShowing = PhotoViewerState.Showing(
    photos = persistentListOf(
        ViewerPhoto(id = "5f1c2d9e", path = "photos/5f1c2d9e-4b7a.jpg", opensInGallery = true),
        ViewerPhoto(id = "8a03b6c1", path = "photos/8a03b6c1-77d2.jpg"),
    ),
    firstPage = 0,
    timeLabel = "14:32",
    dayLabel = "Yesterday",
)
