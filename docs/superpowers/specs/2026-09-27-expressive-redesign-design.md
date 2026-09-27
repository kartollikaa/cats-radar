# Expressive redesign: the Counter and a cat's detail — design

- **Date:** 2026-09-27
- **Status:** direction chosen by the owner in chat, 2026-09-27 ("I like expressive from version 5. stick
  to it"). The details below were decided in this document and wait for the owner's review.
- **Decomposition:** [docs/tbd/decompositions/2026-09-27-expressive-redesign.md](../../tbd/decompositions/2026-09-27-expressive-redesign.md)
- **Builds on:** [app-shell.md § Look](../../features/app-shell.md#look), the
  [design pass of 2026-09-25](./2026-09-25-design-pass-remaining-screens-design.md), and the
  [outing pager](./2026-09-25-outing-pager-design.md), whose pager, bar position and undo bar this
  redesign keeps.
- **Prototype:** the owner's interactive HTML prototype, version 5, direction *Expressive* (a private
  artifact, <https://claude.ai/artifact/NWKd3eGpqRagxdyGq6iU24>). Its numbers, fonts and photos are
  stand-ins. Where it and this document differ, this document wins.

## What was asked

An audit of the screens, mostly a cat's detail, to make them more expressive and make the app stand
out, with Airbnb and then Drinkit as references. A prototype first, then proposals. The owner widened
it to the Counter and asked for several directions. Six were prototyped; the owner kept Listing, Night
walk and Expressive, and chose Expressive as it stood in version 5 of the prototype.

## What is wrong today

Read against renders of the current build and the composables in `ui/…/counter` and `ui/…/detail`.

- **The Counter's count has no more weight than its buttons.** The headline card is the same container
  colour as the controls under it, and nothing says the block is the button.
- **The coat faces are the app's most characteristic art, set small** in a loose grid with three-line
  labels, so they read as a settings list.
- **The detail opens on actions, not on the cat.** On a photographed cat the *Add a photo* card sits
  between the photo and the date; on a cat without one, two full-width buttons are the first thing on
  screen.
- **The headline is the time.** "11:25 PM" is the largest text and the least telling fact. Nothing at
  the top says which cat this is.
- **Four identical cards flatten the detail.** *Add a photo*, *Where* and *Coat* share the tinted card
  and the small primary title; a card that opens the map looks like one that does not.
- **The coat picker is a permanent editing control.** Eleven faces in a strip take a third of the
  screen for a fact set once.
- **Coordinates outrank the place, and Delete outranks everything.** Five-decimal coordinates are in
  the primary colour; the city is a plain line above them. A full-width red outlined button ends every
  cat.

## Out of scope

- Encounters, the Map tab, Statistics, Settings, Places, the photo viewer, the widget, notifications
  and the launcher icon. They change only as far as the theme in section 1 reaches every screen.
- The palette and the colour policy. Dynamic colour on Android 12 and later, the teal palette below
  it and in every preview: unchanged.
- A bundled font. The prototype's faces were stand-ins for the platform's.
- The outing pager's behaviour: paging between an outing's cats, "2 / 5" in the bar, the undo bar
  after a delete. They are its slices P3a-2 to P5b.
- What any control does, apart from the changes listed under *Behaviour changes*.

## 1. The theme

`CatsRadarTheme` draws with `MaterialExpressiveTheme` and `MotionScheme.expressive()` in place of
`MaterialTheme`, with the same colour scheme, shapes and typography. Components take their Expressive
defaults: buttons that change shape under a press, spring motion. The build already pins material3
`1.5.0-alpha27` for `SplitButtonLayout`, and in that version `MaterialExpressiveTheme`, `MotionScheme`,
`MaterialShapes`, `ButtonGroup`, the carousels, the wavy progress indicators and the `*Emphasized`
type styles are public behind the Expressive opt-in (checked against the library's classes,
2026-09-27). `app-shell.md`'s *Why not `MaterialExpressiveTheme`* paragraph says otherwise and is
replaced.

- **Type.** The count, the stat values, the cat's title and the place line use the `*Emphasized`
  variants of the styles they use today. Everything else keeps its style. The font is the
  platform's.
- **Shapes.** The theme's scale is unchanged. The new surfaces use `large` (28 dp) for cards and
  photos and `medium` (20 dp) for tiles and the map.
- **Screen transitions** keep their own specs; `NavTransitionTimingTest` does not change.

The theme reaches every screen, so the slice that changes it renders every tab, light and dark,
before and after. The alpha pin is meant to go once the BOM's material3 has `SplitButtonLayout`; if
that version hides any of these APIs again, this section is revisited before the pin moves.

## 2. The Counter

The order on screen is unchanged apart from the tiles: the notices, the count, the milestone line,
the walk row, the outing line, the stat tiles, the coat grid, Photo. `FillOrScroll` still gives the
count whatever room is left and scrolls the screen once the count reaches its floor.

**The count.** The number sits in a twelve-sided cookie (`MaterialShapes.Cookie12Sided`) in
`primaryContainer`, the largest that fits the block's room, centred. The number is `onPrimaryContainer`
in `displayLargeEmphasized` and still shrinks to fit rather than wrap. Under it, "cats" (a plural) in
`titleMedium`. The whole block stays the button. It still squashes under a press and springs back; the
cookie also turns a few degrees as it squashes and turns back with the motion scheme's spring. The
"+N" badge keeps its corner of the block, in `primary` and `onPrimary`. The roll, the badge's counting
and the TalkBack label do not change.

**The milestone arc.** A ring inside the cookie fills from the rung already reached to the next one
on `Tuning.MILESTONES`: at 62 cats, 50 is reached and 100 is next, so the arc stands at 24 %. The
track is `onPrimaryContainer` at a low alpha, the arc `primary`. The arc moves with the roll. Before
the total is read and past the last rung there is no arc.

**The milestone line.** Under the block, the Statistics line: "38 more to reach 100". It keeps its
line while empty, before the total is read and past the last rung, so nothing under it moves.

**The walk row.** The walk button is drawn as an extended floating action button in
`tertiaryContainer`: 56 dp tall with the FAB's corners. The walking cat, the fill that a held press
drives, the timed hint and the one height stay as they are. Undo becomes a filled tonal button in its
own place at the end of the row, and the rule that it never moves the walk button stands.

**The stat tiles.** Three tiles in a row under the outing line: **Today**, **Last 7 days** and **With
a photo**, the Statistics definitions, from the same `Stats` the count reads. Each tile is the number
in `titleLargeEmphasized` over its label in `bodySmall`, on `surfaceContainerLow` with `medium`
corners. They are not buttons. Each reads to TalkBack as one item ("5, Today"). Until the stats are
read, the tiles show their labels without numbers, so the layout never shifts.

**The coat grid.** Four across, as today. Each face sits in a 62 dp Material shape on
`surfaceContainerHighest`. The shapes go by column: `Circle`, `Square`, `Clover4Leaf`, `Arch`. The
ringed coat's shape fills with `primaryContainer` and takes a 2 dp `primary` outline; its name stays
under it. When the ring moves, clears and follows Undo does not change. The rule that a row's cells
share the tallest one's height so their rings match goes: the ring is now on the shape, and every
shape is the same size. The grid is shared, so the coat question after a photo and the map's coat
filter get the same tiles, and the filter's *Not specified* cell takes the next shape in turn.

**Photo.** The split button it already is, filled `primary`: *Photo* with the camera, and the
gallery icon for an import at the trailing end. `SplitButtonDefaults` gives the two halves their
inner corners.

**Unchanged:** the notice cards above the count and the coat question after a photo.

**Underneath.** `Milestone` in `:domain` gains `reached: Int`, the rung below the total, or 0. The
Counter's state gains the milestone (reached, next, remaining) and the three tile counts; the mapper
computes the arc's fraction, and the composable only draws it. The milestone line renders the way
Statistics renders it.

## 3. A cat's detail

This is one cat's page. When the outing pager lands, it pages these.

**The bar.** The back arrow as today (`BackBar`), and **More** at the other end in the same tonal
circle. More opens a menu: **Show on the map**, disabled when the map does not draw the cat, and
**Remove this cat**. The outing pager's "2 / 5" takes the bar's centre when P3b lands.

**Photos.** A multi-browse carousel (`HorizontalMultiBrowseCarousel`) of the cat's photos, oldest
first. Its large items are 300 dp wide at 4:5, with `large` corners, 8 dp apart, inside 20 dp side
padding. After the photos come two narrower items, **Take a photo** and **From gallery**, an icon
over a label on `surfaceContainerLow`. They open the camera and the picker as the buttons do today and
disable while an attempt runs. With more than one photo, a small outlined label under the carousel
says which is in front ("2 / 3"); TalkBack reads it as "Photo 2 of 3". A tap on a photo opens the
viewer on that photo. When the cat gains a photo, the carousel moves to it. While photos attach, a
wavy linear progress indicator runs under the carousel, determinate when several are counted.

**No photo.** A 4:5 block in `primaryContainer` with `large` corners inside the same side padding:
the cat's face at 170 dp, or the paw when no coat is noted, "No photo yet", and a connected
`ButtonGroup` of **Take a photo** and **Gallery**. During an attempt the group disables and the
progress indicator runs under it.

**The title.** Named by the coat: "Ginger & white cat", or "A cat" with no coat noted, in
`headlineMediumEmphasized`. Each coat has its own title resource, so the Russian can use the generic
«кошка» the way Russian names an unknown cat.

**The facts row.** Under the title, outlined labels with `extraSmall` corners, 32 dp tall: the cat's
number (section 4), the day, the time, and the place with its flag, or "No location yet" with a pin.
They are not buttons, wrap onto a second line when they must, and read to TalkBack as one item.

**The coat card.** On `surfaceContainerLow` with `large` corners: the face in a 72 dp `Clover4Leaf`,
or the paw with no coat; the coat's name in `titleMediumEmphasized` with "Coat" under it; a tonal
**Change** pill at the end, **Add** with no coat. The whole card is one button, which opens the coat
sheet.

**The coat sheet** (a behaviour change). A bottom-sheet destination above the detail, like every
other sheet: `SheetHeader` with "What coat was it?" and the line "Tap the current coat again to clear
it", then the coat grid with the cat's coat ringed. A tap on a coat sets it and closes the sheet; a
tap on the ringed coat clears it and closes the sheet; dismissing changes nothing. It replaces the
inline picker. Setting the coat while a photo attaches still keeps both.

**The Where card.** On `surfaceContainerLow` with `large` corners, headed **Where you met**. The spot
map at 16:10 with `medium` corners; the place with its flag, city and country in
`titleMediumEmphasized`; "Current location · ±12 m" in `bodyMedium` on `onSurfaceVariant`; the
coordinates under it in `bodySmall` on `onSurfaceVariant`, no longer in the primary colour; and a filled
**Show on the map** pill. The card stays one tap target that opens the Map tab on the cat, as today,
and the pill is its visible cue rather than a second control. A cat with no location shows an inner
block on `surfaceContainerHighest` with the pin, "No location yet" and a filled **Set on map** button,
the one control in the card. A cat whose coordinates lie off the globe shows no map and no pill and
opens nothing, as today.

**The accuracy circle.** The spot map draws the fix's accuracy to scale around the cat's dot: a
`primary` disc at a low alpha with a `primary` outline. It is drawn only when the accuracy is known
and the circle would be wider than the dot.

**Remove.** A tonal error button, **Remove this cat** in `errorContainer` and `onErrorContainer`,
centred at the end of the page, and the same entry in the More menu. Both do what Delete does today,
and what the outing pager's P4 makes of it.

**The removed and missing states** do not change.

## 4. The cat's number

The facts row opens with the cat's number: "#62" in English, "№ 62" in Russian, read as "Cat number
62". It is the cat's place among the live cats, oldest first by the time it was logged, ties broken by
id. A delete renumbers the cats logged after it, and an imported photo from before a cat renumbers
that cat. `ObserveEncounterNumber(id)` in `:domain` emits it, null while the cat is not live, from a
count the DAO answers. The number is its own slice; until it lands, the facts row starts with the day.

## Behaviour changes

1. **The detail's coat is set in a sheet**, not in the inline strip. `coat.md` § *Changing it later* is
   rewritten, and its opening-position rule and `EncounterDetailCoatPickerTest` go.
2. **Delete moves** into the More menu and to the end of the page as **Remove this cat**. What it does
   is unchanged. `encounter-detail.md` changes.
3. **The coat ring is drawn on the shape**, not around the whole cell. `coat.md` changes.
4. **New information**: the milestone arc and line and the stat tiles on the Counter, the cat's
   number and the accuracy circle on the detail. `counting-cats.md` and `encounter-detail.md` change.

## Decided here, open to the owner's review

- The colour policy stays: Expressive works with the wallpaper's colours as well as with the teal.
- No bundled font.
- The stat tiles are not buttons. Statistics is one tab away.
- The cat's number follows the live log, so a delete or an older import renumbers.
- The Russian titles name the cat with the generic «кошка».

## The outing pager

The outing pager's slices from P3a-2 on are planned, and no branch carries them. The detail slices of
this redesign change what a page draws; P3a-2 and P3b change the Store and wrap the pages in a pager.
They touch `EncounterDetailScreen.kt` together, so they land one after the other, never in parallel
branches. This redesign's detail slices go first. When P3b lands, the carousel sits inside the
pager the way the photo pager does today: it takes a horizontal drag until its own end, then the pager
takes it. That is checked on a device, since a JVM test cannot tell a nested fling from a flat one.

## Testing

- **Mappers**, whole-state `assertEquals`: the milestone at a rung, between rungs, below the first and
  past the last; the tiles before and after the stats are read; each coat's title token; the facts row
  with and without a place.
- **Domain:** `Milestone.reached` on every rung and between them; `ObserveEncounterNumber` counts only
  live cats, oldest first, breaks ties by id, and renumbers on a delete.
- **Compose, Robolectric in `:app`:**
  - A tap anywhere on the count block logs a cat, the badge counts the run, and no arc is drawn past
    the last rung.
  - The tiles show the three counts.
  - The ringed coat's shape is the one outlined.
  - A tap on the second photo opens the viewer on it; the add items open the camera and the picker
    and disable while attaching.
  - The coat card opens the sheet; a coat sets and closes; the ringed coat clears and closes.
  - The menu's entries and the Remove button reach the same intents as today's.
- **Kept green:** the Counter's *controls do not jump* and floor tests, `NavTransitionTimingTest`,
  `BottomSheetUsageTest`, `BottomSheetNavigationTest`, `CatsRadarColorsTest` (no palette change).
- **Renders:** before and after, light and dark, of every changed surface, from the Robolectric render
  harness; the theme slice renders every tab. The accuracy circle and the carousel's gestures are
  checked on a device, because MapLibre and flings do not run on the JVM.
- Each slice updates its feature documents in its own PR: `app-shell.md`, `counting-cats.md`,
  `coat.md`, `statistics.md` where it describes the milestone, `encounter-detail.md`, `photos.md` where
  it describes the detail's pager, and `map.md` if the spot map's drawing is described there.
