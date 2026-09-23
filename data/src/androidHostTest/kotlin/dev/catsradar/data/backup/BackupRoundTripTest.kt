package dev.catsradar.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.data.db.TestCatsDatabase
import dev.catsradar.data.db.buildInMemoryCatsDatabase
import dev.catsradar.data.platform.AndroidPhotoStorage
import dev.catsradar.data.repository.EncounterRepositoryImpl
import dev.catsradar.data.repository.PlaceCellRepositoryImpl
import dev.catsradar.data.repository.WalkRepositoryImpl
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.stats.Stats
import dev.catsradar.domain.stats.StatsCalculator
import dev.catsradar.domain.usecase.ExportBackup
import dev.catsradar.domain.usecase.ImportBackup
import dev.catsradar.domain.usecase.ImportBackupResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val photoStorage = AndroidPhotoStorage(context)
    private val here = buildInMemoryCatsDatabase(context)
    private val elsewhere = buildInMemoryCatsDatabase(context)

    @After
    fun tearDown() {
        here.close()
        elsewhere.close()
    }

    @Test
    fun aBackupRestoredOnAnEmptyPhoneGivesTheSameStatistics() = runTest {
        val archive = File(temporaryFolder.root, "backup.zip").path
        val cats = catsFromAFewDaysAcrossZones()
        cats.forEach { here.encounters().insert(it) }
        here.encounters().softDelete(cats.first().id, NOW)
        listOf(RED_SQUARE, TIMES_SQUARE).forEach { here.placeCells().upsert(it.cell()) }
        photoStorage.prepare(PHOTO).writeText("a cat")

        ExportBackup(here.encounters(), here.placeCells(), here.walks(), writer()).invoke(archive)
        val imported = ImportBackup(elsewhere.encounters(), elsewhere.placeCells(), elsewhere.walks(), reader())
            .invoke(archive)

        assertEquals(ImportBackupResult.Merged(added = cats.size - 1, updated = 0, unchanged = 0), imported)
        assertEquals(statsOf(here), statsOf(elsewhere))
    }

    private suspend fun statsOf(database: TestCatsDatabase): Stats =
        StatsCalculator.calculate(database.encounters().observeAll().first(), today = TODAY, now = NOW)

    private fun catsFromAFewDaysAcrossZones(): List<Encounter> = listOf(
        cat("deleted", "2026-09-22T09:00", MOSCOW, CatCoat.WHITE),
        cat("moscow-1", "2026-09-19T08:00", MOSCOW, CatCoat.GINGER, RED_SQUARE),
        cat("moscow-2", "2026-09-19T08:12", MOSCOW, CatCoat.GINGER, RED_SQUARE),
        cat("moscow-3", "2026-09-19T08:31", MOSCOW, CatCoat.BLACK, RED_SQUARE, photo = PHOTO),
        cat("new-york", "2026-09-20T22:40", NEW_YORK, coat = null, place = TIMES_SQUARE),
        cat("london", "2026-09-22T07:15", UtcOffset.ZERO, CatCoat.GREY_WHITE),
    )

    @Suppress("LongParameterList") // a fixture builder: every parameter is one field of the row
    private fun cat(
        id: String,
        localTime: String,
        offset: UtcOffset,
        coat: CatCoat?,
        place: Place? = null,
        photo: String? = null,
    ): Encounter {
        val occurredAt = LocalDateTime.parse(localTime).toInstant(offset)
        return Encounter(
            id = id,
            occurredAt = occurredAt,
            tzOffsetMinutes = offset.totalSeconds / 60,
            kind = if (photo == null) EncounterKind.TALLY else EncounterKind.PHOTO,
            origin = EncounterOrigin.APP,
            coat = coat,
            photoPath = photo,
            thumbPath = null,
            galleryUri = null,
            sourceDigest = null,
            lat = place?.lat,
            lon = place?.lon,
            accuracyMeters = place?.let { 8f },
            locationSource = if (place == null) LocationSource.NONE else LocationSource.CURRENT_FIX,
            locationFixedAt = place?.let { occurredAt },
            geohash = place?.geohash,
            placeCellId = place?.cellId,
            deviceId = DEVICE,
            createdAt = occurredAt,
            updatedAt = occurredAt,
            deletedAt = null,
        )
    }

    private data class Place(
        val lat: Double,
        val lon: Double,
        val countryCode: String,
        val country: String,
        val city: String,
    ) {
        val geohash = Geohash.encode(lat, lon, Tuning.GEOHASH_PRECISION)
        val cellId = Geohash.prefix(geohash, Tuning.PLACE_CELL_PRECISION)

        fun cell() = PlaceCell(
            cellId = cellId,
            centerLat = lat,
            centerLon = lon,
            countryCode = countryCode,
            countryName = country,
            adminArea = null,
            locality = city,
            subLocality = null,
            status = PlaceStatus.RESOLVED,
            attempts = 1,
            lastAttemptAt = NOW,
            resolvedAt = NOW,
        )
    }

    private fun writer() = ZipBackupWriter(
        context = context,
        photoStorage = photoStorage,
        deviceIdProvider = object : DeviceIdProvider {
            override val deviceId: String = DEVICE
        },
        clock = object : Clock {
            override fun now(): Instant = NOW
        },
        appVersion = "1.0",
    )

    private fun reader() = ZipBackupReader(context, photoStorage)

    private fun TestCatsDatabase.encounters() = EncounterRepositoryImpl(encounterDao())

    private fun TestCatsDatabase.placeCells() = PlaceCellRepositoryImpl(placeCellDao())

    private fun TestCatsDatabase.walks() = WalkRepositoryImpl(walkDao(), trackPointDao())

    private companion object {
        const val DEVICE = "device-1"
        const val PHOTO = "moscow-3.jpg"
        val NOW = Instant.parse("2026-09-22T12:00:00Z")
        val TODAY = LocalDate(2026, 9, 22)
        val MOSCOW = UtcOffset(hours = 3)
        val NEW_YORK = UtcOffset(hours = -5)
        val RED_SQUARE = Place(55.754, 37.620, "RU", "Russia", "Moscow")
        val TIMES_SQUARE = Place(40.758, -73.986, "US", "United States", "New York")
    }
}
