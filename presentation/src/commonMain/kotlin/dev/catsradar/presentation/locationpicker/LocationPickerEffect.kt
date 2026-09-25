package dev.catsradar.presentation.locationpicker

sealed interface LocationPickerEffect {
    data object Close : LocationPickerEffect
    data object RequestLocationPermission : LocationPickerEffect
    data object PositionUnknown : LocationPickerEffect
}
