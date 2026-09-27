# Expressive redesign: the Counter and a cat's detail — design

- **Date:** 2026-09-27
- **Status:** direction chosen by the owner in chat, 2026-09-27 ("I like expressive from version 5. stick
  to it"). The details below were decided autonomously at the owner's request ("work in autonomous
  mode"), 2026-09-27.
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

- The Map tab, Settings, Places, the photo viewer, the widget, notifications and the launcher icon.
  They change only as far as the theme in section 1 reaches every screen. Statistics (section 5) and
  Encounters (section 6) joined the redesign later.
- The palette and the colour policy. Dynamic colour on Android 12 and later, the teal palette below
  it and in every preview: unchanged.
- A bundled font. The prototype's faces were stand-ins for the platform's.
- The outing pager's behaviour: paging between an outing's cats, "2 / 5" in the bar, the undo bar
  after a delete. They are its slices P3a-2 to P5b.
- What any control does, apart from the changes listed under *Behaviour changes*.

## 1. The theme

`CatsRadarTheme` draws with `MaterialExpressiveTheme` and `MotionScheme.expressive()` in place of
`MaterialTheme`, with the same colour scheme, shapes and typography. In the material3 the build pins,
the motion scheme is the one thing this changes: components that animate through it move on its
springs, and no frame at rest changes. No component reads the Expressive flag, so a button that
changes shape under a press needs the Expressive components' own shape arguments, and the slices
that restyle a control use them. The build already pins a material3 alpha for `SplitButtonLayout`,
and in it `MaterialExpressiveTheme`, `MotionScheme`, `MaterialShapes`, `ButtonGroup`, the carousels,
the wavy progress indicators and the `*Emphasized` type styles are public, some behind the Expressive
opt-in (checked against the library's sources). `app-shell.md`'s *Why not `MaterialExpressiveTheme`*
paragraph says otherwise and is replaced.

- **Type.** The count, the cat's title and the place line use the `*Emphasized` variants of the
  styles they use today. Everything else keeps its style. The font is the platform's. The theme
  defines the `*Emphasized` styles a weight above its own: Material's defaults are a step above
  Material's regular weights, which the theme already exceeds.
- **Shapes.** The theme's scale is unchanged. The new surfaces use `large` (28 dp) for cards and
  photos and `medium` (20 dp) for tiles and the map.
- **Screen transitions** keep their own specs; `NavTransitionTimingTest` does not change.

The theme reaches every screen, so the slice that changes it renders the screens it can draw, light
and dark, before and after, as a check that no frame at rest moves. The Map tab and the bottom bar
are not drawn by that harness; a change to motion alone cannot alter them at rest. The alpha pin is
meant to go once the BOM's material3 has `SplitButtonLayout`; if that version hides any of these
APIs again, this section is revisited before the pin moves.

## 2. The Counter

The order on screen: the location hint, the count, the coat grid, then Walk and Photo in one row. The
status line under the count is gone (see *The tags on the ring*), the walk row under it too (see
*The walk*), and from E15 the import's notice floats over the screen instead of taking a place in the column
(see *The import notice*). `FillOrScroll` still gives the count whatever room is left and scrolls the screen once
the count reaches its floor. Everything under the count therefore costs the count its size, and the
Counter adds no row. Version 5 of the prototype, with three stat tiles, a separate milestone line and
larger coat shapes, left the cookie less than half the room this layout leaves it, once the prototype
drew it with only the height the controls leave (owner feedback, 2026-09-27: "the actual cookie in
prototype is very small").

**The count.** The number sits in a twelve-sided cookie (`MaterialShapes.Cookie12Sided`) in
`primaryContainer`, the largest that fits the block's room, centred. The number is
`onPrimaryContainer` in `displayLargeEmphasized` and still shrinks to fit rather than wrap. Under
it, "cats" (a plural) in `titleMedium`. The whole block stays the button. It still squashes under a
press and springs back. The cookie turns a step further with each cat and stays there, an undo
turning it back a step (owner, 2026-09-27: it used to spring back after each press); the turn moves
on the motion scheme's default spring, and the first count read, or a jump of more than a handful of
cats at once (an import landing), sets it in place rather than spinning it round. The "+N" badge
keeps its corner of the block, in `primary` and
`onPrimary`. The roll, the badge's counting and the TalkBack label do not change.

**The milestone arc.** A ring inside the cookie fills from the rung already reached to the next one
on `Tuning.MILESTONES`: at 62 cats, 50 is reached and 100 is next, so the arc stands at 24 %. The
track is `onPrimaryContainer` at a low alpha, the arc `primary`. The arc moves with the roll. Before
the total is read, with no cats yet, and past the last rung there is no arc.

**The tags on the ring.** Nothing sits under the count: the owner found the Statistics line under the
cookie foreign and the room it kept empty (2026-09-27), and with it gone the cookie takes that room.
The next milestone is pinned where the ring closes: a small pill on `surface` at the ring's top, a flag
and the rung's number in `primary`, and a dot at the arc's head, `primary` with a `primaryContainer`
rim, travelling toward it. TalkBack reads the pill as the Statistics line, "38 more to reach 100".
During an outing a second pill at the ring's bottom carries the outing line, count · time · rate with
the line's own give-way rules and its one spoken item; the goal stays. The pills take no room in the
column, so an outing starting or ending moves nothing under the count. Both pills sit on the stroke's
centre line and squash with the cookie. In a small cookie the number keeps clear of them: its box
loses what the pills reach into, the number shrinks, and "cats" under it gives way first. Past the
last rung, and with no cats yet, there is no pill and no dot.

**Reaching a rung** (slice E11, not yet built). A tally that lands on a rung: the cookie bounces and
the ring fills and glows on the motion scheme's springs, and the bottom pill says "100 cats!" in
`primary` until the undo window closes, in the outing's place meanwhile; an Undo takes it back. Line,
Cookie, Chip and Moments were the other treatments in prototype version 13; the owner chose Ring.

**The walk** (slice E14, in place of E3's walk row). A walk logs nothing: it turns on a mode, the notification with its
*Cat!* button, so the control is a mode switch and sits apart from the things that log a cat. The
owner found the walk row "a mess and very awkward" (2026-09-27): two lines that repeated each other,
a floating button that floated nowhere, the screen's one warm surface on a calm action, a lopsided row.
Three takes were prototyped (versions 17 to 20); *On the ring* was set aside because a start on the
tally's edge would cause misses, and the owner chose **With Photo** with the **warm, breathing
cookie**.

- **The button** sits at the start of the bottom row, beside Photo: a Medium tonal button, 56 dp
  like the split button, `secondaryContainer` and `onSecondaryContainer`, the walking cat and
  *Walk*, as wide as its longer label in both states so a walk starting or ending moves nothing
  beside it. Photo takes the rest of the row. A tap starts a walk. While a walk is on it takes
  `tertiaryContainer` and `onTertiaryContainer`, the toggle's checked corners (squarer than the pill)
  and *Hold to end*, with the walking cat still at its start, walking, where the prototype drew a stop
  glyph: a moving cat says "on a walk" better than a square, and the words already say what the press
  does; the held press with its fill and haptic ticks moves here from the
  old button unchanged (`walking-mode.md` § *Stopping takes a hold*), and a press let go early raises
  a short message, *Hold to end the walk*, which a second early release replaces rather than queues
  behind; a press that drifts off the button or is taken by a scroll raises none. The button carries no
  time: the outing's tag on the ring has the outing's, and the notification's chronometer has the
  walk's. A larger font puts Photo on a line of its own, in both states alike, rather than clip
  either label.
- **The cookie wears the walk.** While a walk is on, the cookie's fill is `tertiaryContainer` and the
  number and "cats" `onTertiaryContainer`; the arc, its dot and the tags keep their colours, and the
  ring's faint track and the dot's rim, drawn from the cookie's own colours, follow it. The change
  animates on the theme's colour spec both ways. And the cookie breathes: the shape alone, not the
  ring, the number or the tags, swells two per cent and settles over about three seconds, again and
  again, for as long as the walk lasts; it stands still when no walk is on, and when the system's
  animator scale is zero. If it ever wears on the owner, the same breath on the outing tag's dot alone
  is the fallback.
- **Undo** floats in the cookie's bottom-end corner, below the "+N" badge, the filled tonal button it
  already is (owner, 2026-09-27: "undo should go bottom right corner of cookie", over the top-start
  corner first built): level with the cookie's bottom at the block's end, so beside a cookie narrower
  than its block, where the outing's tag keeps its width. It appears and goes without moving anything, and a tap on it takes a cat back
  and never logs one. It gives up the older rule that kept Undo outside the block: while it shows, a tap
  on that corner undoes rather than logs. While it shows, the outing's tag at the ring's bottom narrows
  by Undo's width on both sides, staying centred and clear of it; its give-way rules shorten it. The rule that the controls do not jump stands: nothing under the count moves when
  a walk starts or ends or Undo comes and goes.

**The coat grid.** Four across, as today. Each face sits in a 52 dp Material shape on
`surfaceContainerHighest`, and each coat has a shape of its own (owner, 2026-09-27, over one shape
per column): Ginger `Circle`, Ginger & white `Square`, White `Clover4Leaf`, Calico mostly white
`Arch`, Calico little white `Cookie4Sided`, Brown `Slanted`, Brown & white `Gem`, Grey `Fan`, Grey &
white `Pentagon`, Black `PuffyDiamond`, Black & white `Bun`, and no coat `Ghostish`: soft, rounded
forms (owner: the spiky ones were "very angry"), each spanning at least nine tenths of its square
both ways and stretched onto the whole of it, so every tile's shape is the same size (owner: the
clam shell sat small). None is the count's twelve-sided cookie. The ringed coat's shape fills with
`primaryContainer` and takes a 2 dp `primary` outline; its name stays under it. When the ring moves,
clears and follows Undo does not change. The rule that a row's cells share the tallest one's height
so their rings match goes: the ring is now on the shape, and every shape is the same size. The grid
is shared, so the coat question after a photo and the map's coat filter get the same shapes, and the
filter's *Not specified* cell takes no coat's shape. The grid must not grow taller than today's.

**Photo.** The split button it already is, filled `primary`: *Photo* with the camera, and the
gallery icon for an import at the trailing end. `SplitButtonDefaults` gives the two halves their
inner corners. From E14 it shares its row with Walk and takes what Walk leaves.

**The import notice** (slice E15). The owner
asked to revisit "the block of a successful import" (2026-09-27): a settings row set above the count
that shrank the cookie, three lines of fine print at one size, an import of photos that showed none,
and the quietest Undo on the screen. Of *Photo card*, *By Photo* and *On the count* the owner chose
the **Photo card**, then asked that it not shrink the cookie, "so that island will be like a popup".

- **It floats.** A card on `surface` at the elevated card's level, `large` corners, 12 dp in from
  the sides, just under the status bar, over the top of the count rather than in the column, dropping
  in on the motion scheme; the cookie keeps its size. For the notice's ten seconds it covers the goal
  tag at the ring's top, which is the price of floating. The location hint stays a card in the
  column: what lasts sits in the column, what passes floats.
- **What it shows.** At its start, a fanned stack of the first three imported photos (40 dp,
  `medium` corners, each tilted a little), or a check when none has a photo. Running, the gallery icon
  stands in the stack's place, because progress reports counts and the added cats are known only when
  the run ends. Running: *Importing 7 of 12* in `titleSmall` over an Expressive wavy progress
  indicator. Finished: *9 cats added* in `titleMedium`, and under it one muted line with only the
  parts that apply, *2 already here · 1 couldn't be read*; TalkBack reads the words as one item.
- **Closing it.** Undo is the filled tonal button, and a × icon button after it closes the notice; a
  swipe to either side closes it too. Closing does what OK did: the run is recorded as dealt with and
  the Undo lapses; the cats stay. OK itself goes. The running notice has no controls. A tap on the
  card's body does nothing and is reserved: the owner has booked it for an imported-photos manager
  that edits the batch at once, so the body must never be what closes the notice.

The coat question after a photo takes the coat sheet's header (section 3).

**Underneath.** `Milestone` in `:domain` gains `reached: Int`, the rung below the total, or 0. The
Counter's state gains the milestone as the Statistics labels plus the arc's fraction, which the mapper
computes from the rung passed; the composable only draws it. With no cats yet there is no milestone:
a fresh Counter shows no ring and no tag rather than "1 more to reach 1". The goal tag's spoken
label is the Statistics line.

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
`headlineMediumEmphasized`. Each coat has its own title resource, so the Russian can name the cat
the way the app's Russian already does, with «котик».

**The facts row.** Under the title, outlined labels with `extraSmall` corners, 32 dp tall: the cat's
number (section 4), the day, the time, and the place with its flag; a cat with no location has no
place label, and the alert under the row says so.
They are not buttons, wrap onto a second line when they must, and read to TalkBack as one item.

**The coat card.** On `surfaceContainerLow` with `large` corners: the face in a 72 dp shape, the
coat's own, or the paw in no coat's shape; the coat's name in `titleMediumEmphasized` with "Coat"
under it; a tonal **Change** pill at the end, **Add** with no coat. The whole card is one button,
which opens the coat sheet.

**The coat sheet** (a behaviour change). A bottom-sheet destination above the detail, like every
other sheet. Its header is the cat's own face in a 64 dp shape, its coat's, on `primaryContainer`, or the
paw in no coat's shape on `surfaceContainerHighest` with no coat noted, beside "What coat was it?" in
`headlineSmallEmphasized` and one supporting line; then the coat grid of section 2 with the cat's coat
ringed and a twelfth *No coat* tile, the paw, ringed when no coat is noted (owner, 2026-09-27, over
"tap the current coat again", which nothing on screen explained). A tap on a coat sets it and closes
the sheet; a tap on *No coat* clears it and closes; dismissing changes nothing. It replaces the inline
picker. The coat question after a photo takes the same header with the photo just taken in a `medium`
square in place of the face, "Tap a coat to note it" and **Not now**; the map's filter keeps its own
*Not specified* cell. Prototype version 14 draws both. Setting the coat while a photo attaches still
keeps both.

**The no-location alert.** A cat with no location shows the Counter's notice card under the facts
row (owner, 2026-09-27, over the top of the page): the pin in a round `tertiaryContainer` icon, **No
location for this cat** and "It was logged without a fix, so it is not on the map or in Places.",
read by TalkBack as one item, and a tonal **Set on map** at its end, which opens the location picker
for that cat. It goes as soon as the cat has a location, whichever way it came. The notice card moves
to `ui/components` and takes its icon's tone as a parameter. Prototype version 15 draws it.

**The Where card.** On `surfaceContainerLow` with `large` corners, headed **Where you met**. The
spot map at 16:10 with `medium` corners; the place with its flag, city and country in
`titleMediumEmphasized`; "Current location · ±12 m" in `bodyMedium` on `onSurfaceVariant`; the
coordinates under it in `bodySmall` on `onSurfaceVariant`, no longer in the primary colour; and a
filled **Show on the map** pill. The card stays one tap target that opens the Map tab on the cat, as
today, and the pill is its visible cue rather than a second control. A cat with no location has no
Where card: the alert under the facts row takes its place. A cat whose coordinates lie off the globe
shows no map and no pill and opens nothing, as today.

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

## 5. Statistics

Added 2026-09-27 at the owner's request: the stats of the prototype's *Immersive* direction, the bar
chart, the range pill and the tiles, "are really convenient for the Stats page", so the Stats tab is
redesigned as well. Prototype version 12 draws it under *Stats*.

**The headline** keeps its primary-container card with the total in `displayLargeEmphasized`, "cats
seen" under it and the milestone line.

**The chart.** A card headed **Last 7 days** or **Last 30 days**, with a pill at the end of the
header switching between the two. Under it, one bar per day: the cats logged that day, by the
encounter's own local date like the existing windows, today's bar in `primary` and the others in
`surfaceContainerHighest`, the tallest day setting the scale. Under the bars, weekday names for seven
days and a date every seventh bar for thirty. A tap on a bar names its day and count in a line under
the chart ("6 cats · Sat 26 Sep"); today's bar is named until one is tapped. The line keeps its height
while empty, so the tiles under it do not move. A day with no cats draws a stub, so the row of bars
never has gaps.

**The tiles.** Two rows of three, on `surfaceContainerLow` with `medium` corners, a number in
`titleLargeEmphasized` over a `bodySmall` label: **Today** (in `primaryContainer`, the one tile
highlighted), **Last 7 days**, **Last 30 days**; then **With a photo**, **Streak** and **Longest
streak** with "days" set small beside the number. They are the numbers of today's *When* and
*Streaks* cards, and each reads to TalkBack as one item ("3, Today").

**By coat.** Each row keeps its face, its name and "38 · 26%", and gains a share bar under the name:
the row's count over the busiest coat's, in the coat's fur colour, on `surfaceContainerHighest`. The
*Not specified* row's bar is `outline`. It is the Places drill-down's share bar, drawn in the coat's
own colour.

**Outings.** The same figures in the same order, as a two-column grid of small stats: the value in
`titleMediumEmphasized` with its unit set small beside it, the label under it. The best outing's rate
sits in the best outing's label ("Best outing · 1.3 / min"). The walked rows appear and disappear as
they do today.

**Places** keeps its card with the chevron. **The empty state** at zero cats is unchanged.

**Underneath.** `Stats` gains `byDay`, the last thirty days' counts oldest first, today last, computed
in `StatsCalculator` from the same dates as the windows. `StatisticsState` gains the day series with
its labels, built by the mapper; the composable draws it. Nothing else in the domain changes.

## 6. Encounters

Added 2026-09-27 at the owner's request ("also prototype the redesign for encounters page"); the owner
approved prototype version 15's drawing. What the tab does is unchanged: outings newest first, the grid
or the list, the packing, On the map, a long press to select, delete with an undo.

**The headline.** "Encounters" in `headlineMediumEmphasized`, and under it "147 cats · 38 outings" in
`bodyMedium` on `onSurfaceVariant`.

**An outing.** One card on `surfaceContainerLow` with `extraLarge` corners holds the outing. Its header
names the day in `titleLargeEmphasized` ("Today", "Yesterday", "Sep 24"), then the start, the count and
the span on one line ("4:12 PM · 6 cats · 48 min"). An outing that overlapped a walk adds an **On a
walk** chip in `tertiaryContainer` with the walking cat. **On the map** becomes a tonal pill with the
map icon at the header's end, on the same rule as today.

**The grid.** The packing stays. A pair is two photo squares with `medium` corners and the time on a
dark chip over the photo's corner. A run's tiles take each coat's own shape of section 2, with the
face, the paw when no coat is noted, or the photo's thumbnail clipped to the shape, and the time
under each; a short run's cards sit on `surface` inside the outing's card.

**The list.** Each cat is a card on `surface` inside its outing's card, with the tile's shape, the
coat's name or "A cat", and the time and place; "No location yet" takes `onTertiaryContainer`.

**Selecting.** A chosen cat takes a `primary` ring and a check; the bar at the top turns
`primaryContainer` with the close button, the count and Delete. The undo bar is unchanged.

**Underneath.** The outing header's state gains the count and the span labels, built by the mapper from
the outing's cats, and whether a walk overlapped it, from the stored walks. The headline's totals come
from the same grouping. Nothing else in the domain changes.

## Behaviour changes

1. **The detail's coat is set in a sheet**, not in the inline strip. `coat.md` § *Changing it later* is
   rewritten, and its opening-position rule and `EncounterDetailCoatPickerTest` go.
2. **Delete moves** into the More menu and to the end of the page as **Remove this cat**. What it does
   is unchanged. `encounter-detail.md` changes.
3. **The coat ring is drawn on the shape**, not around the whole cell. `coat.md` changes.
4. **New information**: the milestone arc and the goal on the ring on the Counter, the
   cat's number and the accuracy circle on the detail, the per-day chart on Statistics, and each
   outing's count, span and walk on Encounters. `counting-cats.md`, `encounter-detail.md`,
   `statistics.md` and `browsing-cats.md` change.
5. **A cat with no location says so** in an alert under its facts, with Set on map; its Where card
   appears once it has a location. `encounter-detail.md` changes.
6. **The walk moves beside Photo** and shows no time of its own; the cookie turns warm and breathes
   while a walk is on; a press let go early raises a hint; Undo floats in the count block.
   `counting-cats.md` and `walking-mode.md` change.
7. **The import notice floats and is closable**: × and a swipe close it, OK goes, and its body is not
   tappable. `import.md` and `counting-cats.md` change.

## Decided here

- The colour policy stays: Expressive works with the wallpaper's colours as well as with the teal.
- No bundled font.
- No stat tiles on the Counter. Version 5 of the prototype had Today, Last 7 days and With a photo
  under the walk row; they cost the count more than half its room, and Statistics shows them one tab
  away.
- The cat's number follows the live log, so a delete or an older import renumbers.
- The Russian titles use the app's own «котик».
- Statistics joins the redesign (owner, 2026-09-27) with the Immersive direction's chart, range pill and
  tiles; the Counter itself keeps no tiles.
- A cat with no location gets the notice-card alert under its facts rather than at the top of the page
  (owner, 2026-09-27).
- Encounters joins the redesign (owner, 2026-09-27) as prototype version 15 draws it.
- The walk is a mode switch beside Photo, with the cookie warm and breathing while a walk is on
  (owner, 2026-09-27, after three takes; *On the ring* set aside for misses; a breath rather than a
  pulse, so it reads as live from the corner of the eye and never beats on a screen tapped all walk
  long).
- The import notice is the floating Photo card, closable by × or a swipe, its tap reserved for an
  imported-photos manager (owner, 2026-09-27).

## The outing pager

The outing pager's P3a-2 was in progress in another session when this was written, and P3b to P5b
are planned. The detail slices of this redesign change what a page draws; P3a-2 and P3b change the
Store and wrap the pages in a pager. Both touch `EncounterDetailScreen.kt` and `encounter-detail.md`,
so the two epics never have detail branches open at the same time: a detail slice, E5 to E9, starts
only when no outing pager branch is open, and is cut from the `main` that carries the last one. The
Counter slices, E1 to E4, do not touch the detail and go first. Once P3b lands, the carousel sits
inside the pager the way the photo pager does today: it takes a horizontal drag until its own end,
then the pager takes it. That is checked on a device, since a JVM test cannot tell a nested fling
from a flat one.

## Testing

- **Mappers**, whole-state `assertEquals`: the milestone at a rung, between rungs, below the first and
  past the last; each coat's title token; the facts row
  with and without a place.
- **Domain:** `Milestone.reached` on every rung and between them; `ObserveEncounterNumber` counts only
  live cats, oldest first, breaks ties by id, and renumbers on a delete; `Stats.byDay` holds thirty
  entries, today last, a cat logged abroad on its own date, and zero for a day with no cats.
- **Compose, Robolectric in `:app`:**
  - A tap anywhere on the count block logs a cat, the badge counts the run, and no arc is drawn past
    the last rung.
  - The goal tag sits on the ring's top and the outing's on its bottom; nothing sits between the count
    and the walk button; a cramped block keeps the number clear of both.
  - The ringed coat's shape is the one outlined.
  - A tap on the second photo opens the viewer on it; the add items open the camera and the picker
    and disable while attaching.
  - The coat card opens the sheet; a coat sets and closes; the ringed coat clears and closes.
  - Statistics: the pill switches the chart between seven and thirty bars; a tap on a bar names its day;
    the tiles show the six numbers; each coat row's bar is the width of its share.
  - The menu's entries and the Remove button reach the same intents as today's.
  - A cat with no location shows the alert and no Where card; Set on map opens the picker; the alert
    goes once the cat has a location.
  - Encounters: an outing's header carries its count, span and walk chip; the tiles take the coat
    shapes; a long press still selects and Delete still offers Undo.
  - The walk: Walk stands beside Photo at the split button's height; a tap starts a walk and only the
    held press ends one; while a walk is on the button and the cookie's fill are `tertiaryContainer`
    and the cookie's breath runs, and it does not run without a walk or at animator scale zero; Undo
    floats in the block's corner, moves nothing and logs no cat; at font scale 1.5 both labels stay
    whole; nothing sits between the count and the coats.
  - The import notice: the cookie is the same size with the notice and without it; × and a swipe
    close it and record the run as dealt with; Undo takes the batch back; the body has no click; only
    the lines that apply are shown.
- **Kept green:** the Counter's *controls do not jump* and floor tests, `NavTransitionTimingTest`,
  `BottomSheetUsageTest`, `BottomSheetNavigationTest`, `CatsRadarColorsTest` (no palette change).
- **Renders:** before and after, light and dark, of every changed surface, from the Robolectric render
  harness; the theme slice renders every tab. The accuracy circle and the carousel's gestures are
  checked on a device, because MapLibre and flings do not run on the JVM.
- Each slice updates its feature documents in its own PR: `app-shell.md`, `counting-cats.md`,
  `coat.md`, `statistics.md` where it describes the milestone, `encounter-detail.md`, `photos.md` where
  it describes the detail's pager, and `map.md` if the spot map's drawing is described there.
