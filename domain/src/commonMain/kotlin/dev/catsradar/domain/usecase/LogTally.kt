package dev.catsradar.domain.usecase

import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.analytics.logged
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.IdGenerator
import dev.catsradar.domain.repository.EncounterRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.Clock

private const val SECONDS_PER_MINUTE = 60

class LogTally(
    private val encounterRepository: EncounterRepository,
    private val idGenerator: IdGenerator,
    private val deviceIdProvider: DeviceIdProvider,
    private val clock: Clock,
    private val analytics: Analytics,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(
        coat: CatCoat? = null,
        origin: EncounterOrigin = EncounterOrigin.APP,
    ): Encounter {
        val now = clock.now()
        val encounter = Encounter(
            id = idGenerator.newId(),
            occurredAt = now,
            tzOffsetMinutes = timeZone.offsetAt(now).totalSeconds / SECONDS_PER_MINUTE,
            kind = EncounterKind.TALLY,
            origin = origin,
            coat = coat,
            lat = null,
            lon = null,
            accuracyMeters = null,
            locationSource = LocationSource.NONE,
            locationFixedAt = null,
            geohash = null,
            placeCellId = null,
            deviceId = deviceIdProvider.deviceId,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
        encounterRepository.insert(encounter)
        analytics.log(encounter.logged())
        return encounter
    }
}
