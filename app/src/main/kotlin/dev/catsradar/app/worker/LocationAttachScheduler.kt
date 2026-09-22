package dev.catsradar.app.worker

fun interface LocationAttachScheduler {
    fun schedule(encounterId: String)
}
