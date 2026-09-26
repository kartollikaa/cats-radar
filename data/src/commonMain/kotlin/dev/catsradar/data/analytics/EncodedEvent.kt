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
import dev.catsradar.domain.analytics.AnalyticsEvent.LocationSetByHand
import dev.catsradar.domain.analytics.AnalyticsEvent.PhotoAttached
import dev.catsradar.domain.analytics.AnalyticsEvent.PhotosImported
import dev.catsradar.domain.analytics.AnalyticsEvent.ScreenViewed
import dev.catsradar.domain.analytics.AnalyticsEvent.TallyUndone
import dev.catsradar.domain.analytics.AnalyticsEvent.WalkEnded
import dev.catsradar.domain.analytics.AnalyticsEvent.WalkStarted

data class EncodedEvent(
    val name: String,
    val texts: Map<String, String> = emptyMap(),
    val counts: Map<String, Long> = emptyMap(),
)

// Firebase's predefined screen view: its console reports read these two names.
private const val SCREEN_VIEW = "screen_view"
private const val SCREEN_NAME = "screen_name"
private const val NO_COAT = "none"

@Suppress("CyclomaticComplexMethod") // one flat branch per catalogue event; splitting it would only hide the list
fun AnalyticsEvent.encode(): EncodedEvent = when (this) {
    is ScreenViewed -> EncodedEvent(SCREEN_VIEW, texts = mapOf(SCREEN_NAME to screen.token))
    is CatLogged -> EncodedEvent(
        "cat_logged",
        texts = mapOf("kind" to kind.token, "origin" to origin.token, "has_coat" to hasCoat.toString()),
    )
    TallyUndone -> EncodedEvent("tally_undone")
    is CoatSet -> EncodedEvent("coat_set", texts = mapOf("coat" to (coat?.token ?: NO_COAT)))
    is PhotoAttached -> EncodedEvent("photo_attached", texts = mapOf("source" to source.token))
    LocationSetByHand -> EncodedEvent("location_set_by_hand")
    is PhotosImported -> EncodedEvent(
        "photos_imported",
        counts = mapOf("added" to added.toLong(), "duplicates" to duplicates.toLong(), "failed" to failed.toLong()),
    )
    is ImportUndone -> EncodedEvent("import_undone", counts = mapOf("count" to count.toLong()))
    is CatsDeleted -> EncodedEvent("cats_deleted", counts = mapOf("count" to count.toLong()))
    is DeleteUndone -> EncodedEvent("delete_undone", counts = mapOf("count" to count.toLong()))
    BackupExported -> EncodedEvent("backup_exported")
    is BackupImported -> EncodedEvent(
        "backup_imported",
        counts = mapOf("added" to added.toLong(), "updated" to updated.toLong(), "unchanged" to unchanged.toLong()),
    )
    is BackupRejected -> EncodedEvent("backup_rejected", texts = mapOf("reason" to reason.token))
    WalkStarted -> EncodedEvent("walk_started")
    is WalkEnded -> EncodedEvent("walk_ended", counts = mapOf("minutes" to minutes))
}

private val Enum<*>.token: String get() = name.lowercase()
