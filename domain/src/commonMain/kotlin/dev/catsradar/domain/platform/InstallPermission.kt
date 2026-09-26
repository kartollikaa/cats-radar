package dev.catsradar.domain.platform

/** Whether the user has let this app install packages; asked afresh each time, since it can change any moment. */
fun interface InstallPermission {
    fun granted(): Boolean
}
