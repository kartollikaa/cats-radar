# Design pass on the remaining screens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring Places, the Counter's import status and the two coat sheets onto the look slices 23a–23d set.

**Architecture:** Three independent slices, each a PR from `main`. D1 adds the level's own node to
`RegionView`, reshapes `RegionsState` into `Places`/`Cats` with a header, a section and row shares,
and redraws `RegionsScreen` with `SectionCard` and the shared `EncounterRows`. D2 and D3 are `:ui`
layout changes with Robolectric tests in `:app`.

**Tech Stack:** Kotlin Multiplatform (`:domain`, `:presentation`), Compose Material 3 (`:ui`),
Navigation 3 + Koin (`:app`), kotlin.test + Turbine, Robolectric + compose-ui-test in `:app`.

**Spec:** [docs/superpowers/specs/2026-09-25-design-pass-remaining-screens-design.md](../../specs/2026-09-25-design-pass-remaining-screens-design.md)

## Global Constraints

- Every user-facing string in `ui/src/main/res/values/strings.xml` **and** `values-ru/strings.xml`; Russian
  keeps «котик» for cat, as the existing strings do.
- No colour constants in `:ui` screens; colours come from `MaterialTheme.colorScheme`.
- State holds no lambdas, only immutable collections; tokens in State, words in resources.
- Comments: default none; one line at most (`docs/rules/code-commenting-standards.md`).
- `./gradlew check` green before each PR (as `CI=true`).
- Branches from `origin/main`: `feature/places-design` (D1), `feature/import-status-card` (D2),
  `feature/coat-sheets-design` (D3).

---

## D1 — Places on the card rhythm

### Task 1: The level's own node in `RegionView`

**Files:**
- Modify: `domain/src/commonMain/kotlin/dev/catsradar/domain/usecase/ObserveRegion.kt`
- Test: `domain/src/commonTest/kotlin/dev/catsradar/domain/usecase/ObserveRegionTest.kt`

**Interfaces:**
- Produces: `RegionView.self: RegionNode?`; `RegionView.Places(children, self = null)`,
  `RegionView.Cats(encounters, self = null)`.

- [ ] **Step 1: Write the failing tests** in `ObserveRegionTest`:

```kotlin
@Test
fun `the top level has no node of its own`() = runTest {
    assertEquals(null, view(parent = null).self)
}

@Test
fun `every level below the top carries its node exactly as the level above lists it`() = runTest {
    val spain = RegionKey.Country("ES")
    val barcelonaCity = RegionKey.City("ES", "Barcelona")
    val levels = listOf(
        null to spain,
        null to RegionKey.Unresolved,
        null to RegionKey.NoLocation,
        spain to barcelonaCity,
        spain to RegionKey.NoCity("ES"),
        barcelonaCity to areaOf(barcelona, barcelonaCity),
        RegionKey.Unresolved to areaOf(pending, RegionKey.Unresolved),
    )

    levels.forEach { (above, level) ->
        val listed = assertIs<RegionView.Places>(view(above)).children.single { it.key == level }
        assertEquals(listed, view(level).self, "$level")
    }
}
```

Also change the two `assertEquals(RegionView.Cats(listOf(…)), view)` tests to compare
`assertIs<RegionView.Cats>(view).encounters` with the list.

- [ ] **Step 2: Run to see them fail**

Run: `./gradlew :domain:testAndroidHostTest --tests 'dev.catsradar.domain.usecase.ObserveRegionTest'`
Expected: compile error, `self` unresolved.

- [ ] **Step 3: Implement**

```kotlin
sealed interface RegionView {
    /** This level's node as the level above lists it; null at the top. */
    val self: RegionNode?

    data class Places(val children: List<RegionNode>, override val self: RegionNode? = null) : RegionView
    data class Cats(val encounters: List<Encounter>, override val self: RegionNode? = null) : RegionView
}
```

