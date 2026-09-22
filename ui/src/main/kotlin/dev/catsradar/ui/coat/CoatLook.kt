package dev.catsradar.ui.coat

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import dev.catsradar.presentation.coat.CoatOption

internal val Ginger = Color(0xFFE8833A)
internal val White = Color(0xFFF7F5F2)
internal val Brown = Color(0xFF6D4C2F)
internal val Grey = Color(0xFF9E9E9E)
internal val Black = Color(0xFF26262B)
private val TabbyStripes = Color(0xFF3B2716)
private val DarkEyes = Color(0xFF2A2320)
private val AmberEyes = Color(0xFFE6D35A)
private val PinkNose = Color(0xFFE88A9A)
private val DarkNose = Color(0xFF6E2A38)

/**
 * How one coat is painted on a cat face: the fur, the markings laid over it, the eyes and the nose.
 * A null marking is simply absent. [eyes] is chosen for contrast against [fur], and [nose] against the
 * muzzle when there is one and the fur when there is not.
 */
internal data class CoatLook(
    val fur: Color,
    val eyes: Color,
    val nose: Color,
    val muzzle: Color? = null,
    val leftCrown: Color? = null,
    val rightCrown: Color? = null,
    val stripes: Color? = null,
) {
    val colours: Set<Color> get() = setOfNotNull(fur, muzzle, leftCrown, rightCrown)
}

/** The line around every face, so a white cat on a light surface or a black one on a dark surface still has an edge. */
internal fun ColorScheme.faceRim(): Color = outline

internal fun CoatOption.look(): CoatLook = when (this) {
    CoatOption.GINGER -> CoatLook(fur = Ginger, eyes = DarkEyes, nose = DarkNose)
    CoatOption.GINGER_WHITE -> CoatLook(fur = Ginger, eyes = DarkEyes, nose = DarkNose, muzzle = White)
    CoatOption.WHITE -> CoatLook(fur = White, eyes = DarkEyes, nose = DarkNose)
    CoatOption.TRICOLOR_MOSTLY_WHITE ->
        CoatLook(fur = White, eyes = DarkEyes, nose = DarkNose, leftCrown = Ginger, rightCrown = Black)
    CoatOption.TRICOLOR_LITTLE_WHITE ->
        CoatLook(fur = Ginger, eyes = DarkEyes, nose = DarkNose, muzzle = White, rightCrown = Black)
    // Brown cats are nearly always tabbies, and the stripes are what tells brown from black on a
    // face this small: the two furs are only a shade apart.
    CoatOption.BROWN -> CoatLook(fur = Brown, eyes = AmberEyes, nose = PinkNose, stripes = TabbyStripes)
    CoatOption.BROWN_WHITE ->
        CoatLook(fur = Brown, eyes = AmberEyes, nose = DarkNose, muzzle = White, stripes = TabbyStripes)
    CoatOption.GREY -> CoatLook(fur = Grey, eyes = DarkEyes, nose = DarkNose)
    CoatOption.GREY_WHITE -> CoatLook(fur = Grey, eyes = DarkEyes, nose = DarkNose, muzzle = White)
    CoatOption.BLACK -> CoatLook(fur = Black, eyes = AmberEyes, nose = PinkNose)
    CoatOption.BLACK_WHITE -> CoatLook(fur = Black, eyes = AmberEyes, nose = DarkNose, muzzle = White)
}
