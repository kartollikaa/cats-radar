package dev.catsradar.data.db

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class DatabaseVersionTest {

    @Test
    fun theReportedVersionIsTheNewestExportedSchema() {
        val exported = File("schemas/${CatsDatabase::class.qualifiedName}")
            .listFiles { file -> file.extension == "json" }
            .orEmpty()
            .map { file -> file.nameWithoutExtension.toInt() }

        assertEquals(exported.max(), CATS_DATABASE_VERSION)
    }
}
