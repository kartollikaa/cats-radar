package dev.catsradar.app.worker

import androidx.work.ListenableWorker
import dev.catsradar.app.reporting.NonFatalReporter

// A retried failure is the same failure again; recording every attempt would only inflate its count.
internal fun ListenableWorker.recordOnFirstAttempt(reporter: NonFatalReporter, error: Throwable) {
    if (runAttemptCount == 0) reporter.record(error)
}
