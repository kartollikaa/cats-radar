# Coat

Eleven coats, from the owner's own list. A cat's coat is optional — a cat you only half saw is still
a cat — and can be set or changed at any time.

## Logging a cat by its coat

The Counter shows the coats as a **grid of four across**. Tapping one **logs a cat of that coat
immediately**: one tap, not tap-then-choose. The big button above it logs a cat whose coat nobody
noted. Both paths are the same tally — same undo, same location attach, same burst.

Each face sits in a Material shape on the theme's highest container, and every coat has a shape of
its own — ginger a circle, black a puffy diamond, grey and white a pentagon, all of them soft,
rounded forms, each stretched onto the whole of its square so that every shape is the same size — so
a coat is known by its shape as well as its face; "no coat" has one too (`CoatShapesTest`). After a
tap the grid rings the coat just used — its shape fills with the primary container inside a line in
the primary colour, its name under it — so a run of the same cat down the same street reads back at
a glance (`CoatGridLookTest`). An Undo moves the ring back to the coat of the newest cat still
undoable, and it clears when the undo window closes or the last of those cats is undone. Every shape
is the same size, so rings side by side — several coats chosen on the map — match, and the grid is
no taller than it was when the faces sat in plain cells.

This replaced an earlier design where a coat strip appeared *after* a tap. A tap on the grid has
already chosen the coat, so a strip asking again was one control too many, and changing a coat
later has its home on the detail screen.

## Asked after a photo

A photo cannot carry its coat the way a tap on the grid does: the Photo button only opens the
camera, and nothing in that press says what the cat looked like. So once the photo is saved, the
Counter asks in a bottom sheet: the photo's thumbnail beside **What coat was it?** and a line
saying that a tap notes the coat; under them a connected pair of buttons, **One cat · Several**,
with *One cat* checked; then the eleven faces, and **Not now** at the end. The moment after the
shutter is when the coat is known best, with the cat still in front of the lens.

That is not the old strip coming back: a photo has no tap that chose its coat, and the sheet is the
only place the Counter asks about it.

A face sets that coat on the cat just photographed and closes the sheet
(`CounterStorePhotoPromptTest`, *picking a coat sets it on the photographed cat and closes the
prompt*). **Not now**, a swipe down, a tap outside it or back closes it with the coat unset
(*dismissing the prompt leaves the coat unset*); the detail screen can still set it. The cat is
saved before the sheet appears, so losing the sheet — the app killed in the background, say — loses
only the question.

The sheet follows only a photo taken with the Counter's Photo button, which the widget's Photo tile
and the walking notification's Photo button also press (see [photos.md](./photos.md#taking-one)).
An import asks nothing, and neither does an unreadable photo or a cancelled camera, since neither
logs a cat (*no prompt without a logged camera photo*).

### The sheet at the edges

- **It opens all the way** and has no half-open stop, like every sheet in the app (see
  [app-shell.md](./app-shell.md)).
- **A newer photo takes the sheet over** — it asks about the newer cat, and the earlier one keeps
  no coat (*a newer photo takes over the prompt*).
- **The sheet closes before the coat is written**, so it never waits on storage; a write that fails
  still closes it and leaves the cat without a coat (*the prompt closes before the coat is written*;
  *a failed coat write still closes the prompt*).
- **The count changing underneath leaves it open** — a cat logged from the widget meanwhile keeps
  the sheet on the same photo (*the prompt stays open while the counter updates*).
- **A photo whose thumbnail could not be made** still gets the sheet, with the paw in no coat's
  shape where the picture would be (`CounterStateMapperTest`, *the coat prompt has no picture when the
  thumbnail could not be made*; `CoatPromptCountingTest`, *without a thumbnail the question sits beside
  the paw*).
- **A photo given to a logged cat on its detail screen asks nothing** — that screen shows the coat
  card already.

### Several cats on the photo

A photo can show several cats, and the sheet counts them all at once. **Several**, the second of the pair under
the header, turns the same sheet into a count and takes the check; a face tapped under *One cat* still sets that
coat and closes the sheet, so one cat stays one tap. **One cat** goes back: it empties the tray and the sheet asks
for one coat again (`CounterStorePhotoPromptTest`, *one cat after several empties the tray, and a coat then sets
it and closes the prompt*). The checked button takes no tap. A screen reader hears the pair as two radio buttons,
one of them checked (`CoatPromptCountingTest`).

