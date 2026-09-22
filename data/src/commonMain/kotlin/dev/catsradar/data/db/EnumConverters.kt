package dev.catsradar.data.db

import androidx.room3.ColumnTypeConverter
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.PlaceStatus

// Stores the enum name, not the ordinal: reordering entries must never change stored data. An
// unrecognized name (hand-edited DB, a newer app's entry) degrades to a fallback below, never throws.
internal object EnumConverters {
    @ColumnTypeConverter
    fun encounterKindToName(value: EncounterKind): String = value.name

    @ColumnTypeConverter
    fun nameToEncounterKind(value: String): EncounterKind =
        // No member fits "unknown" better than another; TALLY needs no accompanying photo fields.
        EncounterKind.entries.firstOrNull { it.name == value } ?: EncounterKind.TALLY

    @ColumnTypeConverter
    fun encounterOriginToName(value: EncounterOrigin): String = value.name

    @ColumnTypeConverter
    fun nameToEncounterOrigin(value: String): EncounterOrigin =
        // APP is the most generic origin; the others imply a specific capture flow that didn't happen.
        EncounterOrigin.entries.firstOrNull { it.name == value } ?: EncounterOrigin.APP

    @ColumnTypeConverter
    fun locationSourceToName(value: LocationSource): String = value.name

    @ColumnTypeConverter
    fun nameToLocationSource(value: String): LocationSource =
        // LocationSource already has a member for "no location known" -- the natural fallback.
        LocationSource.entries.firstOrNull { it.name == value } ?: LocationSource.NONE

    @ColumnTypeConverter
    fun catCoatToName(value: CatCoat?): String? = value?.name

    @ColumnTypeConverter
    fun nameToCatCoat(value: String?): CatCoat? =
        // coat is nullable and null already means "not specified"; an unknown name maps the same way.
        value?.let { name -> CatCoat.entries.firstOrNull { it.name == name } }

    @ColumnTypeConverter
    fun placeStatusToName(value: PlaceStatus): String = value.name

    @ColumnTypeConverter
    fun nameToPlaceStatus(value: String): PlaceStatus =
        // FAILED, not PENDING: an unrecognized status must not make GeocodePendingCellsWorker retry it forever.
        PlaceStatus.entries.firstOrNull { it.name == value } ?: PlaceStatus.FAILED
}
