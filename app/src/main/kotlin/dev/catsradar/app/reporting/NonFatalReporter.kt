package dev.catsradar.app.reporting

import kotlinx.coroutines.CancellationException

fun interface NonFatalReporter {
    fun record(error: Throwable)
}

// Throwable, not Exception: an Error such as OutOfMemoryError must not take the process down either.
@Suppress("TooGenericExceptionCaught")
suspend fun NonFatalReporter.recordFailureOf(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        record(e)
    }
}
