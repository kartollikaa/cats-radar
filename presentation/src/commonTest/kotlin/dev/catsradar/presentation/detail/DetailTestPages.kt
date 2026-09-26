package dev.catsradar.presentation.detail

import kotlin.test.assertIs

internal fun EncounterDetailStore.shownPage(): CatPage =
    assertIs<EncounterDetailState.Loaded>(state.value).let { loaded ->
        loaded.pages.single { it.id == loaded.currentId }
    }
