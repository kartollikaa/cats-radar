package dev.catsradar.ui.coat

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import dev.catsradar.presentation.coat.CoatOption

internal val Ginger = Color(0xFFE8833A)
internal val White = Color(0xFFF7F5F2)
internal val Brown = Color(0xFF6D4C2F)
internal val Grey = Color(0xFF9E9E9E)
internal val Black = Color(0xFF26262B)
private val DarkEyes = Color(0xFF2A2320)
private val AmberEyes = Color(0xFFE6D35A)

/**
 * How one coat is painted on a cat face: the fur, the patches laid over it, and the eyes. Null patches
 * are fur-coloured. The eyes sit on [fur] and are picked to stay visible against it.
 */
internal data class CoatLook(
    val fur: Color,
    val eyes: Color,
    val muzzle: Color? = null,
    val leftCrown: Color? = null,
    val rightCrown: Color? = null,
) {
    val colours: Set<Color> get() = setOfNotNull(fur, muzzle, leftCrown, rightCrown)
}

/** The line around every face, so a white cat on a light surface or a black one on a dark surface still has an edge. */
internal fun ColorScheme.faceRim(): Color = outline

internal fun CoatOption.look(): CoatLook = when (this) {
    CoatOption.GINGER -> CoatLook(fur = Ginger, eyes = DarkEyes)
    CoatOption.GINGER_WHITE -> CoatLook(fur = Ginger, eyes = DarkEyes, muzzle = White)
    CoatOption.WHITE -> CoatLook(fur = White, eyes = DarkEyes)
    // Calico: white with ginger and black patches, or ginger and black with only a little white.
    CoatOption.TRICOLOR_MOSTLY_WHITE ->
        CoatLook(fur = White, eyes = DarkEyes, leftCrown = Ginger, rightCrown = Black)
    CoatOption.TRICOLOR_LITTLE_WHITE ->
        CoatLook(fur = Ginger, eyes = DarkEyes, muzzle = White, rightCrown = Black)
    CoatOption.BROWN -> CoatLook(fur = Brown, eyes = AmberEyes)
    CoatOption.BROWN_WHITE -> CoatLook(fur = Brown, eyes = AmberEyes, muzzle = White)
    CoatOption.GREY -> CoatLook(fur = Grey, eyes = DarkEyes)
    CoatOption.GREY_WHITE -> CoatLook(fur = Grey, eyes = DarkEyes, muzzle = White)
    CoatOption.BLACK -> CoatLook(fur = Black, eyes = AmberEyes)
    CoatOption.BLACK_WHITE -> CoatLook(fur = Black, eyes = AmberEyes, muzzle = White)
}
