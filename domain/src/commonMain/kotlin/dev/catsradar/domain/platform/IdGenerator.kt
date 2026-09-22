package dev.catsradar.domain.platform

/** Generates a fresh unique identifier for a new row. */
interface IdGenerator {
    fun newId(): String
}
