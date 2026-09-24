package dev.catsradar.app.notification

import kotlin.time.Instant

/** What a screen can do to the walking notification, without knowing how one is built. */
interface WalkingNotifications {
    /** [count] is how many cats this outing has so far; [startedAt] is when the walk began, null until it has. */
    fun show(count: Int, startedAt: Instant?, appOnScreen: Boolean)

    fun clear()
}
