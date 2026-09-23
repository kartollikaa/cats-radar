package dev.catsradar.app.notification

/** What a screen can do to the walking notification, without knowing how one is built. */
interface WalkingNotifications {
    /** [count] is how many cats this outing has so far. */
    fun show(count: Int, appOnScreen: Boolean)

    fun clear()
}
