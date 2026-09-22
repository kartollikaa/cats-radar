package dev.catsradar.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    /** Whether a captured original is copied into the device gallery. On unless turned off. */
    fun saveOriginalsToGallery(): Flow<Boolean>

    suspend fun setSaveOriginalsToGallery(enabled: Boolean)
}
