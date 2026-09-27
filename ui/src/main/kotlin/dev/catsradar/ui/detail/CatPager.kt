package dev.catsradar.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.detail.CatPage
import dev.catsradar.presentation.detail.EncounterDetailState
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CoatPicker
import dev.catsradar.ui.components.SectionCard
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.persistentListOf

const val DetailPagesTestTag = "detail-pages"

/** [onPageSettle] names the cat a swipe comes to rest on, only when it is not already [state]'s cat on screen. */
@Composable
internal fun CatPager(
    state: EncounterDetailState.Loaded,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onPageSettle: (catId: String) -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onCoatClick: (CoatInteraction) -> Unit = {},
    onTakePhotoClick: (catId: String) -> Unit = {},
    onPickPhotoClick: (catId: String) -> Unit = {},
    onPhotoClick: (PhotoInteraction) -> Unit = {},
    onCoordinatesClick: (catId: String) -> Unit = {},
    onSetLocationClick: (catId: String) -> Unit = {},
) {
    val pagerState = rememberPagerState(initialPage = state.currentNumber - 1) { state.pages.size }
    val currentState by rememberUpdatedState(state)
    val currentOnPageSettle by rememberUpdatedState(onPageSettle)
    LaunchedEffect(state.currentId) {
        val onScreen = currentState.currentNumber - 1
        if (onScreen != pagerState.currentPage) pagerState.scrollToPage(onScreen)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { index ->
            val settled = currentState.pages.getOrNull(index)?.id
            if (settled != null && settled != currentState.currentId) currentOnPageSettle(settled)
        }
    }
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize().testTag(DetailPagesTestTag),
        overscrollEffect = null,
        key = { index -> state.pages[index].id },
    ) { index ->
        val page = state.pages[index]
        CatPageContent(
            page,
            contentPadding = contentPadding,
            onDeleteClick = onDeleteClick,
            onCoatClick = { coat -> onCoatClick(CoatInteraction(page.id, coat)) },
            onTakePhotoClick = { onTakePhotoClick(page.id) },
            onPickPhotoClick = { onPickPhotoClick(page.id) },
            onPhotoClick = { photoId -> onPhotoClick(PhotoInteraction(page.id, photoId)) },
            onCoordinatesClick = { onCoordinatesClick(page.id) },
            onSetLocationClick = { onSetLocationClick(page.id) },
        )
    }
}

@Composable
private fun CatPageContent(
    page: CatPage,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onDeleteClick: () -> Unit = {},
    onCoatClick: (CoatOption?) -> Unit = {},
    onTakePhotoClick: () -> Unit = {},
    onPickPhotoClick: () -> Unit = {},
    onPhotoClick: (photoId: String) -> Unit = {},
    onCoordinatesClick: () -> Unit = {},
    onSetLocationClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (page.photos.isNotEmpty()) DetailPhotoPager(page.photos, onPhotoClick = onPhotoClick)
        AddPhotoCard(
            page.addPhoto,
            progress = page.attachProgress,
            onTakePhotoClick = onTakePhotoClick,
            onPickPhotoClick = onPickPhotoClick,
        )
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = page.dayLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = page.timeLabel, style = MaterialTheme.typography.displayMedium)
        }
        WhereCard(page, onCoordinatesClick = onCoordinatesClick, onSetLocationClick = onSetLocationClick)
        SectionCard(R.string.detail_coat) {
            CoatPicker(
                selected = page.coat,
                modifier = Modifier.padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                onCoatClick = onCoatClick,
            )
        }
        OutlinedButton(
            onClick = onDeleteClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        ) {
            Text(text = stringResource(R.string.detail_delete))
        }
    }
}

@ThemePreviews
@Composable
private fun CatPagerPreview() {
    CatsRadarTheme {
        Surface { CatPager(state = samplePages, contentPadding = PaddingValues()) }
    }
}

private val samplePages = EncounterDetailState.Loaded(
    pages = persistentListOf(
        CatPage(
            id = "9c41e7a2",
            dayLabel = "Yesterday",
            timeLabel = "18:47",
            location = LocationLabel.NONE,
            coordinatesLabel = null,
            accuracyMeters = null,
            setsLocation = true,
        ),
        CatPage(
            id = "2b6d0f73",
            dayLabel = "Yesterday",
            timeLabel = "18:40",
            location = LocationLabel.NONE,
            coordinatesLabel = null,
            accuracyMeters = null,
            setsLocation = true,
        ),
    ),
    currentId = "2b6d0f73",
    currentNumber = 2,
)
