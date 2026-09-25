package dev.catsradar.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CoatGrid
import dev.catsradar.ui.components.SheetActions
import dev.catsradar.ui.components.SheetHeader
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapCoatSheet(
    shown: ImmutableSet<CoatOption?>,
    modifier: Modifier = Modifier,
    onCoatToggle: (CoatOption?) -> Unit = {},
    onClear: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        MapCoatFilter(shown = shown, onCoatToggle = onCoatToggle, onClear = onClear)
    }
}

/** [shown] may hold null, which stands for the cats with no coat noted. */
@Composable
fun MapCoatFilter(
    shown: ImmutableSet<CoatOption?>,
    modifier: Modifier = Modifier,
    onCoatToggle: (CoatOption?) -> Unit = {},
    onClear: () -> Unit = {},
) {
    Column(
        modifier = modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SheetHeader(
            title = stringResource(R.string.map_coats_title),
            supporting = stringResource(R.string.map_coats_hint),
        )
        CoatGrid(selected = shown, onCoatClick = onCoatToggle, onUnspecifiedClick = { onCoatToggle(null) })
        SheetActions {
            TextButton(onClick = onClear, enabled = shown.isNotEmpty()) {
                Text(stringResource(R.string.map_coats_all))
            }
        }
    }
}

@ThemePreviews
@Composable
private fun MapCoatFilterPreview() {
    CatsRadarTheme { MapCoatFilter(shown = persistentSetOf(CoatOption.GINGER, CoatOption.BLACK, null)) }
}
