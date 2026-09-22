# Feature docs

One document per user-visible feature or subsystem of the Cats Radar Android app, describing what
it does today, the edge cases the code deliberately handles, where the code lives, and what is
deliberately not built yet. They exist to keep track of each feature as it grows and to stay
readable without the design spec open.

Update a feature's document in the same pull request as any change to that feature's behaviour —
a document nobody updates is worse than none.

- `counting-cats.md` — the tally: what a tap does, the undo window, and why the counter reads
  from the database.
- `location.md` — how an encounter gets its coordinates: the precedence chain, the backfill rule,
  and what happens with no permission.
- `data-model.md` — the `Encounter` and `PlaceCell` schema: field meanings, soft delete, and how a
  corrupt row degrades.
- `outings.md` — the derived-session concept behind an "outing," computed on demand and never
  stored.
- `browsing-cats.md` — the Encounters list grouped by outing, and the bottom nav's root-stack
  back rule.
- `encounter-detail.md` — one cat's screen: what it shows, soft delete with a bounded undo, and
  what a missing or already-deleted id renders as.
- `app-shell.md` — theme, navigation, the MVI `Store` contract, and dependency injection.
- `strings.md` — where user-facing text lives, and how a data-driven label gets from a mapper to
  a translatable resource.
- `photos.md` — reading a photo's metadata, the app's own copies, hashing, the gallery, and why
  the pixel tests need Robolectric's native graphics.
- `import.md` — turning gallery photos into encounters: where their date and their location
  come from, and why a historical photo never gets today's.
- `statistics.md` — what every number means: day windows, streaks, milestones, and why the
  overall rate pools cats and time instead of averaging outings.
- `places.md` — how coordinates become a country, city and area: place cells, the geocoding
  worker, and what happens on a device with no geocoder.
- `coat.md` — the eleven coats: logging a cat by tapping one, why the swatches carry a name and
  a shape, and the by-coat statistics.
- `backup.md` — the archive: what it carries, and how importing one merges with what is
  already here instead of replacing it.
- `quality-gates.md` — what `./gradlew check` enforces: detekt, Android Lint, and the Konsist
  architecture tests.
