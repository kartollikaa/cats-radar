# Design pass: the screens the first pass never reached — design

- **Date:** 2026-09-25
- **Status:** decided autonomously at the owner's request ("go in autonomous mode"), 2026-09-25
- **Decomposition:** [docs/tbd/decompositions/2026-09-25-design-pass-remaining-screens.md](../../tbd/decompositions/2026-09-25-design-pass-remaining-screens.md)
- **Builds on:** direction C, decided 2026-09-23 ([v1 map](../../tbd/decompositions/2026-09-21-cats-radar-v1.md),
  slices 23a–23d) and its vocabulary as [app-shell.md § Look](../../features/app-shell.md#look) describes it

## What was asked

A design pass over the existing screens the earlier passes did not touch: find what was done, what
was skipped, and bring the rest onto the same look.

## What the earlier passes covered

Slices 23a–23d (PRs #39–#43, 2026-09-23) set the look and applied it:

| Slice | Surfaces |
|---|---|
| 23a | palette, shapes, type, bottom-bar icons |
| 23b | coats drawn as cat faces |
| 23c | the Counter: count container, spring and roll, walk chip, Photo button |
| 23d | Encounters list, encounter detail, Statistics, Settings, their empty states — the card rhythm (`SectionCard`, a headline number in a primary-container card, the icon-and-title empty state) |

Everything built after 23d was built on that vocabulary: the Map tab and its empty and unavailable
states, the spot sheet (it reuses the Encounters rows), the grid and batch selection, the walk
button, the detail's photo card. PR #146, open at the time of writing, gives the photo viewer and
the detail a centred top bar.

Three surfaces are left on their original layouts. Renders of the current build, light and dark,
at a 411 dp wide phone:

1. **Places** (Statistics → Places). It predates the pass, and 23d did not include it. Every level
   is bare text rows with a number at the end: no card, no chevron, nothing that says which place
   the screen is showing. The cats at the bottom use the pre-grid row model, so they have no coat
   face or thumbnail, and they look different from the same cats in Encounters and on the map.
2. **The Counter's import progress and summary.** They predate the pass, and 23c restyled the
   Counter around them. They are loose text above the count: a line and a bare progress bar, or
   `labelSmall` lines next to an outlined chip.
3. **The coat sheets**: the coat question after a photo, and the map's coat filter. Both were
   built after the pass as a title, the coat grid and a text button. The filter's "Not specified"
   is a stray chip under the grid rather than a cell of it.

Two fixes to the record come with this pass. `app-shell.md`'s *Not handled yet* still says the
rhythm of the list, detail and statistics is to come, but 23d shipped it. The roadmap research
(PR #117) repeated that line; the actual gap is the list above.

## Out of scope

- The photo viewer and the detail's top bar belong to PR #146.
- The widget has its own colour pass, and its tiles already use the scheme's containers.
- The map's overlay controls are Material filter chips on the theme's containers.
- Turning `MapCoatSheet` into a Navigation 3 destination is behaviour, not look. It stays a
  follow-up on its own.
- A top bar with a back arrow on Places, like #146's. #146 landed during review, so it is slice D4.

## 1. Places

Each level becomes a small page of its own, in the Statistics rhythm.

**Headline.** The top of every level is a primary-container card (`extraLarge` shape), the same
shape as the Statistics headline. It holds the level's name in `headlineSmall` and its cat count
("97 cats", a plural) under it in `titleMedium`. The top level is named **Places** and counts every
cat. Its rows include No location, so that is every cat logged. A pseudo-level uses its own name
(Not named yet, No city, No location), and an unnamed area uses its coordinates ("Around 41.40123,
2.20456"). The screen needs this: today nothing on it says whether you are in Spain or in Gràcia.

**Rows.** A level's rows sit in one `SectionCard` titled by what they are: **Countries**,
**Cities** or **Areas**. Each row has three parts:

- the name, then the count at the end, then a chevron, since every row opens the level below it;
- under the name, a thin bar showing the row's share of the level (its count over the level's
  total). The siblings' counts add up to the level, so their shares add up to one.
- a name in `onSurfaceVariant` for a pseudo-row (Not named yet, No city, No location). It is still
  a row with a bar, because it still holds cats.

The row reads to TalkBack as one item: name, count, and that it opens.

**Cats.** The bottom level draws its cats the way the map's spot sheet and the Encounters list do:
`EncounterRows` in the list layout, with coat faces, photo thumbnails and an outing header over
each run of cards. The header's **On the map** opens the outing on the Map tab, as it does in
Encounters. The headline card is the list's first item, so it scrolls away with the list.

**Empty.** An empty level uses the app's empty-state pattern: a 56 dp icon in the primary colour
above the words it has today, in `titleLarge`. The first-run case (**No places yet**) also gets a
hint line saying that cats with a location are grouped here by country and city. The icon is a
location pin, Material Symbols Rounded (Apache 2.0) like the bottom bar's.

**Loading** stays blank, so a level sliding in never flashes an empty state.

### What it needs underneath

- **`:domain`.** `RegionView.Places` and `RegionView.Cats` gain `self: RegionNode?`: the level's
  own node, meaning its label and count. It is null at the top. `ObserveRegion` finds it by
  listing the level above and picking the node with this key, so the headline count is exactly the
  count its row showed one level up. That doubles the tree work per change, so `ObserveRegion` does
  it on an injected compute dispatcher, as `ObserveWalkStats` does.
- **`:presentation`.**
  - `RegionsState.Loaded` gains a `header` (a title token and a count) and a `section` token
    (countries, cities or areas).
  - `RegionRowState` gains `share: Float`.
  - The cats become `ImmutableList<EncountersRow>`, from `EncountersStateMapper.map(…, grid =
    false)`, the call the spot sheet makes. `EncounterListItem` and `mapList` go away. The map's
    focus chip, their only other user, reads the outing's label through `outingLabel`.
  - The empty state's hint is a State token, so the mapper decides which empty level gets one.
  - The mapper takes the level's parent key instead of a `topLevel` flag, so it can name the
    section.
- **`:ui`.**
  - `EncounterRows` takes an optional leading item, which is how the headline scrolls with the cats.
  - Its long press is optional. The cats of a place, like those of a map spot, have nothing to
    select, so they offer no long press.
  - The empty state and the headline card become shared components (`EmptyState`, `HeadlineCard`),
    which Statistics, Encounters and the Map use as well.
- **`:app`.** The entry wires **On the map** the way the Encounters entry does.

## 2. The Counter's import status

Both the progress and the summary become a low-surface card (`surfaceContainerLow`, `large`
shape), the same surface Statistics and Settings rows sit on.

- Each card has a leading 40 dp tonal circle (`secondaryContainer`) holding an icon: the photo
  library while the import runs, a check once it has finished.
- **Progress:** the title **Importing photos** in `titleSmall`, "7 of 23" in `bodySmall` on
  `onSurfaceVariant`, then a rounded progress bar the width of the text column.
- **Summary:** "9 cats added" in `titleSmall`. The skipped and failed lines, when there are any, go
  in `bodySmall` on `onSurfaceVariant`. **Undo**, or **OK** once undone, is a text button at the
  end.

What the lines say, and when each appears, stays as it is; only the layout changes.

The location-permission hint shares that slot above the count. It takes the same card (the location
pin, its line, Grant and Dismiss), so the notices there read as one family. Each card's words are
one TalkBack item.

## 3. The coat sheets

Both sheets share one layout: a header, the coat grid, an action row.

- **Header.** The title in `titleLarge`, with a supporting line in `bodyMedium` on
  `onSurfaceVariant`.
  - After a photo: the photo's thumbnail leads the header, larger than today (64 dp, `medium`
    shape). The supporting line says that tapping a coat notes it.
  - The map filter: the supporting line says that only cats of the marked coats stay on the map.
- **Grid.** The map filter's **Not specified** becomes the twelfth cell of the coat grid. It uses
  the paw that stands for a cat with no coat in Encounters, and it is marked like any coat, so the
  eleven coats plus it fill three rows of four. The Counter's grid and the detail's picker do not
  get the cell.
- **Actions.** After a photo, **Skip** is a text button at the end of the action row. The filter's
  **All coats** is a text button at the end of the row, disabled while nothing is filtered, as
  today.

Which coat a tap notes or toggles does not change.

The map's spot list, the app's third sheet, opens on the same header without a supporting line,
since its title ("3 cats seen here") says what it is. The header and the action row are shared
components (`SheetHeader`, `SheetActions`), and `app-shell.md` states the rule.

## Testing

- Mappers: whole-`State` `assertEquals` for every level's header, section token and shares, with
  the pseudo-nodes and the cats level. The share test holds a level whose shares add up to one.
- Domain: `ObserveRegion` gives each kind of level the `self` its parent lists, and null at the top.
- Compose, Robolectric in `:app`:
  - Places draws the headline, the section title and a chevron row per child, and a cat row per cat;
    a row tap still reports its key.
  - The import cards show their words and actions.
  - The filter's Not specified cell toggles `null`.
- Each slice's feature doc changes in its own PR: `places.md`, `import.md` and `counting-cats.md`,
  `coat.md` and `map.md`; plus `app-shell.md § Look` for the rhythm.
- Before each PR: light and dark renders of every changed surface, compared with the ones above.
