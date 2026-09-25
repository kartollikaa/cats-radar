package dev.catsradar.presentation.regions

import dev.catsradar.domain.region.RegionKey
import dev.catsradar.domain.region.RegionLabel
import dev.catsradar.domain.region.RegionNode
import dev.catsradar.domain.usecase.RegionView
import dev.catsradar.presentation.encounters.EncountersRow
import dev.catsradar.presentation.encounters.EncountersStateMapper
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.OutingHeader
import dev.catsradar.presentation.encounters.photoFixture
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class RegionsStateMapperTest {

    private val encountersMapper = EncountersStateMapper(FakeDateTimeFormatter(), FakePhotoStorage())
    private val mapper = RegionsStateMapper(encountersMapper)

    private val spain = RegionKey.Country("ES")
    private val barcelona = RegionKey.City("ES", "Barcelona")

    private val oneRowPerKey = RegionView.Places(
        listOf(
            RegionKey.Country("ES"),
            RegionKey.City("ES", "Barcelona"),
            RegionKey.Area("sp3e3", RegionKey.City("ES", "Barcelona")),
            RegionKey.Unresolved,
            RegionKey.NoCity("ES"),
            RegionKey.NoLocation,
        ).map { RegionNode(it, RegionLabel.Named("x"), count = 1) },
    )

    @Test
    fun `the top level is Places, counts every cat, and lists countries`() {
        val view = RegionView.Places(
            listOf(
                RegionNode(spain, RegionLabel.Named("Spain"), 3),
                RegionNode(RegionKey.NoLocation, RegionLabel.NoLocation, 1),
            ),
        )

        assertEquals(
            RegionsState.Places(
                header = RegionsHeader(RegionsTitle.AllPlaces, count = 4),
                section = RegionsSection.COUNTRIES,
                rows = persistentListOf(
                    RegionRowState(
                        RegionRowKey.Country("ES"),
                        RegionRowLabel.Named("Spain"),
                        countLabel = "3",
                        share = 0.75f,
                        pseudo = false,
                        flag = "🇪🇸",
                    ),
                    RegionRowState(
                        RegionRowKey.NoLocation,
                        RegionRowLabel.NoLocation,
                        countLabel = "1",
                        share = 0.25f,
                        pseudo = true,
                    ),
                ),
            ),
            mapper.map(view, parent = null, TODAY),
        )
    }

    // The own row's count differs from the children's sum, so the test tells which one the header took.
    @Test
    fun `a level below the top is named and counted by its own row`() {
        val view = RegionView.Places(
            children = listOf(
                RegionNode(barcelona, RegionLabel.Named("Barcelona"), 2),
                RegionNode(RegionKey.NoCity("ES"), RegionLabel.NoCity, 1),
            ),
            self = RegionNode(spain, RegionLabel.Named("Spain"), 4),
        )

        val state = assertIs<RegionsState.Places>(mapper.map(view, parent = spain, TODAY))

        assertEquals(
            RegionsHeader(RegionsTitle.Of(RegionRowLabel.Named("Spain")), count = 4, flag = "🇪🇸"),
            state.header,
        )
        assertEquals(RegionsSection.CITIES, state.section)
    }

    @Test
    fun `a level whose own row has gone has no header`() {
        val places = RegionView.Places(listOf(RegionNode(barcelona, RegionLabel.Named("Barcelona"), 2)), self = null)
        val cats = RegionView.Cats(listOf(photoFixture("lone", Instant.parse("2026-09-22T10:00:00Z"))), self = null)

        assertNull(assertIs<RegionsState.Places>(mapper.map(places, parent = spain, TODAY)).header)
        assertNull(assertIs<RegionsState.Cats>(mapper.map(cats, parent = RegionKey.NoLocation, TODAY)).header)
    }

    @Test
    fun `each kind of parent names its section, and the three sections differ`() {
        val level = RegionView.Places(listOf(RegionNode(barcelona, RegionLabel.Named("x"), 1)))
        val sections = listOf(null, spain, barcelona, RegionKey.NoCity("ES"), RegionKey.Unresolved)
            .map { assertIs<RegionsState.Places>(mapper.map(level, it, TODAY)).section }

        assertEquals(
            listOf(
                RegionsSection.COUNTRIES,
                RegionsSection.CITIES,
                RegionsSection.AREAS,
                RegionsSection.AREAS,
                RegionsSection.AREAS,
            ),
            sections,
        )
        assertEquals(3, sections.toSet().size)
    }

    @Test
    fun `a level's shares add up to one`() {
        val view = RegionView.Places(
            listOf(5, 3, 2).mapIndexed { i, count ->
                RegionNode(RegionKey.City("ES", "c$i"), RegionLabel.Named("c$i"), count)
            },
        )

        val shares = assertIs<RegionsState.Places>(mapper.map(view, spain, TODAY)).rows.map { it.share }

        assertEquals(listOf(0.5f, 0.3f, 0.2f), shares)
        assertTrue(abs(shares.sum() - 1f) < 1e-6f, "sum ${shares.sum()}")
    }

    @Test
    fun `a level whose rows count no cat gets empty shares, never a division by zero`() {
        val view = RegionView.Places(listOf(RegionNode(barcelona, RegionLabel.Named("Barcelona"), count = 0)))

        assertEquals(listOf(0f), assertIs<RegionsState.Places>(mapper.map(view, spain, TODAY)).rows.map { it.share })
    }

    @Test
    fun `only country rows carry a flag`() {
        val flags = assertIs<RegionsState.Places>(mapper.map(oneRowPerKey, spain, TODAY)).rows.map { it.flag }

        assertEquals(listOf("🇪🇸", null, null, null, null, null), flags)
    }

    @Test
    fun `a country's header carries its flag, and the places above a level ride along its header`() {
        val spainNode = RegionNode(spain, RegionLabel.Named("Spain"), 3)
        val country = RegionView.Places(listOf(RegionNode(barcelona, RegionLabel.Named("Barcelona"), 3)), spainNode)
        val city = RegionView.Places(
            children = listOf(RegionNode(RegionKey.Area("sp3e9", barcelona), RegionLabel.Named("Gràcia"), 2)),
            self = RegionNode(barcelona, RegionLabel.Named("Barcelona"), 2),
            trail = listOf(spainNode),
        )

        val countryHeader = assertIs<RegionsState.Places>(mapper.map(country, spain, TODAY)).header
        val cityHeader = assertIs<RegionsState.Places>(mapper.map(city, barcelona, TODAY)).header

        assertEquals(
            RegionsHeader(RegionsTitle.Of(RegionRowLabel.Named("Spain")), count = 3, flag = "🇪🇸"),
            countryHeader,
        )
        assertEquals(
            RegionsHeader(
                RegionsTitle.Of(RegionRowLabel.Named("Barcelona")),
                count = 2,
                trail = persistentListOf(RegionsCrumb(RegionRowLabel.Named("Spain"), flag = "🇪🇸")),
            ),
            cityHeader,
        )
    }

    @Test
    fun `only the rows standing for no place are pseudo`() {
        val pseudo = assertIs<RegionsState.Places>(mapper.map(oneRowPerKey, spain, TODAY)).rows
            .filter { it.pseudo }
            .map { it.key }

        assertEquals(listOf(RegionRowKey.Unresolved, RegionRowKey.NoCity("ES"), RegionRowKey.NoLocation), pseudo)
    }

    @Test
    fun `an area's cats are the Encounters list rows, one per cat, under the area's own header`() {
        val base = Instant.parse("2026-09-22T10:00:00Z")
        val cats = listOf(photoFixture("older", base), photoFixture("newer", base + 5.minutes))
        val area = RegionKey.Area("sp3e3", barcelona)
        val view = RegionView.Cats(cats, self = RegionNode(area, RegionLabel.Named("Gràcia"), 3))

        val state = assertIs<RegionsState.Cats>(mapper.map(view, parent = area, TODAY))

        assertEquals(RegionsHeader(RegionsTitle.Of(RegionRowLabel.Named("Gràcia")), count = 3), state.header)
        assertEquals(encountersMapper.map(cats, TODAY, grid = false).rows, state.rows)
        assertEquals(
            listOf("newer", "older"),
            state.rows.filterNot { it is OutingHeader }.map { assertIs<EncountersRow.Single>(it).cell.id },
        )
    }

    @Test
    fun `every region key reaches the screen as a row key of its own`() {
        assertEquals(
            listOf(
                RegionRowKey.Country("ES"),
                RegionRowKey.City("ES", "Barcelona"),
                RegionRowKey.Area("sp3e3", RegionRowKey.City("ES", "Barcelona")),
                RegionRowKey.Unresolved,
                RegionRowKey.NoCity("ES"),
                RegionRowKey.NoLocation,
            ),
            mapper.map(oneRowPerKey, spain, TODAY).places().rows.map { it.key },
        )
    }

    @Test
    fun `an area's row key names the parent it was listed under, each parent its own`() {
        val parents = listOf(RegionKey.City("ES", "Barcelona"), RegionKey.NoCity("ES"), RegionKey.Unresolved)
        val view = RegionView.Places(parents.map { RegionNode(RegionKey.Area("sp3e3", it), RegionLabel.Named("x"), 1) })

        assertEquals(
            listOf(
                RegionRowKey.Area("sp3e3", RegionRowKey.City("ES", "Barcelona")),
                RegionRowKey.Area("sp3e3", RegionRowKey.NoCity("ES")),
                RegionRowKey.Area("sp3e3", RegionRowKey.Unresolved),
            ),
            mapper.map(view, barcelona, TODAY).places().rows.map { it.key },
        )
    }

    @Test
    fun `every region label reaches the screen as a token of its own, coordinates to five decimals`() {
        val labels = listOf(
            RegionLabel.Named("Gràcia"),
            RegionLabel.Coordinates(lat = 41.398644, lon = 2.178419),
            RegionLabel.Unresolved,
            RegionLabel.NoCity,
            RegionLabel.NoLocation,
        )
        val area = RegionKey.Area("sp3e3", RegionKey.City("ES", "Barcelona"))
        val view = RegionView.Places(labels.map { RegionNode(area, it, 1) })

        assertEquals(
            listOf(
                RegionRowLabel.Named("Gràcia"),
                RegionRowLabel.Coordinates("41.39864, 2.17842"),
                RegionRowLabel.Unresolved,
                RegionRowLabel.NoCity,
                RegionRowLabel.NoLocation,
            ),
            mapper.map(view, barcelona, TODAY).places().rows.map { it.label },
        )
    }

    @Test
    fun `an empty level says what it lacks, each in its own words`() {
        val noPlaces = RegionView.Places(emptyList())

        assertEquals(
            listOf(
                RegionsState.Empty(RegionsEmptyLabel.NO_PLACES_YET, RegionsEmptyHint.HOW_PLACES_APPEAR),
                RegionsState.Empty(RegionsEmptyLabel.NO_PLACES_HERE, hint = null),
                RegionsState.Empty(RegionsEmptyLabel.NO_CATS_HERE, hint = null),
            ),
            listOf(
                mapper.map(noPlaces, parent = null, TODAY),
                mapper.map(noPlaces, parent = spain, TODAY),
                mapper.map(RegionView.Cats(emptyList()), parent = RegionKey.NoLocation, TODAY),
            ),
        )
    }

    private fun RegionsState.places() = assertIs<RegionsState.Places>(this)

    private companion object {
        val TODAY = LocalDate(2026, 9, 22)
    }
}
