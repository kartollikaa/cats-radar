package dev.catsradar.data.platform

import android.content.Context
import android.location.Address
import android.location.Geocoder
import dev.catsradar.domain.platform.GeocodeResult
import dev.catsradar.domain.platform.PlaceName
import dev.catsradar.domain.platform.ReverseGeocoder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidReverseGeocoder(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ReverseGeocoder {

    override suspend fun resolve(lat: Double, lon: Double): GeocodeResult = withContext(ioDispatcher) {
        // Geocoder.isPresent() is false on devices with no geocoding backend at all — typically
        // ones without Google services — and no amount of retrying will change that.
        if (!Geocoder.isPresent()) return@withContext GeocodeResult.Unavailable

        val addresses = runCatching {
            @Suppress("DEPRECATION") // the listener overload is API 33+; minSdk here is 29
            Geocoder(context).getFromLocation(lat, lon, 1)
        }.getOrNull()

        addresses?.firstOrNull()?.toPlaceName()?.let(GeocodeResult::Resolved) ?: GeocodeResult.Failed
    }

    private fun Address.toPlaceName(): PlaceName? {
        val name = PlaceName(
            countryCode = countryCode,
            countryName = countryName,
            adminArea = adminArea,
            locality = locality,
            subLocality = subLocality,
        )
        // An address with nothing in it is the same as no address: treating it as resolved would
        // retire the cell with no name to show.
        return name.takeIf { it != PlaceName() }
    }
}
