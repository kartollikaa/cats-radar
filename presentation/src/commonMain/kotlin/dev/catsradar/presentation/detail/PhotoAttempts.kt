package dev.catsradar.presentation.detail

import dev.catsradar.domain.model.Encounter

/** Each cat's photo attempt: it runs while its photos are attached and lasts until the cat carries them. */
internal class PhotoAttempts {
    private class Attempt(var progress: AttachProgress) {
        var running = true

        // Attached but not emitted yet: until they arrive the cat's page shows the attempt, not a bare offer.
        val arriving = mutableSetOf<String>()
    }

    private val attempts = mutableMapOf<String, Attempt>()
    private var carried: Map<String, Set<String>> = emptyMap()

    /** Each cat attaching, or waiting for a photo it attached, with how far its attempt got. */
    val progress: Map<String, AttachProgress> get() = attempts.mapValues { it.value.progress }

    fun running(catId: String, progress: AttachProgress) {
        val attempt = attempts.getOrPut(catId) { Attempt(progress) }
        attempt.progress = progress
        attempt.running = true
    }

    fun attached(catId: String, photoId: String) {
        val carries = carried[catId] ?: return
        if (photoId !in carries) attempts[catId]?.arriving?.add(photoId)
    }

    fun finished(catId: String, progress: AttachProgress) {
        attempts[catId]?.let { attempt ->
            attempt.progress = progress
            attempt.running = false
        }
        prune()
    }

    /** Settles every attempt against [cats], the cats on the pages; a cat not among them waits for nothing. */
    fun arrived(cats: List<Encounter>) {
        carried = cats.associate { cat -> cat.id to cat.photos.mapTo(mutableSetOf()) { it.id } }
        attempts.forEach { (catId, attempt) ->
            val carries = carried[catId]
            if (carries == null) attempt.arriving.clear() else attempt.arriving -= carries
        }
        prune()
    }

    private fun prune() {
        attempts.values.removeAll { !it.running && it.arriving.isEmpty() }
    }
}
