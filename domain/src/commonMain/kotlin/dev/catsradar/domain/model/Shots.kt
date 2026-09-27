package dev.catsradar.domain.model

/**
 * The cats whose covers share a shot as one group, oldest first, groups in the order their shots first appear. A cat
 * whose cover is no other cat's photo, or which has none, is a group of its own.
 */
fun List<Encounter>.groupedByShot(): List<List<Encounter>> =
    groupBy { cat -> cat.cover?.let { "shot-${it.shotId}" } ?: "cat-${cat.id}" }
        .values
        .map { cats -> cats.sortedWith(compareBy<Encounter> { it.createdAt }.thenBy { it.id }) }
