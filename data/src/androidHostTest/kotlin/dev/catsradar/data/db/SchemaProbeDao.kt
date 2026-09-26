package dev.catsradar.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.RawQuery
import androidx.room3.RoomRawQuery

/** Raw schema introspection and row-corruption injection for tests; not part of the app's data access. */
@Dao
@Suppress("TooManyFunctions") // one probe per raw read a test needs
internal interface SchemaProbeDao {
    @Query(
        "SELECT name FROM sqlite_master " +
            "WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%'",
    )
    suspend fun tableNames(): List<String>

    // Room's @Query parser only recognizes UPDATE/DELETE/SELECT/INSERT; PRAGMA needs @RawQuery.
    @RawQuery
    suspend fun tableInfo(query: RoomRawQuery): List<PragmaColumn>

    @RawQuery
    suspend fun indexList(query: RoomRawQuery): List<PragmaIndex>

    @Query("SELECT kind FROM encounters WHERE id = :id")
    suspend fun rawEncounterKind(id: String): String

    @Query("SELECT coat FROM encounters WHERE id = :id")
    suspend fun rawEncounterCoat(id: String): String?

    @Query("SELECT status FROM place_cells WHERE cellId = :cellId")
    suspend fun rawPlaceCellStatus(cellId: String): String

    @Query("SELECT COUNT(*) FROM encounters WHERE id = :id")
    suspend fun encounterRowCount(id: String): Int

    @Query("SELECT COUNT(*) FROM encounter_photos")
    suspend fun photoRowCount(): Int

    // Bypasses EncounterDao's own "deletedAt IS NULL" read filters, to inspect a soft-deleted row.
    @Query("SELECT lat FROM encounters WHERE id = :id")
    suspend fun rawEncounterLat(id: String): Double?

    @Query("SELECT locationSource FROM encounters WHERE id = :id")
    suspend fun rawEncounterLocationSource(id: String): String

    @Query("SELECT deletedAt FROM encounters WHERE id = :id")
    suspend fun rawEncounterDeletedAt(id: String): Long?

    // Bypasses EnumConverters/entity validation entirely, to simulate a row written by a future
    // app version or a hand-edited database. origin/locationSource/deviceId are fixed valid
    // literals: only tzOffsetMinutes and kind are the fields under test.
    @Query(
        "INSERT INTO encounters (id, occurredAt, tzOffsetMinutes, kind, origin, locationSource, " +
            "deviceId, createdAt, updatedAt) VALUES " +
            "(:id, :occurredAt, :tzOffsetMinutes, :kind, 'APP', 'NONE', 'device-1', " +
            ":occurredAt, :occurredAt)",
    )
    suspend fun insertRawEncounter(id: String, occurredAt: Long, tzOffsetMinutes: Int, kind: String)
}

internal data class PragmaIndex(val name: String)

internal data class PragmaColumn(
    val name: String,
    val type: String,
    val notnull: Int,
    val pk: Int,
)
