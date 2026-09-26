package dev.catsradar.app.detail

import dev.catsradar.presentation.detail.CatPage
import dev.catsradar.presentation.detail.EncounterDetailState
import kotlinx.collections.immutable.persistentListOf

internal fun loadedWith(page: CatPage): EncounterDetailState.Loaded =
    EncounterDetailState.Loaded(pages = persistentListOf(page), currentId = page.id, currentNumber = 1)
