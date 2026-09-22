package dev.catsradar.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dev.catsradar.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val SaveOriginalsToGallery = booleanPreferencesKey("save_originals_to_gallery")

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override fun saveOriginalsToGallery(): Flow<Boolean> =
        dataStore.data.map { it[SaveOriginalsToGallery] ?: true }

    override suspend fun setSaveOriginalsToGallery(enabled: Boolean) {
        dataStore.edit { it[SaveOriginalsToGallery] = enabled }
    }
}
