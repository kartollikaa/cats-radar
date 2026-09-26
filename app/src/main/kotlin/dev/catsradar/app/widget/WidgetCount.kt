package dev.catsradar.app.widget

import dev.catsradar.domain.usecase.ObserveTodayCount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withContext

/**
 * Today's count as the widget shows it.
 *
 * A tap counts at once, before its row is written, and the stored count takes over once it has caught
 * up: the number never drops below a tap already shown, nor counts one twice.
 */
class WidgetCount(private val observeTodayCount: ObserveTodayCount) {

    private val state = MutableStateFlow(Counting())

    val shown: Flow<Int> = state.mapNotNull { it.shown }.distinctUntilChanged()

    fun start(scope: CoroutineScope): Job =
        observeTodayCount()
            .onEach { count -> state.update { it.withObserved(count) } }
            .launchIn(scope)

    /** Reads the stored count again: storage stays silent when only the day changes. */
    suspend fun refresh() {
        val observationsBefore = state.value.observations
        val today = observeTodayCount().first()
        state.update { it.read(today, observationsBefore) }
    }

    /**
     * Counts a cat at once while [write] stores it; a write that fails takes the cat back off. Returns
     * only once the count has been read back after the last tap in flight.
     */
    suspend fun <T> tally(write: suspend () -> T): T {
        state.update { it.tapped() }
        try {
            return write()
        } finally {
            val written = state.updateAndGet { it.written() }
            if (written.tapsWriting == 0) readBack(afterTap = written.tapsStarted)
        }
    }

    private suspend fun readBack(afterTap: Long) {
        val observationsBefore = state.value.observations
        val today = withContext(NonCancellable) { runCatching { observeTodayCount().first() }.getOrNull() }
        state.update { if (it.tapsStarted == afterTap) it.readBack(today, observationsBefore) else it }
    }
}

/**
 * [stored] is the newest count known, [observed] the last one storage announced by itself. [atLeast] is what
 * the widget has promised: it holds until storage announces at least as much.
 */
private data class Counting(
    val stored: Int? = null,
    val observed: Int? = null,
    val observations: Long = 0,
    val tapsStarted: Long = 0,
    val tapsWriting: Int = 0,
    val atLeast: Int? = null,
) {
    val shown: Int? get() = stored?.let { stored -> atLeast?.let { maxOf(stored, it) } ?: stored }

    fun withObserved(count: Int) = copy(stored = count, observed = count, observations = observations + 1).caughtUp()

    // An announcement made while the read ran is at least as new as the read.
    fun read(count: Int, observationsBefore: Long) =
        if (observations == observationsBefore) copy(stored = count) else this

    fun tapped() = copy(
        tapsStarted = tapsStarted + 1,
        tapsWriting = tapsWriting + 1,
        atLeast = shown?.plus(1) ?: atLeast,
    )

    fun written() = copy(tapsWriting = tapsWriting - 1)

    fun readBack(count: Int?, observationsBefore: Long): Counting {
        val known = if (count == null) this else read(count, observationsBefore)
        return known.copy(atLeast = count).caughtUp()
    }

    private fun caughtUp() =
        if (observed != null && atLeast != null && observed >= atLeast) copy(atLeast = null) else this
}
