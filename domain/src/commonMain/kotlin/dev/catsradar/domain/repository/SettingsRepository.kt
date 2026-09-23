package dev.catsradar.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    /** Whether a captured original is copied into the device gallery. On unless turned off. */
    fun saveOriginalsToGallery(): Flow<Boolean>

    suspend fun setSaveOriginalsToGallery(enabled: Boolean)

    /** Whether the walking notification is up. Off until asked for; survives the app being killed. */
    fun walkingMode(): Flow<Boolean>

    suspend fun setWalkingMode(enabled: Boolean)

    /** The highest milestone already celebrated, so each one is announced once and only once. */
    fun lastSeenMilestone(): Flow<Int>

    suspend fun setLastSeenMilestone(value: Int)

    /** Whether the Encounters tab lays cats out as a grid rather than one row each. On unless turned off. */
    fun encountersGrid(): Flow<Boolean>

    suspend fun setEncountersGrid(enabled: Boolean)
}
