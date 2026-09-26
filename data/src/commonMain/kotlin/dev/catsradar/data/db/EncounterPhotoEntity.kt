package dev.catsradar.data.db

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation
import kotlin.time.Instant

@Entity(
    tableName = "encounter_photos",
    foreignKeys = [
        ForeignKey(
            entity = EncounterEntity::class,
            parentColumns = ["id"],
            childColumns = ["encounterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("encounterId"), Index("sourceDigest"), Index("shotId")],
)
data class EncounterPhotoEntity(
    @PrimaryKey val id: String,
    val encounterId: String,
    val photoPath: String,
    val thumbPath: String?,
    val galleryUri: String?,
    val sourceMediaUri: String?,
    val sourceDigest: String?,
    val deviceId: String,
    val addedAt: Instant,
    val shotId: String,
)

data class EncounterWithPhotos(
    @Embedded val encounter: EncounterEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["encounterId"])
    val photos: List<EncounterPhotoEntity>,
)
