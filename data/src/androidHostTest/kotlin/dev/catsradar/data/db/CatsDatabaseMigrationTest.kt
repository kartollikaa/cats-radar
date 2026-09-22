package dev.catsradar.data.db

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatsDatabaseMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        databaseClass = CatsDatabase::class,
        driver = BundledSQLiteDriver(),
        file = instrumentation.targetContext.getDatabasePath("cats-radar-migration-test"),
    )

    // Proves the harness (schema export, room3-testing, MigrationTestHelper) actually works, from
    // the committed version-1 baseline. Adding v2 should only need a runMigrationsAndValidate case.
    @Test
    fun schemaVersionOneOpensCleanly() = runTest {
        helper.createDatabase(1).close()
    }
}
