package dev.catsradar.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertContentEquals

private const val SCHEMA_RELATIVE_PATH = "dev.catsradar.data.db.CatsDatabase/1.json"
private const val EXPORTED_SCHEMA_PATH = "schemas/$SCHEMA_RELATIVE_PATH"
private const val ASSET_SCHEMA_PATH = "src/androidHostTest/assets/$SCHEMA_RELATIVE_PATH"

// MigrationTestHelper reads the schema from Android assets, not Room's data/schemas export
// directory, so a copy of the frozen v1 schema is committed under androidHostTest/assets too.
@RunWith(AndroidJUnit4::class)
class SchemaAssetSyncTest {
    @Test
    fun assetCopyIsByteIdenticalToTheExportedSchema() {
        val exported = File(EXPORTED_SCHEMA_PATH).readBytes()
        val asset = ApplicationProvider.getApplicationContext<Context>()
            .assets
            .open(SCHEMA_RELATIVE_PATH)
            .use { it.readBytes() }

        assertContentEquals(
            exported,
            asset,
            "$EXPORTED_SCHEMA_PATH and $ASSET_SCHEMA_PATH have diverged; a frozen schema must never be edited",
        )
    }
}
