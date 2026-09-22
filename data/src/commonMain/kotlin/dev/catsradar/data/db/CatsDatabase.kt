package dev.catsradar.data.db

import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

internal const val DATABASE_FILE_NAME = "cats_radar.db"

@Database(
    entities = [EncounterEntity::class, PlaceCellEntity::class],
    version = 1,
)
// @ColumnTypeConverter(s), not Room 2.x's @TypeConverter(s): the old names compile but fail KSP
// with an opaque [MissingType] error on CatsDatabase that never mentions converters.
@ColumnTypeConverters(InstantConverters::class, EnumConverters::class)
@ConstructedBy(CatsDatabaseConstructor::class)
abstract class CatsDatabase : RoomDatabase() {
    abstract fun encounterDao(): EncounterDao

    abstract fun placeCellDao(): PlaceCellDao
}

// The Room KSP compiler generates the actual implementation for this expect declaration.
@Suppress("KotlinNoActualForExpect")
expect object CatsDatabaseConstructor : RoomDatabaseConstructor<CatsDatabase> {
    override fun initialize(): CatsDatabase
}
