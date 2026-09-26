package dev.catsradar.ui.detail

import dev.catsradar.presentation.coat.CoatOption

data class CoatInteraction(val catId: String, val coat: CoatOption?)

data class PhotoInteraction(val catId: String, val photoId: String)
