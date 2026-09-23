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
the picker and anything later draw the same cat.

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
face also leads each coated row in the Encounters list that has no photo, and each coat in the
statistics' By coat block; a cat without a coat keeps a blank space there, so the names still line
up.