In `invoke`, compute `val self = parent?.let { levelNode(it, encounters, cells) }` and pass it to every
branch but the top one, with:

```kotlin
private fun levelNode(key: RegionKey, encounters: List<Encounter>, cells: List<PlaceCell>): RegionNode? {
    val siblings = when (key) {
        is RegionKey.Country, RegionKey.Unresolved, RegionKey.NoLocation -> RegionTree.countries(encounters, cells)
        is RegionKey.City -> RegionTree.cities(key.countryCode, encounters, cells)
        is RegionKey.NoCity -> RegionTree.cities(key.countryCode, encounters, cells)
        is RegionKey.Area -> RegionTree.areas(key.parent, encounters, cells)
    }
    return siblings.firstOrNull { it.key == key }
}
```

- [ ] **Step 4: Run the domain tests** — same command, all PASS. Break `levelNode` (return the first
  sibling) and see the new test fail, then restore.
- [ ] **Step 5: Commit** `feature: a region level knows its own row`.

### Task 2: Places state — header, section, shares, the cats as Encounters rows

**Files:**
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/regions/RegionsState.kt`,
  `RegionsStateMapper.kt`, `RegionsStore.kt`
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/encounters/EncountersState.kt`
  (delete `EncounterListItem`; `OutingHeader` implements only `EncountersRow`),
  `EncountersStateMapper.kt` (delete `mapList`)
- Test: `presentation/src/commonTest/kotlin/dev/catsradar/presentation/regions/RegionsStateMapperTest.kt`,
  `RegionsStoreTest.kt`, `presentation/src/commonTest/.../encounters/EncountersStateMapperTest.kt`
  (drop the `mapList` tests)

**Interfaces:**
- Consumes: `RegionView.self` (Task 1).
- Produces:

```kotlin
sealed interface RegionsState {
    data object Loading : RegionsState
    data class Empty(val label: RegionsEmptyLabel) : RegionsState
    /** [header] is null only when the level above no longer lists this level. */
    data class Places(
        val header: RegionsHeader?,
        val section: RegionsSection,
        val rows: ImmutableList<RegionRowState>,
    ) : RegionsState
    data class Cats(val header: RegionsHeader?, val rows: ImmutableList<EncountersRow>) : RegionsState
}

data class RegionsHeader(val title: RegionsTitle, val count: Int)

sealed interface RegionsTitle {
    data object AllPlaces : RegionsTitle
    data class Of(val label: RegionRowLabel) : RegionsTitle
}

enum class RegionsSection { COUNTRIES, CITIES, AREAS }

data class RegionRowState(
    val key: RegionRowKey,
    val label: RegionRowLabel,
    val countLabel: String,
    /** This row's part of the level's cats, from 0 to 1. */
    val share: Float,
    /** Not named yet, No city and No location: rows that stand for the lack of a place. */
    val pseudo: Boolean,
)
```

`RegionsStateMapper.map(view: RegionView, parent: RegionKey?, today: LocalDate): RegionsState`.

