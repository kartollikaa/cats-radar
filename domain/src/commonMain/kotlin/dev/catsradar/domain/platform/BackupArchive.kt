package dev.catsradar.domain.platform

import dev.catsradar.domain.backup.BackupContents

/** Why an archive could not be read. The user is told which; no row is written for any of them. */
enum class BackupRejection {
    /** Written by a newer version of the app than this one, so its fields cannot be trusted. */
    TOO_NEW,

    /** Not one of ours, or damaged beyond the point where the manifest can be read. */
    UNREADABLE,
}

sealed interface BackupReadResult {
    data class Readable(val contents: BackupContents) : BackupReadResult
    data class Rejected(val reason: BackupRejection) : BackupReadResult
}

/**
 * Writes an archive to [target]. Photo files come from the encounters' own paths, so the archive
 * decides for itself which ones to carry.
 */
interface BackupWriter {
    /** False when the archive could not be written; nothing partial is left behind. */
    suspend fun write(target: String, contents: BackupContents): Boolean
}

interface BackupReader {
    /** Throws, rather than refusing, when the source cannot be opened or this device cannot store what it holds. */
    suspend fun read(source: String): BackupReadResult
}
