package dev.catsradar.presentation.counter

import dev.catsradar.domain.usecase.AddCatsResult
import dev.catsradar.domain.usecase.AddCatsToPhoto
import dev.catsradar.domain.usecase.SetCoat
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.coat.toCatCoat
import dev.catsradar.presentation.runStorageWrite

/** The writes the coat question after a photo makes once its sheet closes. */
internal class CoatQuestion(private val setCoat: SetCoat, private val addCatsToPhoto: AddCatsToPhoto) {

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

    private suspend fun write(intent: CounterIntent.CoatPrompt, answered: CoatPromptState): List<CounterEffect> =
        when (intent) {
            is CounterIntent.CoatPrompt.Picked -> {
                runStorageWrite { setCoat(answered.catId, intent.coat.toCatCoat()) }
                emptyList()
            }
            CounterIntent.CoatPrompt.SaveClicked -> save(answered, answered.counting?.tray.orEmpty())
            else -> emptyList()
        }

    // The first cat counted is the one the camera saved; each of the others joins its shot.
    private suspend fun save(answered: CoatPromptState, tray: List<CoatOption?>): List<CounterEffect> {
        val photoId = answered.photoId
        var result: AddCatsResult = AddCatsResult.NotAddable
        runStorageWrite {
            tray.first()?.let { setCoat(answered.catId, it.toCatCoat()) }
            if (photoId != null) result = addCatsToPhoto(answered.catId, photoId, tray.drop(1).map { it?.toCatCoat() })
        }
        return when (val added = result) {
            is AddCatsResult.Added -> added.cats.filter { it.needsLocation }.map { CounterEffect.AttachLocation(it.id) }
            AddCatsResult.NotAddable -> listOf(CounterEffect.CatsNotSaved)
        }
    }
}
