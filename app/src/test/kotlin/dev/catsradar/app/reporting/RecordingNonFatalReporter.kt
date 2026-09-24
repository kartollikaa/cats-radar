package dev.catsradar.app.reporting

class RecordingNonFatalReporter : NonFatalReporter {
    val recorded = mutableListOf<Throwable>()

    override fun record(error: Throwable) {
        recorded += error
    }
}
