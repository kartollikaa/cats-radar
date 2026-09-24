package dev.catsradar.app.reporting

import kotlinx.coroutines.CancellationException

fun interface NonFatalReporter {
    fun record(error: Throwable)
}

@Suppress("TooGenericExceptionCaught") // whatever the block throws is recorded, not rethrown
suspend fun NonFatalReporter.recordFailureOf(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        record(e)
    }
}
