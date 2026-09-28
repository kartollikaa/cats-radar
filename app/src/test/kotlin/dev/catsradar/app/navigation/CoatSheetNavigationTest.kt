package dev.catsradar.app.navigation

import android.content.Context
import android.os.Looper
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.di.dataModule
import dev.catsradar.app.di.domainModule
import dev.catsradar.app.di.presentationModule
import dev.catsradar.app.di.workerModule
import dev.catsradar.app.notification.tally
import dev.catsradar.app.photo.CameraRequest
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.app.testing.FileProviderCacheReset
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.ui.R
import dev.catsradar.ui.detail.CoatCardTestTag
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.time.Instant

@Config(qualifiers = "w411dp-h891dp-xxhdpi")
@RunWith(AndroidJUnit4::class)
class CoatSheetNavigationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain =
        RuleChain.outerRule(FileProviderCacheReset()).around(ComponentActivityRegistered()).around(compose)

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `a tap on the coat card opens the coat sheet over the detail`() {
        val backStack = show(coat = null, Counter, Encounters, EncounterDetail(ID))
        val card = hasTestTag(CoatCardTestTag)
        awaitTheDatabase { compose.onAllNodes(card).fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(card).performScrollTo().performClick()
        awaitTheDatabase { compose.onAllNodes(sheetTitle()).fetchSemanticsNodes().isNotEmpty() }

        assertEquals(listOf(Counter, Encounters, EncounterDetail(ID), CoatSheet(ID)), backStack.toList())
    }

    @Test
    fun `a coat picked in the sheet shows on the detail's card once the sheet closes`() {
        val backStack = show(coat = null, Counter, Encounters, EncounterDetail(ID), CoatSheet(ID))
        val ginger = hasText(context.getString(R.string.coat_ginger))
        awaitTheDatabase { compose.onAllNodes(sheetTitle()).fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(ginger and hasAnyAncestor(hasTestTag(CoatCardTestTag)).not()).performClick()
        awaitTheDatabase { backStack.toList().last() is EncounterDetail }
        awaitTheDatabase { cardTexts().contains(context.getString(R.string.coat_ginger)) }

        assertEquals(listOf("Ginger", "Coat", "Change"), cardTexts())
    }

    @Test
    fun `a swipe down closes the coat sheet over the detail and leaves the cat's coat as it was`() {
        val backStack = show(coat = CatCoat.BLACK, Counter, Encounters, EncounterDetail(ID), CoatSheet(ID))
        awaitTheDatabase { compose.onAllNodes(sheetTitle()).fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(sheetTitle()).performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 400.dp.toPx(), durationMillis = 500)
        }
        awaitTheDatabase { backStack.toList().last() is EncounterDetail }

        assertEquals(listOf(Counter, Encounters, EncounterDetail(ID)), backStack.toList())
        assertEquals(CatCoat.BLACK, runBlocking { repository().observeById(ID).first() }?.coat)
    }

    private fun show(coat: CatCoat?, vararg keys: NavKey): BottomNavBackStack {
        val koin = startKoin {
            androidContext(context)
            modules(domainModule, dataModule, presentationModule, workerModule)
        }.koin
        runBlocking { koin.get<EncounterRepository>().insert(tally(ID, OCCURRED).copy(coat = coat)) }
        val backStack = BottomNavBackStack(NavBackStack(*keys))
        val entries = catsRadarEntries(backStack, PaddingValues(), CameraRequest(), MapFocusRequest())
        compose.setContent { CatsRadarTheme { CatsRadarNavDisplay(backStack = backStack, entryProvider = entries) } }
        return backStack
    }

    private fun repository(): EncounterRepository = GlobalContext.get().get()

    private fun sheetTitle() = hasText(context.getString(R.string.detail_coat_sheet_title))

    private fun cardTexts(): List<String> {
        val card = compose.onNode(hasTestTag(CoatCardTestTag)).fetchSemanticsNode()
        return card.config[SemanticsProperties.Text].map { it.text }
    }

    private fun awaitTheDatabase(condition: () -> Boolean) = compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    private companion object {
        const val ID = "cat-1"
        const val LOAD_TIMEOUT_MS = 5_000L
        val OCCURRED = Instant.parse("2026-09-21T10:00:00Z")
    }
}