- [ ] **Step 1: Rewrite `RegionsStateMapperTest`** for the new API. Keep the key, parent-key, label and
  empty tests, passing `parent` instead of `topLevel`. Add:
  - *the top level is Places, counts every cat, and lists countries*: children Spain 3 and No location 1,
    `parent = null` → `Places(RegionsHeader(AllPlaces, 4), COUNTRIES, [Row(Country ES, Named Spain, "3",
    0.75f, false), Row(NoLocation, NoLocation, "1", 0.25f, true)])`, whole-state `assertEquals`.
  - *a level below the top is named and counted by its own row*: children Barcelona 2 and No city 1,
    `self = RegionNode(Country ES, Named Spain, 3)`, `parent = Country ES` → header
    `Of(Named("Spain"))`, 3; section `CITIES`.
  - *each kind of parent names its section, and the three sections differ*: `null → COUNTRIES`,
    `Country → CITIES`, `City`, `NoCity`, `Unresolved → AREAS`; `assertEquals(3, setOf(…).size)`.
  - *a level's shares add up to one*: counts 5, 3, 2 → shares 0.5, 0.3, 0.2, sum within 1e-6 of 1.
  - *only the rows standing for no place are pseudo*: one node per key kind → `pseudo` true exactly for
    `Unresolved`, `NoCity`, `NoLocation`.
  - *an area's cats are the Encounters list rows, one per cat, under the area's own header*: two photo
    cats 5 minutes apart, `self = RegionNode(area, Named("Gràcia"), 2)` → `Cats(header Of(Named
    ("Gràcia")), 2, rows == EncountersStateMapper(…).map(cats, today, grid = false).rows)` and every
    cat row is `EncountersRow.Single`.
  - *a level whose own row has gone has no header*: `Places(children, self = null)` below the top →
    `header == null`.
- [ ] **Step 2: Run to see them fail** —
  `./gradlew :presentation:testAndroidHostTest --tests 'dev.catsradar.presentation.regions.*'`, compile errors.
- [ ] **Step 3: Implement the state and mapper**

```kotlin
fun map(view: RegionView, parent: RegionKey?, today: LocalDate): RegionsState = when (view) {
    is RegionView.Places -> when {
        view.children.isEmpty() && parent == null -> RegionsState.Empty(RegionsEmptyLabel.NO_PLACES_YET)
        view.children.isEmpty() -> RegionsState.Empty(RegionsEmptyLabel.NO_PLACES_HERE)
        else -> {
            val total = view.children.sumOf { it.count }
            RegionsState.Places(
                header = header(parent, view.self, total),
                section = parent.section(),
                rows = view.children.map { it.toRow(total) }.toPersistentList(),
            )
        }
    }
    is RegionView.Cats -> when {
        view.encounters.isEmpty() -> RegionsState.Empty(RegionsEmptyLabel.NO_CATS_HERE)
        else -> RegionsState.Cats(
            header = header(parent, view.self, view.encounters.size),
            rows = encountersMapper.map(view.encounters, today, grid = false).rows,
        )
    }
}

private fun header(parent: RegionKey?, self: RegionNode?, total: Int): RegionsHeader? = when {
    parent == null -> RegionsHeader(RegionsTitle.AllPlaces, total)
    self != null -> RegionsHeader(RegionsTitle.Of(self.label.toRowLabel()), self.count)
    else -> null
}

private fun RegionKey?.section(): RegionsSection = when (this) {
    null -> RegionsSection.COUNTRIES
    is RegionKey.Country -> RegionsSection.CITIES
    else -> RegionsSection.AREAS
}

private fun RegionNode.toRow(total: Int) = RegionRowState(
    key = key.toRowKey(),
    label = label.toRowLabel(),
    countLabel = count.toString(),
    share = count.toFloat() / total,
    pseudo = key == RegionKey.Unresolved || key is RegionKey.NoCity || key == RegionKey.NoLocation,
)
```

`RegionsStore` calls `stateMapper.map(view, parent, clock.today(timeZone))`. Delete `mapList` and
`EncounterListItem`; `OutingHeader` keeps `: EncountersRow` only.

- [ ] **Step 4: Update `RegionsStoreTest`** — `RegionsState.Loaded(rows = …)` becomes `RegionsState.Places`
  with the header and section the store now produces; `encounterIds()` reads
  `assertIs<RegionsState.Cats>(…).rows.filterIsInstance<EncountersRow.Single>().map { it.cell.id }`.
- [ ] **Step 5: Run** `:presentation:testAndroidHostTest` → PASS; break `share` (use `1f`) and the
  section (`AREAS` for a country) and see the tests fail, then restore.
- [ ] **Step 6: Commit** `feature: a Places level names itself, counts its rows' share and lists its cats as Encounters does`.

