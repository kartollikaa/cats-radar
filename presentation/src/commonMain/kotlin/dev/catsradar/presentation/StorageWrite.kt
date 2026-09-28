package dev.catsradar.presentation

import kotlinx.coroutines.CancellationException

/** Runs [block], turning any failure into [onFailure]; cancellation still propagates. */
@Suppress("TooGenericExceptionCaught", "SwallowedException") // any storage failure degrades the same way
internal suspend fun runStorageWrite(onFailure: () -> Unit = {}, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure()
    }
}

/** [block]'s result, or [fallback] when storage fails; cancellation still propagates. */
internal suspend fun <T> runStorageRead(fallback: T, block: suspend () -> T): T {
    var result = fallback
    runStorageWrite { result = block() }
    return result
}
