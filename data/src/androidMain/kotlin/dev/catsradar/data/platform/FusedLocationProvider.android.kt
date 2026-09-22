package dev.catsradar.data.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dev.catsradar.domain.location.LocationFix
import dev.catsradar.domain.platform.LocationProvider
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Instant

class FusedLocationProvider(context: Context) : LocationProvider {
    private val appContext = context.applicationContext

    // Lazy: constructing this must not touch Play Services (Koin resolves this eagerly for
    // DI-graph checks; the connection should only ever be attempted from a real location call).
    private val client by lazy { LocationServices.getFusedLocationProviderClient(appContext) }

    override suspend fun getCurrentFix(timeout: Duration): LocationFix? {
        if (!hasPermission()) return null
        val cancellationSource = CancellationTokenSource()
        val location = runCatching {
            withTimeoutOrNull(timeout) {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationSource.token).await()
            }
        }.getOrNull()
        if (location == null) cancellationSource.cancel()
        return location?.toFix()
    }

    override suspend fun lastKnown(): LocationFix? {
        if (!hasPermission()) return null
        return runCatching { client.lastLocation.await() }.getOrNull()?.toFix()
    }

    private fun hasPermission(): Boolean {
        val fine = appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun Location.toFix(): LocationFix = LocationFix(
        lat = latitude,
        lon = longitude,
        accuracyMeters = accuracy,
        fixedAt = Instant.fromEpochMilliseconds(time),
    )
}
