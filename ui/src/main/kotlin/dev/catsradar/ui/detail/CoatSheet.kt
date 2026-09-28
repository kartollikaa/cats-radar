package dev.catsradar.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.coatsheet.CoatSheetHint
import dev.catsradar.presentation.coatsheet.CoatSheetState
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CatFace
import dev.catsradar.ui.coat.CoatGrid
import dev.catsradar.ui.coat.coatShapeFor
import dev.catsradar.ui.components.SheetHeader
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.persistentSetOf

const val CoatSheetLeadTestTag = "coat-sheet-lead"
const val CoatSheetFaceTestTag = "coat-sheet-face"
const val CoatSheetPawTestTag = "coat-sheet-paw"

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CoatSheetContent(
    state: CoatSheetState.Open,
    modifier: Modifier = Modifier,
    onCoatClick: (CoatOption) -> Unit = {},
    onNoCoatClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SheetHeader(
            title = stringResource(R.string.detail_coat_sheet_title),
            supporting = stringResource(state.hint.textRes()),
            titleStyle = MaterialTheme.typography.headlineSmallEmphasized,
            leading = { CoatSheetLead(state.coat) },
        )
        val ringed = remember(state.coat) { persistentSetOf(state.coat) }
        CoatGrid(
            selected = ringed,
            onCoatClick = onCoatClick,
            onUnspecifiedClick = onNoCoatClick,
            unspecifiedLabel = R.string.coat_none,
        )
    }
}

@StringRes
private fun CoatSheetHint.textRes(): Int = when (this) {
    CoatSheetHint.PICK_ANOTHER -> R.string.detail_coat_sheet_hint_change
    CoatSheetHint.TAP_ONE -> R.string.detail_coat_sheet_hint_add
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CoatSheetLead(coat: CoatOption?) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(64.dp)
            .testTag(CoatSheetLeadTestTag)
            .clip(coatShapeFor(coat).toShape())
            .background(if (coat == null) colors.surfaceContainerHighest else colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (coat == null) {
            Icon(
                painter = painterResource(R.drawable.ic_nav_pets),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(28.dp).testTag(CoatSheetPawTestTag),
            )
        } else {
            CatFace(coat = coat, modifier = Modifier.size(48.dp).testTag(CoatSheetFaceTestTag))
        }
    }
}

@ThemePreviews
@Composable
private fun CoatSheetContentPreview() {
    CatsRadarTheme {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            CoatSheetContent(state = CoatSheetState.Open(CoatOption.GINGER_WHITE, CoatSheetHint.PICK_ANOTHER))
            CoatSheetContent(state = CoatSheetState.Open(coat = null, hint = CoatSheetHint.TAP_ONE))
        }
    }
}
