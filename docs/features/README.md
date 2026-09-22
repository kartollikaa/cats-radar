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
- `app-shell.md` — theme, navigation, the MVI `Store` contract, and dependency injection.
- `quality-gates.md` — what `./gradlew check` enforces: detekt, Android Lint, and the Konsist
  architecture tests.
