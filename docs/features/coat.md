# Coat

Eleven coats, from the owner's own list. A cat's coat is optional — a cat you only half saw is still
a cat — and can be set or changed at any time.

## Logging a cat by its coat

The Counter shows the coats as a **grid of four across**. Tapping one **logs a cat of that coat
immediately**: one tap, not tap-then-choose. The big button above it logs a cat whose coat nobody
noted. Both paths are the same tally — same undo, same location attach, same burst.

After a tap the grid rings the coat just used, so a run of the same cat down the same street reads
back at a glance. The ring clears when the undo window closes or the cat is undone.

This replaced an earlier design where a coat strip appeared *after* a tap. Two coat controls on one
screen — one to log, one to amend — is one too many, and the amend case already has a home on the
detail screen.

## Telling them apart

Every swatch is a cat's head — an oval face with an ear rising from each top corner — filled in that
coat's colour. The head is one contour, the ears unioned into the face, so the outline stroke traces
only the silhouette instead of drawing where the ears cross it. That stroke is what keeps a white cat
visible on a light surface.

Several coats are near-identical as colours: grey, grey and white, black, black and white. A swatch
that differed only in fill would be unusable, which the design brief called out explicitly. So every
swatch carries **both** a name and a shape: the "& white" coats are drawn with the right half of the
face white, not as a lighter shade of the same colour.

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

## Not built yet

No coat filter anywhere, and no coat on the map — the map is its own epic after v1. The swatch
colours are fixed values rather than theme tokens, which the visual design pass will revisit.
