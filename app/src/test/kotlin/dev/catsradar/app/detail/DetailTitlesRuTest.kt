package dev.catsradar.app.detail

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.ui.coat.titleRes
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@Config(qualifiers = "ru")
@RunWith(AndroidJUnit4::class)
class DetailTitlesRuTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every russian title names the cat as котик`() {
        (CoatOption.entries + null).forEach { coat ->
            val title = context.getString(coat.titleRes())
            assertTrue(title.contains("котик", ignoreCase = true), "$coat: $title")
        }
    }
}
