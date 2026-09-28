package dev.catsradar.ui.detail

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.detail.AddPhoto
import dev.catsradar.presentation.detail.AttachProgress
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CatFace
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

const val NoPhotoBlockTestTag = "no-photo-block"
const val NoPhotoFaceTestTag = "no-photo-face"
const val NoPhotoPawTestTag = "no-photo-paw"

@Composable
internal fun NoPhotoBlock(
    coat: CoatOption?,
    addPhoto: AddPhoto,
    modifier: Modifier = Modifier,
    progress: AttachProgress? = null,
    onTakePhotoClick: () -> Unit = {},
    onPickPhotoClick: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 5f)
            .testTag(NoPhotoBlockTestTag)
            .clip(MaterialTheme.shapes.large)
            .background(colors.primaryContainer)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        if (coat != null) {
            CatFace(coat = coat, modifier = Modifier.size(170.dp).testTag(NoPhotoFaceTestTag))
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_nav_pets),
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(120.dp).testTag(NoPhotoPawTestTag),
            )
        }
        Text(
            text = stringResource(R.string.detail_no_photo),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onPrimaryContainer,
        )
        PhotoButtons(
            enabled = addPhoto == AddPhoto.READY,
            onTakePhotoClick = onTakePhotoClick,
            onPickPhotoClick = onPickPhotoClick,
        )
        if (addPhoto == AddPhoto.ATTACHING) AttachingBar(progress, Modifier.width(200.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PhotoButtons(enabled: Boolean, onTakePhotoClick: () -> Unit, onPickPhotoClick: () -> Unit) {
    val colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    // At the largest fonts the pair cannot share a line, and stacking beats breaking a word in two.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(
            ButtonGroupDefaults.ConnectedSpaceBetween,
            Alignment.CenterHorizontally,
        ),
        verticalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        Button(
            onClick = onTakePhotoClick,
            shapes = ButtonShapes(
                ButtonGroupDefaults.connectedLeadingButtonShape,
                ButtonGroupDefaults.connectedLeadingButtonPressShape,
            ),
            enabled = enabled,
            colors = colors,
        ) {
            ButtonLabel(R.drawable.ic_photo_camera, R.string.detail_take_photo)
        }
        Button(
            onClick = onPickPhotoClick,
            shapes = ButtonShapes(
                ButtonGroupDefaults.connectedTrailingButtonShape,
                ButtonGroupDefaults.connectedTrailingButtonPressShape,
            ),
            enabled = enabled,
            colors = colors,
        ) {
            ButtonLabel(R.drawable.ic_photo_library, R.string.detail_gallery)
        }
    }
}

@Composable
private fun RowScope.ButtonLabel(@DrawableRes iconRes: Int, @StringRes textRes: Int) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.IconSize),
    )
    Text(text = stringResource(textRes), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
}

@ThemePreviews
@Composable
private fun NoPhotoBlockPreview() {
    CatsRadarTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NoPhotoBlock(coat = CoatOption.GINGER_WHITE, addPhoto = AddPhoto.READY)
            NoPhotoBlock(coat = null, addPhoto = AddPhoto.ATTACHING)
        }
    }
}
