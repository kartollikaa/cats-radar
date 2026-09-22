package dev.catsradar.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import dev.catsradar.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val SaveOriginalsToGallery = booleanPreferencesKey("save_originals_to_gallery")
private val LastSeenMilestone = intPreferencesKey("last_seen_milestone")

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override fun saveOriginalsToGallery(): Flow<Boolean> =
        dataStore.data.map { it[SaveOriginalsToGallery] ?: true }

    override suspend fun setSaveOriginalsToGallery(enabled: Boolean) {
        dataStore.edit { it[SaveOriginalsToGallery] = enabled }
    }

    override fun lastSeenMilestone(): Flow<Int> = dataStore.data.map { it[LastSeenMilestone] ?: 0 }

    override suspend fun setLastSeenMilestone(value: Int) {
        dataStore.edit { it[LastSeenMilestone] = value }
    }
}
