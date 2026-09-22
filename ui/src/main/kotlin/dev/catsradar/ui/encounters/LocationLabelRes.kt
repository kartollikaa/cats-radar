package dev.catsradar.ui.encounters

import androidx.annotation.StringRes
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.ui.R

@StringRes
fun LocationLabel.labelRes(): Int = when (this) {
    LocationLabel.FROM_PHOTO -> R.string.location_from_photo
    LocationLabel.CURRENT -> R.string.location_current
    LocationLabel.LAST_KNOWN -> R.string.location_last_known
    LocationLabel.FROM_OUTING -> R.string.location_from_outing
    LocationLabel.NONE -> R.string.location_none
}
