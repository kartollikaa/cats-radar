# Static Analysis

Three tools, all wired through `build-logic` so a module gets them by applying its convention plugin.
`./gradlew check` runs everything below plus unit tests; CI runs the same command.

| Tool | What it catches | Config |
|---|---|---|
| **detekt** with `detekt-formatting` and `compose-rules` | Style and complexity; ktlint-equivalent formatting; Compose-specific rules (modifier order and defaults, unstable collection parameters, `remember` misuse, preview naming, lambda parameters in composables) | `config/detekt/detekt.yml`; baseline `config/detekt/baseline.xml` only for pre-existing debt, never for new code |
| **Android Lint** | Manifest, resource, API-level and Compose runtime issues, **`:app` and `:ui` only** — AGP's Kotlin Multiplatform Android Library plugin has no lint-report task, so `:domain`/`:data`/`:presentation` are configured but not actually checked | Gradle DSL only (`lint { }` in the convention plugins); no root `lint.xml` — the DSL covers everything needed; `warningsAsErrors = true`, `abortOnError = true` |
| **Konsist** | The four import-boundary rules and the composable-Modifier, `*Store`, `*State` naming rules below — **not** `*Intent`/`*Effect`/`*StateMapper` naming, which has no test | Plain unit tests under `app/src/test/…/architecture` |

## detekt conventions

- `MagicNumber` excludes `**/ui/**` (added on top of detekt's own test-directory exclusions) —
  Compose dimensions inline are fine; ignoring named arguments is detekt's own default, not a
  project override.
- `TopLevelPropertyNaming` accepts `PascalCase` for constants so UI tokens follow the Compose API
  guidelines (see [compose-patterns.md](./compose-patterns.md) §7).
- `MaxLineLength` = 120, matching `.editorconfig` — this is detekt's own shipped default; not
  overridden in `config/detekt/detekt.yml`.
- `UndocumentedPublicClass`/`UndocumentedPublicFunction` stay **off** — KDoc is earned, not
  mandatory ([code-commenting-standards.md](./code-commenting-standards.md)). Also detekt's own
  default, not overridden.
- `UnusedPrivateMember` ignores `@Preview`/`@ThemePreviews`-annotated functions — a private preview
  is invoked by tooling, not by a call site in the file (see
  [compose-preview-patterns.md](./compose-preview-patterns.md) §1).
- compose-rules' `PreviewAnnotationNaming` is off — it requires a multipreview annotation name
  prefixed with `Preview`, which contradicts the fixed name `ThemePreviews` from
  [compose-preview-patterns.md](./compose-preview-patterns.md) §1.
- `build.maxIssues: 0` (any finding fails `./gradlew check`) is detekt's own default; not
  overridden.
- Formatting violations are auto-fixed with `./gradlew detekt --auto-correct`; commit the result,
  don't suppress.

## Suppressions

`@Suppress("RuleName")` is allowed only with the reason on the same line, and only when the rule is
wrong for that spot — never to silence a real finding. A suppression that needs a paragraph is a
refactor.
