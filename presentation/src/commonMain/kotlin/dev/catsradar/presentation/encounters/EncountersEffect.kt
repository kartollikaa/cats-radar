package dev.catsradar.presentation.encounters

sealed interface EncountersEffect {
    data class OpenEncounter(val id: String) : EncountersEffect
}
