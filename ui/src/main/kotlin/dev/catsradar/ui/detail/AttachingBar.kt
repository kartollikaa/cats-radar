package dev.catsradar.ui.detail

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.catsradar.presentation.detail.AttachProgress
import dev.catsradar.ui.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AttachingBar(progress: AttachProgress?, modifier: Modifier = Modifier) {
    if (progress == null) {
        val attaching = stringResource(R.string.detail_photo_attaching)
        LinearWavyProgressIndicator(modifier = modifier.semantics { contentDescription = attaching })
    } else {
        val attached =
            pluralStringResource(R.plurals.detail_photos_attaching, progress.total, progress.done, progress.total)
        LinearWavyProgressIndicator(
            progress = { progress.fraction },
            modifier = modifier.semantics { contentDescription = attached },
        )
    }
}
