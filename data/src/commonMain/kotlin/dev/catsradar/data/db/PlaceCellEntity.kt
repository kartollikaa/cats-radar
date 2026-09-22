package dev.catsradar.data.db

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import dev.catsradar.domain.model.PlaceStatus
import kotlin.time.Instant

@Entity(tableName = "place_cells", indices = [Index("status")])
data class PlaceCellEntity(
    @PrimaryKey val cellId: String,
    val centerLat: Double,
    val centerLon: Double,
    val countryCode: String?,
    val countryName: String?,
    val adminArea: String?,
    val locality: String?,
    val subLocality: String?,
    val status: PlaceStatus,
    val attempts: Int,
    val lastAttemptAt: Instant?,
    val resolvedAt: Instant?,
)
