package dev.catsradar.app.worker

interface LocationAttachScheduler {
    fun schedule(encounterId: String)
    fun cancel(encounterId: String)
}
