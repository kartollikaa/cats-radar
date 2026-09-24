package dev.catsradar.app

import dev.catsradar.app.reporting.NonFatalReporter
import dev.catsradar.app.reporting.recordFailureOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// A repair that fails is retried at the next start; it must never take the app down with it.
class StartupRepairs(
    private val repairs: List<suspend () -> Unit>,
    private val reporter: NonFatalReporter,
) {
    fun launchIn(scope: CoroutineScope) {
        repairs.forEach { repair -> scope.launch { reporter.recordFailureOf(repair) } }
    }
}
