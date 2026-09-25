package dev.catsradar.data.analytics

import dev.catsradar.domain.analytics.AnalyticsEvent
import dev.catsradar.domain.analytics.AnalyticsEvent.BackupExported
import dev.catsradar.domain.analytics.AnalyticsEvent.BackupImported
import dev.catsradar.domain.analytics.AnalyticsEvent.BackupRejected
import dev.catsradar.domain.analytics.AnalyticsEvent.CatLogged
import dev.catsradar.domain.analytics.AnalyticsEvent.CatsDeleted
import dev.catsradar.domain.analytics.AnalyticsEvent.CoatSet
import dev.catsradar.domain.analytics.AnalyticsEvent.DeleteUndone
import dev.catsradar.domain.analytics.AnalyticsEvent.ImportUndone
import dev.catsradar.domain.analytics.AnalyticsEvent.PhotoAttached
import dev.catsradar.domain.analytics.AnalyticsEvent.PhotosImported
import dev.catsradar.domain.analytics.AnalyticsEvent.ScreenViewed
import dev.catsradar.domain.analytics.AnalyticsEvent.TallyUndone
import dev.catsradar.domain.analytics.AnalyticsEvent.WalkEnded
import dev.catsradar.domain.analytics.AnalyticsEvent.WalkStarted
import dev.catsradar.domain.analytics.AnalyticsScreen
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.platform.BackupRejection
import dev.catsradar.domain.usecase.PhotoSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val FirebaseName = Regex("[a-zA-Z][a-zA-Z0-9_]{0,39}")
private val ReservedPrefixes = listOf("firebase_", "google_", "ga_")
private const val MAX_TEXT_VALUE = 100

// A new event type stops compiling here; its new branch is the reminder to add its samples to everyEvent.
private fun sampled(event: AnalyticsEvent): Unit = when (event) {
    is ScreenViewed, is CatLogged, TallyUndone, is CoatSet, is PhotoAttached, is PhotosImported, is ImportUndone,
    is CatsDeleted, is DeleteUndone, BackupExported, is BackupImported, is BackupRejected, WalkStarted, is WalkEnded,
    -> Unit
}

private val everyEvent: List<AnalyticsEvent> =
    AnalyticsScreen.entries.map(::ScreenViewed) +
        EncounterKind.entries.flatMap { kind ->
            EncounterOrigin.entries.map { origin -> CatLogged(kind, origin, hasCoat = true) }
        } +
        (CatCoat.entries + null).map(::CoatSet) +
        PhotoSource.entries.map(::PhotoAttached) +
        BackupRejection.entries.map(::BackupRejected) +
        listOf(
            TallyUndone,
            PhotosImported(added = 12, duplicates = 3, failed = 1),
            ImportUndone(12),
            CatsDeleted(4),
            DeleteUndone(4),
            BackupExported,
            BackupImported(added = 40, updated = 2, unchanged = 310),
            WalkStarted,
            WalkEnded(95),
        )

class AnalyticsEncodingTest {

    @Test
    fun aScreenViewIsFirebasesScreenViewNamedByItsScreen() {
        val expected = mapOf(
            AnalyticsScreen.COUNTER to "counter",
            AnalyticsScreen.ENCOUNTERS to "encounters",
            AnalyticsScreen.ENCOUNTER_DETAIL to "encounter_detail",
            AnalyticsScreen.PHOTO_VIEWER to "photo_viewer",
            AnalyticsScreen.LOCATION_PICKER to "location_picker",
            AnalyticsScreen.STATISTICS to "statistics",
            AnalyticsScreen.REGIONS to "regions",
            AnalyticsScreen.MAP to "map",
            AnalyticsScreen.MAP_SPOT to "map_spot",
            AnalyticsScreen.SETTINGS to "settings",
        )

        assertEquals(AnalyticsScreen.entries.toSet(), expected.keys)
        expected.forEach { (screen, token) ->
            val expectedEvent = EncodedEvent("screen_view", texts = mapOf("screen_name" to token))
            assertEquals(expectedEvent, ScreenViewed(screen).encode())
        }
    }

    @Test
    fun everyProductEventEncodesToItsCatalogueNameAndParameters() {
        val expected = listOf(
            CatLogged(EncounterKind.TALLY, EncounterOrigin.NOTIFICATION, hasCoat = false) to EncodedEvent(
                "cat_logged",
                texts = mapOf("kind" to "tally", "origin" to "notification", "has_coat" to "false"),
            ),
            CatLogged(EncounterKind.PHOTO, EncounterOrigin.CAMERA, hasCoat = true) to EncodedEvent(
                "cat_logged",
                texts = mapOf("kind" to "photo", "origin" to "camera", "has_coat" to "true"),
            ),
            TallyUndone to EncodedEvent("tally_undone"),
            CoatSet(CatCoat.TRICOLOR_MOSTLY_WHITE) to EncodedEvent(
                "coat_set",
                texts = mapOf("coat" to "tricolor_mostly_white"),
            ),
            CoatSet(null) to EncodedEvent("coat_set", texts = mapOf("coat" to "none")),
            PhotoAttached(PhotoSource.GALLERY) to EncodedEvent("photo_attached", texts = mapOf("source" to "gallery")),
            PhotosImported(added = 12, duplicates = 3, failed = 1) to EncodedEvent(
                "photos_imported",
                counts = mapOf("added" to 12L, "duplicates" to 3L, "failed" to 1L),
            ),
            ImportUndone(12) to EncodedEvent("import_undone", counts = mapOf("count" to 12L)),
            CatsDeleted(4) to EncodedEvent("cats_deleted", counts = mapOf("count" to 4L)),
            DeleteUndone(4) to EncodedEvent("delete_undone", counts = mapOf("count" to 4L)),
            BackupExported to EncodedEvent("backup_exported"),
            BackupImported(added = 40, updated = 2, unchanged = 310) to EncodedEvent(
                "backup_imported",
                counts = mapOf("added" to 40L, "updated" to 2L, "unchanged" to 310L),
            ),
            BackupRejected(BackupRejection.UNREADABLE) to EncodedEvent(
                "backup_rejected",
                texts = mapOf("reason" to "unreadable"),
            ),
            WalkStarted to EncodedEvent("walk_started"),
            WalkEnded(95) to EncodedEvent("walk_ended", counts = mapOf("minutes" to 95L)),
        )

        expected.forEach { (event, encoded) -> assertEquals(encoded, event.encode(), "$event") }
    }

    @Test
    fun everyNameAndParameterFitsFirebasesLimits() {
        everyEvent.forEach { event ->
            sampled(event)
            val encoded = event.encode()
            (listOf(encoded.name) + encoded.texts.keys + encoded.counts.keys).forEach { name ->
                assertTrue(FirebaseName.matches(name), "$name is not a valid Firebase name")
                assertTrue(ReservedPrefixes.none(name::startsWith), "$name uses a reserved prefix")
            }
            encoded.texts.values.forEach { value ->
                assertTrue(value.length <= MAX_TEXT_VALUE, "$value in $event is too long")
            }
        }
    }
}
