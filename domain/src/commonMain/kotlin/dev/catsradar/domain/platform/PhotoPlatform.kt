package dev.catsradar.domain.platform

/** Where an encounter's own copies of a photo live, as paths relative to the app's photo directory. */
data class StoredPhoto(val photoPath: String, val thumbPath: String?)

interface ImageResizer {
    /**
     * Writes a downscaled copy and a thumbnail of [sourceUri], named after [baseName], stripped of
     * metadata. Null when the source cannot be decoded; a null [StoredPhoto.thumbPath] means the
     * copy was written but the thumbnail was not.
     */
    suspend fun store(sourceUri: String, baseName: String): StoredPhoto?
}

interface Digest {
    /** SHA-256 of the bytes at [uri], lowercase hex; null when they cannot be read. */
    suspend fun sha256(uri: String): String?
}

interface GallerySaver {
    /**
     * Copies [sourceUri] into the device gallery.
     *
     * Returns the new item's URI, or null when the gallery rejected it — the encounter is still
     * saved either way, so this never throws.
     */
    suspend fun save(sourceUri: String, displayName: String): String?
}

interface PhotoStorage {
    /** Absolute path for a [StoredPhoto] path, for the one layer that must open the file itself. */
    fun resolve(relativePath: String): String

    suspend fun delete(relativePath: String)
}
