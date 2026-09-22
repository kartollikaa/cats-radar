package dev.catsradar.domain.backup

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.PlaceCell

/** Everything a backup carries, in the app's own terms; how it is written down is the archive's business. */
data class BackupContents(
    val encounters: List<Encounter> = emptyList(),
    val placeCells: List<PlaceCell> = emptyList(),
)

/**
 * What a merge decided: [encounters] and [placeCells] are the rows that must be written, already
 * resolved against what was there. Rows the local database already agrees with are absent, so an
 * import of a backup taken a moment ago writes nothing.
 */
data class MergeResult(
    val encounters: List<Encounter> = emptyList(),
    val placeCells: List<PlaceCell> = emptyList(),
    val added: Int = 0,
    val updated: Int = 0,
    val unchanged: Int = 0,
)
