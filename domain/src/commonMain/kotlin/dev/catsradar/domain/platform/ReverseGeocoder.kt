package dev.catsradar.domain.platform

/** What a point turned out to be called. Every part is independently absent. */
data class PlaceName(
    val countryCode: String? = null,
    val countryName: String? = null,
    val adminArea: String? = null,
    val locality: String? = null,
    val subLocality: String? = null,
)

sealed interface GeocodeResult {
    data class Resolved(val name: PlaceName) : GeocodeResult

    /** Nothing came back for this point, or the lookup failed; worth trying again later. */
    data object Failed : GeocodeResult

    /** There is no geocoder on this device at all, so retrying will never help. */
    data object Unavailable : GeocodeResult
}

interface ReverseGeocoder {
    suspend fun resolve(lat: Double, lon: Double): GeocodeResult
}
