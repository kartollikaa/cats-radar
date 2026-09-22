# User-facing text

Every word the app shows comes from an Android string resource, in English and Russian. `:ui` owns
`ui/src/main/res/values{,-ru}/strings.xml` — nearly all of it. `:app` owns only `app_name`, because
that is the launcher label and belongs to the application module; it is marked
`translatable="false"`, being a product name rather than a phrase.

No English sentence is written in Kotlin source. That is not a style preference: a sentence stored
in `:presentation` cannot be translated without editing the mapper that produced it and every test
that asserts it, so a literal there converts a future translation into a refactor.

## How a label reaches the screen

Some text is static and the composable names the resource directly. The rest depends on data, and
that split is where the rule earns its keep:

1. The mapper in `:presentation` decides **which** label applies and puts a token in State — a
   `:presentation` enum such as `LocationLabel`, not the domain enum. `:ui` does not depend on
   `:domain`, so a domain enum could not be resolved there even if the layering allowed it.
2. The composable resolves the token with `stringResource`.

The decision stays where a plain unit test can assert it; the words stay where `values-ru` can
translate them. `EncountersStateMapperTest` pins every `LocationSource` to its own token and
asserts no two of them collapse onto one — a `when` arm pointing at the wrong token changes what
the user reads and nothing else would notice.

## The words a formatter needs

`AndroidDateTimeFormatter` is the one place outside `:ui` that produces words rather than a token:
"today", "yesterday", and the hour and minute units in a span like "1 h 20 min". They cannot be
tokens, because the mapper splices them into a larger label (`"Yesterday, 18:42"`) before the
composable ever sees it.

So `:presentation` carries four strings of its own, in `presentation/src/androidMain/res` — the
module enables `androidResources` for exactly this — and the formatter takes a `Context` to read
them. Everything else it formats comes from the platform's own locale data: the calendar date and
the wall-clock time go through `java.time` with the device locale, which needs no resource.

## Counting things

A phrase with a number in it is a `<plurals>`, never a `<string>` with `%d` spliced in. English
needs two categories, Russian four (`one` / `few` / `many` / `other`) — "1 котик", "2 котика",
"5 котиков" — so a Russian-only category is not optional padding, it is the difference between
correct and wrong text for most counts.

That holds even when the number is not *inside* the phrase. The statistics headline draws the total
at display size with a caption under it, and the caption still has to agree: "21 котик встречен",
"22 котика встречено". Lint's `ImpliedQuantity` flags a numberless Russian `one`, which is right in
general and wrong here, so that one plural carries a `tools:ignore` and a note saying where the
number is.

State carries such a count as an `Int`, never a formatted label — `StatisticsState.total`,
`BestOutingState.count`, `CurrentOutingState.count`. Only the platform knows which form a number
takes, so the composable resolves it with `pluralStringResource`, the same sanctioned exception to
*No mapping in composables* that `stringResource` already is.

## At the edges

A token with no `when` arm is a compile error, because the `when` is exhaustive over the enum and
its result is used. Adding a label therefore cannot be half-done: the resource, the token and the
arm all have to exist before the module compiles.

A string added to `values/` and forgotten in `values-ru/` is caught by Android Lint's
`MissingTranslation`, which `:app` runs with `warningsAsErrors = true`. That is the only build-time
guard on the pair staying complete.

Nothing enforces *at build time* that a literal never appears in a `Text(...)`; that is review's
job, and `docs/rules/compose-patterns.md` states the rule reviewers apply.

## Where the code lives

- `ui/src/main/res/values/strings.xml`, `ui/src/main/res/values-ru/strings.xml` — every
  user-facing string bar the four below and the launcher label.
- `presentation/src/androidMain/res/values{,-ru}/strings.xml` — the formatter's four words.
- `presentation/src/commonMain/kotlin/dev/catsradar/presentation/encounters/LocationLabel.kt` — the
  first data-driven token.
- `docs/rules/compose-patterns.md` — the rule and its sanctioned exception to *No mapping in
  composables*.

## Not built yet

The Russian text was written in one pass rather than reviewed by a second reader, so it is a
translation, not a localisation: no third language exists, and nothing has been checked against how
the phrases read on a narrow screen.
