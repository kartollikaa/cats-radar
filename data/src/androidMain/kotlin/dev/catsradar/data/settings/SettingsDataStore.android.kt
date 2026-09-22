package dev.catsradar.data.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.catsradar.domain.repository.SettingsRepository
import java.io.File

private const val SETTINGS_FILE = "settings.preferences_pb"

/** Builds the store and its repository together, so DataStore stays inside :data. */
fun createSettingsRepository(context: Context): SettingsRepository =
    DataStoreSettingsRepository(PreferenceDataStoreFactory.create { File(context.filesDir, SETTINGS_FILE) })