While counting:

- The title is the number of cats counted on the photo ("3 cats on this photo"), or asks how many there are
  before the first; the photo stays beside it, where it was. Under the pair a **tray** shows one cat per tap, in
  the order they were tapped, each its coat's face in its coat's shape with a small ×; a tapped tray cat is taken
  out. Before the first, the tray says that counted cats gather there.
- Each face in the grid adds a cat of that coat and shows how many of it the tray holds, ringed like a chosen
  coat. A **paw** after the eleven coats, **No coat**, adds a cat whose coat nobody saw. The map's filter names
  the same cell *Not specified*.
- The tray holds at most `Tuning.SHOT_MAX_CATS` cats, the photographed one included; past that the faces and the
  paw dim and take no tap, and the line under the title says the photo holds no more (`CounterStorePhotoPromptTest`,
  *past the most cats a photo can hold, faces stop adding*). A tray cat is taken out only while it is still the one
  tapped, so a second tap landing after the tray moved takes out no other cat.
- **Save N cats** appears with the first cat counted. It closes the sheet, then sets the first counted coat on the
  cat the camera saved and adds the others to its shot, each a cat of its own with its own copy of the photo (see
  [photos.md](./photos.md)). An added cat still waiting for a location goes to the background attach, as the
  photographed cat did (*every added cat still without a location is sent to the location attach*). Encounters
  shows the shot as one entry with a badge of its count (see [browsing-cats.md](./browsing-cats.md)).
- Leaving the count any other way — **Not now**, a swipe down, a tap outside, back — is **Not now**: the
  photographed cat stays with no coat and no other cat is added (*leaving the count without saving keeps the
  photographed cat alone and uncoated*).

The edges above hold while counting too: a newer photo takes the sheet over and the tray goes with the old photo
(*a newer photo takes the sheet over and drops the tray*); the sheet closes before anything is written (*saving
closes the prompt before anything is written*); the count changing underneath keeps the sheet and its tray (*the
tray stays while the counter updates*). The tray is the Store's state, so a rotation keeps it; a process death
loses it with the question. A save that fails shows one message, "Cats not saved", and adds no cat; the
photographed cat keeps the first coat when that write went through (*a failed coat write after saving shows one
message and adds no cat*; *a failed add after saving shows one message and keeps the first coat*).

A screen reader hears a tray cat as its coat, or *No coat*, with a Remove action, and a grid face as its coat
with how many the tray holds (`CoatPromptCountingTest`).

## Telling them apart

Every coat is drawn as a **cat's face in that coat's real markings**, with its name beneath it:

- a **solid** coat is one colour all over;
- an **"& white"** coat has a white muzzle and a blaze running up between the eyes;
- **Calico, mostly white** is a white face with a ginger patch over one ear and a black patch over the
  other; **Calico, little white** is ginger with a black patch and only the white muzzle;
- the **brown** coats are tabbies, with dark stripes on the forehead.

Some coats are close as colours. The muzzle separates each coat from its "& white" twin, and the
stripes separate brown from black, whose furs are only a shade apart. Ginger and grey differ by hue
alone; the name under every face carries that difference, and every other one, for anyone who cannot
see the colours.

**Every face is visible on both themes.** A white cat on the light surface and a black one on the
dark surface have almost no contrast with what is behind them, so each face has a line around it in
the theme's `outline` colour. Dark-furred cats have amber eyes, and each coat's nose is pink or
dark, whichever shows against what it sits on. `CoatLookTest` holds all three to the contrast a
meaningful shape needs: the line against the surface and against the shape a face sits in, in both
themes (a ringed face's line takes the primary colour, since the outline fades on the primary
container in the dark theme), the eyes against the fur, and the nose against the muzzle or the fur.
The rim test fails if the line goes back to `outlineVariant`, which is not enough.

The shapes are one set of paths, scaled and centred in whatever space a face is given, so the grid,
the card, the sheet and anything later draw the same cat. The launcher icon is the ginger-and-white face drawn
with those paths (see [app-shell.md](./app-shell.md)).

