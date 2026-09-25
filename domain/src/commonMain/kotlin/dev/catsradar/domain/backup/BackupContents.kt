package dev.catsradar.domain.backup

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterPhoto
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk

/** Everything a backup carries, in the app's own terms; how it is written down is the archive's business. */
data class BackupContents(
    val encounters: List<Encounter> = emptyList(),
    val placeCells: List<PlaceCell> = emptyList(),
    val walks: List<Walk> = emptyList(),
    val trackPoints: List<TrackPoint> = emptyList(),
)

/**
 * What a merge decided: the rows that must be written, already resolved against what was there.
 * Rows the local database already agrees with are absent, so an import of a backup taken a moment
 * ago writes nothing. [encounters] carry no photos: [photos] are the ones to add, each to its cat.
 * [trackPoints] are only the points to add; a route is never shortened.
 */
data class MergeResult(
    val encounters: List<Encounter> = emptyList(),
    val photos: List<EncounterPhoto> = emptyList(),
    val placeCells: List<PlaceCell> = emptyList(),
    val walks: List<Walk> = emptyList(),
    val trackPoints: List<TrackPoint> = emptyList(),
    val added: Int = 0,
    val updated: Int = 0,
    val unchanged: Int = 0,
)
