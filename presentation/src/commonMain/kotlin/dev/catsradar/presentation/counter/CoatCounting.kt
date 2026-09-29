package dev.catsradar.presentation.counter

import dev.catsradar.presentation.coat.CoatOption
import kotlinx.collections.immutable.toPersistentList

/** The prompt to show after [intent]; null once the sheet closes. */
internal fun CoatPromptState.after(intent: CounterIntent.CoatPrompt): CoatPromptState? = when (intent) {
    CounterIntent.CoatPrompt.SeveralClicked -> copy(counting = counting ?: CoatCountState())
    CounterIntent.CoatPrompt.OneCatClicked -> copy(counting = null)
    is CounterIntent.CoatPrompt.Picked -> counting?.let { copy(counting = it.counted(intent.coat)) }
    CounterIntent.CoatPrompt.UnseenPicked -> counting?.let { copy(counting = it.counted(null)) }
    is CounterIntent.CoatPrompt.TrayCatClicked -> copy(counting = counting?.without(intent.index, intent.coat))
    CounterIntent.CoatPrompt.SaveClicked -> takeIf { counting?.tray.isNullOrEmpty() }
    CounterIntent.CoatPrompt.Dismissed -> null
}

private fun CoatCountState.counted(coat: CoatOption?): CoatCountState =
    if (canAdd) copy(tray = tray.toPersistentList().add(coat)) else this

private fun CoatCountState.without(index: Int, coat: CoatOption?): CoatCountState =
    if (index in tray.indices && tray[index] == coat) copy(tray = tray.toPersistentList().removeAt(index)) else this
