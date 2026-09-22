package dev.catsradar.data.db

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/** Builds the app's [CatsDatabase], backed by the bundled SQLite driver, for Koin wiring in `:app`. */
fun createCatsDatabase(context: Context): CatsDatabase {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(DATABASE_FILE_NAME)
    return Room.databaseBuilder<CatsDatabase>(context = appContext, name = dbFile.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

internal fun RoomDatabase.Builder<CatsDatabase>.withBundledDriver(): RoomDatabase.Builder<CatsDatabase> =
    setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO)
