# Static Analysis

Three tools, all wired through `build-logic` so a module gets them by applying its convention plugin.
`./gradlew check` runs everything below plus unit tests; CI runs the same command.

| Tool | What it catches | Config |
|---|---|---|
| **detekt** with `detekt-formatting` and `compose-rules` | Style and complexity; ktlint-equivalent formatting; Compose-specific rules (modifier order and defaults, unstable collection parameters, `remember` misuse, preview naming, lambda parameters in composables) | `config/detekt/detekt.yml`; baseline `config/detekt/baseline.xml` only for pre-existing debt, never for new code |
| **Android Lint** | Manifest, resource, API-level and Compose runtime issues | `lint.xml` at the root; `warningsAsErrors = true`, `abortOnError = true` |
| **Konsist** | Module and layer boundaries from [module-structure.md](./module-structure.md); naming of `*Store` / `*State` / `*Intent` / `*Effect` / `*StateMapper`; `@Composable` functions with a `Modifier` parameter default to `Modifier` | Plain unit tests under `app/src/test/…/architecture` |

## detekt conventions

- `MagicNumber` ignores named arguments and `**/ui/**` — Compose dimensions inline are fine.
- `TopLevelPropertyNaming` accepts `PascalCase` for constants so UI tokens follow the Compose API
  guidelines (see [compose-patterns.md](./compose-patterns.md) §7).
- `MaxLineLength` = 120, matching `.editorconfig`.
- `UndocumentedPublicClass`/`UndocumentedPublicFunction` stay **off** — KDoc is earned, not
  mandatory ([code-commenting-standards.md](./code-commenting-standards.md)).
- Formatting violations are auto-fixed with `./gradlew detekt --auto-correct`; commit the result,
  don't suppress.

## Suppressions

`@Suppress("RuleName")` is allowed only with the reason on the same line, and only when the rule is
wrong for that spot — never to silence a real finding. A suppression that needs a paragraph is a
refactor.
