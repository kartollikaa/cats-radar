package dev.catsradar.app.photo

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/**
 * Where each open camera was told to write, oldest first. A camera closed under a newer one still
 * reports back, and first, so each result belongs to the oldest target still waiting.
 */
class PendingCaptures(targets: List<String> = emptyList()) {

    private val targets = ArrayDeque(targets)

    fun launched(target: String) {
        targets.addLast(target)
    }

    /** The target the next camera result belongs to, or null when no camera was waiting. */
    fun answered(): String? = targets.removeFirstOrNull()

    companion object {
        val Saver: Saver<PendingCaptures, Any> = listSaver(
            save = { it.targets.toList() },
            restore = { PendingCaptures(it) },
        )
    }
}
