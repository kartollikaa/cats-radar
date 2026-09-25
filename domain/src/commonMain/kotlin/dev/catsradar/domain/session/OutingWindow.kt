package dev.catsradar.domain.session

import dev.catsradar.domain.model.Encounter

data class OutingWindow(
    /** Every live cat from the oldest to the newest outing holding a shown cat, newest first. */
    val cats: List<Encounter>,
    /** The next newer outing, oldest first; null when the window reaches the newest. */
    val newer: List<Encounter>?,
    /** The next older outing, oldest first; null when the window reaches the oldest. */
    val older: List<Encounter>?,
) {
    val newerLanding: Encounter? get() = newer?.first()

    val olderLanding: Encounter? get() = older?.last()
}

/** Null when no cat in [shown] is live. */
fun outingWindow(encounters: List<Encounter>, shown: Set<String>): OutingWindow? {
    // groupByOuting's sort is stable, so sorting by id first breaks a same-instant tie by id.
    val outings = SessionSplitter.groupByOuting(encounters.sortedBy { it.id })
    val holdsShown: (List<Encounter>) -> Boolean = { outing -> outing.any { it.id in shown } }
    val oldest = outings.indexOfFirst(holdsShown)
    if (oldest == -1) return null
    val newest = outings.indexOfLast(holdsShown)
    return OutingWindow(
        cats = outings.subList(oldest, newest + 1).flatten().asReversed(),
        newer = outings.getOrNull(newest + 1),
        older = outings.getOrNull(oldest - 1),
    )
}
