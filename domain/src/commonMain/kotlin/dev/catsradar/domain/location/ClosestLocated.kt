package dev.catsradar.domain.location

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.locatedPoint

/** The other live, located cat logged closest in time to [target], the earlier on a tie; null when there is none. */
fun List<Encounter>.closestLocatedInTime(target: Encounter): Encounter? =
    filter { it.id != target.id && it.deletedAt == null && it.locatedPoint() != null }
        .minWithOrNull(
            compareBy<Encounter> { (it.occurredAt - target.occurredAt).absoluteValue }.thenBy { it.occurredAt },
        )
