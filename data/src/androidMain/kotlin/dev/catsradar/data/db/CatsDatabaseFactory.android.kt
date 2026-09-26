package dev.catsradar.data.db

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/** Builds the app's [CatsDatabase], backed by the bundled SQLite driver, for Koin wiring in `:app`. */
fun createCatsDatabase(context: Context): CatsDatabase {
    val appContext = context.applicationContext
    return catsDatabaseBuilder(appContext, appContext.getDatabasePath(DATABASE_FILE_NAME).absolutePath).build()
}

internal fun catsDatabaseBuilder(context: Context, path: String): RoomDatabase.Builder<CatsDatabase> =
    Room.databaseBuilder<CatsDatabase>(context = context, name = path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(MigrationFrom3To4, MigrationFrom4To5, MigrationFrom5To6)

internal fun <T : RoomDatabase> RoomDatabase.Builder<T>.withBundledDriver(): RoomDatabase.Builder<T> =
    setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO)
