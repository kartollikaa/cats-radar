package dev.catsradar.ui.detail

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.MultiAspectCarouselItemDrawInfo
import androidx.compose.material3.carousel.MultiAspectCarouselScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.catsradar.presentation.detail.AddPhoto
import dev.catsradar.presentation.detail.AttachProgress
import dev.catsradar.presentation.detail.DetailPhoto
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.abs

const val DetailCarouselTestTag = "detail-carousel"

private val PhotoWidth = 300.dp
private val AddItemWidth = 140.dp

// The add items stand as tall as the photos beside them.
private val ItemHeight = PhotoWidth * 5 / 4

private const val TAKE_KEY = "take-a-photo"
private const val PICK_KEY = "from-gallery"

/** [photos] is never empty: a cat without one gets [NoPhotoBlock]. */
@Composable
internal fun DetailPhotoCarousel(
    photos: ImmutableList<DetailPhoto>,
    addPhoto: AddPhoto,
    modifier: Modifier = Modifier,
    progress: AttachProgress? = null,
    onPhotoClick: (photoId: String) -> Unit = {},
    onTakePhotoClick: () -> Unit = {},
    onPickPhotoClick: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    var photosSeen by rememberSaveable { mutableIntStateOf(photos.size) }
    LaunchedEffect(photos.size) {
        if (photos.size > photosSeen) listState.animateScrollToItem(photos.lastIndex)
        photosSeen = photos.size
    }
    val lastPhoto = photos.lastIndex
    val front by remember(listState, lastPhoto) { derivedStateOf { listState.photoInFront(lastPhoto) } }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PhotoRow(
            photos = photos,
            listState = listState,
            addEnabled = addPhoto == AddPhoto.READY,
            onPhotoClick = onPhotoClick,
            onTakePhotoClick = onTakePhotoClick,
            onPickPhotoClick = onPickPhotoClick,
        )
        if (photos.size > 1) PositionLabel(front + 1, photos.size, Modifier.padding(start = PageInset))
        if (addPhoto == AddPhoto.ATTACHING) {
            AttachingBar(progress, Modifier.padding(horizontal = PageInset).fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoRow(
    photos: ImmutableList<DetailPhoto>,
    listState: LazyListState,
    addEnabled: Boolean,
    modifier: Modifier = Modifier,
    onPhotoClick: (photoId: String) -> Unit = {},
    onTakePhotoClick: () -> Unit = {},
    onPickPhotoClick: () -> Unit = {},
) {
    val shape = MaterialTheme.shapes.large
    MultiAspectCarouselScope {
        LazyRow(
            state = listState,
            modifier = modifier.fillMaxWidth().testTag(DetailCarouselTestTag),
            contentPadding = PaddingValues(horizontal = PageInset),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            flingBehavior = rememberSingleAdvanceFling(listState),
        ) {
            itemsIndexed(photos, key = { _, photo -> photo.id }) { index, photo ->
                CarouselPhoto(
                    photo = photo,
                    modifier = Modifier
                        .size(PhotoWidth, ItemHeight)
                        .maskClip(shape, rememberDrawInfo(index, listState)),
                    onClick = { onPhotoClick(photo.id) },
                )
            }
            item(key = TAKE_KEY) {
                AddItem(
                    iconRes = R.drawable.ic_photo_camera,
                    labelRes = R.string.detail_take_photo,
                    enabled = addEnabled,
                    modifier = Modifier
                        .size(AddItemWidth, ItemHeight)
                        .maskClip(shape, rememberDrawInfo(photos.size, listState)),
                    onClick = onTakePhotoClick,
                )
            }
            item(key = PICK_KEY) {
                AddItem(
                    iconRes = R.drawable.ic_photo_library,
                    labelRes = R.string.detail_from_gallery,
                    enabled = addEnabled,
                    modifier = Modifier
                        .size(AddItemWidth, ItemHeight)
                        .maskClip(shape, rememberDrawInfo(photos.size + 1, listState)),
                    onClick = onPickPhotoClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberDrawInfo(index: Int, listState: LazyListState): MultiAspectCarouselItemDrawInfo =
    remember(index, listState) { MultiAspectCarouselItemDrawInfo(index, listState) }

// Scrolled onto the add items, the last photo is still the one in front.
private fun LazyListState.photoInFront(lastPhoto: Int): Int =
    (layoutInfo.visibleItemsInfo.minByOrNull { abs(it.offset) }?.index ?: 0).coerceAtMost(lastPhoto)

// One item per fling, as Material's carousels move, however hard the throw.
@Composable
private fun rememberSingleAdvanceFling(listState: LazyListState): FlingBehavior {
    val snapping = remember(listState) {
        val nearest = SnapLayoutInfoProvider(listState, SnapPosition.Start)
        object : SnapLayoutInfoProvider by nearest {
            override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float = 0f
        }
    }
    return rememberSnapFlingBehavior(snapping)
}

@Composable
private fun CarouselPhoto(photo: DetailPhoto, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    AsyncImage(
        model = photo.path,
        contentDescription = stringResource(R.string.detail_photo_description),
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClickLabel = stringResource(R.string.detail_open_photo), role = Role.Image, onClick = onClick),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun AddItem(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val content = if (enabled) onSurface else onSurface.copy(alpha = 0.38f)
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = content,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PositionLabel(number: Int, count: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.viewer_position_description, number, count)
    Box(
        modifier = modifier
            .heightIn(min = 32.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.viewer_position, number, count),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

@ThemePreviews
@Composable
private fun DetailPhotoCarouselPreview() {
    CatsRadarTheme {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            DetailPhotoCarousel(photos = samplePhotos, addPhoto = AddPhoto.READY)
            DetailPhotoCarousel(
                photos = samplePhotos,
                addPhoto = AddPhoto.ATTACHING,
                progress = AttachProgress(done = 2, total = 5),
            )
        }
    }
}

private val samplePhotos = persistentListOf(
    DetailPhoto(id = "5f1c2d9e", path = "photos/5f1c2d9e-4b7a.jpg"),
    DetailPhoto(id = "8a03b6c1", path = "photos/8a03b6c1-77d2.jpg"),
    DetailPhoto(id = "c2e94f10", path = "photos/c2e94f10-3a51.jpg"),
)
