package dev.catsradar.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

private const val SCHEMA_DIRECTORY = "dev.catsradar.data.db.CatsDatabase"

// MigrationTestHelper reads schemas from Android assets, not Room's data/schemas export
// directory, so a copy of every frozen schema is committed under androidHostTest/assets too.
@RunWith(AndroidJUnit4::class)
class SchemaAssetSyncTest {
    @Test
    fun everyExportedSchemaHasAByteIdenticalAssetCopy() {
        val exported = File("schemas/$SCHEMA_DIRECTORY").listFiles().orEmpty().map { it.name }.sorted()
        assertEquals(listOf("1.json", "2.json"), exported)

        exported.forEach { name ->
            val relative = "$SCHEMA_DIRECTORY/$name"
            val asset = ApplicationProvider.getApplicationContext<Context>()
                .assets
                .open(relative)
                .use { it.readBytes() }
            assertContentEquals(
                File("schemas/$relative").readBytes(),
                asset,
                "schemas/$relative and its asset copy have diverged; a frozen schema must never be edited",
            )
        }
    }
}
