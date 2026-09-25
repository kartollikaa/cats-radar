package dev.catsradar.data.db

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import kotlin.time.Instant

@Entity(
    tableName = "encounters",
    indices = [Index("deletedAt", "occurredAt"), Index("sourceDigest")],
)
data class EncounterEntity(
    @PrimaryKey val id: String,
    val occurredAt: Instant,
    val tzOffsetMinutes: Int,
    val kind: EncounterKind,
    val origin: EncounterOrigin,
    val coat: CatCoat?,
    val photoPath: String?,
    val thumbPath: String?,
    val galleryUri: String?,
    val sourceMediaUri: String? = null,
    val sourceDigest: String?,
    val lat: Double?,
    val lon: Double?,
    val accuracyMeters: Float?,
    val locationSource: LocationSource,
    val locationFixedAt: Instant?,
    val geohash: String?,
    val placeCellId: String?,
    val deviceId: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)
