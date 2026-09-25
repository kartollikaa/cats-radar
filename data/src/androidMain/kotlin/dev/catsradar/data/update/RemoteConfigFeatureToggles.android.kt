package dev.catsradar.data.update

import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import dev.catsradar.domain.platform.Feature
import dev.catsradar.domain.platform.FeatureToggles
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Firebase Remote Config booleans, one per [Feature]. A parameter never set in the console reads false,
 * which is Remote Config's own default for a boolean, so every feature starts off.
 */
class RemoteConfigFeatureToggles(private val remoteConfig: FirebaseRemoteConfig) : FeatureToggles {

    override fun isOn(feature: Feature): Flow<Boolean> = callbackFlow {
        val key = feature.parameter
        fun sendCurrent() {
            trySend(remoteConfig.getBoolean(key))
        }
        sendCurrent()
        val listening = remoteConfig.addOnConfigUpdateListener(
            object : ConfigUpdateListener {
                override fun onUpdate(configUpdate: ConfigUpdate) {
                    if (key in configUpdate.updatedKeys) remoteConfig.activate().addOnCompleteListener { sendCurrent() }
                }

                // Without the real-time channel the fetch below still brings the value, only later.
                override fun onError(error: FirebaseRemoteConfigException) = Unit
            },
        )
        remoteConfig.fetchAndActivate().addOnCompleteListener { sendCurrent() }
        awaitClose { listening.remove() }
    }.distinctUntilChanged()

    private val Feature.parameter: String
        get() = when (this) {
            Feature.IN_APP_UPDATES -> "in_app_updates"
        }
}
