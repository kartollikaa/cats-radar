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
    val outings = SessionSplitter.groupByOuting(encounters)
    val holding = outings.indices.filter { index -> outings[index].any { it.id in shown } }
    if (holding.isEmpty()) return null
    val oldest = holding.first()
    val newest = holding.last()
    return OutingWindow(
        cats = outings.subList(oldest, newest + 1).flatten().asReversed(),
        newer = outings.getOrNull(newest + 1),
        older = outings.getOrNull(oldest - 1),
    )
}
