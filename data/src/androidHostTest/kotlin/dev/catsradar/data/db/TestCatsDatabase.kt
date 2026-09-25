package dev.catsradar.data.db

import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.RoomDatabase

// A separate @Database over the same entities/version, adding SchemaProbeDao for tests. It needs
// no @ConstructedBy/expect-actual: it's only ever built from this Android-only test source set.
@Database(
    entities = [
        EncounterEntity::class,
        EncounterPhotoEntity::class,
        PlaceCellEntity::class,
        WalkEntity::class,
        TrackPointEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@ColumnTypeConverters(InstantConverters::class, EnumConverters::class)
internal abstract class TestCatsDatabase : RoomDatabase() {
    abstract fun encounterDao(): EncounterDao

    abstract fun placeCellDao(): PlaceCellDao

    abstract fun walkDao(): WalkDao

    abstract fun trackPointDao(): TrackPointDao

    abstract fun schemaProbeDao(): SchemaProbeDao
}
