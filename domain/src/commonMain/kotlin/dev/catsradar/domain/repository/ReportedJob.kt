package dev.catsradar.domain.repository

/** A background job whose finished run is reported to the user until they deal with it. */
enum class ReportedJob {
    GALLERY_IMPORT,
    BACKUP,
}
