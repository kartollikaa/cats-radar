# User-facing text

Every word the app shows comes from an Android string resource. `:ui` owns
`ui/src/main/res/values/strings.xml`; `:app` owns only `app_name`, because that is the launcher
label and belongs to the application module.

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

## At the edges

A token with no `when` arm is a compile error, because the `when` is exhaustive over the enum and
its result is used. Adding a label therefore cannot be half-done: the resource, the token and the
arm all have to exist before the module compiles.

Nothing enforces *at build time* that a literal never appears in a `Text(...)`; that is review's
job, and `docs/rules/compose-patterns.md` states the rule reviewers apply.

## Where the code lives

- `ui/src/main/res/values/strings.xml` — every user-facing string.
- `presentation/src/commonMain/kotlin/dev/catsradar/presentation/encounters/LocationLabel.kt` — the
  first data-driven token.
- `docs/rules/compose-patterns.md` — the rule and its sanctioned exception to *No mapping in
  composables*.

## Not built yet

Russian resources (`values-ru`) are their own slice; today `strings.xml` is English only, so a
Russian device sees English. Plurals are not used yet — the counter renders a bare number — and
will need `<plurals>` rather than a `<string>` when a phrase like "3 cats" appears.
