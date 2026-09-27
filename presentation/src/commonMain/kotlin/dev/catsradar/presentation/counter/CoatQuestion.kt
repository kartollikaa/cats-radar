package dev.catsradar.presentation.counter

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.usecase.AddCatsResult
import dev.catsradar.domain.usecase.AddCatsToPhoto
import dev.catsradar.domain.usecase.SetCoat
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.coat.toCatCoat
import dev.catsradar.presentation.runStorageWrite

/** The coat question after a photo: which cat it asks about, and the writes its answer makes once the sheet closes. */
internal class CoatQuestion(private val setCoat: SetCoat, private val addCatsToPhoto: AddCatsToPhoto) {

    private var asked: AskedPhoto? = null

    fun ask(encounter: Encounter) {
        asked = AskedPhoto(encounter.id, checkNotNull(encounter.cover) { "a photographed cat has its photo" }.id)
    }

    /** Shows the prompt [intent] leads to, then makes a closing answer's writes; returns what is left to the screen. */
    suspend fun answer(
        intent: CounterIntent.CoatPrompt,
        prompt: CoatPromptState?,
        show: (CoatPromptState?) -> Unit,
    ): List<CounterEffect> {
        prompt ?: return emptyList()
        val next = prompt.after(intent)
        // Before any write: the sheet never waits on storage, and a failed write still closes it.
        show(next)
        return if (next == null) write(intent, prompt) else emptyList()
    }

    private suspend fun write(intent: CounterIntent.CoatPrompt, answered: CoatPromptState): List<CounterEffect> {
        val photo = asked ?: return emptyList()
        asked = null
        return when (intent) {
            is CounterIntent.CoatPrompt.Picked -> {
                runStorageWrite { setCoat(photo.encounterId, intent.coat.toCatCoat()) }
                emptyList()
            }
            CounterIntent.CoatPrompt.SaveClicked -> save(photo, answered.counting?.tray.orEmpty())
            else -> emptyList()
        }
    }

    // The first cat counted is the one the camera saved; each of the others joins its shot.
    private suspend fun save(photo: AskedPhoto, tray: List<CoatOption?>): List<CounterEffect> {
        var result: AddCatsResult = AddCatsResult.NotAddable
        runStorageWrite {
            tray.first()?.let { setCoat(photo.encounterId, it.toCatCoat()) }
            result = addCatsToPhoto(photo.encounterId, photo.photoId, tray.drop(1).map { it?.toCatCoat() })
        }
        return when (val added = result) {
            is AddCatsResult.Added -> added.cats.filter { it.needsLocation }.map { CounterEffect.AttachLocation(it.id) }
            AddCatsResult.NotAddable -> listOf(CounterEffect.CatsNotSaved)
        }
    }

    private class AskedPhoto(val encounterId: String, val photoId: String)
}
