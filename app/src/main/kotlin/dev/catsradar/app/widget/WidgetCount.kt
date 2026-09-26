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
            .onEach { stored -> state.update { it.withStored(stored) } }
            .launchIn(scope)

    /** Reads the stored count again: storage stays silent when only the day changes. */
    suspend fun refresh() {
        val readSince = state.value.storedAnswers
        val today = observeTodayCount().first()
        // An answer storage gave while this read ran is at least as new as the read.
        state.update { if (it.storedAnswers == readSince) it.copy(stored = today) else it }
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
        val today = withContext(NonCancellable) { runCatching { observeTodayCount().first() }.getOrNull() }
        state.update { if (it.tapsStarted == afterTap) it.readBack(today) else it }
    }
}

/** [atLeast] is what the widget has promised: it holds until the stored count reaches it. */
private data class Counting(
    val stored: Int? = null,
    val storedAnswers: Long = 0,
    val tapsStarted: Long = 0,
    val tapsWriting: Int = 0,
    val atLeast: Int? = null,
) {
    val shown: Int? get() = stored?.let { stored -> atLeast?.let { maxOf(stored, it) } ?: stored }

    fun withStored(count: Int) = copy(stored = count, storedAnswers = storedAnswers + 1).caughtUp()

    fun tapped() = copy(
        tapsStarted = tapsStarted + 1,
        tapsWriting = tapsWriting + 1,
        atLeast = shown?.plus(1) ?: atLeast,
    )

    fun written() = copy(tapsWriting = tapsWriting - 1)

    fun readBack(today: Int?) = copy(atLeast = today).caughtUp()

    private fun caughtUp() = if (stored != null && atLeast != null && stored >= atLeast) copy(atLeast = null) else this
}
