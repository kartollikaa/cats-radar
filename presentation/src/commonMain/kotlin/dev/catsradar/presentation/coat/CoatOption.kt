package dev.catsradar.presentation.coat

import dev.catsradar.domain.model.CatCoat

/**
 * The domain's `CatCoat` in presentation terms — `:ui` does not depend on `:domain` and still has
 * to draw and return a coat.
 */
enum class CoatOption {
    GINGER,
    GINGER_WHITE,
    WHITE,
    TRICOLOR_MOSTLY_WHITE,
    TRICOLOR_LITTLE_WHITE,
    BROWN,
    BROWN_WHITE,
    GREY,
    GREY_WHITE,
    BLACK,
    BLACK_WHITE,
}

fun CatCoat.toOption(): CoatOption = when (this) {
    CatCoat.GINGER -> CoatOption.GINGER
    CatCoat.GINGER_WHITE -> CoatOption.GINGER_WHITE
    CatCoat.WHITE -> CoatOption.WHITE
    CatCoat.TRICOLOR_MOSTLY_WHITE -> CoatOption.TRICOLOR_MOSTLY_WHITE
    CatCoat.TRICOLOR_LITTLE_WHITE -> CoatOption.TRICOLOR_LITTLE_WHITE
    CatCoat.BROWN -> CoatOption.BROWN
    CatCoat.BROWN_WHITE -> CoatOption.BROWN_WHITE
    CatCoat.GREY -> CoatOption.GREY
    CatCoat.GREY_WHITE -> CoatOption.GREY_WHITE
    CatCoat.BLACK -> CoatOption.BLACK
    CatCoat.BLACK_WHITE -> CoatOption.BLACK_WHITE
}

fun CoatOption.toCatCoat(): CatCoat = when (this) {
    CoatOption.GINGER -> CatCoat.GINGER
    CoatOption.GINGER_WHITE -> CatCoat.GINGER_WHITE
    CoatOption.WHITE -> CatCoat.WHITE
    CoatOption.TRICOLOR_MOSTLY_WHITE -> CatCoat.TRICOLOR_MOSTLY_WHITE
    CoatOption.TRICOLOR_LITTLE_WHITE -> CatCoat.TRICOLOR_LITTLE_WHITE
    CoatOption.BROWN -> CatCoat.BROWN
    CoatOption.BROWN_WHITE -> CatCoat.BROWN_WHITE
    CoatOption.GREY -> CatCoat.GREY
    CoatOption.GREY_WHITE -> CatCoat.GREY_WHITE
    CoatOption.BLACK -> CatCoat.BLACK
    CoatOption.BLACK_WHITE -> CatCoat.BLACK_WHITE
}
