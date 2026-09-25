package dev.catsradar.domain.stats

import dev.catsradar.domain.model.Encounter
import kotlin.jvm.JvmInline
import kotlin.time.Instant

/** When each live cat was logged, sorted once, so counting the cats of any span is two binary searches. */
@JvmInline
value class CatTimes private constructor(private val sorted: List<Instant>) {

    /** Cats logged within [from]..[to], both ends included; a null [to] has no end. */
    fun countWithin(from: Instant, to: Instant?): Int {
        val first = firstIndex { it >= from }
        val pastLast = if (to == null) sorted.size else firstIndex { it > to }
        return (pastLast - first).coerceAtLeast(0)
    }

    // The first index whose time passes [test], which must fail for a prefix of the times and pass for the rest.
    private inline fun firstIndex(test: (Instant) -> Boolean): Int {
        var low = 0
        var high = sorted.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (test(sorted[middle])) high = middle else low = middle + 1
        }
        return low
    }

    companion object {
        val NONE = CatTimes(emptyList())

        fun of(encounters: List<Encounter>): CatTimes =
            CatTimes(encounters.filter { it.deletedAt == null }.map { it.occurredAt }.sorted())
    }
}
