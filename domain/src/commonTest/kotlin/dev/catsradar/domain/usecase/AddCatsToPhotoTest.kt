package dev.catsradar.domain.usecase

import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.analytics.AnalyticsEvent
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.EncounterPhoto
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.platform.StoredPhoto
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeDeviceIdProvider
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.FakeIdGenerator
import dev.catsradar.domain.testing.RecordingAnalytics
import dev.catsradar.domain.testing.RecordingPhotoStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class AddCatsToPhotoTest {
    private val encounters = FakeEncounterRepository()
    private val storage = RecordingPhotoStorage()
    private val analytics = RecordingAnalytics()

    private fun addCats(analytics: Analytics = this.analytics) = AddCatsToPhoto(
        encounterRepository = encounters,
        photoStorage = storage,
        idGenerator = FakeIdGenerator(),
        deviceIdProvider = FakeDeviceIdProvider(THIS_DEVICE),
        clock = FakeClock(NOW),
        analytics = analytics,
    )

    @Test
    fun aRequestedCoatBecomesOneCatInTheSourcesShotWithItsOwnFilesAndIdentity() = runTest {
        val source = source()
        encounters.insert(source)

        val result = addCats()(SOURCE_ID, SOURCE_PHOTO_ID, listOf(CatCoat.BLACK, null))

        assertEquals(
            AddCatsResult.Added(
                listOf(
                    AddedCat("id-1", needsLocation = true),
                    AddedCat("id-3", needsLocation = true),
                ),
            ),
            result,
        )
        val inserted = encounters.insertAllIfSourceLiveCalls.single().second
        assertEquals(listOf("id-1", "id-3"), inserted.map { it.id })
        assertEquals(listOf(CatCoat.BLACK, null), inserted.map { it.coat })
        assertTrue(inserted.all { it.occurredAt == source.occurredAt })
        assertTrue(inserted.all { it.tzOffsetMinutes == source.tzOffsetMinutes })
        assertTrue(inserted.all { it.kind == source.kind && it.origin == source.origin })
        assertTrue(inserted.all { it.lat == source.lat && it.lon == source.lon })
        assertTrue(inserted.all { it.accuracyMeters == source.accuracyMeters })
        assertTrue(inserted.all { it.locationSource == source.locationSource })
        assertTrue(inserted.all { it.locationFixedAt == source.locationFixedAt })
        assertTrue(inserted.all { it.geohash == source.geohash && it.placeCellId == source.placeCellId })
        assertTrue(inserted.all { it.deviceId == THIS_DEVICE })
        assertTrue(inserted.all { it.createdAt == NOW && it.updatedAt == NOW && it.deletedAt == null })

        val photos = inserted.map { it.photos.single() }
        assertEquals(listOf("id-2", "id-4"), photos.map { it.id })
        assertEquals(listOf("id-1", "id-3"), photos.map { it.encounterId })
        assertEquals(listOf("id-2.jpg", "id-4.jpg"), photos.map { it.photoPath })
        assertEquals(listOf("id-2_thumb.jpg", "id-4_thumb.jpg"), photos.map { it.thumbPath })
        assertTrue(photos.all { it.galleryUri == source.cover!!.galleryUri })
        assertTrue(photos.all { it.sourceMediaUri == source.cover!!.sourceMediaUri })
        assertTrue(photos.all { it.sourceDigest == source.cover!!.sourceDigest })
        assertTrue(photos.all { it.deviceId == source.cover!!.deviceId })
        assertTrue(photos.all { it.addedAt == NOW && it.shotId == source.cover!!.shotId })
        assertEquals(
            listOf(
                StoredPhoto(SOURCE_PATH, SOURCE_THUMB) to "id-2",
                StoredPhoto(SOURCE_PATH, SOURCE_THUMB) to "id-4",
            ),
            storage.copied,
        )
    }

    @Test
    fun theResultPairsEachAddedCatWithWhetherItNeedsALocationFix() = runTest {
        val source = source().copy(
            lat = 41.39864,
            lon = 2.17842,
            accuracyMeters = 7.5f,
            locationSource = LocationSource.MANUAL,
            locationFixedAt = LOCATION_FIXED_AT,
            geohash = "sp3e986k",
            placeCellId = "sp3e98",
        )
        encounters.insert(source)

        val result = addCats()(SOURCE_ID, SOURCE_PHOTO_ID, listOf(CatCoat.WHITE))

        assertEquals(AddCatsResult.Added(listOf(AddedCat("id-1", needsLocation = false))), result)
        val added = encounters.insertAllIfSourceLiveCalls.single().second.single()
        assertEquals(source.lat, added.lat)
        assertEquals(source.lon, added.lon)
        assertEquals(source.accuracyMeters, added.accuracyMeters)
        assertEquals(source.locationSource, added.locationSource)
        assertEquals(source.locationFixedAt, added.locationFixedAt)
        assertEquals(source.geohash, added.geohash)
        assertEquals(source.placeCellId, added.placeCellId)
    }

    @Test
    fun anUnknownSourceIsNotAddable() = runTest {
        assertEquals(AddCatsResult.NotAddable, addCats()("unknown", SOURCE_PHOTO_ID, listOf(null)))

        assertEquals(emptyList(), storage.copied)
        assertEquals(emptyList(), encounters.insertAllIfSourceLiveCalls)
        assertEquals(emptyList(), analytics.logged)
    }

    @Test
    fun aPhotoOutsideTheSourceIsNotAddable() = runTest {
        encounters.insert(source())

        assertEquals(AddCatsResult.NotAddable, addCats()(SOURCE_ID, "another-photo", listOf(null)))

        assertEquals(emptyList(), storage.copied)
        assertEquals(emptyList(), encounters.insertAllIfSourceLiveCalls)
        assertEquals(emptyList(), analytics.logged)
    }

    @Test
    fun noCoatsIsASuccessfulNoOp() = runTest {
        encounters.insert(source())

        assertEquals(AddCatsResult.Added(emptyList()), addCats()(SOURCE_ID, SOURCE_PHOTO_ID, emptyList()))

        assertEquals(emptyList(), storage.copied)
        assertEquals(emptyList(), encounters.insertAllIfSourceLiveCalls)
        assertEquals(emptyList(), analytics.logged)
    }

    @Test
    fun aLaterCopyFailureRemovesEveryEarlierCopyAndWritesNothing() = runTest {
        encounters.insert(source())
        storage.copyFailureAt = 2

        assertFailsWith<IllegalStateException> {
            addCats()(SOURCE_ID, SOURCE_PHOTO_ID, listOf(CatCoat.BLACK, CatCoat.WHITE))
        }

        assertEquals(listOf("id-2.jpg", "id-2_thumb.jpg"), storage.deleted)
        assertEquals(emptyList(), encounters.insertAllIfSourceLiveCalls)
        assertEquals(emptyList(), analytics.logged)
    }

    @Test
    fun aSourceDeletedBeforeCommitLeavesNoCatsFilesOrAnalytics() = runTest {
        encounters.insert(source())
        encounters.beforeInsertAllIfSourceLive = {
            encounters.softDelete(SOURCE_ID, DELETED_AT)
        }

        val result = addCats()(SOURCE_ID, SOURCE_PHOTO_ID, listOf(CatCoat.BLACK, CatCoat.WHITE))

        assertEquals(AddCatsResult.NotAddable, result)
        assertEquals(
            listOf("id-2.jpg", "id-2_thumb.jpg", "id-4.jpg", "id-4_thumb.jpg"),
            storage.deleted,
        )
        assertEquals(emptyList(), analytics.logged)
        assertEquals(listOf(SOURCE_ID), encounters.loadEvery().map { it.id })
    }

    @Test
    fun aRepositoryFailureRemovesEveryCopyAndLogsNothing() = runTest {
        encounters.insert(source())
        encounters.insertAllIfSourceLiveShouldThrow = IllegalStateException("database unavailable")

        assertFailsWith<IllegalStateException> {
            addCats()(SOURCE_ID, SOURCE_PHOTO_ID, listOf(CatCoat.BLACK, CatCoat.WHITE))
        }

        assertEquals(
            listOf("id-2.jpg", "id-2_thumb.jpg", "id-4.jpg", "id-4_thumb.jpg"),
            storage.deleted,
        )
        assertEquals(emptyList(), analytics.logged)
    }

    @Test
    fun aCommittedBatchLogsEveryAddedCatAfterTheWrite() = runTest {
        encounters.insert(source())
        val logged = mutableListOf<AnalyticsEvent>()
        val orderedAnalytics = object : Analytics {
            override fun log(event: AnalyticsEvent) {
                assertEquals(listOf("id-1", "id-3"), encounters.inserted.takeLast(2).map { it.id })
                logged += event
            }
        }

        addCats(orderedAnalytics)(SOURCE_ID, SOURCE_PHOTO_ID, listOf(CatCoat.BLACK, null))

        assertEquals(
            listOf<AnalyticsEvent>(
                AnalyticsEvent.CatLogged(EncounterKind.PHOTO, EncounterOrigin.GALLERY, hasCoat = true),
                AnalyticsEvent.CatLogged(EncounterKind.PHOTO, EncounterOrigin.GALLERY, hasCoat = false),
            ),
            logged,
        )
    }

    private fun source(): Encounter {
        val photo = EncounterPhoto(
            id = SOURCE_PHOTO_ID,
            encounterId = SOURCE_ID,
            photoPath = SOURCE_PATH,
            thumbPath = SOURCE_THUMB,
            galleryUri = "content://gallery/original",
            sourceMediaUri = "content://media/source",
            sourceDigest = "source-digest",
            deviceId = "source-photo-device",
            addedAt = SOURCE_CREATED_AT,
            shotId = "shot-root",
        )
        return Encounter(
            id = SOURCE_ID,
            occurredAt = OCCURRED_AT,
            tzOffsetMinutes = 180,
            kind = EncounterKind.PHOTO,
            origin = EncounterOrigin.GALLERY,
            coat = CatCoat.GINGER_WHITE,
            photos = listOf(photo),
            lat = null,
            lon = null,
            accuracyMeters = null,
            locationSource = LocationSource.NONE,
            locationFixedAt = null,
            geohash = null,
            placeCellId = null,
            deviceId = "source-encounter-device",
            createdAt = SOURCE_CREATED_AT,
            updatedAt = SOURCE_UPDATED_AT,
            deletedAt = null,
        )
    }

    private companion object {
        const val SOURCE_ID = "source"
        const val SOURCE_PHOTO_ID = "source-photo"
        const val SOURCE_PATH = "source.jpg"
        const val SOURCE_THUMB = "source_thumb.jpg"
        const val THIS_DEVICE = "this-device"
        val OCCURRED_AT = Instant.parse("2026-09-20T10:15:00Z")
        val SOURCE_CREATED_AT = Instant.parse("2026-09-20T12:45:00Z")
        val SOURCE_UPDATED_AT = Instant.parse("2026-09-20T14:00:00Z")
        val LOCATION_FIXED_AT = Instant.parse("2026-09-20T11:30:00Z")
        val NOW = Instant.parse("2026-09-26T12:00:00Z")
        val DELETED_AT = Instant.parse("2026-09-26T12:01:00Z")
    }
}
