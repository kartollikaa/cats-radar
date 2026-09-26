package dev.catsradar.app

import org.junit.Test
import java.io.File
import java.util.Properties
import kotlin.test.assertEquals

class UpdateRepositoryConfigTest {

    @Test
    fun theUpdateSourceIsTheRepositoryGradlePropertiesNames() {
        val properties = Properties().apply { File("../gradle.properties").reader().use(::load) }

        assertEquals(properties.getProperty("catsradar.updateRepository"), BuildConfig.UPDATE_REPOSITORY)
    }
}
