package dev.catsradar.data.db

import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

internal const val DATABASE_FILE_NAME = "cats_radar.db"
const val CATS_DATABASE_VERSION = 6

@Database(
    entities = [
        EncounterEntity::class,
        EncounterPhotoEntity::class,
        PlaceCellEntity::class,
        WalkEntity::class,
        TrackPointEntity::class,
    ],
    version = CATS_DATABASE_VERSION,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
// @ColumnTypeConverter(s), not Room 2.x's @TypeConverter(s): the old names compile but fail KSP
// with an opaque [MissingType] error on CatsDatabase that never mentions converters.
@ColumnTypeConverters(InstantConverters::class, EnumConverters::class)
@ConstructedBy(CatsDatabaseConstructor::class)
abstract class CatsDatabase : RoomDatabase() {
    abstract fun encounterDao(): EncounterDao

    abstract fun placeCellDao(): PlaceCellDao

    abstract fun walkDao(): WalkDao

    abstract fun trackPointDao(): TrackPointDao
}

// The Room KSP compiler generates the actual implementation for this expect declaration.
@Suppress("KotlinNoActualForExpect")
expect object CatsDatabaseConstructor : RoomDatabaseConstructor<CatsDatabase> {
    override fun initialize(): CatsDatabase
}
