package dev.catsradar.ui.coat

import androidx.annotation.StringRes
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.ui.R

@StringRes
fun CoatOption?.titleRes(): Int = when (this) {
    CoatOption.GINGER -> R.string.detail_title_ginger
    CoatOption.GINGER_WHITE -> R.string.detail_title_ginger_white
    CoatOption.WHITE -> R.string.detail_title_white
    CoatOption.TRICOLOR_MOSTLY_WHITE -> R.string.detail_title_tricolor_mostly_white
    CoatOption.TRICOLOR_LITTLE_WHITE -> R.string.detail_title_tricolor_little_white
    CoatOption.BROWN -> R.string.detail_title_brown
    CoatOption.BROWN_WHITE -> R.string.detail_title_brown_white
    CoatOption.GREY -> R.string.detail_title_grey
    CoatOption.GREY_WHITE -> R.string.detail_title_grey_white
    CoatOption.BLACK -> R.string.detail_title_black
    CoatOption.BLACK_WHITE -> R.string.detail_title_black_white
    null -> R.string.detail_title_no_coat
}
