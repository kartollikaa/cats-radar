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

// SQLite cannot make an existing column NOT NULL, so the table is rebuilt; unlike Room's rebuild, nothing here
// checks foreign keys, so a photo row whose cat is gone survives as it did from 4 to 5.
internal val MigrationFrom5To6 = Migration(startVersion = 5, endVersion = 6) { connection ->
    connection.execSQL(
        "CREATE TABLE IF NOT EXISTS `_new_encounter_photos` (`id` TEXT NOT NULL, `encounterId` TEXT NOT NULL, " +
            "`photoPath` TEXT NOT NULL, `thumbPath` TEXT, `galleryUri` TEXT, `sourceMediaUri` TEXT, " +
            "`sourceDigest` TEXT, `deviceId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, `shotId` TEXT NOT NULL, " +
            "PRIMARY KEY(`id`), FOREIGN KEY(`encounterId`) REFERENCES `encounters`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )",
    )
    connection.execSQL(
        "INSERT INTO `_new_encounter_photos` (`id`, `encounterId`, `photoPath`, `thumbPath`, `galleryUri`, " +
            "`sourceMediaUri`, `sourceDigest`, `deviceId`, `addedAt`, `shotId`) " +
            "SELECT `id`, `encounterId`, `photoPath`, `thumbPath`, `galleryUri`, `sourceMediaUri`, `sourceDigest`, " +
            "`deviceId`, `addedAt`, COALESCE(`shotId`, `id`) FROM `encounter_photos`",
    )
    connection.execSQL("DROP TABLE `encounter_photos`")
    connection.execSQL("ALTER TABLE `_new_encounter_photos` RENAME TO `encounter_photos`")
    connection.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_encounter_photos_encounterId` ON `encounter_photos` (`encounterId`)",
    )
    connection.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_encounter_photos_sourceDigest` ON `encounter_photos` (`sourceDigest`)",
    )
    connection.execSQL("CREATE INDEX IF NOT EXISTS `index_encounter_photos_shotId` ON `encounter_photos` (`shotId`)")
}
