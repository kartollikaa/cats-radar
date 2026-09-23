# Quality gates

`./gradlew check` (and CI, running the same command) wires together three independent tools
through `build-logic` convention plugins, so a module gets all of them just by applying its
convention plugin — `catsradar.kmp.library`, `catsradar.android.library`, and
`catsradar.android.application` each apply `catsradar.detekt` themselves: detekt (with
`detekt-formatting` and `compose-rules`) for style, complexity, and Compose-specific rules; Android
Lint for manifest/resource/API-level/Compose-runtime issues; and Konsist for the architecture and
naming rules described in `docs/rules/module-structure.md` and `docs/rules/mvi-architecture.md`,
written as plain JUnit tests under `app/src/test/kotlin/dev/catsradar/app/architecture/`. The
binding rules for all three are `docs/rules/static-analysis.md`.

## At the edges

Lint's actual reach is narrower than "every module": `:domain`, `:data`, and `:presentation`
apply `catsradar.kmp.library`, whose Kotlin Multiplatform Android Library target creates lint
*analysis* tasks (`lintAnalyzeAndroidHostTest`) but no report/abort task — so the `lint { }` block
configured there is inert, and that analysis task is disabled outright rather than paid for on
every `check` with nothing to show for it. Lint genuinely runs, and can fail the build
(`warningsAsErrors = true`, `abortOnError = true`), only on `:app` and `:ui`, the Android
application/library modules. `:app` additionally sets `checkDependencies = true`, so it also lints
its library dependencies' resources, not just its own.

detekt has `build.maxIssues: 0` (its own default, not overridden) and no baseline file —
`config/detekt/baseline.xml` is deliberately never created, so a finding on existing code has to
be fixed rather than grandfathered in. `:ui` alone gets a second, merged config
(`config/detekt/detekt-ui.yml`) that turns off `MagicNumber` for that module, since inline Compose
dimension literals (`24.dp`, `0.74f`) are the convention there, not an exception to police
(`docs/rules/compose-patterns.md` §7).

Konsist's boundary rules are scoped by each file's *declared* package, not by which physical
module directory it sits in — `Konsist.scopeFromModule` returns an empty scope in this project
regardless of name, which is why `ModuleBoundaryTest` uses `scopeFromPackage` instead. That choice
creates a blind spot the tests close deliberately: a file physically inside `:app` but declaring
`package dev.catsradar.presentation` would be invisible to every import-boundary rule while still
matching them, so `ModulePackageMappingTest` separately asserts the mapping both ways — a module's
files declare that module's package, and a declared package's files physically live in that
module — for `domain`, `presentation`, `ui`, and `data`. Two of the module graph's own guarantees —
`:domain` depending on nothing but `kotlinx`, and `:ui` never depending on `:domain` — are enforced
today only by there being no `implementation(projects.domain)` in `:ui`'s `build.gradle.kts` and
no project dependency at all in `:domain`'s; no Konsist test backs either one, so an accidental
dependency edit would not be caught by `check`.

Beyond the import-boundary rules, Konsist also checks: every class named `*Store` lives under
`dev.catsradar.presentation`; every class named `*State` has no function-typed property (a literal
lambda type, a `fun interface`, or a typealias for either — the enforcement mechanism behind
`docs/rules/mvi-architecture.md`'s "State is data, no function types," since detekt/compose-rules
has no rule for it); every public `@Composable` with a `Modifier` parameter declares
`modifier: Modifier = Modifier` as its first defaulted parameter; every `NavKey`
implementation is `@Serializable` (needed for Navigation 3's saved-state restoration, and for
polymorphic key serialization once a non-JVM target exists); and `CatsRadarNavHost` takes its
back stack from `rememberBottomNavBackStack()`, with no other `:app` source building or
remembering a raw `NavBackStack` (`NavBackStackUsageTest`, see `app-shell.md`). `*Intent`, `*Effect`, and
`*StateMapper` naming has no Konsist test at all.

The DI graph gets its own two-layer check outside the three formal tools: `KoinModulesTest`
statically verifies every constructor-injected binding resolves, and `KoinRuntimeResolutionTest`
actually starts Koin and resolves the handful of types obtained by hand that the static check
can't see — see `app-shell.md`.

## Where the code lives

- `build-logic/convention/src/main/kotlin/DetektConventionPlugin.kt`,
  `AndroidLibraryConventionPlugin.kt`, `AndroidApplicationConventionPlugin.kt`,
  `KmpLibraryConventionPlugin.kt`, `dev/catsradar/buildlogic/AndroidCommon.kt`
- `config/detekt/detekt.yml`, `detekt-ui.yml`
- `app/src/test/kotlin/dev/catsradar/app/architecture/*.kt`
- `app/src/test/kotlin/dev/catsradar/app/di/KoinModulesTest.kt`, `KoinRuntimeResolutionTest.kt`

## Not handled yet

The rest of the design spec's §7 test list has tests. The exception is the on-device smoke test (tap
the counter, see 1): `:app` has no `src/androidTest` sources, no instrumentation runner and no
instrumented-test dependencies, and CI runs `./gradlew check` alone, which runs host tests and
starts no emulator.