The labels say **calico**. The stored values are still `TRICOLOR_*`: the database and backup
archives hold those names, and renaming what nobody sees would need a migration for nothing.

## Changing it later

A cat's page shows its coat as a card: the face in its coat's shape, or the paw in no coat's, the coat's name
with "Coat" under it, and a tonal **Change** pill at the end; a cat with no coat reads "Coat not noted" and
**Add** (`DetailCoatCardTest`). The whole card is one button, read as one item; the pill is its visible cue.
Each page of the outing's pages has the card of its own cat (see
[encounter-detail.md](./encounter-detail.md#paging-through-the-outing)).

The card opens the coat sheet over the detail, a sheet destination like the map's spot list (`EncounterDetailEntryTest`,
*the coat card opens the coat sheet for the cat on screen*). Its head is the cat's face in its coat's shape on the
primary container, or the paw on the highest container with no coat noted, beside "What coat was it?" and "Pick
another, or “No coat”" or "Tap the coat that fits" (`DetailCoatSheetTest`). Under it is the coat grid with the
cat's coat ringed and a twelfth *No coat* cell, ringed when no coat is noted. A tap on a coat sets it and closes the
sheet; *No coat* clears it and closes; a tap on the ringed coat changes nothing and closes; a swipe down or back
closes it and changes nothing (`CoatSheetStoreTest`). Only the first answer counts: a second tap before the sheet is
gone writes nothing. A cat removed while its sheet is up closes the sheet, and bringing it back does not reopen it.
Nothing else needs a "clear" control.

## In the statistics

The **By coat** block counts each coat with its share of the total. Coats nobody has seen are
absent rather than listed as zero, and the "not specified" row always comes **last**, however many
cats are in it — it is the absence of an answer, not an answer that happens to be popular.

## At the edges

- **Setting the coat that is already set does nothing** — no write, no new `updatedAt`.
- **A soft-deleted cat cannot be edited**: `observeById` hides it, so the write returns early rather
  than resurrecting a row.
- **A coat set while a photo or a location is being attached keeps both** — only the coat and its
  `updatedAt` are written.
- **A failed write leaves the shown coat as it was**, because the screen re-reads it from the flow; the
  coat sheet closes all the same.

## Where the code lives

- `domain/…/model/CatCoat.kt`, `domain/…/usecase/SetCoat.kt`, `LogTally` (takes a coat)
- `domain/…/stats/StatsCalculator.kt` — the by-coat counts
- `presentation/…/coat/CoatOption.kt` — the presentation token, because `:ui` cannot see `:domain`
- `ui/…/coat/CoatSwatch.kt` — `CoatGrid` (log, ask after a photo and count its cats, filter the map, change a
  cat's coat) with its shaped tiles and `coatShapeFor`
- `ui/…/detail/CoatCard.kt` — the detail's coat card; `ui/…/detail/CoatSheet.kt` — the coat sheet's content;
  `presentation/…/coatsheet/` — its Store, which writes through `SetCoat`; `app/…/navigation/CoatSheet.kt` — its key
- `ui/…/counter/CoatPromptSheet.kt` — the sheet after a photo, its header and *One cat · Several*;
  `CoatPromptTray.kt` its tray; `CounterStore` opens and
  closes it, `presentation/…/counter/CoatCounting.kt` moves it between prompts and `CoatQuestion.kt` makes its writes
- `ui/…/coat/CoatShapes.kt` — each coat's shape, stretched onto its square
- `ui/…/coat/CoatLook.kt` — each coat's fur, patches and eyes, and the line around every face
- `ui/…/coat/CatFace.kt` — the face itself

## On the map

A cat's dot on the map is painted in its coat's colours: the fur over the top half, the markings
sliced below it. Its heat takes the same colours in the same shares, so a black-and-white cat is
half black heat and half white. The faces and the tabby stripes are too small to draw there; see
[map.md](./map.md).

## Not built yet

No coat filter outside the map. The fur colours
are fixed values rather than theme tokens, on purpose: a ginger cat is ginger in both themes. The
face also leads each coated row in the Encounters list that has no photo, and each coat in the
statistics' By coat block; a cat without a coat keeps a blank space there, so the names still line
up.
