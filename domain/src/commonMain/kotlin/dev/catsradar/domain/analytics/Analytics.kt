package dev.catsradar.domain.analytics

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.platform.BackupRejection
import dev.catsradar.domain.usecase.PhotoSource

/** Returns at once: logging never waits on the network. */
interface Analytics {
    fun log(event: AnalyticsEvent)
}

/** Parameters are enums, booleans and counts only, so no event can carry a place, a photo, an id or a time. */
sealed interface AnalyticsEvent {
    data class ScreenViewed(val screen: AnalyticsScreen) : AnalyticsEvent

    data class CatLogged(val kind: EncounterKind, val origin: EncounterOrigin, val hasCoat: Boolean) : AnalyticsEvent

    data object TallyUndone : AnalyticsEvent

    /** [coat] is null when the coat was cleared. */
    data class CoatSet(val coat: CatCoat?) : AnalyticsEvent

    data class PhotoAttached(val source: PhotoSource) : AnalyticsEvent

    data class PhotosImported(val added: Int, val duplicates: Int, val failed: Int) : AnalyticsEvent

    data class ImportUndone(val count: Int) : AnalyticsEvent

    data class CatsDeleted(val count: Int) : AnalyticsEvent

    data class DeleteUndone(val count: Int) : AnalyticsEvent

    data object BackupExported : AnalyticsEvent

    data class BackupImported(val added: Int, val updated: Int, val unchanged: Int) : AnalyticsEvent

    data class BackupRejected(val reason: BackupRejection) : AnalyticsEvent

    data object WalkStarted : AnalyticsEvent

    data class WalkEnded(val minutes: Long) : AnalyticsEvent
}

enum class AnalyticsScreen {
    COUNTER,
    ENCOUNTERS,
    ENCOUNTER_DETAIL,
    PHOTO_VIEWER,
    LOCATION_PICKER,
    STATISTICS,
    REGIONS,
    MAP,
    MAP_SPOT,
    SETTINGS,
}

internal fun Encounter.logged() = AnalyticsEvent.CatLogged(kind, origin, hasCoat = coat != null)
