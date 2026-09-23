package dev.catsradar.data.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.location.LocationFix
import dev.catsradar.domain.platform.LocationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

class FusedLocationProvider(context: Context, private val clock: Clock) : LocationProvider {
    private val appContext = context.applicationContext

    // Lazy: constructing this must not touch Play Services (Koin resolves this eagerly for
    // DI-graph checks; the connection should only ever be attempted from a real location call).
    private val client by lazy { LocationServices.getFusedLocationProviderClient(appContext) }

    override suspend fun getCurrentFix(timeout: Duration): LocationFix? {
        if (!hasPermission()) return null
        val cancellationSource = CancellationTokenSource()
        val location = awaitOrNull {
            withTimeoutOrNull(timeout) {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationSource.token).await()
            }
        }
        if (location == null) cancellationSource.cancel()
        return location?.toFix()
    }

    override suspend fun lastKnown(): LocationFix? {
        if (!hasPermission()) return null
        return awaitOrNull { client.lastLocation.await() }?.toFix()
    }

    override fun trackFixes(): Flow<LocationFix> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it.toFix()) }
            }
        }
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            Tuning.TRACK_FIX_INTERVAL.inWholeMilliseconds,
        ).build()
        if (!hasPermission() || !requestUpdates(request, callback)) {
            close()
            return@callbackFlow
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    @Suppress("SwallowedException") // a permission revoked between the check and the call ends the fixes, not the app
    private fun requestUpdates(request: LocationRequest, callback: LocationCallback): Boolean =
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            true
        } catch (e: SecurityException) {
            false
        }

    private fun hasPermission(): Boolean {
        val fine = appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun Location.toFix(): LocationFix = LocationFix(
        lat = latitude,
        lon = longitude,
        accuracyMeters = accuracyOrNull(),
        // `time` may come from the satellite clock, which a phone's own clock can disagree with.
        fixedAt = fixTime(clock.now(), SystemClock.elapsedRealtimeNanos(), elapsedRealtimeNanos, time),
    )
}

// getAccuracy() reads 0 when a location carries no accuracy, which would pass for a perfect fix.
internal fun Location.accuracyOrNull(): Float? = if (hasAccuracy()) accuracy else null

/** When a fix was taken, on [now]'s clock, from its age on the uptime clock; 0 means no uptime stamp. */
internal fun fixTime(now: Instant, nowUptimeNanos: Long, fixUptimeNanos: Long, fixUtcMillis: Long): Instant =
    if (fixUptimeNanos <= 0L) {
        Instant.fromEpochMilliseconds(fixUtcMillis)
    } else {
        now - (nowUptimeNanos - fixUptimeNanos).coerceAtLeast(0L).nanoseconds
    }

// Cancellation must propagate to the caller; only a genuine Play Services failure (a missing
// module, a SecurityException on a permission revoked mid-call, ...) falls through to null.
@Suppress("TooGenericExceptionCaught", "SwallowedException")
private suspend fun <T> awaitOrNull(block: suspend () -> T): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
