# Static Analysis

Three tools, all wired through `build-logic` so a module gets them by applying its convention plugin.
`./gradlew check` runs everything below plus unit tests; CI runs the same command.

| Tool | What it catches | Config |
|---|---|---|
| **detekt** with `detekt-formatting` and `compose-rules` | Style and complexity; ktlint-equivalent formatting; Compose-specific rules (modifier order and defaults, unstable collection parameters, `remember` misuse, preview naming, lambda parameters in composables) | `config/detekt/detekt.yml`; **no baseline file** — `config/detekt/baseline.xml` is never created, a finding on existing code is fixed, not suppressed |
| **Android Lint** | Manifest, resource, API-level and Compose runtime issues, **`:app` and `:ui` only** — AGP's Kotlin Multiplatform Android Library plugin has no lint-report task, so `:domain`/`:data`/`:presentation` are configured but not actually checked | Gradle DSL only (`lint { }` in the convention plugins); no root `lint.xml` — the DSL covers everything needed; `warningsAsErrors = true`, `abortOnError = true` |
| **Konsist** | The four import-boundary rules and the composable-Modifier, `*Store`, `*State` naming rules below — **not** `*Intent`/`*Effect`/`*StateMapper` naming, which has no test | Plain unit tests under `app/src/test/…/architecture` |

## detekt conventions

- `FunctionNaming` ignores `@Composable` functions — the default pattern is lowerCamelCase, which
  every composable violates by Compose convention (PascalCase).
- `TopLevelPropertyNaming.constantPattern` accepts `PascalCase` in addition to `UPPER_SNAKE`, so
  UI-token constants follow the Compose API guidelines (see
  [compose-patterns.md](./compose-patterns.md) §7). `propertyPattern`/`privatePropertyPattern` drop
  the default's allowance for underscores — a non-const top-level `val` may be camelCase or
  PascalCase, never `snake_case` (same section).
- `MagicNumber` excludes `**/ui/**`, scoped to the `:ui` module only via a second config file
  (`config/detekt/detekt-ui.yml`, merged only when configuring `:ui`) — Compose dimensions inline
  are fine there; ignoring named arguments anywhere is detekt's own default, not a project override.
- `MaxLineLength` = 120, matching `.editorconfig` — this is detekt's own shipped default; not
  overridden in `config/detekt/detekt.yml`.
- `UndocumentedPublicClass`/`UndocumentedPublicFunction` stay **off** — KDoc is earned, not
  mandatory ([code-commenting-standards.md](./code-commenting-standards.md)). Also detekt's own
  default, not overridden.
- `UnusedPrivateMember` ignores `@Preview`/`@ThemePreviews`-annotated functions — a private preview
  is invoked by tooling, not by a call site in the file (see
  [compose-preview-patterns.md](./compose-preview-patterns.md) §1).
- compose-rules' `Material2` is on — the project renders with Material3 only, so any Material2
  import is flagged (opt-in rule, off by default in compose-rules).
- compose-rules' `PreviewAnnotationNaming` is off — it requires a multipreview annotation name
  prefixed with `Preview`, which contradicts the fixed name `ThemePreviews` from
  [compose-preview-patterns.md](./compose-preview-patterns.md) §1.
- compose-rules has no rule for "no lambda-typed property in a state class" — the equivalent of
  [mvi-architecture.md](./mvi-architecture.md)'s "State is data, no function types" is covered
  instead by Konsist's `NamingConventionTest` (`classes named State have no function typed
  properties`), not by detekt.
- `build.maxIssues: 0` (any finding fails `./gradlew check`) is detekt's own default; not
  overridden.
- Formatting violations are auto-fixed with `./gradlew detekt --auto-correct`; commit the result,
  don't suppress.

## Suppressions

`@Suppress("RuleName")` is allowed only with the reason on the same line, and only when the rule is
wrong for that spot — never to silence a real finding. A suppression that needs a paragraph is a
refactor.
