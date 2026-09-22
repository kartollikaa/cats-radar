package dev.catsradar.data.db

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.PlaceStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnumConvertersTest {
    @Test
    fun unrecognizedEncounterKindNameDegradesToTally() {
        assertEquals(EncounterKind.TALLY, EnumConverters.nameToEncounterKind("BOGUS"))
    }

    @Test
    fun unrecognizedEncounterOriginNameDegradesToApp() {
        assertEquals(EncounterOrigin.APP, EnumConverters.nameToEncounterOrigin("BOGUS"))
    }

    @Test
    fun unrecognizedLocationSourceNameDegradesToNone() {
        assertEquals(LocationSource.NONE, EnumConverters.nameToLocationSource("BOGUS"))
    }

    @Test
    fun unrecognizedCatCoatNameDegradesToNull() {
        assertNull(EnumConverters.nameToCatCoat("BOGUS"))
    }

    @Test
    fun unrecognizedPlaceStatusNameDegradesToFailed() {
        assertEquals(PlaceStatus.FAILED, EnumConverters.nameToPlaceStatus("BOGUS"))
    }

    @Test
    fun recognizedNamesStillRoundTrip() {
        assertEquals(EncounterKind.PHOTO, EnumConverters.nameToEncounterKind("PHOTO"))
        assertEquals(CatCoat.GINGER, EnumConverters.nameToCatCoat("GINGER"))
    }
}
