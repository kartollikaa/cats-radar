package dev.catsradar.presentation.coatsheet

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.presentation.coat.toOption

class CoatSheetStateMapper {
    fun map(coat: CatCoat?): CoatSheetState.Open = CoatSheetState.Open(
        coat = coat?.toOption(),
        hint = if (coat == null) CoatSheetHint.TAP_ONE else CoatSheetHint.PICK_ANOTHER,
    )
}
