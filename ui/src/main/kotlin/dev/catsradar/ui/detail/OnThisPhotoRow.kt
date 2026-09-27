package dev.catsradar.ui.detail

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.detail.ShotCat
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CatFace
import dev.catsradar.ui.coat.labelRes
import dev.catsradar.ui.components.SectionCard
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

private val FaceSize = 44.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OnThisPhotoRow(
    cats: ImmutableList<ShotCat>,
    modifier: Modifier = Modifier,
    onCatClick: (catId: String) -> Unit = {},
) {
    SectionCard(R.string.detail_on_this_photo, modifier = modifier) {
        FlowRow(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            cats.forEach { cat ->
                val label = stringResource(cat.coat?.labelRes() ?: R.string.coat_not_specified)
                val ringColor = MaterialTheme.colorScheme.primary
                val ring = if (cat.onScreen) Modifier.border(3.dp, ringColor, CircleShape) else Modifier
                Box(
                    modifier = Modifier
                        .size(FaceSize)
                        .clip(CircleShape)
                        .then(ring)
                        .selectable(selected = cat.onScreen, onClick = { onCatClick(cat.id) })
                        .semantics { contentDescription = label }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val coat = cat.coat
                    if (coat != null) {
                        CatFace(coat = coat, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_nav_pets),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun OnThisPhotoRowPreview() {
    CatsRadarTheme {
        OnThisPhotoRow(
            cats = persistentListOf(
                ShotCat("1", CoatOption.GINGER, onScreen = false),
                ShotCat("2", CoatOption.BLACK_WHITE, onScreen = true),
                ShotCat("3", coat = null, onScreen = false),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}
