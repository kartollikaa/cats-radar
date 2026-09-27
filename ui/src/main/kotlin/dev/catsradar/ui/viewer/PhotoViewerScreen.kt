package dev.catsradar.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.viewer.PhotoViewerState
import dev.catsradar.presentation.viewer.ViewerPhoto
import dev.catsradar.ui.R
import dev.catsradar.ui.components.CenterAppBar
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import dev.catsradar.ui.theme.ViewerColors
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage

@Composable
fun PhotoViewerScreen(
    state: PhotoViewerState,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onOpenInGalleryClick: (photoId: String) -> Unit = {},
    onRemovePhotoClick: (photoId: String) -> Unit = {},
    onCancelPhotoRemoval: () -> Unit = {},
    onConfirmPhotoRemoval: () -> Unit = {},
) {
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    val showing = state as? PhotoViewerState.Showing
    PhotoViewerStage(
        showing = showing,
        chromeVisible = chromeVisible,
        modifier = modifier,
        onPhotoClick = { chromeVisible = !chromeVisible },
        onBackClick = onBackClick,
        onOpenInGalleryClick = onOpenInGalleryClick,
        onRemovePhotoClick = onRemovePhotoClick,
    )
    if (showing != null) {
        PhotoRemovalDialog(showing, onCancelPhotoRemoval, onConfirmPhotoRemoval)
    }
}

@Composable
private fun PhotoViewerStage(
    showing: PhotoViewerState.Showing?,
    chromeVisible: Boolean,
    modifier: Modifier = Modifier,
    onPhotoClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onOpenInGalleryClick: (String) -> Unit = {},
    onRemovePhotoClick: (String) -> Unit = {},
) {
    val dismissState = remember { ViewerDismissState() }
    val dismissScope = rememberCoroutineScope()
    val currentOnBackClick by rememberUpdatedState(onBackClick)
    var heightPx by remember { mutableFloatStateOf(0f) }
    val dismissTransform = viewerDismissTransform(dismissState.offsetY, heightPx)
    val effectiveChromeVisible = chromeVisible && dismissState.offsetY == 0f
    SystemBarsVisibility(visible = effectiveChromeVisible)
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat() }
            .background(ViewerColors.Stage.copy(alpha = dismissTransform.stageAlpha)),
    ) {
        val pagerState = showing?.let { rememberViewerPagerState(it) }
        if (showing != null && pagerState != null) {
            PhotoPager(
                showing = showing,
                pagerState = pagerState,
                onPhotoClick = onPhotoClick,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = dismissState.offsetY
                        scaleX = dismissTransform.scale
                        scaleY = dismissTransform.scale
                    },
                photoModifier = Modifier
                    .fillMaxSize()
                    .dragToDismiss(
                        state = dismissState,
                        scope = dismissScope,
                        heightPx = heightPx,
                        onDismiss = { currentOnBackClick() },
                    ),
            )
        }
        ViewerChrome(
            state = ViewerChromeState(showing = showing, currentPage = pagerState?.currentPage),
            visible = effectiveChromeVisible,
            onBackClick = onBackClick,
            onOpenInGalleryClick = onOpenInGalleryClick,
            onRemovePhotoClick = onRemovePhotoClick,
        )
    }
}

@Composable
private fun PhotoPager(
    showing: PhotoViewerState.Showing,
    pagerState: PagerState,
    onPhotoClick: () -> Unit,
    modifier: Modifier = Modifier,
    photoModifier: Modifier = Modifier,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        key = { showing.photos[it].id },
    ) { page ->
        ZoomableAsyncImage(
            model = showing.photos[page].path,
            contentDescription = stringResource(R.string.detail_photo_description),
            modifier = photoModifier,
            onClick = { onPhotoClick() },
        )
    }
}

private data class ViewerChromeState(
    val showing: PhotoViewerState.Showing?,
    val currentPage: Int?,
)

@Composable
private fun BoxScope.ViewerChrome(
    state: ViewerChromeState,
    visible: Boolean,
    onBackClick: () -> Unit = {},
    onOpenInGalleryClick: (String) -> Unit = {},
    onRemovePhotoClick: (String) -> Unit = {},
) {
    val showing = state.showing
    val onScreen = state.currentPage?.let { showing?.photos?.getOrNull(it) }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.TopStart),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        ViewerTopBar(
            showing = showing,
            opensInGallery = onScreen?.opensInGallery == true,
            canRemove = onScreen != null,
            onBackClick = onBackClick,
            onOpenInGalleryClick = { onScreen?.let { onOpenInGalleryClick(it.id) } },
            onRemovePhotoClick = { onScreen?.let { onRemovePhotoClick(it.id) } },
        )
    }
    if (showing != null && state.currentPage != null && showing.photos.size > 1) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PagePosition(page = state.currentPage, count = showing.photos.size)
        }
    }
}

@Composable
private fun rememberViewerPagerState(showing: PhotoViewerState.Showing): PagerState {
    val pagerState = rememberPagerState(initialPage = showing.firstPage) { showing.photos.size }
    val photoIds = showing.photos.map { it.id }
    var displayedPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
    var previousPhotoIds by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(pagerState, photoIds) {
        val oldIndex = previousPhotoIds.indexOf(displayedPhotoId).takeIf { it >= 0 } ?: pagerState.currentPage
        val keptIndex = photoIds.indexOf(displayedPhotoId)
        val targetPage = if (keptIndex >= 0) keptIndex else oldIndex.coerceAtMost(photoIds.lastIndex)
        pagerState.scrollToPage(targetPage)
        displayedPhotoId = photoIds[targetPage]
        previousPhotoIds = photoIds
        snapshotFlow { pagerState.currentPage }.collectLatest { page ->
            displayedPhotoId = photoIds.getOrNull(page)
        }
    }
    return pagerState
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
    canRemove: Boolean,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onOpenInGalleryClick: () -> Unit = {},
    onRemovePhotoClick: () -> Unit = {},
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
            titlePadding = PaddingValues(horizontal = if (opensInGallery) 112.dp else 64.dp),
            startContent = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.viewer_back),
                    )
                }
            },
            endContent = {
                Row {
                    if (canRemove) {
                        IconButton(onClick = onRemovePhotoClick) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.viewer_remove_photo),
                            )
                        }
                    }
                    if (opensInGallery) {
                        IconButton(onClick = onOpenInGalleryClick) {
                            Icon(
                                painter = painterResource(R.drawable.ic_photo_library),
                                contentDescription = stringResource(R.string.viewer_open_in_gallery),
                            )
                        }
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
