package dev.catsradar.data.db

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Instant

@Entity(tableName = "walks", indices = [Index("endedAt")])
data class WalkEntity(
    @PrimaryKey val id: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val deviceId: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = WalkEntity::class,
            parentColumns = ["id"],
            childColumns = ["walkId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["walkId", "at"])],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val walkId: String,
    val at: Instant,
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float,
)
