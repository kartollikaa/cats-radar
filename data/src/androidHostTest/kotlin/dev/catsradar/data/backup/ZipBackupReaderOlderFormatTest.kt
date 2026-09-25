package dev.catsradar.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.data.platform.AndroidPhotoStorage
import dev.catsradar.domain.platform.BackupReadResult
import dev.catsradar.domain.platform.BackupRejection
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class ZipBackupReaderOlderFormatTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val reader = ZipBackupReader(context, AndroidPhotoStorage(context))

    @Test
    fun aFormatTwoArchiveReadsItsCatsWithNoPickedGalleryItem() = runTest {
        val path = File(temporaryFolder.root, "backup.zip")
        path.writeArchive(
            MANIFEST_ENTRY to FORMAT_TWO_MANIFEST,
            ENCOUNTERS_ENTRY to """[{"id":"a","occurredAt":0,"tzOffsetMinutes":0,"kind":"PHOTO","origin":"GALLERY",""" +
                """"locationSource":"NONE","deviceId":"d","createdAt":0,"updatedAt":0}]""",
            PLACE_CELLS_ENTRY to "[]",
            WALKS_ENTRY to "[]",
            TRACK_POINTS_ENTRY to "[]",
        )

        val read = reader.read(path.path)

        assertIs<BackupReadResult.Readable>(read)
        val cat = read.contents.encounters.single()
        assertEquals("a", cat.id)
        assertEquals(null, cat.cover?.sourceMediaUri)
    }

    @Test
    fun aFormatTwoArchiveCutOffBetweenItsListsIsRefusedRatherThanReadWithoutItsWalks() = runTest {
        val path = File(temporaryFolder.root, "backup.zip")
        path.writeArchive(
            MANIFEST_ENTRY to FORMAT_TWO_MANIFEST,
            ENCOUNTERS_ENTRY to "[]",
            PLACE_CELLS_ENTRY to "[]",
        )

        assertEquals(BackupReadResult.Rejected(BackupRejection.UNREADABLE), reader.read(path.path))
    }

    private companion object {
        const val FORMAT_TWO_MANIFEST = """{"formatVersion":2,"exportedAt":0,"deviceId":"d","appVersion":"1.3.0"}"""
    }
}
