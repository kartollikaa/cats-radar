package dev.catsradar.presentation.settings

/** How an install the system was asked for ended, when it did not replace the app. */
enum class InstallOutcome {
    CANCELLED,

    /** Signed with another key than the installed app. */
    CONFLICT,

    /** Not built for this phone, such as another CPU architecture. */
    INCOMPATIBLE,
    STORAGE,
    MISSING_PACKAGE,
    NOT_THIS_APP,
    NOT_NEWER,
    FAILED,
}
