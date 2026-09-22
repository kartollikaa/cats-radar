package dev.catsradar.data.db

import androidx.room3.ColumnTypeConverter
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.PlaceStatus

// Stores the enum name, not the ordinal: reordering entries must never change stored data.
internal object EnumConverters {
    @ColumnTypeConverter
    fun encounterKindToName(value: EncounterKind): String = value.name

    @ColumnTypeConverter
    fun nameToEncounterKind(value: String): EncounterKind = EncounterKind.valueOf(value)

    @ColumnTypeConverter
    fun encounterOriginToName(value: EncounterOrigin): String = value.name

    @ColumnTypeConverter
    fun nameToEncounterOrigin(value: String): EncounterOrigin = EncounterOrigin.valueOf(value)

    @ColumnTypeConverter
    fun locationSourceToName(value: LocationSource): String = value.name

    @ColumnTypeConverter
    fun nameToLocationSource(value: String): LocationSource = LocationSource.valueOf(value)

    @ColumnTypeConverter
    fun catCoatToName(value: CatCoat?): String? = value?.name

    @ColumnTypeConverter
    fun nameToCatCoat(value: String?): CatCoat? = value?.let(CatCoat::valueOf)

    @ColumnTypeConverter
    fun placeStatusToName(value: PlaceStatus): String = value.name

    @ColumnTypeConverter
    fun nameToPlaceStatus(value: String): PlaceStatus = PlaceStatus.valueOf(value)
}
