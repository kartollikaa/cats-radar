package dev.catsradar.presentation.detail

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.session.OutingWindow
import dev.catsradar.domain.session.outingWindow

/** [currentId] is one of [window]'s cats. */
internal data class ShownPages(val window: OutingWindow, val currentId: String) {
    /** These pages with [catId] on screen, or unchanged when it is not one of them. */
    fun settledOn(catId: String): ShownPages =
        if (window.cats.any { it.id == catId }) copy(currentId = catId) else this
}

/** The cats a detail screen pages through and the one on screen; a cat leaves the pages only by being deleted. */
internal class OutingPages(private val openedId: String, private val restoredId: String?) {
    private var encounters: List<Encounter> = emptyList()

    // Newest first.
    private var pageIds: List<String> = emptyList()
    private var anchor: String? = null

    /** Null before a live cat to start from is seen, and while no cat on the pages is live. */
    var shown: ShownPages? = null
        private set

    fun update(live: List<Encounter>): ShownPages? {
        encounters = live
        if (anchor == null) {
            anchor = listOfNotNull(restoredId, openedId).firstOrNull { live.holdsLive(it) }
            pageIds = listOfNotNull(anchor)
        }
        shown = outingWindow(live, pageIds.toSet())?.let { window ->
            val nextIds = window.cats.map { it.id }
            val kept = handOver(checkNotNull(anchor), nextIds)
            anchor = kept
            pageIds = nextIds
            ShownPages(window, kept)
        }
        return shown
    }

    fun settle(catId: String) {
        val settled = shown?.settledOn(catId) ?: return
        anchor = settled.currentId
        shown = settled
    }

    /** The pages moved to the outing next to them, on its cat nearest to them; null when there is none. */
    fun release(direction: OutingDirection): ShownPages? {
        val window = shown?.window
        val (outing, landing) = when (direction) {
            OutingDirection.NEWER -> window?.newer to window?.newerLanding
            OutingDirection.OLDER -> window?.older to window?.olderLanding
        }
        if (outing == null || landing == null) return null
        anchor = landing.id
        pageIds = outing.map { it.id }
        return update(encounters)
    }

    /** [current], or when its cat has left, the nearest page still there: the next older, else the next newer. */
    private fun handOver(current: String, nextIds: List<String>): String {
        if (current in nextIds) return current
        val at = pageIds.indexOf(current)
        return (pageIds.drop(at + 1) + pageIds.take(at).asReversed()).first { it in nextIds }
    }
}

internal fun List<Encounter>.holdsLive(id: String): Boolean = any { it.id == id && it.deletedAt == null }
