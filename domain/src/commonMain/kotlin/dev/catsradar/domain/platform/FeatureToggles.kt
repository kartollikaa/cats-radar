package dev.catsradar.domain.platform

import kotlinx.coroutines.flow.Flow

/** A part of the app that can be switched on or off from outside a release. */
enum class Feature {
    IN_APP_UPDATES,
}

interface FeatureToggles {
    /** Off until the switch says otherwise; emits again whenever the switch changes. */
    fun isOn(feature: Feature): Flow<Boolean>
}
