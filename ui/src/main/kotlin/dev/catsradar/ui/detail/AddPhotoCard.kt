package dev.catsradar.ui.detail

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.detail.AddPhoto
import dev.catsradar.presentation.detail.AttachProgress
import dev.catsradar.ui.R
import dev.catsradar.ui.components.SectionCard
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
internal fun AddPhotoCard(
    addPhoto: AddPhoto,
    modifier: Modifier = Modifier,
    progress: AttachProgress? = null,
    onTakePhotoClick: () -> Unit = {},
    onPickPhotoClick: () -> Unit = {},
) {
    val enabled = addPhoto == AddPhoto.READY
    SectionCard(R.string.detail_photo, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = onTakePhotoClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                ButtonLabel(R.drawable.ic_photo_camera, R.string.detail_take_photo)
            }
            OutlinedButton(onClick = onPickPhotoClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                ButtonLabel(R.drawable.ic_photo_library, R.string.detail_pick_photo)
            }
            if (addPhoto == AddPhoto.ATTACHING) AttachingBar(progress)
        }
    }
}

@Composable
private fun AttachingBar(progress: AttachProgress?) {
    if (progress == null) {
        val attaching = stringResource(R.string.detail_photo_attaching)
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().semantics { contentDescription = attaching })
    } else {
        val attached =
            pluralStringResource(R.plurals.detail_photos_attaching, progress.total, progress.done, progress.total)
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = attached },
        )
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
private fun AddPhotoCardPreview() {
    CatsRadarTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(16.dp)) {
            AddPhotoCard(addPhoto = AddPhoto.READY)
            AddPhotoCard(addPhoto = AddPhoto.ATTACHING)
            AddPhotoCard(addPhoto = AddPhoto.ATTACHING, progress = AttachProgress(done = 2, total = 5))
        }
    }
}
