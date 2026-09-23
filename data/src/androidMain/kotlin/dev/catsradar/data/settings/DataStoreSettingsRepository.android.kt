package dev.catsradar.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import dev.catsradar.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val SaveOriginalsToGallery = booleanPreferencesKey("save_originals_to_gallery")
private val LastSeenMilestone = intPreferencesKey("last_seen_milestone")
private val WalkingMode = booleanPreferencesKey("walking_mode")
private val EncountersGrid = booleanPreferencesKey("encounters_grid")

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override fun saveOriginalsToGallery(): Flow<Boolean> =
        dataStore.data.map { it[SaveOriginalsToGallery] ?: true }

    override suspend fun setSaveOriginalsToGallery(enabled: Boolean) {
        dataStore.edit { it[SaveOriginalsToGallery] = enabled }
    }

    override fun walkingMode(): Flow<Boolean> = dataStore.data.map { it[WalkingMode] ?: false }

    override suspend fun setWalkingMode(enabled: Boolean) {
        dataStore.edit { it[WalkingMode] = enabled }
    }

    override fun lastSeenMilestone(): Flow<Int> = dataStore.data.map { it[LastSeenMilestone] ?: 0 }

    override suspend fun setLastSeenMilestone(value: Int) {
        dataStore.edit { it[LastSeenMilestone] = value }
    }

    // DataStore's data re-emits on a write to any key, so an unrelated write would repeat this value.
    override fun encountersGrid(): Flow<Boolean> =
        dataStore.data.map { it[EncountersGrid] ?: true }.distinctUntilChanged()

    override suspend fun setEncountersGrid(enabled: Boolean) {
        dataStore.edit { it[EncountersGrid] = enabled }
    }
}
