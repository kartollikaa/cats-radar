package dev.catsradar.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.catsradar.presentation.detail.DetailPhoto
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import dev.catsradar.ui.theme.ViewerColors
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun DetailPhotoPager(
    photos: ImmutableList<DetailPhoto>,
    modifier: Modifier = Modifier,
    onPhotoClick: (photoId: String) -> Unit = {},
) {
    val pagerState = rememberPagerState { photos.size }
    var photosSeen by rememberSaveable { mutableIntStateOf(photos.size) }
    LaunchedEffect(photos.size) {
        if (photos.size > photosSeen) pagerState.animateScrollToPage(photos.lastIndex)
        photosSeen = photos.size
    }
    Box(modifier = modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.extraLarge)) {
        HorizontalPager(state = pagerState, key = { photos[it].id }) { page ->
            val photo = photos[page]
            AsyncImage(
                model = photo.path,
                contentDescription = stringResource(R.string.detail_photo_description),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clickable(
                        onClickLabel = stringResource(R.string.detail_open_photo),
                        role = Role.Image,
                        onClick = { onPhotoClick(photo.id) },
                    ),
                contentScale = ContentScale.Crop,
            )
        }
        if (photos.size > 1) {
            val page = pagerState.currentPage + 1
            val description = stringResource(R.string.viewer_position_description, page, photos.size)
            Text(
                text = stringResource(R.string.viewer_position, page, photos.size),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .background(ViewerColors.ChromeScrim, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 2.dp)
                    .semantics { contentDescription = description },
                color = ViewerColors.OnStage,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@ThemePreviews
@Composable
private fun DetailPhotoPagerPreview() {
    CatsRadarTheme { DetailPhotoPager(photos = samplePhotos, modifier = Modifier.padding(16.dp)) }
}

private val samplePhotos = persistentListOf(
    DetailPhoto(id = "5f1c2d9e", path = "photos/5f1c2d9e-4b7a.jpg"),
    DetailPhoto(id = "8a03b6c1", path = "photos/8a03b6c1-77d2.jpg"),
)
