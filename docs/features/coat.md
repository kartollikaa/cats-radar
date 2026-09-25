# Coat

Eleven coats, from the owner's own list. A cat's coat is optional — a cat you only half saw is still
a cat — and can be set or changed at any time.

## Logging a cat by its coat

The Counter shows the coats as a **grid of four across**. Tapping one **logs a cat of that coat
immediately**: one tap, not tap-then-choose. The big button above it logs a cat whose coat nobody
noted. Both paths are the same tally — same undo, same location attach, same burst.

After a tap the grid rings the coat just used — a line in the theme's primary colour around that
coat's whole cell, face and name together — so a run of the same cat down the same street reads
back at a glance. An Undo moves the ring back to the coat of the newest cat still undoable, and it
clears when the undo window closes or the last of those cats is undone. The cells of one grid row
share the tallest one's height, so rings side by side — several coats chosen on the map — match.

This replaced an earlier design where a coat strip appeared *after* a tap. A tap on the grid has
already chosen the coat, so a strip asking again was one control too many, and changing a coat
later has its home on the detail screen.

## Asked after a photo

A photo cannot carry its coat the way a tap on the grid does: the Photo button only opens the
camera, and nothing in that press says what the cat looked like. So once the photo is saved, the
Counter asks in a bottom sheet: the photo's thumbnail beside **What coat was it?** and a line
saying that a tap notes the coat, then the eleven faces, and **Not now** at the end. The moment
after the shutter is when the coat is known best, with the cat still in front of the lens.

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

- **A newer photo takes the sheet over** — it asks about the newer cat, and the earlier one keeps
  no coat (*a newer photo takes over the prompt*).
- **The sheet closes before the coat is written**, so it never waits on storage; a write that fails
  still closes it and leaves the cat without a coat (*the prompt closes before the coat is written*;
  *a failed coat write still closes the prompt*).
- **The count changing underneath leaves it open** — a cat logged from the widget meanwhile keeps
  the sheet on the same photo (*the prompt stays open while the counter updates*).
- **A photo whose thumbnail could not be made** still gets the sheet, with no picture in it
  (`CounterStateMapperTest`, *the coat prompt has no picture when the thumbnail could not be made*).
- **A photo given to a logged cat on its detail screen asks nothing** — that screen shows the coat
  picker already.

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
the theme's `outline` colour. Dark-furred cats have amber eyes, and each coat's nose is pink or dark,
whichever shows against what it sits on. `CoatLookTest` holds all three to the contrast a meaningful
shape needs: the line against the surface in both themes, the eyes against the fur, and the nose
against the muzzle or the fur. The rim test fails if the line goes back to `outlineVariant`, which is
not enough.

The shapes are one set of paths, scaled and centred in whatever space a face is given, so the grid,
the picker and anything later draw the same cat. The launcher icon is the ginger-and-white face drawn
with those paths (see [app-shell.md](./app-shell.md)).

The labels say **calico**. The stored values are still `TRICOLOR_*`: the database and backup
archives hold those names, and renaming what nobody sees would need a migration for nothing.

## Changing it later

The detail screen shows the coat and lets it be changed, or cleared by tapping the current one
again. Nothing else needs a "clear" control.

The coats there sit in one row wider than a phone, and the row opens scrolled so the cat's own coat
is on screen, whole: second from the start with the coat before it showing, or, for the last few
coats, wherever the row's end leaves it. A cat with no coat opens the row at the first coat
(`EncounterDetailCoatPickerTest`). Only the opening position is chosen: after that the row stays
wherever it is scrolled, including when the coat is changed.

The chosen coat is ringed the same way as on the Counter. Every cell in that row takes the tallest
name's height, so the ring is the same size whichever coat it is on and the row never changes
height while it scrolls. That is why the row is not lazy: a lazy row measures only the cells on
screen, and a three-line name scrolling in would grow the card and push Delete down.

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
- **A failed write leaves the shown coat as it was**, because the screen re-reads it from the flow.

## Where the code lives

- `domain/…/model/CatCoat.kt`, `domain/…/usecase/SetCoat.kt`, `LogTally` (takes a coat)
- `domain/…/stats/StatsCalculator.kt` — the by-coat counts
- `presentation/…/coat/CoatOption.kt` — the presentation token, because `:ui` cannot see `:domain`
- `ui/…/coat/CoatSwatch.kt` — `CoatGrid` (log, and ask after a photo) and `CoatPicker` (amend)
- `ui/…/counter/CoatPromptSheet.kt` — the sheet after a photo; `CounterStore` opens and closes it
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
