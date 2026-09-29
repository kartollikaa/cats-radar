package dev.catsradar.ui.counter

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.catsradar.presentation.counter.CoatCountState
import dev.catsradar.ui.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
internal fun promptTitle(counting: CoatCountState?): String {
    val count = counting?.catCount
    return when {
        counting == null -> stringResource(R.string.counter_coat_prompt_title)
        count == null -> stringResource(R.string.counter_coat_count_title_empty)
        else -> pluralStringResource(R.plurals.counter_coat_count_title, count, count)
    }
}

@Composable
internal fun promptHint(counting: CoatCountState?): String = stringResource(
    when {
        counting == null -> R.string.counter_coat_prompt_hint
        counting.canAdd -> R.string.counter_coat_count_hint
        else -> R.string.counter_coat_count_full
    },
)

@Composable
internal fun countTitles(): ImmutableList<String> {
    val counted = (1..CoatCountState.MOST_CATS).map { pluralStringResource(R.plurals.counter_coat_count_title, it, it) }
    return (counted + stringResource(R.string.counter_coat_count_title_empty)).toImmutableList()
}

@Composable
internal fun countHints(): ImmutableList<String> =
    persistentListOf(stringResource(R.string.counter_coat_count_hint), stringResource(R.string.counter_coat_count_full))
