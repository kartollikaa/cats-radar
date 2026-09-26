package dev.catsradar.app.photo

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/** A camera told to write to [target] for [catId]. */
data class PendingCapture(val target: String, val catId: String?)

/**
 * The cameras still open, oldest first. A camera closed under a newer one still reports back, and
 * first, so each result belongs to the oldest capture still waiting.
 */
class PendingCaptures(captures: List<PendingCapture> = emptyList()) {

    private val captures = ArrayDeque(captures)

    fun launched(capture: PendingCapture) {
        captures.addLast(capture)
    }

    /** The capture the next camera result belongs to, or null when no camera was waiting. */
    fun answered(): PendingCapture? = captures.removeFirstOrNull()

    companion object {
        // A queue saved by a version whose shots named no cat restores empty: guessing a cat would be permanent.
        private const val SHAPE = "pending-captures/2"

        val Saver: Saver<PendingCaptures, Any> = listSaver(
            save = { pending -> listOf(SHAPE) + pending.captures.flatMap { listOf(it.target, it.catId.orEmpty()) } },
            restore = { saved ->
                if (saved.firstOrNull() != SHAPE) {
                    PendingCaptures()
                } else {
                    PendingCaptures(
                        saved.drop(1).chunked(2) { (target, catId) -> PendingCapture(target, catId.ifEmpty { null }) },
                    )
                }
            },
        )
    }
}
