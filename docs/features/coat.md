# Coat

Eleven coats, from the owner's own list. A cat's coat is optional — a cat you only half saw is still
a cat — and can be set or changed at any time.

## Logging a cat by its coat

The Counter shows the coats as a **grid of four across**. Tapping one **logs a cat of that coat
immediately**: one tap, not tap-then-choose. The big button above it logs a cat whose coat nobody
noted. Both paths are the same tally — same undo, same location attach, same burst.

After a tap the grid rings the coat just used — a line in the theme's primary colour around that
face — so a run of the same cat down the same street reads back at a glance. The ring clears when the undo window closes or the cat is undone.

This replaced an earlier design where a coat strip appeared *after* a tap. Two coat controls on one
screen — one to log, one to amend — is one too many, and the amend case already has a home on the
detail screen.

## Telling them apart

Every coat is drawn as a **cat's face in that coat's real markings**, with its name beneath it:

- a **solid** coat is one colour all over;
- an **"& white"** coat has a white muzzle and a blaze running up between the eyes;
- **Calico, mostly white** is a white face with a ginger patch over one ear and a black patch over the
  other; **Calico, little white** is ginger with a black patch and only the white muzzle.

Several coats are near-identical as colours: grey, grey and white, black, black and white. A face
that differed only in fill would be unusable, so the markings carry the difference, and the name
carries it again for anyone who cannot see the colours.

**Every face is visible on both themes.** A white cat on the light surface and a black one on the
dark surface have almost no contrast with what is behind them, so each face has a line around it in
the theme's `outline` colour, and dark-furred cats have amber eyes so their faces do not read as a
blank shape. `CoatLookTest` holds this: the line reaches 3:1 against the surface in both themes, and
every coat's eyes reach 3:1 against its own fur. The line the grid used before, `outlineVariant`,
reached less than 2:1 — which is why the Black cat used to vanish in dark mode — and the test fails
if it is put back.

The shapes are one set of paths on a 40-unit square, scaled to whatever size a face is drawn at, so
the grid, the picker and anything later draw the same cat.

The labels say **calico**. The stored values are still `TRICOLOR_*`: the database and backup
archives hold those names, and renaming what nobody sees would need a migration for nothing.

## Changing it later

The detail screen shows the coat and lets it be changed, or cleared by tapping the current one
again. Nothing else needs a "clear" control.

## In the statistics

The **By coat** block counts each coat with its share of the total. Coats nobody has seen are
absent rather than listed as zero, and the "not specified" row always comes **last**, however many
cats are in it — it is the absence of an answer, not an answer that happens to be popular.

## At the edges

- **Setting the coat that is already set does nothing** — no write, no new `updatedAt`.
- **A soft-deleted cat cannot be edited**: `observeById` hides it, so the write returns early rather
  than resurrecting a row.
- **A failed write leaves the shown coat as it was**, because the screen re-reads it from the flow.

## Where the code lives

- `domain/…/model/CatCoat.kt`, `domain/…/usecase/SetCoat.kt`, `LogTally` (takes a coat)
- `domain/…/stats/StatsCalculator.kt` — the by-coat counts
- `presentation/…/coat/CoatOption.kt` — the presentation token, because `:ui` cannot see `:domain`
- `ui/…/coat/CoatSwatch.kt` — `CoatGrid` (log) and `CoatPicker` (amend)
- `ui/…/coat/CoatLook.kt` — each coat's fur, patches and eyes, and the line around every face
- `ui/…/coat/CatFace.kt` — the face itself

## Not built yet

No coat filter anywhere, and no coat on the map — the map is its own epic after v1. The fur colours
are fixed values rather than theme tokens, on purpose: a ginger cat is ginger in both themes. The
By coat block in the statistics names each coat but does not draw its face yet.
