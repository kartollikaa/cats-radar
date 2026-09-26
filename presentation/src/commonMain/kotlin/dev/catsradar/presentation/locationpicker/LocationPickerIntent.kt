package dev.catsradar.presentation.locationpicker

sealed interface LocationPickerIntent {
    data object BackClicked : LocationPickerIntent
    data object WhereAmIClicked : LocationPickerIntent
    data class LocationPermissionResult(val granted: Boolean) : LocationPickerIntent
    data object MoveReached : LocationPickerIntent

    /** The point under the pin when Save was tapped. */
    data class SaveClicked(val latitude: Double, val longitude: Double) : LocationPickerIntent
}
