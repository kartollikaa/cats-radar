package dev.catsradar.app.widget

import dev.catsradar.domain.usecase.ObserveTodayCount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

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

    /** Counts a cat at once while [write] stores it; a write that fails takes the cat back off. */
    suspend fun <T> tally(write: suspend () -> T): T {
        state.first { it.stored != null }
        state.update { it.tapped() }
        try {
            return write()
        } finally {
            val today = runCatching { observeTodayCount().first() }.getOrNull()
            state.update { it.settled(today) }
        }
    }
}

/** [atLeast] is what the widget has promised: it holds until the stored count reaches it. */
private data class Counting(
    val stored: Int? = null,
    val storedAnswers: Long = 0,
    val tapsWriting: Int = 0,
    val atLeast: Int? = null,
) {
    val shown: Int? get() = stored?.let { stored -> atLeast?.let { maxOf(stored, it) } ?: stored }

    fun withStored(count: Int) = copy(stored = count, storedAnswers = storedAnswers + 1).caughtUp()

    fun tapped() = copy(tapsWriting = tapsWriting + 1, atLeast = checkNotNull(shown) + 1)

    fun settled(today: Int?): Counting {
        val left = copy(tapsWriting = tapsWriting - 1)
        return if (left.tapsWriting > 0) left else left.copy(atLeast = today).caughtUp()
    }

    private fun caughtUp() = if (stored != null && atLeast != null && stored >= atLeast) copy(atLeast = null) else this
}
