package dev.catsradar.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.execSQL

private val photoColumns = listOf("photoPath", "thumbPath", "galleryUri", "sourceMediaUri", "sourceDigest")

/**
 * Moves each cat's photo from its own row into `encounter_photos`, then drops the five columns. The photo takes
 * its cat's id, install and creation time, as `carriedPhoto` reads such a row.
 */
internal val MigrationFrom3To4 = Migration(startVersion = 3, endVersion = 4) { connection ->
    connection.execSQL(
        "CREATE TABLE IF NOT EXISTS `encounter_photos` (`id` TEXT NOT NULL, `encounterId` TEXT NOT NULL, " +
            "`photoPath` TEXT NOT NULL, `thumbPath` TEXT, `galleryUri` TEXT, `sourceMediaUri` TEXT, " +
            "`sourceDigest` TEXT, `deviceId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`encounterId`) REFERENCES `encounters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    )
    connection.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_encounter_photos_encounterId` ON `encounter_photos` (`encounterId`)",
    )
    connection.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_encounter_photos_sourceDigest` ON `encounter_photos` (`sourceDigest`)",
    )
    connection.execSQL(
        "INSERT INTO `encounter_photos` (`id`, `encounterId`, `photoPath`, `thumbPath`, `galleryUri`, " +
            "`sourceMediaUri`, `sourceDigest`, `deviceId`, `addedAt`) " +
            "SELECT `id`, `id`, `photoPath`, `thumbPath`, `galleryUri`, `sourceMediaUri`, `sourceDigest`, " +
            "`deviceId`, `createdAt` FROM `encounters` WHERE `photoPath` IS NOT NULL",
    )
    // Not the rebuild Room generates: wherever foreign keys are on, its DROP TABLE cascades into the rows just copied.
    connection.execSQL("DROP INDEX IF EXISTS `index_encounters_sourceDigest`")
    photoColumns.forEach { column -> connection.execSQL("ALTER TABLE `encounters` DROP COLUMN `$column`") }
}

// Not Room's auto-migration: it rebuilds the table and fails at start-up on any photo row whose cat is gone.
internal val MigrationFrom4To5 = Migration(startVersion = 4, endVersion = 5) { connection ->
    connection.execSQL("ALTER TABLE `encounter_photos` ADD COLUMN `shotId` TEXT")
    connection.execSQL("CREATE INDEX IF NOT EXISTS `index_encounter_photos_shotId` ON `encounter_photos` (`shotId`)")
}
