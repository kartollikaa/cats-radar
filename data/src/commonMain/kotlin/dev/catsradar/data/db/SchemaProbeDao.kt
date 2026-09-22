package dev.catsradar.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.RawQuery
import androidx.room3.RoomRawQuery

/** Raw schema introspection for migration/schema tests; not part of the app's data access. */
@Dao
internal interface SchemaProbeDao {
    @Query(
        "SELECT name FROM sqlite_master " +
            "WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%'",
    )
    suspend fun tableNames(): List<String>

    // Room's @Query parser only recognizes UPDATE/DELETE/SELECT/INSERT; PRAGMA needs @RawQuery.
    @RawQuery
    suspend fun tableInfo(query: RoomRawQuery): List<PragmaColumn>

    @Query("SELECT kind FROM encounters WHERE id = :id")
    suspend fun rawEncounterKind(id: String): String

    @Query("SELECT coat FROM encounters WHERE id = :id")
    suspend fun rawEncounterCoat(id: String): String?

    @Query("SELECT status FROM place_cells WHERE cellId = :cellId")
    suspend fun rawPlaceCellStatus(cellId: String): String

    @Query("SELECT COUNT(*) FROM encounters WHERE id = :id")
    suspend fun encounterRowCount(id: String): Int
}

internal data class PragmaColumn(
    val name: String,
    val type: String,
    val notnull: Int,
    val pk: Int,
)