### Task 3: `RegionsScreen` on the card rhythm

**Files:**
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/regions/RegionsScreen.kt`
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/encounters/EncountersScreen.kt` (`EncounterRows` gains
  `leadingItem: (@Composable () -> Unit)? = null`, drawn as the list's first item)
- Create: `ui/src/main/res/drawable/ic_location_on.xml` (Material Symbols Rounded `location_on`, 960 viewport)
- Modify: `ui/src/main/res/values/strings.xml`, `values-ru/strings.xml`
- Modify: `app/src/main/kotlin/dev/catsradar/app/navigation/CatsRadarNavHost.kt`
- Test: `app/src/test/kotlin/dev/catsradar/app/regions/RegionsScreenTest.kt`,
  `app/src/test/kotlin/dev/catsradar/app/navigation/RegionsDrillDownTest.kt`

**Interfaces:**
- Consumes: `RegionsState.Places`, `RegionsState.Cats`, `RegionsHeader`, `RegionsTitle`, `RegionsSection`,
  `RegionRowState.share`, `RegionRowState.pseudo` (Task 2).
- Produces: `RegionsScreen(state, modifier, contentPadding, onRegionClick, onEncounterClick, onOutingMapClick)`.

Strings (EN / RU):

| name | EN | RU |
|---|---|---|
| `regions_title` | Places | Места |
| `regions_section_countries` | Countries | Страны |
| `regions_section_cities` | Cities | Города |
| `regions_section_areas` | Areas | Районы |
| `regions_no_places_yet_hint` | Cats logged with a location are grouped here by country and city | Котики с местом соберутся здесь по странам и городам |
| plurals `regions_cat_count` | %d cat / %d cats | %d котик / %d котика / %d котиков / %d котика |

- [ ] **Step 1: Write the failing tests** in `RegionsScreenTest`:
  - *a level of places names itself, counts its cats and titles its rows*: `Places(RegionsHeader(Of(Named
    ("Spain")), 128), CITIES, rows)` → text "Spain", "128 cats", "Cities" exist; "Spain" is a heading.
  - *the top level is called Places*: header `AllPlaces` → `regions_title` shown.
  - *an area's cats are drawn as cards with their time and place, under the area's headline*: `Cats(…)`
    with two `EncountersRow.Single` → both time labels and the header's label exist.
  - *an outing header's On the map hands back the outing*: `OutingHeader(mapOutingId = "o1")` →
    click `encounters_outing_on_map` → `onOutingMapClick` got `"o1"`.
  - *the first empty level says how places appear*: `Empty(NO_PLACES_YET)` → title and hint both shown;
    `NO_PLACES_HERE` shows no hint.
  Update `RegionsDrillDownTest` to build `RegionsState.Places(…)` / `RegionsState.Cats(…)` with
  `EncountersRow.Single` cells, and keep its two tap tests (row keys, cat ids; a header without a map
  outing has no click action).
- [ ] **Step 2: Run to see them fail** — `./gradlew :app:testDebugUnitTest --tests 'dev.catsradar.app.regions.*' --tests 'dev.catsradar.app.navigation.RegionsDrillDownTest'`.
- [ ] **Step 3: Implement** `RegionsScreen`:
  - `Places`: a `verticalScroll` `Column`, `padding(contentPadding).padding(16.dp)`, `spacedBy(24.dp)`:
    `RegionsHeadline(header)` then `SectionCard(section.titleRes()) { rows.forEach { RegionRow(…) } }`.
  - `Cats`: `EncounterRows(rows, EncountersLayout.LIST, contentPadding = contentPadding, leadingItem =
    header?.let { { RegionsHeadline(it, Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)) } },
    onEncounterClick, onOutingMapClick = onOutingMapClick)`.
  - `RegionsHeadline`: `Surface(shape = extraLarge, color = primaryContainer, contentColor =
    onPrimaryContainer)`, `Column(padding(horizontal = 24.dp, vertical = 20.dp), spacedBy(4.dp))`:
    title in `headlineSmall` with `semantics { heading() }`, then
    `pluralStringResource(R.plurals.regions_cat_count, count, count)` in `titleMedium`.
  - `RegionRow`: `Row(fillMaxWidth().clickable(onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    spacedBy(12.dp), CenterVertically)` → `Column(weight(1f), spacedBy(8.dp))` holding a `Row` of the name
    (`bodyLarge`, `onSurfaceVariant` when `pseudo`, `weight(1f)`) and the count (`titleMedium`), then
    `ShareBar(share)`; then the chevron icon, `onSurfaceVariant`, no description.
  - `ShareBar`: a 4 dp high `Box`, `clip(CircleShape)`, `surfaceContainerHighest` track, inner `Box`
    `fillMaxWidth(share).fillMaxHeight().background(primary)`.
  - `EmptyRegions`: the 56 dp `ic_location_on` in `primary`, the label's words in `titleLarge`, and for
    `NO_PLACES_YET` the hint in `bodyMedium` on `onSurfaceVariant`, centred, like `EmptyStatistics`.
  - Previews: every level, the cats level, and each empty label.
  - `EncounterRows`: `leadingItem?.let { item(key = "leading", contentType = "leading") { it() } }`
    before the rows.
  - `CatsRadarNavHost`: `RegionsDestination` takes `onOutingMapClick` and the `Regions` entry passes
    `{ id -> mapFocus.postOuting(id); backStack.selectTab(BottomNavTab.MAP) }`.
- [ ] **Step 4: Run the tests** → PASS; remove the heading semantics and the hint, and see those fail.
- [ ] **Step 5: Render** light and dark (throwaway harness, not committed) and compare with the before set.
- [ ] **Step 6: Docs** — `places.md` *Browsing them*: headline, titled card, share bars, chevrons, cats as in
  Encounters, On the map; `app-shell.md` *Rhythm*: Places joins the screens that group rows in titled cards
  and open on a headline card.
- [ ] **Step 7: Commit** `feature: Places on the card rhythm`; `CI=true ./gradlew check`; push; PR;
  `/code-review`; acceptance gate.

## D2 — Import status as a card

### Task 4: Progress and summary cards

**Files:**
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/counter/ImportStatus.kt`, strings EN/RU
  (`counter_import_running`: Importing photos / Импорт фото)
- Test: create `app/src/test/kotlin/dev/catsradar/app/counter/CounterImportStatusTest.kt`
- Docs: `import.md` *What a run reports*, `counting-cats.md` where the Counter's lines are described

- [ ] **Step 1: Failing tests** (`CounterScreen` with `importProgress` / `importSummary`):
  - *a running import names itself and how far it got*: `ImportProgressState(7, 23)` → "Importing photos"
    and "7 of 23" exist, a progress bar exists (`ProgressBarRangeInfo` defined).
  - *a finished import says what it added and offers Undo*: `ImportSummaryState(9, 3, 1, true)` →
    the three plural lines exist; clicking Undo calls `onUndoImportClick` once, not the dismiss.
  - *an undone import offers OK, which dismisses*: `undoable = false` → clicking OK calls
    `onImportSummaryDismiss` once.
- [ ] **Step 2: Run to see the first fail** (no "Importing photos").
- [ ] **Step 3: Implement** a private `ImportCard(iconRes, modifier, trailing, content)`:
  `Surface(shape = large, color = surfaceContainerLow)` → `Row(padding(16.dp), spacedBy(16.dp),
  CenterVertically)`: a 40 dp `secondaryContainer` circle with the 20 dp icon in `onSecondaryContainer`,
  `Column(weight(1f), spacedBy(4.dp), content)`, then `trailing`. Progress: `ic_photo_library`,
  title `titleSmall`, "7 of 23" `bodySmall` `onSurfaceVariant`, `LinearProgressIndicator(fillMaxWidth(),
  strokeCap = StrokeCap.Round)`. Summary: `ic_check`, the added line `titleSmall`, skipped/failed
  `bodySmall` `onSurfaceVariant`, trailing `TextButton` Undo or OK.
- [ ] **Step 4: Run** → PASS; swap the two buttons' callbacks and see the tests fail; restore.
- [ ] **Step 5: Render, docs, commit** `feature: the Counter's import progress and summary are cards`;
  check; push; PR; review; gate.

## D3 — One layout for the coat sheets

### Task 5: Sheet header, the grid's Not-specified cell, both sheets on them

**Files:**
- Create: `ui/src/main/kotlin/dev/catsradar/ui/components/SheetHeader.kt`
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/coat/CoatSwatch.kt` (`CoatGrid(selected, …,
  onUnspecifiedClick: (() -> Unit)? = null)`), `ui/src/main/kotlin/dev/catsradar/ui/counter/CoatPromptSheet.kt`,
  `ui/src/main/kotlin/dev/catsradar/ui/map/MapCoatSheet.kt`, strings EN/RU:
  `counter_coat_prompt_hint` (Tap a coat to note it / Нажмите на окрас, чтобы отметить его),
  `map_coats_hint` (Only cats of the marked coats stay on the map / На карте останутся только котики отмеченных окрасов)
- Test: create `app/src/test/kotlin/dev/catsradar/app/coat/CoatGridTest.kt`
- Docs: `coat.md` *Asked after a photo*, `map.md` *Heat and coats*

- [ ] **Step 1: Failing tests** on the public `CoatGrid`:
  - *the grid offers Not specified only when asked*: without `onUnspecifiedClick` no
    `coat_not_specified` node; with it, one.
  - *Not specified is marked like a coat and reports its own tap*: `selected = setOf(null)` → the cell
    `assertIsSelected()`; a click calls `onUnspecifiedClick` once and `onCoatClick` never.
- [ ] **Step 2: Run to see them fail.**
- [ ] **Step 3: Implement**:
  - `SheetHeader(title: String, supporting: String, modifier, leading: (@Composable () -> Unit)? = null)`:
    `Row(spacedBy(16.dp), CenterVertically)` → leading, `Column(spacedBy(4.dp))` with the title in
    `titleLarge` (a heading) and the supporting line in `bodyMedium` `onSurfaceVariant`.
  - `CoatGrid`: `CoatColumn` becomes `CoatCell(label, selected, modifier, onClick, face)`; the coats pass
    `CatFace`, and the Not-specified cell passes the paw (`ic_nav_pets`, 34 dp, `onSurfaceVariant`), drawn
    after the coats when `onUnspecifiedClick != null`, selected when `null in selected`.
  - `CoatPromptContent`: `SheetHeader(title, hint, leading = thumbnail 64 dp medium)`, `CoatGrid`, `Row(End)`
    with the Skip `TextButton`; `padding(horizontal = 24.dp)`, `spacedBy(16.dp)`.
  - `MapCoatContent`: `SheetHeader(map_coats_title, map_coats_hint)`, `CoatGrid(shown, onCoatClick =
    onCoatToggle, onUnspecifiedClick = { onCoatToggle(null) })`, `Row(End)` with the All-coats `TextButton`;
    the `FilterChip` goes.
- [ ] **Step 4: Run** → PASS; draw the cell without `onUnspecifiedClick` and see the first test fail; restore.
- [ ] **Step 5: Render, docs, commit** `feature: the coat sheets share one layout, and Not specified is a cell of the filter`;
  check; push; PR; review; gate.

## After all three merge

A `tech/design-pass-shipped` PR: map statuses to `merged`, `app-shell.md` *Not handled yet* loses the three
surfaces, this plan moves to `docs/superpowers/plans/archive/`.
