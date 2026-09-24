package dev.catsradar.presentation.map

import dev.catsradar.presentation.coat.CoatOption

/**
 * What the user chose to see on the map: the cat whose outing is in [focus], the [coats] to show
 * (empty for every cat, null for a cat with none noted), and [heat].
 */
data class MapChoices(
    val focus: String? = null,
    val coats: Set<CoatOption?> = emptySet(),
    val heat: Boolean = false,
)
