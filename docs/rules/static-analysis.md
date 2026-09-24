# Static Analysis

Three tools, all wired through `build-logic` so a module gets them by applying its convention plugin.
`./gradlew check` runs everything below plus unit tests; CI runs the same command.

| Tool | What it catches | Config |
|---|---|---|
| **detekt** with `detekt-formatting` and `compose-rules` | Style and complexity; ktlint-equivalent formatting; Compose-specific rules (modifier order and defaults, unstable collection parameters, `remember` misuse, preview naming) | `config/detekt/detekt.yml`; **no baseline file** — `config/detekt/baseline.xml` is never created, a finding on existing code is fixed, not suppressed |
| **Android Lint** | Manifest, resource, API-level and Compose runtime issues, **`:app` and `:ui` only** — AGP's Kotlin Multiplatform Android Library plugin has no lint-report task, so `:domain`/`:data`/`:presentation` are configured but not actually checked | Gradle DSL only (`lint { }` in the convention plugins); no root `lint.xml` — the DSL covers everything needed; `warningsAsErrors = true`, `abortOnError = true` |
| **Konsist** | The five import-boundary rules and the composable-Modifier, `*Store`, `*State` naming rules below — **not** `*Intent`/`*Effect`/`*StateMapper` naming, which has no test | Plain unit tests under `app/src/test/…/architecture` |

## detekt conventions

- `LongParameterList` ignores `@Composable` functions — [compose-patterns.md](./compose-patterns.md) §5
  requires one explicit lambda per user action, so a screen's parameter count measures how much the
  user can do there, not how tangled the function is. Non-composable functions keep the default.
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
- compose-rules' `PreviewNaming` is on — enforces the preview naming strategy, matching our
  `*Preview` suffix convention from [compose-preview-patterns.md](./compose-preview-patterns.md) §5
  (opt-in rule, off by default in compose-rules).
- compose-rules' `UnstableCollections` is on — a composable or class parameter typed as a plain
  `List`/`Set`/`Map` is flagged, matching the `Immutable*` collections rule for State from
  [compose-patterns.md](./compose-patterns.md) §2 (opt-in rule, off by default in compose-rules).
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

## Android Lint conventions

- `disable += "GradleDependency"` (`AndroidCommon.kt`) — the version catalog is updated
  deliberately, not on lint's schedule.
- `disable += "AndroidGradlePluginVersion"` (`AndroidCommon.kt`) — same reasoning, scoped to AGP
  itself: the catalog can pin an AGP older than lint's idea of latest (e.g. to match the IDE's
  supported range) without that pin failing `check`.

## Suppressions

`@Suppress("RuleName")` is allowed only with the reason on the same line, and only when the rule is
wrong for that spot — never to silence a real finding. A suppression that needs a paragraph is a
refactor.

## What these tools do not see

Native code inside a dependency. A green `check` says nothing about whether the `.so` files an
AndroidX AAR ships are 16 KB page compatible, and `GradleDependency` is disabled, so a version bump
that reintroduces the problem passes every gate here. The app has to be launched on a 16 KB device
to find out — see [docs/reference/16kb-page-size.md](../reference/16kb-page-size.md).

What R8 removes from a release build. `check` builds and tests unminified code, so a class a library
creates from its name, like a Glance action callback, can lose its constructor in the release APK
while every gate here stays green. Only that APK, launched, shows it — see
[docs/reference/releasing.md](../reference/releasing.md).
